package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.manager.OrderManager;
import net.eclipse.donutorders.util.ItemBuilder;
import net.eclipse.donutorders.util.NumberUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ConfirmSellMenu extends Menu {

    public ConfirmSellMenu(DonutOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "ORDER-CONFIRM-SELL-MENU";
    }

    @Override
    protected void decorate() {
        OrderManager.SellPreview preview = plugin.orders().previewSell(player);

        inventory.setItem(slot("CANCEL-SLOT", 11),
                ItemBuilder.fromSection(button("CANCEL-BUTTON"), Material.RED_CONCRETE).build());

        inventory.setItem(slot("ITEM-SLOT", 13),
                ItemBuilder.fromSection(button("ITEM-BUTTON"), Material.CHEST)
                        .with("player", player.getName())
                        .with("amount", preview.sellableItems())
                        .with("unsellable", preview.unsellableItems())
                        .with("total", NumberUtil.money(preview.total()))
                        .build());

        inventory.setItem(slot("CONFIRM-SLOT", 15),
                ItemBuilder.fromSection(button("CONFIRM-BUTTON"), Material.LIME_CONCRETE)
                        .with("total", NumberUtil.money(preview.total()))
                        .with("amount", preview.sellableItems())
                        .build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();

        if (raw == slot("CANCEL-SLOT", 11)) {
            click();
            openLater(new CollectItemsMenu(plugin, player));
            return;
        }
        if (raw == slot("CONFIRM-SLOT", 15)) {
            double earned = plugin.orders().sellAll(player);
            if (earned > 0) success();
            else deny();
            openLater(new CollectItemsMenu(plugin, player));
        }
    }
}
