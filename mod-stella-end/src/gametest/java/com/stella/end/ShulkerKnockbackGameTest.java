package com.stella.end;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.phys.Vec3;

/** A hit on a closed castle shulker throws the attacker back; a hit on an open one, or from afar, does not. */
public class ShulkerKnockbackGameTest {
	/** A shulker and an attacker, both without AI, on the floor 3 ground, the attacker a given distance away. */
	record Duel(Shulker shulker, Zombie attacker, ServerLevel end) {
	}

	static Duel duel(final GameTestHelper helper, final boolean closed, final double minDistance, final double maxDistance) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(3);
		int[] cell = plan.spawnCells().get(plan.spawnCells().size() / 2);
		BlockPos shulkerAt = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(3), cell[1]);
		BlockPos attackerAt = EndTestSupport.cellNear(helper, plan, 3, cell[0], cell[1], minDistance, maxDistance);
		Shulker shulker = (Shulker) EndMobs.spawn(end, 3, EndMobs.Kind.SHULKER, shulkerAt);
		helper.assertTrue(shulker != null, "the shulker was refused");
		shulker.setNoAi(true);
		ShulkerKnockback.setClosed(shulker, closed);
		Zombie attacker = EntityTypes.ZOMBIE.create(end, EntitySpawnReason.SPAWNER);
		// Tagged so no other mod's spawn roll gives it knockback resistance or armor.
		attacker.addTag("mod-stella-mobs.no_spawn_roll");
		attacker.addTag(EndMobs.FLOOR_TAG + "0");
		attacker.setNoAi(true);
		attacker.snapTo(attackerAt.getX() + 0.5, attackerAt.getY(), attackerAt.getZ() + 0.5, 0.0F, 0.0F);
		helper.assertTrue(end.addFreshEntity(attacker), "the attacker was refused");
		return new Duel(shulker, attacker, end);
	}

	/** How fast a hit threw the attacker away from the shulker, in blocks a tick; negative if toward it. */
	static double thrown(final Duel duel, final DamageSource source) {
		duel.attacker().setDeltaMovement(Vec3.ZERO);
		duel.shulker().setInvulnerableTime(0);
		duel.shulker().hurtServer(duel.end(), source, 2.0F);
		Vec3 away = duel.attacker().position().subtract(duel.shulker().position()).multiply(1.0, 0.0, 1.0).normalize();
		return duel.attacker().getDeltaMovement().multiply(1.0, 0.0, 1.0).dot(away);
	}

	@GameTest(environment = "mod-stella-end-test:shulker_closed", maxTicks = 100)
	public void aMeleeHitOnAClosedShulkerThrowsTheAttackerBack(final GameTestHelper helper) {
		Duel duel = duel(helper, true, 2.0, 3.5);
		double resist = duel.attacker().getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		double speed = thrown(duel, duel.end().damageSources().mobAttack(duel.attacker()));
		double expected = ShulkerKnockback.STRENGTH * (1.0 - resist);
		EndTestSupport.result("shulkerClosed", "thrown=" + speed + " expected=" + expected + " distance=" + duel.attacker().distanceTo(duel.shulker()));
		EndTestSupport.fresh(helper);
		helper.assertTrue(Math.abs(speed - expected) < 0.05, "the attacker was thrown at " + speed + ", expected " + expected);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:shulker_open", maxTicks = 100)
	public void aMeleeHitOnAnOpenShulkerThrowsNobody(final GameTestHelper helper) {
		Duel duel = duel(helper, false, 2.0, 3.5);
		double speed = thrown(duel, duel.end().damageSources().mobAttack(duel.attacker()));
		EndTestSupport.result("shulkerOpen", "thrown=" + speed);
		EndTestSupport.fresh(helper);
		helper.assertTrue(Math.abs(speed) < 0.05, "an open shulker threw the attacker at " + speed);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:shulker_far", maxTicks = 100)
	public void aHitFromBeyondFourBlocksThrowsNobody(final GameTestHelper helper) {
		Duel duel = duel(helper, true, 5.0, 8.0);
		double speed = thrown(duel, duel.end().damageSources().mobAttack(duel.attacker()));
		double distance = duel.attacker().distanceTo(duel.shulker());
		EndTestSupport.result("shulkerFar", "thrown=" + speed + " distance=" + distance);
		EndTestSupport.fresh(helper);
		helper.assertTrue(distance > ShulkerKnockback.RANGE, "the test attacker stood within reach at " + distance);
		helper.assertTrue(Math.abs(speed) < 0.05, "a far hit threw the attacker at " + speed);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:shulker_thrown", maxTicks = 100)
	public void aThrownSnowballOnAClosedShulkerThrowsNobody(final GameTestHelper helper) {
		Duel duel = duel(helper, true, 2.0, 3.5);
		Snowball snowball = EntityTypes.SNOWBALL.create(duel.end(), EntitySpawnReason.MOB_SUMMONED);
		snowball.setOwner(duel.attacker());
		double speed = thrown(duel, duel.end().damageSources().thrown(snowball, duel.attacker()));
		EndTestSupport.result("shulkerThrown", "thrown=" + speed);
		EndTestSupport.fresh(helper);
		helper.assertTrue(Math.abs(speed) < 0.05, "a thrown snowball threw the thrower at " + speed);
		helper.succeed();
	}
}
