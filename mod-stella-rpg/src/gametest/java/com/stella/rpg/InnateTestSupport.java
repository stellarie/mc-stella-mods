package com.stella.rpg;

import com.stella.rpg.job.Job;
import com.stella.rpg.job.Jobs;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Shared set-up for the job innate cases: a joined player with a job, tough dummies, and a death with a respawn. */
final class InnateTestSupport {
	static final double TOUGH = 100_000.0;
	static final double SLACK = 0.002;

	private InnateTestSupport() {
	}

	/** A joined player of this job at the middle of the test cell, with empty hands and no worn gear. */
	static ServerPlayer player(final GameTestHelper helper, final Job job, final int level) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		player.experienceLevel = level;
		if (job != Job.NONE && !Jobs.choose(player, job)) {
			helper.fail("the job " + job + " was refused");
		}
		return player;
	}

	/** Takes the job away, so the innate of a finished case stops acting on its neighbours. */
	static void retire(final ServerPlayer player) {
		player.setAttached(Jobs.ATTACHMENT, Jobs.State.EMPTY);
		player.setAbsorptionAmount(0.0F);
	}

	/** A mob with no AI, no gear, and a large health pool at full health. */
	static <E extends Mob> E dummy(final GameTestHelper helper, final EntityType<E> type, final int x, final int z) {
		return dummy(helper, type, x, 1, z);
	}

	/** The same mob at a height. Held in the air, it stays clear of the walls of the test cell. */
	static <E extends Mob> E dummy(final GameTestHelper helper, final EntityType<E> type, final int x, final int y, final int z) {
		E mob = helper.spawnWithNoFreeWill(type, x, y, z);
		mob.setNoGravity(true);
		RpgTestSupport.bare(mob);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TOUGH);
		mob.setHealth(mob.getMaxHealth());
		return mob;
	}

	/** Damage that reaches health as exactly this many health points, whatever the VIT of the target. */
	static float hit(final GameTestHelper helper, final ServerPlayer target, final double healthPoints) {
		DamageSource source = helper.getLevel().damageSources().genericKill();
		target.damageCooldownTime = 0;
		float before = target.getHealth() + target.getAbsorptionAmount();
		target.hurtServer(helper.getLevel(), source, (float) (healthPoints / RpgScale.k()));
		return before - target.getHealth() - target.getAbsorptionAmount();
	}

	/** Kills the player and returns the player that respawns, put back in the middle of the test cell. */
	static ServerPlayer respawn(final GameTestHelper helper, final ServerPlayer player) {
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1_000_000.0F);
		helper.assertTrue(player.isDeadOrDying(), "the player survived, so the case proves nothing");
		ServerPlayer reborn = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		RpgTestSupport.spendSecondWind(reborn);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		reborn.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.assertTrue(reborn.position().distanceTo(at) < 1.0 && reborn.level() == helper.getLevel(),
			"the respawned player stands at " + reborn.position() + " in " + reborn.level().dimension() + ", not at " + at);
		return reborn;
	}

	static boolean near(final double actual, final double expected) {
		return Math.abs(actual - expected) <= SLACK * Math.abs(expected) + 1e-3;
	}
}
