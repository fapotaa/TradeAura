# TradeAura

A [Meteor Client](https://meteorclient.com) addon that automates villager trading on **Minecraft 1.21.4**.

Point it at a trading hall, tell it what you want to buy and sell, and it works the hall for you — walking
between villagers, trading until they are out of stock, fetching emeralds out of your shulker boxes when it runs
low, putting purchases away when the inventory fills up, and rerolling villagers until the trade you want shows
up.

Rewritten fork of [DortyTheGreat/TradeAura](https://github.com/DortyTheGreat/TradeAura). Upstream moved on to
1.21.11 and 26.1.2, and its last real 1.21.4 release only had the core trading loop. This fork brings the whole
feature set back to 1.21.4, fixes the bugs listed at the bottom, and adds movement, villager lists, automatic
shulker use, profession-aware targeting, rerolling and a packet rate limiter.

| Minecraft | This addon | Meteor build |
| --- | --- | --- |
| 1.21.4 | `TradeAura-1.21.4-2.1.0` | `meteor-client-1.21.4-18.jar` or newer 1.21.4 build |

**📖 [Full settings reference →](docs/SETTINGS.md)** — every setting, what it does, when to change it, plus
ready-made presets for a trading hall and a troubleshooting section.

---

## Quick start

1. Drop the jar in `mods` next to Fabric Loader and Meteor Client for 1.21.4.
2. Open the **Trade-Aura** module (category *TradeAura*) and click the GUI panel.
3. Add a **Buy Rule**: pick your items, set `Max Price`, set `Buy Limit` (`-1` = no limit).
4. Add a **Sell Rule** the same way if you want to sell.
5. Turn on **villager-aura**.
6. Optional but recommended: set **movement-mode** to `Pathfind` and stand in the middle of your hall when you
   switch the module on.

Turn on **render** while you are setting things up — every villager gets outlined in a colour that says exactly
what the module decided about it.

---

## What it does

### Trades properly

Reads the price the trading screen actually shows — demand increases and discounts included — checks both
ingredients of a trade, checks you can really afford it, checks the result will fit, and keeps trading with one
villager until it is out of stock or one of your limits is reached. One trade per pass, with the inventory
re-read between them, so nothing is decided against a stale snapshot.

### Walks the hall

`movement-mode: Pathfind` uses whatever path manager you have installed (Baritone or Voyager) to walk to the
next villager worth visiting, stop in range, trade, and move on. There is **no compile-time dependency on
Baritone** — the addon builds and runs with or without it, and falls back to simple steering when nothing is
installed.

A **leash radius** around an anchor keeps it inside your hall instead of following a wandering villager across
the map, and the moment you touch a movement key it hands the controls straight back to you.

### Uses your shulker boxes

Your buy and sell rules already say what belongs in a shulker box, so **no extra rules are needed**:

- Out of emeralds in front of a librarian → fetches emeralds out of a box.
- Out of wheat in front of a farmer → fetches your sell stock.
- Inventory full → puts the purchases away.

Reading a shulker box costs nothing and needs no interaction, so the module always knows what is inside before
it places anything down. Jobs come both from thresholds and directly from the trading loop: a trade that was
just refused for want of emeralds asks for emeralds *now*, rather than waiting for a number to be crossed. Items
no rule mentions are never touched.

### Picks its villagers

- **Profession and level aware.** A librarian is not opened when every rule you wrote is about wool.
- **Whitelist and blacklist**, by UUID so they survive relogs and chunk reloads. Look at a villager, press a
  key. Three modes: trade with everyone, only the ones you picked, or the ones you picked first and then
  everyone else.
- Skips babies, nitwits, unemployed villagers, villagers another player is already using, and anything you
  cannot actually see.

### Rerolls villagers

Breaks and replaces a workstation until the trade you configured appears — the enchanted-book trading-hall
routine, automated. It refuses to touch a villager that has already been traded with, because one completed
trade locks a villager's offers forever, and it will not reroll over a problem a reroll cannot fix.

### Stays out of trouble

One shared packet budget for trades, inventory clicks, crafting, shulker transfers and drops. Pauses on server
lag, while you are eating or mining, and on low health.

---

## Building

Requires JDK 21.

```bash
./gradlew build
```

The jar lands in `build/libs/TradeAura-1.21.4-2.1.0.jar`.

```bash
./gradlew test
```

The trading decision logic, the targeting modes and the rate limiter are written without any Minecraft
references specifically so they can be unit tested; `src/test/java` covers the rules that decide whether
emeralds get spent.

### Where to look in the code

| File | What it holds |
| --- | --- |
| `trading/TradeEvaluator.java` | The whole "should I make this trade" decision, with no Minecraft references. |
| `trading/VillagerUtil.java` | Everything version-specific about reading a villager, including the profession lookup. |
| `trading/VillagerList.java` | The UUID-keyed whitelist / blacklist. |
| `trading/RerollTask.java` | The workstation break/replace reroll loop. |
| `nav/Navigation.java` | Pathing, the leash and the manual override. |
| `inventory/AutoShulker.java` | Derives shulker jobs from the trade rules. |
| `modules/TradeAura.java` | Settings, GUI, the aura loop and the trading-screen state machine. |
| `safety/RateLimiter.java` | The shared packet budget. |

---

## Bugs fixed from upstream

All real defects in the upstream code, not style changes.

<details>
<summary><b>Trading</b></summary>

| | What went wrong |
| --- | --- |
| **The second ingredient was ignored** | A trade costing "1 emerald + 1 book" was treated as costing one emerald and attempted without checking you had the book. |
| **Prices were read before adjustment** | The raw first ingredient was used, so a trade whose price had risen through demand was still accepted at its base price. Now the value the trading screen actually shows is used. |
| **Buy rules could never use "no price limit"** | `-1` was special-cased for the buy *limit* but not the *price*, so a price limit of -1 made `price > -1` true for everything and the rule silently never fired. |
| **Affordability was never checked** | Owning *at least one* emerald counted as being able to pay for a 30-emerald trade. |
| **Every matching offer fired in one tick** | Selection and shift-click were sent for every matching offer inside the packet handler, against an inventory that could not have updated in between. Source of the double spends and ghost items. |
| **Only one trade per offer per visit** | The villager was left with stock on the counter. |
| **No inventory-space check** | Results could be lost when the inventory was full. |
| **`Close` off froze the module** | The tick handler returned early forever and the module never traded again. |
| **"Rules are empty" spammed chat** | Printed on every trade-offers packet. |
| **Close delay was multiplied** | `ticks-to-close` and `forget-after` were multiplied by `ticks-to-wait` instead of counting real ticks. |

</details>

<details>
<summary><b>Targeting</b></summary>

| | What went wrong |
| --- | --- |
| **Profession detection was missing entirely** | Hardcoded to `"any"` with the comment *"idk how to get the profession, reflection doesn't seem to work"*. The whole built-in trade table was dead code and every villager matched every rule. No reflection is needed — `VillagerEntity` implements `VillagerDataContainer`, and in 1.21.4 `VillagerProfession` is a record whose `id()` is exactly the string the table is keyed by. |
| **The pre-check skipped its own limit checks** | For an item missing from the trade table it set `anyValidTrade = true` and broke out *before* the limit check. |
| **No line-of-sight check** | Villagers behind walls were clicked, rejected, and then retried as "unsynced" — a loop that produced nothing but packets. |
| **Babies, nitwits and unemployed villagers were opened** | None of them can trade. |
| **Wandering traders were unreachable** | They are merchants too. |

</details>

<details>
<summary><b>Inventory automation</b></summary>

| | What went wrong |
| --- | --- |
| **The whole tab did nothing with the aura off** | `invManager.tick()` was only called inside `if (aura.get() && ...)`. |
| **Packet bursts** | Dropping could send up to 128 throw packets in one tick, and filling a crafting grid up to 64 clicks per slot. |
| **Crafting could ask for more than it had** | The surplus was not clamped to what was in the inventory, so the task failed and put the trigger on cooldown. |
| **`clearGrid` reported success on failure** | It flagged leftovers before re-reading the slot. |
| **The drop task did not hold its rotation** | Items scattered across follow-up ticks. |
| **Rule lists survived a profile switch** | Missing tags left the previous profile's rules in place. |

</details>

<details>
<summary><b>Rendering and state</b></summary>

| | What went wrong |
| --- | --- |
| **Two `HashMap` copies per tick and per frame** | The cooldown map was copied wholesale every tick and every frame. |
| **Entities leaked** | Entries were only removed by the age counter, which did not run when entities unloaded. |
| **Colours were attributed to the wrong villager** | `remember_entity` was whatever the aura last touched, including screens the player opened by hand. |
| **Movement keys could stay suppressed** | A held key stayed released after the module returned early, leaving the player unable to move. |

</details>

---

## Notes on use

- This is a client modification that automates interactions. Many servers forbid that. Check the rules of the
  server you play on before turning it on.
- `range` above roughly 4.5 blocks is wasted: the server rejects interactions beyond your reach, and every
  rejected interaction costs a packet.
- Rerolling only works on villagers that have never completed a trade with anyone.

## License

CC0, same as upstream. Original work by [Dorty](https://github.com/DortyTheGreat).
