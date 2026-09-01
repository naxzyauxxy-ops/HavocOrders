package net.eclipse.donutorders.util;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.eclipse.donutorders.DonutOrders;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Chat-based text input for the amount / price / search prompts.
 *
 * The SIGN-TITLE values in menu.yml are used as the prompt label. If you later move
 * to the 1.21.6+ Dialog API you can swap this class out without touching the menus.
 */
public final class ChatInput implements Listener {

    private record Prompt(Consumer<String> onInput, Runnable onCancel) {
    }

    private final DonutOrders plugin;
    private final Map<UUID, Prompt> waiting = new ConcurrentHashMap<>();

    public ChatInput(DonutOrders plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String label, Consumer<String> onInput, Runnable onCancel) {
        waiting.put(player.getUniqueId(), new Prompt(onInput, onCancel));
        player.closeInventory();
        player.sendMessage(Text.component("&#f40d0d" + label + "&f: type a value in chat, or 'cancel'."));
    }

    public boolean isWaiting(Player player) {
        return waiting.containsKey(player.getUniqueId());
    }

    public void clear(Player player) {
        waiting.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = waiting.remove(player.getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(Text.component(plugin.message("INPUT-CANCELLED")));
                if (prompt.onCancel() != null) prompt.onCancel().run();
                return;
            }
            prompt.onInput().accept(message);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
    }
}
