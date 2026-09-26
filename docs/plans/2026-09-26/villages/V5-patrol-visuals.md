# V5 — Patrol visuals for Vindicator, Pillager, and Ravager

| Field | Value |
|---|---|
| Mod | `mc-stella-villages` |
| Depends on | V1 (roster without Evoker and Witch) |
| Source | Villages handoff: "Implement patrol visuals for Vindicator, Pillager, and Ravager. Keep ordinary hostiles on vanilla textures." |

## Current state (verified in this tree)

- Patrol members carry a marker through `MobPatrolAccess` / `MobPatrolMixin` (server side).
- `src/client` has only the entrypoint. No renderer, texture, or synced data exists.

## Design

- **Sync**: the client needs to know which entity is a patrol member. Add a boolean
  `SynchedEntityData` accessor through the existing `MobPatrolMixin`, or a small S2C payload on track
  start. Recommendation: the payload, because a new synched data field on vanilla classes breaks
  vanilla clients joining the server. The client keeps a set of patrol entity ids.
- **Render**: client mixins on the Vindicator, Pillager, and Ravager renderers' `getTextureLocation`
  return `mc-stella-villages:textures/entity/patrol/<type>.png` when the entity id is in the set, and
  vanilla otherwise. Ordinary hostiles never change.
- **Assets**: three PNGs, the same UV layout as vanilla. Their art is oniichan's call (D-V5-1).

## Plan

1. Server test: `patrolMembersAreAnnouncedToTrackingPlayers` and
   `ordinaryIllagersAreNeverAnnounced` (recorded packets). Red run.
2. Payload, client set, renderer mixins, assets.
3. Gates, review, deploy the client jar as well, hash.

## Manual acceptance

Patrol textures in a client, beside an ordinary Pillager for contrast. This is already in the
handoff's user-owned list.

## Open decisions

- **D-V5-1** The textures: a supplied art pack, or a recolour of vanilla (which the mod may not
  redistribute if derived from vanilla assets; a hand-made texture avoids that).
