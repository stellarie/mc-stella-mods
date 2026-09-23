package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class PowerFixtures {
	public static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
	public static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
	public static final Identifier END = Identifier.fromNamespaceAndPath("minecraft", "the_end");
	public static final long LEVEL_50_POINTS = 6275L;
	public static final long STEP_49_TO_50_POINTS = 819L;
	public static final long LEVEL_49_POINTS = 5456L;
	public static final double LEVEL_50_SHARE = 0.13;
	public static final double GROWTH = 1.15;
	public static final double BASE = 1.0;
	public static final double AWARD_DIVISOR = 0.5;
	public static final int AWARD_MINIMUM = 1;
	public static final double MAX_HEALTH_AT_CAP = 1004.0;
	public static final double ARMOR_AT_CAP = 11.76;
	public static final double KNOCKBACK_RESISTANCE_AT_CAP = 0.294;
	public static final double ATTACK_DAMAGE_AT_CAP = 20.0;
	public static final double ATTACK_SPEED_AT_CAP = 1.0;
	public static final double ARMOR_CEILING = 30.0;
	public static final double KNOCKBACK_CEILING = 1.0;

	private PowerFixtures() {
	}

	public static PowerConfig acceptanceConfig() {
		return new PowerConfig(
			1,
			50,
			new PowerConfig.Curve(BASE, GROWTH),
			new PowerConfig.Award(AWARD_DIVISOR, AWARD_MINIMUM),
			new PowerConfig.MobLevel(
				300.0,
				32,
				4,
				16.0,
				0.0,
				36,
				1.0,
				Map.of(NETHER, 8.0, OVERWORLD, 1.0, END, 1.0)
			),
			new PowerConfig.Bands(20, 10, 6, 5),
			new PowerConfig.Tracker(40, 5),
			new PowerConfig.Attributes(
				MAX_HEALTH_AT_CAP,
				ARMOR_AT_CAP,
				KNOCKBACK_RESISTANCE_AT_CAP,
				ATTACK_DAMAGE_AT_CAP,
				ATTACK_SPEED_AT_CAP
			),
			new PowerConfig.Readout(true, 32.0, 40, 0.9, 0.5)
		);
	}
}
