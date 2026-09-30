package com.stella.learn.gym;

import com.stella.learn.LearnConfigStore;

/** With `stella.gym.baseline=true` no mob is model driven: the hand-written goals and the Phase 1 learn goals fight. */
final class BaselineMode {
	private BaselineMode() {
	}

	static void apply() {
		LearnConfigStore.set(LearnConfigStore.get().withModelArcher(false));
		GymLog.info(GymLog.CONFIG, "BASELINE", "model_archer=false");
	}
}
