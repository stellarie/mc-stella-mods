package com.stella.ruins.site.meteorcrater;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinStructure;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** The site as the game sees it: registration, worldgen files, blocks, and the crater drawn through the real layout. */
public class CraterSiteGameTest {
	static final int SEEDS = 24;

	@GameTest
	public void theSiteRegistersAsASurfaceSiteWithAOnceClaimAndTheCoolingObjective(final GameTestHelper helper) {
		RuinSite site = CraterTestSupport.site();
		helper.assertTrue(site.sizeX() == 42 && site.sizeZ() == 42 && site.sizeY() == CraterPlan.SIZE_Y, "bounds " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(site.placement() instanceof Placement.Surface surface && surface.maxSlope() == CraterPlan.FOUNDATION, "placement " + site.placement());
		helper.assertTrue(site.placement().foundation() + site.sizeY() <= 48, "the box is " + (site.placement().foundation() + site.sizeY()) + " tall");
		helper.assertTrue(site.claim().isOnce() && site.claim().resetTicks(site.id()) == -1L, "the claim is not once");
		helper.assertTrue(site.activationRadius() == 20 && site.clueWeight() == 3, "radius " + site.activationRadius() + " clue " + site.clueWeight());
		helper.assertTrue(site.roster().isEmpty() && site.hasUnique() && site.vaultLoot().equals(CraterMobs.VAULT_LOOT), "roster, unique or vault table");
		helper.assertTrue(site.newObjective() instanceof CoolingObjective, "the objective is not the cooling objective");
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, StellaRuins.id("meteor_crater"));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals("meteor_crater"), "the structure does not name the site");
		ResourceKey<StructureSet> setKey = ResourceKey.create(Registries.STRUCTURE_SET, StellaRuins.id("meteor_crater"));
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(setKey).value();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow().getAsJsonObject();
		CraterTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 34 && placement.get("separation").getAsInt() == 12, "spread " + placement);
		helper.assertTrue(placement.get("salt").getAsInt() == 466102735, "salt " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue("minecraft:villages".equals(zone.get("other_set").getAsString()) && zone.get("chunk_count").getAsInt() >= 8, "exclusion " + zone);
		helper.assertTrue(set.structures().size() == 1 && set.structures().getFirst().structure().value() == structure.value(), "the set holds another structure");
		helper.succeed();
	}

	@GameTest
	public void theCraterStandsWholeOver24SeedsAndAllFourRotations(final GameTestHelper helper) {
		int drawn = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			for (Rotation rotation : Rotation.values()) {
				long frameSeed = seed * 1_000_003L + 17L;
				RuinSite site = CraterTestSupport.site();
				RuinFrame frame = new RuinFrame(site.id(), new BlockPos(1000, 70, 2000), rotation.ordinal(), frameSeed, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
				BoundingBox box = frame.box();
				Map<BlockPos, BlockState> sink = new HashMap<>();
				LayoutContext ctx = LayoutContext.forCapture(frame, sink, box);
				site.layout().draw(ctx);
				CraterPlan plan = CraterPlan.derive(RandomSource.create(frameSeed));
				String what = "seed " + seed + " " + rotation;
				for (BlockPos pos : sink.keySet()) {
					helper.assertTrue(box.isInside(pos), what + " wrote outside the box at " + pos);
				}
				int ore = 0;
				int meteor = 0;
				int scorched = 0;
				int fluids = 0;
				for (Map.Entry<BlockPos, BlockState> e : sink.entrySet()) {
					BlockState state = e.getValue();
					ore += state.is(MeteorBlocks.STARMETAL_ORE) ? 1 : 0;
					fluids += state.getFluidState().isEmpty() ? 0 : 1;
				}
				for (CraterPlan.Blob blob : plan.blob()) {
					BlockPos world = frame.toWorld(blob.spot().x(), blob.spot().y(), blob.spot().z());
					BlockState state = sink.get(world);
					helper.assertTrue(state != null && !state.isAir(), what + " the meteor block " + blob.spot() + " is missing");
					meteor++;
				}
				for (int x = 0; x < CraterPlan.SIZE_X; x++) {
					for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
						if (plan.affected(x, z) && plan.surface(x, z) != CraterPlan.Surface.GRASS) {
							BlockState top = sink.get(frame.toWorld(x, plan.top(x, z), z));
							helper.assertTrue(top != null && !top.is(Blocks.GRASS_BLOCK) && !top.isAir(), what + " the ring is missing at " + x + "," + z);
							scorched++;
						}
					}
				}
				helper.assertTrue(ore >= 8 && ore <= 16 && ore == plan.ore().size(), what + " ore " + ore);
				helper.assertTrue(meteor >= 30 && meteor <= 60, what + " meteor " + meteor);
				helper.assertTrue(scorched > 100, what + " scorched " + scorched);
				helper.assertTrue(fluids == 0, what + " fluid blocks " + fluids);
				CraterPlan.Spot core = plan.core();
				CraterPlan.Spot vault = plan.vault();
				helper.assertTrue(sink.get(frame.toWorld(core.x(), core.y(), core.z())).is(RuinBlocks.CORE), what + " no core");
				helper.assertTrue(sink.get(frame.toWorld(vault.x(), vault.y(), vault.z())).is(RuinBlocks.VAULT), what + " no vault");
				BlockState floor = sink.get(frame.toWorld(CraterPlan.CENTER + 1, plan.floorY(), CraterPlan.CENTER + 2));
				helper.assertTrue(floor != null && !floor.isAir(), what + " no floor");
				LayoutContext dry = LayoutContext.forDryRun(frame);
				site.layout().draw(dry);
				Map<String, List<BlockPos>> marks = dry.markers();
				helper.assertTrue(marks.get(CraterMarkers.ORE).size() == plan.ore().size(), what + " ore markers");
				helper.assertTrue(marks.get(CraterMarkers.GOLEM).size() == 3 && marks.get(CraterMarkers.SLIME).size() == 4, what + " mob markers");
				helper.assertTrue(marks.get(RuinMarkers.CORE).size() == 1 && marks.get(RuinMarkers.VAULT).size() == 1, what + " core or vault marker");
				drawn++;
			}
		}
		CraterTestSupport.result("standsWhole", "draws=" + drawn);
		helper.assertTrue(drawn == SEEDS * 4, "drew " + drawn);
		helper.succeed();
	}

	@GameTest
	public void everyMarkerOfARotatedCraterMatchesTheBlocksItNames(final GameTestHelper helper) {
		RuinSite site = CraterTestSupport.site();
		for (Rotation rotation : Rotation.values()) {
			RuinFrame frame = new RuinFrame(site.id(), new BlockPos(1000, 70, 2000), rotation.ordinal(), 99L, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
			Map<BlockPos, BlockState> sink = new HashMap<>();
			site.layout().draw(LayoutContext.forCapture(frame, sink, frame.box()));
			LayoutContext dry = LayoutContext.forDryRun(frame);
			site.layout().draw(dry);
			for (BlockPos ore : dry.markers().get(CraterMarkers.ORE)) {
				helper.assertTrue(sink.get(ore) != null && sink.get(ore).is(MeteorBlocks.STARMETAL_ORE), rotation + " ore marker " + ore + " is " + sink.get(ore));
			}
			for (BlockPos spot : dry.markers().get(CraterMarkers.GOLEM)) {
				helper.assertTrue(sink.get(spot) == null || sink.get(spot).isAir(), rotation + " golem marker " + spot + " is in " + sink.get(spot));
				helper.assertTrue(sink.get(spot.below()) != null && !sink.get(spot.below()).isAir(), rotation + " golem marker " + spot + " floats");
			}
			BlockPos notch = dry.markers().get(CraterMarkers.NOTCH).get(0);
			helper.assertTrue(sink.get(notch) != null && sink.get(notch).isAir(), rotation + " notch " + sink.get(notch));
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 6000)
	public void theBowlHoldsNoWaterEvenWhenTheOldGroundDid(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CraterTestSupport.ArenaBody body = arena -> {
			// The arena is already drawn. Pour water into the bowl, redraw, and look for any fluid left in a column the crater owns.
			CraterPlan plan = arena.plan();
			RuinFrame frame = arena.frame();
			for (int x = 17; x <= 25; x++) {
				for (int z = 17; z <= 25; z++) {
					for (int y = plan.floorY() - 4; y <= CraterPlan.GROUND + 4; y++) {
						BlockPos pos = frame.toWorld(x, y, z);
						if (!pos.equals(arena.corePos())) {
							level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
						}
					}
				}
			}
			arena.piece().build(level, frame.box());
			int fluids = 0;
			for (int x = 0; x < CraterPlan.SIZE_X; x++) {
				for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
					if (!plan.affected(x, z)) {
						continue;
					}
					for (int y = CraterPlan.MIN_Y; y < CraterPlan.SIZE_Y; y++) {
						fluids += level.getFluidState(frame.toWorld(x, y, z)).isEmpty() ? 0 : 1;
					}
				}
			}
			CraterTestSupport.result("noWater", "fluidBlocks=" + fluids);
			helper.assertTrue(fluids == 0, fluids + " fluid blocks remain in the crater's columns");
		};
		CraterTestSupport.inArena(helper, 4242L, body);
	}

	@GameTest(maxTicks = 6000)
	public void theLocalGroundRowIsTheBlockUnderTheFloorOfTheFrame(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, 4343L, arena -> {
			ServerLevel level = arena.level();
			RuinFrame frame = arena.frame();
			CraterPlan plan = arena.plan();
			Placement.Probe probe = Placement.Probe.of(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState());
			// The framework floor is one block over the highest ground of the footprint, so local y -1 is the reference ground.
			int highest = Integer.MIN_VALUE;
			for (int x = 0; x < CraterPlan.SIZE_X; x += 4) {
				for (int z = 0; z < CraterPlan.SIZE_Z; z += 4) {
					BlockPos world = frame.toWorld(x, 0, z);
					highest = Math.max(highest, probe.groundY(world.getX(), world.getZ()));
				}
			}
			helper.assertTrue(frame.min().getY() - 1 >= highest, "the frame floor " + frame.min().getY() + " is under the ground " + highest);
			helper.assertTrue(frame.toWorld(0, CraterPlan.GROUND, 0).getY() == frame.min().getY() - 1, "local ground row is not the block under the floor");
			// A column the plan never touches keeps the world's own ground: nothing of the crater reaches it.
			int untouched = 0;
			for (int x = 1; x < CraterPlan.SIZE_X - 1; x++) {
				for (int z = 1; z < CraterPlan.SIZE_Z - 1; z++) {
					if (!plan.affected(x, z) && (x < 3 || z > CraterPlan.SIZE_Z - 4)) {
						BlockPos world = frame.toWorld(x, CraterPlan.GROUND + 1, z);
						helper.assertTrue(level.getBlockState(world).isAir() || !level.getBlockState(world).is(Blocks.GRASS_BLOCK), "an untouched column " + x + "," + z + " changed");
						untouched++;
					}
				}
			}
			helper.assertTrue(untouched > 0, "no untouched column to check");
			// The deepest bowl floor stays inside the box, over the bottom row of the foundation.
			helper.assertTrue(frame.toWorld(CraterPlan.CENTER, plan.floorY(), CraterPlan.CENTER).getY() >= frame.box().minY(), "the floor is under the box");
			BlockState floor = level.getBlockState(frame.toWorld(CraterPlan.CENTER + 1, plan.floorY(), CraterPlan.CENTER + 2));
			helper.assertTrue(!floor.isAir(), "no floor block at the floor row");
			CraterTestSupport.result("groundRow", "floorY=" + frame.min().getY() + " highestGround=" + highest + " bowlFloor=" + plan.floorY() + " untouchedColumns=" + untouched);
		});
	}

	@GameTest
	public void hotOreGlowsAtLightTenBurnsOnStepAndHasNoItem(final GameTestHelper helper) {
		BlockState hot = MeteorBlocks.STARMETAL_ORE.defaultBlockState();
		helper.assertTrue(hot.getLightEmission() == MeteorBlocks.ORE_LIGHT && MeteorBlocks.ORE_LIGHT == 10, "light " + hot.getLightEmission());
		helper.assertTrue(MeteorBlocks.COOLED_METEORITE.defaultBlockState().getLightEmission() == 0, "the cooled block glows");
		for (var block : new net.minecraft.world.level.block.Block[] {MeteorBlocks.STARMETAL_ORE, MeteorBlocks.COOLED_METEORITE}) {
			helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(BuiltInRegistries.BLOCK.getKey(block)), BuiltInRegistries.BLOCK.getKey(block) + " has an item");
		}
		ServerLevel level = helper.getLevel();
		Cow cow = CraterTestSupport.cow(helper, 3.5, 3.5, 20.0F);
		float before = cow.getHealth();
		MeteorBlocks.STARMETAL_ORE.stepOn(level, helper.absolutePos(new BlockPos(3, 1, 3)), hot, cow);
		helper.assertTrue(Math.abs(before - cow.getHealth() - MeteorBlocks.BURN) < 1.0E-4F, "a step did " + (before - cow.getHealth()));
		float second = cow.getHealth();
		MeteorBlocks.COOLED_METEORITE.stepOn(level, helper.absolutePos(new BlockPos(3, 1, 3)), MeteorBlocks.COOLED_METEORITE.defaultBlockState(), cow);
		helper.assertTrue(cow.getHealth() == second, "cooled meteorite burned");
		cow.discard();
		CraterTestSupport.result("hotOre", "light=" + hot.getLightEmission() + " burn=" + (before - second));
		helper.succeed();
	}

	@GameTest
	public void miningHotOreDropsOneOrTwoRawStarmetalAndCooledMeteoriteDropsNothingExclusive(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		boolean one = false;
		boolean two = false;
		for (int i = 0; i < 300; i++) {
			List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(MeteorBlocks.STARMETAL_ORE.defaultBlockState(), level, pos, null);
			helper.assertTrue(drops.size() == 1 && drops.getFirst().is(CraterItems.RAW_STARMETAL), "hot ore dropped " + drops);
			int count = drops.getFirst().getCount();
			helper.assertTrue(count == 1 || count == 2, "count " + count);
			one |= count == 1;
			two |= count == 2;
			for (ItemStack stack : net.minecraft.world.level.block.Block.getDrops(MeteorBlocks.COOLED_METEORITE.defaultBlockState(), level, pos, null)) {
				helper.assertTrue(!com.stella.ruins.world.Exclusives.isExclusive(stack), "cooled meteorite dropped the exclusive " + stack);
			}
		}
		helper.assertTrue(one && two, "the counts did not cover 1 and 2");
		helper.succeed();
	}

	@GameTest
	public void fireOnMagmaIsInfiniburnAndTheTreesAreCleared(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Blocks.MAGMA_BLOCK.defaultBlockState().is(level.dimensionType().infiniburn()), "magma does not keep a fire alive in the overworld");
		Set<Boolean> seen = new HashSet<>();
		for (int seed = 0; seed < SEEDS; seed++) {
			CraterPlan plan = CraterPlan.derive(RandomSource.create(seed * 31L));
			for (CraterPlan.Fire fire : plan.fires()) {
				seen.add(fire.campfire());
				for (CraterPlan.Spot cell : fire.cells()) {
					BlockState base = plan.stateAt(cell.x(), cell.y(), cell.z());
					BlockState above = plan.stateAt(cell.x(), cell.y() + 1, cell.z());
					if (fire.campfire()) {
						helper.assertTrue(above != null && above.is(Blocks.HAY_BLOCK), "a campfire has no hay under it");
						BlockState camp = plan.stateAt(cell.x(), cell.y() + 2, cell.z());
						helper.assertTrue(camp != null && camp.is(Blocks.CAMPFIRE), "no campfire");
					} else {
						helper.assertTrue(base != null && base.is(Blocks.MAGMA_BLOCK) && above != null && above.is(Blocks.FIRE), "a fire patch is " + base + " / " + above);
					}
				}
			}
		}
		helper.assertTrue(seen.size() == 2, "the 24 seeds did not draw both a fire patch and a campfire");
		helper.succeed();
	}

	@GameTest
	public void everyChunkOfTheCraterDrawsTheSamePlanAsOneBox(final GameTestHelper helper) {
		RuinSite site = CraterTestSupport.site();
		long totalNanos = 0L;
		int draws = 0;
		for (Rotation rotation : new Rotation[] {Rotation.NONE, Rotation.CLOCKWISE_90}) {
			for (long seed = 1; seed <= 4; seed++) {
				RuinFrame frame = new RuinFrame(site.id(), new BlockPos(1003, 70, 2005), rotation.ordinal(), seed * 77L, site.sizeX(), site.sizeY(), site.sizeZ(), site.placement().foundation());
				BoundingBox box = frame.box();
				Map<BlockPos, BlockState> whole = new HashMap<>();
				long begun = System.nanoTime();
				site.layout().draw(LayoutContext.forCapture(frame, whole, box));
				totalNanos += System.nanoTime() - begun;
				draws++;
				Map<BlockPos, BlockState> pieces = new HashMap<>();
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
					for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
						BoundingBox chunk = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
						site.layout().draw(LayoutContext.forCapture(frame, pieces, chunk));
					}
				}
				helper.assertTrue(whole.equals(pieces), rotation + " seed " + seed + ": the chunk-by-chunk draw differs from the one-box draw (" + whole.size() + " vs " + pieces.size() + ")");
			}
		}
		double meanMs = totalNanos / (double) draws / 1.0E6;
		CraterTestSupport.result("chunkDraw", "draws=" + draws + " meanMsPerWholeCrater=" + meanMs + " budgetMsPerChunk=50");
		helper.assertTrue(meanMs <= 50.0 * 16.0, "a whole crater takes " + meanMs + " ms");
		helper.succeed();
	}

	/** A stub ground: flat at a height, with one bump and one lake. */
	private static Placement.Probe stub(final int base, final int bump, final boolean lake) {
		return new Placement.Probe() {
			@Override
			public int groundY(final int x, final int z) {
				return x == 12 && z == 12 ? base + bump : base;
			}

			@Override
			public int surfaceY(final int x, final int z) {
				return lake && x == 20 && z == 20 ? base + 1 : groundY(x, z);
			}

			@Override
			public BlockState stateAt(final int x, final int y, final int z) {
				return Blocks.STONE.defaultBlockState();
			}

			@Override
			public int minY() {
				return -64;
			}

			@Override
			public int maxY() {
				return 320;
			}
		};
	}

	@GameTest
	public void placementPutsTheFloorOneBlockOverTheHighestGroundAndRefusesSteepGroundAndLakes(final GameTestHelper helper) {
		Placement placement = CraterTestSupport.site().placement();
		RandomSource random = RandomSource.create(1L);
		var flat = placement.choose(stub(64, 0, false), random, 0, 0, 42, CraterPlan.SIZE_Y, 42);
		helper.assertTrue(flat.isPresent() && flat.get().getY() == 65, "flat ground gave " + flat);
		var bumpy = placement.choose(stub(64, 17, false), random, 0, 0, 42, CraterPlan.SIZE_Y, 42);
		helper.assertTrue(bumpy.isPresent() && bumpy.get().getY() == 64 + 17 + 1, "a 17 block bump gave " + bumpy);
		helper.assertTrue(placement.choose(stub(64, 18, false), random, 0, 0, 42, CraterPlan.SIZE_Y, 42).isEmpty(), "an 18 block bump was accepted");
		helper.assertTrue(placement.choose(stub(64, 0, true), random, 0, 0, 42, CraterPlan.SIZE_Y, 42).isEmpty(), "a lake in the footprint was accepted");
		helper.succeed();
	}
}
