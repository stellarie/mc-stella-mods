package com.stella.rpg;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;

public final class MobRollGameTest {
	@GameTest
	public void levelThirtySkeletonRollsDexMain(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, 1, 2, 1);
		Map<Mob, Integer> threats = new IdentityHashMap<>();
		threats.put(skeleton, 30);
		MobsBridge.setFacts(new MobsBridge.Facts() {
			@Override
			public int threat(final Mob mob) {
				return threats.getOrDefault(mob, 0);
			}

			@Override
			public String role(final Mob mob) {
				return "ranged";
			}
		});
		MobStats.setRandomized(true);
		try {
			int total = 0;
			int dex = MobStats.base(skeleton, Stat.DEX);
			boolean highest = true;
			for (Stat stat : Stat.values()) {
				int value = MobStats.base(skeleton, stat);
				total += value;
				highest &= stat == Stat.DEX || value < dex;
			}
			helper.assertTrue(total >= 27 && total <= 33, "total was " + total);
			helper.assertTrue(highest, "DEX " + dex + " was not the highest");
		} finally {
			MobStats.setRandomized(false);
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}
}
