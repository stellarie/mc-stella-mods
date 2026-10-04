package com.stella.ruins.site.crystalgrotto;

import com.mojang.serialization.DataResult;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;

/** Items 4 to 8, and the sketch review additions: the chime order, the strikes, the replay and the spokes. */
public class ChimeObjectiveGameTest {
	static final int TICKS = 6000;
	static final long SEED = 0x61A770L;

	private static boolean orderIsValid(final int[] order, final int pillars) {
		for (int i = 0; i < order.length; i++) {
			if (order[i] < 0 || order[i] >= pillars || i > 0 && order[i] == order[i - 1]) {
				return false;
			}
		}
		return true;
	}

	private static int wrongPillar(final GrottoTestSupport.Arena arena, final int right) {
		return (right + 1) % arena.markers(GrottoMarkers.PILLAR).size();
	}

	@GameTest(maxTicks = TICKS)
	public void activationPlaysAnOrderOfFourAndStoresIt(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the grotto did not wake: " + arena.phase());
			ChimeObjective objective = arena.objective();
			int pillars = arena.markers(GrottoMarkers.PILLAR).size();
			GrottoTestSupport.result("firstOrder", "round=" + objective.round() + " order=" + Arrays.toString(objective.order()) + " pillars=" + pillars + " saved=" + arena.entry().objective());
			helper.assertTrue(objective.round() == 0 && objective.order().length == 4, "round " + objective.round() + " order " + Arrays.toString(objective.order()));
			helper.assertTrue(orderIsValid(objective.order(), pillars), "the order is not valid over " + pillars + " pillars: " + Arrays.toString(objective.order()));
			helper.assertTrue(arena.entry().objective().getIntArray("order").isPresent() && arena.entry().objective().getIntArray("order").get().length == 4, "the order is not in the saved state");
			helper.assertTrue(objective.playing() && objective.progress() == 0, "the chimes did not start");
			helper.assertTrue(arena.rosterOf("prism_slime") == 3, "the roster holds " + arena.rosterOf("prism_slime") + " slimes");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void strikingTheStoredOrderThroughThreeRoundsCompletesTheCore(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			int[] lengths = new int[3];
			for (int round = 0; round < 3; round++) {
				arena.listen();
				ChimeObjective before = arena.objective();
				helper.assertTrue(!before.playing() && before.round() == round, "round " + round + " state: round " + before.round() + " playing " + before.playing());
				lengths[round] = arena.solveRound(player).length;
				helper.assertTrue(arena.vault().sealed() || round == 2, "the vault opened early in round " + round);
			}
			helper.assertTrue(arena.objective().done(), "the objective is not done after three rounds");
			arena.core().pulse(arena.level());
			GrottoTestSupport.result("threeRounds", "lengths=" + Arrays.toString(lengths) + " phase=" + arena.phase() + " sealed=" + arena.vault().sealed());
			helper.assertTrue(Arrays.equals(lengths, new int[] {4, 5, 6}), "round lengths " + Arrays.toString(lengths));
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the core is " + arena.phase());
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aStrikeOnAnythingButAPillarCountsForNothing(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			arena.listen();
			int right = arena.objective().order()[0];
			BlockPos plinth = arena.markers(GrottoMarkers.PILLAR).get(right).below();
			InteractionResult onPlinth = AttackBlockCallback.EVENT.invoker().interact(player, arena.level(), InteractionHand.MAIN_HAND, plinth, Direction.UP);
			InteractionResult onCore = AttackBlockCallback.EVENT.invoker().interact(player, arena.level(), InteractionHand.MAIN_HAND, arena.corePos(), Direction.UP);
			ChimeObjective after = arena.objective();
			GrottoTestSupport.result("nonPillarStrike", "plinth=" + onPlinth + " core=" + onCore + " progress=" + after.progress() + " roster=" + arena.entry().roster().size());
			helper.assertTrue(onPlinth == InteractionResult.PASS && onCore == InteractionResult.PASS, "the event took a block that is no pillar");
			helper.assertTrue(after.progress() == 0 && !after.playing() && after.round() == 0, "a strike on a plain block moved the objective");
			helper.assertTrue(arena.rosterOf("shard_sentinel") == 0, "a strike on a plain block summoned sentinels");
			InteractionResult onPillar = arena.strike(player, right);
			helper.assertTrue(onPillar == InteractionResult.FAIL && arena.objective().progress() == 1, "the right pillar was not counted: " + onPillar + " progress " + arena.objective().progress());
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aWrongStrikeSummonsTwoSentinelsAndStartsANewOrderOfTheSameLength(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			arena.listen();
			int[] old = arena.objective().order();
			int roster = arena.entry().roster().size();
			arena.strike(player, wrongPillar(arena, old[0]));
			ChimeObjective after = arena.objective();
			GrottoTestSupport.result("wrongStrike", "old=" + Arrays.toString(old) + " new=" + Arrays.toString(after.order()) + " sentinels=" + arena.rosterOf("shard_sentinel")
				+ " roster=" + roster + "->" + arena.entry().roster().size());
			helper.assertTrue(arena.rosterOf("shard_sentinel") == 2, "the wrong strike summoned " + arena.rosterOf("shard_sentinel") + " sentinels");
			helper.assertTrue(arena.entry().roster().size() == roster + 2, "the roster grew by " + (arena.entry().roster().size() - roster));
			helper.assertTrue(after.order().length == old.length && !Arrays.equals(after.order(), old), "the new order is " + Arrays.toString(after.order()) + " after " + Arrays.toString(old));
			helper.assertTrue(after.progress() == 0 && after.playing() && after.round() == 0, "progress " + after.progress() + " playing " + after.playing());
			arena.leave(player);
		});
	}

	@GameTest
	public void aNewOrderDiffersFromTheOldOneOverFiftySeededTrials(final GameTestHelper helper) {
		int identical = 0;
		for (int trial = 0; trial < 50; trial++) {
			RandomSource random = RandomSource.create(900L + trial);
			int pillars = 4 + trial % 3;
			int length = ChimeObjective.lengthOf(trial % 3);
			int[] old = ChimeObjective.draw(pillars, length, random);
			int[] next = ChimeObjective.redraw(old, pillars, random);
			if (Arrays.equals(old, next)) {
				identical++;
			}
			helper.assertTrue(next.length == old.length, "trial " + trial + " changed the length");
			helper.assertTrue(orderIsValid(next, pillars), "trial " + trial + " order " + Arrays.toString(next));
		}
		GrottoTestSupport.result("redraw", "trials=50 identical=" + identical);
		helper.assertTrue(identical == 0, identical + " of 50 new orders equalled the old one");
		helper.succeed();
	}

	@GameTest
	public void theFallbackOrderAlwaysDiffersFromTheOldOne(final GameTestHelper helper) {
		for (int trial = 0; trial < 200; trial++) {
			RandomSource random = RandomSource.create(4000L + trial);
			int pillars = 4 + trial % 3;
			int[] old = ChimeObjective.draw(pillars, 4 + trial % 3, random);
			int[] forced = ChimeObjective.forceDifferent(old, pillars, random);
			helper.assertTrue(!Arrays.equals(forced, old) && orderIsValid(forced, pillars), "trial " + trial + ": " + Arrays.toString(forced) + " after " + Arrays.toString(old));
		}
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void strikesWhileTheCrystalsChimeAreIgnored(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			ChimeObjective during = arena.objective();
			helper.assertTrue(during.playing(), "the chimes are not playing right after the wake");
			int right = during.order()[0];
			arena.strike(player, right);
			arena.strike(player, wrongPillar(arena, right));
			ChimeObjective after = arena.objective();
			GrottoTestSupport.result("strikeDuringChime", "progress=" + after.progress() + " sentinels=" + arena.rosterOf("shard_sentinel") + " orderSame=" + Arrays.equals(during.order(), after.order()));
			helper.assertTrue(after.progress() == 0 && Arrays.equals(during.order(), after.order()), "a strike during the chimes changed the objective");
			helper.assertTrue(arena.rosterOf("shard_sentinel") == 0, "a strike during the chimes summoned sentinels");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theObjectiveSurvivesSaveAndLoadMidRound(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			arena.listen();
			arena.solveRound(player);
			arena.listen();
			int[] order = arena.objective().order();
			arena.strike(player, order[0]);
			arena.strike(player, order[1]);
			ChimeObjective before = arena.objective();
			helper.assertTrue(before.round() == 1 && before.progress() == 2 && order.length == 5, "round " + before.round() + " progress " + before.progress());
			DataResult<net.minecraft.nbt.Tag> encoded = RuinData.CODEC.encodeStart(NbtOps.INSTANCE, RuinData.get(arena.level()));
			RuinData reloaded = RuinData.CODEC.parse(NbtOps.INSTANCE, encoded.getOrThrow()).getOrThrow();
			RuinData.Entry entry = reloaded.entry(arena.corePos());
			ChimeObjective loaded = (ChimeObjective) arena.site().newObjective();
			loaded.load(entry.objective());
			GrottoTestSupport.result("saveLoad", "round=" + loaded.round() + " progress=" + loaded.progress() + " order=" + Arrays.toString(loaded.order()) + " phase=" + entry.phase());
			helper.assertTrue(entry.phase() == RuinPhase.ACTIVE, "the phase is " + entry.phase());
			helper.assertTrue(loaded.round() == 1 && loaded.progress() == 2 && Arrays.equals(loaded.order(), order) && !loaded.playing() && !loaded.done(), "the loaded state differs");
			for (int i = 2; i < order.length; i++) {
				arena.strike(player, order[i]);
			}
			helper.assertTrue(arena.objective().round() == 2 && arena.objective().order().length == 6, "the round did not advance after the reload point");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aCompletedGrottoRearmsAfterThreeDaysWithNewOrders(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {1_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				Set<String> firstOrders = new HashSet<>();
				for (int cycle = 0; cycle < 6; cycle++) {
					ServerPlayer player = arena.wake();
					arena.freeze();
					firstOrders.add(Arrays.toString(arena.objective().order()));
					helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.objective().round() == 0 && arena.objective().order().length == 4, "cycle " + cycle + " woke wrong: " + arena.phase());
					for (int round = 0; round < 3; round++) {
						arena.listen();
						arena.solveRound(player);
					}
					arena.core().pulse(arena.level());
					helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "cycle " + cycle + " did not complete: " + arena.phase());
					now[0] += 3L * 24_000L - 1L;
					arena.core().pulse(arena.level());
					helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the grotto rearmed one tick early");
					now[0] += 1L;
					arena.leave(player);
					arena.core().pulse(arena.level());
					helper.assertTrue(arena.phase() == RuinPhase.DORMANT && arena.vault().sealed(), "cycle " + cycle + " after three days: " + arena.phase() + " sealed " + arena.vault().sealed());
				}
				GrottoTestSupport.result("rearm", "cycles=6 distinctFirstOrders=" + firstOrders.size());
				helper.assertTrue(firstOrders.size() >= 2, "six rearmed grottoes drew the same first order: " + firstOrders);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aReplayCostsOnePrismSlimeAndOnlyOnThePlayersTurn(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			InteractionResult early = arena.useCore(player, ItemStack.EMPTY);
			helper.assertTrue(!early.consumesAction() || arena.objective().replays() == 0, "a replay started while the chimes played");
			helper.assertTrue(arena.objective().replays() == 0 && arena.rosterOf("prism_slime") == 3, "replays " + arena.objective().replays() + " slimes " + arena.rosterOf("prism_slime"));
			arena.listen();
			int[] order = arena.objective().order();
			arena.strike(player, order[0]);
			InteractionResult first = arena.useCore(player, ItemStack.EMPTY);
			ChimeObjective afterFirst = arena.objective();
			helper.assertTrue(first.consumesAction() && afterFirst.playing() && afterFirst.progress() == 0 && afterFirst.replays() == 1, "first replay: " + first + " playing " + afterFirst.playing() + " progress " + afterFirst.progress());
			helper.assertTrue(arena.rosterOf("prism_slime") == 4 && Arrays.equals(afterFirst.order(), order), "slimes " + arena.rosterOf("prism_slime") + " or the order changed");
			arena.listen();
			arena.useCore(player, new ItemStack(Items.STICK));
			helper.assertTrue(arena.objective().replays() == 2 && arena.rosterOf("prism_slime") == 5, "second replay: replays " + arena.objective().replays() + " slimes " + arena.rosterOf("prism_slime"));
			arena.listen();
			arena.solveRound(player);
			arena.listen();
			helper.assertTrue(arena.objective().round() == 1 && arena.objective().replays() == 0, "a new round did not clear the replay count: " + arena.objective().replays());
			GrottoTestSupport.result("replay", "slimesAfterTwoReplays=5 roundReset=true");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theFloorSpokesLightForThePillarsStruckSoFar(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			arena.listen();
			int[] order = arena.objective().order();
			for (int i = 0; i < arena.markers(GrottoMarkers.PILLAR).size(); i++) {
				for (BlockPos cell : arena.markers(GrottoMarkers.spoke(i))) {
					helper.assertTrue(arena.level().getBlockState(cell) == GrottoPlan.SPOKE_UNLIT, "spoke " + i + " starts lit at " + cell);
				}
			}
			arena.strike(player, order[0]);
			arena.strike(player, order[1]);
			int lit = 0;
			for (int i = 0; i < arena.markers(GrottoMarkers.PILLAR).size(); i++) {
				boolean struck = i == order[0] || i == order[1];
				for (BlockPos cell : arena.markers(GrottoMarkers.spoke(i))) {
					boolean on = arena.level().getBlockState(cell) == GrottoPlan.SPOKE_LIT;
					lit += on ? 1 : 0;
					helper.assertTrue(on == struck, "spoke " + i + " lit=" + on + " struck=" + struck);
				}
			}
			helper.assertTrue(lit > 0, "no spoke lit");
			arena.strike(player, wrongPillar(arena, order[2]));
			for (int i = 0; i < arena.markers(GrottoMarkers.PILLAR).size(); i++) {
				for (BlockPos cell : arena.markers(GrottoMarkers.spoke(i))) {
					helper.assertTrue(arena.level().getBlockState(cell) == GrottoPlan.SPOKE_UNLIT, "spoke " + i + " stayed lit after a wrong strike");
				}
			}
			GrottoTestSupport.result("spokes", "litAfterTwoStrikes=" + lit);
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void everyRotationPlaysTheSameRounds(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED + 3, Rotation.COUNTERCLOCKWISE_90, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			for (int round = 0; round < 3; round++) {
				arena.listen();
				arena.solveRound(player);
			}
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "a turned grotto ended " + arena.phase());
			GrottoTestSupport.result("rotatedRounds", "turns=" + arena.frame().turns() + " phase=" + arena.phase());
			arena.leave(player);
		});
	}

	@GameTest
	public void theChimeStateReadsBackExactly(final GameTestHelper helper) {
		ChimeObjective objective = new ChimeObjective();
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putInt("round", 2);
		tag.putIntArray("order", new int[] {1, 3, 0, 2, 1, 0});
		tag.putInt("progress", 4);
		tag.putInt("play", -1);
		tag.putInt("replays", 3);
		tag.putBoolean("done", false);
		objective.load(tag);
		helper.assertTrue(objective.save().equals(tag), "save after load differs: " + objective.save() + " vs " + tag);
		helper.assertTrue(List.of(objective.round(), objective.progress(), objective.replays()).equals(List.of(2, 4, 3)), "fields differ");
		helper.succeed();
	}
}
