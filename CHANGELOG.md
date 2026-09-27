# Changelog

## 1.21.4-2.1.0

The release that turns the addon from "trades the villager in front of you" into "works a trading hall".
See [docs/SETTINGS.md](docs/SETTINGS.md) for the full reference.

### Added

**Movement** — the module walks to villagers instead of waiting for you to bring them into reach.

- `movement-mode`: `Off`, `Pathfind` (uses the installed path manager — Baritone or Voyager) or `Simple`
  (key-press steering, no dependencies). Pathing goes through Meteor's own path-manager abstraction, so there is
  **no compile-time dependency on Baritone** and the addon builds and runs with or without it.
- An **anchor** and a **leash radius** keep it inside your hall instead of following a wandering villager across
  the map. The anchor can be placed with a keybind and survives the module being toggled.
- **Manual override**: touching a movement key hands the controls straight back for a configurable window, so
  you can walk through your own hall while it trades.
- Travel timeout and an unreachable-villager cooldown, so it stops grinding against a wall when a villager is
  behind glass.
- Optional return-to-anchor when there is nothing left to do.

**Villager whitelist and blacklist** — per-villager control, stored by UUID so the lists survive relogs and
chunk reloads.

- Look at a villager and press a key to add or remove it; the GUI has the same buttons plus a per-list Clear.
- Each entry keeps a readable label (custom name, else profession and level) and the position it was standing at.
- Three modes: `Everyone`, `WhitelistOnly`, `PreferWhitelist` (whitelist first, then everyone else).
- The blacklist applies in every mode, adding to one list removes from the other, and a whitelisted villager is
  never rerolled.
- Whitelisted and blacklisted villagers in range are outlined in their own colours.

**Automatic shulker use** — your backpack becomes the module's stockroom, with no rules to configure.

- Derived entirely from the buy and sell rules: emeralds are fetched when low and stored when they pile up,
  sell stock is fetched when it runs low, purchases are stored when the inventory gets tight. Items no rule
  mentions are never touched, and an item that is both bought and sold is never stored.
- Jobs also come **directly from the trading loop**: a trade refused for want of emeralds, want of an item or
  want of room asks for exactly that item to be fetched or stored, instead of waiting for a threshold.
- A request is only kept while a carried shulker box can actually serve it, so it cannot loop.
- Shulker contents are read from the item itself, so the module knows what is inside before placing anything.

### Fixed

- **Trading was too slow to keep up while moving.** `interact-delay` (2, was 4), `ticks-to-close` (0, was 2) and
  `trade-delay` (1, was 2) now default to values that let the module start a new villager several times a
  second, and closing a screen no longer costs a full interaction cycle before the next villager is tried.
  `retry-delay` dropped from 20 ticks to 10.
- Villagers are now scanned every tick; only the clicking is paced.
- The module no longer starts walking away in the gap between an interaction and the screen opening.

### Changed

- Renamed for clarity: `ticks-to-wait` → `interact-delay`, `forget-after` → `revisit-delay`,
  `refresh-unsynced-cooldown` → `retry-unanswered`, `cancel-movement` → `freeze-while-trading`. The freeze
  settings now only appear with `movement-mode: Off`, since the other modes control where you stand.
- `priority` defaults to `LowestDistance`, which suits a hall better than `ClosestAngle`.
- The profession pre-check and the in-range scan are computed once per tick rather than per villager per call,
  and the derived shulker rules are cached for a few ticks.
- New `docs/SETTINGS.md`: every setting documented, with trading-hall presets and a troubleshooting section.

## 1.21.4-2.0.0

First release of this fork. Ported from the upstream 26.1.2 tree down to **Minecraft 1.21.4** (yarn mappings,
JDK 21, loom 1.9), with the bugs below fixed and the features below added. `README.md` explains each item in
more detail.

### Fixed

**Trading**

- The second ingredient of a trade was ignored; a "1 emerald + 1 book" trade was attempted without the book.
- Prices were read before demand and discount adjustments, so a trade that had become more expensive was still
  accepted at its base price.
- A buy price limit of `-1` made every buy trade look too expensive, so buy rules silently never fired.
- Affordability was never checked: owning one emerald counted as being able to pay for a 30-emerald trade.
- Every matching offer was selected and shift-clicked inside the packet handler in a single tick, against a
  stale inventory. Trades now run one at a time with the inventory re-read between them.
- Only one trade per offer per visit was ever executed, leaving stock behind.
- Results could be lost because inventory space was never checked.
- With `Close` turned off the tick handler returned early forever and the module stopped trading entirely.
- "Rules are empty" was printed on every trade-offers packet.
- `ticks-to-close` and `forget-after` were multiplied by `ticks-to-wait` instead of counting real ticks.

**Targeting**

- Villager profession detection was missing entirely (hardcoded to `"any"`), which made the built-in trade table
  dead code and matched every rule against every villager.
- The pre-check skipped its own limit checks for any item missing from the trade table.
- Villagers behind walls were clicked, rejected by the server, and then retried as "unsynced".
- Babies, nitwits and unemployed villagers were opened even though they cannot trade.

**Inventory automation**

- The entire "Inventory manipulation" tab did nothing while Villager-Aura was off.
- Dropping could send up to 128 packets in one tick and crafting up to 64 clicks per grid slot.
- `TriggerMinusLeave` mode could ask for more crafts than there were ingredients, failing the task.
- `clearGrid` reported a grid as emptied when the shift-click had not worked.
- The drop task did not hold its rotation across ticks, so items scattered.
- Rule lists from a previous profile survived a profile switch.

**Rendering and state**

- The villager map was copied wholesale every tick and every rendered frame.
- Entities were never dropped when they unloaded, so the map grew without bound.
- Render colours were attributed to whichever villager the aura last touched, including for screens the player
  opened by hand.
- A held movement key could stay suppressed after the module returned early, leaving the player unable to move.

### Added

- **Profession and level awareness** - real profession filtering, plus `min-level` / `max-level`.
- **Adjusted prices, both ingredients, exact affordability, inventory space and out-of-stock detection.**
- **Multi-trade per villager** with `max-trades-per-villager` and `trade-delay`.
- **Villager rerolling** - break and replace a workstation until the wanted trade appears, with guards against
  rerolling a villager whose offers are already locked.
- **Safety layer** - a shared packet rate limiter, pause on server lag, pause while using items, pause on low
  health, and optional auto-disable when idle.
- **Wandering trader support**, line-of-sight checks and busy-villager skipping.
- **Unit tests** for the trading decision logic and the rate limiter (`./gradlew test`).
