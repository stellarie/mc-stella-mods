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

One mixin clamps the anvil cost to 1 level. `AnvilMenuMixin` holds an
`@Accessor("cost")` on the private cost slot, a `@ModifyExpressionValue` on the
cost read inside the "Too Expensive" gate, and one `@Inject` at the RETURN of
`createResult` that writes `AnvilCost.clamp(cost)` back into the slot. The gate
reads the clamped value, so a heavily repaired item still assembles.
`AnvilMenu` exists on the client and the server, and both sides read that one
field, so the display and the charge cannot disagree once the menu has finished
computing: the anvil shows 1 and takes 1. The in-method sync runs before the
clamp, so a client can hold the pre-clamp value for one tick. The clamp leaves
`min_cost` and `max_cost` alone, so the enchanting table keeps its per-enchantment
rarity.

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
