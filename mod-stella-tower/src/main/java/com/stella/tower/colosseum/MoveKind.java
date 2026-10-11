package com.stella.tower.colosseum;

import java.util.List;

/**
 * Every special move of the two guardians. A move starts when the guardian is off its gap, the move is off its cooldown,
 * and the target stands inside the move's range; the range is measured in blocks from the rider.
 */
public enum MoveKind {
	/** Melee: blinks behind a player 8 blocks or more away, and hits. */
	BLINK(GuardianKind.MELEE, 8.0, 28.0, 140),
	/** Melee: a 60 degree cone of fire, 6 blocks long. */
	FIRE_CONE(GuardianKind.MELEE, 0.0, 6.0, 100),
	/** Melee: a 4 by 16 strip in front, with knockback and Nausea. */
	SLAM(GuardianKind.MELEE, 0.0, 12.0, 160),
	/** Ranged: invisible for two seconds with its horse, then an attack. */
	VANISH(GuardianKind.RANGED, 6.0, 30.0, 220),
	/** Ranged: eight arrows, one every three ticks. */
	VOLLEY(GuardianKind.RANGED, 6.0, 30.0, 160),
	/** Ranged: eight burning arrows lobbed around the target, each exploding where it lands. */
	LOB(GuardianKind.RANGED, 8.0, 32.0, 200);

	private final GuardianKind owner;
	private final double minRange;
	private final double maxRange;
	private final int cooldown;

	MoveKind(final GuardianKind owner, final double minRange, final double maxRange, final int cooldown) {
		this.owner = owner;
		this.minRange = minRange;
		this.maxRange = maxRange;
		this.cooldown = cooldown;
	}

	public GuardianKind owner() {
		return this.owner;
	}

	public double minRange() {
		return this.minRange;
	}

	public double maxRange() {
		return this.maxRange;
	}

	/** Ticks before the move may start again. */
	public int cooldown() {
		return this.cooldown;
	}

	public boolean inRange(final double distance) {
		return distance >= this.minRange && distance <= this.maxRange;
	}

	public static List<MoveKind> of(final GuardianKind kind) {
		return java.util.Arrays.stream(values()).filter(move -> move.owner == kind).toList();
	}
}
