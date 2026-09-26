# V6 — Villages release: docs, final gates, integration, deployment

| Field | Value |
|---|---|
| Mod | `mc-stella-villages` |
| Depends on | V1 to V5 |
| Source | Villages handoff, "Remaining feature work", last five bullets |

## Plan

1. Update `mc-stella-villages/README.md` and the root `README.md` to the shipped roster (V1),
   Screen (V2), raid rate with its measured number (V3), mobs compatibility (V4), and visuals (V5).
2. `clean build`, then five separate consecutive `runGameTest --rerun` runs. Copy each log first.
3. Review the full diff and the jar: list its entries, confirm zero GameTest entries, record the size
   and SHA-256.
4. Integration: `main-villages` does not exist on `origin` (D-X-1 in the index). After oniichan
   decides, create it from the reviewed tip. Then
   `git merge-tree --write-tree main-villages feat/mc-stella-villages`. Fast-forward only if there is
   no conflict. Repeat the final gates on the integration branch.
5. Deploy the jar to server and client. Hash both copies.
6. Manual acceptance, user-owned: mission Screen interaction, patrol textures, survival-player
   retaliation (`patrolRetaliatesAgainstSurvivalPlayerRequiresManualTest` stays optional and visibly
   failing), and 200-tick cross-dimension threat cleanup. Use disposable worlds or backups.

## Accepted residuals carried forward

No-site mission search may load up to 64 chunks transiently. New locked maps may render blank
terrain until the client explores. GameTest mock players are CREATIVE.
