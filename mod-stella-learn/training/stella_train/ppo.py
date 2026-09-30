"""PPO with the clipped objective and GAE, on CPU PyTorch."""

from __future__ import annotations

import time
from dataclasses import dataclass

import numpy as np
import torch

from .model import ActorCritic


@dataclass(frozen=True)
class PpoConfig:
    learning_rate: float = 3e-4
    learning_rate_final: float = 3e-5
    discount: float = 0.995
    gae_lambda: float = 0.95
    clip: float = 0.2
    entropy: float = 0.01
    entropy_final: float = 0.001
    value_coefficient: float = 0.5
    epochs: int = 4
    minibatch: int = 4096
    rollout_per_agent: int = 256
    max_grad_norm: float = 0.5
    target_kl: float = 0.03
    log_std_init: float = -0.5
    bow_bias_init: tuple = (-1.0, 1.0, -0.5, -1.0)
    jump_bias_init: tuple = (0.5, -0.5)

    @classmethod
    def from_dict(cls, table: dict) -> "PpoConfig":
        known = {name for name in cls.__dataclass_fields__}
        unknown = set(table) - known
        if unknown:
            raise ValueError(f"unknown ppo settings: {sorted(unknown)}")
        table = dict(table)
        for key in ("bow_bias_init", "jump_bias_init"):
            if key in table:
                table[key] = tuple(table[key])
        return cls(**table)


def gae(rewards: np.ndarray, values: np.ndarray, next_values: np.ndarray, dones: np.ndarray, discount: float, lam: float) -> tuple[np.ndarray, np.ndarray]:
    """Advantages and returns for one trajectory.

    `next_values[t]` is the value of the state after step t: the next step's value while the episode goes on, the value
    of the final observation for a truncated end, and 0 for a real end. `dones[t]` cuts the advantage chain.
    """
    n = len(rewards)
    advantages = np.zeros(n, dtype=np.float32)
    running = 0.0
    for t in range(n - 1, -1, -1):
        delta = rewards[t] + discount * next_values[t] - values[t]
        running = delta + discount * lam * (0.0 if dones[t] else 1.0) * running
        advantages[t] = running
    return advantages, advantages + values


@dataclass
class Batch:
    obs: np.ndarray
    u: np.ndarray
    jump: np.ndarray
    bow: np.ndarray
    logp: np.ndarray
    advantages: np.ndarray
    returns: np.ndarray
    values: np.ndarray

    def __len__(self) -> int:
        return len(self.obs)


class Ppo:
    def __init__(self, model: ActorCritic, config: PpoConfig):
        self.model = model
        self.config = config
        self.optimizer = torch.optim.Adam(model.parameters(), lr=config.learning_rate, eps=1e-5)
        self.rng = np.random.default_rng(0)

    def lr_at(self, progress: float) -> float:
        c = self.config
        return c.learning_rate + (c.learning_rate_final - c.learning_rate) * min(max(progress, 0.0), 1.0)

    def entropy_at(self, progress: float) -> float:
        c = self.config
        return c.entropy + (c.entropy_final - c.entropy) * min(max(progress, 0.0), 1.0)

    def update(self, batch: Batch, progress: float) -> dict:
        c = self.config
        lr = self.lr_at(progress)
        entropy_coef = self.entropy_at(progress)
        for group in self.optimizer.param_groups:
            group["lr"] = lr
        started = time.perf_counter()
        obs = torch.as_tensor(batch.obs, dtype=torch.float32)
        u = torch.as_tensor(batch.u)
        jump = torch.as_tensor(batch.jump)
        bow = torch.as_tensor(batch.bow)
        old_logp = torch.as_tensor(batch.logp)
        advantages = torch.as_tensor(batch.advantages)
        advantages = (advantages - advantages.mean()) / (advantages.std() + 1e-8)
        returns = torch.as_tensor(batch.returns)
        n = len(batch)
        totals = {"policy_loss": 0.0, "value_loss": 0.0, "entropy": 0.0, "kl": 0.0, "clip_fraction": 0.0}
        steps = 0
        stopped = False
        epochs_run = 0
        for epoch in range(c.epochs):
            order = self.rng.permutation(n)
            epochs_run += 1
            for start in range(0, n, c.minibatch):
                index = torch.as_tensor(order[start : start + c.minibatch])
                logp, entropy, value = self.model.evaluate(obs[index], u[index], jump[index], bow[index])
                log_ratio = logp - old_logp[index]
                ratio = log_ratio.exp()
                adv = advantages[index]
                unclipped = ratio * adv
                clipped = torch.clamp(ratio, 1.0 - c.clip, 1.0 + c.clip) * adv
                policy_loss = -torch.min(unclipped, clipped).mean()
                value_loss = 0.5 * ((value - returns[index]) ** 2).mean()
                entropy_mean = entropy.mean()
                loss = policy_loss + c.value_coefficient * value_loss - entropy_coef * entropy_mean
                self.optimizer.zero_grad(set_to_none=True)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(self.model.parameters(), c.max_grad_norm)
                self.optimizer.step()
                with torch.no_grad():
                    kl = ((ratio - 1.0) - log_ratio).mean().item()
                    clip_fraction = ((ratio - 1.0).abs() > c.clip).float().mean().item()
                totals["policy_loss"] += policy_loss.item()
                totals["value_loss"] += value_loss.item()
                totals["entropy"] += entropy_mean.item()
                totals["kl"] += kl
                totals["clip_fraction"] += clip_fraction
                steps += 1
            if totals["kl"] / max(steps, 1) > 1.5 * c.target_kl:
                stopped = True
                break
        metrics = {key: value / max(steps, 1) for key, value in totals.items()}
        explained = 1.0 - float(np.var(batch.returns - batch.values) / (np.var(batch.returns) + 1e-8))
        metrics.update(
            lr=lr, entropy_coef=entropy_coef, epochs_run=epochs_run, early_stop=stopped, minibatches=steps,
            explained_variance=explained, log_std=float(self.model.log_std.mean().item()),
            update_seconds=time.perf_counter() - started,
        )
        return metrics

    def state(self) -> dict:
        return {"model": self.model.state_dict(), "optimizer": self.optimizer.state_dict()}

    def load(self, state: dict) -> None:
        self.model.load_state_dict(state["model"])
        self.optimizer.load_state_dict(state["optimizer"])
