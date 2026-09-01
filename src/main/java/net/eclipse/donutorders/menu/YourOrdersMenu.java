package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.model.Order;
import net.eclipse.donutorders.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YourOrdersMenu extends Menu {

    private final Map<Integer, Order> slots = new HashMap<>();

    public YourOrdersMenu(DonutOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "YOUR-ORDERS-MENU";
    }

    @Override
    protected void decorate() {
        slots.clear();
        int newOrderSlot = slot("NEW-ORDER-SLOT", size() - 1);
        int backSlot = slot("BACK-SLOT", size() - 9);

        List<Order> orders = plugin.orders().ordersOf(player.getUniqueId());
        int cursor = 0;
        for (Order order : orders) {
            while (cursor == newOrderSlot || cursor == backSlot) cursor++;
            if (cursor >= size()) break;
            int amount = Math.max(1, Math.min(order.getItem().getMaxStackSize(), order.getRemaining()));
            inventory.setItem(cursor, ItemBuilder.fromSection(button("ORDER-BUTTON"), order.getItemCopy(amount))
                    .with(OrdersMenu.placeholders(order))
                    .build());
            slots.put(cursor, order);
            cursor++;
        }

        inventory.setItem(newOrderSlot,
                ItemBuilder.fromSection(button("NEW-ORDER-BUTTON"), Material.MAP)
                        .with("count", orders.size())
                        .build());
        inventory.setItem(backSlot,
                ItemBuilder.fromSection(plugin.globalItem("BACK"), Material.RED_CONCRETE)
                        .with("current", 1).with("total", 1)
                        .build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();

        if (raw == slot("NEW-ORDER-SLOT", size() - 1)) {
            click();
            openLater(new NewOrderMenu(plugin, player));
            return;
        }
        if (raw == slot("BACK-SLOT", size() - 9)) {
            click();
            openLater(new OrdersMenu(plugin, player));
            return;
        }

        Order order = slots.get(raw);
        if (order == null) return;
        click();
        openLater(new EditOrderMenu(plugin, player, order));
    }
}
