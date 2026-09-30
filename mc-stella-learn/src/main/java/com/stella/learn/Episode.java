package com.stella.learn;

import java.util.UUID;

/** One tactic a mob chose against one player, and what happened over the window that followed. */
public final class Episode {
	final UUID mob;
	final UUID player;
	final String mobKind;
	final Situation situation;
	final Tactic tactic;
	final int startTick;
	double dealt;
	double taken;

	Episode(final UUID mob, final UUID player, final String mobKind, final Situation situation, final Tactic tactic, final int startTick) {
		this.mob = mob;
		this.player = player;
		this.mobKind = mobKind;
		this.situation = situation;
		this.tactic = tactic;
		this.startTick = startTick;
	}

	public String key() {
		return Policy.key(this.mobKind, this.situation, this.tactic);
	}

	public double dealt() {
		return this.dealt;
	}

	public double taken() {
		return this.taken;
	}

	public Tactic tactic() {
		return this.tactic;
	}

	public Situation situation() {
		return this.situation;
	}
}
