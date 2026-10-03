package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** A rally sends a mob to a flank slot beside the player, and only while the request lasts. */
public class RallyGameTest {
	private static final int LONG = 700;
	private static final double ARRIVED = 2.5;
	private static final double DOT_TOLERANCE = 0.1;
	private static final String ARCHER_CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": 5.0, "far": 15.0 } },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	static {
		Rally.register();
	}

	private static ServerPlayer facingSouth(final GameTestHelper helper) {
		ServerPlayer player = LearnArena.player(helper, 24.5, 20.5);
		player.setYRot(0.0F);
		player.setYHeadRot(0.0F);
		return player;
	}

	private static Callout rally(final Mob caller, final ServerPlayer player, final int duration) {
		return new Callout(Rally.TYPE, caller.getUUID(), player.getUUID(), 1, duration, null);
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		return helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aRallyingMobWalksToAFlankSlotBeforeItReachesThePlayer(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper);
		Mob caller = zombie(helper, 20, 16);
		Mob receiver = zombie(helper, 24, 42);
		LearnArena.whenReady(helper, java.util.List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player, 300), LearnConfig.defaults());
			Vec3 slot = Rally.slotOf(receiver);
			helper.assertTrue(slot != null, "the receiver was given no slot");
			LearnArena.pollUntil(helper, () -> receiver.position().distanceTo(slot) <= ARRIVED, LONG - 40, () -> {
				double distance = receiver.distanceTo(player);
				StellaLearn.LOGGER.info(
					"[BEHAVIOR] test=aRallyingMobWalksToAFlankSlotBeforeItReachesThePlayer RESULT slotDistance={} playerDistance={}",
					receiver.position().distanceTo(slot), distance
				);
				helper.assertTrue(distance > 3.5, "the receiver reached the player, not its slot: " + distance);
				helper.succeed();
			}, () -> "the receiver never reached its slot");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSlotIsBesideThePlayerNotInFrontOrBehind(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper);
		Mob caller = zombie(helper, 20, 16);
		Mob receiver = zombie(helper, 24, 42);
		LearnArena.whenReady(helper, java.util.List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player, 300), LearnConfig.defaults());
			Vec3 slot = Rally.slotOf(receiver);
			helper.assertTrue(slot != null, "the receiver was given no slot");
			Vec3 offset = slot.subtract(player.position());
			Vec3 flat = new Vec3(offset.x, 0.0, offset.z);
			Vec3 facing = player.getLookAngle();
			double along = Math.abs(flat.normalize().dot(new Vec3(facing.x, 0.0, facing.z).normalize()));
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theSlotIsBesideThePlayerNotInFrontOrBehind RESULT along={} distance={}", along, flat.length());
			helper.assertTrue(along < DOT_TOLERANCE, "the slot lies " + along + " along the player's facing");
			helper.assertTrue(Math.abs(flat.length() - Rally.MELEE_SLOT_DISTANCE) < 0.01, "the slot is " + flat.length() + " from the player");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void twoReceiversTakeOppositeFlanks(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper);
		Mob caller = zombie(helper, 20, 16);
		Mob first = zombie(helper, 20, 42);
		Mob second = zombie(helper, 28, 42);
		LearnArena.whenReady(helper, java.util.List.of(player, caller, first, second), () -> {
			CalloutBoard.send(caller, player, rally(caller, player, 300), LearnConfig.defaults());
			List<Mob> receivers = List.of(first, second);
			helper.assertTrue(receivers.stream().allMatch(mob -> Rally.slotOf(mob) != null), "a receiver was given no slot");
			double a = Rally.slotOf(first).subtract(player.position()).x;
			double b = Rally.slotOf(second).subtract(player.position()).x;
			StellaLearn.LOGGER.info("[BEHAVIOR] test=twoReceiversTakeOppositeFlanks RESULT firstSide={} secondSide={}", a, b);
			helper.assertTrue(a * b < 0.0, "both receivers took the same flank: " + a + " and " + b);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aRallyGivesAnIdleMobItsTarget(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper);
		Mob caller = zombie(helper, 20, 16);
		Mob receiver = zombie(helper, 24, 42);
		// Without its own AI it picks no target for itself, so a target it holds later came from the rally.
		receiver.setNoAi(true);
		LearnArena.whenReady(helper, java.util.List.of(player, caller, receiver), () -> {
			helper.assertTrue(receiver.getTarget() == null, "the receiver already had a target, so the case proves nothing");
			CalloutBoard.send(caller, player, rally(caller, player, 300), LearnConfig.defaults());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aRallyGivesAnIdleMobItsTarget RESULT target={}", receiver.getTarget());
			helper.assertTrue(receiver.getTarget() == player, "the receiver's target is " + receiver.getTarget());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobThatDeclinedIsGivenNoSlot(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper);
		Mob caller = zombie(helper, 20, 16);
		Mob hurt = zombie(helper, 24, 42);
		// A stance would let the hurt mob answer.
		// NORMAL keeps the 35 percent survival line.
		StanceFixtures.dropStance(helper, hurt);
		LearnArena.whenReady(helper, java.util.List.of(player, caller, hurt), () -> {
			// Set once the mob has loaded: the mobs mod tops a mob's health up when it joins the world.
			hurt.setHealth(1.0F);
			CalloutBoard.send(caller, player, rally(caller, player, 300), LearnConfig.defaults());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobThatDeclinedIsGivenNoSlot RESULT slot={}", Rally.slotOf(hurt));
			helper.assertTrue(Rally.slotOf(hurt) == null, "a mob that declined was given a slot");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anExpiredRallyEndsTheWalk(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = facingSouth(helper);
		Mob caller = zombie(helper, 20, 16);
		Mob receiver = zombie(helper, 24, 42);
		// The player is the receiver's target from the start, so once the walk ends nothing else pulls it on.
		LearnArena.keepTargeting(helper, receiver, player);
		LearnArena.whenReady(helper, java.util.List.of(player, caller, receiver), () -> {
			CalloutBoard.send(caller, player, rally(caller, player, 5), LearnConfig.defaults());
			Vec3 slot = Rally.slotOf(receiver);
			helper.assertTrue(slot != null, "the receiver was given no slot");
			helper.runAfterDelay(60L, () -> {
				boolean walking = receiver.getGoalSelector().getAvailableGoals().stream()
					.anyMatch(wrapped -> wrapped.getGoal() instanceof AnswerRallyGoal && wrapped.isRunning());
				StellaLearn.LOGGER.info("[BEHAVIOR] test=anExpiredRallyEndsTheWalk RESULT walking={}", walking);
				helper.assertTrue(!walking, "the walk went on after the request expired");
				helper.assertTrue(CalloutBoard.active(receiver) == null, "the request outlived its 5 ticks");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void anArcherTakesASlotInsideItsBand(final GameTestHelper helper) {
		LearnArena.floor(helper);
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(ARCHER_CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		ServerPlayer player = facingSouth(helper);
		Mob caller = zombie(helper, 20, 16);
		Mob archer = helper.spawnMob(EntityTypes.SKELETON, 24, LearnArena.SPAWN_Y, 42).spawn();
		LearnArena.whenReady(helper, java.util.List.of(player, caller, archer), () -> {
			CalloutBoard.send(caller, player, rally(caller, player, 300), LearnConfig.defaults());
			Vec3 slot = Rally.slotOf(archer);
			helper.assertTrue(slot != null, "the archer was given no slot");
			ReactiveState state = ReactiveState.get(archer);
			double distance = new Vec3(slot.x - player.getX(), 0.0, slot.z - player.getZ()).length();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=anArcherTakesASlotInsideItsBand RESULT distance={} near={} far={}", distance, state.getNearDistance(), state.getFarDistance());
			helper.assertTrue(distance > state.getNearDistance() && distance < state.getFarDistance(), "the archer's slot is " + distance + " out");
			helper.succeed();
		});
	}
}
