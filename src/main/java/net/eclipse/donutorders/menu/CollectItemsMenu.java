package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.manager.OrderManager;
import net.eclipse.donutorders.model.Order;
import net.eclipse.donutorders.util.ItemBuilder;
import net.eclipse.donutorders.util.NumberUtil;
import net.eclipse.donutorders.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything delivered to the player's orders, waiting to be taken or sold. */
public class CollectItemsMenu extends PagedMenu {

    private record Loot(Order order, int amount) {
    }

    private List<Loot> visible = new ArrayList<>();

    public CollectItemsMenu(DonutOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "COLLECT-ITEMS-MENU";
    }

    @Override
    public int size() {
        return 54;
    }

    private List<Loot> compute() {
        List<Loot> loot = new ArrayList<>();
        for (Order order : plugin.orders().collectable(player.getUniqueId())) {
            int remaining = order.getCollectable();
            int stackSize = order.getItem().getMaxStackSize();
            while (remaining > 0) {
                int amount = Math.min(remaining, stackSize);
                loot.add(new Loot(order, amount));
                remaining -= amount;
            }
        }
        return loot;
    }

    @Override
    protected void decorate() {
        visible = compute();
        List<Loot> slice = pageSlice(visible);
        ConfigurationSection lootButton = button("LOOT-BUTTON");

        for (int index = 0; index < slice.size() && index < itemsPerPage(); index++) {
            Loot loot = slice.get(index);
            double value = plugin.sellPrices().totalPrice(loot.order().getItem(), loot.amount());
            inventory.setItem(index, ItemBuilder.fromSection(lootButton, loot.order().getItemCopy(loot.amount()))
                    .with(OrdersMenu.placeholders(loot.order()))
                    .with("amount", loot.amount())
                    .with("value", NumberUtil.money(value))
                    .build());
        }

        OrderManager.SellPreview preview = plugin.orders().previewSell(player);

        ConfigurationSection sellAll = button("SELL-ALL-BUTTON");
        if (sellAll != null) {
            inventory.setItem(sellAll.getInt("SLOT", 50),
                    ItemBuilder.fromSection(sellAll, Material.EMERALD)
                            .with("total", NumberUtil.money(preview.total()))
                            .with("total_items", preview.sellableItems())
                            .with("unsellable", preview.unsellableItems())
                            .build());
        }
        ConfigurationSection dropAll = button("DROP-ALL-BUTTON");
        if (dropAll != null) {
            inventory.setItem(dropAll.getInt("SLOT", 48),
                    ItemBuilder.fromSection(dropAll, Material.HOPPER).build());
        }
        ConfigurationSection collectAll = button("COLLECT-ALL-BUTTON");
        if (collectAll != null) {
            inventory.setItem(collectAll.getInt("SLOT", 49),
                    ItemBuilder.fromSection(collectAll, Material.CHEST).build());
        }
        ConfigurationSection back = button("BACK-BUTTON");
        if (back != null) {
            inventory.setItem(back.getInt("SLOT", 47),
                    ItemBuilder.fromSection(back, Material.RED_CONCRETE).build());
        }

        renderPageButtons(visible.size());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();

        if (handlePageClick(raw, visible.size())) return;

        ConfigurationSection sellAll = button("SELL-ALL-BUTTON");
        if (sellAll != null && raw == sellAll.getInt("SLOT", 50)) {
            click();
            openLater(new ConfirmSellMenu(plugin, player));
            return;
        }

        ConfigurationSection collectAll = button("COLLECT-ALL-BUTTON");
        if (collectAll != null && raw == collectAll.getInt("SLOT", 49)) {
            int collected = plugin.orders().collectAll(player);
            if (collected > 0) success();
            else deny();
            refresh();
            return;
        }

        ConfigurationSection dropAll = button("DROP-ALL-BUTTON");
        if (dropAll != null && raw == dropAll.getInt("SLOT", 48)) {
            Set<Order> pageOrders = new LinkedHashSet<>();
            for (Loot loot : pageSlice(visible)) pageOrders.add(loot.order());
            int dropped = plugin.orders().dropAll(player, new ArrayList<>(pageOrders));
            if (dropped > 0) {
                success();
                tell(Text.apply(plugin.message("DROPPED-ALL"), Map.of("amount", String.valueOf(dropped))));
            } else {
                deny();
            }
            refresh();
            return;
        }

        ConfigurationSection back = button("BACK-BUTTON");
        if (back != null && raw == back.getInt("SLOT", 47)) {
            click();
            openLater(new YourOrdersMenu(plugin, player));
            return;
        }

        if (raw >= 0 && raw < itemsPerPage()) {
            List<Loot> slice = pageSlice(visible);
            if (raw >= slice.size()) return;
            Loot loot = slice.get(raw);
            int collected = plugin.orders().collect(player, loot.order(), loot.amount());
            if (collected > 0) success();
            else deny();
            refresh();
        }
    }
}
