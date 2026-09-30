package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class PowerFixtures {
	public static final String TEST_MOD_ID = "mod-stella-equips-enchants-test-mod-stella-equips-enchants";
	public static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
	public static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
	public static final Identifier END = Identifier.fromNamespaceAndPath("minecraft", "the_end");
	public static final double HEALTH_BASE = 20.0;
	public static final double HEALTH_PER_LEVEL = 4.0;
	public static final double HEALTH_AT_CAP = 216.0;
	public static final double HEALTH_GAIN_AT_CAP = 196.0;

	private PowerFixtures() {
	}

	public static PowerConfig acceptanceConfig() {
		return new PowerConfig(
			1,
			50,
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
				HEALTH_BASE,
				HEALTH_PER_LEVEL
			),
			new PowerConfig.Readout(true, 32.0, 40, 0.9, 0.5)
		);
	}

	public static PowerConfig withMobLevel(final PowerConfig.MobLevel mobLevel) {
		PowerConfig base = acceptanceConfig();
		return new PowerConfig(
			base.version(), base.levelCap(), mobLevel, base.bands(), base.tracker(),
			base.attributes(), base.readout()
		);
	}
}
