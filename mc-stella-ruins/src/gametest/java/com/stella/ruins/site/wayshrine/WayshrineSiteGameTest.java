package com.stella.ruins.site.wayshrine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.Placement;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinClueItem;
import com.stella.ruins.world.RuinClues;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinPhase;
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
import java.util.concurrent.atomic.AtomicLong;
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
import net.minecraft.world.level.block.Block;
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

/** The registration, the data files, the clue for this site (item 11), and the lamp on the obelisk. */
public class WayshrineSiteGameTest {
	static ResourceKey<Structure> structureKey() {
		return ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("mod-stella-ruins", WayshrineSite.ID));
	}

	@GameTest
	public void theSiteIsRegisteredWithTheValuesOfThePlan(final GameTestHelper helper) {
		RuinSite site = RuinSites.get(WayshrineSite.ID);
		helper.assertTrue(site != null && site == WayshrineSite.site(), "the wayshrine is not in the registry");
		helper.assertTrue(site.size() == SiteSize.SMALL, "the size is " + site.size());
		helper.assertTrue(site.placement() instanceof Placement.Surface surface && surface.maxSlope() == 4 && site.placement().foundation() == 4, "the placement is " + site.placement());
		helper.assertTrue(site.sizeX() == 21 && site.sizeY() == 16 && site.sizeZ() == 21, "the bounds are " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(!site.claim().isOnce() && site.claim().days() == 1 && site.claim().resetTicks(WayshrineSite.ID) == Claim.DAY_TICKS, "the claim does not reset after one day");
		helper.assertTrue(site.activationRadius() == 16, "the activation radius is " + site.activationRadius());
		helper.assertTrue(site.clueWeight() == 3, "the clue weight is " + site.clueWeight());
		helper.assertTrue(site.vaultLoot().equals(WayshrineSite.VAULT_LOOT) && WayshrineSite.VAULT_LOOT.identifier().getPath().equals("chests/wayshrine"), "the vault table is " + site.vaultLoot());
		helper.assertTrue(site.roster().isEmpty(), "the site roster is not empty: the waves spawn the mobs");
		helper.succeed();
	}

	/** The numbers the plan names, pinned to their written values, so a constant that drifts fails here and not silently elsewhere. */
	@GameTest
	public void thePlanNumbersAreWhatShips(final GameTestHelper helper) {
		helper.assertTrue(WayshrineMobs.HEAL_PERIOD == 100 && WayshrineMobs.HEAL_RANGE == 6.0 && WayshrineMobs.HEAL_AMOUNT == 4.0F, "the pilgrim heals " + WayshrineMobs.HEAL_AMOUNT + " within " + WayshrineMobs.HEAL_RANGE + " every " + WayshrineMobs.HEAL_PERIOD);
		helper.assertTrue(WayshrineMobs.BLIND_TICKS == 40, "the wisp blinds for " + WayshrineMobs.BLIND_TICKS);
		helper.assertTrue(WayshrineMobs.SLOW_PERIOD == 160 && WayshrineMobs.SLOW_RANGE == 8.0 && WayshrineMobs.SLOW_TICKS == 100, "the acolyte slows for " + WayshrineMobs.SLOW_TICKS + " within " + WayshrineMobs.SLOW_RANGE + " every " + WayshrineMobs.SLOW_PERIOD);
		helper.assertTrue(ShrineRite.WATCH_RADIUS == 32 && ShrineRite.ABANDON_TICKS == 600L, "the watch is " + ShrineRite.WATCH_RADIUS + " blocks for " + ShrineRite.ABANDON_TICKS + " ticks");
		helper.assertTrue(WayshrineRewards.HEALTH_BONUS == 0.10 && WayshrineRewards.AURA_RANGE == 8.0 && WayshrineRewards.AURA_TICKS == 60 && WayshrineRewards.AURA_PERIOD == 20, "the set numbers drifted");
		helper.assertTrue(WayshrineRewards.CENSER_BELOW == 0.5F && WayshrineRewards.CENSER_TICK_HEAL == 1.0F && WayshrineRewards.CENSER_KILL_HEAL == 2.0F, "the censer numbers drifted");
		helper.assertTrue(WayshrineSite.waves().waves().size() == 3, "the shrine has " + WayshrineSite.waves().waves().size() + " waves");
		int[][] expected = {{4, 0, 0}, {3, 3, 0}, {2, 2, 1}};
		for (int wave = 0; wave < 3; wave++) {
			var roster = WayshrineSite.waves().waves().get(wave);
			int pilgrims = 0;
			int wisps = 0;
			int acolytes = 0;
			for (var entry : roster.entries()) {
				pilgrims += entry.kind() == WayshrineMobs.pilgrim() ? entry.count() : 0;
				wisps += entry.kind() == WayshrineMobs.wisp() ? entry.count() : 0;
				acolytes += entry.kind() == WayshrineMobs.acolyte() ? entry.count() : 0;
			}
			helper.assertTrue(pilgrims == expected[wave][0] && wisps == expected[wave][1] && acolytes == expected[wave][2], "wave " + (wave + 1) + " is " + pilgrims + "/" + wisps + "/" + acolytes);
		}
		helper.succeed();
	}

	@GameTest
	public void theStructureAndItsSetCarryTheSpreadTheSaltAndTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey());
		helper.assertTrue(structure.value() instanceof RuinStructure ruin && ruin.site().equals(WayshrineSite.ID), "the structure is not the wayshrine's");
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		StructureSet found = null;
		for (Holder.Reference<StructureSet> holder : level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().toList()) {
			if (holder.value().structures().stream().anyMatch(entry -> entry.structure().value() == structure.value())) {
				found = holder.value();
			}
		}
		helper.assertTrue(found != null, "no structure set holds the wayshrine");
		JsonObject placement = StructurePlacement.CODEC.encodeStart(ops, found.placement()).getOrThrow().getAsJsonObject();
		WayshrineTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 34 && placement.get("separation").getAsInt() == 12, "the spread is " + placement.get("spacing") + "/" + placement.get("separation"));
		helper.assertTrue(placement.get("salt").getAsInt() == 734120551, "the salt is " + placement.get("salt"));
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		helper.assertTrue(zone != null && "minecraft:villages".equals(zone.get("other_set").getAsString()) && zone.get("chunk_count").getAsInt() == 8, "the village exclusion is " + zone);
		helper.succeed();
	}

	@GameTest
	public void theMergedLangHoldsTheNamesTheMobsTheItemsAndTheRiddle(final GameTestHelper helper) throws Exception {
		JsonObject lang;
		try (InputStream in = WayshrineSite.class.getResourceAsStream("/assets/mod-stella-ruins/lang/en_us.json")) {
			helper.assertTrue(in != null, "the merged lang file is missing");
			lang = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		List<String> missing = new ArrayList<>();
		for (String key : new String[] {
			"site.mod-stella-ruins.wayshrine", "clue.mod-stella-ruins.wayshrine", "block.mod-stella-ruins.wayshrine_lamp",
			"item.mod-stella-ruins.shrine_shard", "item.mod-stella-ruins.pilgrim_ash", "item.mod-stella-ruins.wisp_light",
			"item.mod-stella-ruins.pilgrim_helmet", "item.mod-stella-ruins.pilgrim_chestplate", "item.mod-stella-ruins.pilgrim_leggings", "item.mod-stella-ruins.pilgrim_boots",
			"item.mod-stella-ruins.pilgrims_censer", "set.mod-stella-ruins.pilgrim", "effect.mod-stella-ruins.censer",
			"mob.mod-stella-ruins.hollow_pilgrim", "mob.mod-stella-ruins.lantern_wisp", "mob.mod-stella-ruins.fallen_acolyte"}) {
			if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "lang keys missing: " + missing);
		helper.succeed();
	}

	@GameTest
	public void theClueForTheWayshrineShowsItsRiddle(final GameTestHelper helper) {
		ItemStack clue = RuinClueItem.of(WayshrineSite.ID);
		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		clue.getItem().appendHoverText(clue, net.minecraft.world.item.Item.TooltipContext.EMPTY, net.minecraft.world.item.component.TooltipDisplay.DEFAULT, lines::add, net.minecraft.world.item.TooltipFlag.NORMAL);
		helper.assertTrue(lines.size() == 1 && lines.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents riddle && riddle.getKey().equals("clue.mod-stella-ruins.wayshrine"), "the tooltip is " + lines);
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

	@GameTest(maxTicks = 6000)
	public void usingAClueNearAPlacedWayshrineMapsToIt(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			ServerLevel level = arena.level();
			List<ChunkPos> forced = new ArrayList<>();
			RuinClues.Locator old = null;
			try {
				ServerPlayer player = arena.players().get(0);
				Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structureKey());
				var placement = (RandomSpreadStructurePlacement) level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).get(0);
				ChunkPos candidate = placement.getPotentialStructureChunk(level.getSeed(), player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
				for (int cx = candidate.x() - 3; cx <= candidate.x() + 3; cx++) {
					for (int cz = candidate.z() - 3; cz <= candidate.z() + 3; cz++) {
						if (level.setChunkForced(cx, cz, true)) {
							forced.add(new ChunkPos(cx, cz));
						}
					}
				}
				StructureStart start = generate(level, candidate);
				helper.assertTrue(start.isValid() && start.getPieces().get(0) instanceof RuinPiece, "the real wayshrine structure did not place at the planted chunk: the ground refused it");
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
				WayshrineSite.site().layout().draw(marks);
				BlockPos core = marks.markers().get(RuinMarkers.CORE).get(0);
				BlockPos locate = placement.getLocatePos(start.getChunkPos());
				helper.assertTrue(frame.site().equals(WayshrineSite.ID) && level.getBlockState(core).is(RuinBlocks.CORE), "the planted start is not a standing wayshrine");
				old = RuinClues.replaceLocator((where, siteId, from) -> siteId.equals(WayshrineSite.ID) ? java.util.Optional.of(locate) : java.util.Optional.empty());
				player.setItemInHand(InteractionHand.MAIN_HAND, RuinClueItem.of(WayshrineSite.ID));
				InteractionResult result = RuinClueItem.ITEM.use(level, player, InteractionHand.MAIN_HAND);
				ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
				helper.assertTrue(result.consumesAction() && held.is(Items.FILLED_MAP), "the clue became " + held.getItem());
				var decorations = held.get(DataComponents.MAP_DECORATIONS);
				helper.assertTrue(decorations != null && decorations.decorations().size() == 1, "the map has no single target decoration");
				var target = decorations.decorations().values().iterator().next();
				helper.assertTrue(target.type().equals(MapDecorationTypes.TARGET_X) && Math.abs(target.x() - locate.getX()) < 1.0 && Math.abs(target.z() - locate.getZ()) < 1.0, "the target is at " + target.x() + "," + target.z());
				helper.assertTrue(locate.getX() >= box.minX() - 16 && locate.getX() <= box.maxX() + 16 && locate.getZ() >= box.minZ() - 16 && locate.getZ() <= box.maxZ() + 16, "the target " + locate.toShortString() + " is not at the planted shrine " + box);
				WayshrineTestSupport.result("clueMap", "target=" + locate.toShortString() + " siteBox=" + box + " core=" + core.toShortString());
			} finally {
				if (old != null) {
					RuinClues.replaceLocator(old);
				}
				forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
			}
		});
	}

	private static boolean lit(final WayshrineTestSupport.Arena arena) {
		return arena.level().getBlockState(arena.lampPos()).getValue(WayshrineLamp.LIT);
	}

	@GameTest(maxTicks = 6000)
	public void theLampBurnsWhileArmedAndGoesDarkWhenTheShrineRestsThenRelights(final GameTestHelper helper) {
		WayshrineTestSupport.inArena(helper, WayshrinePlan.RING, arena -> {
			AtomicLong clock = new AtomicLong(13_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				arena.lamp().refresh(arena.level());
				helper.assertTrue(lit(arena) && arena.phase() == RuinPhase.DORMANT, "the lamp is dark before anyone came");
				helper.assertTrue(arena.lamp().core() != null && arena.lamp().core().equals(arena.corePos()), "the lamp found core " + arena.lamp().core() + ", not " + arena.corePos());
				ServerPlayer player = arena.wake();
				arena.lamp().refresh(arena.level());
				helper.assertTrue(lit(arena) && arena.phase() == RuinPhase.ACTIVE, "the lamp went dark while the rite runs");
				arena.offer(player, new ItemStack(Items.GOLD_INGOT, 2));
				arena.killRoster(player);
				arena.killRoster(player);
				arena.killRoster(player);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the rite did not complete: " + arena.phase());
				arena.lamp().refresh(arena.level());
				boolean darkWhileResting = !lit(arena);
				int light = arena.level().getBlockState(arena.lampPos()).getLightEmission();
				clock.addAndGet(Claim.DAY_TICKS);
				arena.leave(player);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the shrine did not rearm: " + arena.phase());
				arena.lamp().refresh(arena.level());
				boolean relit = lit(arena);
				WayshrineTestSupport.result("lamp", "darkWhileComplete=" + darkWhileResting + " lightWhileDark=" + light + " relitAfterRearm=" + relit);
				helper.assertTrue(darkWhileResting && light == 0, "the lamp still burned after the shrine paid out");
				helper.assertTrue(relit && arena.level().getBlockState(arena.lampPos()).getLightEmission() == 15, "the lamp did not relight when the shrine rearmed");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = 6000)
	public void theLampFollowsTheCoreOnItsOwnTickWithoutAnyCallFromTheTest(final GameTestHelper helper) {
		boolean[] dark = {false};
		WayshrineTestSupport.inArenaAfter(helper, Rotation.NONE, WayshrineTestSupport.seedOf(WayshrinePlan.SUNKEN), WayshrineLampEntity.PERIOD * 3, arena -> {
			ServerPlayer player = arena.wake();
			arena.offer(player, new ItemStack(Items.GOLD_INGOT, 2));
			arena.killRoster(player);
			arena.killRoster(player);
			arena.killRoster(player);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the rite did not complete: " + arena.phase());
			helper.assertTrue(lit(arena), "the lamp was dark before its own tick ran, so the test proves nothing");
		}, arena -> {
			dark[0] = !lit(arena);
			WayshrineTestSupport.result("lampOwnTick", "dark=" + dark[0] + " core=" + arena.lamp().core());
			helper.assertTrue(dark[0], "the lamp did not go dark by itself within " + WayshrineLampEntity.PERIOD * 3 + " ticks");
		});
	}

	@GameTest
	public void theLampBlockIsRegisteredAndCannotBeBroken(final GameTestHelper helper) {
		Block lamp = WayshrineLamp.LAMP;
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(lamp).equals(Identifier.fromNamespaceAndPath("mod-stella-ruins", "wayshrine_lamp")), "the lamp id is " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(lamp));
		helper.assertTrue(lamp.defaultBlockState().getValue(WayshrineLamp.LIT), "a new lamp is dark");
		helper.assertTrue(lamp.defaultBlockState().getDestroySpeed(helper.getLevel(), BlockPos.ZERO) < 0.0F, "the lamp can be broken");
		helper.succeed();
	}
}
