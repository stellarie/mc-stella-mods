package com.stella.learn;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.HoldStandoffGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.SkeletonVolleyGoal;
import com.stella.mobs.behavior.StrayKit;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.mixin.MobTargetSelectorAccessor;
import com.stella.learn.model.LearnedArcherGoal;
import com.stella.learn.model.ModelDriver;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A bow archer gets the model's one goal, every other mob keeps its own, and the config switch keeps them all. */
public class ModelInstallGameTest {
	/** Waits past the one tick the driver holds a loaded mob, so the mobs mod has installed its goals first. */
	private static final long INSTALL_WAIT = 6L;
	private static final List<Class<?>> REMOVED = List.of(
		RetreatGoal.class, KeepDistanceGoal.class, HoldStandoffGoal.class, SkeletonVolleyGoal.class, RangedBowAttackGoal.class,
		TacticGoal.class, LearnedBandGoal.class, WaitOutShieldGoal.class, AnswerRallyGoal.class, TakeCoverGoal.class,
		HoldAtChokeGoal.class, InterceptGoal.class, DodgeCritGoal.class
	);

	private static boolean keepsEverything(final Mob mob, final Class<?>... goals) {
		for (Class<?> goal : goals) {
			if (!ModelTests.has(mob, goal)) {
				return false;
			}
		}

		return !ModelTests.tagged(mob) && !ModelTests.has(mob, LearnedArcherGoal.class);
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void modelDrivenArcherHasOnlyTheModelGoal(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob archer = ModelTests.archer(helper, 24.5, 24.5);
		LearnArena.keepTargeting(helper, archer, player);
		LearnArena.whenReady(helper, List.of(player, archer), () -> LearnArena.pollUntil(helper, () -> ModelTests.tagged(archer), 40, () -> {
			for (Class<?> removed : REMOVED) {
				helper.assertTrue(!ModelTests.has(archer, removed), "the archer still has " + removed.getSimpleName() + ": " + ModelTests.goalNames(archer));
			}

			long models = archer.getGoalSelector().getAvailableGoals().stream().filter(wrapped -> wrapped.getGoal() instanceof LearnedArcherGoal).count();
			helper.assertTrue(models == 1, "the archer has " + models + " model goals");
			helper.assertTrue(ModelDriver.isDriven(archer), "the driver does not list the archer");
			helper.assertTrue(!((MobTargetSelectorAccessor) archer).stellaMobs$targetSelector().getAvailableGoals().isEmpty(), "the archer lost its target goals");
			// The install line names what was taken, which shows the goals were there before.
			List<String> installs = ModelTests.lines(archer, "INSTALL");
			helper.assertTrue(installs.size() == 1, "the archer wrote " + installs.size() + " INSTALL lines");
			String install = installs.get(0);
			helper.assertTrue(install.contains("SkeletonVolleyGoal") && install.contains("RetreatGoal") && install.contains("TacticGoal"), "INSTALL does not name the removed goals: " + install);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=modelDrivenArcherHasOnlyTheModelGoal RESULT goals={}", ModelTests.goalNames(archer));
			LearnArena.pollUntil(helper, () -> !ModelTests.lines(archer, "ACT").isEmpty(), 80, () -> ModelTests.pass(helper), () -> "the driven archer never wrote an ACT line");
		}, () -> "the archer was never made model driven: " + ModelTests.goalNames(archer)));
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV)
	public void ineligibleMobsKeepTheirGoals(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob control = ModelTests.archer(helper, 8.5, 24.5);
		Mob sword = ModelTests.archer(helper, 14.5, 24.5);
		sword.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Mob shielded = ModelTests.archer(helper, 20.5, 24.5);
		shielded.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3(26.5, 1.0, 24.5));
		zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		zombie.setPersistenceRequired();
		helper.getLevel().addFreshEntity(zombie);
		Mob sniper = ModelTests.skeleton(helper, EntityTypes.STRAY, 32.5, 24.5, true);
		Elite.mark(sniper, GearTier.IRON, 1.0);
		StrayKit.install(sniper);
		LearnArena.whenReady(helper, List.of(player, control, sword, shielded, zombie, sniper), () -> helper.runAfterDelay(INSTALL_WAIT, () -> {
			helper.assertTrue(ModelTests.tagged(control), "the control archer was not made model driven, so the test proves nothing: " + ModelTests.goalNames(control));
			helper.assertTrue(keepsEverything(sword, RetreatGoal.class, SkeletonVolleyGoal.class), "the sword skeleton lost its goals: " + ModelTests.goalNames(sword));
			helper.assertTrue(keepsEverything(shielded, RetreatGoal.class, SkeletonVolleyGoal.class), "the shielded skeleton lost its goals: " + ModelTests.goalNames(shielded));
			helper.assertTrue(keepsEverything(zombie, RetreatGoal.class), "the zombie lost its goals: " + ModelTests.goalNames(zombie));
			helper.assertTrue(keepsEverything(sniper, RetreatGoal.class, StrayKit.SniperGoal.class), "the elite stray lost its goals: " + ModelTests.goalNames(sniper));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=ineligibleMobsKeepTheirGoals RESULT driven={}", ModelDriver.drivenCount());
			ModelTests.pass(helper);
		}));
	}

	@GameTest(maxTicks = ModelTests.LONG, padding = LearnArena.PADDING, environment = ModelTests.ENV_SWITCH)
	public void switchOffKeepsCurrentGoals(final GameTestHelper helper) {
		ModelTests.acquire(helper);
		LearnConfig before = LearnConfigStore.get();
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 34.5);
		Mob early = ModelTests.archer(helper, 14.5, 24.5);
		LearnArena.keepTargeting(helper, early, player);
		LearnArena.whenReady(helper, List.of(player, early), () -> LearnArena.pollUntil(helper, () -> ModelTests.running(early), 60, () -> {
			// The switch flips while `early` is driven, then a second archer loads with it off.
			LearnConfigStore.set(before.withModelArcher(false));
			Mob late = ModelTests.archer(helper, 34.5, 24.5);
			helper.runAfterDelay(INSTALL_WAIT, () -> {
				helper.assertTrue(keepsEverything(late, SkeletonVolleyGoal.class, RetreatGoal.class), "the archer loaded with the switch off lost its goals: " + ModelTests.goalNames(late));
				helper.assertTrue(keepsEverything(early, SkeletonVolleyGoal.class, RetreatGoal.class), "the driven archer did not get its goals back: " + ModelTests.goalNames(early));
				helper.assertTrue(ModelTests.lines(early, "FALLBACK", "reason=switch").size() == 1, "no single FALLBACK reason=switch line for the driven archer");
				StellaLearn.LOGGER.info("[BEHAVIOR] test=switchOffKeepsCurrentGoals RESULT late={} early={}", ModelTests.goalNames(late), ModelTests.goalNames(early));
				LearnConfigStore.set(before);
				ModelTests.pass(helper);
			});
		}, () -> "the first archer never started its model goal"));
	}
}
