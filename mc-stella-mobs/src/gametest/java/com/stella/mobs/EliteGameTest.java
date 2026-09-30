package com.stella.mobs;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.gear.GearTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Item 1 of the elite block: the persistence split. The durable flags live in a persistent attachment
 * and survive a codec round trip; the timing state stays in the transient attachment and carries no
 * codec at all, so a lost cooldown is harmless and a save entry is not written for every mob.
 */
public class EliteGameTest {
	private static final String TEST = "eliteStatePersists";
	private static final long TIMING_SENTINEL = 1234L;

	@GameTest(maxTicks = 200)
	public void eliteStateIsDurableAndTimingStateIsNot(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 2).spawn();

		EliteState.setSummoned(zombie, true);
		EliteState.setWarCallUsed(zombie, true);
		EliteState.markElite(zombie, GearTier.NETHERITE);
		ReactiveState.get(zombie).setShieldReadyTick(TIMING_SENTINEL);

		boolean eliteStorePersists = EliteState.TYPE.isPersistent();
		boolean timingStorePersists = ReactiveState.TYPE.isPersistent();
		EliteState decoded = roundTrip(EliteState.get(zombie));

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test={} RESULT eliteStorePersists={} timingStorePersists={} summoned={} elite={} tier={} warCallUsed={}",
			TEST,
			eliteStorePersists,
			timingStorePersists,
			decoded.summoned(),
			decoded.elite(),
			decoded.eliteTier().orElse(null),
			decoded.warCallUsed()
		);

		helper.assertTrue(eliteStorePersists, "the elite state attachment is not persistent");
		helper.assertFalse(timingStorePersists, "the timing state attachment became persistent");
		helper.assertTrue(decoded.summoned(), "summoned did not survive the codec round trip");
		helper.assertTrue(decoded.elite(), "elite did not survive the codec round trip");
		helper.assertTrue(decoded.warCallUsed(), "warCallUsed did not survive the codec round trip");
		helper.assertValueEqual(decoded.eliteTier().orElse(null), GearTier.NETHERITE, "the elite tier after a round trip");
		helper.assertValueEqual(
			Long.valueOf(ReactiveState.get(zombie).getShieldReadyTick()),
			Long.valueOf(TIMING_SENTINEL),
			"the timing field stayed in its own store"
		);
		helper.succeed();
	}

	private static EliteState roundTrip(final EliteState state) {
		var encoded = EliteState.CODEC.encodeStart(NbtOps.INSTANCE, state).resultOrPartial().orElseThrow();
		return EliteState.CODEC.parse(NbtOps.INSTANCE, encoded).resultOrPartial().orElseThrow();
	}
}
