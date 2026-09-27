# mod-stella-equips-enchants

A Fabric mod with server-side gameplay and an optional client heart layout cap.
Enchanting costs emeralds and never experience. The mod also holds the
enchantment catalogue, player power, and a per-player mob threat readout.

Build and test:

```powershell
cd mod-stella-equips-enchants
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run `runGameTest --rerun` five separate times for the completion gate. Save each
run log before the next run.

The built jar is
`mod-stella-equips-enchants/build/libs/mod-stella-equips-enchants-0.1.0+26.3.jar`.
Copy it into a Fabric server's `mods` folder. The mod needs Fabric API. Install
it on a client only to cap the drawn heart rows for high-health players.

## Enchanting prices

Enchanting costs emeralds and spends no experience, at either container.

- **Anvil.** The price is the number of enchantments the operation applies, at
  one emerald each. One enchantment costs 1, a combine that applies two costs 2,
  and a repair that applies none is free.
- **Table.** The price is the offered enchantment's level. A level 1 offer costs
  1 emerald and a level 5 offer costs 5. `min_cost` and `max_cost` still decide
  which offers appear; they are no longer a price.

`AnvilMenuMixin` writes the price into the anvil's `cost` slot, which feeds
`getCost`, `mayPickup` and `onTake`, so the display and the charge cannot
disagree once the menu has finished computing. It also replaces the level price
that the vanilla "Too Expensive" gate reads, so a heavily repaired item still
assembles. `EnchantmentMenuMixin` puts the price in the table's `costs` array,
which is the array the client renders, and takes the emeralds from the currency
slot, which becomes an emerald slot.

Two enchanting UI limits remain on vanilla clients. The optional client mixin
only changes the heart layout:

- A vanilla client reads its own copy of `costs` and its own experience level
  when it decides whether to draw a table offer and whether to send the click. A
  player holding fewer experience levels than the price sees a disabled row and
  the red level line, although the server would accept the emeralds. Levels are
  never spent.
- The table's tooltip still names lapis lazuli, because that text is client code.
  The slot itself takes emeralds.

Emeralds are the enchanting currency, so a villager hall becomes the engine of
enchanting and a player with no emerald supply cannot enchant at all. That is
the intended shape.

## Weapon enchantments and mastery

- **Tempo** (any weapon, level 1). A direct hit slows the victim for 2 seconds and, when the attacker
  is a player, gives the player Speed for 2 seconds. A thrown trident does not trigger it.
- **Undertow** (trident, level 1). A hit pulls the wielder and the victim together, to their
  midpoint, one block apart. Both move or neither does: a pair that is mounted, already close, or
  blocked by a wall stays where it is.
- **Soulbound** (any item with durability, level 1, rare). The item stays with you when you die and
  is back in your inventory when you respawn, even if the server restarts in between. Ordinary items
  still drop. It is offered at the table and in loot at the catalogue's lowest weight.
- **Weapon Mastery.** A sword, axe, spear, trident or mace counts the hostiles its wielder kills with
  it in melee. At 10, 50, 150 and 400 kills it reaches Mastery I to IV, and each tier adds half a
  point of attack damage. The tooltip shows the tier, the count and the next threshold. The count and
  the bonus live on the item, so they survive trades, anvils and restarts. Peaceful kills and tools
  never count.
- **Mastery IV perks.** At Mastery IV each weapon type unlocks a perk on every melee hit, named in
  its tooltip:

  | Weapon | Perk | Effect |
  | --- | --- | --- |
  | Sword | Lifesteal | heals you half a heart |
  | Axe | Cleave | hostiles within 2.5 blocks of the target take half the hit |
  | Spear | Momentum | Speed I for 3 seconds |
  | Trident | Tidecaller | Conduit Power for 5 seconds |
  | Mace | Aftershock | hostiles within 3 blocks of the target take 3 damage |

## Player power

Player power follows the vanilla experience level, capped at `level_cap` (50). The
mod reads `experienceLevel` and never writes experience, so kills, deaths,
enchanting, and experience orbs work exactly as in vanilla. There are no custom
points, no kill awards, and no death penalty.

All five player attributes grow together from the capped level, because there is
one input. `max_health`, `armor`, `knockback_resistance`, `attack_damage`, and
`attack_speed` each reach a stated value at level 50 and scale linearly from zero
at level 1; a level above the cap reads as the cap. Nothing a player owns reads
their position, so walking can neither raise nor lower their power. The tracker
rescans every 40 ticks, so the attributes follow a level change within two
seconds.

The attributes are stated in `power.json` as the value at level 50. The armor and
knockback resistance values leave room on purpose: `armor_at_cap` 11.76 of the
vanilla 30 and `knockback_resistance_at_cap` 0.294 of 1.0, so the rest of both
attributes stays free for enchantments. The applied modifier also stops at the
attribute's own ceiling.

Worlds from before this change: the old `power_state` player attachment is skipped
with one warning when the player loads, and the first scan replaces the five saved
`power_*` modifiers with the values for the player's vanilla level. Old combat
points are not converted into experience.

Mob threat is a separate rating: `min(cap, floor(scaled_distance / blocks_per_level))`
plus up to `depth_bonus_cap` for depth, capped at `mob_level.level_cap` (36).
`dimension_scale` multiplies the horizontal distance before the division, so 1200
nether blocks read as 9600 overworld blocks. Threat never changes a mob or awards
anything; it only feeds the readout.

The config is `config/mod-stella-equips-enchants/power.json`. The mod writes it on
the first start and re-reads it when the data pack reloads. A hand edit wins, a
bad entry logs and keeps the previous value, and every tuning number lives there.
An existing file is never upgraded: a key you set wins and a key you delete falls
back to the bundled document. The retired `curve` and `award` keys of older files
are reported as unknown and ignored. The `mob_level` section keeps its name so old
files still apply.

## Mob threat readout

Each player sees the threat of nearby mobs, colored by how that threat compares
with their own level. The color is relative, so one mob can read green for one
player and red for another. A readout reads `Threat 7 Zombie 40/40`: the raw
threat, the mob's own name, and its current and maximum health.

With `mod-stella-mobs` installed, elite and boss mobs also carry a rank tag, as in
`Threat 7 <Elite> Zombie 40/40` or `Threat 12 <Boss> Wither 200/200`; boss wins when
both apply. The rank comes from the optional `mod-stella-mobs:readout-rank`
provider, so this mod has no dependency on the mobs mod. Without it, or with an
unknown value, no tag is shown. A rank or health change updates the text on the
next scan.

For the color, raw threat is scaled onto the player level range:
`round(threat * level_cap / mob_level.level_cap)`, so threat 0, 18, and 36 compare as
0, 25, and 50. The gap is that scaled threat minus the owner's capped level. The
bands come from the `bands` block of `power.json`: red at 20 above, yellow at 10
above, orange at 6 above, green from 5 above to 5 below, and white at 6 or more
below. `/stellaequips readout off` clears the readouts of one player and stops new
ones; `on` restores them.

The mob's name is colored by how it treats the viewer: red when the mob is an enemy
by nature (every `Enemy`, including endermen, piglins, zombified piglins, and
spiders, which are neutral in play), targets the viewer, or is angry at the viewer;
green otherwise. Only the name takes this color; the threat, rank tag, and health
keep the band color. It is worked out per viewer, so a golem one player hit turns
red for that player only. A player's hit rechecks that mob within a few ticks, and
any other change shows on the next scan. A colored custom name is shown in the
hostility color too.

A readout is a text display entity. It never joins the level, so the vanilla
tracking broadcast cannot reach a second player. The mod sends the add packet and
the data packet to the owner alone. The mob keeps its own name and name tag.

A readout follows its mob on every tick, and its text is re-sent only when the
band, the text, or the name color changes. A readout exists only for a mob within
32 blocks of its owner, and one player holds at most 40 readouts, nearest first. A
readout is removed when its mob dies, when it leaves the radius, when the cap
drops it, when the owner disconnects, and when the owner opts out. The `readout`
block of `power.json` holds `enabled_by_default`, `radius`, `max_per_player`,
`height_offset`, and `view_range`.

## Damage numbers

Each hit an entity takes shows a small red number over it, and each heal a green one with a plus
sign: the health it actually lost or gained, so a hit that Resistance soaks up shows nothing, and a
hit on golden hearts still counts. The number drifts up by a block and fades over one second, then
goes. It is sent to the players within 32 blocks as a display that never joins the world, so it is
never saved. A player never sees the numbers over their own head. An invisible entity shows no
number, so a hit never gives its place away, and each entity shows at most one damage number and
one heal number every 4 ticks, so a mob farm does not flood its viewers.

## Refining

Weapons and armour refine from +0 to +15, after Ragnarok Online Renewal. Refine at a **Refining
Anvil** (a silver anvil crafted from nine anvils; it never wears, never falls and opens no menu) or at
a village weaponsmith, toolsmith or armorer with the material in your off hand.

- The first use quotes the next step: its chance, its price, and a red warning when a failure would
  break the piece. Use again within five seconds, with the same piece, to pay and roll.
- The material is the piece's own repair item (iron for iron gear, diamonds for diamond gear, planks
  for wood; iron for a bow). A step costs 1, plus 1 for every three levels the piece already has.
- A failure keeps the level and chips 20% of the piece's whole durability. When the piece has no more
  than that left, a failure breaks it, and the quote says so.
- The odds follow Renewal's tables. Weapons are graded by material: wood, stone, gold and copper are
  grade 1 (safe to +7); iron is grade 2 (safe to +6); diamond, the trident and the mace are grade 3
  (safe to +5); netherite is grade 4 (safe to +4). Armour is safe to +4. The last step is 15%.
- Each level adds +0.75 attack damage to a melee weapon, +0.75 base damage to a bow's or crossbow's
  arrows, and +0.5 armour to a piece of armour.
- The level shows as the first line of the tooltip, like an enchantment, and gives the piece a glint.
  It is not an enchantment, so it takes no enchantment's place and leaves the enchantments unchanged.

| Level | Weapon | Armour (each piece) |
|---|---|---|
| +10 | Searing: hits set the target alight for 3 s | Vigor: +2 max health |
| +12 | Rending: hits also inflict Wither for 3 s | Thorned: melee attackers take 2 damage |
| +15 | Ascended: hits send a shockwave, 40% of the hit to hostiles within 3 blocks | Blessed: a player heals 1 health every 5 s |

Each effect keeps working at the higher levels, so a +15 weapon sears, rends and sends a shockwave.

## Game tests

```powershell
.\gradlew.bat runGameTest --rerun
```

The game tests run a headless server. They check the test-only fixture enchantment
`mod-stella-equips-enchants-test-mod-stella-equips-enchants:test_marker`, its cost
fields, the clamped anvil cost, the 71 catalogue enchantments, threat scaling, the
config parser, the attribute modifiers, the vanilla-level tracker, loading worlds
saved before this change, and the readout: its text, rank tags, and colors, its
radius, its cap, its band updates, its removal on death, on range, and on
disconnect, its invisibility to a second player, and the opt-out command. They also
check refining: the odds tables, the quote and confirm, the price, the chip and the break,
the cap, the smith, the bonuses, the tooltip, each special effect, and the recipe.

## Target platform

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |
