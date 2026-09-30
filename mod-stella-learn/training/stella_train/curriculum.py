"""Stage promotion rules and the tasks each stage plays.

    A: flat terrain; CritRusher, SwordSweeper, BowKiter at the lowest skill; no gear.
       Promotes when the `stage-a` suite on held-out seeds shows 80 percent wins.
    B: all terrain features; the full roster; no gear and iron.
       Promotes when the win rate has not risen for a number of evals, or when its share of the budget ends.
    C: the same, with all gear tiers, profile noise and creepers. It runs until the budget ends.
"""

from __future__ import annotations

import numpy as np

from . import bodies, roster
from .rollout import Task
from .runlog import RunLog


class Curriculum:
    def __init__(self, config: dict, budget: int, log: RunLog, seed: int = 0):
        self.config = config
        self.budget = budget
        self.log = log
        self.stage = roster.STAGE_A
        self.stage_start = 0
        self.best = -1.0
        self.stale = 0
        self.history: list[dict] = []
        self.ticks = int(config.get("stage_a_ticks", 600))
        self.bands = [tuple(b) if len(b) == 3 else (b[0], b[1], None) for b in config.get("stage_a_bands", [[22, 26]])]
        self.eval_series = 0
        self.band = 0
        self.band_start = -1
        self.blocked = False
        bodies.set_stage_a_hp(*self.bands[0])
        self.rng = np.random.default_rng(seed)

    # -- what to play -----------------------------------------------------------------------------------------------

    def passive(self) -> bool:
        return self.stage == roster.STAGE_A and self.band == 0 and len(self.bands) > 1 and self.bands[0][1] == self.bands[0][0]

    def train_task(self) -> Task:
        brains = (roster.PASSIVE,) if self.passive() else roster.stage_brains(self.stage)
        return Task(int(brains[self.rng.integers(len(brains))]), int(self.rng.integers(0, roster.TRAIN_SEED_LIMIT)), self.stage, self.ticks, self.body().wire())

    def body(self) -> bodies.BodyRange:
        return bodies.range_for_stage(self.stage)

    def band_text(self) -> str:
        low, high, _ = self.bands[self.band]
        return f"{low}-{high}" + ("-passive" if self.passive() else "")

    def log_body(self, decisions: int) -> None:
        if self.band_start < 0:
            self.band_start = decisions
        ranges = self.body()
        self.log.info("trainStage BODY stage=%s decisions=%d hp=%d-%d armor=0-%d toughness=0-%d protection=0-%d special=%.2f", roster.STAGE_NAMES[self.stage], decisions,
                      ranges.hp_min, ranges.hp_max, ranges.armor_max, ranges.toughness_max, ranges.protection_max, ranges.special_permille / 1000.0)
        self.log.event("trainStage BODY", stage=roster.STAGE_NAMES[self.stage], decisions=decisions, **ranges.__dict__)

    def eval_stage(self) -> int:
        """Stage A tests stage A. Stage B tests itself. Stage C tests the release mix, which has no extras."""
        return roster.STAGE_RELEASE if self.stage == roster.STAGE_C else self.stage

    def eval_tasks(self) -> list[Task]:
        stage = self.eval_stage()
        names = roster.STAGE_A_BRAINS if stage == roster.STAGE_A else roster.BRAINS
        count = 50 if stage == roster.STAGE_A else int(self.config["eval_episodes_per_brain"])
        if self.passive():
            return [Task(roster.PASSIVE, roster.EVAL_SEED_BASE + j, stage, self.ticks, bodies.range_for_stage(stage).wire()) for j in range(2 * count)]
        return [Task(roster.brain_index(name), roster.EVAL_SEED_BASE + j, stage, self.ticks, bodies.range_for_stage(stage).wire()) for j in range(count) for name in names]

    # -- promotion --------------------------------------------------------------------------------------------------

    def after_eval(self, decisions: int, win_rate: float) -> bool:
        """Records one eval and returns True when the stage just changed."""
        self.history.append({"decisions": decisions, "stage": self.stage, "win_rate": win_rate})
        stage_decisions = decisions - self.stage_start
        last_band = self.band == len(self.bands) - 1
        if self.stage == roster.STAGE_A and not last_band:
            need = self.config.get("band0_promote_at", 0.60) if self.passive() else self.config.get("band_promote_at", 0.40)
            if win_rate >= need:
                self._next_band(decisions, win_rate)
            elif decisions - self.band_start > int(self.config.get("band_blocked_decisions", 20_000_000)):
                self.blocked = True
                self.log.info("trainStage BLOCKED band=%s decisions=%d in_band=%d win_rate=%.3f", self.band_text(), decisions, decisions - self.band_start, win_rate)
                self.log.event("trainStage BLOCKED", band=self.band_text(), decisions=decisions, win_rate=win_rate)
            return False
        if self.stage == roster.STAGE_A and win_rate > self.config.get("stage_a_short_at", 0.40) and self.ticks > int(self.config.get("short_ticks", 400)):
            self._set_ticks(decisions, int(self.config.get("short_ticks", 400)), f"stage A win_rate={win_rate:.3f} passed {self.config.get('stage_a_short_at', 0.40)}")
        if self.stage == roster.STAGE_A:
            if win_rate >= self.config["stage_a_win_rate"] and stage_decisions >= self.config["stage_a_min_decisions"]:
                return self._promote(decisions, f"win_rate={win_rate:.3f} bar={self.config['stage_a_win_rate']}")
        elif self.stage == roster.STAGE_B:
            if win_rate > self.best + 1e-9:
                self.best = win_rate
                self.stale = 0
            else:
                self.stale += 1
            share_end = decisions >= self.config["stage_b_budget_share"] * self.budget
            patience = self.stale >= self.config["stage_b_patience_evals"] and stage_decisions >= self.config["stage_b_min_decisions"]
            if patience or share_end:
                return self._promote(decisions, f"win_rate={win_rate:.3f} best={self.best:.3f} stale_evals={self.stale} budget_share_ended={share_end}")
        return False

    def reset_evals(self, decisions: int, band: int = 0) -> None:
        """Starts a new eval series and a new band clock at `band`; earlier evals stay in the log and no longer count."""
        self.eval_series += 1
        self.band = band
        self.band_start = decisions
        self.blocked = False
        self.stage = roster.STAGE_A
        self.history = []
        bodies.set_stage_a_hp(*self.bands[band])
        self.log.info("trainStage EVAL_RESET series=%d band=%s decisions=%d", self.eval_series, self.band_text(), decisions)
        self.log.event("trainStage EVAL_RESET", series=self.eval_series, band=self.band_text(), decisions=decisions)

    def _next_band(self, decisions: int, win_rate: float) -> None:
        old = self.band_text()
        self.band += 1
        self.band_start = decisions
        bodies.set_stage_a_hp(*self.bands[self.band])
        self.log.info("trainStage HP_BAND old=%s new=%s win_rate=%.3f decisions=%d", old, self.band_text(), win_rate, decisions)
        self.log.event("trainStage HP_BAND", old=old, new=self.band_text(), win_rate=win_rate, decisions=decisions)

    def _set_ticks(self, decisions: int, new: int, reason: str) -> None:
        old = self.ticks
        self.ticks = new
        self.log.info("trainStage TIMEOUT old=%d new=%d decisions=%d reason=%s", old, new, decisions, reason)
        self.log.event("trainStage TIMEOUT", old=old, new=new, decisions=decisions, reason=reason)

    def _promote(self, decisions: int, reason: str) -> bool:
        old = roster.STAGE_NAMES[self.stage]
        self.stage += 1
        self.stage_start = decisions
        self.best = -1.0
        self.stale = 0
        new = roster.STAGE_NAMES[self.stage]
        self.log_body(decisions)
        wanted = int(self.config.get("stage_b_ticks", 600)) if self.stage == roster.STAGE_B else int(self.config.get("stage_c_ticks", 400))
        if wanted != self.ticks:
            self._set_ticks(decisions, wanted, f"stage {old} to {new}")
        self.log.info("trainStage PROMOTE from=%s to=%s decisions=%d %s", old, new, decisions, reason)
        self.log.event("trainStage PROMOTE", **{"from": old, "to": new}, decisions=decisions, reason=reason)
        return True

    def state(self) -> dict:
        return {"stage": self.stage, "stage_start": self.stage_start, "best": self.best, "stale": self.stale, "history": self.history, "ticks": self.ticks, "band": self.band, "band_start": self.band_start, "eval_series": self.eval_series}

    def load(self, state: dict) -> None:
        self.stage = state["stage"]
        self.stage_start = state["stage_start"]
        self.best = state["best"]
        self.stale = state["stale"]
        self.history = state["history"]
        self.ticks = state.get("ticks", self.ticks)
        self.band = min(state.get("band", 0), len(self.bands) - 1)
        self.band_start = state.get("band_start", -1)
        self.eval_series = state.get("eval_series", 0)
        bodies.set_stage_a_hp(*self.bands[self.band])
