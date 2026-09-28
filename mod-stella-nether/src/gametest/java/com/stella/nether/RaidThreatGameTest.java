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

/** The raid nether is at least as dangerous as the nether: its mobs carry the nether's threat floor or more. */
public class RaidThreatGameTest {
	static final int MOBS = 8;

	@GameTest(environment = "mod-stella-nether-test:threat", maxTicks = 400)
	public void raidMobsCarryAtLeastTheNethersThreat(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		List<ZombifiedPiglin> mobs = new ArrayList<>();
		for (int i = 0; i < MOBS; i++) {
			mobs.add(RaidModeGameTest.spawnInRaid((ServerLevel) player.level(), player.position().add(2.0 + i % 3, 0.0, i / 3 - 1.0)));
		}
		helper.succeedWhen(() -> {
			for (ZombifiedPiglin mob : mobs) {
				helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(mob.getUUID()), "a mob has not loaded yet");
			}
			int below = 0;
			int unscaled = 0;
			StringBuilder levels = new StringBuilder();
			for (ZombifiedPiglin mob : mobs) {
				int level = ThreatState.level(mob);
				double minimumHealth = mob.getAttributeBaseValue(Attributes.MAX_HEALTH) * SpawnScaling.healthMultiplier(ThreatState.NETHER_MINIMUM, 0.0);
				levels.append(' ').append(level).append('/').append((int) mob.getMaxHealth());
				below += level < ThreatState.NETHER_MINIMUM ? 1 : 0;
				unscaled += mob.getMaxHealth() < minimumHealth - 0.01 ? 1 : 0;
			}
			NetherTestSupport.result("raidThreatFloor", "below=" + below + " unscaled=" + unscaled + " levels/health=" + levels);
			helper.assertTrue(below == 0 && unscaled == 0, below + " mobs below the nether's threat, " + unscaled + " unscaled:" + levels);
			mobs.forEach(ZombifiedPiglin::discard);
			NetherTestSupport.leave(helper, player);
		});
	}
}
