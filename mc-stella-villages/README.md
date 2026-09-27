# Mc Stella Villages

Mc Stella Villages adds patrol guards, Nemesis missions, and small raids to vanilla villages.

## Requirements

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |

Install the jar on the server and on each client. Everything a player sees uses vanilla assets and a vanilla chest menu, so a client without the mod can still read the board and see the patrols. The mod-stella-mobs mod is optional.

## Features

- One ten-entity patrol per discovered vanilla village. Every 5 seconds, the chunks within 2 of each
  player are checked, so a village whose villagers claimed their beds after its chunk loaded still
  gets its patrol. When a village's head has been missing for a minute while a player is near, the
  village gets a new patrol: fallen members are replaced, and living ones stay.
- The head keeps its watch within 8 blocks of the village's bell. The other members are set down across
  the village, up to 24 blocks from the bell, and walk their own beats within 32 blocks of it, the Ravager
  included; a member that finds itself outside heads back in.
- The head wears full netherite, every piece enchanted (Protection IV and Unbreaking III, Feather Falling IV
  on the boots), and carries a netherite axe with Sharpness V. With `mod-stella-spells` loaded, the axe
  also carries one offensive spell, chosen at random, which the head casts on what it strikes. The kit
  never drops, so a head that falls and returns is no netherite farm; a head saved in the old diamond kit
  is re-armed when it loads.
- Patrol members never despawn, and they do not count toward the natural mob cap. On Peaceful they leave
  as any illager does, and no new patrol is raised.
- Villagers do not fear their patrol, iron and snow golems never take a member as their target, and a
  player may sleep in a bed beside the patrol.
- A patrol takes on any mob that has a player or a villager as its target, of whatever kind: a slime or a
  wolf as much as a zombie. It still hunts every monster in range, and never the mobs mod's friendly
  pillager pairs.
- Three contracts at each village head, each for a different kind of mob drawn from every kind with a
  spawn egg: a Nemesis can be any mob, a cow or a warden alike. Never offered: the ender dragon, which
  has no fight outside its own End, and the mobs that would die on the land a Nemesis stands on (every
  water mob, the guardians and the tadpole) or melt there (the snow golem). Right-click the head within 8 blocks to
  open the Village Missions board, a one-row chest menu with each offer shown as its target's spawn egg.
  Click an offer to accept it; the server checks it, gives you a map to the Nemesis, and closes the
  board, or tells you why it refused. Each contract creates one persistent Nemesis target, with 100
  health on top of its type's own, and the map looks like a vanilla filled map. The reward drops only
  when a player lands the kill; a Nemesis that falls to anything else ends its contract with nothing.
- Patrol Pillagers and Vindicators wear the village banner (a lime field with a white globe and a
  green border), and every patrol member, the Ravager included, gives off a green village sparkle.
  An ordinary illager keeps its vanilla look.
- Every patrol member wears a label over its head: "Village Head" on the head and "Village Patrol"
  on the rest. It is green while the village is calm and red while a player provokes the village.
- With `mod-stella-spells` loaded, every village also has a witch by its bell, labelled "Village
  Witch", in full diamond armour: Protection IV and Unbreaking III on every piece, with Arcane Sight,
  Clear Mind and Doublecast on the helmet and Arcane Weave and Spellguard on the chestplate. Its kit
  never drops. It is one of the village's own, so golems, the patrol and the villagers never take it for
  a foe, and it keeps within 16 blocks of the bell. The spells mod gives it its mind: no potions, spells
  at the village's foes, and heals and blessings for its friends. A village settled before the witch
  existed gets one the next time it loads, and a fallen witch returns with the patrol.
- Patrol members are one side. A stray bolt or swing between members, of any village, does no
  damage, and no member ever takes another as its target.
- A weaponsmith, toolsmith or armorer hardens a weapon. Sneak and right-click the smith while holding
  anything with its own attack damage: 12 emeralds buy +1 attack damage, up to +5 per weapon. The
  bonus shows in the item's tooltip. Spectators cannot harden.
- Ring a village's bell, within 48 blocks of the village's first bell, and its patrol rallies: every
  member gets Speed II and Strength I for 30 seconds and runs to the bell. A village rallies once a
  minute.
- One daily 10% roll for a single-wave village raid. The roll is fixed by the world seed and the day,
  so a restart cannot reroll a day. On a raid day, the first village with a player within 64 blocks
  of its centre is raided with one wave and no bonus wave; if no village has a player nearby, the
  day passes quietly. The first day a world is seen only starts the count. Raids from a player's
  Raid Omen are unchanged. Measured rate: 10.1% over 100,000 seeded days.

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

To run the same suite with `mod-stella-mobs` loaded, build that mod first, then run
`.\gradlew.bat runGameTest --rerun -PwithStellaMobs=true`. The log must list `mod-stella-mobs` among the
loaded mods. Patrol members and Nemesis targets carry the `mod-stella-mobs.no_spawn_roll` tag, so the
mobs mod adds its combat goals but leaves their kit, health and rank alone.

The jar is `build/libs/mc-stella-villages-0.1.0+26.3.jar`. Copy it to the `mods` folder on the server and the client.
