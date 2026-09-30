"""Starts one real gym server and walks the protocol end to end. Off by default: it needs Java and about a minute.

    STELLA_GYM_SMOKE=1 python -m pytest tests/test_smoke.py
"""

import os
import time

import numpy as np
import pytest

from stella_train import roster, wire
from stella_train.client import GymClient
from stella_train.gym_launch import GymCluster
from stella_train.runlog import RunLog, timestamp

pytestmark = pytest.mark.skipif(os.environ.get("STELLA_GYM_SMOKE") != "1", reason="set STELLA_GYM_SMOKE=1 to start a server")


def wait_for(path, needle, seconds=20):
    text = ""
    for _ in range(seconds * 2):
        text = path.read_text(encoding="utf-8", errors="replace")
        if needle in text:
            return text
        time.sleep(0.5)
    return text


def test_handshake_reset_step_and_bye():
    log = RunLog(f"smoke-{timestamp()}")
    try:
        with GymCluster(1, 4, log, sprint=False, episode_ticks=60) as cluster:
            client = GymClient(cluster.servers[0].port, log, "smoke")
            hello = client.connect()
            assert (hello.obs_size, hello.act_size, hello.arenas) == (116, 6, 4)
            first = client.recv_step()
            assert first.agents.tolist() == []
            client.send_reset(1, 7, roster.brain_index("SwordSweeper"), roster.STAGE_A)
            client.send_act(first.step_id, [], np.zeros((0, 6)))
            started = None
            for _ in range(200):
                step = client.recv_step()
                if len(step.agents) and (step.flags[0] & wire.FLAG_START):
                    started = step
                    break
                client.send_act(step.step_id, [], np.zeros((0, 6)))
            assert started is not None, "the archer never made its first decision"
            assert started.agents.tolist() == [1]
            assert started.obs.shape == (1, 116)
            assert np.all(np.isfinite(started.obs))
            client.send_act(started.step_id, started.agents, np.zeros((1, 6)))
            ended = None
            for _ in range(200):
                step = client.recv_step()
                if len(step.agents) and (step.flags[0] & wire.FLAG_TERMINAL):
                    ended = step
                    break
                client.send_act(step.step_id, step.agents, np.zeros((len(step.agents), 6)))
            assert ended is not None, "the episode never ended"
            client.send_act(ended.step_id, [], np.zeros((0, 6)))
            client.close()
            text = wait_for(cluster.servers[0].latest_log, "gymBridge CLOSE reason=clientBye")
            assert "gymBridge CLOSE reason=clientBye" in text
            assert "gymTerrain BUILD" in text
            assert "gymBot BRAIN" in text and "brain=SwordSweeper" in text
            assert "gymEpisode END" in text
    finally:
        log.close()
