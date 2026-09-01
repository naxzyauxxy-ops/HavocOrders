package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.model.Order;
import net.eclipse.donutorders.model.SortOption;
import net.eclipse.donutorders.util.Category;
import net.eclipse.donutorders.util.ItemBuilder;
import net.eclipse.donutorders.util.NumberUtil;
import net.eclipse.donutorders.util.Text;
import net.eclipse.donutorders.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The public order board. */
public class OrdersMenu extends PagedMenu {

    private SortOption sort = SortOption.RECENTLY_LISTED;
    private Category filter = Category.ALL;
    private String query = "";

    private List<Order> visible = new ArrayList<>();

    public OrdersMenu(DonutOrders plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String configPath() {
        return "ORDERS-MENU";
    }

    @Override
    public int size() {
        return 54;
    }

    private List<Order> computeVisible() {
        List<Order> orders = new ArrayList<>();
        String lowered = query.toLowerCase(Locale.ROOT);
        for (Order order : plugin.orders().listed()) {
            if (!filter.matches(order.getMaterial())) continue;
            if (!lowered.isEmpty() && !order.getItemName().toLowerCase(Locale.ROOT).contains(lowered)
                    && !order.getOwnerName().toLowerCase(Locale.ROOT).contains(lowered)) {
                continue;
            }
            orders.add(order);
        }
        orders.sort(sort.getComparator());
        return orders;
    }

    @Override
    protected void decorate() {
        visible = computeVisible();
        List<Order> slice = pageSlice(visible);

        ConfigurationSection orderButton = button("ORDER-BUTTON");
        for (int index = 0; index < slice.size() && index < itemsPerPage(); index++) {
            inventory.setItem(index, render(slice.get(index), orderButton));
        }

        renderRefresh();
        renderSort();
        renderFilter();
        renderSearch();
        renderYourOrders();
        renderPageButtons(visible.size());
    }

    private ItemStack render(Order order, ConfigurationSection config) {
        int stackAmount = Math.max(1, Math.min(order.getItem().getMaxStackSize(), order.getRemaining()));
        return ItemBuilder.fromSection(config, order.getItemCopy(stackAmount))
                .with(placeholders(order))
                .build();
    }

    static Map<String, String> placeholders(Order order) {
        Map<String, String> map = new HashMap<>();
        map.put("player", order.getOwnerName());
        map.put("material", order.getItemName());
        map.put("unit_price", NumberUtil.money(order.getUnitPrice()));
        map.put("current", String.valueOf(order.getDelivered()));
        map.put("max", String.valueOf(order.getAmount()));
        map.put("remaining", String.valueOf(order.getRemaining()));
        map.put("paid", NumberUtil.money(order.getPaid()));
        map.put("max_paid", NumberUtil.money(order.getMaxPaid()));
        map.put("refund", NumberUtil.money(order.getRefund()));
        map.put("collectable", String.valueOf(order.getCollectable()));
        map.put("expires", TimeUtil.shortDuration(order.getMillisUntilExpiry()));
        return map;
    }

    private void renderRefresh() {
        ConfigurationSection config = button("REFRESH-BUTTON");
        if (config == null) return;
        inventory.setItem(config.getInt("SLOT", 49),
                ItemBuilder.fromSection(config, Material.MAP).with("total", visible.size()).build());
    }

    private void renderSort() {
        ConfigurationSection config = button("SORT-BUTTON");
        if (config == null) return;
        ConfigurationSection names = section().getConfigurationSection("SORT-OPTIONS");
        List<String> lore = new ArrayList<>();
        for (SortOption option : SortOption.values()) {
            String label = names == null ? Text.pretty(option.name())
                    : names.getString(option.getConfigKey(), Text.pretty(option.name()));
            String format = option == sort
                    ? config.getString("SELECTED-FORMAT", "&#f40d0d> {option}")
                    : config.getString("UNSELECTED-FORMAT", "&f  {option}");
            lore.add(format.replace("{option}", label));
        }
        inventory.setItem(config.getInt("SLOT", 47),
                ItemBuilder.fromSection(config, Material.CAULDRON).lore(lore).build());
    }

    private void renderFilter() {
        ConfigurationSection config = button("FILTER-BUTTON");
        if (config == null) return;
        ConfigurationSection names = config.getConfigurationSection("NAMES");
        List<String> lore = new ArrayList<>();
        for (Category category : Category.values()) {
            String label = names == null ? Text.pretty(category.name())
                    : names.getString(category.name(), Text.pretty(category.name()));
            String format = category == filter
                    ? config.getString("SELECTED-FORMAT", "&#f40d0d> {option}")
                    : config.getString("UNSELECTED-FORMAT", "&f  {option}");
            lore.add(format.replace("{option}", label));
        }
        inventory.setItem(config.getInt("SLOT", 48),
                ItemBuilder.fromSection(config, Material.HOPPER).lore(lore).build());
    }

    private void renderSearch() {
        ConfigurationSection config = button("SEARCH-BUTTON");
        if (config == null) return;
        inventory.setItem(config.getInt("SLOT", 50),
                ItemBuilder.fromSection(config, Material.OAK_SIGN)
                        .with("query", query.isEmpty() ? "none" : query)
                        .build());
    }

    private void renderYourOrders() {
        ConfigurationSection config = button("YOUR-ORDERS-BUTTON");
        if (config == null) return;
        inventory.setItem(config.getInt("SLOT", 51),
                ItemBuilder.fromSection(config, Material.CHEST)
                        .with("count", plugin.orders().ordersOf(player.getUniqueId()).size())
                        .build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int slot = event.getRawSlot();

        if (handlePageClick(slot, visible.size())) return;

        ConfigurationSection sortConfig = button("SORT-BUTTON");
        if (sortConfig != null && slot == sortConfig.getInt("SLOT", 47)) {
            sort = event.isRightClick() ? sort.previous() : sort.next();
            page = 0;
            click();
            refresh();
            return;
        }

        ConfigurationSection filterConfig = button("FILTER-BUTTON");
        if (filterConfig != null && slot == filterConfig.getInt("SLOT", 48)) {
            filter = event.isRightClick() ? filter.previous() : filter.next();
            page = 0;
            click();
            refresh();
            return;
        }

        ConfigurationSection refreshConfig = button("REFRESH-BUTTON");
        if (refreshConfig != null && slot == refreshConfig.getInt("SLOT", 49)) {
            click();
            refresh();
            return;
        }

        ConfigurationSection searchConfig = button("SEARCH-BUTTON");
        if (searchConfig != null && slot == searchConfig.getInt("SLOT", 50)) {
            if (event.isRightClick()) {
                query = "";
                click();
                refresh();
                return;
            }
            click();
            plugin.chatInput().request(player, searchConfig.getString("SIGN-TITLE", "Search"), input -> {
                query = input;
                page = 0;
                open();
            }, this::open);
            return;
        }

        ConfigurationSection yourOrders = button("YOUR-ORDERS-BUTTON");
        if (yourOrders != null && slot == yourOrders.getInt("SLOT", 51)) {
            click();
            openLater(new YourOrdersMenu(plugin, player));
            return;
        }

        if (slot >= 0 && slot < itemsPerPage()) {
            List<Order> slice = pageSlice(visible);
            if (slot >= slice.size()) return;
            Order order = slice.get(slot);

            if (order.getOwner().equals(player.getUniqueId())) {
                deny();
                tell(plugin.message("OWN-ORDER"));
                return;
            }

            if (event.isShiftClick()) {
                int delivered = plugin.orders().deliverFromInventory(player, order, order.getRemaining());
                if (delivered > 0) success();
                else deny();
                refresh();
                return;
            }

            click();
            openLater(new DeliverItemsMenu(plugin, player, order));
        }
    }
}
