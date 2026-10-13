package com.stella.rpg;

import com.stella.rpg.job.Job;
import com.stella.rpg.job.Sanctuary;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.spider.Spider;
import org.joml.Vector3f;

/**
 * The Priest innate: once a second, players and allies within 3 blocks heal 1.5 percent of their max health, and hostile
 * mobs within 3 blocks lose 1.5 percent of their max health. Red and green sparkles mark the radius.
 */
public final class SanctuaryGameTest {
	private static final int LEVEL = 20;
	private static final int WINDOW = 70;

	/** The health of a mob on each tick of the window. */
	private record Trace(LivingEntity mob, List<Float> health) {
		static Trace of(final GameTestHelper helper, final LivingEntity mob) {
			Trace trace = new Trace(mob, new ArrayList<>());
			helper.onEachTick(() -> trace.health().add(mob.getHealth()));
			return trace;
		}

		/** The ticks on which the health changed, with the change. */
		List<double[]> changes() {
			List<double[]> out = new ArrayList<>();
			for (int tick = 1; tick < this.health.size(); tick++) {
				float delta = this.health.get(tick) - this.health.get(tick - 1);
				if (delta != 0.0F) {
					out.add(new double[] {tick, delta});
				}
			}
			return out;
		}
	}

	private static Wolf pet(final GameTestHelper helper, final ServerPlayer owner, final int x, final int z) {
		return pet(helper, owner, x, 1, z);
	}

	private static Wolf pet(final GameTestHelper helper, final ServerPlayer owner, final int x, final int y, final int z) {
		Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, x, y, z);
		wolf.setNoGravity(true);
		// Taming resets the health pool, so the dummy pool comes after it.
		wolf.tame(owner);
		RpgTestSupport.bare(wolf);
		wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(InnateTestSupport.TOUGH);
		wolf.setHealth(wolf.getMaxHealth() * 0.5F);
		return wolf;
	}

	@GameTest(maxTicks = WINDOW + 10)
	public void everySecondAlliesHealAndHostilesLoseOnePointFivePercent(final GameTestHelper helper) {
		helper.assertTrue(Sanctuary.SHARE == 0.015 && Sanctuary.RADIUS == 3.0 && Sanctuary.PULSE_TICKS == 20,
			"the aura numbers drifted from 1.5 percent, 3 blocks and 20 ticks");
		ServerPlayer priest = InnateTestSupport.player(helper, Job.PRIEST, LEVEL);
		Wolf pet = pet(helper, priest, 4, 1, 2);
		// Five blocks above the priest, so no wall of the cell is near them.
		Wolf farPet = pet(helper, priest, 2, 6, 2);
		Spider foe = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 2, 4);
		Spider farFoe = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 3, 6, 2);
		Trace petTrace = Trace.of(helper, pet);
		Trace farPetTrace = Trace.of(helper, farPet);
		Trace foeTrace = Trace.of(helper, foe);
		Trace farFoeTrace = Trace.of(helper, farFoe);
		helper.runAfterDelay(WINDOW, () -> {
			List<double[]> healed = petTrace.changes();
			List<double[]> hurt = foeTrace.changes();
			RpgTestSupport.result("everySecondAlliesHealAndHostilesLoseOnePointFivePercent",
				"healed=" + summary(healed) + " hurt=" + summary(hurt) + " farPet=" + summary(farPetTrace.changes()) + " farFoe=" + summary(farFoeTrace.changes()));
			helper.assertTrue(healed.size() >= 2, "the pet healed on " + healed.size() + " ticks");
			helper.assertTrue(hurt.size() == healed.size(), "the foe was hurt on " + hurt.size() + " ticks, the pet healed on " + healed.size());
			for (int pulse = 0; pulse < healed.size(); pulse++) {
				helper.assertTrue(healed.get(pulse)[0] == hurt.get(pulse)[0], "the heal and the hit came on different ticks");
				helper.assertTrue(InnateTestSupport.near(healed.get(pulse)[1], Sanctuary.SHARE * pet.getMaxHealth()),
					"the pet healed " + healed.get(pulse)[1] + ", expected " + Sanctuary.SHARE * pet.getMaxHealth());
				helper.assertTrue(InnateTestSupport.near(-hurt.get(pulse)[1], Sanctuary.SHARE * foe.getMaxHealth()),
					"the foe lost " + -hurt.get(pulse)[1] + ", expected " + Sanctuary.SHARE * foe.getMaxHealth());
				if (pulse > 0) {
					double gap = healed.get(pulse)[0] - healed.get(pulse - 1)[0];
					helper.assertTrue(gap == Sanctuary.PULSE_TICKS, "pulses came " + gap + " ticks apart");
				}
			}
			helper.assertTrue(farPetTrace.changes().isEmpty(), "a pet 5 blocks away was healed");
			helper.assertTrue(farFoeTrace.changes().isEmpty(), "a mob 5 blocks away was hurt by " + farFoe.getLastDamageSource());
			pet.discard();
			farPet.discard();
			foe.discard();
			farFoe.discard();
			InnateTestSupport.retire(priest);
			helper.succeed();
		});
	}

	private static String summary(final List<double[]> changes) {
		List<String> out = new ArrayList<>();
		changes.forEach(change -> out.add((int) change[0] + ":" + change[1]));
		return out.toString();
	}

	@GameTest(maxTicks = 50)
	public void onlyPlayersPetsAndSummonsAreAlliesAndOnlyUnmarkedHostilesAreFoes(final GameTestHelper helper) {
		ServerPlayer priest = InnateTestSupport.player(helper, Job.PRIEST, LEVEL);
		Spider summoned = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 4, 2);
		summoned.addTag(RpgCombat.SUMMONED_TAG);
		summoned.setHealth(summoned.getMaxHealth() * 0.5F);
		Spider patrol = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 2, 4);
		patrol.addTag("mc-stella-villages.patrol");
		patrol.setHealth(patrol.getMaxHealth() * 0.5F);
		Wolf wild = InnateTestSupport.dummy(helper, EntityTypes.WOLF, 1, 2);
		wild.setHealth(wild.getMaxHealth() * 0.5F);
		float summonedBefore = summoned.getHealth();
		float patrolBefore = patrol.getHealth();
		float wildBefore = wild.getHealth();
		helper.runAfterDelay(Sanctuary.PULSE_TICKS + 8L, () -> {
			RpgTestSupport.result("onlyPlayersPetsAndSummonsAreAlliesAndOnlyUnmarkedHostilesAreFoes",
				"summoned=" + summonedBefore + "->" + summoned.getHealth() + " patrol=" + patrolBefore + "->" + patrol.getHealth()
					+ " wild=" + wildBefore + "->" + wild.getHealth());
			helper.assertTrue(summoned.getHealth() > summonedBefore, "a summoned mob was not healed, though its kind is hostile");
			helper.assertTrue(patrol.getHealth() == patrolBefore, "a village patrol member was touched: " + patrolBefore + "->" + patrol.getHealth());
			helper.assertTrue(wild.getHealth() == wildBefore, "a wild wolf was touched: " + wildBefore + "->" + wild.getHealth());
			summoned.discard();
			patrol.discard();
			wild.discard();
			InnateTestSupport.retire(priest);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void aFighterBesideThePetsAndFoesChangesNothing(final GameTestHelper helper) {
		ServerPlayer fighter = InnateTestSupport.player(helper, Job.FIGHTER, LEVEL);
		Wolf pet = pet(helper, fighter, 4, 2);
		Spider foe = InnateTestSupport.dummy(helper, EntityTypes.SPIDER, 2, 4);
		float petBefore = pet.getHealth();
		helper.runAfterDelay(2L * Sanctuary.PULSE_TICKS + 4L, () -> {
			helper.assertTrue(pet.getHealth() == petBefore && foe.getHealth() == foe.getMaxHealth(),
				"a fighter's neighbours changed: pet " + petBefore + "->" + pet.getHealth() + ", foe " + foe.getHealth());
			pet.discard();
			foe.discard();
			InnateTestSupport.retire(fighter);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void redAndGreenSparklesMarkTheRadiusAtTheFeet(final GameTestHelper helper) {
		RpgTestSupport.Recorded recorded = RpgTestSupport.recordedPlayer(helper);
		ServerPlayer priest = recorded.player();
		RpgTestSupport.bare(priest);
		priest.experienceLevel = LEVEL;
		com.stella.rpg.job.Jobs.choose(priest, Job.PRIEST);
		helper.runAfterDelay(4L, () -> {
			while (recorded.channel().readOutbound() != null) {
				// Discard what the priest was sent so far.
			}
			helper.runAfterDelay(25L, () -> {
				int red = 0;
				int green = 0;
				double narrow = Double.MAX_VALUE;
				double wide = 0.0;
				double lowest = Double.MAX_VALUE;
				double highest = -Double.MAX_VALUE;
				Object message;
				while ((message = recorded.channel().readOutbound()) != null) {
					// A priest in a neighbouring cell sends its own sparkles to the same connection.
					if (message instanceof ClientboundLevelParticlesPacket packet && packet.particle() instanceof DustParticleOptions dust
						&& Math.hypot(packet.x() - priest.getX(), packet.z() - priest.getZ()) < Sanctuary.RADIUS + 1.0) {
						Vector3f color = dust.getColor();
						red += color.x > 0.8F && color.y < 0.4F ? 1 : 0;
						green += color.y > 0.8F && color.x < 0.4F ? 1 : 0;
						double distance = Math.hypot(packet.x() - priest.getX(), packet.z() - priest.getZ());
						narrow = Math.min(narrow, distance);
						wide = Math.max(wide, distance);
						lowest = Math.min(lowest, packet.y() - priest.getY());
						highest = Math.max(highest, packet.y() - priest.getY());
					}
				}
				RpgTestSupport.result("redAndGreenSparklesMarkTheRadiusAtTheFeet",
					"red=" + red + " green=" + green + " near=" + narrow + " far=" + wide + " low=" + lowest + " high=" + highest);
				helper.assertTrue(red >= 12 && green >= 12, "the aura showed " + red + " red and " + green + " green sparkles");
				helper.assertTrue(Math.abs(narrow - Sanctuary.RADIUS) < 0.05 && Math.abs(wide - Sanctuary.RADIUS) < 0.05,
					"the sparkles lie " + narrow + " to " + wide + " blocks from the priest, not " + Sanctuary.RADIUS);
				helper.assertTrue(lowest >= 0.0 && highest <= 0.3, "the sparkles are " + lowest + " to " + highest + " blocks above the feet");
				InnateTestSupport.retire(priest);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 70)
	public void theAuraStillPulsesAfterRespawn(final GameTestHelper helper) {
		ServerPlayer priest = InnateTestSupport.player(helper, Job.PRIEST, 45);
		ServerPlayer reborn = InnateTestSupport.respawn(helper, priest);
		Wolf pet = pet(helper, reborn, 4, 2);
		Trace trace = Trace.of(helper, pet);
		helper.runAfterDelay(2L * Sanctuary.PULSE_TICKS + 6L, () -> {
			List<double[]> healed = trace.changes();
			RpgTestSupport.result("theAuraStillPulsesAfterRespawn", "healed=" + summary(healed));
			helper.assertTrue(!healed.isEmpty(), "the respawned priest healed nothing");
			helper.assertTrue(InnateTestSupport.near(healed.getFirst()[1], Sanctuary.SHARE * pet.getMaxHealth()),
				"the respawned priest healed " + healed.getFirst()[1]);
			pet.discard();
			InnateTestSupport.retire(reborn);
			helper.succeed();
		});
	}
}
