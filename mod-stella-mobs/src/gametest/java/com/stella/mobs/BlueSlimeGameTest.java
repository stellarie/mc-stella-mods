package com.stella.mobs;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.entity.BlueSlime;
import com.stella.mobs.entity.StellaEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Every blue slime is passive and carries the harmless tag, and no mob targets it. */
public class BlueSlimeGameTest {
	@GameTest(maxTicks = 100)
	public void escortIsPassive(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		BlueSlime escort = StellaEntities.BLUE_SLIME.create(helper.getLevel(), EntitySpawnReason.EVENT);
		EliteState.setSummoned(escort, true);
		escort.snapTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)), 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(escort);
		ServerPlayer player = Arena.addSurvivalPlayer(helper);
		Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1.0, 3.0));
		player.setPos(spot.x, spot.y, spot.z);
		float before = player.getHealth();
		helper.runAfterDelay(60, () -> {
			escort.playerTouch(player);
			float taken = before - player.getHealth();
			StellaMobs.LOGGER.info("[BEHAVIOR] test=blueEscortPassive RESULT tagged={} target={} taken={}",
				escort.entityTags().contains(BlueSlime.HARMLESS_TAG), escort.getTarget(), taken);
			helper.assertTrue(escort.isEscort() && escort.entityTags().contains(BlueSlime.HARMLESS_TAG), "the escort is not tagged harmless");
			helper.assertTrue(escort.getTarget() == null && taken == 0.0F, "the escort targeted " + escort.getTarget() + " and dealt " + taken);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void golemSpares(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		BlueSlime slime = helper.spawn(StellaEntities.BLUE_SLIME, new Vec3(2.5, 1.0, 2.5));
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new Vec3(4.5, 1.0, 4.5));
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(6.5, 1.0, 6.5));
		golem.setTarget(slime);
		boolean forced = golem.getTarget() == slime;
		golem.setTarget(zombie);
		boolean control = golem.getTarget() == zombie;
		golem.setTarget(null);
		zombie.discard();
		helper.runAfterDelay(60, () -> {
			StellaMobs.LOGGER.info("[BEHAVIOR] test=blueGolemSpares RESULT forced={} control={} later={}", forced, control, golem.getTarget());
			helper.assertTrue(control, "the golem could not target a zombie, so the case proves nothing");
			helper.assertTrue(!forced && golem.getTarget() != slime, "the golem targeted the blue slime");
			helper.succeed();
		});
	}
}
