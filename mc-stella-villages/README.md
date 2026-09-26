# Mc Stella Villages

Mc Stella Villages adds patrol guards, Nemesis missions, and small raids to vanilla villages.

## Requirements

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |

Install the jar on the server and on each client. The client jar displays the patrol textures and mission screen. The mod-stella-mobs mod is optional.

## Features

- One ten-entity patrol per discovered vanilla village.
- Three contracts at each elite vindicator head. Each contract creates one persistent Nemesis target.
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

To run the same suite with `mod-stella-mobs` loaded, build that mod first, then run
`.\gradlew.bat runGameTest --rerun -PwithStellaMobs=true`. The log must list `mod-stella-mobs` among the
loaded mods. Patrol members and Nemesis targets carry the `mod-stella-mobs.no_spawn_roll` tag, so the
mobs mod adds its combat goals but leaves their kit, health and rank alone.

The jar is `build/libs/mc-stella-villages-0.1.0+26.3.jar`. Copy it to the `mods` folder on the server and the client.
