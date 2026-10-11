package com.stella.nether;

import com.stella.mobs.scaling.SelfScaled;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The Sovereign takes a threat level from the raid's players; its health and its hits follow the mobs mod's curves. */
public class SovereignScaleGameTest {
	/** Vanilla's small fireball damage; the Sovereign's deals half of it before the level factor. */
	static final float VANILLA_FIREBALL = 5.0F;
	static final double TOLERANCE = 0.05;
	/** A level-0 player in a normal raid: the mode's minimum plus the bonus. */
	static final int LEVEL = RaidInstance.MIN_LEVEL_NORMAL + BlazeSovereign.LEVEL_BONUS;

	/** Runs the real small-fireball impact on a victim without flying it there. */
	static final class Striker extends SmallFireball {
		Striker(final ServerLevel raid, final Blaze owner) {
			super(raid, owner, new Vec3(1.0, 0.0, 0.0));
		}

		void strike(final net.minecraft.world.entity.Entity victim) {
			this.onHitEntity(new EntityHitResult(victim));
		}
	}

	/** A target no hit can finish, with no armor, so the health it loses is the hit it took. */
	static Zombie sponge(final SovereignSpecialsGameTest.Scene scene, final Vec3 at, final boolean fireProof, final GameTestHelper helper) {
		Zombie zombie = SovereignSpecialsGameTest.standIn(helper, scene, at);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000.0);
		zombie.setHealth(5000.0F);
		// A zombie wears 2 armor by nature; armor would trim every hit.
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		if (fireProof) {
			zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0));
		}
		return zombie;
	}

	/**
	 * A sponge as a probe needs it, put right at the moment of the probe: no effect, no absorption, full health, no hit
	 * cooldown. A raid creature can carry effects its load handlers gave it, and one that does (a health boost, say) makes
	 * the health it lost read wrong.
	 */
	static void settle(final Zombie zombie, final boolean fireProof) {
		zombie.removeAllEffects();
		zombie.setAbsorptionAmount(0.0F);
		if (fireProof) {
			zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0));
		}
		zombie.setHealth(zombie.getMaxHealth());
		zombie.damageCooldownTime = 0;
	}

	@GameTest(environment = "mod-stella-nether-test:sovereign_scale_level", maxTicks = 100)
	public void levelFollowsTheRaidsBestPlayer(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer low = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		ServerPlayer high = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) low.level();
		low.experienceLevel = 20;
		high.experienceLevel = 80;
		int best = BlazeSovereign.threatLevel(raid, RaidMode.NORMAL);
		Blaze blaze = BlazeSovereign.rise(raid, BlazeSovereignGameTest.arena(raid, server, 8));
		int risen = ThreatState.level(blaze);
		high.experienceLevel = 10;
		int floored = BlazeSovereign.threatLevel(raid, RaidMode.NORMAL);
		int empty = BlazeSovereign.threatLevel(List.of(), RaidMode.NORMAL);
		int emptyHard = BlazeSovereign.threatLevel(List.of(), RaidMode.HARD);
		NetherTestSupport.result("sovereignLevel", "best=" + best + " risen=" + risen + " floored=" + floored + " empty=" + empty + " emptyHard=" + emptyHard);
		blaze.discard();
		NetherTestSupport.leave(helper, high);
		NetherTestSupport.leave(helper, low);
		helper.assertTrue(best == 90 && risen == 90 && floored == LEVEL && empty == LEVEL && emptyHard == RaidInstance.MIN_LEVEL_HARD + BlazeSovereign.LEVEL_BONUS,
			"levels: best " + best + ", risen " + risen + ", floored " + floored + ", empty " + empty + ", empty hard " + emptyHard);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:sovereign_scale_health", maxTicks = 100)
	public void healthFollowsTheLevel(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.NORMAL);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		player.experienceLevel = 80;
		Blaze blaze = BlazeSovereign.rise(raid, BlazeSovereignGameTest.arena(raid, server, 8));
		int level = ThreatState.level(blaze);
		double expected = BlazeSovereign.BASE_HEALTH * SpawnScaling.healthMultiplier(level);
		double effective = BlazeSovereign.effectiveHealth(blaze);
		double attribute = Math.min(expected, BlazeSovereign.healthCap());
		NetherTestSupport.result("sovereignScaledHealth", "level=" + level + " effective=" + effective + " expected=" + expected + " attribute=" + blaze.getMaxHealth());
		blaze.discard();
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(level == 90 && Math.abs(effective - expected) < 1.0 && Math.abs(blaze.getMaxHealth() - attribute) < 1.0E-3,
			"level " + level + ": health " + effective + " of " + expected + ", attribute " + blaze.getMaxHealth() + " of " + attribute);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:sovereign_scale_dive", maxTicks = 1200)
	public void specialHitsFollowTheLevelDive(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 16);
		Zombie target = sponge(scene, scene.floor().add(4.0, 0.0, 0.0), true, helper);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, target), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				settle(target, true);
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.DIVE, target);
			})
			.thenWaitUntil(() -> helper.assertTrue(cast[0].done(), "the dive has not landed"))
			.thenExecute(() -> {
				double lost = target.getMaxHealth() - target.getHealth();
				// Fire Resistance refuses the fire half, so the physical half is all this target takes.
				double expected = SovereignSpecials.DIVE_DAMAGE * SelfScaled.factor(LEVEL) / 2.0;
				int live = ThreatState.level(scene.sovereign());
				NetherTestSupport.result("scaledDive", "lost=" + lost + " expected=" + expected + " hit=" + cast[0].hits().contains(target.getUUID()) + " level=" + live);
				SovereignSpecialsGameTest.end(helper, scene, target);
				helper.assertTrue(Math.abs(lost - expected) < TOLERANCE && live == LEVEL, "the dive took " + lost + " of " + expected + " at level " + live + ", effects " + target.getActiveEffects());
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:sovereign_scale_wave", maxTicks = 1200)
	public void specialHitsFollowTheLevelWave(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie target = sponge(scene, scene.floor().add(SovereignSpecials.WAVE_RADIUS / 2.0, 0.0, 0.0), false, helper);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, target), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				settle(target, false);
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.CHARGED_WAVE, target);
			})
			.thenWaitUntil(() -> helper.assertTrue(cast[0].done(), "the wave has not finished"))
			.thenExecute(() -> {
				double lost = target.getMaxHealth() - target.getHealth();
				double expected = SovereignSpecials.WAVE_DAMAGE * SelfScaled.factor(LEVEL);
				int live = ThreatState.level(scene.sovereign());
				NetherTestSupport.result("scaledWave", "lost=" + lost + " expected=" + expected + " hit=" + cast[0].hits().contains(target.getUUID()) + " level=" + live);
				SovereignSpecialsGameTest.end(helper, scene, target);
				helper.assertTrue(Math.abs(lost - expected) < TOLERANCE && live == LEVEL, "the wave took " + lost + " of " + expected + " at level " + live + ", effects " + target.getActiveEffects());
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:sovereign_scale_fireball", maxTicks = 1200)
	public void fireballsFollowTheLevel(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie target = sponge(scene, scene.floor().add(4.0, 0.0, 0.0), false, helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, target), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				settle(target, false);
				new Striker(scene.raid(), scene.sovereign()).strike(target);
				double lost = target.getMaxHealth() - target.getHealth();
				double expected = VANILLA_FIREBALL * 0.5 * SelfScaled.factor(LEVEL);
				int live = ThreatState.level(scene.sovereign());
				NetherTestSupport.result("scaledFireball", "lost=" + lost + " expected=" + expected + " level=" + live);
				SovereignSpecialsGameTest.end(helper, scene, target);
				helper.assertTrue(Math.abs(lost - expected) < TOLERANCE && live == LEVEL, "the fireball took " + lost + " of " + expected + " at level " + live);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:sovereign_scale_hard", maxTicks = 100)
	public void levelFollowsTheHardMinimum(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidMode.HARD);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Blaze blaze = BlazeSovereign.rise(raid, BlazeSovereignGameTest.arena(raid, server, RaidMode.HARD, 8));
		int risen = ThreatState.level(blaze);
		player.experienceLevel = 70;
		int better = BlazeSovereign.threatLevel(raid, RaidMode.HARD);
		NetherTestSupport.result("sovereignHardLevel", "risen=" + risen + " better=" + better);
		blaze.discard();
		NetherTestSupport.leave(helper, player);
		int hard = RaidInstance.MIN_LEVEL_HARD + BlazeSovereign.LEVEL_BONUS;
		helper.assertTrue(risen == hard && better == 70 + BlazeSovereign.LEVEL_BONUS, "hard levels: risen " + risen + " of " + hard + ", with a level-70 player " + better);
		helper.succeed();
	}
}
