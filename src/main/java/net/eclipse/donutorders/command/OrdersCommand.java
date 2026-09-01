package net.eclipse.donutorders.command;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.menu.CollectItemsMenu;
import net.eclipse.donutorders.menu.OrdersMenu;
import net.eclipse.donutorders.menu.YourOrdersMenu;
import net.eclipse.donutorders.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrdersCommand implements CommandExecutor, TabCompleter {

    private final DonutOrders plugin;

    public OrdersCommand(DonutOrders plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("donutorders.admin")) {
                sender.sendMessage(Text.component(plugin.message("NO-PERMISSION")));
                return true;
            }
            plugin.reloadEverything();
            sender.sendMessage(Text.component(plugin.message("RELOADED")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.component(plugin.message("PLAYERS-ONLY")));
            return true;
        }
        if (!player.hasPermission("donutorders.use")) {
            player.sendMessage(Text.component(plugin.message("NO-PERMISSION")));
            return true;
        }
        if (!plugin.economy().isReady()) {
            player.sendMessage(Text.component(plugin.message("NO-ECONOMY")));
            return true;
        }

        if (args.length == 0) {
            new OrdersMenu(plugin, player).open();
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mine", "my" -> new YourOrdersMenu(plugin, player).open();
            case "collect" -> new CollectItemsMenu(plugin, player).open();
            case "sellall" -> plugin.orders().sellAll(player);
            default -> new OrdersMenu(plugin, player).open();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("mine");
            options.add("collect");
            options.add("sellall");
            if (sender.hasPermission("donutorders.admin")) options.add("reload");
            options.removeIf(option -> !option.startsWith(args[0].toLowerCase(Locale.ROOT)));
        }
        return options;
    }
}
