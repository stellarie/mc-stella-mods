"""The bot body of an episode, computed from its seed the way `com.stella.learn.gym.Body` does (SplitMix64)."""

from __future__ import annotations

from dataclasses import dataclass

MASK = (1 << 64) - 1
GAMMA = 0x9E3779B97F4A7C15
SPECIAL_SHARE = 0.10
TOUGHNESS_BUCKETS = ("0-2", "3-5", "6-8", "9-12")


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


def body_for_seed(seed: int) -> Body:
    """`seed` is the unsigned 32-bit seed of the Reset frame."""
    draw = _stream(seed)
    max_health = 20 + int(next(draw) * 41)
    armor = int(next(draw) * 21)
    toughness = int(next(draw) * 13)
    protection = int(next(draw) * 5)
    roll = next(draw)
    which = next(draw)
    special = ("resistance" if which < 0.5 else "absorption") if roll < SPECIAL_SHARE else "none"
    return Body(max_health, armor, toughness, protection, special)
