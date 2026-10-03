package com.stella.equips;

import com.stella.equips.power.PowerAttributes;
import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigParser;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import com.stella.equips.readout.DangerColor;
import com.stella.equips.readout.ReadoutPayload;
import com.stella.equips.readout.ReadoutService;
import com.stella.equips.readout.ReadoutThreat;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** The readout level comes from the mod-stella-mobs share, and is blank without it. */
public class ReadoutThreatGameTest {
	private static final double TOLERANCE = 1.0E-6;

	/** One method, so no parallel test sees another's share stub. */
	@GameTest
	@SuppressWarnings("removal")
	public void readoutFollowsTheShare(final GameTestHelper helper) {
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		Object previous = share.get(ReadoutThreat.OBJECT_SHARE_KEY);
		PowerConfig config = PowerConfigStore.bundled();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = 20;
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		ReadoutPayload shared;
		ReadoutPayload negative;
		ReadoutPayload absent;
		DangerColor weaker;
		DangerColor even;
		DangerColor stronger;
		try {
			share.put(ReadoutThreat.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 37);
			shared = ReadoutService.payloadFor(mob, player, config);
			share.put(ReadoutThreat.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 12);
			weaker = ReadoutService.payloadFor(mob, player, config).dangerValue();
			share.put(ReadoutThreat.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 20);
			even = ReadoutService.payloadFor(mob, player, config).dangerValue();
			share.put(ReadoutThreat.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 26);
			stronger = ReadoutService.payloadFor(mob, player, config).dangerValue();
			share.put(ReadoutThreat.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> -1);
			negative = ReadoutService.payloadFor(mob, player, config);
			share.remove(ReadoutThreat.OBJECT_SHARE_KEY);
			absent = ReadoutService.payloadFor(mob, player, config);
		} finally {
			if (previous == null) {
				share.remove(ReadoutThreat.OBJECT_SHARE_KEY);
			} else {
				share.put(ReadoutThreat.OBJECT_SHARE_KEY, previous);
			}
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutFollowsTheShare RESULT shared={} negative={} absent={}",
			shared, negative, absent);
		helper.assertTrue(shared.threat() == 37, "the payload ignored the share: " + shared.threat());
		StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutDanger RESULT shared={} weaker={} even={} stronger={} absent={}",
			shared.dangerValue(), weaker, even, stronger, absent.dangerValue());
		helper.assertTrue(shared.dangerValue() == DangerColor.RED, "a gap of 17 read " + shared.dangerValue());
		helper.assertTrue(stronger == DangerColor.YELLOW, "a gap of 6 read " + stronger);
		helper.assertTrue(even == DangerColor.WHITE, "a gap of 0 read " + even);
		helper.assertTrue(weaker == DangerColor.GREEN, "a gap of -8 read " + weaker);
		helper.assertTrue(absent.dangerValue() == DangerColor.WHITE, "a missing level read " + absent.dangerValue());
		helper.assertTrue(shared.bandValue() == PowerCurve.band(17, config),
			"the band did not use the raw gap of 17: " + shared.bandValue());
		helper.assertTrue(negative.threat() == ReadoutThreat.NONE && negative.bandValue() == PowerCurve.Band.WHITE,
			"a negative share value was not blank: " + negative);
		helper.assertTrue(absent.threat() == ReadoutThreat.NONE && absent.bandValue() == PowerCurve.Band.WHITE,
			"a missing share was not blank: " + absent);
		helper.succeed();
	}

	@GameTest
	public void mobLevelRetired(final GameTestHelper helper) {
		String old = """
			{ "level_cap": 30, "mob_level": { "blocks_per_level": 300, "cap": 32 } }
			""";
		PowerConfig parsed = PowerConfigParser.parse(old, PowerConfigStore.bundled());
		List<String> ignored = PowerConfigParser.ignoredKeys();
		StellaEquips.LOGGER.info("[BEHAVIOR] test=mobLevelRetired RESULT levelCap={} ignored={}", parsed.levelCap(), ignored);
		helper.assertTrue(parsed.levelCap() == 30, "the level cap was lost: " + parsed.levelCap());
		helper.assertTrue(ignored.stream().anyMatch(key -> key.startsWith("mob_level")), "mob_level was not reported: " + ignored);
		helper.succeed();
	}

	@GameTest
	public void newHealthCurve(final GameTestHelper helper) {
		PowerConfig config = PowerConfigStore.bundled();
		double at50 = PowerAttributes.healthFor(50, config);
		double at100 = PowerAttributes.healthFor(100, config);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=newHealthCurve RESULT cap={} at50={} at100={}", config.levelCap(), at50, at100);
		helper.assertTrue(config.levelCap() == 1024, "level cap " + config.levelCap());
		helper.assertTrue(Math.abs(at50 - 142.5) < TOLERANCE && Math.abs(at100 - 267.5) < TOLERANCE,
			"at50=" + at50 + " at100=" + at100);
		helper.succeed();
	}
}
