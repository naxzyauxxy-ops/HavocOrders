package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Adds page state plus the PAGE-BUTTON rendering shared by every paginated menu. */
public abstract class PagedMenu extends Menu {

    protected int page = 0;

    protected PagedMenu(DonutOrders plugin, Player player) {
        super(plugin, player);
    }

    protected int itemsPerPage() {
        ConfigurationSection section = section();
        int value = section == null ? 45 : section.getInt("ITEMS-PER-PAGE", 45);
        return Math.max(1, Math.min(value, size()));
    }

    protected int totalPages(int elements) {
        return Math.max(1, (int) Math.ceil(elements / (double) itemsPerPage()));
    }

    protected <T> List<T> pageSlice(List<T> all) {
        int perPage = itemsPerPage();
        int total = totalPages(all.size());
        if (page >= total) page = total - 1;
        if (page < 0) page = 0;
        int from = page * perPage;
        int to = Math.min(all.size(), from + perPage);
        return from >= to ? List.of() : all.subList(from, to);
    }

    protected void renderPageButtons(int elements) {
        ConfigurationSection config = button("PAGE-BUTTON");
        if (config == null) return;

        int total = totalPages(elements);
        int backSlot = config.getInt("BACK-SLOT", 45);
        int nextSlot = config.getInt("NEXT-SLOT", 53);
        Material material = Material.matchMaterial(config.getString("MATERIAL", "ARROW"));
        if (material == null) material = Material.ARROW;

        boolean hasPrevious = page > 0;
        boolean hasNext = page < total - 1;

        Map<String, String> placeholders = Map.of(
                "current", String.valueOf(page + 1),
                "next", String.valueOf(Math.min(page + 2, total)),
                "previous", String.valueOf(Math.max(page, 1)),
                "total", String.valueOf(total)
        );

        if (backSlot >= 0 && backSlot < size()) {
            ItemStack back = ItemBuilder.of(hasPrevious ? material : Material.GRAY_DYE)
                    .name(config.getString(hasPrevious ? "BACK-NAME" : "FIRST-PAGE-NAME", "Back"))
                    .lore(config.getStringList(hasPrevious ? "BACK-LORE" : "FIRST-PAGE-LORE"))
                    .with(placeholders)
                    .build();
            inventory.setItem(backSlot, back);
        }
        if (nextSlot >= 0 && nextSlot < size()) {
            ItemStack next = ItemBuilder.of(hasNext ? material : Material.GRAY_DYE)
                    .name(config.getString(hasNext ? "NEXT-NAME" : "LAST-PAGE-NAME", "Next"))
                    .lore(config.getStringList(hasNext ? "NEXT-LORE" : "LAST-PAGE-LORE"))
                    .with(placeholders)
                    .build();
            inventory.setItem(nextSlot, next);
        }
    }

    protected boolean handlePageClick(int slot, int elements) {
        ConfigurationSection config = button("PAGE-BUTTON");
        if (config == null) return false;
        int total = totalPages(elements);

        if (slot == config.getInt("BACK-SLOT", 45)) {
            if (page > 0) {
                page--;
                click();
                refresh();
            } else {
                deny();
            }
            return true;
        }
        if (slot == config.getInt("NEXT-SLOT", 53)) {
            if (page < total - 1) {
                page++;
                click();
                refresh();
            } else {
                deny();
            }
            return true;
        }
        return false;
    }
}
