package com.stella.ruins.world;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/** A modifier one run can carry. It makes the run harder and adds one vault roll. */
public enum Omen {
	/** The roster runs faster. */
	FRENZIED("frenzied", 0.25, 0.0, 0.0, 0.0),
	/** The roster wears armor and has more health. */
	BULWARK("bulwark", 0.0, 8.0, 0.0, 0.25),
	/** The roster hits harder. */
	FEROCIOUS("ferocious", 0.0, 0.0, 0.35, 0.0),
	/** The first kind of the roster comes in greater number. */
	SWARMING("swarming", 0.0, 0.0, 0.0, 0.0),
	/** A weakness pulse falls on the players inside the radius. */
	HEXED("hexed", 0.0, 0.0, 0.0, 0.0);

	/** The share of extra mobs of the first roster kind that {@link #SWARMING} adds. */
	public static final double SWARM_SHARE = 0.25;

	private final String id;
	private final double speed;
	private final double armor;
	private final double damage;
	private final double health;

	Omen(final String id, final double speed, final double armor, final double damage, final double health) {
		this.id = id;
		this.speed = speed;
		this.armor = armor;
		this.damage = damage;
		this.health = health;
	}

	public String id() {
		return this.id;
	}

	/** The speed gain, as a share of the base speed. */
	public double speed() {
		return this.speed;
	}

	/** The armor gain, in points. */
	public double armor() {
		return this.armor;
	}

	/** The damage gain, as a share of the base damage. */
	public double damage() {
		return this.damage;
	}

	/** The health gain, as a share of the base health. It counts toward the cap with the tier gain. */
	public double health() {
		return this.health;
	}

	public static @Nullable Omen byId(final String id) {
		return Arrays.stream(values()).filter(omen -> omen.id.equals(id)).findFirst().orElse(null);
	}
}
