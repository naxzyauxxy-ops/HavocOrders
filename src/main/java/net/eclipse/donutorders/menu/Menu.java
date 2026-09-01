package net.eclipse.donutorders.menu;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class Menu implements InventoryHolder {

    protected final DonutOrders plugin;
    protected final Player player;
    protected Inventory inventory;

    protected Menu(DonutOrders plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Config section for this menu, e.g. "ORDERS-MENU". */
    protected abstract String configPath();

    protected ConfigurationSection section() {
        return plugin.menuSection(configPath());
    }

    protected ConfigurationSection buttons() {
        ConfigurationSection section = section();
        if (section == null) return null;
        ConfigurationSection nested = section.getConfigurationSection("BUTTONS");
        return nested != null ? nested : section;
    }

    protected ConfigurationSection button(String key) {
        ConfigurationSection buttons = buttons();
        return buttons == null ? null : buttons.getConfigurationSection(key);
    }

    public String title() {
        ConfigurationSection section = section();
        return section == null ? "Menu" : section.getString("TITLE", "Menu");
    }

    public int size() {
        ConfigurationSection section = section();
        int size = section == null ? 27 : section.getInt("SIZE", 27);
        if (size % 9 != 0 || size < 9 || size > 54) size = 27;
        return size;
    }

    protected abstract void decorate();

    public void open() {
        inventory = Bukkit.createInventory(this, size(), Text.component(title()));
        decorate();
        player.openInventory(inventory);
    }

    public void refresh() {
        if (inventory == null || inventory.getSize() != size()) {
            open();
            return;
        }
        inventory.clear();
        decorate();
        player.updateInventory();
    }

    public abstract void onClick(InventoryClickEvent event);

    public void onClose(InventoryCloseEvent event) {
    }

    /** When true the listener cancels every click before handing it over. */
    public boolean cancelClicksByDefault() {
        return true;
    }

    /** Slots in the top inventory the player is allowed to put items into. */
    public boolean isDepositSlot(int slot) {
        return false;
    }

    protected void click() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5F, 1.2F);
    }

    protected void success() {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.0F);
    }

    protected void deny() {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.6F);
    }

    /** Opens another menu on the next tick - safer than swapping inventories mid-click. */
    protected void openLater(Menu menu) {
        Bukkit.getScheduler().runTask(plugin, menu::open);
    }

    protected void tell(String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(Text.component(message));
    }

    protected int slot(String key, int fallback) {
        ConfigurationSection section = section();
        if (section == null) return fallback;
        int value = section.getInt(key, fallback);
        return value >= 0 && value < size() ? value : fallback;
    }

    protected int buttonSlot(String key, int fallback) {
        ConfigurationSection button = button(key);
        if (button == null) return fallback;
        int value = button.getInt("SLOT", fallback);
        return value >= 0 && value < size() ? value : fallback;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
