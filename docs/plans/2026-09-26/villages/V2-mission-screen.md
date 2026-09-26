# V2 — Server-authoritative mission Screen and payloads

| Field | Value |
|---|---|
| Mod | `mc-stella-villages` |
| Work branch | a slice branch from `feat/mc-stella-villages` after V1 merges into it |
| Depends on | V1 |
| Source | Villages handoff, "Remaining feature work" ("the approved server-authoritative mission Screen") |

## Current state (verified in this tree)

- Offers and acceptance already exist on the server: `MissionService.offersFor`, `acceptOffer(player,
  headId, offerId)` with range, staleness, and head checks, and a board per player
  (`mission/MissionService.java:77-141`). Interaction enters through `UseEntityCallback` (`:69`).
- The client entrypoint only logs (`client/VillageClient.java`). No payload or screen exists.
- The approved Screen design sits on the blackboard, not in this repository.

## Design

The server stays the only authority. The client shows what it is sent and asks.

| Payload | Direction | Fields |
|---|---|---|
| `mc-stella-villages:mission_board` | S2C | head entity id, head UUID, up to 3 offers (offer UUID, target type id, distance band) |
| `mc-stella-villages:mission_accept` | C2S | head UUID, offer UUID |
| `mc-stella-villages:mission_result` | S2C | offer UUID, accepted flag, reason code |

- Register with Fabric `PayloadTypeRegistry.playS2C/playC2S` in the main source set (types only);
  the Screen and receivers live in `src/client`.
- On right-click of a registered head within `MISSION_MENU_RANGE`, the server sends
  `mission_board`. The C2S handler calls the existing `acceptOffer` unchanged, then replies with
  `mission_result` using its existing reason codes (`stale_or_foreign_head`, `stale_offer`,
  `no_safe_target`, and so on).
- A client without the mod never receives a payload (Fabric checks channel support). The server
  falls back to today's behaviour for that player.
- The Screen: three offer rows and an Accept button per row. A result closes the screen or shows the
  reason text.

## Plan

1. Recover the approved Screen spec and commit it beside this file.
2. Server tests (headless): `boardPayloadListsTheBoardOffers`,
   `acceptPayloadRunsTheExistingChecks`, `forgedAcceptForAnotherHeadIsRejected`,
   `acceptOutsideRangeIsRejected`, `clientWithoutChannelGetsNoPayload`. The payload handler is called
   directly with a mock player; the recorded packets are asserted. Red run.
3. Payload types, server handlers, then the client Screen.
4. Gates, Hana review, deploy (server **and** client jar), hash.

## Manual acceptance

The Screen interaction itself (open, read, accept, reject message) is client code, so it is a
user-owned manual check, as the handoff already lists.
