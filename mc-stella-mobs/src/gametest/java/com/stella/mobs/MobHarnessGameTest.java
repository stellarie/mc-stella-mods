package com.stella.mobs;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public class MobHarnessGameTest {
	@GameTest
	public void zombieSpawnsInTestLevel(GameTestHelper context) {
		Zombie zombie = context.spawn(EntityTypes.ZOMBIE, 1, 1, 1);

		context.assertTrue(zombie.isAlive(), "spawned zombie is not alive");
		context.assertTrue(zombie.level() == context.getLevel(), "spawned zombie is not in the test level");
		context.succeed();
	}
}
