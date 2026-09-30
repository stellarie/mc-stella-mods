"""The bot body of an episode, computed from its seed the way `com.stella.learn.gym.Body` does (SplitMix64)."""

from __future__ import annotations

from dataclasses import dataclass

MASK = (1 << 64) - 1
GAMMA = 0x9E3779B97F4A7C15
TOUGHNESS_BUCKETS = ("0-2", "3-5", "6-8", "9-12")


@dataclass(frozen=True)
class BodyRange:
    """The bounds a stage draws its bot bodies from; the same six numbers go to the gym in a Reset frame."""
    hp_min: int = 20
    hp_max: int = 60
    armor_max: int = 20
    toughness_max: int = 12
    protection_max: int = 4
    special_permille: int = 100

    def wire(self) -> tuple:
        return (self.hp_min, self.hp_max, self.armor_max, self.toughness_max, self.protection_max, self.special_permille)


FULL = BodyRange()
_ranges: dict[int, BodyRange] = {}


def configure(config: dict) -> None:
    """Reads `[body.A]`, `[body.B]` and `[body.C]` of the run config. The release suite uses Stage C's."""
    from . import roster

    _ranges.clear()
    for name, code in (("A", roster.STAGE_A), ("B", roster.STAGE_B), ("C", roster.STAGE_C)):
        _ranges[code] = BodyRange(**config[name])
    _ranges[roster.STAGE_RELEASE] = _ranges[roster.STAGE_C]


def range_for_stage(stage: int) -> BodyRange:
    if not _ranges:
        import tomllib
        from pathlib import Path

        default = Path(__file__).resolve().parents[1] / "configs" / "archer.toml"
        configure(tomllib.loads(default.read_text(encoding="utf-8"))["body"])
    return _ranges[stage]


@dataclass(frozen=True)
class Body:
    max_health: int
    armor: int
    toughness: int
    protection: int
    special: str

    @property
    def toughness_bucket(self) -> int:
        return min(self.toughness // 3, len(TOUGHNESS_BUCKETS) - 1)


def _stream(seed: int):
    state = (seed + GAMMA) & MASK
    while True:
        state = (state + GAMMA) & MASK
        z = state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
        z ^= z >> 31
        yield (z >> 11) * 2.0**-53


def body_for_seed(seed: int, ranges: BodyRange = FULL) -> Body:
    """`seed` is the unsigned 32-bit seed of the Reset frame."""
    draw = _stream(seed)
    max_health = ranges.hp_min + int(next(draw) * (ranges.hp_max - ranges.hp_min + 1))
    armor = int(next(draw) * (ranges.armor_max + 1))
    toughness = int(next(draw) * (ranges.toughness_max + 1))
    protection = int(next(draw) * (ranges.protection_max + 1))
    roll = next(draw)
    which = next(draw)
    special = ("resistance" if which < 0.5 else "absorption") if roll < ranges.special_permille / 1000.0 else "none"
    return Body(max_health, armor, toughness, protection, special)
