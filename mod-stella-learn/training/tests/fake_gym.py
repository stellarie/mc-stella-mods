"""A stand-in gym server for the trainer tests. It speaks the wire protocol and plays scripted episodes."""

from __future__ import annotations

import socket
import struct
import threading

import numpy as np

from stella_train import wire


class FakeGym(threading.Thread):
    """Every episode lasts `length` decisions. Arenas with an even index win, odd ones lose, unless `timeout` is set."""

    def __init__(self, arenas: int, length: int, obs_size: int = 121, act_size: int = 6, contract_hash: bytes = bytes(32), timeout: bool = False):
        super().__init__(daemon=True)
        self.arenas = arenas
        self.length = length
        self.obs_size = obs_size
        self.act_size = act_size
        self.hash = contract_hash
        self.timeout = timeout
        self.server = socket.socket()
        self.server.bind(("127.0.0.1", 0))
        self.server.listen(1)
        self.port = self.server.getsockname()[1]
        self.time = [-1] * arenas          # -1 idle, otherwise decisions played
        self.starting = [False] * arenas
        self.resets: list[tuple] = []
        self.actions: list[tuple] = []
        self.steps = 0
        self.stop = False

    def run(self) -> None:
        conn, _ = self.server.accept()
        conn.sendall(wire.encode_hello(self.obs_size, self.act_size, self.arenas, self.hash))
        step_id = 0
        try:
            while not self.stop:
                step_id += 1
                agents, flags, rewards, shaping, obs = [], [], [], [], []
                for arena in range(self.arenas):
                    if self.starting[arena]:
                        self.starting[arena] = False
                        self.time[arena] = 0
                        agents.append(arena)
                        flags.append(wire.FLAG_START)
                        rewards.append(0.0)
                        shaping.append(0.0)
                    elif self.time[arena] >= 0:
                        self.time[arena] += 1
                        agents.append(arena)
                        shaping.append(0.25)
                        if self.time[arena] >= self.length:
                            self.time[arena] = -1
                            if self.timeout:
                                flags.append(wire.FLAG_TERMINAL | wire.FLAG_TRUNCATED)
                                rewards.append(0.1)
                            elif arena % 2 == 0:
                                flags.append(wire.FLAG_TERMINAL | wire.FLAG_WIN)
                                rewards.append(1.0)
                            else:
                                flags.append(wire.FLAG_TERMINAL)
                                rewards.append(-1.0)
                        else:
                            flags.append(0)
                            rewards.append(0.0)
                    else:
                        continue
                    row = np.zeros(self.obs_size, dtype="<f4")
                    row[0] = arena
                    row[1] = max(self.time[arena], 0)
                    obs.append(row)
                conn.sendall(wire.encode_step(step_id, agents, flags, rewards, shaping, np.array(obs).reshape(len(agents), self.obs_size)))
                self.steps += 1
                while True:
                    frame_type, body = wire.read_frame(conn)
                    if frame_type == wire.RESET:
                        arena, seed, brain, stage, ticks = wire.decode_reset(body)
                        self.resets.append((arena, seed, brain, stage, ticks))
                        self.starting[arena] = True
                    elif frame_type == wire.ACT:
                        sid, ids, acts = wire.decode_act(body, self.act_size)
                        assert sid == step_id
                        self.actions.append((sid, ids.copy(), acts.copy()))
                        break
                    elif frame_type == wire.BYE:
                        return
        except (ConnectionError, OSError, struct.error):
            pass
        finally:
            conn.close()
            self.server.close()
