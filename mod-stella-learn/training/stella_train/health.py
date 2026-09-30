"""Guards against a gym server that no longer lets arrows hurt its bot."""

from __future__ import annotations

from typing import Callable

import numpy as np

from . import bodies, roster
from .model import Sample

PROBE_TICKS = 200
PROBE_SEED = roster.EVAL_SEED_BASE + 999_000
FAIL_MARK = "gymHealth FAIL"


class ZeroOffsetArcher:
    """Fires at the aim point with no offset and does not move."""

    def values(self, obs):
        return np.zeros(len(obs), dtype=np.float32)

    def act(self, obs, rng, deterministic=True):
        count = len(obs)
        env = np.zeros((count, 6), dtype=np.float32)
        env[:, 5] = 2
        zero = np.zeros(count, np.float32)
        return Sample(env, env[:, :4].copy(), zero.astype(np.int64), np.full(count, 2, np.int64), zero, zero)


def probe_task():
    from .rollout import Task

    body = bodies.BodyRange(hp_min=10, hp_max=10, armor_max=0, toughness_max=0, protection_max=0, special_permille=0)
    return Task(roster.STATIC, PROBE_SEED, roster.STAGE_A, PROBE_TICKS, body.wire())


class Guard:
    """Runs a probe every `every` decisions and on the first check, and reads the servers' logs for Java-side failures."""

    def __init__(self, log, servers: list, every: int, probe: Callable[[], list[bool]], restart: Callable[[list[int]], None]):
        self.log = log
        self.servers = servers
        self.every = every
        self.probe = probe
        self.restart = restart
        self.next_probe = 0
        self.offsets = {server.index: self._size(server) for server in servers}

    @staticmethod
    def _size(server) -> int:
        path = server.latest_log
        return path.stat().st_size if path.exists() else 0

    def _java_failures(self) -> list[int]:
        failed = []
        for server in self.servers:
            path = server.latest_log
            if not path.exists():
                continue
            with open(path, "rb") as handle:
                handle.seek(self.offsets.get(server.index, 0))
                data = handle.read()
                self.offsets[server.index] = handle.tell()
            if FAIL_MARK in data.decode("utf-8", errors="replace"):
                failed.append(server.index)
        return failed

    def check(self, decisions: int) -> bool:
        """Returns True when a server was restarted."""
        failed = set(self._java_failures())
        for index in failed:
            self.log.warning("gymHealth FAIL server=%d source=java decisions=%d", index, decisions)
            self.log.event("gymHealth FAIL", server=index, source="java", decisions=decisions)
        if decisions >= self.next_probe:
            self.next_probe = decisions + self.every
            for position, ok in enumerate(self.probe()):
                index = self.servers[position].index
                if ok:
                    self.log.info("gymHealth OK server=%d decisions=%d", index, decisions)
                else:
                    failed.add(index)
                    self.log.warning("gymHealth FAIL server=%d source=probe decisions=%d", index, decisions)
                    self.log.event("gymHealth FAIL", server=index, source="probe", decisions=decisions)
        if not failed:
            return False
        ordered = sorted(failed)
        self.restart(ordered)
        for server in self.servers:
            if server.index in failed:
                self.offsets[server.index] = self._size(server)
        for index in ordered:
            self.log.info("gymHealth RESTART server=%d decisions=%d", index, decisions)
            self.log.event("gymHealth RESTART", server=index, decisions=decisions)
        return True
