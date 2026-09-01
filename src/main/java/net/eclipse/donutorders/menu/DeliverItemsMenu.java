package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.model.Order;
import net.eclipse.donutorders.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deposit area (slots 0-26) plus a control row.
 * Anything left in the deposit area is handed back when the menu closes.
 */
public class DeliverItemsMenu extends Menu {

    private static final int DEPOSIT_END = 27;

    private final Order order;
    private boolean returning = false;

    public DeliverItemsMenu(DonutOrders plugin, Player player, Order order) {
        super(plugin, player);
        this.order = order;
    }

    @Override
    protected String configPath() {
        return "DELIVER-ITEMS-MENU";
    }

    @Override
    public int size() {
        return 36;
    }

    @Override
    public boolean cancelClicksByDefault() {
        return false;
    }

    @Override
    public boolean isDepositSlot(int slot) {
        return slot >= 0 && slot < DEPOSIT_END;
    }

    @Override
    protected void decorate() {
        renderControls();
    }

    /** Redraws only the control row so deposited items are not wiped. */
    private void renderControls() {
        ConfigurationSection info = button("ORDER-BUTTON");
        if (info != null) {
            inventory.setItem(info.getInt("SLOT", 27),
                    ItemBuilder.fromSection(info, order.getItemCopy(1))
                            .with(OrdersMenu.placeholders(order))
                            .build());
        }
        ConfigurationSection confirm = button("CONFIRM-BUTTON");
        if (confirm != null) {
            inventory.setItem(confirm.getInt("SLOT", 31),
                    ItemBuilder.fromSection(confirm, Material.LIME_CONCRETE)
                            .with(OrdersMenu.placeholders(order)).build());
        }
        ConfigurationSection depositAll = button("DEPOSIT-ALL-BUTTON");
        if (depositAll != null) {
            inventory.setItem(depositAll.getInt("SLOT", 29),
                    ItemBuilder.fromSection(depositAll, Material.HOPPER)
                            .with("held", plugin.orders().countMatching(player, order)).build());
        }
        ConfigurationSection cancel = button("CANCEL-BUTTON");
        if (cancel != null) {
            inventory.setItem(cancel.getInt("SLOT", 35),
                    ItemBuilder.fromSection(cancel, Material.RED_CONCRETE).build());
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        boolean topInventory = raw < size();

        // Free movement inside the deposit area.
        if (topInventory && isDepositSlot(raw)) {
            return;
        }

        if (!topInventory) {
            // Shift-clicking from the player's inventory should land in the deposit area only.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) return;
                moveIntoDeposit(clicked);
                player.updateInventory();
            }
            return;
        }

        event.setCancelled(true);

        ConfigurationSection confirm = button("CONFIRM-BUTTON");
        if (confirm != null && raw == confirm.getInt("SLOT", 31)) {
            confirmDelivery();
            return;
        }
        ConfigurationSection depositAll = button("DEPOSIT-ALL-BUTTON");
        if (depositAll != null && raw == depositAll.getInt("SLOT", 29)) {
            depositEverything();
            return;
        }
        ConfigurationSection cancel = button("CANCEL-BUTTON");
        if (cancel != null && raw == cancel.getInt("SLOT", 35)) {
            click();
            returnDeposit();
            openLater(new OrdersMenu(plugin, player));
        }
    }

    private void moveIntoDeposit(ItemStack clicked) {
        ItemStack moving = clicked.clone();
        clicked.setAmount(0);
        for (int slot = 0; slot < DEPOSIT_END && moving.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                inventory.setItem(slot, moving.clone());
                moving.setAmount(0);
            } else if (existing.isSimilar(moving)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                int move = Math.min(space, moving.getAmount());
                existing.setAmount(existing.getAmount() + move);
                moving.setAmount(moving.getAmount() - move);
            }
        }
        if (moving.getAmount() > 0) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(moving);
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
    }

    private void depositEverything() {
        int needed = order.getRemaining();
        for (int slot = 0; slot < DEPOSIT_END; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && order.matches(existing)) needed -= existing.getAmount();
        }
        if (needed <= 0) {
            deny();
            return;
        }

        int moved = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && moved < needed; index++) {
            ItemStack stack = contents[index];
            if (stack == null || !order.matches(stack)) continue;
            int take = Math.min(stack.getAmount(), needed - moved);
            ItemStack moving = stack.clone();
            moving.setAmount(take);
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) contents[index] = null;
            moveIntoDeposit(moving);
            moved += take;
        }
        player.getInventory().setStorageContents(contents);

        if (moved > 0) {
            click();
        } else {
            deny();
            tell(plugin.message("NOTHING-TO-DELIVER"));
        }
        renderControls();
        player.updateInventory();
    }

    private void confirmDelivery() {
        List<ItemStack> deposited = new ArrayList<>();
        for (int slot = 0; slot < DEPOSIT_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) deposited.add(stack);
        }
        if (deposited.isEmpty()) {
            deny();
            tell(plugin.message("NOTHING-TO-DELIVER"));
            return;
        }

        int delivered = plugin.orders().deliverStacks(player, order, deposited);
        if (delivered > 0) {
            success();
        } else {
            deny();
        }

        // Write back the (possibly reduced) stacks and clear empties.
        for (int slot = 0; slot < DEPOSIT_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getAmount() <= 0) inventory.setItem(slot, null);
        }

        returnDeposit();
        if (order.getRemaining() <= 0 || !order.isListed()) {
            openLater(new OrdersMenu(plugin, player));
        } else {
            renderControls();
            player.updateInventory();
        }
    }

    /** Hands back everything sitting in the deposit area. */
    private void returnDeposit() {
        if (returning) return;
        returning = true;
        for (int slot = 0; slot < DEPOSIT_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) continue;
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        returning = false;
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        returnDeposit();
    }
}
