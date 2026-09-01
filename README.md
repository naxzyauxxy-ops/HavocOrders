# DonutOrders

A player-driven buy-order plugin for **Paper / Purpur 1.21.x**. Players post orders ("I'll pay $12
each for 512 diamonds"), anyone can deliver items into those orders and get paid instantly, and the
order owner collects — or sells — the loot from a menu.

Every menu is driven by `menu.yml`, in the same format as the sample you provided.

## Features

- **Order board** (`/orders`) with pagination, four sort modes, nine category filters, and search.
- **Deliver menu** — drop items into the deposit area and confirm, or shift-click an order on the
  board to deliver everything matching in one go. Anything left in the deposit area is returned
  when the menu closes.
- **Your Orders / Edit Order / Cancel Order** flows with proportional refunds.
- **New Order** builder: item picker (every non-blocked item, plus every potion variant), an
  enchantment picker for enchanted books, amount and price prompts.
- **Collect Items** menu with per-stack collect, Collect All, Drop Loot, and **Sell All**.
- **Vault economy** for every transaction, with escrow: the order value is taken up front and
  paid out to deliverers, and whatever is unspent is refunded on cancel or expiry.
- **SQLite (default) or MySQL** storage, all writes off the main thread.
- Orders expire after `SETTINGS.EXPIRE-DAYS` and auto-refund.

## Build

```
mvn clean package
```

The jar lands in `target/DonutOrders-1.0.0.jar`. Requires JDK 21.

## Install

1. Drop the jar in `plugins/`.
2. Install **Vault** plus an economy provider (EssentialsX Economy, CMI, etc). The plugin
   disables itself without one.
3. Start the server once, then edit `config.yml` and `menu.yml`.
4. `/orders reload` applies changes.

## Commands & permissions

| Command | Description |
| --- | --- |
| `/orders` | Open the order board |
| `/orders mine` | Your orders |
| `/orders collect` | Collect / sell menu |
| `/orders sellall` | Sell everything waiting, no menu |
| `/orders reload` | Reload config and menus |

`donutorders.use` (default true), `donutorders.admin` (default op — reload, bypasses the order limit,
can cancel anyone's order).

## Sell All

`SELL.PRICES` in `config.yml` sets the per-item sell value. Anything without an entry falls back to
`SELL.DEFAULT-PRICE`; a value of `0` means the item cannot be sold and is left in the collect menu.
`SELL.MULTIPLIER` scales everything, which is handy for sales or rank perks.

## Notes

- **Amount / price / search prompts use chat**, not signs. The `SIGN-TITLE` keys in `menu.yml` are
  reused as the prompt label so your config stays compatible. If you're on 1.21.6+ and want real
  popup inputs, `util/ChatInput.java` is the only class you'd need to swap for the Paper Dialog API.
- Items are stored with the native `ItemStack#serializeAsBytes` serialiser, so saved orders survive
  Minecraft version upgrades.
- Cancelling is blocked while an order still has items waiting to be collected, matching the
  `CANCEL-NOT-ALLOWED-PENDING-COLLECTION` message.

## Layout

```
net.eclipse.donutorders
├── DonutOrders            plugin entry point, config access
├── command/               /orders
├── economy/               Vault hook, sell prices
├── manager/               OrderManager (all money + inventory logic), ItemCatalogue
├── menu/                  Menu base, PagedMenu, listener, one class per screen
├── model/                 Order, OrderStatus, SortOption
├── storage/               SqlStorage (SQLite / MySQL)
└── util/                  Text (hex colours), ItemBuilder, ChatInput, Category, ...
```
