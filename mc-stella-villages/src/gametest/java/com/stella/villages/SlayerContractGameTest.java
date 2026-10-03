package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import com.stella.villages.data.VillageWorldData.MissionStatus;
import com.stella.villages.mission.SlayerContract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The slayer contract's enraged red slime: a formidable fight of 1000 health. */
public final class SlayerContractGameTest {
	@GameTest
	public void slayerSlimeHasThousandHealth(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MissionRecord mission = new MissionRecord(UUID.randomUUID(), UUID.randomUUID(), SlayerContract.TARGET, helper.absolutePos(new BlockPos(2, 1, 2)),
			Optional.empty(), MissionStatus.TARGET_PENDING, Optional.empty(), level.dimension(), MissionKind.SLAYER, 1, List.of());
		VillageWorldData.get(level).addMission(mission);
		Mob slime = SlayerContract.spawnTarget(level, mission);
		helper.assertTrue(slime != null, "the enraged slime did not spawn");
		double base = slime.getAttributeBaseValue(Attributes.MAX_HEALTH);
		PatrolTestSupport.result("slayerSlimeHasThousandHealth", "base=" + base + " max=" + slime.getMaxHealth() + " health=" + slime.getHealth());
		helper.assertTrue(base == SlayerContract.HEALTH && SlayerContract.HEALTH == 1000.0, "the base maximum health is " + base);
		helper.assertTrue(slime.getMaxHealth() == 1000.0F && slime.getHealth() == slime.getMaxHealth(),
			"the slime has " + slime.getHealth() + " of " + slime.getMaxHealth());
		// A reload resets the stats to the red slime's own; the contract puts them back.
		slime.getAttribute(Attributes.MAX_HEALTH).setBaseValue(12.0);
		slime.setHealth(5.0F);
		SlayerContract.restoreHealth(slime);
		helper.assertTrue(slime.getMaxHealth() == 1000.0F && slime.getHealth() == 1000.0F,
			"after a reload the slime has " + slime.getHealth() + " of " + slime.getMaxHealth());
		VillageWorldData.get(level).removeMission(mission.id());
		slime.discard();
		helper.succeed();
	}
}
