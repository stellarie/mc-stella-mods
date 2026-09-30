"""PPO training of the archer against the gym.

    python -m stella_train.train --config configs/archer.toml

The run directory `runs/{name}-{timestamp}` holds `log.txt`, `events.jsonl`, `cost.json`, and `checkpoints/`.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import subprocess
import sys
import time
import tomllib
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch

from . import roster
from .bench import cpu_model, git, java_version, ram_gb
from .contract import load as load_contract
from .curriculum import Curriculum
from .gym_launch import GymCluster, prepare_spec
from .model import ActorCritic, Normalizer, Sampler
from .ppo import Ppo, PpoConfig
from .rollout import Fleet
from .runlog import RUNS_DIR, RunLog, timestamp

OBS_SAMPLE_ROWS = 4000


class Trainer:
    def __init__(self, config: dict, log: RunLog, run_dir: Path):
        self.config = config
        self.log = log
        self.run_dir = run_dir
        self.run = config["run"]
        self.contract = load_contract()
        from . import bodies
        bodies.configure(config["body"])
        self.ppo_config = PpoConfig.from_dict(config["ppo"])
        torch.manual_seed(self.run["seed"])
        self.model = ActorCritic(self.contract, self.ppo_config.log_std_init, self.ppo_config.bow_bias_init, self.ppo_config.jump_bias_init)
        self.normalizer = Normalizer(self.contract.obs_size)
        self.model.set_normalizer(self.normalizer)
        self.ppo = Ppo(self.model, self.ppo_config)
        self.budget = int(self.run["budget_decisions"])
        self.curriculum = Curriculum(config["curriculum"], self.budget, log, seed=self.run["seed"])
        self.sampler = Sampler(self.model, self.normalizer)
        self.decisions = 0
        self.updates = 0
        self.wall_before = 0.0
        self.obs_sample = np.zeros((0, self.contract.obs_size), dtype=np.float32)
        self.rng = np.random.default_rng(self.run["seed"] + 1)
        self.next_eval = int(self.run["eval_every_decisions"])
        self.next_checkpoint = int(self.run["checkpoint_every_decisions"])
        self.started = time.monotonic()
        self.wall_start_iso = datetime.now(timezone.utc).isoformat(timespec="seconds")
        self.machine = {"cpu_model": cpu_model(), "logical_cores": os.cpu_count(), "ram_gb": ram_gb(), "os": platform.platform(),
                        "python": platform.python_version(), "torch": torch.__version__}
        self.servers = int(self.run["servers"])
        self.arenas = int(self.run["arenas"])

    # -- checkpoints ------------------------------------------------------------------------------------------------

    def wall_seconds(self) -> float:
        return self.wall_before + time.monotonic() - self.started

    def save_checkpoint(self, name: str) -> Path:
        directory = self.run_dir / "checkpoints"
        directory.mkdir(exist_ok=True)
        path = directory / name
        torch.save({
            "model": self.model.state_dict(), "optimizer": self.ppo.optimizer.state_dict(), "normalizer": self.normalizer.state(),
            "decisions": self.decisions, "updates": self.updates, "curriculum": self.curriculum.state(), "obs_sample": self.obs_sample,
            "wall_seconds": self.wall_seconds(),
        }, path)
        self.log.info("trainCheckpoint SAVE path=%s decisions=%d updates=%d stage=%s", path.name, self.decisions, self.updates,
                      roster.STAGE_NAMES[self.curriculum.stage])
        self.log.event("trainCheckpoint SAVE", path=path.name, decisions=self.decisions, updates=self.updates, stage=roster.STAGE_NAMES[self.curriculum.stage])
        return path

    def resume(self, path: Path) -> None:
        state = torch.load(path, map_location="cpu", weights_only=False)
        self.model.load_state_dict(state["model"])
        self.ppo.optimizer.load_state_dict(state["optimizer"])
        self.normalizer.load(state["normalizer"])
        self.decisions = state["decisions"]
        self.updates = state["updates"]
        self.curriculum.load(state["curriculum"])
        self.obs_sample = state.get("obs_sample", self.obs_sample)
        self.wall_before = state.get("wall_seconds", 0.0)
        self.model.set_normalizer(self.normalizer)
        self.sampler = Sampler(self.model, self.normalizer)
        every = int(self.run["eval_every_decisions"])
        self.next_eval = (self.decisions // every + 1) * every
        every = int(self.run["checkpoint_every_decisions"])
        self.next_checkpoint = (self.decisions // every + 1) * every
        self.log.info("train RESUME path=%s decisions=%d updates=%d stage=%s", path, self.decisions, self.updates,
                      roster.STAGE_NAMES[self.curriculum.stage])

    def widen_from_checkpoint(self, path: Path) -> None:
        """Takes the actor of an older, narrower contract: new input columns start at zero weight, the critic starts fresh."""
        state = torch.load(path, map_location="cpu", weights_only=False)
        new_names = list(self.contract.names)
        added = {"target.last_hit", "target.mean_hit", "target.hits_needed", "target.ticks_since_hit", "target.has_hit"}
        old_names = [name for name in new_names if name not in added]
        old_weight = state["model"]["actor.0.weight"]
        if old_weight.shape[1] != len(old_names):
            raise ValueError(f"the checkpoint has {old_weight.shape[1]} inputs, the older layout has {len(old_names)}")
        actor = {key[len("actor."):]: value for key, value in state["model"].items() if key.startswith("actor.")}
        wide = torch.zeros(old_weight.shape[0], len(new_names))
        for column, name in enumerate(old_names):
            wide[:, new_names.index(name)] = old_weight[:, column]
        actor["0.weight"] = wide
        self.model.actor.load_state_dict(actor)
        self.model.log_std.data.copy_(state["model"]["log_std"])
        old_stats = state["normalizer"]
        mean = np.zeros(len(new_names))
        var = np.ones(len(new_names))
        for column, name in enumerate(old_names):
            mean[new_names.index(name)] = old_stats["mean"][column]
            var[new_names.index(name)] = old_stats["var"][column]
        self.normalizer.load({"mean": mean, "var": var, "count": old_stats["count"]})
        self.decisions = state["decisions"]
        self.updates = state["updates"]
        self.curriculum.load(state["curriculum"])
        self.wall_before = state.get("wall_seconds", 0.0)
        self.model.set_normalizer(self.normalizer)
        self.restart_stage_a(keep_stage=True)
        every = int(self.run["eval_every_decisions"])
        self.next_eval = (self.decisions // every + 1) * every
        every = int(self.run["checkpoint_every_decisions"])
        self.next_checkpoint = (self.decisions // every + 1) * every
        self.log.info("train WIDEN path=%s from=%d to=%d decisions=%d", path, len(old_names), len(new_names), self.decisions)
        self.log.event("train WIDEN", path=str(path), old_inputs=len(old_names), new_inputs=len(new_names), decisions=self.decisions)

    def shrink_aim_rows(self, factor: float) -> None:
        """The yaw and pitch outputs changed meaning (offsets from the target direction), so their rows start near zero."""
        last = [m for m in self.model.actor if isinstance(m, torch.nn.Linear)][-1]
        rows = [self.contract.continuous.index("yaw"), self.contract.continuous.index("pitch")]
        with torch.no_grad():
            last.weight[rows] *= factor
            last.bias[rows] *= factor
        self.sampler = Sampler(self.model, self.normalizer)
        self.log.info("train SHRINK rows=%s factor=%.2f", rows, factor)
        self.log.event("train SHRINK", rows=rows, factor=factor)

    def restart_stage_a(self, keep_stage: bool = False) -> None:
        """Keeps the actor, gives the critic fresh weights, and starts Stage A again at the current decision count."""
        from .model import _init, _mlp
        self.model.critic = _mlp(self.contract.obs_size, self.contract.hidden, 1)
        _init(self.model.critic, 1.0)
        self.ppo = Ppo(self.model, self.ppo_config)
        if not keep_stage:
            self.curriculum.stage = roster.STAGE_A
            self.curriculum.stage_start = self.decisions
            self.curriculum.best = -1.0
            self.curriculum.stale = 0
        self.model.set_normalizer(self.normalizer)
        self.sampler = Sampler(self.model, self.normalizer)
        self.log.info("train RESTART stage=A decisions=%d critic=reinitialized actor=kept", self.decisions)
        self.log.event("train RESTART", stage="A", decisions=self.decisions)

    def write_cost(self, dps: float | None = None) -> None:
        wall = self.wall_seconds()
        record = {
            "run": self.run_dir.name, "started": self.wall_start_iso, "updated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "wall_seconds": wall, "total_decisions": self.decisions, "updates": self.updates, "budget_decisions": self.budget,
            "servers": self.servers, "arenas_per_server": self.arenas, "arenas_total": self.servers * self.arenas,
            "decisions_per_second": self.decisions / wall if wall else 0.0, "stage": roster.STAGE_NAMES[self.curriculum.stage],
            "machine": self.machine, "commit": git("rev-parse", "HEAD"),
        }
        (self.run_dir / "cost.json").write_text(json.dumps(record, indent=2), encoding="utf-8")

    # -- the loop ---------------------------------------------------------------------------------------------------

    def shaping_scale(self) -> float:
        # The Java side prices the shaping (0.005 a hit point, 0.0002 an unhit decision); the scale stays fixed.
        return float(self.config["shaping"]["scale"])

    def train(self, fleet: Fleet, max_seconds: float | None) -> None:
        rollout = int(self.ppo_config.rollout_per_agent) * fleet.agent_count
        self.log.info("train START budget=%d agents=%d rollout=%d servers=%d arenas=%d", self.budget, fleet.agent_count, rollout,
                      self.servers, self.arenas)
        self.log.event("train START", budget=self.budget, agents=fleet.agent_count, rollout=rollout, config=self.config)
        self.curriculum.log_body(self.decisions)
        while self.decisions < self.budget:
            if max_seconds is not None and time.monotonic() - self.started >= max_seconds:
                self.log.info("train STOP reason=maxSeconds seconds=%.0f", max_seconds)
                break
            if self.curriculum.blocked:
                self.log.info("train STOP reason=bandStalled decisions=%d", self.decisions)
                break
            if (self.run_dir / "STOP").exists():
                self.log.info("train STOP reason=stopFile decisions=%d", self.decisions)
                break
            self.step(fleet, rollout)
            if self.decisions >= self.next_checkpoint:
                self.next_checkpoint += int(self.run["checkpoint_every_decisions"])
                self.save_checkpoint(f"ckpt-{self.decisions:011d}.pt")
                self.write_cost()
            if self.decisions >= self.next_eval:
                self.next_eval += int(self.run["eval_every_decisions"])
                self.evaluate(fleet)

    def step(self, fleet: Fleet, rollout: int) -> None:
        began = time.monotonic()
        scale = self.shaping_scale()
        batch, stats = fleet.collect(self.sampler, rollout, self.curriculum.train_task, scale, self.ppo_config.discount, self.ppo_config.gae_lambda)
        collect_seconds = time.monotonic() - began
        progress = self.decisions / self.budget
        metrics = self.ppo.update(batch, progress)
        self.normalizer.update(batch.obs)
        self.model.renormalize(self.normalizer)
        self.sampler = Sampler(self.model, self.normalizer)
        keep = min(OBS_SAMPLE_ROWS, len(batch))
        self.obs_sample = batch.obs[self.rng.choice(len(batch), keep, replace=False)].copy()
        self.decisions += stats.decisions
        self.updates += 1
        self.log_update(stats, metrics, scale, collect_seconds, time.monotonic() - began)

    def log_update(self, stats, metrics: dict, scale: float, collect_seconds: float, total_seconds: float) -> None:
        wins = sum(c["win"] for c in stats.outcomes.values())
        episodes = stats.episodes
        by_style = {}
        for brain, counter in stats.outcomes.items():
            n = sum(counter.values())
            if n and brain >= 0:
                by_style[roster.brain_name(brain)] = {"n": n, "win_rate": counter["win"] / n}
        finished = wins / episodes if episodes else float("nan")
        mean_return = stats.episode_return_sum / episodes if episodes else float("nan")
        per_decision = stats.reward_sum / stats.decisions if stats.decisions else 0.0
        dps = stats.decisions / collect_seconds if collect_seconds else 0.0
        styles = " ".join(f"{name}:{row['win_rate']:.2f}/{row['n']}" for name, row in sorted(by_style.items()))
        acted = max(stats.acted, 1)
        bow = [float(x) / acted for x in stats.bow_counts]
        jump = stats.jump_count / acted
        shaping = stats.shaping_sum / max(episodes, 1)
        ticks_sum, engaged_sum, chase_sum, shots_sum = (float(x) for x in stats.engagement)
        engaged = engaged_sum / ticks_sum if ticks_sum else 0.0
        shots_ep = shots_sum / max(episodes, 1)
        chase_ep = chase_sum / max(episodes, 1)
        wins_n = wins
        losses_n = sum(c["loss"] for c in stats.outcomes.values())
        self.log.info(
            "train UPDATE update=%d decisions=%d stage=%s dps=%.0f episodes=%d win_rate=%.3f mean_reward=%.3f terminal_per_decision=%.5f policy_loss=%.4f "
            "value_loss=%.4f entropy=%.3f kl=%.4f clip_fraction=%.3f explained_variance=%.3f lr=%.2e entropy_coef=%.4f shaping_scale=%.4f log_std=%.3f "
            "timeouts=%d wins=%d losses=%d hp_band=%s shaping_per_episode=%.3f terminal_reward_per_episode=%.3f engaged=%.3f shots_per_episode=%.1f chase_ticks_per_episode=%.0f bow=[%.2f,%.2f,%.2f,%.2f] jump=%.2f styles=[%s]",
            self.updates, self.decisions, roster.STAGE_NAMES[self.curriculum.stage], dps, episodes, finished, mean_return, per_decision,
            metrics["policy_loss"], metrics["value_loss"], metrics["entropy"], metrics["kl"], metrics["clip_fraction"], metrics["explained_variance"],
            metrics["lr"], metrics["entropy_coef"], scale, metrics["log_std"], sum(c["timeout"] for c in stats.outcomes.values()), wins_n, losses_n, self.curriculum.band_text(), shaping, stats.reward_sum / max(episodes, 1), engaged, shots_ep, chase_ep, *bow, jump, styles)
        self.log.event(
            "train UPDATE", update=self.updates, decisions=self.decisions, stage=roster.STAGE_NAMES[self.curriculum.stage], dps=dps,
            wall_seconds=self.wall_seconds(), episodes=episodes, win_rate=finished, mean_reward=mean_return, terminal_per_decision=per_decision,
            policy_loss=metrics["policy_loss"], value_loss=metrics["value_loss"], entropy=metrics["entropy"], kl=metrics["kl"],
            clip_fraction=metrics["clip_fraction"], explained_variance=metrics["explained_variance"], lr=metrics["lr"],
            entropy_coef=metrics["entropy_coef"], shaping_scale=scale, log_std=metrics["log_std"], epochs_run=metrics["epochs_run"],
            update_seconds=metrics["update_seconds"], collect_seconds=collect_seconds, hp_band=self.curriculum.band_text(), shaping_per_episode=shaping, wins=wins_n, losses=losses_n, engaged_share=engaged, shots_per_episode=shots_ep, chase_ticks_per_episode=chase_ep,
            bow_action_share=bow, jump_share=jump, win_rate_by_style=by_style,
            outcomes={roster.brain_name(b) if b >= 0 else "unknown": dict(c) for b, c in stats.outcomes.items()})

    def evaluate(self, fleet: Fleet) -> None:
        began = time.monotonic()
        tasks = self.curriculum.eval_tasks()
        stats = fleet.evaluate(self.sampler, tasks)
        sampled = fleet.evaluate(self.sampler, tasks, deterministic=False)
        sampled_wins = sum(r.outcome == "win" for r in sampled.results)
        sampled_rate = sampled_wins / len(sampled.results) if sampled.results else 0.0
        overall = {"n": 0, "win": 0}
        rows = {}
        for result in stats.results:
            name = roster.brain_name(result.brain)
            row = rows.setdefault(name, {"n": 0, "win": 0, "loss": 0, "timeout": 0, "error": 0})
            row["n"] += 1
            row[result.outcome] += 1
            overall["n"] += 1
            overall["win"] += result.outcome == "win"
        rate = overall["win"] / overall["n"] if overall["n"] else 0.0
        from . import bodies
        toughness = {name: {"n": 0, "win": 0} for name in bodies.TOUGHNESS_BUCKETS}
        for result in stats.results:
            bucket = toughness[bodies.TOUGHNESS_BUCKETS[bodies.body_for_seed(result.seed, bodies.range_for_stage(result.stage)).toughness_bucket]]
            bucket["n"] += 1
            bucket["win"] += result.outcome == "win"
        eng_ticks = sum(r.ticks for r in stats.results)
        engaged_share = sum(r.engaged for r in stats.results) / eng_ticks if eng_ticks else 0.0
        shots_ep = sum(r.shots for r in stats.results) / max(len(stats.results), 1)
        chase_ep = sum(r.chase for r in stats.results) / max(len(stats.results), 1)
        by_toughness = " ".join(f"{name}:{row['win'] / row['n']:.2f}/{row['n']}" for name, row in toughness.items() if row["n"])
        stage = roster.STAGE_NAMES[self.curriculum.stage]
        styles = " ".join(f"{name}:{row['win'] / row['n']:.2f}" for name, row in rows.items())
        self.log.info("trainEval RESULT decisions=%d stage=%s suite_stage=%s episodes=%d/%d win_rate=%.3f win_rate_sampled=%.3f seconds=%.0f hp_band=%s styles=[%s] toughness=[%s] engaged=%.3f shots_per_episode=%.1f chase_ticks_per_episode=%.0f", self.decisions, stage,
                      roster.STAGE_NAMES[self.curriculum.eval_stage()], overall["n"], len(tasks), rate, sampled_rate, time.monotonic() - began, self.curriculum.band_text(), styles, by_toughness, engaged_share, shots_ep, chase_ep)
        self.log.event("trainEval RESULT", decisions=self.decisions, stage=stage, suite_stage=roster.STAGE_NAMES[self.curriculum.eval_stage()],
                       episodes=overall["n"], expected=len(tasks), win_rate=rate, win_rate_sampled=sampled_rate, hp_band=self.curriculum.band_text(), per_toughness=toughness, engaged_share=engaged_share, shots_per_episode=shots_ep, chase_ticks_per_episode=chase_ep, per_style=rows, seconds=time.monotonic() - began)
        self.curriculum.after_eval(self.decisions, rate)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path("configs/archer.toml"))
    parser.add_argument("--run-dir", type=Path, default=None, help="continue in an existing run directory")
    parser.add_argument("--resume", type=Path, default=None, help="a checkpoint to continue from")
    parser.add_argument("--widen-from", type=Path, default=None, help="start from the actor of a checkpoint made for a narrower contract")
    parser.add_argument("--shrink-aim-rows", type=float, default=None, help="after --resume: scale the yaw and pitch output rows of the actor by this factor")
    parser.add_argument("--note", default=None, help="a line for the log at the start, written as trainStage NOTE")
    parser.add_argument("--eval-reset", action="store_true", help="after --resume: start a new eval series and band clock at band 0")
    parser.add_argument("--restart-stage-a", action="store_true", help="after --resume: reinitialize the critic and start Stage A again")
    parser.add_argument("--max-seconds", type=float, default=None, help="stop after this much wall time, for a smoke run")
    parser.add_argument("--set", action="append", default=[], metavar="SECTION.KEY=VALUE", help="override one config value")
    args = parser.parse_args(argv)

    config = tomllib.loads(args.config.read_text(encoding="utf-8"))
    for override in args.set:
        key, _, value = override.partition("=")
        section, _, name = key.partition(".")
        config[section][name] = json.loads(value)
    name = args.run_dir.name if args.run_dir else f"{config['run']['name']}-{timestamp()}"
    log = RunLog(name)
    run_dir = log.dir
    (run_dir / "config.toml").write_text(args.config.read_text(encoding="utf-8"), encoding="utf-8")
    (run_dir / "config.effective.json").write_text(json.dumps(config, indent=2), encoding="utf-8")
    trainer = Trainer(config, log, run_dir)
    if args.widen_from:
        trainer.widen_from_checkpoint(args.widen_from)
    elif args.resume:
        trainer.resume(args.resume)
        if args.shrink_aim_rows is not None:
            trainer.shrink_aim_rows(args.shrink_aim_rows)
        if args.note:
            log.info("trainStage NOTE decisions=%d %s", trainer.decisions, args.note)
            log.event("trainStage NOTE", decisions=trainer.decisions, note=args.note)
        if args.eval_reset:
            trainer.curriculum.reset_evals(trainer.decisions, 0)
        if args.restart_stage_a:
            trainer.restart_stage_a()
    spec = prepare_spec(log)
    run = config["run"]
    code = 0
    try:
        with GymCluster(int(run["servers"]), int(run["arenas"]), log, spec=spec, seed=int(run["seed"]), decision_ticks=int(run["decision_ticks"]),
                        episode_ticks=int(run["episode_ticks"]), heap=run["heap"],
                        extra_props={"stella.gym.stage": "A"}) as cluster:
            fleet = Fleet([server.port for server in cluster.servers], log, seed=int(run["seed"]))
            try:
                trainer.train(fleet, args.max_seconds)
            except KeyboardInterrupt:
                log.warning("train INTERRUPTED decisions=%d", trainer.decisions)
                code = 130
            finally:
                trainer.save_checkpoint("ckpt-final.pt")
                trainer.write_cost()
                fleet.close()
    finally:
        log.close()
    return code


if __name__ == "__main__":
    sys.exit(main())
