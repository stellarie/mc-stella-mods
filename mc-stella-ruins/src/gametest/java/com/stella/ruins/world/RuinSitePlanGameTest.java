package com.stella.ruins.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Item 2 and item 3 of the structure notes: no ruin site stands inside another's footprint, and the crater thinned.
 * <p>
 * Every ruin structure shares one structure set, so one grid places the whole family. {@code separation} is then the
 * whole guarantee: two sites of one set can never fall closer than that many chunks on each axis, whatever their
 * kinds, so a pair of footprints narrower than the separation cannot both hold blocks. The guards read what ships.
 */
public class RuinSitePlanGameTest {
	/** The one set every ruin structure belongs to. */
	public static final String SITES = "sites";
	/** The seed and the chunk region every measurement uses. */
	static final long SEED = 12345L;
	static final int MIN_CHUNK = -64;
	static final int MAX_CHUNK = 79;
	/** The widest pair of footprints the ruin sites can bring to one pair of chunks, in chunks. */
	static final int WIDEST_PAIR_SPAN = 8;
	/** The structure count and the grid main shipped before the merge, for the rate the crater has to beat. */
	static final int SITES_BEFORE = 11;
	static final int SPACING_BEFORE = 34;
	static final int SITE_COUNT = 11;

	/** One ruin structure of the set, with the site its structure names. */
	record Plan(String id, RuinSite site, int weight, RandomSpreadStructurePlacement placement) {
	}

	static ResourceKey<StructureSet> sitesKey() {
		return ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, SITES));
	}

	/** The one ruin structure set, or null when the merge did not land. */
	public static StructureSet sites(final ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(sitesKey()).map(Holder::value).orElse(null);
	}

	/** Every ruin structure of the set, with the site it names. */
	static List<Plan> plans(final ServerLevel level) {
		StructureSet set = sites(level);
		List<Plan> plans = new ArrayList<>();
		if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
			return plans;
		}
		for (StructureSet.StructureSelectionEntry entry : set.structures()) {
			if (!(entry.structure().value() instanceof RuinStructure ruin)) {
				continue;
			}
			RuinSite site = RuinSites.get(ruin.site());
			if (site != null) {
				plans.add(new Plan(ruin.site(), site, entry.weight(), spread));
			}
		}
		return plans;
	}

	/** Every structure set this mod ships, so the merge itself is checked. */
	static List<String> ruinSetIds(final ServerLevel level) {
		List<String> ids = new ArrayList<>();
		for (Holder.Reference<StructureSet> holder : level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().toList()) {
			if (holder.key().identifier().getNamespace().equals(StellaRuins.MOD_ID)) {
				ids.add(holder.key().identifier().getPath());
			}
		}
		return ids;
	}

	static int spanX(final RuinSite site) {
		return (site.sizeX() + 15) / 16;
	}

	/**
	 * True when the one ruin set holds this site, as a structure whose own file names it, with a positive weight.
	 * Every ruin structure shares one set now, so a site test proves its site by its entry in that set rather than
	 * by a set id of its own.
	 */
	public static boolean theSitesSetHolds(final ServerLevel level, final String siteId) {
		for (Plan plan : plans(level)) {
			if (plan.id().equals(siteId) && plan.weight() > 0) {
				return true;
			}
		}
		return false;
	}

	/** The structure key of this site's own structure file, or null when no structure names it. */
	public static ResourceKey<Structure> structureOf(final ServerLevel level, final String siteId) {
		for (Holder.Reference<Structure> holder : level.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().toList()) {
			if (holder.value() instanceof RuinStructure ruin && ruin.site().equals(siteId)
				&& holder.key().identifier().getNamespace().equals(StellaRuins.MOD_ID)) {
				return holder.key();
			}
		}
		return null;
	}

	static int spanZ(final RuinSite site) {
		return (site.sizeZ() + 15) / 16;
	}

	/** Every potential structure chunk of the set inside the fixed region, the way the game places it. */
	static List<ChunkPos> potential(final RandomSpreadStructurePlacement placement) {
		List<ChunkPos> chunks = new ArrayList<>();
		for (int cx = MIN_CHUNK; cx <= MAX_CHUNK; cx++) {
			for (int cz = MIN_CHUNK; cz <= MAX_CHUNK; cz++) {
				ChunkPos at = placement.getPotentialStructureChunk(SEED, cx, cz);
				if (at.x() == cx && at.z() == cz) {
					chunks.add(at);
				}
			}
		}
		return chunks;
	}

	/**
	 * Item 2, the measured outcome. Two ruin sites never stand inside each other's footprint.
	 * <p>
	 * The bound is derived, not written by hand: every site's own bounds give its chunk span, the widest pair any
	 * two sites can bring is twice the widest span, and the set has to keep them apart by at least that. The guard
	 * measures both, so moving a site's bounds or the separation moves the bound it is held to.
	 */
	@GameTest(maxTicks = 400)
	public void noRuinSiteStandsInsideAnotherSitesFootprint(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Plan> plans = plans(level);
		List<ChunkPos> chunks = plans.isEmpty() ? List.of() : potential(plans.getFirst().placement());
		int separation = plans.isEmpty() ? 0 : plans.getFirst().placement().separation();
		int widestSpan = 0;
		String widest = "none";
		for (Plan plan : plans) {
			int span = Math.max(spanX(plan.site()), spanZ(plan.site()));
			if (span > widestSpan) {
				widestSpan = span;
				widest = plan.id();
			}
		}
		int needed = widestSpan * 2;
		List<String> collisions = new ArrayList<>();
		int closest = Integer.MAX_VALUE;
		for (int i = 0; i < chunks.size(); i++) {
			for (int j = i + 1; j < chunks.size(); j++) {
				ChunkPos a = chunks.get(i);
				ChunkPos b = chunks.get(j);
				int dx = Math.abs(a.x() - b.x());
				int dz = Math.abs(a.z() - b.z());
				closest = Math.min(closest, Math.max(dx, dz));
				if (dx < needed && dz < needed && collisions.size() < 8) {
					collisions.add(a.x() + "," + a.z() + " x " + b.x() + "," + b.z() + " dx=" + dx + " dz=" + dz);
				}
			}
		}
		WorldTestSupport.result("siteOverlap", "structures=" + plans.size() + " sites=" + chunks.size() + " separation=" + separation
			+ " widestFootprint=" + widestSpan + " at " + widest + " widestPair=" + needed + " seed=" + SEED
			+ " chunks=" + MIN_CHUNK + ".." + MAX_CHUNK + " collisions=" + collisions.size()
			+ " closestChunkGap=" + (closest == Integer.MAX_VALUE ? "n/a" : closest) + (collisions.isEmpty() ? "" : " first=" + collisions));
		helper.assertTrue(plans.size() >= SITE_COUNT, "only " + plans.size() + " ruin structures are in the set");
		helper.assertTrue(chunks.size() > 0, "the region holds no potential ruin chunk, so the measurement proves nothing");
		helper.assertTrue(separation >= needed, "a separation of " + separation + " is below the " + needed + " chunks two footprints need");
		helper.assertTrue(collisions.isEmpty(), collisions.size() + " ruin footprints collide: " + collisions);
		helper.succeed();
	}

	/**
	 * Item 2, the property the guard above rests on. Every ruin structure sits in the one set, the widest pair of
	 * footprints is 8 chunks, and the separation is at or above it, so two sites cannot both hold blocks.
	 */
	@GameTest(maxTicks = 400)
	public void everyRuinStructureSitsInOneSetWithASeparationWideEnoughForTwoFootprints(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Plan> plans = plans(level);
		List<String> setIds = ruinSetIds(level);
		List<String> spreads = new ArrayList<>();
		Set<Integer> separations = new LinkedHashSet<>();
		Set<Integer> spacings = new LinkedHashSet<>();
		for (Plan plan : plans) {
			separations.add(plan.placement().separation());
			spacings.add(plan.placement().spacing());
		}
		int separation = separations.isEmpty() ? 0 : separations.iterator().next();
		int spacing = spacings.isEmpty() ? 0 : spacings.iterator().next();
		WorldTestSupport.result("siteGrid", "ruinSets=" + setIds + " structures=" + plans.size() + " spacing=" + spacing
			+ " separation=" + separation + " widestPair=" + WIDEST_PAIR_SPAN + " spreads=" + spreads);
		helper.assertTrue(setIds.size() == 1, "the ruins are split over " + setIds.size() + " structure sets: " + setIds);
		helper.assertTrue(plans.size() >= SITE_COUNT, "only " + plans.size() + " ruin structures are in the set");
		helper.assertTrue(spacings.size() == 1 && separations.size() == 1, "the structures do not share one grid");
		helper.assertTrue(separation >= WIDEST_PAIR_SPAN, "a separation of " + separation + " is below the " + WIDEST_PAIR_SPAN + " chunk footprint pair");
		helper.assertTrue(spacing > separation, "the spacing " + spacing + " is not above the separation " + separation);
		helper.succeed();
	}

	/**
	 * Item 3, the measured rate. The set places {@code spacing * spacing} chunks apart, and the crater takes its
	 * weight of those. The rate therefore fell against the grid main shipped, where the crater was its own set on a
	 * spacing of {@link #SPACING_BEFORE} among {@link #SITES_BEFORE} sets. Every number comes from what ships.
	 */
	@GameTest(maxTicks = 400)
	public void theCraterRateFellAgainstTheGridItShippedBefore(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Plan> plans = plans(level);
		Plan crater = null;
		int totalWeight = 0;
		for (Plan plan : plans) {
			totalWeight += plan.weight();
			if (plan.id().equals("meteor_crater")) {
				crater = plan;
			}
		}
		helper.assertTrue(crater != null, "no meteor_crater structure is in the set");
		int side = MAX_CHUNK - MIN_CHUNK + 1;
		int spacing = crater.placement().spacing();
		int sites = potential(crater.placement()).size();
		double region = (double) side * side;
		double per1000 = sites / (region * 16.0) * 1000.0 * crater.weight() / totalWeight;
		double before = 1.0 / ((double) SPACING_BEFORE * SPACING_BEFORE) / 16.0 * 1000.0;
		WorldTestSupport.result("craterRate", "structures=" + plans.size() + " totalWeight=" + totalWeight + " craterWeight=" + crater.weight()
			+ " spacing=" + spacing + " sitesInRegion=" + sites + " sitesPer1000BlocksOfLine=" + String.format("%.4f", per1000)
			+ " before=" + String.format("%.4f", before));
		helper.assertTrue(per1000 > 0.0, "the crater never places, so the rate proves nothing");
		helper.assertTrue(per1000 < before, "the crater rate " + per1000 + " did not fall against " + before);
		helper.succeed();
	}

	/**
	 * The merge itself. One set holds every ruin structure, each with a positive weight, and the set keeps the
	 * village exclusion. A structure left out of the set would never place, and a lost exclusion would drop a rule
	 * every ruin set shipped with.
	 */
	@GameTest(maxTicks = 400)
	public void theOneSetHoldsEveryRuinStructureAndKeepsTheVillageExclusion(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Plan> plans = plans(level);
		StructureSet set = sites(level);
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		JsonElement encoded = set == null ? null : StructurePlacement.CODEC.encodeStart(ops, set.placement()).getOrThrow();
		Set<String> ids = new LinkedHashSet<>();
		int zeroWeight = 0;
		for (Plan plan : plans) {
			ids.add(plan.id());
			zeroWeight += plan.weight() > 0 ? 0 : 1;
		}
		WorldTestSupport.result("sitesSet", "set=" + SITES + " structures=" + plans.size() + " ids=" + ids + " zeroWeight=" + zeroWeight
			+ " placement=" + encoded);
		helper.assertTrue(set != null, "no " + SITES + " structure set");
		helper.assertTrue(plans.size() >= SITE_COUNT, "the set holds " + plans.size() + " ruin structures, not " + SITE_COUNT + ": " + ids);
		helper.assertTrue(ids.size() == plans.size(), "a site appears twice in the set: " + ids);
		helper.assertTrue(zeroWeight == 0, zeroWeight + " structures carry no weight, so they never place");
		helper.assertTrue(excludesVillages(encoded), "the merged set lost the village exclusion: " + encoded);
		helper.succeed();
	}

	/** True when a placement carries the village exclusion over 8 chunks, named directly as the codec requires. */
	public static boolean excludesVillages(final JsonElement placement) {
		if (placement == null || !placement.isJsonObject() || !placement.getAsJsonObject().has("exclusion_zone")) {
			return false;
		}
		JsonObject zone = placement.getAsJsonObject().getAsJsonObject("exclusion_zone");
		return zone.has("other_set") && "minecraft:villages".equals(zone.get("other_set").getAsString())
			&& zone.has("chunk_count") && zone.get("chunk_count").getAsInt() >= 8;
	}
}
