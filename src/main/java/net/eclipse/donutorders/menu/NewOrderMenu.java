package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.manager.OrderManager;
import net.eclipse.donutorders.util.ItemBuilder;
import net.eclipse.donutorders.util.ItemNames;
import net.eclipse.donutorders.util.NumberUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class NewOrderMenu extends Menu {

    private ItemStack selected;
    private int amount;
    private double price;

    public NewOrderMenu(DonutOrders plugin, Player player) {
        super(plugin, player);
        this.amount = Math.max(1, plugin.getConfig().getInt("SETTINGS.MIN-ITEM-AMOUNT", 1));
        this.price = Math.max(0.0D, plugin.getConfig().getDouble("SETTINGS.MIN-PRICE-AMOUNT", 1.0D));
    }

    @Override
    protected String configPath() {
        return "NEW-ORDER-MENU";
    }

    public void setSelected(ItemStack selected) {
        this.selected = selected == null ? null : selected.clone();
    }

    public ItemStack getSelected() {
        return selected;
    }

    private double total() {
        return amount * price;
    }

    @Override
    protected void decorate() {
        ConfigurationSection itemButton = button("ITEM-BUTTON");
        ItemStack icon = selected != null
                ? selected.clone()
                : new ItemStack(Material.matchMaterial(
                        itemButton == null ? "BARRIER" : itemButton.getString("MATERIAL", "BARRIER")));
        icon.setAmount(1);
        inventory.setItem(slot("ITEM-SLOT", 10),
                ItemBuilder.fromSection(itemButton, icon)
                        .with("material", selected == null ? "none" : ItemNames.display(selected))
                        .build());

        inventory.setItem(slot("AMOUNT-SLOT", 12),
                ItemBuilder.fromSection(button("AMOUNT-BUTTON"), Material.CHEST)
                        .amount(Math.min(64, Math.max(1, amount)))
                        .with("amount", amount)
                        .build());

        inventory.setItem(slot("PRICE-SLOT", 14),
                ItemBuilder.fromSection(button("PRICE-BUTTON"), Material.EMERALD)
                        .with("price", NumberUtil.money(price))
                        .build());

        inventory.setItem(slot("CONFIRM-SLOT", 16),
                ItemBuilder.fromSection(button("CONFIRM-BUTTON"), Material.LIME_CONCRETE)
                        .with("total", NumberUtil.money(total()))
                        .with("amount", amount)
                        .with("price", NumberUtil.money(price))
                        .with("material", selected == null ? "none" : ItemNames.display(selected))
                        .build());

        inventory.setItem(slot("CANCEL-SLOT", 22),
                ItemBuilder.fromSection(button("CANCEL-BUTTON"), Material.RED_CONCRETE).build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();

        if (raw == slot("ITEM-SLOT", 10)) {
            click();
            openLater(new SelectItemMenu(plugin, player, this));
            return;
        }

        if (raw == slot("AMOUNT-SLOT", 12)) {
            click();
            ConfigurationSection config = button("AMOUNT-BUTTON");
            String label = config == null ? "Amount" : config.getString("SIGN-TITLE", "Amount");
            plugin.chatInput().request(player, label, input -> {
                Integer parsed = NumberUtil.parseInt(input);
                if (parsed == null || parsed <= 0) {
                    tell(plugin.message("INVALID-AMOUNT"));
                } else {
                    amount = parsed;
                }
                open();
            }, this::open);
            return;
        }

        if (raw == slot("PRICE-SLOT", 14)) {
            click();
            ConfigurationSection config = button("PRICE-BUTTON");
            String label = config == null ? "Price" : config.getString("SIGN-TITLE", "Price");
            plugin.chatInput().request(player, label, input -> {
                Double parsed = NumberUtil.parseDouble(input);
                if (parsed == null || parsed <= 0) {
                    tell(plugin.message("INVALID-PRICE"));
                } else {
                    price = parsed;
                }
                open();
            }, this::open);
            return;
        }

        if (raw == slot("CONFIRM-SLOT", 16)) {
            OrderManager.Result result = plugin.orders().createOrder(player, selected, amount, price);
            tell(result.message());
            if (result.success()) {
                success();
                openLater(new YourOrdersMenu(plugin, player));
            } else {
                deny();
            }
            return;
        }

        if (raw == slot("CANCEL-SLOT", 22)) {
            click();
            openLater(new YourOrdersMenu(plugin, player));
        }
    }
}
