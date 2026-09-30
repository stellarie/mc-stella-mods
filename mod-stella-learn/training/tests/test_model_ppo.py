"""The network, the sampler that mirrors it, the exported weights, and the PPO update."""

import numpy as np
import pytest
import torch

from stella_train import contract as contract_module
from stella_train import policy_file
from stella_train.model import ActorCritic, FileSampler, Normalizer, Sampler
from stella_train.ppo import Batch, Ppo, PpoConfig, gae


@pytest.fixture(scope="module")
def contract():
    return contract_module.load()


def observations(contract, n=32, seed=0):
    return np.random.default_rng(seed).normal(0.0, 1.0, size=(n, contract.obs_size)).astype(np.float32)


def test_sampler_matches_the_torch_network(contract):
    model = ActorCritic(contract)
    normalizer = Normalizer(contract.obs_size)
    obs = observations(contract)
    normalizer.update(obs * 2.0 + 1.0)
    model.set_normalizer(normalizer)
    sampler = Sampler(model, normalizer)
    sample = sampler.act(obs, np.random.default_rng(1))
    logp, entropy, value = model.evaluate(torch.tensor(obs), torch.tensor(sample.u), torch.tensor(sample.jump), torch.tensor(sample.bow))
    assert np.allclose(logp.detach().numpy(), sample.logp, atol=1e-4)
    assert np.allclose(value.detach().numpy(), sample.value, atol=1e-4)
    assert sample.env.shape == (32, 6)
    assert np.all(np.abs(sample.env[:, :4]) <= 1.0)


def test_exported_weights_give_the_mean_action(contract):
    model = ActorCritic(contract)
    normalizer = Normalizer(contract.obs_size)
    obs = observations(contract)
    normalizer.update(obs * 2.0 - 0.5)
    model.set_normalizer(normalizer)
    policy = model.export(normalizer)
    decoded = policy_file.decode(policy_file.encode(policy))
    mean = Sampler(model, normalizer).act(obs, np.random.default_rng(0), deterministic=True)
    from_file = FileSampler(decoded, contract).act(obs, np.random.default_rng(0))
    assert np.allclose(mean.env[:, :4], from_file.env[:, :4], atol=1e-4)
    assert np.array_equal(mean.env[:, 4:], from_file.env[:, 4:])
    assert decoded.contract_hash == contract.hash


def test_renormalizing_keeps_both_networks(contract):
    model = ActorCritic(contract)
    normalizer = Normalizer(contract.obs_size)
    obs = observations(contract, 64)
    normalizer.update(obs)
    model.set_normalizer(normalizer)
    x = torch.tensor(obs)
    before = (model.heads(x)[0].detach().clone(), model.value(x).detach().clone())
    normalizer.update(obs * 3.0 - 2.0)
    model.renormalize(normalizer)
    assert torch.allclose(model.heads(x)[0], before[0], atol=1e-4)
    assert torch.allclose(model.value(x), before[1], atol=1e-4)


def test_the_bow_bias_starts_the_draw(contract):
    model = ActorCritic(contract, bow_bias=(-1.0, 1.0, -0.5, -1.0))
    sample = Sampler(model, Normalizer(contract.obs_size)).act(observations(contract, 2000), np.random.default_rng(2))
    shares = np.bincount(sample.bow, minlength=4) / 2000
    assert shares[1] > 0.5 and shares[0] < 0.2


def test_gae_matches_a_hand_calculation():
    rewards = np.array([0.0, 0.0, 1.0], dtype=np.float32)
    values = np.array([0.5, 0.5, 0.5], dtype=np.float32)
    next_values = np.array([0.5, 0.5, 0.0], dtype=np.float32)
    dones = np.array([False, False, True])
    advantages, returns = gae(rewards, values, next_values, dones, 0.9, 1.0)
    # delta = [-0.05, -0.05, 0.5]; with lambda 1 the advantages are the discounted sums.
    assert advantages[2] == pytest.approx(0.5)
    assert advantages[1] == pytest.approx(-0.05 + 0.9 * 0.5)
    assert advantages[0] == pytest.approx(-0.05 + 0.9 * (-0.05 + 0.9 * 0.5))
    assert np.allclose(returns, advantages + values)


def test_an_update_raises_the_probability_of_a_rewarded_action(contract):
    torch.manual_seed(0)
    model = ActorCritic(contract)
    normalizer = Normalizer(contract.obs_size)
    model.set_normalizer(normalizer)
    ppo = Ppo(model, PpoConfig(epochs=8, minibatch=256))
    obs = observations(contract, 1024)
    sampler = Sampler(model, normalizer)
    sample = sampler.act(obs, np.random.default_rng(3))
    # Reward the draw (bow index 1) and punish the rest.
    advantage = np.where(sample.bow == 1, 1.0, -1.0).astype(np.float32)
    batch = Batch(obs, sample.u, sample.jump, sample.bow, sample.logp, advantage, advantage + sample.value, sample.value)
    before = Sampler(model, normalizer).act(obs, np.random.default_rng(4)).bow
    metrics = ppo.update(batch, 0.0)
    after = Sampler(model, normalizer).act(obs, np.random.default_rng(4)).bow
    assert (after == 1).mean() > (before == 1).mean()
    assert np.isfinite(metrics["policy_loss"]) and np.isfinite(metrics["value_loss"]) and metrics["kl"] >= 0.0
