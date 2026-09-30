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
    speed_min_permille: int = 1000
    speed_max_permille: int = 1000

    def wire(self) -> tuple:
        return (self.hp_min, self.hp_max, self.armor_max, self.toughness_max, self.protection_max, self.special_permille,
                self.speed_min_permille, self.speed_max_permille)


FULL = BodyRange()
_ranges: dict[int, BodyRange] = {}


def configure(config: dict) -> None:
    """Reads `[body.A]`, `[body.B]` and `[body.C]` of the run config. The release suite uses Stage C's."""
    from . import roster

    _ranges.clear()
    for name, code in (("A", roster.STAGE_A), ("B", roster.STAGE_B), ("C", roster.STAGE_C)):
        _ranges[code] = BodyRange(**config[name])
    _ranges[roster.STAGE_RELEASE] = _ranges[roster.STAGE_C]


_stage_a_hp: tuple | None = None


def set_stage_a_hp(low: int, high: int, armor_max: int | None = None, kind: str = "") -> None:
    """The band of the Stage A ladder that is in force; it overrides the health bounds, and the armor bound if given, of `[body.A]`."""
    global _stage_a_hp
    _stage_a_hp = (low, high, armor_max)


def range_for_stage(stage: int) -> BodyRange:
    from dataclasses import replace
    from . import roster

    ranges = _range_table()
    if stage == roster.STAGE_A and _stage_a_hp is not None:
        armor = ranges[stage].armor_max if _stage_a_hp[2] is None else _stage_a_hp[2]
        return replace(ranges[stage], hp_min=_stage_a_hp[0], hp_max=_stage_a_hp[1], armor_max=armor)
    return ranges[stage]


def _range_table() -> dict:
    if not _ranges:
        import tomllib
        from pathlib import Path

        default = Path(__file__).resolve().parents[1] / "configs" / "archer.toml"
        configure(tomllib.loads(default.read_text(encoding="utf-8"))["body"])
    return _ranges


@dataclass(frozen=True)
class Body:
    max_health: int
    armor: int
    toughness: int
    protection: int
    special: str
    archer_speed: float = 1.0
    bot_speed: float = 1.0

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
    archer_roll = next(draw)
    bot_roll = next(draw)
    low = ranges.speed_min_permille if ranges.speed_min_permille > 0 else 1000
    high = ranges.speed_max_permille if ranges.speed_max_permille >= low else low
    return Body(max_health, armor, toughness, protection, special, (low + archer_roll * (high - low)) / 1000.0, (low + bot_roll * (high - low)) / 1000.0)


SPEED_NAMES = ("slow", "normal", "fast")


def speed_bucket(factor: float) -> int:
    """Thirds of the 0.6 to 1.4 range: slow below 0.867, fast from 1.133."""
    return 0 if factor < 0.8667 else 2 if factor >= 1.1333 else 1


def speed_key(body: Body) -> str:
    return f"archer-{SPEED_NAMES[speed_bucket(body.archer_speed)]}/bot-{SPEED_NAMES[speed_bucket(body.bot_speed)]}"
