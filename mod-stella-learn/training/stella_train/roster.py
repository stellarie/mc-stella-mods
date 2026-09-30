"""The bot roster and the stage codes. The order matches `com.stella.learn.gym.brains.Brains` on the Java side."""

from __future__ import annotations

BRAINS = (
    "CritRusher", "SwordSweeper", "AxeBreaker", "ShieldTurtle", "StrafeDuelist", "BowKiter", "CrossbowSniper",
    "HighGroundShooter", "PillarUp", "BlockTrapper", "DoorwayHolder", "Burrower", "Bunker", "Tunneler",
    "PotionHealer", "ArmorTank", "PearlEscaper", "StyleSwitcher", "RandomMix",
)
DIGGERS = ("Burrower", "Bunker", "Tunneler")
STAGE_A_BRAINS = ("CritRusher", "SwordSweeper", "BowKiter")

STAGE_A = 0
STAGE_B = 1
STAGE_C = 2
STAGE_RELEASE = 3
STAGE_NAMES = {STAGE_A: "A", STAGE_B: "B", STAGE_C: "C", STAGE_RELEASE: "release"}

EVAL_SEED_BASE = 1_000_000
TRAIN_SEED_LIMIT = 1_000_000


def brain_index(name: str) -> int:
    return BRAINS.index(name)


def stage_brains(stage: int) -> tuple[int, ...]:
    names = STAGE_A_BRAINS if stage == STAGE_A else BRAINS
    return tuple(brain_index(name) for name in names)
