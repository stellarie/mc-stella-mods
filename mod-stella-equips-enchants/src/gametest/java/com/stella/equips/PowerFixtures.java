package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

public final class PowerFixtures {
	public static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
	public static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
	public static final Identifier END = Identifier.fromNamespaceAndPath("minecraft", "the_end");
	public static final long LEVEL_50_POINTS = 3969L;
	public static final int DISTANCE_CAP_POINTS = 2000;
	public static final int DEPTH_CAP_POINTS = 8;
	public static final double ATTACK_DAMAGE_MAX = 20.0;
	public static final double ATTACK_SPEED_MAX = 1.0;
	public static final double ARMOR_PLATEAU_POINTS = 125.0;
	public static final double ARMOR_CEILING = 30.0;

	private PowerFixtures() {
	}

	public static PowerConfig acceptanceConfig() {
		return new PowerConfig(
			1,
			50,
			new PowerConfig.PointsPerLevel(6L, 3L),
			new PowerConfig.Distance(5.0, DISTANCE_CAP_POINTS),
			new PowerConfig.Depth(16.0, 63.0, DEPTH_CAP_POINTS),
			new PowerConfig.Kills(
				1L,
				4L,
				Set.of(
					Identifier.fromNamespaceAndPath("minecraft", "wither"),
					Identifier.fromNamespaceAndPath("minecraft", "ender_dragon"),
					Identifier.fromNamespaceAndPath("minecraft", "warden")
				)
			),
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
			new PowerConfig.XpMultiplier(1.0, 0.15, 0.05, 4.0),
			new PowerConfig.Bands(20, 10, 6, 5),
			new PowerConfig.Tracker(40, 5),
			new PowerConfig.Attributes(
				new PowerConfig.Survivability(0.5, 0.24, 0.006),
				new PowerConfig.Combat(ATTACK_DAMAGE_MAX, ATTACK_SPEED_MAX)
			),
			new PowerConfig.Readout(true, 32.0, 40, 0.9, 0.5)
		);
	}
}
