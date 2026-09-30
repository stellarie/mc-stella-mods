package com.stella.learn.gym.brains;

import com.stella.learn.gym.BotBrain;
import com.stella.learn.gym.BotInput;
import com.stella.learn.gym.BotView;
import java.util.Set;

/** Runs one brain drawn at random when the episode starts. */
public final class RandomMix implements BotBrain {
	private final BotBrain chosen;

	public RandomMix(final BotBrain chosen) {
		this.chosen = chosen;
	}

	@Override
	public String name() {
		return "RandomMix";
	}

	@Override
	public void tick(final BotView view, final BotInput.Builder input) {
		chosen.tick(view, input);
	}

	@Override
	public void active(final Set<String> names) {
		chosen.active(names);
	}

	public BotBrain chosen() {
		return chosen;
	}
}
