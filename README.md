# mc-stella-mods

Server-side Minecraft mods for a private Fabric server.

## Mods

| Directory | Purpose | Status |
|---|---|---|
| `mod-stella-mobs/` | Smarter mob AI, gear, and behavior | phase 0 |

## Target platform

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |

Each mod is a separate Gradle project with its own build. This layout keeps
unrelated mods out of one build graph.

## Layout

Each mod directory contains a complete Fabric project. The directory name
matches the mod id, so a jar and its source are easy to pair.
