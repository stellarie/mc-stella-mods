# mod-stella-equips-enchants

A server-side Fabric mod. Every enchantment costs at most one level to assemble
at an anvil. Later slices add the enchantment catalogue, a player power system,
and a per-player mob level readout.

Build and test:

```powershell
cd mod-stella-equips-enchants
.\gradlew.bat clean build
.\gradlew.bat runGameTest
```

The built jar is
`mod-stella-equips-enchants/build/libs/mod-stella-equips-enchants-0.1.0+26.3.jar`.
Copy it into a Fabric server's `mods` folder. The mod needs Fabric API. No client
mod is required.

## Anvil cost

One mixin clamps `AnvilMenu.getCost()` to 1. `AnvilMenu` exists on the client
and the server, so the displayed cost equals the charged cost. The clamp leaves
`min_cost` and `max_cost` alone, so the enchanting table keeps its
per-enchantment rarity.

## Game tests

```powershell
.\gradlew.bat runGameTest --rerun
```

The game tests run a headless server. They check the fixture enchantment
`mod-stella-equips-enchants:test_marker`, its cost fields, and the clamped anvil
cost.

## Target platform

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |
