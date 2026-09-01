package net.eclipse.donutorders.manager;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.util.ItemNames;
import net.eclipse.donutorders.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Every stack a player is allowed to order, built once on enable and on reload. */
public class ItemCatalogue {

    public record Entry(ItemStack stack, String name, boolean enchantedBook) {
    }

    private final DonutOrders plugin;
    private final List<Entry> entries = new ArrayList<>();

    public ItemCatalogue(DonutOrders plugin) {
        this.plugin = plugin;
    }

    public List<Entry> entries() {
        return entries;
    }

    public void build() {
        entries.clear();
        ConfigurationSection suffixes = plugin.menuSection("SELECT-ITEM-MENU") == null
                ? null
                : plugin.menuSection("SELECT-ITEM-MENU").getConfigurationSection("POTION-SUFFIXES");

        for (Material material : Material.values()) {
            if (material.isLegacy() || material == Material.AIR || !material.isItem()) continue;
            if (plugin.isBlocked(material)) continue;

            if (material == Material.POTION || material == Material.SPLASH_POTION
                    || material == Material.LINGERING_POTION) {
                String suffix = suffixes == null ? defaultSuffix(material) : suffixes.getString(material.name(), defaultSuffix(material));
                for (PotionType type : PotionType.values()) {
                    ItemStack stack = new ItemStack(material);
                    if (stack.getItemMeta() instanceof PotionMeta meta) {
                        meta.setBasePotionType(type);
                        stack.setItemMeta(meta);
                    }
                    entries.add(new Entry(stack, Text.pretty(type.name()) + suffix, false));
                }
                continue;
            }

            ItemStack stack = new ItemStack(material);
            entries.add(new Entry(stack, ItemNames.display(stack), material == Material.ENCHANTED_BOOK));
        }

        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        plugin.getLogger().info("Item picker loaded with " + entries.size() + " entries.");
    }

    private String defaultSuffix(Material material) {
        return switch (material) {
            case SPLASH_POTION -> " (Splash)";
            case LINGERING_POTION -> " (Lingering)";
            default -> "";
        };
    }
}
