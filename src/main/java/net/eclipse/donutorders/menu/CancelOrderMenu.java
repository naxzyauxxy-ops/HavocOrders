package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.manager.OrderManager;
import net.eclipse.donutorders.model.Order;
import net.eclipse.donutorders.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class CancelOrderMenu extends Menu {

    private final Order order;

    public CancelOrderMenu(DonutOrders plugin, Player player, Order order) {
        super(plugin, player);
        this.order = order;
    }

    @Override
    protected String configPath() {
        return "CANCEL-ORDER-MENU";
    }

    @Override
    protected void decorate() {
        inventory.setItem(slot("CANCEL-SLOT", 11),
                ItemBuilder.fromSection(button("CANCEL-BUTTON"), Material.RED_CONCRETE).build());
        int amount = Math.max(1, Math.min(order.getItem().getMaxStackSize(), order.getRemaining()));
        inventory.setItem(slot("ORDER-SLOT", 13),
                ItemBuilder.fromSection(button("ORDER-ITEM-BUTTON"), order.getItemCopy(amount))
                        .with(OrdersMenu.placeholders(order)).build());
        inventory.setItem(slot("CONFIRM-SLOT", 15),
                ItemBuilder.fromSection(button("CONFIRM-BUTTON"), Material.LIME_CONCRETE)
                        .with(OrdersMenu.placeholders(order)).build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();

        if (raw == slot("CANCEL-SLOT", 11)) {
            click();
            openLater(new EditOrderMenu(plugin, player, order));
            return;
        }
        if (raw == slot("CONFIRM-SLOT", 15)) {
            OrderManager.Result result = plugin.orders().cancel(player, order);
            tell(result.message());
            if (result.success()) {
                success();
                openLater(new YourOrdersMenu(plugin, player));
            } else {
                deny();
            }
        }
    }
}
