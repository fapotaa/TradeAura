# TradeAura — full settings reference

Every setting the module has, what it actually does, and when you would change it.
Defaults are in **bold**. If you only read one section, read [Trading-hall presets](#trading-hall-presets).

- [Rules (the GUI tables)](#rules-the-gui-tables)
- [General](#general)
- [Aura](#aura)
- [Movement](#movement)
- [Targeting](#targeting)
- [Villager lists](#villager-lists)
- [Rerolling](#rerolling)
- [Safety](#safety)
- [Inventory manipulation](#inventory-manipulation)
- [Render](#render)
- [Trading-hall presets](#trading-hall-presets)
- [Troubleshooting](#troubleshooting)

---

## Rules (the GUI tables)

These are not toggles in the settings list — they are the tables inside the module's GUI panel. Everything else
in this document exists to serve them.

### Buy Rules — what to buy from villagers

| Column | Meaning |
| --- | --- |
| **Items** | Which items this row is about. A row with no items does nothing. |
| **Max Price** | The highest number of emeralds to pay for one trade. `-1` means no limit. |
| **Buy Limit** | Stop buying once your inventory holds this many. `-1` means no limit. |

The price is the number the trading screen actually shows — demand increases and hero-of-the-village or
cured-villager discounts are already applied. If a trade needs two ingredients and one of them is emeralds, the
emerald count is the price; if it needs no emeralds at all, the count of the first ingredient is used instead.

### Sell Rules — what to sell to villagers

| Column | Meaning |
| --- | --- |
| **Items** | Which items to hand over. |
| **Max Sell Qty** | The largest quantity to give up in one trade. `-1` means no limit. A farmer who wants 26 wheat per emerald is a worse deal than one who wants 20; this is how you refuse the bad one. |
| **Emerald Limit** | Stop selling once you hold this many emeralds. `-1` means no limit. |

### Whitelist / Blacklist

Two tables of individual villagers, each with a button that adds whatever your crosshair is on. See
[Villager lists](#villager-lists).

### Drop / Dump / Refill Rules

Only appear when the matching toggle in *Inventory manipulation* is on. With **auto-shulker** enabled you do not
need Dump or Refill rules at all — see [Inventory manipulation](#inventory-manipulation).

---

## General

| Setting | Default | What it does |
| --- | --- | --- |
| **debug** | **off** | Prints a line for every offer, every target decision and every shulker job. Noisy, and the fastest way to find out why something is not being traded. Turn it on first whenever something looks wrong. |
| **close-screen** | **on** | Close the trading screen once there is nothing left to trade. Turning it off makes the module stop working that villager and wait for you — useful for inspecting what it saw, useless for actual trading. |
| **cancel-event** | **on** | Do not render the trading window. The trades still happen. With the aura working a hall this is the difference between a usable screen and a strobe light. |
| **ticks-to-close** | **0** | Ticks to wait before closing a finished screen. 0 is right while moving. Raise it only if your server dislikes fast open/close cycles. |
| **max-trades-per-villager** | **12** | How many trades to run with one villager before moving on. Low values spread your buying across the hall; high values drain one villager completely before walking on. The villager is revisited later either way. |
| **trade-delay** | **1** | Ticks between two trades with the same villager. 1 is the minimum, because the inventory the next decision reads has to be the one the server just confirmed. Raise to 2–4 on a laggy server. |
| **offers-timeout** | **40** | How long to hold a trading screen open waiting for the server's trade list before giving up on that villager. |

---

## Aura

| Setting | Default | What it does |
| --- | --- | --- |
| **villager-aura** | **off** | The master switch for automatic interaction. With it off, the module still completes trades on villagers *you* click — the manual auto-trade mode. |
| **interact-delay** | **2** | Ticks between two villager interactions. **This is the single biggest lever on how fast the module works a hall.** At 2 it can start a new villager ten times a second; at 10 you have to walk slowly for it to keep up. Villagers are scanned every tick regardless — this only paces the clicking. |
| **rotate-to-villager** | **on** | Look at the villager before interacting. The click is sent from inside the rotation callback, so the server already sees the new angle when it arrives. Turning this off is marginally faster and far more obvious to anti-cheat. |
| **revisit-delay** | **40** | Ticks before a villager the module already handled is considered again. Too low and it re-opens the same villager forever; too high and it ignores one that has since restocked. |
| **retry-unanswered** | **on** | If a villager never sent its trade list, interact again instead of giving up. Interactions genuinely do get eaten, especially while moving. |
| **retry-delay** | **10** | Ticks to give the server to answer before retrying. |
| **max-retries** | **3** | How often the same villager may be retried in a row before it is left alone until the revisit delay expires. Stops the module hammering a villager that never answers. |

---

## Movement

This is the group that makes the module work a large hall instead of one villager.

| Setting | Default | What it does |
| --- | --- | --- |
| **movement-mode** | **Off** | `Off` — never move you; trade only with what is in reach. `Pathfind` — use the installed path manager (Baritone or Voyager) to walk to villagers; handles stairs, doors, gaps and obstacles. `Simple` — press the movement keys towards the target and jump at obstacles; no dependencies, fine in an open flat hall, useless in a maze. |
| **search-radius** | **24** | How far to look for the next villager to walk to. Trading still only happens inside `range`; this is purely how far the module is willing to travel. |
| **stop-distance** | **3.0** | Stop walking once this close to the villager. Keep it a little under `range` so the villager is comfortably in reach on arrival instead of right on the edge. |
| **leash-radius** | **48** | Never walk further than this from the anchor. **This is what keeps the module inside your hall** instead of following a wandering villager across the map. `0` removes the leash entirely. |
| **anchor-on-enable** | **on** | Set the anchor to where you are standing when the module is switched on. Turn it off to keep an anchor you placed by hand. |
| **set-anchor-key** | unbound | Sets the anchor to where you are standing right now. Bind this and stand in the middle of your hall. |
| **return-to-anchor** | **off** | Walk back to the anchor when there is nothing left to do, instead of standing wherever the last villager happened to be. |
| **repath-interval** | **10** | Minimum ticks between two path requests for the same villager. Re-issuing a goal every tick makes a pathfinder recalculate constantly and you stutter in place. Only used in `Pathfind`. |
| **travel-timeout** | **120** | Give up on a villager after this many ticks of walking. Stops the module grinding against a wall when a villager is behind glass. |
| **unreachable-cooldown** | **600** | How long a villager it could not reach is ignored before being tried again. |
| **manual-override-ticks** | **20** | The moment you touch a movement key the module lets go of the controls for this many ticks. **This is what lets you walk through your own hall while it trades**, instead of fighting it for the keyboard. `0` disables the override. |

### Baritone

`Pathfind` mode goes through Meteor's own path-manager abstraction, so the addon has **no compile-time
dependency on Baritone** — it builds and runs with or without it. If Baritone (or Voyager) is installed, it is
used automatically. If neither is, the module says so when you switch it on and falls back to `Simple` steering.

### Freeze settings (only with `movement-mode: Off`)

Superseded by navigation; they exist for people who want to stand in one spot and not drift out of range.

| Setting | Default | What it does |
| --- | --- | --- |
| **freeze-while-trading** | **off** | Block your own movement input while a villager the aura can click is in range. Never triggers when there is nothing to trade with. |
| **freeze-window** | **2** | Freeze only when fewer than this many ticks remain until the next interaction. Keep it clearly below `interact-delay`, or every tick falls inside the window and you are frozen the whole time. |
| **freeze-whole-time** | **off** | Freeze the entire time a villager is in range rather than only inside the window above. |

Only the *input* is suppressed — movement packets keep being sent exactly as usual, so nothing desyncs.

---

## Targeting

| Setting | Default | What it does |
| --- | --- | --- |
| **priority** | **LowestDistance** | How to pick a villager when several are in reach. `ClosestAngle` favours whatever you are already looking at; `LowestDistance` the nearest one. |
| **range** | **4.5** | Maximum distance at which a villager is interacted with. The server rejects interactions beyond your reach, so anything much above 4.5 only wastes packets and produces failed clicks. |
| **max-targets** | **128** | How many entities to consider per scan. Only worth lowering on a machine that struggles in a village with hundreds of villagers. |
| **require-line-of-sight** | **on** | Skip villagers you cannot actually see. Clicking through a wall is always rejected, and every rejected click then gets retried as an unanswered villager. Turn off only for trading cells where the villager is behind a block you can still reach past. |
| **profession-filter** | **on** | Use the villager's real profession to skip ones that can never offer what you configured, so a librarian is not opened when every rule is about wool. Items the built-in table does not know are always allowed through, so modded and data-pack trades are never skipped by mistake. |
| **skip-babies-and-nitwits** | **on** | Skip babies, nitwits and unemployed villagers. None of them can trade. |
| **skip-busy-villagers** | **on** | Skip villagers another player is already trading with; the server refuses the interaction anyway. |
| **wandering-traders** | **off** | Also trade with wandering traders. They have no profession, so the profession filter never applies to them and they are never rerolled. |
| **min-level** / **max-level** | **1** / **5** | Only trade with villagers in this level band (1 novice … 5 master). Useful to ignore fresh villagers whose good trades are still locked. |

---

## Villager lists

Two lists of individual villagers, stored by UUID. A UUID is the only villager identity that is both visible to
the client and stable across relogs and chunk reloads — an entity's network id changes every time the chunk
unloads, and a position is only stable for a villager locked in a cell. Each entry also stores a readable label
and the position it was standing at when you added it, so the list in the GUI is something a human can read.

| Setting | Default | What it does |
| --- | --- | --- |
| **target-mode** | **Everyone** | `Everyone` — trade with anything not blacklisted; the whitelist is ignored. `WhitelistOnly` — trade *only* with the villagers you picked. `PreferWhitelist` — work through the whitelist first, then fall back to everyone else. |
| **whitelist-key** | unbound | Look at a villager, press the key: it is added, or removed if already on the list. |
| **blacklist-key** | unbound | Same, for the blacklist. |
| **announce-list-changes** | **on** | Print a chat line on every add or remove, so you can tell whether the key press landed on the villager you meant. |

**The blacklist always applies, in every mode.** It is the "never touch this villager" list: one you have rolled
to a perfect trade, one that belongs to a friend, one standing somewhere the module cannot reach. Adding a
villager to one list automatically removes it from the other, and a whitelisted villager is never rerolled
unless `target-mode` is `Everyone`.

Both lists can also be edited in the GUI panel, which has an **Add villager you are looking at** button and a
**Clear** button per list — the keybind and the button do exactly the same thing.

---

## Rerolling

Break a villager's workstation and put it back, so it rolls a completely new trade list. This is the classic
enchanted-book trading-hall routine, automated.

| Setting | Default | What it does |
| --- | --- | --- |
| **reroll-villagers** | **off** | Turn the whole feature on. |
| **reroll-on-price** | **on** | Also reroll when the villager offers what you want but above your price limit. This is how you roll for a cheap book rather than just for *a* book. |
| **max-rerolls** | **20** | How often the same villager may be rerolled before the module gives up on it. |
| **workstation-radius** | **3.0** | How far from the villager to look for its workstation. The block also has to be within your own reach. |
| **reroll-settle-ticks** | **20** | Ticks to wait after breaking, and again after replacing, the workstation. **Too low and the villager simply re-claims the block with the same trades**, which looks exactly like the reroll silently not working. Raise this first if rerolling seems ineffective. |
| **reroll-timeout** | **60** | Ticks a single reroll step may take before the attempt is abandoned. |

The module refuses to reroll a villager that has already been traded with. One completed trade locks a
villager's profession and offers **forever**; breaking the workstation after that only stops it restocking. It
also will not reroll over a problem a reroll cannot fix — no emeralds, no inventory space, or a limit you set
yourself.

The broken block drops as an item and is picked back up before being placed again, so you do not need to carry a
spare workstation — though if one is already in your inventory it is used immediately.

---

## Safety

| Setting | Default | What it does |
| --- | --- | --- |
| **packets-per-second** | **40** | Upper limit for every packet the module sends. Trades, inventory clicks, crafting, shulker transfers and item drops all draw from **one shared budget**. Lower is safer and slower. |
| **packet-burst** | **24** | How many packets may go out back to back before the per-second limit takes over. A shulker transfer needs a handful at once, so do not set this too low. |
| **pause-on-lag** | **on** | Stand still while the server is not keeping up. Trading against a lagging server is the fastest way to a desynced inventory. |
| **min-tick-rate** | **12.0** | Pause below this server tick rate. 20 is a healthy server. |
| **pause-while-using-items** | **on** | Do nothing while you are eating, drinking or breaking a block, so the module does not interrupt you. |
| **pause-on-low-health** | **on** | Stop trading below the health threshold, so you are not stuck in a trading screen while something is hitting you. |
| **min-health** | **8.0** | Health to pause at. |
| **disable-when-idle** | **off** | Turn the module off once every rule has hit its limit and there is nothing left to do. Handy for leaving it running until your emerald target is met. |
| **idle-ticks** | **600** | How long nothing may happen before it turns itself off (600 ticks = 30 seconds). |

---

## Inventory manipulation

Optional triggers that keep the inventory workable while the aura runs. Each one interrupts the aura while it
runs, and they chain into each other: *few emeralds → craft emeralds from blocks → few blocks → fetch blocks
from the shulker* falls out automatically.

### General

| Setting | Default | What it does |
| --- | --- | --- |
| **inventory-manipulation** | **off** | Master switch for everything in this group. |
| **run-without-aura** | **on** | Keep the triggers running while Villager-Aura is off. |
| **action-delay** | **2** | Ticks between two inventory operations, so the server can confirm the previous one. |
| **action-timeout** | **60** | How many ticks a single step (placing, opening, breaking) may take before the action is aborted. |
| **fail-cooldown** | **100** | Ticks a trigger is skipped after it failed, so a hopeless action is not retried forever. |
| **max-chained-actions** | **12** | How many actions may run back to back before the module assumes two triggers are undoing each other. Hitting the cap pauses the group for 10s and prints the likely conflict. |
| **cancel-screens** | **on** | Do not render the crafting and shulker screens the module opens. |
| **rotate** | **on** | Rotate towards the crafting table / shulker box being interacted with. |
| **amount-mode** | **ToLimit** | `ToLimit` moves everything down or up to the leave value in one go — recommended, because the trigger is satisfied immediately. `TriggerMinusLeave` moves exactly `trigger − leave` per action, which may need several chained actions. |

### Automatic shulker use

**This is the part that makes the backpack useful, and it needs no rules from you.** Your buy and sell rules
already say everything needed: emeralds are what you spend and collect, sell-rule items are stock you hand over,
buy-rule items are what you accumulate.

Reading a shulker box costs nothing and requires no interaction — the contents travel with the item — so the
module always knows what is inside before it places anything down.

Jobs appear in two ways:

1. **Thresholds** — emeralds below the low mark, a sell item below its low mark, free inventory slots below the
   limit.
2. **Requests from the trading loop** — when a trade is refused for want of emeralds, want of an item, or want of
   room, the module asks for *exactly that item* to be fetched or stored. It does not wait for a threshold. This
   is what makes the whole thing self-sustaining: standing in front of a librarian with no emeralds means "fetch
   emeralds now", not "wait until the count happens to drop below a number".

A request is only kept while a carried shulker box can actually serve it, so asking for emeralds you do not have
anywhere never puts the module into a loop.

| Setting | Default | What it does |
| --- | --- | --- |
| **auto-shulker** | **on** | Master switch for this section. With it on you do not need Dump or Refill rules at all. |
| **auto-emeralds** | **on** | Fetch emeralds out of a shulker when you run low, put them away when they pile up. |
| **emerald-low** | **64** | Fetch emeralds once you hold fewer than this. |
| **emerald-target** | **256** | How many emeralds to carry. Fetching fills up to this, storing trims down to it. |
| **emerald-high** | **1024** | Store emeralds once you hold more than this. Keep it well above the target or the two fight each other. |
| **auto-sell-stock** | **on** | Fetch whatever your Sell Rules list when it runs low, so you never stand in front of a farmer with all your wheat in a box. |
| **sell-stock-low** | **16** | Fetch a sell item once you hold fewer than this many. |
| **sell-stock-target** | **256** | How many of a sell item to carry once fetched. |
| **auto-store-purchases** | **on** | Put whatever your Buy Rules list into a shulker once the inventory gets tight. **Items no rule mentions are never touched**, so your tools, food and blocks are left alone. |
| **purchase-keep** | **0** | How many of a bought item to leave in the inventory when storing the rest. |
| **store-when-free-slots** | **3** | Start storing once this many inventory slots or fewer are empty. A safety net — the trading loop asks for a specific item the moment a trade is refused for lack of room. |

### Shulker handling

| Setting | Default | What it does |
| --- | --- | --- |
| **shulker-auto-tool** | **on** | Swap to the fastest tool in the hotbar before breaking the box. |
| **transfers-per-tick** | **12** | How many stacks are moved per tick. The packet rate limit still applies on top of this. |
| **lock-movement-while-placed** | **on** | Suppress your movement input from the moment the box is placed until it is broken again, so you cannot walk away from an open box full of your items. |
| **walk-to-dropped-shulker** | **on** | Walk over to the broken box if it landed out of pickup range instead of leaving it behind. |
| **shulker-pickup-ticks** | **80** | How long to wait for (and walk towards) the broken box before giving up on it. |

### Drop excess items

| Setting | Default | What it does |
| --- | --- | --- |
| **drop-excess-items** | **off** | Throw away everything above the limit in the Drop Rules table. |
| **drop-direction** | **Forward** | Where to throw, relative to you. The module rotates server-side first and holds that rotation while throwing, so the items land together. |

### Crafting

| Setting | Default | What it does |
| --- | --- | --- |
| **compress-emeralds** | **off** | Craft excess emeralds into blocks. Needs a crafting table in range or the trigger does not fire. |
| **compress-trigger** / **compress-leave** | **128** / **64** | Fires above the trigger; leaves this many emeralds behind. |
| **decompress-emeralds** | **off** | Craft blocks back into emeralds in the 2×2 grid — no table needed. Does not fire without blocks in the inventory. |
| **decompress-trigger** / **decompress-target** | **32** / **128** | Fires below the trigger; restores up to the target. |
| **craft-glass-panes** | **off** | Craft excess glass into panes (6 → 16), for selling to cartographers. Needs a table. |
| **glass-trigger** / **glass-leave** | **64** / **0** | Fires above the trigger; leaves this much glass. |
| **crafting-table-range** | **4.0** | How far away a crafting table may be. |
| **max-crafts-per-action** | **64** | Upper limit of crafts per action. A grid slot cannot hold more than a stack, so 64 is the maximum. |

> Keep **compress-leave** above **decompress-trigger**, or the two triggers will undo each other forever. The
> module warns you about this when you enable it.

---

## Render

| Setting | Default | What it does |
| --- | --- | --- |
| **render** | **off** | Draw a box around villagers the module handled, coloured by what happened. **The single most useful setting for working out why a villager is being skipped.** |
| **fill-opacity** | **0.3** | Opacity of the box fill. |
| **highlight-lists** | **on** | Always outline whitelisted and blacklisted villagers in range, even ones the module has not touched, so you can see your lists at a glance. |

### What the colours mean

| Colour | Meaning |
| --- | --- |
| grey — **default-color** | Interacted, waiting for the server to answer. |
| green — **traded-color** | A trade went through. |
| red — **no-trades-color** | None of the villager's offers matches a rule. |
| blue — **no-emerald-color** | Not enough emeralds. |
| white — **no-sell-item-color** | Not enough of the items the trade asks for. |
| yellow — **out-of-stock-color** | Locked until the villager restocks. |
| pink — **too-expensive-color** | Above your price limit. |
| orange — **limit-reached-color** | Your per-item inventory limit has been reached. |
| brown — **inventory-full-color** | No room for the result. With auto-shulker on, a storage job was just requested. |
| slate — **skipped-color** | Skipped by the profession filter or a list, never opened. |
| purple — **reroll-color** | Currently being rerolled. |
| cyan — **nav-target-color** | The villager currently being walked to. |
| **whitelist-color** / **blacklist-color** | List membership, shown when `highlight-lists` is on. |

---

## Trading-hall presets

### Large hall, you walk around yourself

The module trades whatever comes into reach and never touches your controls.

```
movement-mode            Off
interact-delay           2
ticks-to-close           0
trade-delay              1
revisit-delay            40
range                    4.5
require-line-of-sight    on   (off if your villagers are behind glass)
freeze-while-trading     off
```

If it only trades when you stand still in front of a villager, `interact-delay` is too high — see
[Troubleshooting](#troubleshooting).

### Large hall, fully hands-off

The module walks the hall by itself and you can take over at any moment.

```
movement-mode            Pathfind      (install Baritone; otherwise Simple)
search-radius            24
stop-distance            3.0
leash-radius             48
anchor-on-enable         on
manual-override-ticks    20
return-to-anchor         on
interact-delay           2
target-mode              Everyone
```

Stand in the middle of the hall when you switch it on — that is where the anchor lands.

### Only the villagers I picked

```
target-mode              WhitelistOnly
whitelist-key            <bind a key>
movement-mode            Pathfind
leash-radius             32
```

Walk the hall once, looking at each villager you care about and pressing the key. Turn on
`render` + `highlight-lists` to check your list at a glance.

### Rolling a librarian for a cheap book

```
Buy Rules                enchanted_book, Max Price 16, Buy Limit -1
reroll-villagers         on
reroll-on-price          on
max-rerolls              100
reroll-settle-ticks      20      (raise to 30-40 if rerolls seem ineffective)
target-mode              Everyone
movement-mode            Off
```

Blacklist a librarian the moment it rolls something you want to keep, so the module stops touching it.

### Self-sustaining with a backpack of shulkers

```
inventory-manipulation   on
auto-shulker             on
auto-emeralds            on
emerald-low              64
emerald-target           256
emerald-high             1024
auto-sell-stock          on
auto-store-purchases     on
store-when-free-slots    3
```

Carry one shulker with emeralds, one with your sell stock, and one empty for purchases. The module fetches and
stores as needed and never touches an item your rules do not mention.

---

## Troubleshooting

**It only trades when I stand still in front of a villager.**
Lower `interact-delay` to 2 and `ticks-to-close` to 0, and set `trade-delay` to 1. Those three decide how long
one villager occupies the module. Also check `revisit-delay` is not so high that villagers you pass are being
ignored.

**It fights me for the movement keys.**
Raise `manual-override-ticks`, or set `movement-mode` to `Off` and `freeze-while-trading` to off. With
`movement-mode` on anything other than `Off`, touching a movement key always hands control straight back.

**It walks off across the map.**
Lower `leash-radius` and set the anchor in the middle of your hall with `set-anchor-key`.

**It ignores a villager that clearly has what I want.**
Turn on `render` and read the colour. Then `debug` for the exact reason per offer. The usual causes are
`profession-filter` with a trade the built-in table maps to a different profession, a `min-level` / `max-level`
band, `require-line-of-sight` with a villager behind glass, or the villager being on the blacklist.

**Rerolling does nothing.**
The villager has probably been traded with already — that locks its offers permanently and nothing can undo it.
If it is genuinely untraded, raise `reroll-settle-ticks`: too short a wait lets the villager re-claim the
workstation with the same trades.

**It stops with "no shulker box has room" or "none of the carried shulker boxes has any".**
That message is literal — carry a box with the item, or an empty one with free slots.

**I get kicked or rubber-band.**
Lower `packets-per-second` and `packet-burst`, raise `trade-delay` and `action-delay`, and make sure
`pause-on-lag` is on.
