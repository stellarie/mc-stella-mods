package com.stella.nether;

import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/** The raid nether scales its own mobs: no threat level reaches them, and each stands on the raid's own base health. */
public class RaidThreatGameTest {
	static final int MOBS = 8;

	@GameTest(environment = "mod-stella-nether-test:threat", maxTicks = 400)
	public void raidMobsCarryNoThreatAndTheRaidsOwnHealth(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		List<ZombifiedPiglin> mobs = new ArrayList<>();
		for (int i = 0; i < MOBS; i++) {
			mobs.add(RaidModeGameTest.spawnInRaid((ServerLevel) player.level(), player.position().add(2.0 + i % 3, 0.0, i / 3 - 1.0)));
		}
		helper.startSequence().thenWaitUntil(() -> {
			for (ZombifiedPiglin mob : mobs) {
				helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(mob.getUUID()), "a mob has not loaded yet");
			}
		}).thenExecute(() -> {
			double expected = RaidInstance.MOB_BASE_HEALTH * RaidInstance.NORMAL_STRENGTH;
			int threatened = 0;
			int scaled = 0;
			int wrongHealth = 0;
			StringBuilder seen = new StringBuilder();
			for (ZombifiedPiglin mob : mobs) {
				// The rolled threat itself; the level an elite or boss reads adds its rank's bonus to it, and a rank
				// is kept (seen once in five runs: an elite reading 5 at the raid's 80 health).
				ThreatState rolled = ThreatState.get(mob);
				int level = rolled == null ? -1 : rolled.areaLevel() + rolled.variation();
				boolean threatScaled = mob.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID) != null
					|| mob.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(SpawnScaling.DAMAGE_ID) != null;
				seen.append(' ').append(level).append('/').append(mob.getAttributeBaseValue(Attributes.MAX_HEALTH)).append('/').append(mob.getMaxHealth());
				threatened += level != 0 ? 1 : 0;
				scaled += threatScaled ? 1 : 0;
				wrongHealth += Math.abs(mob.getAttributeBaseValue(Attributes.MAX_HEALTH) - RaidInstance.MOB_BASE_HEALTH) > 1.0E-6
					|| Math.abs(mob.getMaxHealth() - expected) > 0.01 ? 1 : 0;
			}
			NetherTestSupport.result("raidOwnScaling", "threatened=" + threatened + " scaled=" + scaled + " wrongHealth=" + wrongHealth
				+ " expected=" + expected + " level/base/max=" + seen);
			mobs.forEach(ZombifiedPiglin::discard);
			NetherTestSupport.leave(helper, player);
			helper.assertTrue(threatened == 0 && scaled == 0 && wrongHealth == 0,
				threatened + " mobs with a threat level, " + scaled + " scaled by it, " + wrongHealth + " off the raid's health of " + expected + ":" + seen);
		}).thenSucceed();
	}
}
