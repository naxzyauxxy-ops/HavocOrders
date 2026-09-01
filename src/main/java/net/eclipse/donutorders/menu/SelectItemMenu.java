package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.manager.ItemCatalogue;
import net.eclipse.donutorders.util.Category;
import net.eclipse.donutorders.util.ItemBuilder;
import net.eclipse.donutorders.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SelectItemMenu extends PagedMenu {

    private final NewOrderMenu parent;

    private Category filter = Category.ALL;
    private boolean ascending = true;
    private String query = "";

    private List<ItemCatalogue.Entry> visible = new ArrayList<>();

    public SelectItemMenu(DonutOrders plugin, Player player, NewOrderMenu parent) {
        super(plugin, player);
        this.parent = parent;
    }

    @Override
    protected String configPath() {
        return "SELECT-ITEM-MENU";
    }

    @Override
    public int size() {
        return 54;
    }

    private List<ItemCatalogue.Entry> compute() {
        String lowered = query.toLowerCase(Locale.ROOT);
        List<ItemCatalogue.Entry> result = new ArrayList<>();
        for (ItemCatalogue.Entry entry : plugin.catalogue().entries()) {
            if (!filter.matches(entry.stack().getType())) continue;
            if (!lowered.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(lowered)) continue;
            result.add(entry);
        }
        Comparator<ItemCatalogue.Entry> comparator =
                Comparator.comparing(ItemCatalogue.Entry::name, String.CASE_INSENSITIVE_ORDER);
        result.sort(ascending ? comparator : comparator.reversed());
        return result;
    }

    @Override
    protected void decorate() {
        visible = compute();
        List<ItemCatalogue.Entry> slice = pageSlice(visible);

        ConfigurationSection itemButton = button("ITEM-BUTTON");
        for (int index = 0; index < slice.size() && index < itemsPerPage(); index++) {
            ItemCatalogue.Entry entry = slice.get(index);
            ItemStack icon = ItemBuilder.fromSection(itemButton, entry.stack())
                    .with("name", entry.name())
                    .with("material", entry.name())
                    .build();
            inventory.setItem(index, icon);
        }

        renderSort();
        renderFilter();
        renderSearch();
        renderCancel();
        renderPageButtons(visible.size());
    }

    private void renderSort() {
        ConfigurationSection config = button("SORT-BUTTON");
        if (config == null) return;
        ConfigurationSection names = section().getConfigurationSection("SORT-OPTIONS");
        String aToZ = names == null ? "A to Z" : names.getString("A-TO-Z", "A to Z");
        String zToA = names == null ? "Z to A" : names.getString("Z-TO-A", "Z to A");
        String selected = config.getString("SELECTED-FORMAT", "&#f40d0d> {option}");
        String unselected = config.getString("UNSELECTED-FORMAT", "&f  {option}");
        List<String> lore = List.of(
                (ascending ? selected : unselected).replace("{option}", aToZ),
                (ascending ? unselected : selected).replace("{option}", zToA)
        );
        inventory.setItem(config.getInt("SLOT", 48),
                ItemBuilder.fromSection(config, Material.CAULDRON).lore(lore).build());
    }

    private void renderFilter() {
        ConfigurationSection config = button("FILTER-BUTTON");
        if (config == null) return;
        ConfigurationSection names = plugin.menuSection("ORDERS-MENU") == null ? null
                : plugin.menuSection("ORDERS-MENU").getConfigurationSection("BUTTONS.FILTER-BUTTON.NAMES");
        List<String> lore = new ArrayList<>();
        for (Category category : Category.values()) {
            String label = names == null ? Text.pretty(category.name())
                    : names.getString(category.name(), Text.pretty(category.name()));
            String format = category == filter
                    ? config.getString("SELECTED-FORMAT", "&#f40d0d> {option}")
                    : config.getString("UNSELECTED-FORMAT", "&f  {option}");
            lore.add(format.replace("{option}", label));
        }
        inventory.setItem(config.getInt("SLOT", 49),
                ItemBuilder.fromSection(config, Material.HOPPER).lore(lore).build());
    }

    private void renderSearch() {
        ConfigurationSection config = button("SEARCH-BUTTON");
        if (config == null) return;
        inventory.setItem(config.getInt("SLOT", 50),
                ItemBuilder.fromSection(config, Material.OAK_SIGN)
                        .with("query", query.isEmpty() ? "none" : query).build());
    }

    private void renderCancel() {
        ConfigurationSection config = button("CANCEL-BUTTON");
        if (config == null) return;
        inventory.setItem(config.getInt("SLOT", 47),
                ItemBuilder.fromSection(config, Material.RED_CONCRETE).build());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();

        if (handlePageClick(raw, visible.size())) return;

        ConfigurationSection sortConfig = button("SORT-BUTTON");
        if (sortConfig != null && raw == sortConfig.getInt("SLOT", 48)) {
            ascending = !ascending;
            page = 0;
            click();
            refresh();
            return;
        }

        ConfigurationSection filterConfig = button("FILTER-BUTTON");
        if (filterConfig != null && raw == filterConfig.getInt("SLOT", 49)) {
            filter = event.isRightClick() ? filter.previous() : filter.next();
            page = 0;
            click();
            refresh();
            return;
        }

        ConfigurationSection searchConfig = button("SEARCH-BUTTON");
        if (searchConfig != null && raw == searchConfig.getInt("SLOT", 50)) {
            if (event.isRightClick()) {
                query = "";
                page = 0;
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

        ConfigurationSection cancelConfig = button("CANCEL-BUTTON");
        if (cancelConfig != null && raw == cancelConfig.getInt("SLOT", 47)) {
            click();
            openLater(parent);
            return;
        }

        if (raw >= 0 && raw < itemsPerPage()) {
            List<ItemCatalogue.Entry> slice = pageSlice(visible);
            if (raw >= slice.size()) return;
            ItemCatalogue.Entry entry = slice.get(raw);

            if (entry.enchantedBook()) {
                click();
                openLater(new EnchantmentMenu(plugin, player, parent));
                return;
            }

            parent.setSelected(entry.stack());
            success();
            openLater(parent);
        }
    }
}
