package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.util.ItemBuilder;
import net.eclipse.donutorders.util.ItemNames;
import net.eclipse.donutorders.util.Text;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Picks a single enchantment + level, returned to the New Order menu as an enchanted book. */
public class EnchantmentMenu extends PagedMenu {

    private record Choice(Enchantment enchantment, int level) {
    }

    private final NewOrderMenu parent;
    private final List<Choice> choices = new ArrayList<>();
    private Choice selected;

    public EnchantmentMenu(DonutOrders plugin, Player player, NewOrderMenu parent) {
        super(plugin, player);
        this.parent = parent;
        buildChoices();
    }

    private void buildChoices() {
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            all.add(enchantment);
        }
        all.sort(Comparator.comparing(ItemNames::enchantment, String.CASE_INSENSITIVE_ORDER));
        for (Enchantment enchantment : all) {
            for (int level = 1; level <= Math.max(1, enchantment.getMaxLevel()); level++) {
                choices.add(new Choice(enchantment, level));
            }
        }
    }

    @Override
    protected String configPath() {
        return "ENCHANTMENT-MENU";
    }

    @Override
    public int size() {
        return 54;
    }

    private ItemStack book(Choice choice) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(choice.enchantment(), choice.level(), true);
            book.setItemMeta(meta);
        }
        return book;
    }

    @Override
    protected void decorate() {
        List<Choice> slice = pageSlice(choices);
        ConfigurationSection config = button("ENCHANT-BUTTON");

        for (int index = 0; index < slice.size() && index < itemsPerPage(); index++) {
            Choice choice = slice.get(index);
            boolean isSelected = choice.equals(selected);
            List<String> lore = config == null ? List.of()
                    : config.getStringList(isSelected ? "SELECTED-LORE" : "LORE");
            inventory.setItem(index, ItemBuilder.fromSection(config, book(choice))
                    .lore(lore)
                    .with("enchantment", ItemNames.enchantment(choice.enchantment()))
                    .with("level", Text.roman(choice.level()))
                    .build());
        }

        ConfigurationSection glass = button("GLASS-BUTTON");
        if (glass != null) {
            ItemStack filler = ItemBuilder.fromSection(glass, Material.LIGHT_GRAY_CONCRETE).build();
            for (int index = itemsPerPage(); index < size(); index++) {
                if (inventory.getItem(index) == null) inventory.setItem(index, filler);
            }
        }

        inventory.setItem(slot("CANCEL-SLOT", 48),
                ItemBuilder.fromSection(button("CANCEL-BUTTON"), Material.RED_CONCRETE).build());
        inventory.setItem(slot("CONFIRM-SLOT", 50),
                ItemBuilder.fromSection(button("CONFIRM-BUTTON"), Material.LIME_CONCRETE)
                        .with("enchantment", selected == null ? "none" : ItemNames.enchantment(selected.enchantment()))
                        .with("level", selected == null ? "" : Text.roman(selected.level()))
                        .build());

        ConfigurationSection nav = button("NAVIGATION-BUTTON");
        if (nav != null) {
            int total = totalPages(choices.size());
            Material arrow = Material.matchMaterial(nav.getString("MATERIAL", "ARROW"));
            if (arrow == null) arrow = Material.ARROW;
            if (page > 0) {
                inventory.setItem(slot("BACK-SLOT", 45), ItemBuilder.of(arrow)
                        .name(nav.getString("PREVIOUS-NAME", "Previous Page")).build());
            }
            if (page < total - 1) {
                inventory.setItem(slot("NEXT-SLOT", 53), ItemBuilder.of(arrow)
                        .name(nav.getString("NEXT-NAME", "Next Page")).build());
            }
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) return;
        int raw = event.getRawSlot();
        int total = totalPages(choices.size());

        if (raw == slot("BACK-SLOT", 45)) {
            if (page > 0) {
                page--;
                click();
                refresh();
            } else {
                deny();
            }
            return;
        }
        if (raw == slot("NEXT-SLOT", 53)) {
            if (page < total - 1) {
                page++;
                click();
                refresh();
            } else {
                deny();
            }
            return;
        }
        if (raw == slot("CANCEL-SLOT", 48)) {
            click();
            openLater(parent);
            return;
        }
        if (raw == slot("CONFIRM-SLOT", 50)) {
            if (selected == null) {
                deny();
                return;
            }
            parent.setSelected(book(selected));
            success();
            openLater(parent);
            return;
        }

        if (raw >= 0 && raw < itemsPerPage()) {
            List<Choice> slice = pageSlice(choices);
            if (raw >= slice.size()) return;
            selected = slice.get(raw);
            click();
            refresh();
        }
    }
}
