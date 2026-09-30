"""A client for one gym server: handshake, then the step loop."""

from __future__ import annotations

import socket
import time
from dataclasses import dataclass, field

import numpy as np

from . import wire
from .runlog import RunLog


@dataclass
class Stats:
    steps: int = 0
    decisions: int = 0
    episodes_ended: int = 0
    reward_sum: float = 0.0
    started: float = field(default_factory=time.perf_counter)
    finished: float = 0.0


class GymClient:
    def __init__(self, port: int, log: RunLog, name: str, host: str = "127.0.0.1"):
        self.port = port
        self.log = log
        self.name = name
        self.host = host
        self.sock: socket.socket | None = None
        self.hello: wire.Hello | None = None
        self.stats = Stats()

    def connect(self, timeout: float = 120.0) -> wire.Hello:
        """Connects, retrying until the server listens, then reads Hello."""
        deadline = time.monotonic() + timeout
        last: Exception | None = None
        while time.monotonic() < deadline:
            try:
                self.sock = socket.create_connection((self.host, self.port), timeout=5.0)
                break
            except OSError as error:
                last = error
                time.sleep(0.5)
        else:
            raise ConnectionError(f"{self.name}: no gym server on port {self.port}: {last}")
        self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.sock.settimeout(60.0)
        frame_type, body = wire.read_frame(self.sock)
        if frame_type != wire.HELLO:
            raise wire.WireError(f"{self.name}: expected Hello, got frame type {frame_type}")
        self.hello = wire.decode_hello(body)
        if self.hello.version != wire.PROTOCOL_VERSION:
            raise wire.WireError(f"{self.name}: server speaks protocol {self.hello.version}")
        self.log.info("gymClient HELLO name=%s port=%d obs=%d act=%d arenas=%d", self.name, self.port,
                      self.hello.obs_size, self.hello.act_size, self.hello.arenas)
        self.log.event("gymClient HELLO", name=self.name, port=self.port, obs=self.hello.obs_size,
                       act=self.hello.act_size, arenas=self.hello.arenas)
        return self.hello

    def recv_step(self) -> wire.Step:
        frame_type, body = wire.read_frame(self.sock)
        if frame_type == wire.BYE:
            raise ConnectionError(f"{self.name}: server said bye")
        if frame_type != wire.STEP:
            raise wire.WireError(f"{self.name}: expected Step, got frame type {frame_type}")
        return wire.decode_step(body, self.hello.obs_size)

    def send_act(self, step_id: int, agents, actions) -> None:
        self.sock.sendall(wire.encode_act(step_id, agents, actions))

    def send_reset(self, arena: int, seed: int, brain: int = -1, stage: int = -1, ticks: int = 0, body: tuple = (0, 0, 0, 0, 0, 0)) -> None:
        self.sock.sendall(wire.encode_reset(arena, seed, brain, stage, ticks, body))

    def close(self) -> None:
        if self.sock is None:
            return
        try:
            self.sock.sendall(wire.encode_bye())
            # Closing at once could reset the connection before the server reads Bye, so wait for the server to close first.
            self.sock.shutdown(socket.SHUT_WR)
            self.sock.settimeout(5.0)
            while self.sock.recv(1 << 16):
                pass
        except OSError:
            pass
        self.sock.close()
        self.sock = None
        self.log.info("gymClient CLOSE name=%s steps=%d decisions=%d", self.name, self.stats.steps, self.stats.decisions)

    def run_random(self, seconds: float, seed: int, rate_every: float = 10.0, stop=None) -> Stats:
        """Answers every Step with random actions for `seconds`. One rate line goes out every `rate_every` seconds."""
        rng = np.random.default_rng(seed)
        pool = rng.uniform(-1.0, 1.0, size=(256, self.hello.arenas, self.hello.act_size)).astype("<f4")
        pool[..., 4] = (pool[..., 4] > 0).astype("<f4")
        pool[..., 5] = np.floor((pool[..., 5] + 1.0) * 2.0).clip(0, 3)
        resets = 0
        self.stats = Stats()
        deadline = self.stats.started + seconds
        next_rate = self.stats.started + rate_every
        last_steps = last_decisions = 0
        last_time = self.stats.started
        while True:
            now = time.perf_counter()
            if now >= deadline or (stop is not None and stop.is_set()):
                break
            step = self.recv_step()
            self.stats.steps += 1
            self.stats.decisions += len(step.agents)
            self.stats.episodes_ended += int(np.count_nonzero(step.flags & wire.FLAG_TERMINAL))
            self.stats.reward_sum += float(step.rewards.sum())
            # An arena stays idle after a terminal step until it is reset, or when nothing runs yet (the first step).
            ended = step.agents[(step.flags & wire.FLAG_TERMINAL) != 0]
            if self.stats.steps == 1:
                ended = np.arange(self.hello.arenas)
            for arena in ended:
                resets += 1
                self.send_reset(int(arena), resets)
            self.send_act(step.step_id, step.agents, pool[self.stats.steps & 255][: len(step.agents)])
            if now >= next_rate:
                span = now - last_time
                self.log.info("gymClient RATE name=%s steps=%d decisionsPerSec=%.0f episodesEnded=%d", self.name, self.stats.steps,
                              (self.stats.decisions - last_decisions) / span, self.stats.episodes_ended)
                self.log.event("gymClient RATE", name=self.name, steps=self.stats.steps,
                               decisions_per_sec=(self.stats.decisions - last_decisions) / span,
                               steps_per_sec=(self.stats.steps - last_steps) / span, episodes_ended=self.stats.episodes_ended)
                last_steps, last_decisions, last_time = self.stats.steps, self.stats.decisions, now
                next_rate = now + rate_every
        self.stats.finished = time.perf_counter()
        return self.stats
