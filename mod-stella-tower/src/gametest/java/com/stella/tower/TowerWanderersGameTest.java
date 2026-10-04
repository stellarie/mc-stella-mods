package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** Tower mobs of the highest floor reached, loose in the overworld, the nether and the end. */
public class TowerWanderersGameTest {
	private static final int OPEN_FLOOR = 40;
	private static final int EARLY_FLOOR = 10;

	/** An area no earlier run has rolled: the test world keeps its chunks, so a reused one proves nothing. Rows from 3000 on, clear of the areas earlier runs took. */
	private static ChunkPos freshAnchor() {
		int n = TowerTestSupport.freshArea();
		return new ChunkPos(TowerWanderers.REGION_CHUNKS * (n % 400_000), TowerWanderers.REGION_CHUNKS * (3_000 + (n / 400_000) % 1_000));
	}

	/**
	 * Loads an area with its entities, then runs the check once. The check returns null when it holds, else
	 * what went wrong; a wait that returned early would pass, so the verdict is kept and asserted every tick.
	 */
	private static void whenLoaded(final GameTestHelper helper, final ServerLevel level, final ChunkPos pos, final Supplier<@Nullable String> check) {
		level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, pos, 1);
		boolean[] ran = {false};
		String[] verdict = {null};
		helper.succeedWhen(() -> {
			if (!ran[0]) {
				helper.assertTrue(level.areEntitiesLoaded(pos.pack()), "the area at " + pos + " never loaded");
				ran[0] = true;
				verdict[0] = check.get();
			}
			helper.assertTrue(verdict[0] == null, String.valueOf(verdict[0]));
		});
	}

	private static ServerLevel world(final GameTestHelper helper, final net.minecraft.resources.ResourceKey<Level> key) {
		ServerLevel level = helper.getLevel().getServer().getLevel(key);
		helper.assertTrue(level != null, key.identifier() + " is not loaded");
		return level;
	}

	@GameTest
	public void anAreaRollsOneTimeInAHundred(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int areas = 100_000;
		int rolled = 0;
		for (int i = 0; i < areas; i++) {
			ChunkPos pos = new ChunkPos(TowerWanderers.REGION_CHUNKS * (i % 400), TowerWanderers.REGION_CHUNKS * (i / 400));
			rolled += TowerWanderers.rolls(TowerWanderers.regionRandom(level, pos), TowerWanderers.CHANCE) ? 1 : 0;
		}
		double expected = areas * TowerWanderers.CHANCE;
		double spread = 5.0 * Math.sqrt(areas * TowerWanderers.CHANCE * (1.0 - TowerWanderers.CHANCE));
		TowerTestSupport.result("wanderRate", "areas=" + areas + " rolled=" + rolled + " rate=" + (double) rolled / areas);
		helper.assertTrue(Math.abs(rolled - expected) <= spread, rolled + " of " + areas + " areas rolled, not about " + expected);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aLockedTowerSendsNothing(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos pos = freshAnchor();
		LevelChunk chunk = level.getChunk(pos.x(), pos.z());
		whenLoaded(helper, level, pos, () -> {
			Mob mob = TowerWanderers.rollRegion(level, chunk, 0, 1.0);
			TowerTestSupport.result("lockedTower", "mob=" + (mob != null) + " rolled=" + TowerWanderers.isRolled(chunk));
			if (mob != null) {
				mob.discard();
				return "a locked tower sent a " + mob.getType().getDescriptionId();
			}
			return TowerWanderers.isRolled(chunk) ? "a locked tower spent the area's one roll" : null;
		});
	}

	@GameTest(maxTicks = 200)
	public void anOpenTowerSendsAMobOfItsHighestFloor(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos pos = freshAnchor();
		LevelChunk chunk = level.getChunk(pos.x(), pos.z());
		whenLoaded(helper, level, pos, () -> {
			Mob mob = TowerWanderers.rollRegion(level, chunk, OPEN_FLOOR, 1.0);
			TowerMobs.State state = mob == null ? null : TowerMobs.stateOf(mob);
			TowerTestSupport.result("openTower", "mob=" + (mob == null ? "none" : mob.getType().getDescriptionId())
				+ " floor=" + (state == null ? -1 : state.floor()) + " rank=" + (state == null ? "none" : state.rank()));
			if (mob != null) {
				mob.discard();
			}
			if (state == null) {
				return "an open tower sent no tower mob";
			}
			return state.floor() == OPEN_FLOOR && state.rank() == TowerRank.MOB ? null
				: "the wanderer is a " + state.rank() + " of floor " + state.floor() + ", not a mob of " + OPEN_FLOOR;
		});
	}

	/** Its health, as all else, comes from the floor: the attribute and the divisor hold the floor's whole health. */
	@GameTest(maxTicks = 200)
	public void aWandererHasItsFloorsHealth(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos pos = freshAnchor();
		LevelChunk chunk = level.getChunk(pos.x(), pos.z());
		whenLoaded(helper, level, pos, () -> {
			Mob mob = TowerWanderers.rollRegion(level, chunk, OPEN_FLOOR, 1.0);
			if (mob == null || TowerMobs.stateOf(mob) == null) {
				return "an open tower sent no tower mob";
			}
			double held = mob.getMaxHealth() * TowerMobs.stateOf(mob).healthDivisor();
			double want = TowerTestSupport.floorHealth(mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue(), OPEN_FLOOR, TowerRank.MOB);
			boolean inWorld = level.getEntity(mob.getUUID()) == mob;
			TowerTestSupport.result("wandererHealth", "held=" + held + " want=" + want + " inWorld=" + inWorld);
			mob.discard();
			if (!inWorld) {
				return "the wanderer was never added to the world";
			}
			return Math.abs(held - want) <= want * 1.0E-6 ? null : "the wanderer holds " + held + " health, not its floor's " + want;
		});
	}

	@GameTest(maxTicks = 200)
	public void anAreaRollsOnce(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos pos = freshAnchor();
		LevelChunk chunk = level.getChunk(pos.x(), pos.z());
		whenLoaded(helper, level, pos, () -> {
			Mob first = TowerWanderers.rollRegion(level, chunk, OPEN_FLOOR, 1.0);
			Mob second = TowerWanderers.rollRegion(level, chunk, OPEN_FLOOR, 1.0);
			TowerTestSupport.result("rollsOnce", "first=" + (first != null) + " second=" + (second != null));
			if (first != null) {
				first.discard();
			}
			if (second != null) {
				second.discard();
			}
			if (first == null) {
				return "the first roll sent nothing, so the case proves nothing";
			}
			return second == null ? null : "an area rolled twice";
		});
	}

	/** An area with a player already in it waits: a wanderer never rises at someone's feet. */
	@GameTest(maxTicks = 200)
	public void anAreaWithAPlayerInItWaits(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos pos = freshAnchor();
		LevelChunk chunk = level.getChunk(pos.x(), pos.z());
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		BlockPos stand = pos.getMiddleBlockPosition(level.getSeaLevel() + 40);
		player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		player.setNoGravity(true);
		whenLoaded(helper, level, pos, () -> {
			Mob mob = TowerWanderers.rollRegion(level, chunk, OPEN_FLOOR, 1.0);
			TowerTestSupport.result("playerInArea", "mob=" + (mob != null) + " rolled=" + TowerWanderers.isRolled(chunk));
			TowerTestSupport.leave(helper, player);
			if (mob != null) {
				mob.discard();
				return "a wanderer rose " + (int) mob.distanceTo(player) + " blocks from a player in its area";
			}
			return TowerWanderers.isRolled(chunk) ? "the area spent its roll with a player in it" : null;
		});
	}

	/** Peaceful raises no monster, and an area rolled then would lose its one roll to a mob that vanishes at once. */
	@GameTest
	public void peacefulRaisesNoWanderer(final GameTestHelper helper) {
		boolean peaceful = TowerWanderers.raisesIn(net.minecraft.world.Difficulty.PEACEFUL);
		boolean easy = TowerWanderers.raisesIn(net.minecraft.world.Difficulty.EASY);
		TowerTestSupport.result("peaceful", "peaceful=" + peaceful + " easy=" + easy);
		helper.assertTrue(easy && !peaceful, "wanderers rise on peaceful=" + peaceful + " easy=" + easy);
		helper.succeed();
	}

	/** Difficulty is the whole server's, so a test cannot turn it to peaceful without clearing every other test's mobs. */
	@GameTest(required = false)
	public void aPeacefulWorldKeepsItsAreasUnrolledRequiresManualTest(final GameTestHelper helper) {
		helper.fail("difficulty is server-wide: set peaceful by hand, walk into new land, set easy, and wanderers must still come");
	}

	/**
	 * By day an undead wanderer would burn under the open sky, its drops going to no one; its area waits for the
	 * night instead of spending its roll. Twenty areas of an early floor, where a third of the kinds burn.
	 */
	@GameTest(maxTicks = 300)
	public void noUndeadWandererRisesByDay(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ChunkPos> areas = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ChunkPos pos = freshAnchor();
			areas.add(pos);
			level.getChunk(pos.x(), pos.z());
			level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, pos, 1);
		}
		boolean[] ran = {false};
		String[] verdict = {null};
		helper.succeedWhen(() -> {
			if (!ran[0]) {
				for (ChunkPos pos : areas) {
					helper.assertTrue(level.areEntitiesLoaded(pos.pack()), "the area at " + pos + " never loaded");
				}
				ran[0] = true;
				int burners = 0;
				int waited = 0;
				int risen = 0;
				for (ChunkPos pos : areas) {
					LevelChunk chunk = level.getChunk(pos.x(), pos.z());
					Mob mob = TowerWanderers.rollRegion(level, chunk, EARLY_FLOOR, 1.0, true);
					if (mob != null) {
						risen++;
						burners += mob.is(net.minecraft.tags.EntityTypeTags.BURN_IN_DAYLIGHT) ? 1 : 0;
						mob.discard();
					} else if (!TowerWanderers.isRolled(chunk)) {
						waited++;
					}
				}
				TowerTestSupport.result("undeadByDay", "risen=" + risen + " burners=" + burners + " waited=" + waited);
				verdict[0] = burners > 0 ? burners + " undead wanderers rose under the day's sun"
					: waited == 0 ? "no area waited for the night, so the case proves nothing" : null;
			}
			helper.assertTrue(verdict[0] == null, String.valueOf(verdict[0]));
		});
	}

	/** Only a player who fought their way up raises the world's highest floor: not a creative flyer, nor an unbuilt floor. */
	@GameTest(maxTicks = 100)
	public void onlyAClimberRaisesTheWorldsHighest(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		boolean climber = TowerProgress.counts(player, tower);
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		boolean creative = TowerProgress.counts(player, tower);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		net.minecraft.world.phys.Vec3 unbuilt = TowerLayout.arrival(tower, TowerTestSupport.freshFloor());
		player.teleportTo(tower, unbuilt.x, unbuilt.y, unbuilt.z, java.util.Set.of(), 0.0F, 0.0F, true);
		boolean onNothing = TowerProgress.counts(player, tower);
		TowerTestSupport.result("climbersOnly", "climber=" + climber + " creative=" + creative + " unbuilt=" + onNothing);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(climber, "a survival player on a built floor does not count, so the case proves nothing");
		helper.assertFalse(creative || onNothing, "the world's highest rises for creative=" + creative + " unbuilt=" + onNothing);
		helper.succeed();
	}

	/** The first twenty floors hold overworld mobs alone, and so do the wanderers they send; later floors send the rest. */
	@GameTest
	public void anEarlyTowerSendsOnlyOverworldKinds(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		List<EntityType<? extends Mob>> early = new ArrayList<>();
		List<EntityType<? extends Mob>> later = new ArrayList<>();
		for (int i = 0; i < 2_000; i++) {
			early.add(TowerWanderers.pick(random, EARLY_FLOOR));
			later.add(TowerWanderers.pick(random, OPEN_FLOOR));
		}
		long strangers = early.stream().filter(type -> !TowerMobs.OVERWORLD.contains(type)).count();
		long laterKinds = later.stream().filter(TowerMobs.LATER::contains).count();
		TowerTestSupport.result("earlyKinds", "strangers=" + strangers + " laterKinds=" + laterKinds);
		helper.assertTrue(laterKinds > 0, "floor " + OPEN_FLOOR + " never sent a later kind, so the case proves nothing");
		helper.assertTrue(strangers == 0, "floor " + EARLY_FLOOR + " sent " + strangers + " mobs no early floor holds");
		helper.succeed();
	}

	/**
	 * The nether's roof is bedrock: a wanderer stands on a cave floor beneath it, never on top of it. The test
	 * server's nether is flat, so the area is given its roof first.
	 */
	@GameTest(maxTicks = 200)
	public void aNetherWandererStandsBeneathTheRoof(final GameTestHelper helper) {
		ServerLevel nether = world(helper, Level.NETHER);
		ChunkPos pos = freshAnchor();
		LevelChunk chunk = nether.getChunk(pos.x(), pos.z());
		int roof = nether.getMinY() + nether.dimensionType().logicalHeight();
		whenLoaded(helper, nether, pos, () -> {
			for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
				for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
					nether.setBlockAndUpdate(new BlockPos(x, roof - 1, z), Blocks.BEDROCK.defaultBlockState());
				}
			}
			Mob mob = TowerWanderers.rollRegion(nether, chunk, OPEN_FLOOR, 1.0);
			if (mob == null) {
				return "the roofed nether area took no wanderer, so the case proves nothing";
			}
			BlockPos feet = mob.blockPosition();
			boolean standing = nether.getBlockState(feet.below()).isFaceSturdy(nether, feet.below(), Direction.UP);
			TowerTestSupport.result("netherWanderer", "at=" + feet + " roof=" + roof + " standing=" + standing);
			mob.discard();
			if (feet.getY() >= roof - 1) {
				return "the wanderer stands on the nether's roof at " + feet;
			}
			return standing ? null : "the wanderer at " + feet + " stands on nothing";
		});
	}

	/**
	 * The end past its island is void: an area there spends its roll on nothing, and drops no mob to its death.
	 * The test server's end is flat, so the area's ground is taken away first.
	 */
	@GameTest(maxTicks = 200)
	public void theEndsVoidTakesNoWanderer(final GameTestHelper helper) {
		ServerLevel end = world(helper, Level.END);
		ChunkPos pos = freshAnchor();
		LevelChunk chunk = end.getChunk(pos.x(), pos.z());
		whenLoaded(helper, end, pos, () -> {
			int top = end.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getMiddleBlockX(), pos.getMiddleBlockZ());
			for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
				for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
					for (int y = end.getMinY(); y < top; y++) {
						end.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
					}
				}
			}
			Mob mob = TowerWanderers.rollRegion(end, chunk, OPEN_FLOOR, 1.0);
			TowerTestSupport.result("endVoid", "cleared=" + (top - end.getMinY()) + " mob=" + (mob != null) + " rolled=" + TowerWanderers.isRolled(chunk));
			if (mob != null) {
				mob.discard();
				return "a wanderer was dropped into the end's void at " + mob.blockPosition();
			}
			return TowerWanderers.isRolled(chunk) ? null : "the void area never rolled, so the case proves nothing";
		});
	}

	/** A player walking the world meets the areas around them roll, and a wanderer rises out beyond the near ring. */
	@GameTest(maxTicks = 300)
	public void aPlayerAbroadMeetsAWanderer(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos home = freshAnchor();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		BlockPos stand = home.getMiddleBlockPosition(level.getSeaLevel() + 40);
		player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		player.setNoGravity(true);
		// Deep enough that the whole ring is entity-loaded, not just built.
		level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, home, TowerWanderers.MAX_RING + 3);
		for (int dx = -TowerWanderers.MAX_RING; dx <= TowerWanderers.MAX_RING; dx++) {
			for (int dz = -TowerWanderers.MAX_RING; dz <= TowerWanderers.MAX_RING; dz++) {
				level.getChunk(home.x() + dx, home.z() + dz);
			}
		}
		List<Mob> met = new ArrayList<>();
		java.util.function.Predicate<ServerPlayer> before = TowerWanderers.scanFilter();
		TowerWanderers.setRecorder(met::add);
		TowerWanderers.pinChance(1.0);
		TowerProgress.pinHighest(OPEN_FLOOR);
		TowerWanderers.setScanFilter(candidate -> candidate == player);
		helper.onEachTick(() -> {
			if (!met.isEmpty() || helper.getTick() >= 280) {
				TowerWanderers.setScanFilter(before);
				TowerWanderers.setRecorder(null);
				TowerWanderers.pinChance(null);
				TowerProgress.pinHighest(null);
			}
		});
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			if (met.isEmpty()) {
				helper.assertTrue(false, "no wanderer rose around a player abroad: " + ringState(level, player, home));
			}
			Mob mob = met.get(0);
			// The nearest of all it met: none may rise inside the near ring.
			int ring = met.stream().mapToInt(one -> Math.max(Math.abs(one.chunkPosition().x() - home.x()), Math.abs(one.chunkPosition().z() - home.z())))
				.min().orElse(-1);
			if (!done[0]) {
				done[0] = true;
				TowerTestSupport.result("playerAbroad", "tick=" + helper.getTick() + " met=" + met.size() + " ring=" + ring + " floor=" + TowerMobs.stateOf(mob).floor());
				met.forEach(Mob::discard);
				TowerTestSupport.leave(helper, player);
			}
			helper.assertTrue(ring >= TowerWanderers.MIN_RING, "a wanderer rose " + ring + " chunks from the player, inside the near ring");
		});
	}

	/** What the scan saw: the player's place, and the ring's anchors, loaded, entity-loaded and rolled. */
	private static String ringState(final ServerLevel level, final ServerPlayer player, final ChunkPos home) {
		int anchors = 0;
		int loaded = 0;
		int entities = 0;
		int rolled = 0;
		for (int dx = -TowerWanderers.MAX_RING; dx <= TowerWanderers.MAX_RING; dx++) {
			for (int dz = -TowerWanderers.MAX_RING; dz <= TowerWanderers.MAX_RING; dz++) {
				ChunkPos pos = new ChunkPos(home.x() + dx, home.z() + dz);
				if (Math.max(Math.abs(dx), Math.abs(dz)) < TowerWanderers.MIN_RING || !TowerWanderers.isAnchor(pos)) {
					continue;
				}
				anchors++;
				LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
				loaded += chunk != null ? 1 : 0;
				entities += level.areEntitiesLoaded(pos.pack()) ? 1 : 0;
				rolled += chunk != null && TowerWanderers.isRolled(chunk) ? 1 : 0;
			}
		}
		return "player=" + player.chunkPosition() + " in " + player.level().dimension().identifier() + " removed=" + player.isRemoved()
			+ " home=" + home + " anchors=" + anchors + " loaded=" + loaded + " entities=" + entities + " rolled=" + rolled
			+ " highest=" + TowerProgress.highest(level.getServer()) + " chance=" + TowerWanderers.chance();
	}

	@GameTest(maxTicks = 100)
	public void standingOnAFloorRaisesTheWorldsHighest(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		helper.succeedWhen(() -> {
			int recorded = TowerProgress.get(helper.getLevel().getServer()).recorded();
			helper.assertTrue(recorded >= floor, "the world's highest floor is " + recorded + " with a player on " + floor);
			TowerTestSupport.result("worldHighest", "floor=" + floor + " recorded=" + recorded);
			TowerTestSupport.leave(helper, player);
		});
	}
}
