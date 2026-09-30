"""Stage promotion rules and the tasks each stage plays.

    A: flat terrain; CritRusher, SwordSweeper, BowKiter at the lowest skill; no gear.
       Promotes when the `stage-a` suite on held-out seeds shows 80 percent wins.
    B: all terrain features; the full roster; no gear and iron.
       Promotes when the win rate has not risen for a number of evals, or when its share of the budget ends.
    C: the same, with all gear tiers, profile noise and creepers. It runs until the budget ends.
"""

from __future__ import annotations

import numpy as np

from . import roster
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
        self.rng = np.random.default_rng(seed)

    # -- what to play -----------------------------------------------------------------------------------------------

    def train_task(self) -> Task:
        brains = roster.stage_brains(self.stage)
        return Task(int(brains[self.rng.integers(len(brains))]), int(self.rng.integers(0, roster.TRAIN_SEED_LIMIT)), self.stage, self.ticks)

    def eval_stage(self) -> int:
        """Stage A tests stage A. Stage B tests itself. Stage C tests the release mix, which has no extras."""
        return roster.STAGE_RELEASE if self.stage == roster.STAGE_C else self.stage

    def eval_tasks(self) -> list[Task]:
        stage = self.eval_stage()
        names = roster.STAGE_A_BRAINS if stage == roster.STAGE_A else roster.BRAINS
        count = 50 if stage == roster.STAGE_A else int(self.config["eval_episodes_per_brain"])
        return [Task(roster.brain_index(name), roster.EVAL_SEED_BASE + j, stage, self.ticks) for j in range(count) for name in names]

    # -- promotion --------------------------------------------------------------------------------------------------

    def after_eval(self, decisions: int, win_rate: float) -> bool:
        """Records one eval and returns True when the stage just changed."""
        self.history.append({"decisions": decisions, "stage": self.stage, "win_rate": win_rate})
        stage_decisions = decisions - self.stage_start
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
        wanted = int(self.config.get("stage_b_ticks", 600)) if self.stage == roster.STAGE_B else int(self.config.get("stage_c_ticks", 400))
        if wanted != self.ticks:
            self._set_ticks(decisions, wanted, f"stage {old} to {new}")
        self.log.info("trainStage PROMOTE from=%s to=%s decisions=%d %s", old, new, decisions, reason)
        self.log.event("trainStage PROMOTE", **{"from": old, "to": new}, decisions=decisions, reason=reason)
        return True

    def state(self) -> dict:
        return {"stage": self.stage, "stage_start": self.stage_start, "best": self.best, "stale": self.stale, "history": self.history, "ticks": self.ticks}

    def load(self, state: dict) -> None:
        self.stage = state["stage"]
        self.stage_start = state["stage_start"]
        self.best = state["best"]
        self.stale = state["stale"]
        self.history = state["history"]
        self.ticks = state.get("ticks", self.ticks)
