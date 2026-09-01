package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.model.Order;
import net.eclipse.donutorders.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class EditOrderMenu extends Menu {

    private final Order order;

    public EditOrderMenu(DonutOrders plugin, Player player, Order order) {
        super(plugin, player);
        this.order = order;
    }

    @Override
    protected String configPath() {
        return "EDIT-ORDER-MENU";
    }

    @Override
    protected void decorate() {
        int amount = Math.max(1, Math.min(order.getItem().getMaxStackSize(), order.getRemaining()));
        inventory.setItem(slot("ORDER-SLOT", 13),
                ItemBuilder.fromSection(button("ORDER-BUTTON"), order.getItemCopy(amount))
                        .with(OrdersMenu.placeholders(order)).build());
        inventory.setItem(slot("CANCEL-SLOT", 11),
                ItemBuilder.fromSection(button("CANCEL-BUTTON"), Material.RED_CONCRETE)
                        .with(OrdersMenu.placeholders(order)).build());
        inventory.setItem(slot("COLLECT-SLOT", 15),
                ItemBuilder.fromSection(button("COLLECT-BUTTON"), Material.CHEST)
                        .with(OrdersMenu.placeholders(order)).build());
        inventory.setItem(slot("BACK-SLOT", 18),
                ItemBuilder.fromSection(plugin.globalItem("BACK"), Material.RED_CONCRETE)
                        .with("current", 1).with("total", 1).build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();

        if (raw == slot("CANCEL-SLOT", 11)) {
            click();
            openLater(new CancelOrderMenu(plugin, player, order));
        } else if (raw == slot("COLLECT-SLOT", 15)) {
            click();
            openLater(new CollectItemsMenu(plugin, player));
        } else if (raw == slot("BACK-SLOT", 18)) {
            click();
            openLater(new YourOrdersMenu(plugin, player));
        }
    }
}
