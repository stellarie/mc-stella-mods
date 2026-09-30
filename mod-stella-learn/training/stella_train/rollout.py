"""Reads Step frames from all servers into one batch, and answers them.

One thread per server reads its Steps, asks a frozen numpy copy of the network for actions, and sends the Act. The main
thread starts a phase (collect decisions for training, or run a list of eval episodes) and waits until every thread has
parked. A parked thread holds the Step it last read, so the server waits for the update instead of timing out.
"""

from __future__ import annotations

import collections
import threading
import time
from dataclasses import dataclass, field
from typing import Callable

import numpy as np

from . import wire
from .client import GymClient
from .model import Sampler
from .ppo import Batch, gae
from .roster import BRAINS
from .runlog import RunLog

STUCK_STEPS = 400


@dataclass(frozen=True)
class Task:
    """One episode the trainer asks an arena to play."""
    brain: int
    seed: int
    stage: int
    ticks: int = 0
    body: tuple = (0, 0, 0, 0, 0, 0)


@dataclass
class Result:
    brain: int
    seed: int
    stage: int
    outcome: str        # win, loss, timeout, error
    steps: int


@dataclass
class Phase:
    """What the worker threads do until they park."""
    sampler: Sampler
    mode: str                                     # train or eval
    next_task: Callable[[], Task | None]
    remaining: Callable[[], int] = lambda: 0
    deterministic: bool = True
    target_decisions: int = 0
    shaping_scale: float = 0.0
    discount: float = 0.995
    lam: float = 0.95
    lock: threading.Lock = field(default_factory=threading.Lock)
    decisions: int = 0

    def add(self, count: int) -> None:
        with self.lock:
            self.decisions += count


@dataclass
class Stats:
    decisions: int = 0
    episodes: int = 0
    reward_sum: float = 0.0
    shaping_sum: float = 0.0
    episode_return_sum: float = 0.0
    episode_steps_sum: int = 0
    dealt_sum: float = 0.0
    taken_sum: float = 0.0
    bow_counts: np.ndarray = field(default_factory=lambda: np.zeros(4, dtype=np.int64))
    jump_count: int = 0
    acted: int = 0
    outcomes: dict = field(default_factory=lambda: collections.defaultdict(lambda: collections.Counter()))
    results: list = field(default_factory=list)

    def merge(self, other: "Stats") -> None:
        self.decisions += other.decisions
        self.episodes += other.episodes
        self.reward_sum += other.reward_sum
        self.shaping_sum += other.shaping_sum
        self.episode_return_sum += other.episode_return_sum
        self.episode_steps_sum += other.episode_steps_sum
        self.dealt_sum += other.dealt_sum
        self.taken_sum += other.taken_sum
        self.bow_counts += other.bow_counts
        self.jump_count += other.jump_count
        self.acted += other.acted
        for brain, counter in other.outcomes.items():
            self.outcomes[brain].update(counter)
        self.results.extend(other.results)


class _Trajectory:
    __slots__ = ("obs", "u", "jump", "bow", "logp", "value", "reward", "done", "next_value")

    def __init__(self) -> None:
        for name in self.__slots__:
            setattr(self, name, [])

    def __len__(self) -> int:
        return len(self.reward)


class Worker(threading.Thread):
    def __init__(self, index: int, client: GymClient, log: RunLog, seed: int):
        super().__init__(name=f"worker-{index}", daemon=True)
        self.index = index
        self.client = client
        self.log = log
        self.rng = np.random.default_rng(seed)
        self.phase: Phase | None = None
        self.go = threading.Event()
        self.parked = threading.Event()
        self.quit = False
        self.error: BaseException | None = None
        self.carry: wire.Step | None = None
        self.arenas = client.hello.arenas
        self.pending: dict[int, tuple] = {}
        self.requested: dict[int, int] = {}      # arena -> steps since the reset went out
        self.live: set[int] = set()
        self.ep_steps: dict[int, int] = {}
        self.ep_return: dict[int, float] = {}
        self.ep_task: dict[int, Task] = {}
        self.task_of_request: dict[int, Task] = {}
        self.trajectories: dict[int, _Trajectory] = collections.defaultdict(_Trajectory)
        self.stats = Stats()

    # -- control from the main thread ------------------------------------------------------------------------------

    def start_phase(self, phase: Phase) -> None:
        self.phase = phase
        self.stats = Stats()
        self.parked.clear()
        self.go.set()

    def wait_parked(self, timeout: float | None = None) -> bool:
        return self.parked.wait(timeout)

    def stop(self) -> None:
        self.quit = True
        self.go.set()

    def drop_progress(self) -> None:
        """Forget every half-finished episode, for a phase that resets the arenas itself."""
        self.pending.clear()
        self.live.clear()
        self.requested.clear()
        self.ep_steps.clear()
        self.ep_return.clear()
        self.ep_task.clear()
        self.task_of_request.clear()
        self.trajectories.clear()

    # -- the thread -----------------------------------------------------------------------------------------------

    def run(self) -> None:
        try:
            while not self.quit:
                self.go.wait()
                self.go.clear()
                if self.quit:
                    break
                self._pump()
                self.parked.set()
        except BaseException as error:  # the main thread reports it; a dead worker must not hang the wait
            self.error = error
            self.parked.set()

    def _finished(self, phase: Phase) -> bool:
        if phase.mode == "train":
            return phase.decisions >= phase.target_decisions
        return phase.remaining() == 0 and not self.live and not self.requested

    def _pump(self) -> None:
        phase = self.phase
        while not self.quit:
            step = self.carry if self.carry is not None else self.client.recv_step()
            self.carry = None
            if self._finished(phase):
                self.carry = step
                return
            self._process(step, phase)

    def _process(self, step: wire.Step, phase: Phase) -> None:
        agents = step.agents
        count = len(agents)
        sample = phase.sampler.act(step.obs, self.rng, deterministic=phase.mode == "eval" and phase.deterministic) if count else None
        done_flags = (step.flags & wire.FLAG_TERMINAL) != 0
        acting = []
        for i in range(count):
            arena = int(agents[i])
            flags = int(step.flags[i])
            if flags & wire.FLAG_START:
                self.pending.pop(arena, None)
                self.live.add(arena)
                self.requested.pop(arena, None)
                self.ep_steps[arena] = 0
                self.ep_return[arena] = 0.0
                self.ep_task[arena] = self.task_of_request.pop(arena, None)
            pending = self.pending.pop(arena, None)
            terminal = bool(done_flags[i])
            reward = float(step.rewards[i])
            shaping = float(step.shaping[i])
            if pending is not None and phase.mode == "train":
                shaped = reward + phase.shaping_scale * shaping
                truncated = bool(flags & wire.FLAG_TRUNCATED) or bool(flags & wire.FLAG_ERROR)
                next_value = 0.0 if (terminal and not truncated) else float(sample.value[i])
                trajectory = self.trajectories[arena]
                obs, u, jump, bow, logp, value = pending
                trajectory.obs.append(obs)
                trajectory.u.append(u)
                trajectory.jump.append(jump)
                trajectory.bow.append(bow)
                trajectory.logp.append(logp)
                trajectory.value.append(value)
                trajectory.reward.append(shaped)
                trajectory.done.append(terminal)
                trajectory.next_value.append(next_value)
                self.stats.decisions += 1
                self.stats.shaping_sum += shaping
                # Damage in units of the bot's health: what the archer dealt is negative for it to take.
                self.stats.dealt_sum += max(shaping, 0.0)
                self.stats.taken_sum += max(-shaping, 0.0)
                self.stats.reward_sum += reward
                phase.add(1)
            self.ep_steps[arena] = self.ep_steps.get(arena, 0) + 1
            self.ep_return[arena] = self.ep_return.get(arena, 0.0) + reward + phase.shaping_scale * shaping
            if terminal:
                self._finish_episode(arena, flags, reward, phase)
            else:
                self.pending[arena] = (step.obs[i].copy(), sample.u[i], int(sample.jump[i]), int(sample.bow[i]),
                                       float(sample.logp[i]), float(sample.value[i]))
                acting.append(i)
        resets = []
        for arena in range(self.arenas):
            if arena in self.live or arena in self.requested:
                if arena in self.requested:
                    self.requested[arena] += 1
                    if self.requested[arena] > STUCK_STEPS:
                        self.log.warning("rollout STUCK server=%d arena=%d reset repeated after %d steps", self.index, arena, STUCK_STEPS)
                        task = self.task_of_request.get(arena)
                        if task is not None:
                            resets.append((arena, task))
                        self.requested[arena] = 0
                continue
            task = phase.next_task()
            if task is None:
                continue
            self.requested[arena] = 0
            self.task_of_request[arena] = task
            resets.append((arena, task))
        for arena, task in resets:
            self.client.send_reset(arena, task.seed, task.brain, task.stage, task.ticks, task.body)
        if count:
            if acting and phase.mode == "train":
                self.stats.bow_counts += np.bincount(sample.bow[acting], minlength=4)
                self.stats.jump_count += int(sample.jump[acting].sum())
                self.stats.acted += len(acting)
            self.client.send_act(step.step_id, agents[acting], sample.env[acting] if acting else np.zeros((0, 6), dtype=np.float32))
        else:
            self.client.send_act(step.step_id, [], np.zeros((0, 6), dtype=np.float32))
        self.client.stats.steps += 1
        self.client.stats.decisions += count

    def _finish_episode(self, arena: int, flags: int, reward: float, phase: Phase) -> None:
        task = self.ep_task.pop(arena, None)
        self.live.discard(arena)
        steps = self.ep_steps.pop(arena, 0)
        episode_return = self.ep_return.pop(arena, 0.0)
        if flags & wire.FLAG_ERROR:
            outcome = "error"
        elif flags & wire.FLAG_WIN:
            outcome = "win"
        elif flags & wire.FLAG_TRUNCATED:
            outcome = "timeout"
        else:
            outcome = "loss"
        brain = task.brain if task is not None else -1
        self.stats.episodes += 1
        self.stats.episode_return_sum += episode_return
        self.stats.episode_steps_sum += steps
        self.stats.outcomes[brain][outcome] += 1
        if phase.mode == "eval":
            if task is not None:
                self.stats.results.append(Result(task.brain, task.seed, task.stage, outcome, steps))

    # -- batch ---------------------------------------------------------------------------------------------------

    def take_batch(self, discount: float, lam: float) -> Batch | None:
        parts = []
        for trajectory in self.trajectories.values():
            if not len(trajectory):
                continue
            rewards = np.asarray(trajectory.reward, dtype=np.float32)
            values = np.asarray(trajectory.value, dtype=np.float32)
            next_values = np.asarray(trajectory.next_value, dtype=np.float32)
            dones = np.asarray(trajectory.done, dtype=bool)
            advantages, returns = gae(rewards, values, next_values, dones, discount, lam)
            parts.append(Batch(
                obs=np.asarray(trajectory.obs, dtype=np.float32), u=np.asarray(trajectory.u, dtype=np.float32),
                jump=np.asarray(trajectory.jump, dtype=np.int64), bow=np.asarray(trajectory.bow, dtype=np.int64),
                logp=np.asarray(trajectory.logp, dtype=np.float32), advantages=advantages, returns=returns.astype(np.float32),
                values=values,
            ))
        self.trajectories.clear()
        if not parts:
            return None
        return Batch(*(np.concatenate([getattr(p, name) for p in parts]) for name in
                       ("obs", "u", "jump", "bow", "logp", "advantages", "returns", "values")))


class Fleet:
    def __init__(self, ports: list[int], log: RunLog, seed: int = 1):
        self.log = log
        self.clients = [GymClient(port, log, f"server{i}") for i, port in enumerate(ports)]
        for client in self.clients:
            client.connect()
        self.workers = [Worker(i, client, log, seed * 1009 + i) for i, client in enumerate(self.clients)]
        for worker in self.workers:
            worker.start()
        self.arenas = self.workers[0].arenas

    @property
    def agent_count(self) -> int:
        return sum(worker.arenas for worker in self.workers)

    def _run(self, phase: Phase, timeout: float = 3600.0) -> None:
        for worker in self.workers:
            worker.start_phase(phase)
        deadline = time.monotonic() + timeout
        for worker in self.workers:
            while not worker.wait_parked(1.0):
                if time.monotonic() > deadline:
                    raise TimeoutError(f"worker {worker.index} did not park within {timeout:.0f} s")
                if not worker.is_alive():
                    break
            if worker.error is not None:
                raise RuntimeError(f"worker {worker.index} failed: {worker.error!r}") from worker.error

    def collect(self, sampler: Sampler, decisions: int, next_task, shaping_scale: float, discount: float, lam: float):
        phase = Phase(sampler, "train", next_task, target_decisions=decisions, shaping_scale=shaping_scale, discount=discount, lam=lam)
        self._run(phase)
        stats = Stats()
        batches = []
        for worker in self.workers:
            stats.merge(worker.stats)
            batch = worker.take_batch(discount, lam)
            if batch is not None:
                batches.append(batch)
        batch = Batch(*(np.concatenate([getattr(b, name) for b in batches]) for name in
                        ("obs", "u", "jump", "bow", "logp", "advantages", "returns", "values")))
        return batch, stats

    def evaluate(self, sampler: Sampler, tasks: list[Task], timeout: float = 3600.0, deterministic: bool = True) -> Stats:
        """Plays `tasks` with the mean actions. The arenas reset first, so training episodes in flight are dropped."""
        queue = collections.deque(tasks)
        lock = threading.Lock()

        def next_task() -> Task | None:
            with lock:
                return queue.popleft() if queue else None

        for worker in self.workers:
            worker.drop_progress()
        phase = Phase(sampler, "eval", next_task, deterministic=deterministic, remaining=lambda: len(queue))
        self._run(phase, timeout)
        stats = Stats()
        for worker in self.workers:
            stats.merge(worker.stats)
            worker.drop_progress()
        return stats

    def close(self) -> None:
        for worker in self.workers:
            worker.stop()
        for client in self.clients:
            try:
                client.close()
            except OSError:
                pass
