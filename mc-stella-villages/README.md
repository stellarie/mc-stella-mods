# Mc Stella Villages

Mc Stella Villages adds patrol guards, Nemesis missions, and small raids to vanilla villages.

## Requirements

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |
| Mod Stella Mobs | Matching 0.1.0+26.3 build |

Install the jar and Mod Stella Mobs on the server and each client. The mission board uses vanilla assets and a vanilla chest menu.

## Features

- One patrol of six per discovered vanilla village: the duke, two melee guards, two ranged guards, and
  the mage. Every 5 seconds, the chunks within 2 of each
  player are checked, so a village whose villagers claimed their beds after its chunk loaded still
  gets its patrol. A village saved by an older version gains its two ranged guards at its next check.
  When a village's head has been missing for a minute while a player is near, the
  village gets a new patrol: fallen members are replaced, and living ones stay.
- A melee guard carries an iron sword and a shield. A ranged guard carries a crossbow. Both run the
  `mod-stella-mobs` AI. A melee guard raises its shield at an incoming arrow. A ranged guard stands and
  fires at hostile mobs within 24 blocks, and walks closer when its target is farther or out of sight.
  Both kinds wear the village banner and offer the daily guard quest.
- The head guards within 8 blocks of the village's bell and never chases a threat beyond that radius.
  Inside that radius it fights any hostile mob it sees, a slime as much as a zombie.
  The other members are set down across
  the village, outside the head's 8-block circle and up to 24 blocks from the bell, and walk their own beats within 32 blocks of it, the Ravager
  included; a member that finds itself outside heads back in.
- The head wears full netherite, every piece enchanted (Protection IV and Unbreaking III, Feather Falling IV
  on the boots), and carries a netherite axe with Sharpness V. With `mod-stella-spells` loaded, the axe
  also carries one offensive spell, chosen at random, which the head casts on what it strikes. The kit
  never drops, so a head that falls and returns is no netherite farm; a head saved in the old diamond kit
  is re-armed when it loads.
- Patrol members never despawn, and they do not count toward the natural mob cap. On Peaceful they leave
  as any illager does, and no new patrol is raised.
- A village raises its missing members one fill at a time. A fill that finds no place for a member waits
  600 ticks before the village tries again. The heartbeat and spawn-reject log lines are at DEBUG level.
- Villagers do not fear their patrol, iron and snow golems never take a member as their target, and a
  player may sleep in a bed beside the patrol.
- A patrol takes on any mob that has a player or a villager as its target, of whatever kind: a slime or a
  wolf as much as a zombie. It still hunts every monster in range, and never the mobs mod's friendly
  pillager pairs.
- Nine contracts at each village head: three Nemesis, three elite and three kill contracts, each of a
  different hostile mob type within its kind. A Nemesis may be an iron golem with a
  spawn egg. Neutral mobs, including traders and creakings, never appear. Water mobs, bosses, creepers,
  endermites, silverfish, blazes, phantoms and ghasts are also excluded. An iron golem Nemesis pursues its mission player. Right-click the head within 8 blocks to
  open the Village Missions board, a two-row chest menu with each offer shown as its target's spawn egg.
  The top row holds the Nemesis contracts, the bottom row the elite contracts on the left and the kill
  contracts on the right. Each icon names the kind, the count and the reward.
  Slot 0 holds the slayer contract, a beginner contract you can repeat. Defeat one enraged red slime
  128 to 256 blocks from the bell. It is level 20 with 1000 health and permanent Speed II and Strength II. You hold one
  slayer contract at a time. A player kill drops the Apprentice's Chestplate: iron armor with 1000
  durability that repairs with iron ingots. It takes no enchantment. In the equips mod's Refine tab it
  refines like any chestplate. Worn, it removes 1.5% of incoming damage for each light level below 15,
  so 22.5% in full darkness. The red slime needs `mod-stella-mobs` with the red slime.
  The board also sells potions. The second-row slots at the left end, the middle, and the right end hold
  a Potion of Healing, Swiftness, and Strength. Each costs 32 slime balls. With fewer than 32, the duke
  says so and takes nothing. The potion goes to your inventory, or drops at your feet when it is full.
  With `mod-stella-spells`, the top-right slot sells a Scroll of Return for 16 slime balls.
  Click an offer to accept it; the server checks it, gives you a map to the target, and closes the
  board, or tells you why it refused. Every accepted contract sends you to a random spot 256 to 384
  blocks, measured sideways, from the village bell. The spot is new for each contract, and it is always safe ground.
  Each Nemesis contract creates a persistent elite Nemesis and a same-kind
  elite companion. They share one permanent buff. A killed companion stays dead for that contract.
  The Nemesis has 100 health above its elite health,
  diamond or netherite armor, and a matching weapon with three random enchantments per piece. It can
  teleport near its target player; ranged mobs choose a safe position farther away. The map looks like
  a vanilla filled map. A player kill drops one book, two stacks of 64 emeralds and two random
  potions. The book is an enchanted book or, when `mod-stella-spells` is loaded, a spell book, with even
  odds. Three separate rolls add more: 5% for 32 diamonds, 5% for exactly one equipped piece, and 0.1%
  for one ancient debris. A Nemesis no longer drops a head.
  A Nemesis that dies another way ends its contract without these rewards.
  A Nemesis carries the entity tag `stella_nemesis`. With `mod-stella-mobs`, it gives five times the kill XP.
- An **elite contract** spawns one elite and two ordinary mobs of its type at the target. The elite pays
  64 emeralds and a reward roll (see Quest rewards) at double budget when a player kills it, dropped where
  it fell. The two ordinary mobs pay nothing and leave with the elite. An elite that dies another way ends the contract
  without a reward.
- A **kill contract** spawns 5 to 10 ordinary mobs of one type within 6 blocks of the target. The action
  bar shows `[Quest] Kill Zombie 3/7` after each kill. Any death of one of the pack counts, whoever or
  whatever causes it. The last death pays its owner one reward roll (see Quest rewards) into the
  inventory, or at the owner's feet when the inventory is full. When the owner is offline at the last
  death, the mob type and its level are stored, and the roll is paid at the owner's next login.
- **Quest rewards.** The elite, kill, and guard contracts pay by the mob. Each mob type has a tier from 1 to 5,
  taken from its vanilla health plus three times its attack damage (below 25, 40, 70, and 120 for tiers 1
  to 4, and above that tier 5). The ravager, evoker, and elder guardian sit at tier 5, the top ordinary
  tier. Some examples: skeleton 2, zombie 2, vindicator 2, hoglin 3, piglin brute 4, ravager 5.
  The budget, in value points where an iron ingot is 1, is `base x elite x threat`:
  - base by tier: 60, 90, 140, 220, 350.
  - elite: x2 for an elite contract, x1 otherwise.
  - threat: `1 + level / 50`, with the level capped at 150.

  A zombie kill contract pays about 100 points and a ravager contract about 400. The budget buys up to
  six picks from a weighted pool:
  - Ores and ingots (40%): iron, gold, lapis, emerald, amethyst shard, and rarely diamond.
  - Enchanted gear (20%): iron, or diamond at a larger budget. Sword, axe, bow, crossbow, and armor.
  - Enchanted books (20%).
  - Cooked food (20%): stacks of 4 to 32 tipped with one or two beneficial effects. They are Regeneration,
    Speed, Haste, Resistance, and Fire Resistance, and the first three reach level II at tier 4.

  Stacks never exceed the item maximum. No elite, kill, or guard reward holds netherite.
- Every mob a contract spawns, the Nemesis and its companion included, never despawns and carries a
  `[Quest] ` name prefix. A quest mob that loads after its contract has ended is removed. A saved contract
  keeps its kind and the count that remains, and a save from before the quest kinds loads as a Nemesis contract. Hold a mob head you already
  own and right-click any village head to trade it for three random enchanted books.
- Patrol Pillagers and Vindicators wear the village banner (a lime field with a white globe and a
  green border), and every patrol member, the Ravager included, gives off a green village sparkle.
  An ordinary illager keeps its vanilla look.
- Guards and the mage appear only outdoors at street level: nothing solid above them but leaves, and
  within 3 blocks of the bell's height. The rule reads blocks only, so it keeps them out of buildings and
  off roofs from any structure mod. The duke still stands by the bell.
- Right-click a guard (not the duke or the mage) for its daily quest: kill 3 to 6 of one hostile type
  128 to 256 blocks from the bell, marked on a map, for 32 emeralds and one reward roll. Each guard
  gives each player one quest a day; an offline owner is paid at the next login.
- Every patrol member wears a label over its head: "Duke" on the head and "Guard"
  on the rest. It is green while the village is calm and red while a player provokes the village.
- With `mod-stella-spells` loaded, every village also has a witch by its bell, labelled "Mage", in full diamond armour: Protection IV and Unbreaking III on every piece, with Arcane Sight,
  Clear Mind and Doublecast on the helmet and Arcane Weave and Spellguard on the chestplate. Its kit
  never drops. It is one of the village's own, so golems, the patrol and the villagers never take it for
  a foe. It joins the patrol's 32-block beat and detects enemies within 48 blocks. It can throw
  splash healing or buffs at nearby players, villagers, and patrol members every five seconds.
  A separate five-second timer controls random splash Harming, Poison, or Wither against hostile mobs.
  Harmful splash effects spare friendlies,
  and Harming still damages undead enemies.
  The spells mod gives it Warden's Roar, Spark Shot, Life Bloom, and safe-distance blinks. A village settled before the witch
  existed gets one the next time it loads, and a fallen witch returns with the patrol.
- Right-click the mage within 8 blocks to open the Mage
  Shop, a one-row chest menu. It sells five enchanted books of different enchantments, each at a level
  the enchantment allows and each priced from 320 to 960 emeralds. Click a book to buy it: the price
  comes out of your whole inventory and your emerald bags (with `mod-stella-equips-enchants`), the book stays on the shelf, and a short purse changes nothing. The
  clock in slot 7 rerolls all five books for 32 emeralds. The shop also rerolls once at every midnight (day
  time 18000). Each village keeps its own offers in its saved data.
- While a player has the head's mission board or the witch's shop open, that mob stands still, faces the
  player, and does not fight. It returns to its work 5 seconds after the last player closes the menu.
- Patrol members are one side. A stray bolt or swing between members, of any village, does no
  damage, and no member ever takes another as its target.
- A patrol member's stray hit also spares the village: a villager, an iron or snow golem, or a player
  the member is not fighting takes no damage. A player the patrol hunts as a threat still does.
- A weaponsmith, toolsmith or armorer hardens a weapon. Sneak and right-click the smith while holding
  anything with its own attack damage: 12 emeralds buy +1 attack damage, up to +5 per weapon. The
  bonus shows in the item's tooltip. Spectators cannot harden.
- Ring a village's bell, within 48 blocks of the village's first bell, and the loaded patrol teleports
  to safe spots within 8 blocks of it. Each member gets Speed II and Strength II for five minutes.
  Every ring refreshes the rally.
- One daily 10% roll for a village mini-raid. The roll is fixed by the world seed and the day,
  so a restart cannot reroll a day. On a raid day, the first village with a player within 64 blocks
  of its centre is raided with one wave and no bonus wave; if no village has a player nearby, the
  day passes quietly. The first day a world is seen only starts the count. The wave holds 3 to 5
  pillagers, 4 on average, and no other raider. Raids from a player's Raid Omen are unchanged and keep
  every vanilla number. Measured rate: 10.1% over 100,000 seeded days.
- Any raid at a village alerts its guards, whether a mini-raid or a Raid Omen raid. Each guard
  targets the nearest raider within 48 blocks of the bell. The guard needs no line of sight. The raider
  need not target anyone. The duke keeps its 8-block rule, and the mage keeps its own spells. A guard
  never joins a raid. The server log holds one `raid_alert_started` line when an alert begins and one
  `raid_alert_ended` line when its raid ends.
- Defenders grow with each kill. When a guard, the duke, or the mage kills a mob or a player, it gains
  0.5 points in one random stat. A fellow defender, a villager, a golem, and the mobs mod's friendly
  pillagers never count. With `mod-stella-rpg` loaded, the object share `mod-stella-rpg:mob-stat-grow`
  grows an rpg stat. The share is a `BiFunction<Mob, Double, String>`: it takes the mob and the points,
  and returns the key of the stat it grew. Without that mod, the defender gains a permanent modifier
  on max health, attack damage, armor, or armor toughness. One modifier per attribute holds the
  total. The total survives a save. The log holds one `defender_grown` line per kill.

## Build and test

Run commands from this directory.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run the GameTest command five separate times for final verification. Save each log before the next run.

To run the suite with `mod-stella-spells` loaded, build that mod first, then run
`.\gradlew.bat runGameTest --rerun -PwithStellaSpells=true` (or pass the jar's own path). The head's axe
test then checks the spell on the axe.

The Gradle build includes the neighboring `mod-stella-mobs` project. The GameTest suite loads both
mods. The villages mod guards patrol equipment and health against mobs mod spawn rolls.

The jar is `build/libs/mc-stella-villages-0.1.0+26.3.jar`. Copy it to the `mods` folder on the server and the client.

## Training dummy

Each recorded village gets one training dummy by its bell: a large armor stand (scale 1.5) with arms, 3 to 5
blocks from the bell, on a sturdy floor with 3 blocks of room.

- Every hit on the dummy is recorded and cancelled, so nothing breaks it, not even `/kill`.
- Its name reads `Avg 18.6 · Last 21 · 12 hits · DPS 44.2`: the average of the last 20 hits, the last hit, the count,
  and the damage per second over the last 5 seconds. The window resets after 10 seconds without a hit.
- With `mod-stella-rpg`, the recorded damage passes through the object share `mod-stella-rpg:outgoing-damage`,
  so it includes the STR dice, crits, and the attack charge. Without it, the dummy records the raw hit.
- Every 10 seconds, a village whose bell is loaded gets its dummy back if it was removed.
- The dummy carries the entity tags `mc-stella-villages.training_dummy` and `mc-stella-villages.dummy_of.<village id>`.
