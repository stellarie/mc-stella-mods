"""Win-rate arithmetic, the release bar, the report, and the roster list."""

import pytest

from stella_train import eval as evaluation
from stella_train import roster, stats
from stella_train.curriculum import Curriculum
from stella_train.rollout import Result
from stella_train.runlog import RunLog


def test_roster_has_nineteen_unique_brains_and_three_diggers():
    assert len(roster.BRAINS) == 19 and len(set(roster.BRAINS)) == 19
    assert set(roster.DIGGERS) <= set(roster.BRAINS) and len(roster.DIGGERS) == 3
    assert roster.brain_index("CritRusher") == 0 and roster.brain_index("RandomMix") == 18


def test_wilson_interval_brackets_the_rate():
    low, high = stats.wilson(45, 100)
    assert low < 0.45 < high and 0.0 < low and high < 1.0
    assert stats.wilson(0, 0) == (0.0, 1.0)


def test_a_clear_gap_has_a_lower_bound_above_zero():
    assert stats.difference_lower_bound(200, 300, 100, 300) > 0.15
    assert stats.difference_lower_bound(160, 300, 150, 300) < 0.0


def results(win_by_brain, n=300, timeouts=0, engaged=420.0):
    out = []
    for brain, wins in win_by_brain.items():
        for i in range(n):
            outcome = "win" if i < wins else ("timeout" if i < wins + timeouts else "loss")
            out.append(Result(roster.brain_index(brain), roster.EVAL_SEED_BASE + i, roster.STAGE_RELEASE, outcome, 10, 600.0, engaged, 30.0, 5.0))
    return out


def column(rate, styles=None, engaged=420.0):
    base = {name: int(rate * 300) for name in roster.BRAINS}
    base.update(styles or {})
    return evaluation.summarize(results(base, engaged=engaged), 300 * len(roster.BRAINS))


def test_release_bar_needs_fifteen_points_and_no_bad_style():
    baseline = column(0.40)
    assert evaluation.compare(column(0.60), baseline)["passes"]
    assert not evaluation.compare(column(0.50), baseline)["passes"]
    trailing = evaluation.compare(column(0.60, {"Bunker": 60}), baseline)     # 20 percent against 40 percent
    assert trailing["failing_styles"] == ["Bunker"] and not trailing["passes"]
    within = evaluation.compare(column(0.60, {"Bunker": 108}), baseline)      # 36 percent against 40 percent: inside 5 points
    assert within["passes"]


def test_a_small_shortfall_that_noise_explains_does_not_fail_a_style():
    baseline = column(0.40, {"Tunneler": 120})
    close = column(0.60, {"Tunneler": 103})    # 5.7 points below on 300 episodes: the bound includes zero
    result = evaluation.compare(close, baseline)
    assert result["styles"]["Tunneler"]["fails"] is False


def test_the_report_lists_each_digger_on_its_own_row():
    model, baseline = column(0.6), column(0.4)
    report = {"suite": "release", "policy": "x.bin", "timestamp": "now", "model": model, "baseline": baseline,
              "comparison": evaluation.compare(model, baseline)}
    text = evaluation.render(report)
    for digger in roster.DIGGERS:
        assert any(line.startswith(digger) for line in text.splitlines())
    assert "release bar" in text


def test_suites_hold_the_planned_episode_counts():
    assert len(evaluation.build_tasks("stage-a")) == 2 * 50
    assert len(evaluation.build_tasks("release")) == 19 * 300
    assert min(task.seed for task in evaluation.build_tasks("release")) >= roster.EVAL_SEED_BASE


def test_stage_a_promotes_at_the_bar_and_stage_b_after_stale_evals(tmp_path):
    config = {"stage_a_win_rate": 0.8, "stage_a_min_decisions": 100, "stage_b_patience_evals": 3, "stage_b_budget_share": 0.9,
              "stage_b_min_decisions": 0, "eval_episodes_per_brain": 2}
    log = RunLog("curriculum", base=tmp_path)
    curriculum = Curriculum(config, budget=1_000_000, log=log)
    assert not curriculum.after_eval(50, 0.95)                # below the minimum decisions
    assert not curriculum.after_eval(200, 0.7)
    assert curriculum.after_eval(300, 0.8) and curriculum.stage == roster.STAGE_B
    assert not curriculum.after_eval(400, 0.3) and not curriculum.after_eval(500, 0.31)
    assert not curriculum.after_eval(600, 0.3) and not curriculum.after_eval(700, 0.3)
    assert curriculum.after_eval(800, 0.3) and curriculum.stage == roster.STAGE_C
    assert len(curriculum.eval_tasks()) == 19 * 2
    log.close()


def test_release_bar_also_needs_sixty_percent_engaged():
    baseline = column(0.40)
    assert evaluation.compare(column(0.60, engaged=420.0), baseline)["passes"]          # 70 percent of the ticks
    low = evaluation.compare(column(0.60, engaged=300.0), baseline)                      # 50 percent
    assert not low["engaged_passes"] and not low["passes"] and low["overall_passes"]
