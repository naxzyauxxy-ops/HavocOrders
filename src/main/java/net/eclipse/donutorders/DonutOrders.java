package net.eclipse.donutorders;

import net.eclipse.donutorders.command.OrdersCommand;
import net.eclipse.donutorders.economy.EconomyHook;
import net.eclipse.donutorders.economy.SellPrices;
import net.eclipse.donutorders.manager.ItemCatalogue;
import net.eclipse.donutorders.manager.OrderManager;
import net.eclipse.donutorders.menu.MenuListener;
import net.eclipse.donutorders.storage.SqlStorage;
import net.eclipse.donutorders.util.ChatInput;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class DonutOrders extends JavaPlugin {

    private FileConfiguration menus;
    private File menuFile;

    private EconomyHook economy;
    private SellPrices sellPrices;
    private SqlStorage storage;
    private OrderManager orderManager;
    private ItemCatalogue catalogue;
    private ChatInput chatInput;

    private final Set<Material> blocked = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadMenus();
        loadBlockedItems();

        economy = new EconomyHook(this);
        if (!economy.setup()) {
            getLogger().severe("Disabling DonutOrders - Vault economy is required.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        sellPrices = new SellPrices(this);

        storage = new SqlStorage(this);
        try {
            storage.initialise();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "Could not initialise the database - disabling.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        orderManager = new OrderManager(this, storage);
        orderManager.loadAll();

        catalogue = new ItemCatalogue(this);
        catalogue.build();

        chatInput = new ChatInput(this);
        getServer().getPluginManager().registerEvents(chatInput, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        PluginCommand command = getCommand("orders");
        if (command != null) {
            OrdersCommand executor = new OrdersCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        long expiryTicks = Math.max(20L, getConfig().getInt("SETTINGS.EXPIRY-CHECK-SECONDS", 60) * 20L);
        getServer().getScheduler().runTaskTimer(this, () -> orderManager.tickExpiry(), expiryTicks, expiryTicks);

        long saveTicks = Math.max(20L, getConfig().getInt("SETTINGS.SAVE-INTERVAL-SECONDS", 300) * 20L);
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> orderManager.saveAllBlocking(), saveTicks, saveTicks);

        getLogger().info("DonutOrders enabled.");
    }

    @Override
    public void onDisable() {
        if (orderManager != null) orderManager.saveAllBlocking();
        if (storage != null) storage.close();
    }

    // ------------------------------------------------------------------ config

    public void reloadMenus() {
        menuFile = new File(getDataFolder(), "menu.yml");
        if (!menuFile.exists()) saveResource("menu.yml", false);
        menus = YamlConfiguration.loadConfiguration(menuFile);
    }

    private void loadBlockedItems() {
        blocked.clear();
        for (String raw : getConfig().getStringList("ITEM-RESTRICTIONS.BLOCKED-ITEMS")) {
            Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material == null) {
                getLogger().warning("Unknown blocked item: " + raw);
                continue;
            }
            blocked.add(material);
        }
    }

    public void reloadEverything() {
        reloadConfig();
        reloadMenus();
        loadBlockedItems();
        sellPrices.reload();
        catalogue.build();
    }

    public boolean isBlocked(Material material) {
        return material == null || material == Material.AIR || blocked.contains(material);
    }

    public ConfigurationSection menuSection(String path) {
        return menus.getConfigurationSection("MENUS." + path);
    }

    public ConfigurationSection globalItem(String key) {
        return menus.getConfigurationSection("MENUS.GLOBAL_ITEMS." + key);
    }

    public String message(String path) {
        String prefix = getConfig().getString("MESSAGES.PREFIX", "");
        String message = getConfig().getString("MESSAGES." + path, "");
        return message.isEmpty() ? "" : prefix + message;
    }

    // ------------------------------------------------------------------ accessors

    public EconomyHook economy() {
        return economy;
    }

    public SellPrices sellPrices() {
        return sellPrices;
    }

    public OrderManager orders() {
        return orderManager;
    }

    public ItemCatalogue catalogue() {
        return catalogue;
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    public void async(Runnable runnable) {
        if (!isEnabled()) {
            runnable.run();
            return;
        }
        getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }
}
