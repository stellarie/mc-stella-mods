package com.stella.tower;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;

/** The larger floors (T17): their mobs and chests in step with their area, their layouts, and their lights and walks. */
public class TowerLargeFloorGameTest {
	private static final int BASE_AREA = TowerLayout.SIZE * TowerLayout.SIZE;

	/** A fresh biome floor of this size that is no elite or boss floor, so no guard or guaranteed chest is added to its count. */
	private static int floorOf(final long seed, final int width, final int depth) {
		for (int block = 0; block < 12; block++) {
			try {
				return TowerTestSupport.freshBiomeFloorWhere(f -> {
					TowerSizes.Size size = TowerSizes.of(seed, f);
					return size.width() == width && size.depth() == depth && f % TowerElites.ELITE_EVERY != 0;
				});
			} catch (IllegalStateException e) {
				// This block of floors holds none of the shape: the next block is tried.
			}
		}
		throw new IllegalStateException("no floor of " + width + "x" + depth + " in twelve blocks of floors");
	}

	/** A fresh biome floor that is drawn with this layout, found by the layout's own draw, and so by no plan. */
	private static int layoutFloor(final long seed, final BiomeLayout layout) {
		for (int block = 0; block < 20; block++) {
			try {
				return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0
					&& BiomeLayout.of(seed, f, FloorBiomes.of(seed, f), TowerSizes.of(seed, f)) == layout);
			} catch (IllegalStateException e) {
				// This block of floors holds none: the next block is tried.
			}
		}
		throw new IllegalStateException("no floor of the " + layout + " layout in twenty blocks of floors");
	}

	private static void hold(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos middle = plan.origin().offset(plan.width() / 2, 0, plan.depth() / 2);
		tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(middle), TowerFloors.chunkRadius(plan));
	}

	private static int chestBlocks(final ServerLevel tower, final TowerPlan plan) {
		BlockPos origin = plan.origin();
		int chests = 0;
		for (int x = 0; x < plan.width(); x++) {
			for (int z = 0; z < plan.depth(); z++) {
				for (int y = -1; y <= 12; y++) {
					chests += tower.getBlockState(origin.offset(x, y, z)).is(TowerBlocks.TOWER_CHEST) ? 1 : 0;
				}
			}
		}
		return chests;
	}

	/**
	 * Catches a big floor kept as thin as a small one: three floors of 64 by 64, 96 by 128 and 128 by 128 are stocked by the
	 * real spawner with a player on each, and the mobs each holds and the chests each is built with stand in step with its
	 * area, so the biggest has about four times the base floor's.
	 */
	@GameTest(maxTicks = 800)
	public void mobsAndChestsScaleWithTheFloorsArea(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int[] floors = {floorOf(seed, 64, 64), floorOf(seed, 96, 128), floorOf(seed, 128, 128)};
		List<ServerPlayer> players = new ArrayList<>();
		for (int floor : floors) {
			TowerGhosts.pinRiseChance(floor, 0.0);
			TowerPhantoms.pinRiseChance(floor, 0.0);
			TowerFloors.ensure(tower, floor);
			hold(tower, floor);
			ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
			player.setGameMode(GameType.CREATIVE);
			players.add(player);
		}
		int[] filledAt = {-1, -1, -1};
		helper.onEachTick(() -> {
			for (int i = 0; i < floors.length; i++) {
				if (filledAt[i] < 0 && TowerMobs.count(tower, floors[i]) >= TowerMobs.floorCap(tower, floors[i])) {
					filledAt[i] = (int) helper.getTick();
				}
			}
		});
		helper.runAfterDelay(700L, () -> {
			int[] mobs = new int[floors.length];
			int[] spots = new int[floors.length];
			int[] chests = new int[floors.length];
			StringBuilder report = new StringBuilder();
			for (int i = 0; i < floors.length; i++) {
				TowerPlan plan = TowerPlan.at(tower, floors[i]);
				mobs[i] = TowerMobs.count(tower, floors[i]);
				spots[i] = plan.chestSpots().size();
				chests[i] = chestBlocks(tower, plan);
				report.append(plan.width()).append('x').append(plan.depth()).append(" cap=").append(TowerMobs.floorCap(tower, floors[i])).append(" mobs=").append(mobs[i])
					.append(" filledAt=").append(filledAt[i]).append(" spots=").append(spots[i]).append(" chestBlocks=").append(chests[i]).append("; ");
			}
			TowerTestSupport.result("floorScale", report.toString());
			for (int i = 0; i < floors.length; i++) {
				TowerGhosts.pinRiseChance(floors[i], null);
				TowerPhantoms.pinRiseChance(floors[i], null);
				TowerGhosts.on(tower, floors[i]).forEach(net.minecraft.world.entity.Entity::discard);
				tower.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, TowerMobs.room(floors[i]), mob -> TowerMobs.stateOf(mob) != null).forEach(net.minecraft.world.entity.Entity::discard);
				TowerTestSupport.leave(helper, players.get(i));
			}
			double mobRatio = mobs[2] / (double) mobs[0];
			double midMobRatio = mobs[1] / (double) mobs[0];
			double chestRatio = spots[2] / (double) spots[0];
			double midChestRatio = spots[1] / (double) spots[0];
			helper.assertTrue(mobs[0] >= 60, "the base floor holds only " + mobs[0] + " mobs");
			helper.assertTrue(mobRatio >= 3.7 && mobRatio <= 4.3, "the 128x128 floor holds " + mobs[2] + " mobs against the base floor's " + mobs[0] + ": " + mobRatio + " times, not about 4");
			helper.assertTrue(midMobRatio >= 2.7 && midMobRatio <= 3.3, "the 96x128 floor holds " + mobs[1] + " mobs against the base floor's " + mobs[0] + ": " + midMobRatio + " times, not about 3");
			helper.assertTrue(chestRatio >= 3.3 && chestRatio <= 4.8, "the 128x128 floor has " + spots[2] + " chests against the base floor's " + spots[0] + ": " + chestRatio + " times, not about 4");
			helper.assertTrue(midChestRatio >= 2.4 && midChestRatio <= 3.6, "the 96x128 floor has " + spots[1] + " chests against the base floor's " + spots[0] + ": " + midChestRatio + " times, not about 3");
			for (int i = 0; i < floors.length; i++) {
				helper.assertTrue(chests[i] == 2 * spots[i], "floor " + floors[i] + " stands with " + chests[i] + " chest blocks for " + spots[i] + " double chests");
			}
			helper.succeed();
		});
	}

	/** Whether a recorded part of a layout stands in a floor: its rows are read through {@code solid}, a test of one block of the room. */
	private interface Solids {
		boolean solid(int x, int y, int z);
	}

	/**
	 * The share of a mark's columns that hold a block this far over the ground, and whether any reaches nearly its height. The
	 * columns of a wall, a tower and a keep are those of its outline: what lies inside is open ground.
	 */
	private static double standing(final TowerPlan plan, final int[] mark, final Solids blocks, final int over, final boolean[] reaches) {
		int kind = mark[BiomeDraft.MARK_KIND];
		int x0 = mark[BiomeDraft.MARK_X];
		int z0 = mark[BiomeDraft.MARK_Z];
		int w = mark[BiomeDraft.MARK_W];
		int l = mark[BiomeDraft.MARK_L];
		int height = mark[BiomeDraft.MARK_HEIGHT];
		int counted = 0;
		int holds = 0;
		reaches[0] = false;
		for (int x = x0; x < x0 + w; x++) {
			for (int z = z0; z < z0 + l; z++) {
				boolean outline = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + l - 1;
				boolean hollow = kind == BiomeDraft.MARK_WALL || kind == BiomeDraft.MARK_TOWER || kind == BiomeDraft.MARK_KEEP;
				if (hollow && !outline) {
					continue;
				}
				counted++;
				int feet = plan.biome().feet()[x][z];
				// The road runs under a tower or a wall it crosses, with four blocks clear over its ground: the column stands above that.
				boolean stands = false;
				for (int y = feet + over; y <= feet + Math.max(over, height - 2) && !stands; y++) {
					stands = blocks.solid(x, y, z);
				}
				holds += stands ? 1 : 0;
				reaches[0] |= blocks.solid(x, feet + Math.max(1, height - 2), z);
			}
		}
		return counted == 0 ? 0.0 : holds / (double) counted;
	}

	/** The faults of a plan's layout as it stands in {@code blocks}: marks outside the room, or marks that are not built, or a layout short of its parts. */
	private static List<String> layoutFaults(final TowerPlan plan, final Solids blocks, final Map<Integer, Double> leastShare) {
		BiomeDraft draft = plan.biome();
		List<String> faults = new ArrayList<>();
		Map<Integer, Integer> kinds = new java.util.HashMap<>();
		for (int[] mark : draft.marks()) {
			kinds.merge(mark[BiomeDraft.MARK_KIND], 1, Integer::sum);
			boolean inside = mark[BiomeDraft.MARK_X] >= 0 && mark[BiomeDraft.MARK_Z] >= 0 && mark[BiomeDraft.MARK_X] + mark[BiomeDraft.MARK_W] <= plan.width()
				&& mark[BiomeDraft.MARK_Z] + mark[BiomeDraft.MARK_L] <= plan.depth();
			if (!inside) {
				faults.add("a mark lies outside the room: " + java.util.Arrays.toString(mark));
				continue;
			}
			if (mark[BiomeDraft.MARK_HEIGHT] > 0) {
				boolean[] reaches = new boolean[1];
				double share = standing(plan, mark, blocks, 1, reaches);
				leastShare.merge(mark[BiomeDraft.MARK_KIND], share, Math::min);
				// A wall is open at its gates, its breaches and the road, so it is held to the least.
				double need = switch (mark[BiomeDraft.MARK_KIND]) {
					case BiomeDraft.MARK_WALL -> 0.25;
					case BiomeDraft.MARK_CLIFF -> 0.3;
					default -> 0.35;
				};
				if (share < need || !reaches[0]) {
					faults.add("floor " + plan.floor() + " a mark is not built: " + java.util.Arrays.toString(mark) + " share=" + share + " reaches=" + reaches[0]);
				}
			}
		}
		int walls = kinds.getOrDefault(BiomeDraft.MARK_WALL, 0);
		int towers = kinds.getOrDefault(BiomeDraft.MARK_TOWER, 0);
		switch (draft.layout()) {
			case WALLED_TOWN -> {
				if (walls < 1 || towers < 4 || kinds.getOrDefault(BiomeDraft.MARK_PLAZA, 0) < 1) {
					faults.add("a walled town lacks its wall, its four towers or its plaza: " + kinds);
				}
			}
			case CANYON -> {
				if (kinds.getOrDefault(BiomeDraft.MARK_CLIFF, 0) < 2 || kinds.getOrDefault(BiomeDraft.MARK_BUTTE, 0) < 3) {
					faults.add("a canyon lacks its two cliffs or its buttes: " + kinds);
				}
			}
			case FORTRESS_YARD -> {
				if (walls < 1 || towers < 4 || kinds.getOrDefault(BiomeDraft.MARK_KEEP, 0) < 1) {
					faults.add("a fortress yard lacks its wall, its four towers or its keep: " + kinds);
				}
			}
			default -> {
				if (draft.marks().length != 0) {
					faults.add("a standard layout holds marks");
				}
			}
		}
		return faults;
	}

	/**
	 * Catches a larger floor with no set piece: over a run of drawn floors at least three layouts other than the standard
	 * come, only on floors larger than the base, each drawn with its parts standing in the plan's volume, and every larger
	 * floor still carries the cover and the hills the enclosed floors have. A floor of each layout is then built and its
	 * parts are read back from the world, with the walk from the arrival to the portal over the built blocks.
	 */
	@GameTest(maxTicks = 3000)
	public void largeFloorsCarryThreeNewLayoutsWithCoverAndHills(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		Map<BiomeLayout, Integer> layouts = new EnumMap<>(BiomeLayout.class);
		Map<BiomeLayout, Integer> firstFloor = new EnumMap<>(BiomeLayout.class);
		List<String> faults = new ArrayList<>();
		Map<Integer, Double> leastShare = new java.util.TreeMap<>();
		Map<Integer, Double> builtLeast = new java.util.TreeMap<>();
		int plans = 0;
		int fallbacks = 0;
		int smallWithLayout = 0;
		for (int floor = TowerTestSupport.BIOME_FLOORS_FROM + 200_000; plans < 150 && floor < TowerTestSupport.BIOME_FLOORS_FROM + 220_000; floor++) {
			TowerSizes.Size size = TowerSizes.of(seed, floor);
			if (size.area() <= BASE_AREA || !FloorBiomes.of(seed, floor).openRoof() || floor % TowerElites.BOSS_EVERY == 0) {
				continue;
			}
			TowerPlan plan = TowerPlan.of(seed, floor);
			TowerVolume volume = TowerVolume.of(plan);
			plans++;
			fallbacks += plan.attempt() == TowerPlan.FALLBACK ? 1 : 0;
			BiomeDraft draft = plan.biome();
			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			for (int x = 0; x < plan.width(); x++) {
				for (int z = 0; z < plan.depth(); z++) {
					if (!draft.water()[x][z] && !draft.lava()[x][z]) {
						low = Math.min(low, draft.feet()[x][z]);
						high = Math.max(high, draft.feet()[x][z]);
					}
				}
			}
			if (draft.cover().length < TowerMobs.scaled(BiomeCover.LEAST, plan) || high - low < BiomeGen.MIN_RISE) {
				faults.add("floor " + floor + " " + plan.width() + "x" + plan.depth() + " has " + draft.cover().length + " pieces of cover and a rise of " + (high - low));
			}
			layouts.merge(draft.layout(), 1, Integer::sum);
			firstFloor.putIfAbsent(draft.layout(), floor);
			if (draft.layout() != BiomeLayout.STANDARD) {
				faults.addAll(layoutFaults(plan, (x, y, z) -> volume.kind(x, y, z) != TowerVolume.AIR, leastShare));
			}
		}
		for (int floor = TowerTestSupport.BIOME_FLOORS_FROM + 400_000, counted = 0; counted < 40 && floor < TowerTestSupport.BIOME_FLOORS_FROM + 420_000; floor++) {
			if (TowerSizes.of(seed, floor).area() <= BASE_AREA && floor % TowerElites.BOSS_EVERY != 0) {
				counted++;
				smallWithLayout += TowerPlan.of(seed, floor).biome().layout() != BiomeLayout.STANDARD ? 1 : 0;
			}
		}
		TowerTestSupport.result("largeLayouts", "plans=" + plans + " layouts=" + layouts + " fallbacks=" + fallbacks + " smallWithLayout=" + smallWithLayout
			+ " faults=" + faults.size() + " leastShareByMarkKind=" + leastShare);
		helper.assertTrue(plans >= 60, "only " + plans + " larger open floors came in twenty thousand floors");
		long other = layouts.entrySet().stream().filter(e -> e.getKey() != BiomeLayout.STANDARD && e.getValue() >= 3).count();
		helper.assertTrue(other >= 3, "fewer than three layouts other than the standard came, three times each, over " + plans + " larger floors: " + layouts);
		helper.assertTrue(smallWithLayout == 0, smallWithLayout + " floors of the base size took a layout of the larger ones");
		helper.assertTrue(fallbacks * 100 <= plans, fallbacks + " of " + plans + " larger floors fell back to the plain yard");
		helper.assertTrue(faults.isEmpty(), "larger floors drawn wrong: " + faults.subList(0, Math.min(8, faults.size())));
		for (BiomeLayout layout : BiomeLayout.values()) {
			if (layout == BiomeLayout.STANDARD || !firstFloor.containsKey(layout)) {
				continue;
			}
			int floor = layoutFloor(seed, layout);
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.of(seed, floor);
			BlockPos origin = plan.origin();
			List<String> builtFaults = layoutFaults(plan, (x, y, z) -> !tower.getBlockState(origin.offset(x, y, z)).isAir(), builtLeast);
			int[] dist = TowerWalk.distances(TowerWalk.world(tower, floor), plan.arrivalX(), 0, plan.arrivalZ());
			boolean reaches = false;
			for (int[] goal : TowerVolume.portalGoals(plan)) {
				reaches |= dist[TowerWalk.index(goal[0], goal[1], goal[2])] >= 0;
			}
			TowerTestSupport.result("largeLayoutBuilt", layout + " floor=" + floor + " " + plan.width() + "x" + plan.depth() + " faults=" + builtFaults.size() + " reaches=" + reaches + " leastShareByMarkKind=" + builtLeast);
			helper.assertTrue(builtFaults.isEmpty(), layout + " floor " + floor + " is not built as drawn: " + builtFaults);
			helper.assertTrue(reaches, layout + " floor " + floor + " has no walk from the arrival to the portal over its built blocks");
		}
		helper.succeed();
	}

	/**
	 * Catches a larger floor with a dark corner or no way through: over a run of the largest and the long floors, each
	 * plan is walked from the arrival to the portal, and every spot a body reaches has a light within the reach the lamp
	 * rule asks.
	 */
	@GameTest(maxTicks = 3000)
	public void largeFloorsAreWalkableAndLitEverywhere(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int plans = 0;
		int stuck = 0;
		int dark = 0;
		long longestNanos = 0L;
		StringBuilder report = new StringBuilder();
		for (int floor = TowerTestSupport.BIOME_FLOORS_FROM + 600_000; plans < 36 && floor < TowerTestSupport.BIOME_FLOORS_FROM + 620_000; floor++) {
			TowerSizes.Size size = TowerSizes.of(seed, floor);
			if (size.area() <= BASE_AREA || floor % TowerElites.BOSS_EVERY == 0) {
				continue;
			}
			long started = System.nanoTime();
			TowerPlan plan = TowerPlan.of(seed, floor);
			longestNanos = Math.max(longestNanos, System.nanoTime() - started);
			TowerVolume volume = TowerVolume.of(plan);
			plans++;
			stuck += volume.reaches(plan) ? 0 : 1;
			List<int[]> lamps = new ArrayList<>();
			for (int x = 0; x < plan.width(); x++) {
				for (int z = 0; z < plan.depth(); z++) {
					for (int y = plan.low(); y < plan.roofY(); y++) {
						if (volume.kind(x, y, z) == TowerVolume.BIOME_BLOCK && BiomeBlocks.lights(volume.code(x, y, z))) {
							lamps.add(new int[] {x, y, z});
						}
					}
				}
			}
			int[] dist = TowerWalk.distances(volume, plan.arrivalX(), 0, plan.arrivalZ());
			int own = 0;
			for (int x = 0; x < plan.width(); x++) {
				for (int z = 0; z < plan.depth(); z++) {
					for (int y = TowerWalk.LOW; y <= TowerWalk.HIGH; y++) {
						if (dist[TowerWalk.index(x, y, z)] < 0) {
							continue;
						}
						boolean lit = false;
						for (int[] lamp : lamps) {
							int dx = lamp[0] - x;
							int dy = lamp[1] - y;
							int dz = lamp[2] - z;
							lit |= dx * dx + dy * dy + dz * dz <= BiomeGen.LIGHT_REACH * BiomeGen.LIGHT_REACH;
						}
						own += lit ? 0 : 1;
					}
				}
			}
			dark += own;
			if (own != 0) {
				report.append(floor).append(' ').append(plan.width()).append('x').append(plan.depth()).append(" dark=").append(own).append("; ");
			}
		}
		TowerTestSupport.result("largeWalkLit", "plans=" + plans + " stuck=" + stuck + " dark=" + dark + " longestPlanMs=" + longestNanos / 1.0E6 + " " + report);
		helper.assertTrue(plans >= 30, "only " + plans + " larger floors came in twenty thousand floors");
		helper.assertTrue(stuck == 0, stuck + " of " + plans + " larger floors have no walk from the arrival to the portal");
		helper.assertTrue(dark == 0, dark + " reachable spots of the larger floors have no light within " + BiomeGen.LIGHT_REACH + " blocks: " + report);
		helper.succeed();
	}

	/**
	 * Catches a road with a block on it that turns it to dirt once the floor runs: over floors of every size and biome,
	 * no column of a plan has a road surface under a block that is not open to a walk. The plan holds the dirt that
	 * such a road turns to already, which is what keeps a floor from changing by itself (see "stands still" above).
	 */
	@GameTest(maxTicks = 3000)
	public void noRoadLiesUnderABlockThatTurnsItToDirt(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int plans = 0;
		int large = 0;
		int roads = 0;
		List<String> faults = new ArrayList<>();
		for (int floor = TowerTestSupport.BIOME_FLOORS_FROM + 800_000; plans < 150 && floor < TowerTestSupport.BIOME_FLOORS_FROM + 830_000; floor++) {
			if (floor % TowerElites.BOSS_EVERY == 0) {
				continue;
			}
			TowerPlan plan = TowerPlan.of(seed, floor);
			BiomeDraft draft = plan.biome();
			plans++;
			large += plan.area() > BASE_AREA ? 1 : 0;
			for (int x = 0; x < plan.width(); x++) {
				for (int z = 0; z < plan.depth(); z++) {
					roads += draft.surface()[x][z] == BiomeBlocks.PATH ? 1 : 0;
				}
			}
			for (BiomeDraft.Piece piece : draft.decor(plan)) {
				int x = piece.x();
				int z = piece.z();
				if (piece.kind() == TowerVolume.BIOME_BLOCK && BiomeBlocks.walk(piece.code()) != TowerWalk.OPEN && x >= 0 && x < plan.width() && z >= 0 && z < plan.depth()
					&& piece.y() == draft.feet()[x][z] && draft.surface()[x][z] == BiomeBlocks.PATH) {
					faults.add("floor " + floor + " " + plan.biome().biome() + " " + plan.width() + "x" + plan.depth() + " dais piece " + piece.code() + " at " + x + "," + piece.y() + "," + z);
				}
			}
			int[] placed = draft.placed();
			for (int i = 0; i < placed.length; i += 4) {
				int x = placed[i];
				int z = placed[i + 2];
				if (draft.surface()[x][z] == BiomeBlocks.PATH && placed[i + 1] == draft.feet()[x][z] && BiomeBlocks.walk((byte) placed[i + 3]) != TowerWalk.OPEN) {
					faults.add("floor " + floor + " " + plan.biome().biome() + " " + plan.width() + "x" + plan.depth() + " code " + placed[i + 3] + " at " + x + "," + placed[i + 1] + "," + z);
				}
			}
		}
		TowerTestSupport.result("roadUnder", "plans=" + plans + " large=" + large + " roadColumns=" + roads + " faults=" + faults.size() + " " + faults.subList(0, Math.min(6, faults.size())));
		helper.assertTrue(plans >= 100 && large >= 20 && roads > 1000, "the sample is too thin: plans=" + plans + " large=" + large + " roadColumns=" + roads);
		helper.assertTrue(faults.isEmpty(), faults.size() + " road columns lie under a block that turns them to dirt: " + faults.subList(0, Math.min(6, faults.size())));
		helper.succeed();
	}
}
