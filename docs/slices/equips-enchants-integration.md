# `feat/equips-enchants-integration` — Integration: loot, heart cap, Veinminer plane, Warden Wave

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | Integration |
| Code tip | `aabc843` (2026-09-24) |
| Built on | the combined line with slices 3–7b, HP, and the curses replayed |
| Status | Merged into `main` history. The base of `feat/ore-regeneration`, and so of `main-equip-ench`. |

## Design

| Change | Commit | Design |
|---|---|---|
| Heart cap (client) | `64005a1` | A client source set caps the drawn heart containers at 100 (ten rows). The mod still loads on a dedicated server. |
| Player health slice | `3a44c1f`, `aed2a02` | Cherry-pick of the flat health curve; the package cycle between power and readout is broken. |
| Echolocation light | `8f26f5d` | Scan all 125 blocks at the miner's feet; each ore stays outlined for 40 ticks. |
| Chest loot | `9490679` | Three loot tables hooked into 55 vanilla chest tables: gear 30%, book 30%, jackpot 5%. Tier bands from the mob-level numbers. `chest_loot` tag, no curses or fixtures. |
| Bonus chest and modded containers | `7ccfbfe` | A guaranteed iron kit in the bonus chest; container words match in any namespace. |
| Veinminer plane, Cloudstep jump | `6bad930` | Veinminer level 1, a three-block plane chosen from the look axis, cooldown armed before the first break. Cloudstep: Jump Boost 3 plus Slow Falling. |
| Warden Wave and warden head | `aabc843` | Chance 0.25, radius 3, 4 damage, Slowness 3 for 40 ticks, 60-tick cooldown, needs a mob head worn. The head drops from the Warden at 0.25. **The commit states that no test proves the wave fires.** |

## Open plans that touch this code

- E1 (Stage 2) removes or changes Veinminer (the handoff's "Veinminer item" gate). Plan on `main-equip-ench`.
- The Warden Wave behaviour is still unproven. No plan covers it yet.
