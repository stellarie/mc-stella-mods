"""The actor and the critic, input normalization, and the numpy copy that sampling threads use.

The actor has the shape the contract fixes: observation in, two tanh layers, then 4 continuous means, 2 jump logits, and
4 bow logits. The critic has the same hidden layers and one output. It never ships.

The continuous heads are Gaussian in the space before tanh. The environment gets `tanh(u)`. The shipped model uses
`tanh(mean)`, which is what the Java side computes from the raw outputs.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np
import torch
from torch import nn

from .contract import Contract
from .policy_file import Layer, Policy, make_policy

HALF_LOG_2PI = 0.5 * math.log(2.0 * math.pi)
STD_FLOOR = 5e-2
MAX_NORMALIZER_COUNT = 5e7


class Normalizer:
    """Running mean and variance of the scaled observation. Updated between rollouts, never during one."""

    def __init__(self, size: int):
        self.mean = np.zeros(size, dtype=np.float64)
        self.var = np.ones(size, dtype=np.float64)
        self.count = 1e-4

    def update(self, batch: np.ndarray) -> None:
        batch = np.asarray(batch, dtype=np.float64)
        n = batch.shape[0]
        if n == 0:
            return
        b_mean = batch.mean(axis=0)
        b_var = batch.var(axis=0)
        delta = b_mean - self.mean
        total = self.count + n
        self.mean = self.mean + delta * n / total
        m_a = self.var * self.count
        m_b = b_var * n
        self.var = (m_a + m_b + delta**2 * self.count * n / total) / total
        self.count = min(total, MAX_NORMALIZER_COUNT)

    @property
    def std(self) -> np.ndarray:
        return np.maximum(np.sqrt(self.var), STD_FLOOR)

    def state(self) -> dict:
        return {"mean": self.mean.copy(), "var": self.var.copy(), "count": self.count}

    def load(self, state: dict) -> None:
        self.mean = np.asarray(state["mean"], dtype=np.float64).copy()
        self.var = np.asarray(state["var"], dtype=np.float64).copy()
        self.count = float(state["count"])


def _mlp(inputs: int, hidden: tuple[int, ...], outputs: int) -> nn.Sequential:
    layers: list[nn.Module] = []
    size = inputs
    for width in hidden:
        layers += [nn.Linear(size, width), nn.Tanh()]
        size = width
    layers.append(nn.Linear(size, outputs))
    return nn.Sequential(*layers)


def _init(net: nn.Sequential, last_gain: float) -> None:
    linears = [m for m in net if isinstance(m, nn.Linear)]
    for index, layer in enumerate(linears):
        gain = last_gain if index == len(linears) - 1 else math.sqrt(2.0)
        nn.init.orthogonal_(layer.weight, gain)
        nn.init.zeros_(layer.bias)


class ActorCritic(nn.Module):
    def __init__(self, contract: Contract, log_std_init: float = -0.5, bow_bias: tuple[float, ...] | None = None, jump_bias: tuple[float, ...] | None = None):
        super().__init__()
        self.contract = contract
        self.n_cont = len(contract.continuous)
        self.n_jump = contract.jump_choices
        self.n_bow = contract.bow_choices
        self.actor = _mlp(contract.obs_size, contract.hidden, contract.out_size)
        self.critic = _mlp(contract.obs_size, contract.hidden, 1)
        _init(self.actor, 0.01)
        _init(self.critic, 1.0)
        # A start that draws the bow more often than it lets go gives the first arrows a chance to fly and land.
        last = [m for m in self.actor if isinstance(m, nn.Linear)][-1]
        with torch.no_grad():
            if bow_bias is not None:
                last.bias[self.n_cont + self.n_jump :] = torch.tensor(bow_bias, dtype=torch.float32)
            if jump_bias is not None:
                last.bias[self.n_cont : self.n_cont + self.n_jump] = torch.tensor(jump_bias, dtype=torch.float32)
        self.log_std = nn.Parameter(torch.full((self.n_cont,), float(log_std_init)))
        self.register_buffer("obs_mean", torch.zeros(contract.obs_size))
        self.register_buffer("obs_std", torch.ones(contract.obs_size))

    def set_normalizer(self, normalizer: Normalizer) -> None:
        self.obs_mean.copy_(torch.as_tensor(normalizer.mean, dtype=torch.float32))
        self.obs_std.copy_(torch.as_tensor(normalizer.std, dtype=torch.float32))

    @torch.no_grad()
    def renormalize(self, normalizer: Normalizer) -> None:
        """Moves to new input statistics without changing what either network computes.

        The first layer absorbs the change, so the policy that collected the last batch is the policy that trains on the next.
        """
        new_mean = torch.as_tensor(normalizer.mean, dtype=torch.float32)
        new_std = torch.as_tensor(normalizer.std, dtype=torch.float32)
        shift = (new_mean - self.obs_mean) / self.obs_std
        scale = new_std / self.obs_std
        for net in (self.actor, self.critic):
            first = next(m for m in net if isinstance(m, nn.Linear))
            first.bias.add_(first.weight @ shift)
            first.weight.mul_(scale[None, :])
        self.obs_mean.copy_(new_mean)
        self.obs_std.copy_(new_std)

    def normalize(self, obs: torch.Tensor) -> torch.Tensor:
        return (obs - self.obs_mean) / self.obs_std

    def heads(self, obs: torch.Tensor):
        out = self.actor(self.normalize(obs))
        mean = out[:, : self.n_cont]
        jump = out[:, self.n_cont : self.n_cont + self.n_jump]
        bow = out[:, self.n_cont + self.n_jump :]
        return mean, jump, bow

    def value(self, obs: torch.Tensor) -> torch.Tensor:
        return self.critic(self.normalize(obs)).squeeze(-1)

    def evaluate(self, obs: torch.Tensor, u: torch.Tensor, jump: torch.Tensor, bow: torch.Tensor):
        """Log probability, entropy, and value of the stored actions under the current network."""
        mean, jump_logits, bow_logits = self.heads(obs)
        std = self.log_std.exp()
        logp_cont = (-0.5 * ((u - mean) / std) ** 2 - self.log_std - HALF_LOG_2PI).sum(-1)
        jump_dist = torch.distributions.Categorical(logits=jump_logits)
        bow_dist = torch.distributions.Categorical(logits=bow_logits)
        logp = logp_cont + jump_dist.log_prob(jump) + bow_dist.log_prob(bow)
        entropy_cont = (self.log_std + 0.5 + HALF_LOG_2PI).sum().expand(obs.shape[0])
        entropy = entropy_cont + jump_dist.entropy() + bow_dist.entropy()
        return logp, entropy, self.value(obs)

    def export(self, normalizer: Normalizer) -> Policy:
        linears = [m for m in self.actor if isinstance(m, nn.Linear)]
        layers = [Layer(m.weight.detach().numpy().astype(np.float32).copy(), m.bias.detach().numpy().astype(np.float32).copy())
                  for m in linears]
        return make_policy(self.contract, layers, normalizer.mean.astype(np.float32), normalizer.std.astype(np.float32))


@dataclass
class Sample:
    """Actions for a batch of agents. `env` is what the wire carries; the rest is what the update needs."""
    env: np.ndarray
    u: np.ndarray
    jump: np.ndarray
    bow: np.ndarray
    logp: np.ndarray
    value: np.ndarray


class Sampler:
    """A frozen numpy copy of the network. Sampling threads use it, so they never touch torch state mid-update."""

    def __init__(self, model: ActorCritic, normalizer: Normalizer):
        self.mean = normalizer.mean.astype(np.float32)
        self.std = normalizer.std.astype(np.float32)
        self.actor = [(m.weight.detach().numpy().copy(), m.bias.detach().numpy().copy()) for m in model.actor if isinstance(m, nn.Linear)]
        self.critic = [(m.weight.detach().numpy().copy(), m.bias.detach().numpy().copy()) for m in model.critic if isinstance(m, nn.Linear)]
        self.log_std = model.log_std.detach().numpy().astype(np.float32).copy()
        self.n_cont = model.n_cont
        self.n_jump = model.n_jump

    @staticmethod
    def _run(layers, x):
        last = len(layers) - 1
        for index, (w, b) in enumerate(layers):
            x = x @ w.T + b
            if index < last:
                x = np.tanh(x)
        return x

    def values(self, obs: np.ndarray) -> np.ndarray:
        x = ((obs - self.mean) / self.std).astype(np.float32)
        return self._run(self.critic, x)[:, 0]

    def act(self, obs: np.ndarray, rng: np.random.Generator, deterministic: bool = False) -> Sample:
        x = ((obs - self.mean) / self.std).astype(np.float32)
        out = self._run(self.actor, x)
        mean = out[:, : self.n_cont]
        jump_logits = out[:, self.n_cont : self.n_cont + self.n_jump]
        bow_logits = out[:, self.n_cont + self.n_jump :]
        std = np.exp(self.log_std)
        n = obs.shape[0]
        if deterministic:
            u = mean.copy()
            jump = jump_logits.argmax(-1)
            bow = bow_logits.argmax(-1)
        else:
            u = mean + std * rng.standard_normal(mean.shape).astype(np.float32)
            jump = _sample_categorical(jump_logits, rng)
            bow = _sample_categorical(bow_logits, rng)
        logp = (-0.5 * ((u - mean) / std) ** 2 - self.log_std - HALF_LOG_2PI).sum(-1)
        logp = logp + _log_prob(jump_logits, jump) + _log_prob(bow_logits, bow)
        value = self._run(self.critic, x)[:, 0]
        env = np.empty((n, self.n_cont + 2), dtype=np.float32)
        env[:, : self.n_cont] = np.tanh(u)
        env[:, self.n_cont] = jump
        env[:, self.n_cont + 1] = bow
        return Sample(env, u.astype(np.float32), jump.astype(np.int64), bow.astype(np.int64), logp.astype(np.float32), value.astype(np.float32))


def _log_softmax(logits: np.ndarray) -> np.ndarray:
    shifted = logits - logits.max(axis=-1, keepdims=True)
    return shifted - np.log(np.exp(shifted).sum(axis=-1, keepdims=True))


def _log_prob(logits: np.ndarray, choice: np.ndarray) -> np.ndarray:
    return np.take_along_axis(_log_softmax(logits), choice[:, None], axis=-1)[:, 0]


def _sample_categorical(logits: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    gumbel = -np.log(-np.log(rng.random(logits.shape).clip(1e-12, 1.0 - 1e-12)))
    return (logits + gumbel).argmax(-1)


class FileSampler:
    """Plays an exported weights file with its mean actions, the way the Java network does."""

    def __init__(self, policy, contract):
        self.policy = policy
        self.n_cont = len(contract.continuous)
        self.n_jump = contract.jump_choices

    def values(self, obs: np.ndarray) -> np.ndarray:
        return np.zeros(len(obs), dtype=np.float32)

    def act(self, obs: np.ndarray, rng: np.random.Generator, deterministic: bool = True) -> Sample:
        raw = self.policy.forward(obs)
        n = len(obs)
        jump = raw[:, self.n_cont : self.n_cont + self.n_jump].argmax(-1)
        bow = raw[:, self.n_cont + self.n_jump :].argmax(-1)
        env = np.empty((n, self.n_cont + 2), dtype=np.float32)
        env[:, : self.n_cont] = np.tanh(raw[:, : self.n_cont])
        env[:, self.n_cont] = jump
        env[:, self.n_cont + 1] = bow
        zeros = np.zeros(n, dtype=np.float32)
        return Sample(env, raw[:, : self.n_cont].astype(np.float32), jump.astype(np.int64), bow.astype(np.int64), zeros, zeros)
