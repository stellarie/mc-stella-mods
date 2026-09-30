"""The health guard: probe failures and Java failure lines both restart the server."""

from pathlib import Path

import pytest

from stella_train.health import Guard
from stella_train.rollout import Fleet, Task
from stella_train.runlog import RunLog

from fake_gym import FakeGym


class FakeServer:
    def __init__(self, index, directory: Path):
        self.index = index
        self.latest_log = directory / f"latest-{index}.log"
        self.latest_log.write_text("old line\n", encoding="utf-8")


@pytest.fixture()
def log(tmp_path):
    log = RunLog("health", base=tmp_path)
    yield log
    log.close()


def make_guard(tmp_path, log, verdicts, every=100):
    servers = [FakeServer(i, tmp_path) for i in range(3)]
    restarted = []
    calls = iter(verdicts)
    guard = Guard(log, servers, every, lambda: next(calls), restarted.append)
    return guard, servers, restarted


def test_failed_probe_restarts_only_that_server(tmp_path, log):
    guard, _, restarted = make_guard(tmp_path, log, [[True, False, True]])
    assert guard.check(0) is True
    assert restarted == [[1]]
    assert "gymHealth RESTART server=1" in (log.dir / "log.txt").read_text(encoding="utf-8")


def test_probe_runs_every_interval_and_not_between(tmp_path, log):
    guard, _, restarted = make_guard(tmp_path, log, [[True] * 3, [True] * 3])
    assert guard.check(0) is False
    assert guard.check(50) is False
    assert guard.check(100) is False
    assert restarted == []


def test_java_failure_line_restarts_the_server(tmp_path, log):
    guard, servers, restarted = make_guard(tmp_path, log, [[True] * 3])
    guard.check(0)
    with open(servers[2].latest_log, "a", encoding="utf-8") as handle:
        handle.write("[INFO] gymHealth FAIL arena=3 reason=notHittable\n")
    assert guard.check(10) is True
    assert restarted == [[2]]
    assert guard.check(20) is False


def test_failure_line_before_the_guard_started_is_ignored(tmp_path, log):
    servers = [FakeServer(0, tmp_path)]
    servers[0].latest_log.write_text("gymHealth FAIL old\n", encoding="utf-8")
    guard = Guard(log, servers, 100, lambda: [True], lambda indices: None)
    assert guard.check(0) is False


def test_reconnect_replaces_a_server_connection(tmp_path, log):
    first, second = FakeGym(arenas=2, length=3), FakeGym(arenas=2, length=3)
    first.start()
    fleet = Fleet([first.port], log, seed=1)
    try:
        second.start()
        fleet.reconnect(0, second.port)
        assert fleet.workers[0].is_alive()
        assert fleet.clients[0].port == second.port
    finally:
        fleet.close()
        first.stop = second.stop = True
