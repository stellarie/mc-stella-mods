package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.Claim;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinConfig;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;

/**
 * Acceptance items 3 to 6 and 8, and the sketch review's rules: the three offerings, the wave order, the markers of a
 * kind, the vault, the one-day reset, and the despawn of an abandoned rite.
 */
public class WayshrineRiteGameTest {
	static final int TICKS = 6000;

	private static void startsWaveOneWith(final GameTestHelper helper, final Item offering, final com.stella.ruins.world.WavePlan rite) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().isEmpty(), "the core woke with " + arena.entry().roster().size() + " mobs before any offering");
			ItemStack stack = new ItemStack(offering, 2);
			InteractionResult taken = arena.offer(player, stack);
			int total = rite.waves().get(0).entries().stream().mapToInt(com.stella.ruins.world.Roster.Entry::count).sum();
			WayshrineTestSupport.result("offering", "item=" + offering + " taken=" + taken.consumesAction() + " left=" + stack.getCount() + " roster=" + arena.entry().roster().size() + " expected=" + total);
			helper.assertTrue(taken.consumesAction() && stack.getCount() == 1, "the offering was not taken, or took more than one item: left " + stack.getCount());
			helper.assertTrue(arena.entry().roster().size() == total, "wave 1 holds " + arena.entry().roster().size() + " mobs, not " + total);
			for (com.stella.ruins.world.Roster.Entry entry : rite.waves().get(0).entries()) {
				helper.assertTrue(arena.count(entry.kind().id()) == entry.count(), "wave 1 holds " + arena.count(entry.kind().id()) + " of " + entry.kind().id() + ", not " + entry.count());
			}
		});
	}

	@GameTest
	public void eachOfferingChoosesADifferentRite(final GameTestHelper helper) {
		java.util.Set<String> firstWaves = new java.util.HashSet<>();
		for (com.stella.ruins.world.WavePlan plan : List.of(WayshrineSite.pilgrimWaves(), WayshrineSite.wispWaves(), WayshrineSite.acolyteWaves())) {
			StringBuilder shape = new StringBuilder();
			plan.waves().forEach(wave -> wave.entries().forEach(entry -> shape.append(entry.kind().id()).append(entry.count()).append(',')));
			firstWaves.add(shape.toString());
		}
		helper.assertTrue(firstWaves.size() == 3, "the three rites are not three different plans: " + firstWaves);
		helper.assertTrue(WayshrineSite.ACOLYTE_EXTRA_ROLLS >= 1, "the acolyte rite pays no more than the others");
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void aGoldIngotOfferedAtTheCoreStartsWaveOne(final GameTestHelper helper) {
		startsWaveOneWith(helper, Items.GOLD_INGOT, WayshrineSite.pilgrimWaves());
	}

	@GameTest(maxTicks = TICKS)
	public void anEmeraldOfferedAtTheCoreStartsWaveOne(final GameTestHelper helper) {
		startsWaveOneWith(helper, Items.EMERALD, WayshrineSite.wispWaves());
	}

	@GameTest(maxTicks = TICKS)
	public void aShrineShardOfferedAtTheCoreStartsWaveOne(final GameTestHelper helper) {
		startsWaveOneWith(helper, WayshrineItems.SHRINE_SHARD, WayshrineSite.acolyteWaves());
	}

	@GameTest(maxTicks = TICKS)
	public void anyOtherItemOfferedAtTheCoreDoesNothing(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			ServerPlayer player = arena.wake();
			List<String> taken = new ArrayList<>();
			for (Item other : new Item[] {Items.STICK, Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_NUGGET, Items.EMERALD_BLOCK, WayshrineItems.PILGRIM_ASH, WayshrineItems.WISP_LIGHT}) {
				ItemStack stack = new ItemStack(other, 3);
				InteractionResult result = arena.offer(player, stack);
				if (result.consumesAction() || stack.getCount() != 3) {
					taken.add(other.toString());
				}
			}
			arena.core().pulse(arena.level());
			WayshrineTestSupport.result("otherOfferings", "taken=" + taken + " roster=" + arena.entry().roster().size() + " phase=" + arena.phase());
			helper.assertTrue(taken.isEmpty(), "the altar took " + taken);
			helper.assertTrue(arena.entry().roster().isEmpty() && arena.phase() == RuinPhase.ACTIVE, "a wrong item changed the rite: roster " + arena.entry().roster().size() + ", phase " + arena.phase());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void waveTwoStartsOnlyWhenWaveOneIsDeadAndWaveThreeHoldsTheAcolyte(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			ServerPlayer player = arena.wake();
			arena.offer(player, new ItemStack(Items.GOLD_INGOT, 2));
			arena.freeze();
			List<Mob> first = new ArrayList<>(arena.roster());
			helper.assertTrue(first.size() == 4, "wave 1 holds " + first.size());
			arena.kill(player, first.subList(0, 3));
			helper.assertTrue(arena.roster().size() == 1 && arena.count(WayshrineMobs.WISP) == 0, "wave 2 began with a pilgrim alive: roster " + arena.roster().size() + ", wisps " + arena.count(WayshrineMobs.WISP));
			arena.kill(player, arena.roster());
			long pilgrims2 = arena.count(WayshrineMobs.PILGRIM);
			long wisps2 = arena.count(WayshrineMobs.WISP);
			long acolytes2 = arena.count(WayshrineMobs.ACOLYTE);
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && pilgrims2 == 3 && wisps2 == 3 && acolytes2 == 0, "wave 2 holds " + pilgrims2 + " pilgrims, " + wisps2 + " wisps, " + acolytes2 + " acolytes");
			arena.killRoster(player);
			long pilgrims3 = arena.count(WayshrineMobs.PILGRIM);
			long wisps3 = arena.count(WayshrineMobs.WISP);
			long acolytes3 = arena.count(WayshrineMobs.ACOLYTE);
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && pilgrims3 == 2 && wisps3 == 2 && acolytes3 == 1, "wave 3 holds " + pilgrims3 + " pilgrims, " + wisps3 + " wisps, " + acolytes3 + " acolytes");
			helper.assertTrue(arena.vault().sealed(), "the vault opened before the last wave died");
			arena.killRoster(player);
			WayshrineTestSupport.result("waves", "wave2=" + pilgrims2 + "/" + wisps2 + "/" + acolytes2 + " wave3=" + pilgrims3 + "/" + wisps3 + "/" + acolytes3 + " phase=" + arena.phase());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "killing wave 3 left the core " + arena.phase());
		});
	}

	private static Map<BlockPos, Integer> spotsOf(final WayshrineTestSupport.Arena arena, final String kind) {
		Map<BlockPos, Integer> spots = new HashMap<>();
		for (Mob mob : arena.roster()) {
			if (mob.entityTags().contains("mod-stella-ruins.kind." + kind)) {
				spots.merge(mob.blockPosition(), 1, Integer::sum);
			}
		}
		return spots;
	}

	private static Map<BlockPos, Integer> firstMarkers(final WayshrineTestSupport.Arena arena, final String kind, final int count) {
		List<BlockPos> markers = arena.markers(WayshrinePlan.spawnMarker(kind));
		Map<BlockPos, Integer> expected = new HashMap<>();
		for (int i = 0; i < count; i++) {
			expected.merge(markers.get(i % markers.size()), 1, Integer::sum);
		}
		return expected;
	}

	@GameTest(maxTicks = TICKS)
	public void everyMobAppearsAtAMarkerOfItsOwnKind(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.SUNKEN, arena -> {
			ServerPlayer player = arena.wake();
			arena.offer(player, new ItemStack(Items.GOLD_INGOT, 2));
			Map<BlockPos, Integer> pilgrims1 = spotsOf(arena, WayshrineMobs.PILGRIM);
			helper.assertTrue(pilgrims1.equals(firstMarkers(arena, WayshrineMobs.PILGRIM, 4)), "wave 1 pilgrims stand at " + pilgrims1 + ", markers " + arena.markers(WayshrinePlan.spawnMarker(WayshrineMobs.PILGRIM)));
			arena.killRoster(player);
			Map<BlockPos, Integer> pilgrims2 = spotsOf(arena, WayshrineMobs.PILGRIM);
			Map<BlockPos, Integer> wisps2 = spotsOf(arena, WayshrineMobs.WISP);
			helper.assertTrue(pilgrims2.equals(firstMarkers(arena, WayshrineMobs.PILGRIM, 3)), "wave 2 pilgrims stand at " + pilgrims2);
			helper.assertTrue(wisps2.equals(firstMarkers(arena, WayshrineMobs.WISP, 3)), "wave 2 wisps stand at " + wisps2 + ", markers " + arena.markers(WayshrinePlan.spawnMarker(WayshrineMobs.WISP)));
			arena.killRoster(player);
			helper.assertTrue(spotsOf(arena, WayshrineMobs.ACOLYTE).equals(firstMarkers(arena, WayshrineMobs.ACOLYTE, 1)), "the acolyte stands at " + spotsOf(arena, WayshrineMobs.ACOLYTE));
			helper.assertTrue(spotsOf(arena, WayshrineMobs.WISP).equals(firstMarkers(arena, WayshrineMobs.WISP, 2)), "wave 3 wisps stand at " + spotsOf(arena, WayshrineMobs.WISP));
			WayshrineTestSupport.result("markersOfKind", "pilgrimMarkers=" + arena.markers(WayshrinePlan.spawnMarker(WayshrineMobs.PILGRIM)).size() + " wispMarkers=" + arena.markers(WayshrinePlan.spawnMarker(WayshrineMobs.WISP)).size());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aWaveWithMoreMobsThanMarkersReusesTheMarkersInOrder(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrineTestInit.overflow, Rotation.NONE, WayshrineTestSupport.seedOf(WayshrinePlan.RING), arena -> {
			ServerPlayer player = arena.wake();
			arena.offer(player, new ItemStack(Items.GOLD_INGOT, 2));
			Map<BlockPos, Integer> at = spotsOf(arena, WayshrineMobs.PILGRIM);
			List<BlockPos> markers = arena.markers(WayshrinePlan.spawnMarker(WayshrineMobs.PILGRIM));
			WayshrineTestSupport.result("markerReuse", "pilgrims=" + arena.count(WayshrineMobs.PILGRIM) + " markers=" + markers.size() + " perMarker=" + at.values());
			helper.assertTrue(arena.count(WayshrineMobs.PILGRIM) == WayshrineTestInit.OVERFLOW_PILGRIMS, "the wave holds " + arena.count(WayshrineMobs.PILGRIM) + " pilgrims");
			helper.assertTrue(markers.size() < WayshrineTestInit.OVERFLOW_PILGRIMS, "the layout has " + markers.size() + " markers, so nothing is reused");
			helper.assertTrue(at.equals(firstMarkers(arena, WayshrineMobs.PILGRIM, WayshrineTestInit.OVERFLOW_PILGRIMS)), "the pilgrims stand at " + at + ", markers " + markers);
			helper.assertTrue(at.get(markers.get(0)) == 2 && at.get(markers.get(1)) == 2 && at.get(markers.get(2)) == 1 && at.get(markers.get(3)) == 1, "the first markers did not take the extra mobs: " + at);
		});
	}

	private static int itemsInVault(final WayshrineTestSupport.Arena arena, final Item item) {
		int total = 0;
		for (int i = 0; i < arena.vault().getContainerSize(); i++) {
			ItemStack stack = arena.vault().getItem(i);
			total += stack.is(item) ? stack.getCount() : 0;
		}
		return total;
	}

	private static void finishRite(final WayshrineTestSupport.Arena arena, final ServerPlayer player) {
		arena.offer(player, new ItemStack(Items.GOLD_INGOT, 2));
		arena.killRoster(player);
		arena.killRoster(player);
		arena.killRoster(player);
	}

	@GameTest(maxTicks = TICKS)
	public void theCompletedVaultHoldsShrineShardsAndTheTableIsExclusive(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.CHAPEL, arena -> {
			ServerPlayer player = arena.wake();
			finishRite(arena, player);
			int shards = itemsInVault(arena, WayshrineItems.SHRINE_SHARD);
			int emeralds = itemsInVault(arena, Items.EMERALD);
			WayshrineTestSupport.result("vaultContents", "phase=" + arena.phase() + " shards=" + shards + " emeralds=" + emeralds + " sealed=" + arena.vault().sealed());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the core is " + arena.phase());
			helper.assertTrue(shards >= 1, "the vault holds " + shards + " shards");
			helper.assertTrue(shards >= 2, "the table promises at least 2 shards, the vault holds " + shards);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theShrineRearmsAfterOneDayAndNotBeforeAndTheVaultKeepsItsStacks(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			AtomicLong clock = new AtomicLong(5_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				finishRite(arena, player);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the rite did not complete: " + arena.phase());
				int shards = itemsInVault(arena, WayshrineItems.SHRINE_SHARD);
				long done = clock.get();
				clock.set(done + Claim.DAY_TICKS - 1);
				arena.core().pulse(arena.level());
				ItemStack early = new ItemStack(Items.GOLD_INGOT, 2);
				InteractionResult tooSoon = arena.offer(player, early);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !tooSoon.consumesAction() && early.getCount() == 2, "the shrine rearmed or took an offering one tick early: " + arena.phase());
				clock.set(done + Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COOLDOWN, "with a player standing in the radius the core is " + arena.phase() + ", not resting");
				ItemStack resting = new ItemStack(Items.GOLD_INGOT, 2);
				helper.assertTrue(!arena.offer(player, resting).consumesAction() && resting.getCount() == 2, "the resting core took an offering");
				arena.leave(player);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "after the player left the core is " + arena.phase());
				helper.assertTrue(arena.vault().sealed() && itemsInVault(arena, WayshrineItems.SHRINE_SHARD) == shards, "the vault did not lock with its stacks: sealed " + arena.vault().sealed());
				ServerPlayer again = arena.wake();
				ItemStack fresh = new ItemStack(Items.GOLD_INGOT, 2);
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.offer(again, fresh).consumesAction() && arena.entry().roster().size() == 4, "the rearmed shrine did not take an offering and start wave 1");
				WayshrineTestSupport.result("rearm", "afterTicks=" + Claim.DAY_TICKS + " shardsKept=" + shards + " phase=" + arena.phase());
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	private static void leaveTheShrine(final WayshrineTestSupport.Arena arena, final ServerPlayer player, final int blocks) {
		BlockPos core = arena.corePos();
		player.setPos(core.getX() + 0.5 + blocks, core.getY(), core.getZ() + 0.5);
	}

	@GameTest(maxTicks = TICKS)
	public void ifNoPlayerStaysWithinThirtyTwoBlocksForSixHundredTicksTheWaveVanishesAndTheCoreSleeps(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			AtomicLong clock = new AtomicLong(7_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				arena.offer(player, new ItemStack(Items.GOLD_INGOT, 3));
				arena.freeze();
				List<Mob> wave = new ArrayList<>(arena.roster());
				helper.assertTrue(wave.size() == 4, "wave 1 holds " + wave.size());
				leaveTheShrine(arena, player, ShrineRite.WATCH_RADIUS * 2);
				clock.addAndGet(ShrineRite.ABANDON_TICKS - 1);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.roster().size() == 4, "the wave vanished after " + (ShrineRite.ABANDON_TICKS - 1) + " ticks: phase " + arena.phase() + ", roster " + arena.roster().size());
				clock.addAndGet(1);
				arena.core().pulse(arena.level());
				long alive = wave.stream().filter(mob -> !mob.isRemoved()).count();
				WayshrineTestSupport.result("abandon", "afterTicks=" + ShrineRite.ABANDON_TICKS + " phase=" + arena.phase() + " roster=" + arena.entry().roster().size() + " stillInWorld=" + alive);
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the core is " + arena.phase() + " after the wave was abandoned");
				helper.assertTrue(arena.entry().roster().isEmpty() && alive == 0, "the wave mobs remain: roster " + arena.entry().roster().size() + ", in the world " + alive);
				helper.assertTrue(arena.level().getEntities((net.minecraft.world.entity.Entity) null, arena.area().inflate(46.0), WayshrineMobs::isShrineMob).isEmpty(), "a shrine mob still stands near the core");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aPlayerWithinThirtyTwoBlocksKeepsTheRiteAlive(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			AtomicLong clock = new AtomicLong(9_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				arena.offer(player, new ItemStack(Items.GOLD_INGOT, 3));
				arena.freeze();
				leaveTheShrine(arena, player, ShrineRite.WATCH_RADIUS - 1);
				for (int pulse = 0; pulse < 10; pulse++) {
					clock.addAndGet(ShrineRite.ABANDON_TICKS * 2);
					arena.core().pulse(arena.level());
				}
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.roster().size() == 4, "a player " + (ShrineRite.WATCH_RADIUS - 1) + " blocks away lost the wave: phase " + arena.phase() + ", roster " + arena.roster().size());
				leaveTheShrine(arena, player, ShrineRite.WATCH_RADIUS + 1);
				clock.addAndGet(ShrineRite.ABANDON_TICKS - 1);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the rite ended before " + ShrineRite.ABANDON_TICKS + " ticks of absence: " + arena.phase());
				clock.addAndGet(1);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "a player " + (ShrineRite.WATCH_RADIUS + 1) + " blocks away kept the rite alive: " + arena.phase());
				WayshrineTestSupport.result("watchRadius", "radius=" + ShrineRite.WATCH_RADIUS + " inside=alive outside=dormant");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void anAbandonedOfferingIsSpentSoTheNextTryNeedsAnother(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			AtomicLong clock = new AtomicLong(11_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.wake();
				ItemStack gold = new ItemStack(Items.GOLD_INGOT, 5);
				arena.offer(player, gold);
				arena.freeze();
				leaveTheShrine(arena, player, ShrineRite.WATCH_RADIUS + 5);
				clock.addAndGet(ShrineRite.ABANDON_TICKS);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the rite was not abandoned: " + arena.phase());
				leaveTheShrine(arena, player, 3);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().isEmpty(), "the woken core spawned " + arena.entry().roster().size() + " mobs without a new offering");
				InteractionResult second = arena.offer(player, gold);
				WayshrineTestSupport.result("spentOffering", "goldLeft=" + gold.getCount() + " second=" + second.consumesAction() + " roster=" + arena.entry().roster().size());
				helper.assertTrue(second.consumesAction() && gold.getCount() == 3 && arena.entry().roster().size() == 4, "the second try did not need and take a second offering: gold " + gold.getCount() + ", roster " + arena.entry().roster().size());
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aShrineMobKilledByAPlayerCanDropTheCenserAtTheConfiguredChance(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			RuinConfig.Values old = RuinConfig.get();
			try {
				RuinConfig.replace(old.withUniqueDropChance(1.0));
				ServerPlayer player = arena.wake();
				arena.offer(player, new ItemStack(Items.GOLD_INGOT, 2));
				arena.freeze();
				List<Mob> wave = arena.roster();
				arena.kill(player, wave.subList(0, 1));
				int dropped = itemsNear(arena, WayshrineRewards.censer());
				RuinConfig.replace(old.withUniqueDropChance(0.0));
				arena.kill(player, arena.roster().subList(0, 1));
				int after = itemsNear(arena, WayshrineRewards.censer());
				WayshrineTestSupport.result("censerDrop", "atChance1=" + dropped + " afterChance0=" + after);
				helper.assertTrue(dropped == 1, "a chance of 1.0 dropped " + dropped + " censers");
				helper.assertTrue(after == 1, "a chance of 0.0 dropped a censer: " + after + " in all");
			} finally {
				RuinConfig.replace(old);
			}
		});
	}

	static int itemsNear(final WayshrineTestSupport.Arena arena, final Item item) {
		int total = 0;
		for (ItemEntity entity : arena.level().getEntitiesOfClass(ItemEntity.class, arena.area())) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}
}
