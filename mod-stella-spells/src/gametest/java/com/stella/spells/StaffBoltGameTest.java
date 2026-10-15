package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** A staff bolt reaches a zombie in line and hurts it. */
public class StaffBoltGameTest {
	@GameTest(maxTicks = 100)
	public void boltHurtsZombie(final GameTestHelper helper) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 6).withNoFreeWill().spawn();
		zombie.setNoGravity(true);
		helper.runAfterDelay(5L, () -> {
			float before = zombie.getHealth();
			helper.assertTrue(StaffBolt.fire(caster) == zombie, "bolt missed the zombie");
			helper.assertTrue(zombie.getHealth() < before, "zombie took no damage");
			helper.getLevel().getServer().getPlayerList().remove(caster);
			helper.succeed();
		});
	}
}
