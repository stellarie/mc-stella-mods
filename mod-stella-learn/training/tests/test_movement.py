"""The movement summary: means over episodes, release distance weighted by shots, deaths counted apart."""

from stella_train import movement


def row(travelled, forward, strafe, retreat, release, shots, death):
    metrics = [600, 0, 0, shots, travelled, forward, strafe, retreat, release, death]
    return movement.episode_row(metrics)


def test_summary_weights_release_distance_by_shots():
    summary = movement.summarize([row(10, 0.5, 0.25, 0.5, 8.0, 3, 0), row(20, 0.25, 0.75, 0.0, 12.0, 1, 4.0)])
    assert summary["travelled"] == 15
    assert summary["abs_forward"] == 0.375
    assert summary["release_distance"] == 9.0
    assert summary["death_distance"] == 4.0


def test_empty_summary_is_zero():
    assert movement.summarize([])["travelled"] == 0.0
