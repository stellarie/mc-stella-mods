"""Win-rate arithmetic for the eval reports: Wilson intervals and a one-sided bound on a difference of rates."""

from __future__ import annotations

import math

Z_ONE_SIDED_95 = 1.6448536269514722
Z_TWO_SIDED_95 = 1.959963984540054


def wilson(wins: int, n: int, z: float = Z_TWO_SIDED_95) -> tuple[float, float]:
    if n == 0:
        return 0.0, 1.0
    p = wins / n
    denominator = 1.0 + z * z / n
    centre = (p + z * z / (2 * n)) / denominator
    half = z * math.sqrt(p * (1.0 - p) / n + z * z / (4 * n * n)) / denominator
    return max(0.0, centre - half), min(1.0, centre + half)


def difference_upper_bound(wins_a: int, n_a: int, wins_b: int, n_b: int, z: float = Z_ONE_SIDED_95) -> float:
    """One-sided 95 percent upper bound on (rate_b - rate_a). Negative when the rate of `a` is above `b` for sure."""
    if n_a == 0 or n_b == 0:
        return 1.0
    pa, pb = wins_a / n_a, wins_b / n_b
    se = math.sqrt(pa * (1.0 - pa) / n_a + pb * (1.0 - pb) / n_b)
    return (pb - pa) + z * se


def difference_lower_bound(wins_a: int, n_a: int, wins_b: int, n_b: int, z: float = Z_ONE_SIDED_95) -> float:
    """One-sided 95 percent lower bound on (rate_a - rate_b)."""
    if n_a == 0 or n_b == 0:
        return -1.0
    pa, pb = wins_a / n_a, wins_b / n_b
    se = math.sqrt(pa * (1.0 - pa) / n_a + pb * (1.0 - pb) / n_b)
    return (pa - pb) - z * se
