package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.readout.ReadoutPayload;
import com.stella.equips.readout.ReadoutService;
import com.stella.equips.readout.ReadoutServantLevel;
import com.stella.equips.readout.ReadoutThreat;
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

/**
 * The badge number for a summoned skeleton is its live servant level, and the mobs mod's rolled level
 * still answers for every other mob and whenever the servant share says nothing.
 */
public class ReadoutServantLevelGameTest {

	@GameTest
	@SuppressWarnings("removal")
	public void badgeFollowsTheServantLevelAndFallsBack(final GameTestHelper helper) {
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		Object previousServant = share.get(ReadoutServantLevel.OBJECT_SHARE_KEY);
		Object previousThreat = share.get(ReadoutThreat.OBJECT_SHARE_KEY);
		PowerConfig config = PowerConfigStore.bundled();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = 20;
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		int fromServant;
		int refreshed;
		int retiredServant;
		int absentServant;
		try {
			// The mobs share still answers a stale rolled level; the servant share must win over it.
			share.put(ReadoutThreat.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 4);
			share.put(ReadoutServantLevel.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 31);
			fromServant = ReadoutService.payloadFor(mob, player, config).threat();
			// The servant levels up: the badge must follow the live answer, not the rolled one.
			share.put(ReadoutServantLevel.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 32);
			refreshed = ReadoutService.payloadFor(mob, player, config).threat();
			// The servant share answers -1, as it does for anything that is not a servant.
			share.put(ReadoutServantLevel.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> -1);
			retiredServant = ReadoutService.payloadFor(mob, player, config).threat();
			// The spells mod is absent altogether.
			share.remove(ReadoutServantLevel.OBJECT_SHARE_KEY);
			absentServant = ReadoutService.payloadFor(mob, player, config).threat();
		} finally {
			restore(share, ReadoutServantLevel.OBJECT_SHARE_KEY, previousServant);
			restore(share, ReadoutThreat.OBJECT_SHARE_KEY, previousThreat);
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=badgeFollowsTheServantLevelAndFallsBack RESULT servant={} refreshed={} retiredServant={} absentServant={} rolled=4",
			fromServant, refreshed, retiredServant, absentServant);
		helper.assertTrue(fromServant == 31, "the badge showed " + fromServant + " for a servant of level 31");
		helper.assertTrue(refreshed == 32, "the badge did not follow the servant to level 32: " + refreshed);
		helper.assertTrue(retiredServant == 4, "with the servant share answering -1 the badge showed " + retiredServant);
		helper.assertTrue(absentServant == 4, "without the servant share the badge showed " + absentServant);
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void aNonServantStillAnswersFromTheMobsShare(final GameTestHelper helper) {
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		Object previousServant = share.get(ReadoutServantLevel.OBJECT_SHARE_KEY);
		Object previousThreat = share.get(ReadoutThreat.OBJECT_SHARE_KEY);
		PowerConfig config = PowerConfigStore.bundled();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.experienceLevel = 20;
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		ReadoutPayload plain;
		ReadoutPayload silent;
		try {
			// A real servant share answers -1 for this zombie, which is what the spells mod publishes.
			share.put(ReadoutServantLevel.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> -1);
			share.put(ReadoutThreat.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> 29);
			plain = ReadoutService.payloadFor(mob, player, config);
			// A share that answers nothing usable must not blank a level the mobs share does have.
			share.put(ReadoutServantLevel.OBJECT_SHARE_KEY, (Function<Entity, Integer>) entity -> null);
			silent = ReadoutService.payloadFor(mob, player, config);
		} finally {
			restore(share, ReadoutServantLevel.OBJECT_SHARE_KEY, previousServant);
			restore(share, ReadoutThreat.OBJECT_SHARE_KEY, previousThreat);
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=aNonServantStillAnswersFromTheMobsShare RESULT plain={} silent={} expected=29",
			plain.threat(), silent.threat());
		helper.assertTrue(plain.threat() == 29, "a non-servant read " + plain.threat() + " instead of the mobs level 29");
		helper.assertTrue(silent.threat() == 29, "an unusable servant answer blanked the mobs level to " + silent.threat());
		helper.succeed();
	}

	private static void restore(final ObjectShare share, final String key, final Object previous) {
		if (previous == null) {
			share.remove(key);
		} else {
			share.put(key, previous);
		}
	}
}
