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

	/** An area no earlier run has rolled: the test world keeps its chunks, so a reused one proves nothing. */
	private static ChunkPos freshAnchor() {
		int n = TowerTestSupport.freshFloor() / 4;
		return new ChunkPos(TowerWanderers.REGION_CHUNKS * (n % 400_000), TowerWanderers.REGION_CHUNKS * (2_000 + (n / 400_000) % 1_000));
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
			double want = TowerCurve.health(mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue(), OPEN_FLOOR);
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
