package com.stella.learn.gym;

import java.util.Set;

/**
 * A scripted opponent. Each tick it reads the view and fills the input. Skill values, reaction delay, and gear come
 * from the episode; a brain adds no state of its own beyond what its style needs.
 */
public interface BotBrain {
	String name();

	void tick(BotView view, BotInput.Builder input);

	/** Adds the names of the brains that ran, itself or the ones it hands over to. */
	default void active(final Set<String> names) {
		names.add(name());
	}
}
