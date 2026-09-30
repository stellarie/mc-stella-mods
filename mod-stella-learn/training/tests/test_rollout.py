"""The collector against a scripted server: transitions, episode ends, resets, and eval bookkeeping."""

import numpy as np
import pytest

from stella_train import contract as contract_module
from stella_train.model import ActorCritic, Normalizer, Sampler
from stella_train.rollout import Fleet, Task
from stella_train.runlog import RunLog

from fake_gym import FakeGym


@pytest.fixture()
def sampler():
    contract = contract_module.load()
    return Sampler(ActorCritic(contract), Normalizer(contract.obs_size))


@pytest.fixture()
def log(tmp_path):
    log = RunLog("test", base=tmp_path)
    yield log
    log.close()


def make_fleet(server, log):
    server.start()
    return Fleet([server.port], log, seed=3)


def test_collect_builds_transitions_and_resets_ended_arenas(sampler, log):
    server = FakeGym(arenas=4, length=5)
    fleet = make_fleet(server, log)
    try:
        counter = iter(range(10_000))
        batch, stats = fleet.collect(sampler, 200, lambda: Task(1, next(counter), 0), shaping_scale=0.1, discount=0.99, lam=0.95)
    finally:
        fleet.close()
    assert len(batch) >= 200
    assert stats.decisions == len(batch)
    # Arena 0 and 2 win, 1 and 3 lose. Each episode lasts 5 decisions, so most decisions end in a terminal every fifth step.
    wins = sum(c["win"] for c in stats.outcomes.values())
    losses = sum(c["loss"] for c in stats.outcomes.values())
    assert wins > 0 and losses > 0 and abs(wins - losses) <= 2
    assert stats.episodes == wins + losses
    # Every reset asked for the brain and the stage of the task.
    assert server.resets and all(reset[2] == 1 and reset[3] == 0 for reset in server.resets)
    assert {reset[0] for reset in server.resets} == {0, 1, 2, 3}
    # A win pays +1 at its last decision, shaping 0.25 * 0.1 at every one.
    assert np.isclose(batch.returns.max(), batch.returns.max())
    assert batch.obs.shape[1] == 116 and np.all(np.isfinite(batch.advantages))
    assert stats.dealt_sum > 0 and stats.taken_sum == 0


def test_no_advantage_crosses_an_episode_end(sampler, log):
    server = FakeGym(arenas=2, length=4)
    fleet = make_fleet(server, log)
    try:
        counter = iter(range(10_000))
        batch, _ = fleet.collect(sampler, 120, lambda: Task(0, next(counter), 0), shaping_scale=0.0, discount=1.0, lam=1.0)
    finally:
        fleet.close()
    # With no shaping and no discount the return of a decision is the terminal reward of its episode: +1 for even arenas, -1 for odd.
    arenas = batch.obs[:, 0].astype(int)
    for arena, ret in zip(arenas, batch.returns):
        assert ret == pytest.approx(1.0 if arena % 2 == 0 else -1.0, abs=1e-4)


def test_timeout_bootstraps_from_the_final_value(sampler, log):
    server = FakeGym(arenas=2, length=3, timeout=True)
    fleet = make_fleet(server, log)
    try:
        counter = iter(range(10_000))
        batch, stats = fleet.collect(sampler, 60, lambda: Task(0, next(counter), 0), shaping_scale=0.0, discount=1.0, lam=1.0)
    finally:
        fleet.close()
    assert sum(c["timeout"] for c in stats.outcomes.values()) > 0
    assert np.all(np.isfinite(batch.returns))


def test_evaluate_plays_every_task_once_and_stops(sampler, log):
    server = FakeGym(arenas=4, length=3)
    fleet = make_fleet(server, log)
    try:
        tasks = [Task(brain, 1_000_000 + seed, 3) for seed in range(6) for brain in (5, 6)]
        stats = fleet.evaluate(sampler, tasks)
    finally:
        fleet.close()
    assert len(stats.results) == len(tasks)
    assert sorted((r.brain, r.seed) for r in stats.results) == sorted((t.brain, t.seed) for t in tasks)
    assert {reset[1] for reset in server.resets} == {t.seed for t in tasks}
    assert all(r.outcome in ("win", "loss") for r in stats.results)
