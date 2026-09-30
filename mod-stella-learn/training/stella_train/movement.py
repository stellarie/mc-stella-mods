"""How far and how the archer moves, from the movement columns of an episode's last step."""

from __future__ import annotations

import numpy as np

# Columns of a step's metrics after ticks, engaged, chase and shots.
TRAVELLED, FORWARD, STRAFE, RETREAT, RELEASE, DEATH = 4, 5, 6, 7, 8, 9


def episode_row(metrics) -> tuple:
    """Travelled, |forward|, |strafe|, retreat share, release distance, shots, death distance of one episode."""
    return (float(metrics[TRAVELLED]), float(metrics[FORWARD]), float(metrics[STRAFE]), float(metrics[RETREAT]),
            float(metrics[RELEASE]), float(metrics[3]), float(metrics[DEATH]))


def summarize(rows) -> dict:
    """Means over episodes; release distance is weighted by shots, death distance counts only the episodes that had a death."""
    if not len(rows):
        return {"travelled": 0.0, "abs_forward": 0.0, "abs_strafe": 0.0, "retreat_share": 0.0, "release_distance": 0.0, "death_distance": 0.0}
    data = np.asarray(rows, dtype=np.float64)
    shots = data[:, 5].sum()
    deaths = data[:, 6] > 0.0
    return {
        "travelled": float(data[:, 0].mean()), "abs_forward": float(data[:, 1].mean()), "abs_strafe": float(data[:, 2].mean()),
        "retreat_share": float(data[:, 3].mean()),
        "release_distance": float((data[:, 4] * data[:, 5]).sum() / shots) if shots else 0.0,
        "death_distance": float(data[deaths, 6].mean()) if deaths.any() else 0.0,
    }


def text(summary: dict) -> str:
    return (f"travelled={summary['travelled']:.1f} abs_forward={summary['abs_forward']:.2f} abs_strafe={summary['abs_strafe']:.2f} "
            f"retreat_share={summary['retreat_share']:.2f} release_distance={summary['release_distance']:.1f} death_distance={summary['death_distance']:.1f}")
