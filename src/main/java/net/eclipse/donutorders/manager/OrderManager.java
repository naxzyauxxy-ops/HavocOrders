package net.eclipse.donutorders.manager;

import net.eclipse.donutorders.DonutOrders;
import net.eclipse.donutorders.model.Order;
import net.eclipse.donutorders.model.OrderStatus;
import net.eclipse.donutorders.storage.SqlStorage;
import net.eclipse.donutorders.util.ItemNames;
import net.eclipse.donutorders.util.NumberUtil;
import net.eclipse.donutorders.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Owns every order in memory and is the only place that touches money or inventories. */
public class OrderManager {

    private final DonutOrders plugin;
    private final SqlStorage storage;
    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    public OrderManager(DonutOrders plugin, SqlStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    // ------------------------------------------------------------------ loading

    public void loadAll() {
        orders.clear();
        for (Order order : storage.loadAll()) {
            if (order.isFinished()) {
                plugin.async(() -> storage.delete(order.getId()));
                continue;
            }
            orders.put(order.getId(), order);
        }
        plugin.getLogger().info("Loaded " + orders.size() + " orders.");
    }

    public void saveAllBlocking() {
        storage.saveAll(new ArrayList<>(orders.values()));
    }

    private void persist(Order order) {
        plugin.async(() -> storage.save(order));
    }

    // ------------------------------------------------------------------ lookups

    public Order byId(UUID id) {
        return orders.get(id);
    }

    public List<Order> listed() {
        List<Order> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.isListed()) result.add(order);
        }
        return result;
    }

    public List<Order> ordersOf(UUID owner) {
        List<Order> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.getOwner().equals(owner) && !order.isFinished()) result.add(order);
        }
        result.sort(Comparator.comparingLong(Order::getCreatedAt));
        return result;
    }

    public long activeCount(UUID owner) {
        return orders.values().stream()
                .filter(order -> order.getOwner().equals(owner) && order.isListed())
                .count();
    }

    /** Orders belonging to the player that still have items waiting to be picked up. */
    public List<Order> collectable(UUID owner) {
        List<Order> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.getOwner().equals(owner) && order.getCollectable() > 0) result.add(order);
        }
        result.sort(Comparator.comparingLong(Order::getCreatedAt));
        return result;
    }

    // ------------------------------------------------------------------ creation

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public Result createOrder(Player player, ItemStack template, int amount, double unitPrice) {
        if (template == null || template.getType() == Material.AIR) {
            return Result.fail(plugin.message("NO-ITEM-SELECTED"));
        }
        if (plugin.isBlocked(template.getType())) {
            return Result.fail(plugin.message("BLOCKED-ITEM"));
        }

        int minAmount = plugin.getConfig().getInt("SETTINGS.MIN-ITEM-AMOUNT", 1);
        int maxAmount = plugin.getConfig().getInt("SETTINGS.MAX-ITEM-AMOUNT", 3456);
        double minPrice = plugin.getConfig().getDouble("SETTINGS.MIN-PRICE-AMOUNT", 1.0D);
        double maxPrice = plugin.getConfig().getDouble("SETTINGS.MAX-PRICE-AMOUNT", 1_000_000D);

        if (amount < minAmount) {
            return Result.fail(Text.apply(plugin.message("AMOUNT-TOO-LOW"), Map.of("min", String.valueOf(minAmount))));
        }
        if (amount > maxAmount) {
            return Result.fail(Text.apply(plugin.message("AMOUNT-TOO-HIGH"), Map.of("max", String.valueOf(maxAmount))));
        }
        if (unitPrice < minPrice) {
            return Result.fail(Text.apply(plugin.message("PRICE-TOO-LOW"), Map.of("min", NumberUtil.money(minPrice))));
        }
        if (unitPrice > maxPrice) {
            return Result.fail(Text.apply(plugin.message("PRICE-TOO-HIGH"), Map.of("max", NumberUtil.money(maxPrice))));
        }

        int maxOrders = plugin.getConfig().getInt("SETTINGS.MAX-ORDERS-PER-PLAYER", 10);
        if (!player.hasPermission("donutorders.admin") && activeCount(player.getUniqueId()) >= maxOrders) {
            return Result.fail(Text.apply(plugin.message("MAX_ORDERS_REACHED"), Map.of("max", String.valueOf(maxOrders))));
        }

        double total = amount * unitPrice;
        boolean escrow = plugin.getConfig().getBoolean("SETTINGS.ESCROW", true);
        if (escrow) {
            if (!plugin.economy().has(player, total)) {
                return Result.fail(Text.apply(plugin.message("NOT-ENOUGH-MONEY"), Map.of("total", NumberUtil.money(total))));
            }
            if (!plugin.economy().withdraw(player, total)) {
                return Result.fail(plugin.message("REFUND-ERROR"));
            }
        }

        long expiry = TimeUnit.DAYS.toMillis(plugin.getConfig().getInt("SETTINGS.EXPIRE-DAYS", 7));
        Order order = Order.create(player.getUniqueId(), player.getName(), template, amount, unitPrice, expiry);
        orders.put(order.getId(), order);
        persist(order);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("item", ItemNames.display(template));
        placeholders.put("total", NumberUtil.money(total));
        return Result.ok(Text.apply(plugin.message("ORDERED"), placeholders));
    }

    // ------------------------------------------------------------------ delivering

    /** How many matching items the player is carrying. */
    public int countMatching(Player player, Order order) {
        int found = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && order.matches(stack)) found += stack.getAmount();
        }
        return found;
    }

    /**
     * Delivers up to {@code requested} matching items from the player's inventory.
     * Returns the number actually delivered.
     */
    public int deliverFromInventory(Player player, Order order, int requested) {
        if (!validForDelivery(player, order)) return 0;

        int deliverable = Math.min(Math.min(requested, order.getRemaining()), countMatching(player, order));
        if (deliverable <= 0) {
            player.sendMessage(Text.component(plugin.message("NOTHING-TO-DELIVER")));
            return 0;
        }

        removeMatching(player.getInventory(), order, deliverable);
        applyDelivery(player, order, deliverable);
        return deliverable;
    }

    /** Used by the deposit-area deliver menu, where the items are already out of the inventory. */
    public int deliverStacks(Player player, Order order, List<ItemStack> deposited) {
        if (!validForDelivery(player, order)) return 0;

        int total = 0;
        for (ItemStack stack : deposited) {
            if (stack == null || !order.matches(stack)) continue;
            int take = Math.min(stack.getAmount(), order.getRemaining() - total);
            if (take <= 0) break;
            stack.setAmount(stack.getAmount() - take);
            total += take;
        }
        if (total <= 0) {
            player.sendMessage(Text.component(plugin.message("NOTHING-TO-DELIVER")));
            return 0;
        }
        applyDelivery(player, order, total);
        return total;
    }

    private boolean validForDelivery(Player player, Order order) {
        Order current = orders.get(order.getId());
        if (current == null) {
            player.sendMessage(Text.component(plugin.message("ORDER_DELETED")));
            return false;
        }
        if (current.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(Text.component(plugin.message("OWN-ORDER")));
            return false;
        }
        if (!current.getStatus().acceptsDeliveries() || current.isExpired()) {
            player.sendMessage(Text.component(plugin.message("ORDER_NO_LONGER_VALID")));
            return false;
        }
        if (current.getRemaining() <= 0) {
            player.sendMessage(Text.component(plugin.message("ORDER_FULL")));
            return false;
        }
        return true;
    }

    private void applyDelivery(Player deliverer, Order order, int quantity) {
        double payout = quantity * order.getUnitPrice();
        order.addDelivered(quantity);
        persist(order);

        boolean escrow = plugin.getConfig().getBoolean("SETTINGS.ESCROW", true);
        if (!escrow) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(order.getOwner());
            plugin.economy().withdraw(owner, payout);
        }
        plugin.economy().deposit(deliverer, payout);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(quantity));
        placeholders.put("item", order.getItemName());
        placeholders.put("received", NumberUtil.money(payout));
        placeholders.put("deliverer", deliverer.getName());
        deliverer.sendMessage(Text.component(Text.apply(plugin.message("DELIVERED"), placeholders)));

        Player owner = Bukkit.getPlayer(order.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(Text.component(Text.apply(plugin.message("DELIVERY_RECEIVED"), placeholders)));
        }
    }

    private void removeMatching(PlayerInventory inventory, Order order, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !order.matches(stack)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) contents[slot] = null;
            remaining -= take;
        }
        inventory.setStorageContents(contents);
    }

    // ------------------------------------------------------------------ collecting

    /** Gives the owner up to {@code requested} of the items waiting on this order. */
    public int collect(Player player, Order order, int requested) {
        if (!order.getOwner().equals(player.getUniqueId())) return 0;
        int available = Math.min(requested, order.getCollectable());
        if (available <= 0) {
            player.sendMessage(Text.component(plugin.message("COLLECT.NOTHING_TO_COLLECT")));
            return 0;
        }

        int given = give(player, order.getItem(), available);
        if (given <= 0) {
            player.sendMessage(Text.component(plugin.message("COLLECT.INVENTORY_FULL")));
            return 0;
        }
        order.addCollected(given);
        persistOrRemove(order);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(given));
        placeholders.put("item", order.getItemName());
        placeholders.put("collected", String.valueOf(given));
        placeholders.put("total", String.valueOf(available));
        if (given < available) {
            player.sendMessage(Text.component(Text.apply(plugin.message("COLLECT.PARTIAL_COLLECTION"), placeholders)));
        } else {
            player.sendMessage(Text.component(Text.apply(plugin.message("COLLECT.SUCCESS"), placeholders)));
        }
        return given;
    }

    public int collectAll(Player player) {
        int total = 0;
        for (Order order : collectable(player.getUniqueId())) {
            int given = give(player, order.getItem(), order.getCollectable());
            if (given <= 0) continue;
            order.addCollected(given);
            persistOrRemove(order);
            total += given;
        }
        if (total == 0) {
            player.sendMessage(Text.component(plugin.message("COLLECT.NOTHING_TO_COLLECT")));
        }
        return total;
    }

    /** Drops everything waiting for the player at their feet. */
    public int dropAll(Player player, List<Order> subset) {
        int total = 0;
        for (Order order : subset) {
            int quantity = order.getCollectable();
            if (quantity <= 0) continue;
            ItemStack template = order.getItem();
            int remaining = quantity;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, template.getMaxStackSize());
                ItemStack drop = template.clone();
                drop.setAmount(stackSize);
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                remaining -= stackSize;
            }
            order.addCollected(quantity);
            persistOrRemove(order);
            total += quantity;
        }
        return total;
    }

    private int give(Player player, ItemStack template, int quantity) {
        int given = 0;
        int remaining = quantity;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, template.getMaxStackSize());
            ItemStack stack = template.clone();
            stack.setAmount(stackSize);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            int notGiven = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            given += stackSize - notGiven;
            if (notGiven > 0) break;
            remaining -= stackSize;
        }
        return given;
    }

    // ------------------------------------------------------------------ selling

    public record SellPreview(int sellableItems, int unsellableItems, double total) {
    }

    public SellPreview previewSell(Player player) {
        int sellable = 0;
        int unsellable = 0;
        double total = 0.0D;
        for (Order order : collectable(player.getUniqueId())) {
            int quantity = order.getCollectable();
            double unit = plugin.sellPrices().unitPrice(order.getItem());
            if (unit <= 0) {
                unsellable += quantity;
            } else {
                sellable += quantity;
                total += unit * quantity;
            }
        }
        return new SellPreview(sellable, unsellable, total);
    }

    /** Sells every collectable item the player owns. Returns the money paid out. */
    public double sellAll(Player player) {
        if (!plugin.sellPrices().isEnabled()) {
            player.sendMessage(Text.component(plugin.message("SELL-DISABLED")));
            return 0.0D;
        }

        double total = 0.0D;
        int sold = 0;
        for (Order order : collectable(player.getUniqueId())) {
            int quantity = order.getCollectable();
            double unit = plugin.sellPrices().unitPrice(order.getItem());
            if (unit <= 0 || quantity <= 0) continue;
            total += unit * quantity;
            sold += quantity;
            order.addCollected(quantity);
            persistOrRemove(order);
        }

        if (sold == 0) {
            player.sendMessage(Text.component(plugin.message("SELL-NOTHING")));
            return 0.0D;
        }

        plugin.economy().deposit(player, total);
        player.sendMessage(Text.component(Text.apply(plugin.message("SELL_ALL_SOLD"),
                Map.of("total", NumberUtil.money(total), "amount", String.valueOf(sold)))));
        return total;
    }

    // ------------------------------------------------------------------ cancelling / expiry

    public Result cancel(Player player, Order order) {
        Order current = orders.get(order.getId());
        if (current == null) return Result.fail(plugin.message("ORDER_DELETED"));
        if (!current.getOwner().equals(player.getUniqueId()) && !player.hasPermission("donutorders.admin")) {
            return Result.fail(plugin.message("NO-PERMISSION"));
        }
        if (current.getCollectable() > 0) {
            return Result.fail(Text.apply(plugin.message("CANCEL-NOT-ALLOWED-PENDING-COLLECTION"),
                    Map.of("amount", String.valueOf(current.getCollectable()))));
        }

        double refund = current.getRefund();
        current.setStatus(OrderStatus.CANCELLED);

        if (plugin.getConfig().getBoolean("SETTINGS.ESCROW", true) && refund > 0) {
            if (!plugin.economy().deposit(player, refund)) {
                return Result.fail(plugin.message("REFUND-ERROR"));
            }
            player.sendMessage(Text.component(Text.apply(plugin.message("REFUND-ISSUED"),
                    Map.of("refund", NumberUtil.money(refund)))));
        } else if (refund <= 0) {
            player.sendMessage(Text.component(plugin.message("NO-REFUND")));
        }

        persistOrRemove(current);
        return Result.ok(plugin.message("ORDER-CANCELLED"));
    }

    /** Runs on a timer: refunds and closes anything past its expiry. */
    public void tickExpiry() {
        for (Order order : new ArrayList<>(orders.values())) {
            if (order.getStatus() == OrderStatus.ACTIVE && order.isExpired()) {
                double refund = order.getRefund();
                order.setStatus(OrderStatus.EXPIRED);
                if (plugin.getConfig().getBoolean("SETTINGS.ESCROW", true) && refund > 0) {
                    plugin.economy().deposit(Bukkit.getOfflinePlayer(order.getOwner()), refund);
                }
                Player owner = Bukkit.getPlayer(order.getOwner());
                if (owner != null) {
                    owner.sendMessage(Text.component(Text.apply(plugin.message("EXPIRED"),
                            Map.of("amount", String.valueOf(order.getRemaining()), "item", order.getItemName()))));
                }
                persistOrRemove(order);
            } else if (order.isFinished()) {
                persistOrRemove(order);
            }
        }
    }

    /** Saves the order, or drops it from memory and storage once it is fully settled. */
    private void persistOrRemove(Order order) {
        if (order.isFinished()) {
            orders.remove(order.getId());
            plugin.async(() -> storage.delete(order.getId()));
        } else {
            persist(order);
        }
    }
}
