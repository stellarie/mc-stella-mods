package com.stella.ruins.site.provinggrounds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinClueItem;
import com.stella.ruins.world.RuinClues;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinPiece;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinSites;
import com.stella.ruins.world.RuinStructure;
import com.stella.ruins.world.SiteSize;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/** The registration, the data files, the plan's numbers, and the clue for this site (item 11 of the framework). */
public class ProvingSiteGameTest {
	static ResourceKey<Structure> structureKey() {
		return ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("mod-stella-ruins", ProvingGroundsSite.ID));
	}

	@GameTest
	public void theSiteIsRegisteredWithTheValuesOfThePlan(final GameTestHelper helper) {
		RuinSite site = RuinSites.get(ProvingGroundsSite.ID);
		helper.assertTrue(site != null && site == ProvingGroundsSite.site(), "the proving grounds are not in the registry");
		helper.assertTrue(site.size() == SiteSize.LARGE, "the size is " + site.size());
		helper.assertTrue(site.placement() instanceof Placement.Surface surface && surface.maxSlope() == 5 && site.placement().foundation() == 5, "the placement is " + site.placement());
		helper.assertTrue(site.sizeX() == 48 && site.sizeY() == 20 && site.sizeZ() == 48, "the bounds are " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(!site.claim().isOnce() && site.claim().days() == 1 && site.claim().resetTicks(ProvingGroundsSite.ID) == Claim.DAY_TICKS, "the claim does not reset after one day");
		helper.assertTrue(site.activationRadius() == 24, "the activation radius is " + site.activationRadius());
		helper.assertTrue(site.clueWeight() == 2, "the clue weight is " + site.clueWeight());
		helper.assertTrue(site.vaultLoot().equals(ProvingGroundsSite.vaultTable(1)) && ProvingGroundsSite.vaultTable(3).identifier().getPath().equals("chests/proving_grounds_3"), "the vault table is " + site.vaultLoot());
		helper.assertTrue(site.roster().isEmpty(), "the site roster is not empty: the tiers spawn the mobs");
		helper.assertTrue(site.newObjective() instanceof TierObjective, "the objective is " + site.newObjective());
		helper.succeed();
	}

	/** The numbers the plan names, pinned to their written values, so a constant that drifts fails here and not silently elsewhere. */
	@GameTest
	public void thePlanNumbersAreWhatShips(final GameTestHelper helper) {
		helper.assertTrue(GroundsMobs.GLADIATOR_SCALE == 1.05F && GroundsMobs.HOUND_HEALTH == 2.0 && GroundsMobs.BEAST_HEALTH == 3.0, "the mob numbers drifted");
		helper.assertTrue(GroundsMobs.NET_TICKS == 40 && GroundsMobs.NET_AMPLIFIER == 1, "the net slows for " + GroundsMobs.NET_TICKS + " at amplifier " + GroundsMobs.NET_AMPLIFIER);
		helper.assertTrue(TierObjective.LEAVE_TICKS == 600L && TierObjective.BOUNDS_MARGIN == 2 && TierObjective.WAVE_PAUSE_TICKS == 100L, "the run numbers drifted");
		helper.assertTrue(TierObjective.GRATE_OPEN_TICKS == 60L && TierObjective.GRATE_PERIOD_TICKS == 400L && TierObjective.GRATE_TIER == 3 && TierObjective.BRAZIER_TIER == 2, "the hazard numbers drifted");
		helper.assertTrue(GroundsRewards.DAMAGE_BONUS == 0.10 && GroundsRewards.SPEED_BONUS == 0.10 && GroundsRewards.KILL_STRENGTH_TICKS == 100, "the set numbers drifted");
		helper.assertTrue(GroundsRewards.CROWD_STEP == 0.06 && GroundsRewards.CROWD_MAX == 5 && GroundsRewards.CROWD_RESET_TICKS == 60, "the gladius numbers drifted");
		helper.assertTrue(TierObjective.waveCount(1) == 5 && TierObjective.waveCount(2) == 7 && TierObjective.waveCount(3) == 10, "the wave counts drifted");
		int[][][] expected = ProvingGroundsSite.WAVES;
		for (int tier = 1; tier <= 3; tier++) {
			for (int wave = 0; wave < expected[tier - 1].length; wave++) {
				var roster = ProvingGroundsSite.waves(tier).waves().get(wave);
				int[] seen = new int[4];
				for (var entry : roster.entries()) {
					seen[entry.kind() == GroundsMobs.gladiator() ? 0 : entry.kind() == GroundsMobs.retiarius() ? 1 : entry.kind() == GroundsMobs.hound() ? 2 : 3] += entry.count();
				}
				helper.assertTrue(java.util.Arrays.equals(seen, expected[tier - 1][wave]), "tier " + tier + " wave " + (wave + 1) + " is " + java.util.Arrays.toString(seen));
			}
		}
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadTheSaltAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey());
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals(ProvingGroundsSite.ID), "the structure is not the proving grounds'");
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		StructureSet found = com.stella.ruins.world.RuinSitePlanGameTest.sites(level);
		helper.assertTrue(found != null, "no sites structure set");
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, found.placement()).getOrThrow().getAsJsonObject();
		ProvingTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 12 && placement.get("separation").getAsInt() == 9, "the spread is " + placement.get("spacing") + "/" + placement.get("separation"));
		helper.assertTrue(placement.get("salt").getAsInt() == 702913845, "the salt is " + placement.get("salt"));
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.excludesVillages(placement), "the village exclusion is " + placement);
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.theSitesSetHolds(level, ProvingGroundsSite.ID), "the one ruin set does not hold the proving grounds");
		helper.succeed();
	}

	@GameTest
	public void theMergedLangHoldsTheNamesTheMobsTheItemsTheMessagesAndTheRiddle(final GameTestHelper helper) throws Exception {
		JsonObject lang;
		try (InputStream in = ProvingGroundsSite.class.getResourceAsStream("/assets/mod-stella-ruins/lang/en_us.json")) {
			helper.assertTrue(in != null, "the merged lang file is missing");
			lang = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		List<String> missing = new ArrayList<>();
		for (String key : new String[] {
			"site.mod-stella-ruins.proving_grounds", "clue.mod-stella-ruins.proving_grounds",
			"item.mod-stella-ruins.champion_token", "item.mod-stella-ruins.net_fiber", "item.mod-stella-ruins.hound_fang",
			"item.mod-stella-ruins.champion_helmet", "item.mod-stella-ruins.champion_chestplate", "item.mod-stella-ruins.champion_leggings", "item.mod-stella-ruins.champion_boots",
			"item.mod-stella-ruins.gladius_of_the_crowd", "set.mod-stella-ruins.champion", "effect.mod-stella-ruins.crowd",
			"mob.mod-stella-ruins.gladiator", "mob.mod-stella-ruins.retiarius", "mob.mod-stella-ruins.arena_hound", "mob.mod-stella-ruins.pit_beast",
			"message.mod-stella-ruins.proving_grounds.sleeping", "message.mod-stella-ruins.proving_grounds.armed", "message.mod-stella-ruins.proving_grounds.not_armed",
			"message.mod-stella-ruins.proving_grounds.claimed", "message.mod-stella-ruins.proving_grounds.busy", "message.mod-stella-ruins.proving_grounds.started",
			"message.mod-stella-ruins.proving_grounds.won", "message.mod-stella-ruins.proving_grounds.lost"}) {
			if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "lang keys missing: " + missing);
		helper.succeed();
	}

	@GameTest
	public void theClueForTheProvingGroundsShowsItsRiddle(final GameTestHelper helper) {
		ItemStack clue = RuinClueItem.of(ProvingGroundsSite.ID);
		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		clue.getItem().appendHoverText(clue, net.minecraft.world.item.Item.TooltipContext.EMPTY, net.minecraft.world.item.component.TooltipDisplay.DEFAULT, lines::add, net.minecraft.world.item.TooltipFlag.NORMAL);
		helper.assertTrue(lines.size() == 1 && lines.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents riddle && riddle.getKey().equals("clue.mod-stella-ruins.proving_grounds"), "the tooltip is " + lines);
		helper.succeed();
	}

	/** Runs the structure's own generation for one chunk, as worldgen would. */
	private static StructureStart generate(final ServerLevel level, final ChunkPos chunk) {
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey());
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		return holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(), randomState.createClimateSampler(SamplerContext.builder().enableCaches().build()), randomState,
			level.getServer().getStructureTemplateManager(), level.getSeed(), chunk, 0, level, biome -> true);
	}

	@GameTest(maxTicks = 400)
	public void usingAClueNearAPlacedProvingGroundsMapsToIt(final GameTestHelper helper) {
		{
			ServerLevel level = helper.getLevel();
			List<ChunkPos> forced = new ArrayList<>();
			RuinClues.Locator old = null;
			ServerPlayer player = ProvingTestSupport.join(helper, new net.minecraft.world.phys.Vec3(1.5, 2.0, 1.5));
			try {
				Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey());
				var placement = (RandomSpreadStructurePlacement) level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).get(0);
				ChunkPos candidate = placement.getPotentialStructureChunk(level.getSeed(), player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
				for (int cx = candidate.x() - 4; cx <= candidate.x() + 4; cx++) {
					for (int cz = candidate.z() - 4; cz <= candidate.z() + 4; cz++) {
						if (level.setChunkForced(cx, cz, true)) {
							forced.add(new ChunkPos(cx, cz));
						}
					}
				}
				StructureStart start = generate(level, candidate);
				helper.assertTrue(start.isValid() && start.getPieces().get(0) instanceof RuinPiece, "the real proving grounds structure did not place at the planted chunk: the ground refused it");
				level.getChunk(candidate.x(), candidate.z(), net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS).setStartForStructure(holder.value(), start);
				level.structureManager().addReference(start);
				var box = start.getBoundingBox();
				for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
					for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
						var chunkBox = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
						start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(1L), chunkBox, new ChunkPos(cx, cz));
					}
				}
				RuinFrame frame = ((RuinPiece) start.getPieces().get(0)).frame();
				LayoutContext marks = LayoutContext.forDryRun(frame);
				ProvingGroundsSite.site().layout().draw(marks);
				BlockPos core = marks.markers().get(RuinMarkers.CORE).get(0);
				BlockPos locate = placement.getLocatePos(start.getChunkPos());
				helper.assertTrue(frame.site().equals(ProvingGroundsSite.ID) && level.getBlockState(core).is(RuinBlocks.CORE), "the planted start is not a standing arena");
				old = RuinClues.replaceLocator((where, siteId, from) -> siteId.equals(ProvingGroundsSite.ID) ? java.util.Optional.of(locate) : java.util.Optional.empty());
				player.setItemInHand(InteractionHand.MAIN_HAND, RuinClueItem.of(ProvingGroundsSite.ID));
				InteractionResult result = RuinClueItem.ITEM.use(level, player, InteractionHand.MAIN_HAND);
				ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
				helper.assertTrue(result.consumesAction() && held.is(Items.FILLED_MAP), "the clue became " + held.getItem());
				var decorations = held.get(DataComponents.MAP_DECORATIONS);
				helper.assertTrue(decorations != null && decorations.decorations().size() == 1, "the map has no single target decoration");
				var target = decorations.decorations().values().iterator().next();
				helper.assertTrue(target.type().equals(MapDecorationTypes.TARGET_X) && Math.abs(target.x() - locate.getX()) < 1.0 && Math.abs(target.z() - locate.getZ()) < 1.0, "the target is at " + target.x() + "," + target.z());
				helper.assertTrue(locate.getX() >= box.minX() - 16 && locate.getX() <= box.maxX() + 16 && locate.getZ() >= box.minZ() - 16 && locate.getZ() <= box.maxZ() + 16, "the target " + locate.toShortString() + " is not at the planted arena " + box);
				ProvingTestSupport.result("clueMap", "target=" + locate.toShortString() + " siteBox=" + box + " core=" + core.toShortString());
			} finally {
				if (old != null) {
					RuinClues.replaceLocator(old);
				}
				forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
				ProvingTestSupport.leave(helper, player);
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 6000)
	public void aRotatedArenaWorksTheSameFromTheGongToTheTierStart(final GameTestHelper helper) {
		ProvingTestSupport.inArena(helper, Rotation.CLOCKWISE_90, 11L, arena -> {
			ServerPlayer player = arena.wake();
			TierObjective.Result result = arena.start(player, 1);
			helper.assertTrue(result == TierObjective.Result.STARTED && java.util.Arrays.equals(arena.mix(), ProvingGroundsSite.WAVES[0][0]), "a turned arena gave " + result + " with " + java.util.Arrays.toString(arena.mix()));
			helper.assertTrue(arena.portcullisBars() == 21, "the turned portcullis has " + arena.portcullisBars() + " bars");
		});
	}
}
