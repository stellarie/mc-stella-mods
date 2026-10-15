package com.stella.tower;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * The lost castle set's rate in play: the spacing and separation the data ships, the sites the game's own
 * spread arithmetic yields over a fixed region, and the villages exclusion that keeps a castle clear of a
 * village. The spacing lives in the data file and not in the set's codec, so the values are read from the
 * file the jar carries and are bounded to the constants in this class.
 */
public class TowerLostRoomStructureGameTest {
	/** The spacing a lost castle set is placed with: one attempt every this many chunks. */
	public static final int SPACING = 29;
	/** The least distance two castle attempts keep, under the spacing at separation 8. */
	public static final int SEPARATION = 8;
	/** The spacing before the rate rose 1.5 times: 36 to 29. Kept only to measure both in one run. */
	public static final int OLD_SPACING = 36;
	/** The villages a castle must keep clear of: the shipped exclusion zone. */
	public static final String EXCLUDED_SET = "minecraft:villages";
	/** The chunks the exclusion reaches, so a castle keeps more than a footprint from a village. */
	public static final int EXCLUDED_CHUNKS = 8;
	/** The set's region, big enough that a count over it differs from the arithmetic by under a percent. */
	public static final int REGION_CHUNKS = 200;
	/** The data file the set ships in, the only place the spacing is written down. */
	static final String DATA = "/data/mod-stella-tower/worldgen/structure_set/lost_rooms.json";

	/** The placement as the data file writes it, as JSON: the shipped values, not a copy of them. */
	static JsonObject shippedPlacement(final GameTestHelper helper) {
		InputStream stream = TowerLostRoomStructureGameTest.class.getResourceAsStream(DATA);
		helper.assertTrue(stream != null, "the lost_rooms data file is not on the classpath: " + DATA);
		return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("placement");
	}

	/** How many chunks of a square region the game's own spread arithmetic gives this placement. */
	static int sites(final int spacing, final int separation, final int salt, final int region) {
		RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(spacing, separation, RandomSpreadType.LINEAR, salt);
		int found = 0;
		for (int cx = 0; cx < region; cx++) {
			for (int cz = 0; cz < region; cz++) {
				ChunkPos at = placement.getPotentialStructureChunk(salt, cx, cz);
				found += at.x() == cx && at.z() == cz ? 1 : 0;
			}
		}
		return found;
	}

	@GameTest(maxTicks = 20)
	public void theCastlesShipTheRateTheyMeasured(final GameTestHelper helper) {
		JsonObject placement = shippedPlacement(helper);
		int spacing = placement.get("spacing").getAsInt();
		int separation = placement.get("separation").getAsInt();
		TowerTestSupport.result("lostRoomPlacement", "spacing=" + spacing + "/" + SPACING + " separation=" + separation + "/" + SEPARATION
			+ " exclusion=" + placement.getAsJsonObject("exclusion_zone"));
		helper.assertTrue(spacing == SPACING && separation == SEPARATION,
			"the lost_rooms set ships spacing " + spacing + " and separation " + separation + ", not " + SPACING + " and " + SEPARATION);
		helper.assertTrue(separation < spacing, "the separation " + separation + " does not stay under the spacing " + spacing);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void moreCastlesStandPerThousandChunks(final GameTestHelper helper) {
		JsonObject placement = shippedPlacement(helper);
		int salt = placement.get("salt").getAsInt();
		RandomSpreadStructurePlacement shipped = new RandomSpreadStructurePlacement(SPACING, SEPARATION, RandomSpreadType.LINEAR, salt);
		int chunks = REGION_CHUNKS * REGION_CHUNKS;
		int before = sites(OLD_SPACING, SEPARATION, salt, REGION_CHUNKS);
		int after = sites(SPACING, SEPARATION, salt, REGION_CHUNKS);
		int expected = chunks * (SPACING - 2 * SEPARATION) / (SPACING * SPACING);
		TowerTestSupport.result("lostRoomRate", "region=" + chunks + "chunks oldSpacing=" + OLD_SPACING + " sites=" + before
			+ " oldPer10k=" + String.format(java.util.Locale.ROOT, "%.2f", before * 10_000.0 / chunks) + " spacing=" + SPACING
			+ " sites=" + after + " per10k=" + String.format(java.util.Locale.ROOT, "%.2f", after * 10_000.0 / chunks)
			+ " ratio=" + String.format(java.util.Locale.ROOT, "%.3f", (double) after / before) + " sampledAt" + SPACING + "=" + expected
			+ " shippedSpacing=" + shipped.spacing());
		helper.assertTrue(shipped.spacing() == SPACING && shipped.separation() == SEPARATION,
			"the measured set is not the shipped " + SPACING + "/" + SEPARATION);
		helper.assertTrue(after > before, "the castles are not more over the same region: " + before + " at spacing " + OLD_SPACING
			+ ", " + after + " at spacing " + SPACING);
		helper.assertTrue(after <= 2 * expected, "the castle rate is far above its spacing's share: " + after + " sites over " + chunks
			+ " chunks, where " + expected + " is the sampled share at spacing " + SPACING);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void theCastlesKeepTheirVillagesExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<StructureSet> key = ResourceKey.create(Registries.STRUCTURE_SET, StellaTower.id("lost_rooms"));
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(key);
		helper.assertTrue(set != null, "no lost_rooms structure set");
		JsonObject json = StructureSet.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), set).getOrThrow().getAsJsonObject();
		boolean kept = set.placement() instanceof RandomSpreadStructurePlacement spread && spread.spacing() == SPACING && spread.separation() == SEPARATION;
		String zone = json.getAsJsonObject("placement").getAsJsonObject("exclusion_zone").toString();
		TowerTestSupport.result("lostRoomExclusion", "loadedPlacement=" + set.placement().getClass().getSimpleName() + " spacing="
			+ (set.placement() instanceof RandomSpreadStructurePlacement spread ? spread.spacing() + "/" + spread.separation() : "none")
			+ " zone=" + zone);
		helper.assertTrue(kept, "the loaded set is not the shipped spacing " + SPACING + " and separation " + SEPARATION);
		helper.assertTrue(zone.contains(EXCLUDED_SET) && zone.contains(String.valueOf(EXCLUDED_CHUNKS)),
			"the loaded set does not keep " + EXCLUDED_CHUNKS + " chunks from " + EXCLUDED_SET + ": " + zone);
		helper.succeed();
	}
}
