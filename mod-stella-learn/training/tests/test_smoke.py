"""Starts one real gym server and walks the protocol end to end. Off by default: it needs Java and about a minute.

    STELLA_GYM_SMOKE=1 python -m pytest tests/test_smoke.py
"""

import os

import numpy as np
import pytest

from stella_train import wire
from stella_train.client import GymClient
from stella_train.gym_launch import GymCluster
from stella_train.runlog import RunLog, timestamp

pytestmark = pytest.mark.skipif(os.environ.get("STELLA_GYM_SMOKE") != "1", reason="set STELLA_GYM_SMOKE=1 to start a server")


def test_handshake_reset_step_and_bye():
    log = RunLog(f"smoke-{timestamp()}")
    try:
        with GymCluster(1, 4, log, sprint=False) as cluster:
            client = GymClient(cluster.servers[0].port, log, "smoke")
            hello = client.connect()
            assert (hello.obs_size, hello.act_size, hello.arenas) == (16, 4, 4)
            first = client.recv_step()
            assert first.agents.tolist() == [0, 1, 2, 3]
            assert np.all(first.flags & wire.FLAG_START)
            client.send_act(first.step_id, first.agents, np.zeros((4, 4)))
            second = client.recv_step()
            assert second.step_id == first.step_id + 1
            assert not np.any(second.flags & wire.FLAG_START)
            client.send_reset(2, 7)
            after = client.recv_step()
            assert after.step_id == second.step_id + 1
            assert (after.flags[2] & wire.FLAG_START) and not (after.flags[0] & wire.FLAG_START)
            client.send_act(after.step_id, after.agents, np.zeros((4, 4)))
            client.close()
            text = ""
            for _ in range(20):
                text = cluster.servers[0].latest_log.read_text(encoding="utf-8", errors="replace")
                if "gymBridge CLOSE reason=clientBye" in text:
                    break
                __import__("time").sleep(0.5)
            assert "gymBridge CLOSE reason=clientBye" in text
            assert "gymBridge RESET arena=2" in text
    finally:
        log.close()
