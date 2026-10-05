package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The cooling objective: the wake, the timer rules, the ore, the slimes, the cooling, and the payout. */
public class CoolingObjectiveGameTest {
	static final long SEED = 0xC0FFEEL;
	static final int TICKS = 6000;

	private static Vec3 centre(final BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}

	private static int golems(final CraterTestSupport.Arena arena) {
		return arena.rosterOf("star_golem");
	}

	private static int slimes(final CraterTestSupport.Arena arena) {
		return CoolingObjective.aliveSlimes(arena.level(), arena.entry());
	}

	private static int hotOre(final CraterTestSupport.Arena arena) {
		int count = 0;
		for (BlockPos pos : arena.markers(CraterMarkers.ORE)) {
			count += arena.level().getBlockState(pos).is(MeteorBlocks.STARMETAL_ORE) ? 1 : 0;
		}
		return count;
	}

	private static int cooledOre(final CraterTestSupport.Arena arena) {
		int count = 0;
		for (BlockPos pos : arena.markers(CraterMarkers.ORE)) {
			count += arena.level().getBlockState(pos).is(MeteorBlocks.COOLED_METEORITE) ? 1 : 0;
		}
		return count;
	}

	private static int rawDropped(final CraterTestSupport.Arena arena, final BlockPos near) {
		int total = 0;
		for (ItemEntity item : arena.level().getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(4.0))) {
			ItemStack stack = item.getItem();
			total += stack.is(CraterItems.RAW_STARMETAL) ? stack.getCount() : 0;
			item.discard();
		}
		return total;
	}

	/** A player who stands where the bowl rule does not reach, but inside the activation radius. */
	private static ServerPlayer onTheRim(final CraterTestSupport.Arena arena) {
		Vec3 core = centre(arena.corePos());
		ServerPlayer player = arena.join(core.add(0.0, 0.0, -15.0));
		return player;
	}

	@GameTest(maxTicks = TICKS)
	public void activationWakesThreeStarGolemsAndFourEmberSlimesAtTheirOwnMarkers(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			arena.freeze();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "phase " + arena.phase());
			List<UUID> roster = arena.entry().roster();
			helper.assertTrue(roster.size() == 7, "the roster holds " + roster.size());
			helper.assertTrue(golems(arena) == 3 && arena.rosterOf("ember_slime") == 4, "golems " + golems(arena) + " slimes " + arena.rosterOf("ember_slime"));
			List<BlockPos> golemSpots = arena.markers(CraterMarkers.GOLEM);
			List<BlockPos> slimeSpots = arena.markers(CraterMarkers.SLIME);
			int[] slots = new int[3];
			for (UUID id : roster) {
				Mob mob = (Mob) arena.level().getEntity(id);
				helper.assertTrue(mob.entityTags().contains(RuinMobKind.TAG_MOB) && mob.isPersistenceRequired(), "a roster mob is not tagged and persistent");
				boolean golem = mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + "star_golem");
				List<BlockPos> spots = golem ? golemSpots : slimeSpots;
				boolean atSpot = spots.stream().anyMatch(spot -> Math.abs(mob.getX() - (spot.getX() + 0.5)) < 1.01 && Math.abs(mob.getZ() - (spot.getZ() + 0.5)) < 1.01);
				helper.assertTrue(atSpot, "a " + (golem ? "golem" : "slime") + " woke away from its markers at " + mob.position());
				if (golem) {
					slots[CraterMobs.slotOf(mob)]++;
				}
			}
			helper.assertTrue(slots[0] == 1 && slots[1] == 1 && slots[2] == 1, "golem slots " + java.util.Arrays.toString(slots));
			CraterTestSupport.result("wake", "roster=" + roster.size() + " golems=" + golems(arena) + " slimes=" + arena.rosterOf("ember_slime"));
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aFightOnTheRimDoesNotStartTheTimerNoMatterHowLong(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {1_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				onTheRim(arena);
				arena.core().pulse(arena.level());
				arena.freeze();
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the rim player woke nothing: " + arena.phase());
				for (int i = 0; i < 6; i++) {
					now[0] += 1000L;
					arena.core().pulse(arena.level());
				}
				helper.assertTrue(!arena.objective().started(), "the timer started from the rim, beside a spectator in the bowl");
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && hotOre(arena) == arena.markers(CraterMarkers.ORE).size(), "the crater cooled without a timer");
				CraterTestSupport.result("rimFight", "pulses=6 clockAdvance=6000 started=false phase=" + arena.phase());
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theTimerStartsWithinTwelveBlocksOfTheCoreInThreeDimensions(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {2_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				Vec3 core = centre(arena.corePos());
				ServerPlayer player = arena.join(core.add(0.0, 0.0, -12.5));
				arena.core().pulse(arena.level());
				arena.freeze();
				arena.core().pulse(arena.level());
				helper.assertTrue(!arena.objective().started(), "the timer started at 12.5 blocks");
				player.snapTo(core.x, core.y + 8.0, core.z - 9.0);
				arena.core().pulse(arena.level());
				helper.assertTrue(!arena.objective().started(), "the timer started at 12.04 blocks in 3D");
				player.snapTo(core.x, core.y + 8.0, core.z - 8.5);
				now[0] += 77L;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.objective().started() && arena.objective().startTick() == now[0], "the timer did not start at 11.7 blocks: " + arena.objective().started());
				CraterTestSupport.result("bowlRule", "started=" + arena.objective().started() + " startTick=" + arena.objective().startTick());
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void miningTheFirstOreStartsTheTimerWakesASlimeAndDropsOneOrTwoRawStarmetal(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {3_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				ServerPlayer player = onTheRim(arena);
				arena.core().pulse(arena.level());
				arena.freeze();
				helper.assertTrue(!arena.objective().started() && slimes(arena) == 4, "before the mining: started " + arena.objective().started() + " slimes " + slimes(arena));
				BlockPos ore = arena.markers(CraterMarkers.ORE).get(0);
				helper.assertTrue(arena.level().getBlockState(ore).is(MeteorBlocks.STARMETAL_ORE), "the first ore marker is not hot ore");
				now[0] += 5L;
				boolean broke = player.gameMode.destroyBlock(ore);
				helper.assertTrue(broke && arena.level().getBlockState(ore).isAir(), "the ore did not break");
				helper.assertTrue(arena.objective().started() && arena.objective().startTick() == now[0], "mining the first ore did not start the timer");
				int raw = rawDropped(arena, ore);
				helper.assertTrue(raw >= 1 && raw <= 2, "the ore dropped " + raw + " raw starmetal");
				helper.assertTrue(slimes(arena) == 5, "slimes after one ore: " + slimes(arena));
				CraterTestSupport.result("firstOre", "started=true raw=" + raw + " slimes=" + slimes(arena));
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void eachMinedOreWakesOneSlimeUpToSixAlive(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = onTheRim(arena);
			arena.core().pulse(arena.level());
			arena.freeze();
			List<BlockPos> ores = arena.markers(CraterMarkers.ORE);
			int mined = 0;
			int raw = 0;
			List<Integer> alive = new ArrayList<>();
			for (BlockPos ore : ores) {
				helper.assertTrue(player.gameMode.destroyBlock(ore), "ore " + ore + " did not break");
				mined++;
				raw += rawDropped(arena, ore);
				alive.add(slimes(arena));
			}
			CraterTestSupport.result("slimeCap", "mined=" + mined + " raw=" + raw + " alive=" + alive);
			helper.assertTrue(alive.get(0) == 5 && alive.get(1) == 6, "alive after the first two ores: " + alive);
			for (int count : alive) {
				helper.assertTrue(count <= CoolingObjective.SLIME_CAP, "more than six slimes alive: " + alive);
			}
			helper.assertTrue(alive.get(alive.size() - 1) == 6, "the cap was not reached: " + alive);
			helper.assertTrue(raw >= mined && raw <= 2 * mined, "raw starmetal " + raw + " from " + mined + " ore blocks");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aKilledSlimeMakesRoomForTheNextOneAndOreNeverWakesSlimesBeforeTheWake(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			ServerPlayer player = onTheRim(arena);
			// Before any pulse the crater is dormant: mining wakes nothing.
			BlockPos first = arena.markers(CraterMarkers.ORE).get(0);
			player.gameMode.destroyBlock(first);
			helper.assertTrue(arena.entry() == null || arena.entry().roster().isEmpty(), "mining a dormant crater woke mobs");
			arena.core().pulse(arena.level());
			arena.freeze();
			BlockPos second = arena.markers(CraterMarkers.ORE).get(1);
			player.gameMode.destroyBlock(second);
			player.gameMode.destroyBlock(arena.markers(CraterMarkers.ORE).get(2));
			helper.assertTrue(slimes(arena) == 6, "slimes " + slimes(arena));
			for (UUID id : arena.entry().roster()) {
				Entity mob = arena.level().getEntity(id);
				if (mob != null && mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + "ember_slime")) {
					mob.discard();
					break;
				}
			}
			player.gameMode.destroyBlock(arena.markers(CraterMarkers.ORE).get(3));
			helper.assertTrue(slimes(arena) == 6, "after one died and one more ore: " + slimes(arena));
		});
	}

	@GameTest(maxTicks = TICKS)
	public void atTick2400TheRemainingOreCoolsTheSlimesDieAndTheVaultOpens(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {4_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				int total = arena.markers(CraterMarkers.ORE).size();
				player.gameMode.destroyBlock(arena.markers(CraterMarkers.ORE).get(0));
				player.gameMode.destroyBlock(arena.markers(CraterMarkers.ORE).get(1));
				long started = arena.objective().startTick();
				helper.assertTrue(arena.objective().started() && started == now[0], "the timer is not running");
				helper.assertTrue(arena.vault().sealed(), "the vault is open before the cooling");
				now[0] = started + CoolingObjective.COOL_TICKS - 1;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && hotOre(arena) == total - 2 && cooledOre(arena) == 0 && arena.vault().sealed(),
					"at tick 2399: phase " + arena.phase() + " hot " + hotOre(arena) + " cooled " + cooledOre(arena));
				now[0] = started + CoolingObjective.COOL_TICKS;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "at tick 2400 the core is " + arena.phase());
				helper.assertTrue(hotOre(arena) == 0 && cooledOre(arena) == total - 2, "hot " + hotOre(arena) + " cooled " + cooledOre(arena) + " of " + total);
				helper.assertTrue(arena.rosterOf("ember_slime") == 0 && golems(arena) == 3, "slimes " + arena.rosterOf("ember_slime") + " golems " + golems(arena));
				helper.assertTrue(!arena.vault().sealed(), "the vault is still sealed");
				int stacks = 0;
				for (int i = 0; i < arena.vault().getContainerSize(); i++) {
					stacks += arena.vault().getItem(i).isEmpty() ? 0 : 1;
				}
				helper.assertTrue(stacks >= 4, "the vault holds " + stacks + " stacks");
				CraterTestSupport.result("cooling", "hot=" + hotOre(arena) + " cooled=" + cooledOre(arena) + " slimes=" + arena.rosterOf("ember_slime") + " golems=" + golems(arena) + " stacks=" + stacks);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theCoreStaysCompleteAfterThirtyDays(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {5_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				arena.wake();
				arena.freeze();
				arena.core().pulse(arena.level());
				now[0] += CoolingObjective.COOL_TICKS;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase());
				int cooled = cooledOre(arena);
				now[0] += 30L * 24_000L;
				for (int i = 0; i < 3; i++) {
					arena.core().pulse(arena.level());
				}
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed() && cooledOre(arena) == cooled && hotOre(arena) == 0,
					"after 30 days: " + arena.phase() + " sealed " + arena.vault().sealed());
				CraterTestSupport.result("thirtyDays", "phase=" + arena.phase() + " cooled=" + cooledOre(arena));
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aHotCraterSendsSmokeToPlayersWithin48BlocksAndStopsWhenItCools(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			long[] now = {6_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				ServerLevel level = arena.level();
				arena.leave(arena.players().get(0));
				Vec3 core = centre(arena.corePos());
				ServerPlayer far = arena.join(core.add(0.0, 0.0, -10.0));
				far.snapTo(core.x + 70.0, core.y, core.z);
				StringBuilder who = new StringBuilder();
				for (ServerPlayer p : level.players()) {
					who.append(p.getName().getString()).append('@').append(p.position()).append(' ');
				}
				int sentFar = CraterSmoke.emitFrom(level, arena.core());
				helper.assertTrue(CraterSmoke.tracked(arena.corePos()), "the world never told the beacon about the core");
				helper.assertTrue(sentFar == 0, "smoke went to a player 70 blocks away: sent " + sentFar + " core " + arena.corePos() + " players " + who);
				far.snapTo(core.x + 47.0, core.y, core.z);
				int hot = CraterSmoke.emitFrom(level, arena.core());
				helper.assertTrue(hot >= 1, "no smoke reached a player 47 blocks away from a dormant crater");
				arena.wake();
				arena.freeze();
				arena.core().pulse(level);
				helper.assertTrue(arena.objective().started(), "the timer did not start with a player in the bowl");
				helper.assertTrue(CraterSmoke.emitFrom(level, arena.core()) >= 1, "no smoke from an active crater");
				now[0] += CoolingObjective.COOL_TICKS;
				arena.core().pulse(level);
				arena.core().pulse(level);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the crater did not cool: " + arena.phase());
				int cooled = CraterSmoke.emitFrom(level, arena.core());
				helper.assertTrue(cooled == 0, "smoke after the meteor cooled: " + cooled);
				CraterTestSupport.result("smoke", "hotSends=" + hot + " afterCooling=" + cooled);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}
}
