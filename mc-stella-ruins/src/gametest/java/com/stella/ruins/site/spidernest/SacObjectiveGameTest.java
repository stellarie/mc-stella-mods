package com.stella.ruins.site.spidernest;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinPhase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Items 3 to 6 and the sketch review additions: the clock, the finish, the hatch, the regrowth, and the signals. */
public class SacObjectiveGameTest {
	static final int TICKS = 6000;
	static final long SEED = 0x5AC0B1L;
	static final long START = 5_000_000L;

	private static Map<Item, Integer> contents(final com.stella.ruins.world.RuinVaultBlockEntity vault) {
		Map<Item, Integer> totals = new HashMap<>();
		for (int slot = 0; slot < vault.getContainerSize(); slot++) {
			ItemStack stack = vault.getItem(slot);
			if (!stack.isEmpty()) {
				totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return totals;
	}

	@GameTest(maxTicks = TICKS)
	public void breakingTheFirstSacStartsTheClockAndTheNextOnesDoNotMoveIt(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			RuinClock.Source saved = RuinClock.replace(level -> START);
			try {
				ServerPlayer player = arena.wake();
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the nest did not wake: " + arena.phase());
				SacObjective armed = arena.objective();
				int sacs = arena.sacs().size();
				helper.assertTrue(armed.stage() == SacObjective.Stage.ARMED && armed.total() == sacs && armed.deadline() == 0L, "armed state: " + armed.stage() + " total " + armed.total());
				helper.assertTrue(arena.standing() == sacs, "only " + arena.standing() + " of " + sacs + " sacs stand");
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.objective().stage() == SacObjective.Stage.ARMED, "the clock started with no sac broken");
				arena.removeSac(0);
				SacObjective timing = arena.objective();
				NestTestSupport.result("clock", "sacs=" + sacs + " stage=" + timing.stage() + " deadline=" + timing.deadline() + " start=" + START);
				helper.assertTrue(timing.stage() == SacObjective.Stage.TIMING, "the first break left the stage at " + timing.stage());
				helper.assertTrue(timing.deadline() == START + SacObjective.durationFor(sacs), "deadline " + timing.deadline() + " is not start plus the clock for " + sacs + " sacs");
				helper.assertTrue(arena.standing() == sacs - 1, "the sac did not leave the world");
				RuinClock.replace(level -> START + 300);
				arena.removeSac(1);
				helper.assertTrue(arena.objective().deadline() == timing.deadline(), "a later break moved the deadline");
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theClockGrowsByTheSacCountAboveSix(final GameTestHelper helper) {
		helper.assertTrue(SacObjective.durationFor(6) == 1800 && SacObjective.durationFor(7) == 1860 && SacObjective.durationFor(10) == 2040, "the clock formula: "
			+ SacObjective.durationFor(6) + " " + SacObjective.durationFor(7) + " " + SacObjective.durationFor(10));
		helper.assertTrue(SacObjective.BASE_TICKS == 1800 && SacObjective.TICKS_PER_EXTRA_SAC == 60 && SacObjective.BASE_SACS == 6, "the clock constants moved");
		long seed = NestTestSupport.seedWithSacs(100L, 10);
		NestTestSupport.inArena(helper, seed, arena -> {
			RuinClock.Source saved = RuinClock.replace(level -> START);
			try {
				ServerPlayer player = arena.wake();
				helper.assertTrue(arena.sacs().size() == 10, "the arena holds " + arena.sacs().size() + " sacs");
				arena.removeSac(0);
				helper.assertTrue(arena.objective().deadline() == START + 2040L, "ten sacs gave a clock of " + (arena.objective().deadline() - START));
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aNestOfSixSacsKeepsTheBaseClock(final GameTestHelper helper) {
		long seed = NestTestSupport.seedWithSacs(100L, 6);
		NestTestSupport.inArena(helper, seed, arena -> {
			RuinClock.Source saved = RuinClock.replace(level -> START);
			try {
				ServerPlayer player = arena.wake();
				arena.removeSac(2);
				helper.assertTrue(arena.objective().deadline() == START + 1800L, "six sacs gave a clock of " + (arena.objective().deadline() - START));
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void breakingEverySacBeforeTheClockEndsCompletesTheCoreAndUnlocksTheVault(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			RuinClock.Source saved = RuinClock.replace(level -> START);
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				int sacs = arena.sacs().size();
				helper.assertTrue(arena.vault().sealed(), "the vault stood open before the nest was won");
				for (int i = 0; i < sacs - 1; i++) {
					arena.removeSac(i);
				}
				helper.assertTrue(arena.objective().stage() == SacObjective.Stage.TIMING && arena.phase() == RuinPhase.ACTIVE, "the nest finished one sac early");
				arena.removeSac(sacs - 1);
				helper.assertTrue(arena.objective().stage() == SacObjective.Stage.DONE, "the last break left the stage at " + arena.objective().stage());
				arena.core().pulse(arena.level());
				Map<Item, Integer> loot = contents(arena.vault());
				NestTestSupport.result("finish", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed() + " loot=" + loot + " weaversAlive=" + arena.rosterOf(NestMobs.NEST_WEAVER));
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "phase " + arena.phase() + " sealed " + arena.vault().sealed());
				helper.assertTrue(loot.getOrDefault(NestItems.SILKWEAVE_ITEM, 0) >= 4 && loot.getOrDefault(Items.STRING, 0) >= 4 && loot.getOrDefault(Items.EMERALD, 0) >= 2, "the clean vault holds " + loot);
				helper.assertTrue(arena.rosterOf(NestMobs.NEST_MATRIARCH) == 0 && arena.rosterOf(NestMobs.BROOD_CRAWLER) == 0, "a clean win hatched the Matriarch");
				helper.assertTrue(!NestVaults.queued(arena.level(), arena.corePos()), "a clean win queued a hatched swap");
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void whenTheClockEndsTheMatriarchAndSixCrawlersHatchAndHerDeathCompletesTheCoreWithTheSmallerTable(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {START};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				int sacs = arena.sacs().size();
				arena.removeSac(0);
				arena.removeSac(1);
				long deadline = arena.objective().deadline();
				now[0] = deadline - 1;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.objective().stage() == SacObjective.Stage.TIMING && arena.standing() == sacs - 2, "the nest hatched one tick early");
				now[0] = deadline;
				arena.core().pulse(arena.level());
				arena.freeze();
				SacObjective hatched = arena.objective();
				NestTestSupport.result("hatch", "stage=" + hatched.stage() + " standing=" + arena.standing() + " matriarchs=" + arena.rosterOf(NestMobs.NEST_MATRIARCH)
					+ " crawlers=" + arena.rosterOf(NestMobs.BROOD_CRAWLER) + " weavers=" + arena.rosterOf(NestMobs.NEST_WEAVER));
				helper.assertTrue(hatched.stage() == SacObjective.Stage.HATCHED && hatched.hatched(), "stage " + hatched.stage());
				helper.assertTrue(arena.standing() == 0, "the sacs did not burst: " + arena.standing());
				helper.assertTrue(arena.rosterOf(NestMobs.NEST_MATRIARCH) == 1, "matriarchs " + arena.rosterOf(NestMobs.NEST_MATRIARCH));
				helper.assertTrue(arena.rosterOf(NestMobs.BROOD_CRAWLER) == 6, "crawlers " + arena.rosterOf(NestMobs.BROOD_CRAWLER));
				helper.assertTrue(arena.rosterOf(NestMobs.NEST_WEAVER) == 3, "the Weavers of the activation are gone: " + arena.rosterOf(NestMobs.NEST_WEAVER));
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the burst finished the nest: " + arena.phase());
				Mob mother = arena.firstOf(NestMobs.NEST_MATRIARCH);
				BlockPos pit = arena.markers(NestMarkers.MATRIARCH).get(0);
				helper.assertTrue(mother.blockPosition().distManhattan(pit) <= 1, "the Matriarch did not rise in the pit: " + mother.blockPosition() + " vs " + pit);
				List<BlockPos> spots = arena.markers(NestMarkers.CRAWLER);
				int[] used = new int[spots.size()];
				for (java.util.UUID id : arena.entry().roster()) {
					if (arena.level().getEntity(id) instanceof Mob mob && com.stella.ruins.world.RuinMobs.kindOf(mob) == NestMobs.BROOD_CRAWLER) {
						for (int i = 0; i < spots.size(); i++) {
							if (mob.blockPosition().distManhattan(spots.get(i)) <= 1) {
								used[i]++;
							}
						}
					}
				}
				for (int count : used) {
					helper.assertTrue(count == 1, "the crawlers did not take one marker each: " + java.util.Arrays.toString(used));
				}
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.objective().stage() == SacObjective.Stage.HATCHED, "the nest finished with the Matriarch alive");
				mother.hurtServer(arena.level(), arena.level().damageSources().genericKill(), 1.0E9F);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "her death did not complete the core: " + arena.phase());
				helper.assertTrue(NestVaults.queued(arena.level(), arena.corePos()), "no hatched swap is queued");
				NestVaults.settle(arena.level().getServer());
				Map<Item, Integer> loot = contents(arena.vault());
				NestTestSupport.result("hatchedVault", "loot=" + loot + " sealed=" + arena.vault().sealed());
				helper.assertTrue(!arena.vault().sealed() && !NestVaults.queued(arena.level(), arena.corePos()), "the vault is sealed or the swap is still queued");
				helper.assertTrue(loot.size() == 1 && loot.getOrDefault(NestItems.SILKWEAVE_ITEM, 0) >= 2, "the hatched vault holds " + loot);
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theSacsWebsAndCocoonsGrowBackThreeDaysAfterTheWinAndMinedRockStaysMined(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {START};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				int sacs = arena.sacs().size();
				for (int i = 0; i < sacs; i++) {
					arena.removeSac(i);
				}
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the nest did not complete: " + arena.phase());
				helper.assertTrue(NestRegrowth.data(arena.level()).cores().contains(arena.corePos()), "the won nest is not remembered for regrowth");
				BlockPos web = find(arena, NestPlan.WEB, true);
				BlockPos cocoon = find(arena, NestPlan.COCOON, false);
				BlockPos rock = findRockWall(arena);
				arena.level().setBlock(web, Blocks.AIR.defaultBlockState(), 3);
				arena.level().setBlock(cocoon, Blocks.AIR.defaultBlockState(), 3);
				arena.level().setBlock(rock, Blocks.AIR.defaultBlockState(), 3);
				now[0] = START + 3L * Claim.DAY_TICKS - 1L;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !NestRegrowth.pollCore(arena.level(), arena.corePos()), "the nest grew back one tick early");
				helper.assertTrue(arena.standing() == 0, "a sac grew back before the delay");
				now[0] = START + 3L * Claim.DAY_TICKS;
				arena.leave(player);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT && arena.vault().sealed(), "after three days: " + arena.phase());
				boolean grown = NestRegrowth.pollCore(arena.level(), arena.corePos());
				NestTestSupport.result("regrow", "grown=" + grown + " standing=" + arena.standing() + "/" + sacs);
				helper.assertTrue(grown, "the poll did not grow the nest back");
				helper.assertTrue(arena.standing() == sacs, "only " + arena.standing() + " of " + sacs + " sacs grew back");
				for (BlockPos pos : arena.sacs()) {
					helper.assertTrue(arena.level().getBlockState(pos).is(EggSacBlock.BLOCK), "no sac at its marker " + pos);
				}
				helper.assertTrue(arena.level().getBlockState(web).is(Blocks.COBWEB), "the web did not grow back: " + arena.level().getBlockState(web));
				helper.assertTrue(arena.level().getBlockState(cocoon).is(Blocks.WOOL.white()), "the cocoon did not grow back: " + arena.level().getBlockState(cocoon));
				helper.assertTrue(arena.level().getBlockState(rock).isAir(), "mined rock grew back: " + arena.level().getBlockState(rock));
				helper.assertTrue(!NestRegrowth.data(arena.level()).cores().contains(arena.corePos()), "the nest is still on the regrowth list");
				ServerPlayer again = arena.wake();
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.objective().stage() == SacObjective.Stage.ARMED && arena.objective().total() == sacs, "the regrown nest did not arm again");
				arena.leave(again);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theLiveTickEventGrowsTheRearmedNestBackWithoutAnyPoll(final GameTestHelper helper) {
		NestTestSupport.Arena arena = NestTestSupport.arena(helper.getLevel(), net.minecraft.world.level.block.Rotation.NONE, SEED);
		boolean[] set = {false};
		BlockPos[] web = {null};
		long begun = System.nanoTime();
		helper.succeedWhen(() -> NestTestSupport.guarded(helper, () -> {
			if (!set[0]) {
				NestTestSupport.awaitReady(helper, arena, begun);
				set[0] = true;
				ServerPlayer player = arena.wake();
				arena.freeze();
				for (int i = 0; i < arena.sacs().size(); i++) {
					arena.removeSac(i);
				}
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the nest did not complete: " + arena.phase());
				web[0] = find(arena, NestPlan.WEB, true);
				arena.level().setBlock(web[0], Blocks.AIR.defaultBlockState(), 3);
				arena.entry().setClaimTime(RuinClock.now(arena.level()) - 3L * Claim.DAY_TICKS);
				arena.leave(player);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the nest did not rearm: " + arena.phase());
				helper.assertTrue(false, "waiting for the tick event to grow the nest back");
			}
			boolean back = arena.standing() == arena.sacs().size() && arena.level().getBlockState(web[0]).is(Blocks.COBWEB);
			if (back) {
				NestTestSupport.result("liveRegrow", "sacs=" + arena.standing() + " web=back");
				arena.release();
			}
			helper.assertTrue(back, "the tick event has not grown the nest back: " + arena.standing() + " of " + arena.sacs().size() + " sacs");
		}));
	}

	private static BlockPos find(final NestTestSupport.Arena arena, final byte id, final boolean skipPlug) {
		NestPlan plan = arena.plan();
		for (int y = 0; y < NestPlan.SIZE_Y; y++) {
			for (int z = 0; z < NestPlan.NEST_Z; z++) {
				for (int x = 0; x < NestPlan.SIZE_X; x++) {
					if (plan.cell(x, y, z) == id && !(skipPlug && plan.inPlug(x, z))) {
						return arena.frame().toWorld(x, y, z + NestPlan.EXTENSION);
					}
				}
			}
		}
		throw new IllegalStateException("the plan holds no cell " + id);
	}

	/** A rock cell of the wall that touches the cave. */
	private static BlockPos findRockWall(final NestTestSupport.Arena arena) {
		NestPlan plan = arena.plan();
		for (int y = 2; y < NestPlan.MAX_AIR_Y; y++) {
			for (int z = 3; z < NestPlan.NEST_Z - 3; z++) {
				for (int x = 3; x < NestPlan.SIZE_X - 3; x++) {
					if (plan.cell(x, y, z) == NestPlan.ROCK && plan.cell(x + 1, y, z) == NestPlan.AIR && plan.cell(x - 1, y, z) == NestPlan.ROCK) {
						return arena.frame().toWorld(x, y, z + NestPlan.EXTENSION);
					}
				}
			}
		}
		throw new IllegalStateException("the plan holds no wall cell");
	}

	@GameTest(maxTicks = TICKS)
	public void aSacBreaksInOneHitDropsNothingAndStartsTheClockThroughARealBreak(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			RuinClock.Source saved = RuinClock.replace(level -> START);
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				BlockPos sac = arena.sacs().get(0);
				BlockState state = arena.level().getBlockState(sac);
				helper.assertTrue(state.is(EggSacBlock.BLOCK) && state.getDestroyProgress(player, arena.level(), sac) >= 1.0F, "the sac does not break in one hit: " + state.getDestroyProgress(player, arena.level(), sac));
				helper.assertTrue(state.getDestroySpeed(arena.level(), sac) == 0.0F, "the sac is not instant to break");
				boolean broken = player.gameMode.destroyBlock(sac);
				AABB area = new AABB(sac).inflate(6.0);
				List<ItemEntity> drops = arena.level().getEntitiesOfClass(ItemEntity.class, area);
				NestTestSupport.result("oneHit", "broken=" + broken + " drops=" + drops.size() + " stage=" + arena.objective().stage());
				helper.assertTrue(broken && arena.level().getBlockState(sac).isAir(), "the break did not remove the sac");
				helper.assertTrue(drops.isEmpty(), "the sac dropped " + drops);
				helper.assertTrue(arena.objective().stage() == SacObjective.Stage.TIMING, "a player's break did not start the clock: " + arena.objective().stage());
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aSacLostWhileTheNestSleepsCountsForNothing(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			arena.removeSac(0);
			helper.assertTrue(arena.entry() == null || arena.phase() == RuinPhase.DORMANT, "a sleeping nest changed its phase");
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the nest did not wake");
			helper.assertTrue(arena.sacs().size() >= 6, "markers lost a sac");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theClockShowsTheSacsAndSecondsLeftToPlayersInsideTheBoundsOnly(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer inside = arena.join(arena.standAt());
			ServerPlayer outside = arena.join(arena.standAt().add(300.0, 0.0, 0.0));
			int saw = SacObjective.announce(arena.level(), arena.corePos(), 4, 1234L);
			int expected = 0;
			for (ServerPlayer player : arena.level().players()) {
				if (arena.frame().box().isInside(player.blockPosition())) {
					expected++;
				}
			}
			NestTestSupport.result("actionBar", "saw=" + saw + " inside=" + expected + " players=" + arena.level().players().size());
			helper.assertTrue(saw == expected && saw >= 2, "the clock reached " + saw + " players, " + expected + " stand inside");
			helper.assertTrue(!arena.frame().box().isInside(outside.blockPosition()), "the outside player stands inside the bounds");
			arena.leave(inside);
			arena.leave(outside);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aPulseWhileTheClockRunsAnnouncesAndStartsTheHeartbeat(final GameTestHelper helper) {
		NestTestSupport.inArena(helper, SEED, arena -> {
			RuinClock.Source saved = RuinClock.replace(level -> START);
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				arena.removeSac(0);
				helper.assertTrue(!NestHeartbeat.isTracking(arena.level(), arena.corePos()), "the heartbeat beat before the clock started");
				arena.core().pulse(arena.level());
				NestTestSupport.result("pulseSignals", "tracking=" + NestHeartbeat.isTracking(arena.level(), arena.corePos()));
				helper.assertTrue(NestHeartbeat.isTracking(arena.level(), arena.corePos()), "the pulse did not start the heartbeat");
				arena.removeSac(1);
				for (int i = 2; i < arena.sacs().size(); i++) {
					arena.removeSac(i);
				}
				arena.core().pulse(arena.level());
				helper.assertTrue(!NestHeartbeat.isTracking(arena.level(), arena.corePos()), "the heartbeat kept beating after the last sac");
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theHeartbeatSpeedsUpInTheLastTwentySeconds(final GameTestHelper helper) {
		helper.assertTrue(NestHeartbeat.interval(2000) == 40 && NestHeartbeat.interval(401) == 40 && NestHeartbeat.interval(400) == 40, "the slow beat is not 40 ticks");
		helper.assertTrue(NestHeartbeat.interval(0) == 8 && NestHeartbeat.interval(-5) == 8, "the fast beat is not 8 ticks");
		int last = Integer.MAX_VALUE;
		for (long remaining = 2000; remaining >= 0; remaining -= 7) {
			int gap = NestHeartbeat.interval(remaining);
			helper.assertTrue(gap <= last && gap >= 8 && gap <= 40, "the beat gap " + gap + " at " + remaining + " ticks left breaks the curve");
			last = gap;
		}
		helper.assertTrue(NestHeartbeat.interval(200) < NestHeartbeat.interval(399) && NestHeartbeat.interval(40) < NestHeartbeat.interval(200), "the beat does not speed up toward the end");
		NestTestSupport.inArena(helper, SEED, arena -> {
			RuinClock.Source saved = RuinClock.replace(level -> START);
			try {
				List<BlockPos> sacs = arena.sacs();
				long base = NestHeartbeat.tick();
				NestHeartbeat.track(arena.level(), arena.corePos(), sacs, START + 2000L);
				long slowTick = (base / 40 + 1) * 40;
				helper.assertTrue(NestHeartbeat.beatCore(arena.level(), arena.corePos(), slowTick + 1) == 0, "a beat fell on an off tick");
				int slow = NestHeartbeat.beatCore(arena.level(), arena.corePos(), slowTick);
				helper.assertTrue(slow == sacs.size(), "the slow beat played " + slow + " of " + sacs.size() + " sacs");
				NestHeartbeat.track(arena.level(), arena.corePos(), sacs, START + 100L);
				long fastTick = base + 1;
				while (fastTick % 16 != 0 || fastTick % 40 == 0) {
					fastTick++;
				}
				long quietTick = base + 1;
				while (quietTick % 40 != 0 || quietTick % 16 == 0) {
					quietTick++;
				}
				int fast = NestHeartbeat.beatCore(arena.level(), arena.corePos(), fastTick);
				int quiet = NestHeartbeat.beatCore(arena.level(), arena.corePos(), quietTick);
				NestTestSupport.result("heartbeat", "slow=" + slow + " fast=" + fast + " quiet=" + quiet + " gapAt100=" + NestHeartbeat.interval(100));
				helper.assertTrue(fast == sacs.size(), "the fast beat played " + fast + " sacs at tick " + fastTick);
				helper.assertTrue(quiet == 0, "the slow tick " + quietTick + " beat in the fast stretch");
				helper.assertTrue(NestHeartbeat.beatCore(arena.level(), arena.corePos(), base + NestHeartbeat.STALE_TICKS + 40) == 0 && !NestHeartbeat.isTracking(arena.level(), arena.corePos()), "a stale beat stayed");
			} finally {
				RuinClock.replace(saved);
				NestHeartbeat.clear(arena.level(), arena.corePos());
			}
		});
	}

	@GameTest
	public void theObjectiveSavesAndLoadsItsStage(final GameTestHelper helper) {
		SacObjective saved = new SacObjective();
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString("stage", "HATCHED");
		tag.putLong("deadline", 777L);
		tag.putInt("total", 9);
		tag.putBoolean("hatched", true);
		saved.load(tag);
		helper.assertTrue(saved.stage() == SacObjective.Stage.HATCHED && saved.deadline() == 777L && saved.total() == 9 && saved.hatched(), "the loaded state differs");
		SacObjective again = new SacObjective();
		again.load(saved.save());
		helper.assertTrue(again.stage() == SacObjective.Stage.HATCHED && again.deadline() == 777L && again.total() == 9 && again.hatched(), "the round trip differs");
		SacObjective blank = new SacObjective();
		blank.load(new net.minecraft.nbt.CompoundTag());
		helper.assertTrue(blank.stage() == SacObjective.Stage.ARMED && blank.deadline() == 0L, "an empty tag did not load as armed");
		helper.succeed();
	}
}
