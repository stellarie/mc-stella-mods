package com.stella.tower;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The enclosed tower: every floor under a stone roof, the open floors dressed with hills and cover that hide the
 * mobs from a player who lands, and a pool of mobs that holds breezes and shulkers and no endermites.
 */
public class TowerEnclosedGameTest {
	/** Floors built for each open biome, and the spawn spots sampled on each. */
	private static final int FLOORS_PER_BIOME = 2;
	private static final int SIGHT_SAMPLES = 60;
	/** The most spots a player on the arrival may see, as a share of the spots a mob is raised on. */
	private static final double MOST_SEEN = 0.5;
	private static final double EYE = 1.62;

	private static int floorOf(final ServerLevel tower, final FloorBiome biome) {
		long seed = tower.getSeed();
		return biome == FloorBiome.GREAT_HALL
			? TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY == 0)
			: TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f) == biome);
	}

	private static void hold(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos middle = plan.origin().offset(plan.width() / 2, 0, plan.depth() / 2);
		tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(middle), TowerFloors.chunkRadius(plan));
	}

	private static boolean seen(final ServerLevel tower, final net.minecraft.world.entity.Entity looker, final Vec3 from, final Vec3 to) {
		return tower.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker)).getType() == HitResult.Type.MISS;
	}

	/** Catches a floor with a gap in its roof: a barrier, glass, a missing block, or anything a sky would show through. */
	@GameTest(maxTicks = 1000)
	public void everyColumnOfEveryBiomeFloorHasASolidRoof(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int columns = 0;
		int expected = 0;
		int bad = 0;
		StringBuilder report = new StringBuilder();
		Map<String, Integer> blocks = new HashMap<>();
		for (FloorBiome biome : FloorBiome.values()) {
			int floor = floorOf(tower, biome);
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			BlockPos origin = plan.origin();
			expected += plan.area();
			int gaps = 0;
			for (int x = 0; x < plan.width(); x++) {
				for (int z = 0; z < plan.depth(); z++) {
					BlockState roof = tower.getBlockState(origin.offset(x, plan.roofY(), z));
					columns++;
					boolean solid = roof.isSolidRender() && !roof.is(Blocks.BARRIER) && roof.getLightDampening() >= 15;
					gaps += solid ? 0 : 1;
					blocks.merge(biome.id() + ":" + roof.getBlock().getDescriptionId(), 1, Integer::sum);
				}
			}
			bad += gaps;
			report.append(biome.id()).append(" floor=").append(floor).append(" gaps=").append(gaps).append("; ");
		}
		TowerTestSupport.result("roofs", "columns=" + columns + " gaps=" + bad + " " + report);
		helper.assertTrue(columns == expected, "the roofs sampled " + columns + " columns, not " + expected);
		helper.assertTrue(bad == 0, bad + " roof columns are not a solid, opaque block: " + report);
		helper.succeed();
	}

	/**
	 * Catches an open floor that is flat and bare: it holds at least six structures, its ground rises at least four blocks,
	 * the structures stand in the built world as planned, and most of the spots a mob is raised on are hidden from a
	 * player on the arrival.
	 */
	@GameTest(maxTicks = 2400)
	public void openFloorsHaveCoverHillsAndHideMostMobsFromTheArrival(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		List<String> thin = new ArrayList<>();
		List<String> flat = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		List<String> exposed = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		int floors = 0;
		for (FloorBiome biome : FloorBiome.DRAWN) {
			if (!biome.openRoof()) {
				continue;
			}
			for (int n = 0; n < FLOORS_PER_BIOME; n++) {
				int floor = floorOf(tower, biome);
				TowerFloors.ensure(tower, floor);
				hold(tower, floor);
				floors++;
				TowerPlan plan = TowerPlan.at(tower, floor);
				BiomeDraft draft = plan.biome();
				String name = biome.id() + "#" + floor;
				int[][] cover = draft.cover();
				if (cover.length < 6) {
					thin.add(name + " cover=" + cover.length);
				}
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
				if (high - low < 4) {
					flat.add(name + " rise=" + (high - low));
				}
				// Each recorded structure stands in the world: its tallest column holds a block at the planned height.
				BlockPos origin = plan.origin();
				int tallest = 0;
				for (int[] piece : cover) {
					boolean found = false;
					for (int x = piece[BiomeDraft.COVER_X]; x < piece[BiomeDraft.COVER_X] + piece[BiomeDraft.COVER_W]; x++) {
						for (int z = piece[BiomeDraft.COVER_Z]; z < piece[BiomeDraft.COVER_Z] + piece[BiomeDraft.COVER_L]; z++) {
							int top = TowerLayout.BASE_Y + draft.feet()[x][z] + piece[BiomeDraft.COVER_HEIGHT] - 1;
							found |= !tower.getBlockState(origin.offset(x, top - TowerLayout.BASE_Y, z)).isAir();
						}
					}
					if (!found) {
						missing.add(name + " piece=" + piece[BiomeDraft.COVER_KIND] + "@" + piece[BiomeDraft.COVER_X] + "," + piece[BiomeDraft.COVER_Z]);
					}
					tallest = Math.max(tallest, piece[BiomeDraft.COVER_HEIGHT]);
				}
				// The spots a mob is raised on, seen from the eyes of a player standing on the arrival.
				ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
				Vec3 eye = plan.arrivalPos().add(0.0, EYE, 0.0);
				Zombie probe = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
				int placed = 0;
				int visible = 0;
				for (int i = 0; i < SIGHT_SAMPLES; i++) {
					if (TowerMobs.placeInRoom(tower, floor, probe, tower.getRandom())) {
						placed++;
						visible += seen(tower, player, eye, probe.getEyePosition()) ? 1 : 0;
					}
				}
				// The same eyes on any ground the floor offers beyond the clear ring, with no preference for hidden spots.
				Random random = new Random(floor);
				int ground = 0;
				int open = 0;
				for (int tries = 0; tries < 4000 && ground < 150; tries++) {
					int x = 1 + random.nextInt(plan.width() - 2);
					int z = 1 + random.nextInt(plan.depth() - 2);
					int y = plan.standY(x, z);
					Vec3 spot = new Vec3(origin.getX() + x + 0.5, TowerLayout.BASE_Y + y, origin.getZ() + z + 0.5);
					if (y >= TowerWalk.LOW && !draft.inDais(plan, x, z) && spot.distanceTo(plan.arrivalPos()) >= TowerMobs.BIOME_ARRIVAL_CLEAR) {
						ground++;
						open += seen(tower, player, eye, spot.add(0.0, EYE, 0.0)) ? 1 : 0;
					}
				}
				TowerTestSupport.leave(helper, player);
				double share = placed == 0 ? 1.0 : visible / (double) placed;
				if (placed < SIGHT_SAMPLES / 2 || share > MOST_SEEN) {
					exposed.add(name + " seen=" + visible + "/" + placed);
				}
				report.append(name).append(" cover=").append(cover.length).append(" tallest=").append(tallest).append(" rise=").append(high - low)
					.append(" seen=").append(visible).append("/").append(placed).append(" groundSeen=").append(open).append("/").append(ground).append("; ");
			}
		}
		TowerTestSupport.result("cover", "floors=" + floors + " " + report);
		helper.assertTrue(thin.isEmpty(), "floors with fewer than six structures: " + thin);
		helper.assertTrue(flat.isEmpty(), "floors whose ground rises under four blocks: " + flat);
		helper.assertTrue(missing.isEmpty(), "planned structures missing from the built world: " + missing);
		helper.assertTrue(exposed.isEmpty(), "floors where a mob raised in sight of the arrival is too common: " + exposed);
		helper.succeed();
	}

	/**
	 * Catches a generator that cannot dress a biome: over many drawn floors of each open biome none falls back to the plain
	 * yard, every one carries its six structures and four blocks of rise, and a tower reaches high while none passes twenty.
	 * The world case above builds a few floors; this one is the number that keeps it from failing by chance.
	 */
	@GameTest(maxTicks = 1200)
	public void everyDrawnOpenFloorCarriesItsCoverAndHills(final GameTestHelper helper) {
		long seed = 0x7A11_B10E_C0B3L;
		int floors = 0;
		int fallbacks = 0;
		int thin = 0;
		int flat = 0;
		int tooTall = 0;
		StringBuilder report = new StringBuilder();
		for (FloorBiome biome : FloorBiome.DRAWN) {
			if (!biome.openRoof()) {
				continue;
			}
			int drawn = 0;
			int tallest = 0;
			int fewest = Integer.MAX_VALUE;
			int least = Integer.MAX_VALUE;
			for (int floor = 2; drawn < 40 && floor < 6000; floor++) {
				if (floor % TowerElites.BOSS_EVERY == 0 || FloorBiomes.of(seed, floor) != biome) {
					continue;
				}
				TowerPlan plan = BiomeGen.plan(seed, floor);
				BiomeDraft draft = plan.biome();
				drawn++;
				floors++;
				fallbacks += plan.attempt() == TowerPlan.FALLBACK ? 1 : 0;
				thin += draft.cover().length < BiomeCover.LEAST ? 1 : 0;
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
				flat += high - low < BiomeGen.MIN_RISE ? 1 : 0;
				least = Math.min(least, high - low);
				fewest = Math.min(fewest, draft.cover().length);
				for (int[] piece : draft.cover()) {
					tallest = Math.max(tallest, piece[BiomeDraft.COVER_HEIGHT]);
					tooTall += piece[BiomeDraft.COVER_HEIGHT] > BiomeCover.TOWER_MAX ? 1 : 0;
				}
			}
			report.append(biome.id()).append(" floors=").append(drawn).append(" fewestCover=").append(fewest).append(" lowestRise=").append(least)
				.append(" tallest=").append(tallest).append("; ");
			helper.assertTrue(tallest >= 12, biome.id() + " has no tower over twelve blocks: tallest " + tallest);
		}
		TowerTestSupport.result("coverPlans", "floors=" + floors + " fallbacks=" + fallbacks + " thin=" + thin + " flat=" + flat + " " + report);
		helper.assertTrue(floors >= 200, "only " + floors + " floors were drawn");
		helper.assertTrue(fallbacks == 0, fallbacks + " floors fell back to the plain yard, with no cover");
		helper.assertTrue(thin == 0 && flat == 0, thin + " floors under six structures, " + flat + " floors under four blocks of rise: " + report);
		helper.assertTrue(tooTall == 0, tooTall + " structures rise past " + BiomeCover.TOWER_MAX + " blocks");
		helper.succeed();
	}

	/**
	 * Catches a pool without breezes or shulkers, or one that holds or raises an endermite or a silverfish, over a large
	 * sample of rolls and in every pool the tower draws from: each biome's, the older floors' (which the lost rooms, the
	 * wanderers, the traps' ambushes and the guardians draw from as well) and the sampled floors' own.
	 */
	@GameTest(maxTicks = 2400)
	public void towerFloorsRaiseBreezesAndShulkersAndNoEndermitesOrSilverfish(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		Map<EntityType<?>, Integer> raised = new HashMap<>();
		int rolls = 0;
		int stuck = 0;
		int banned = 0;
		for (FloorBiome biome : FloorBiome.values()) {
			banned += BiomePools.of(biome).contains(EntityTypes.SILVERFISH) || BiomePools.of(biome).contains(EntityTypes.ENDERMITE) ? 1 : 0;
		}
		for (int floor = 1; floor <= 120; floor++) {
			for (List<EntityType<? extends Mob>> pool : List.of(TowerMobs.pool(floor), TowerElites.guardianPool(floor))) {
				banned += pool.contains(EntityTypes.SILVERFISH) || pool.contains(EntityTypes.ENDERMITE) ? 1 : 0;
			}
		}
		for (FloorBiome biome : FloorBiome.DRAWN) {
			int floor = floorOf(tower, biome);
			TowerFloors.ensure(tower, floor);
			hold(tower, floor);
			for (List<EntityType<? extends Mob>> pool : List.of(TowerMobs.poolOf(tower, floor), TowerElites.guardianPool(tower, floor))) {
				banned += pool.contains(EntityTypes.SILVERFISH) || pool.contains(EntityTypes.ENDERMITE) ? 1 : 0;
			}
			for (int i = 0; i < 160; i++) {
				Mob mob = TowerMobs.spawnOne(tower, floor);
				if (mob != null) {
					rolls++;
					raised.merge(mob.getType(), 1, Integer::sum);
					stuck += tower.noCollision(mob, mob.getBoundingBox()) ? 0 : 1;
					mob.discard();
				}
			}
		}
		List<EntityType<? extends Mob>> later = TowerMobs.pool(TowerMobs.OVERWORLD_ONLY_UNTIL + 1);
		TowerTestSupport.result("pool", "rolls=" + rolls + " breezes=" + raised.getOrDefault(EntityTypes.BREEZE, 0) + " shulkers="
			+ raised.getOrDefault(EntityTypes.SHULKER, 0) + " endermites=" + raised.getOrDefault(EntityTypes.ENDERMITE, 0) + " silverfish="
			+ raised.getOrDefault(EntityTypes.SILVERFISH, 0) + " poolsHoldingEither=" + banned + " stuck=" + stuck);
		helper.assertTrue(rolls >= 1000, "only " + rolls + " rolls placed a mob");
		helper.assertTrue(raised.getOrDefault(EntityTypes.BREEZE, 0) > 0, "no breeze rose in " + rolls + " rolls");
		helper.assertTrue(raised.getOrDefault(EntityTypes.SHULKER, 0) > 0, "no shulker rose in " + rolls + " rolls");
		helper.assertTrue(raised.getOrDefault(EntityTypes.ENDERMITE, 0) == 0, raised.get(EntityTypes.ENDERMITE) + " endermites rose");
		helper.assertTrue(raised.getOrDefault(EntityTypes.SILVERFISH, 0) == 0, raised.get(EntityTypes.SILVERFISH) + " silverfish rose");
		helper.assertTrue(banned == 0, banned + " pools hold an endermite or a silverfish");
		helper.assertTrue(!later.contains(EntityTypes.ENDERMITE) && !later.contains(EntityTypes.SILVERFISH) && later.contains(EntityTypes.BREEZE) && later.contains(EntityTypes.SHULKER),
			"the older floors' pool is " + later);
		helper.assertTrue(stuck == 0, stuck + " raised mobs started inside a block");
		helper.succeed();
	}

	/**
	 * Catches a shulker that teleports out of its floor: over the roof, under the floor, or past the walls. It starts
	 * under the roof and at each edge of the room, where a jump of eight blocks can leave.
	 */
	@GameTest(maxTicks = 600)
	public void aShulkerThatTeleportsStaysInItsFloorVolume(final GameTestHelper helper) throws Exception {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int floor = TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && FloorBiomes.of(seed, f).openRoof());
		TowerFloors.ensure(tower, floor);
		hold(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos origin = plan.origin();
		Method teleport = Shulker.class.getDeclaredMethod("teleportSomewhere");
		teleport.setAccessible(true);
		int midX = plan.width() / 2;
		int midZ = plan.depth() / 2;
		int[][] starts = {{midX, plan.roofY() - 4, midZ}, {1, 12, midZ}, {plan.width() - 2, 12, midZ}, {midX, 12, 1}, {midX, 12, plan.depth() - 2}};
		int teleports = 0;
		int outside = 0;
		StringBuilder report = new StringBuilder();
		for (int[] start : starts) {
			BlockPos perch = origin.offset(start[0], start[1], start[2]);
			tower.setBlock(perch, Blocks.STONE.defaultBlockState(), 2);
			tower.setBlock(perch.above(), Blocks.AIR.defaultBlockState(), 2);
			Shulker shulker = EntityTypes.SHULKER.create(tower, EntitySpawnReason.COMMAND);
			shulker.snapTo(perch.getX() + 0.5, perch.getY() + 1.0, perch.getZ() + 0.5);
			tower.addFreshEntity(shulker);
			int moved = 0;
			int left = 0;
			for (int i = 0; i < 160; i++) {
				if ((boolean) teleport.invoke(shulker)) {
					moved++;
					BlockPos at = shulker.blockPosition();
					boolean inside = at.getX() >= origin.getX() && at.getX() < origin.getX() + plan.width()
						&& at.getZ() >= origin.getZ() && at.getZ() < origin.getZ() + plan.depth()
						&& at.getY() >= TowerLayout.BASE_Y + plan.low() && at.getY() < TowerLayout.BASE_Y + plan.roofY();
					left += inside ? 0 : 1;
					if (!inside) {
						report.append(at.toShortString()).append(' ');
					}
				}
			}
			shulker.discard();
			teleports += moved;
			outside += left;
		}
		TowerTestSupport.result("shulkerTeleports", "teleports=" + teleports + " outside=" + outside + " " + report);
		helper.assertTrue(teleports >= 20, "the shulkers teleported only " + teleports + " times, so the case proves nothing");
		helper.assertTrue(outside == 0, outside + " of " + teleports + " shulker teleports left the floor's volume: " + report);
		helper.succeed();
	}
}
