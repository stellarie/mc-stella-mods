package com.stella.tower;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Writes the volumes of a few plans of each archetype to {@code tower-previews/} under the run directory, as text,
 * for a reviewer to render. It asserts only that each archetype was found and drawn without falling back.
 */
public class TowerPreviewGameTest {
	static final long[] SEEDS = {11L, 22L, 33L};
	/** The first seed's plans are drawn from this floor up, in a band that lays traps. */
	static final int TRAP_BAND = 31;

	@GameTest
	public void dumpThreeVolumesOfEachArchetype(final GameTestHelper helper) {
		Path dir = Path.of("tower-previews");
		List<String> missing = new ArrayList<>();
		try {
			Files.createDirectories(dir);
			for (TowerArchetype archetype : TowerArchetype.values()) {
				// The older archetypes only: a biome floor is no archetype of the older generator's.
				if (archetype == TowerArchetype.BIOME) {
					continue;
				}
				for (long seed : SEEDS) {
					int floor = seed == SEEDS[0] ? TRAP_BAND : archetype == TowerArchetype.HALL && seed == SEEDS[2] ? TowerElites.BOSS_EVERY : 1;
					while (TowerArchetypes.of(seed, floor) != archetype && floor < 400) {
						floor++;
					}
					if (floor >= 400) {
						missing.add(archetype + "@" + seed);
						continue;
					}
					TowerPlan plan = TowerPlan.of(seed, floor);
					Files.writeString(dir.resolve(archetype.name().toLowerCase() + "-" + seed + ".txt"), dump(plan), StandardCharsets.UTF_8);
					TowerTestSupport.result("preview", "archetype=" + archetype + " seed=" + seed + " floor=" + floor + " attempt=" + plan.attempt()
						+ " stories=" + plan.stories().size() + " chests=" + plan.chestSpots().size());
				}
			}
		} catch (IOException e) {
			helper.fail("cannot write previews: " + e);
		}
		helper.assertTrue(missing.isEmpty(), "no floor found for " + missing);
		helper.succeed();
	}

	/** A header line, then one block of 64 rows per height from the bedrock to the roof, each part a letter from 'a'. */
	static String dump(final TowerPlan plan) {
		TowerVolume volume = TowerVolume.of(plan);
		StringBuilder out = new StringBuilder();
		out.append("archetype=").append(plan.archetype()).append(" seed=").append(plan.seed()).append(" floor=").append(plan.floor())
			.append(" attempt=").append(plan.attempt()).append(" arrival=").append(plan.arrivalX()).append(',').append(plan.arrivalZ())
			.append(" portal=").append(plan.portalX()).append(',').append(plan.portalY()).append(',').append(plan.portalZ())
			.append(" stories=");
		for (TowerStory story : plan.stories()) {
			out.append(story.y()).append('/').append(story.top()).append(';');
		}
		out.append(" chests=");
		for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
			out.append(spot.x()).append(',').append(spot.y()).append(',').append(spot.z()).append(';');
		}
		out.append(" traps=");
		for (TowerTraps.Trap trap : TowerTraps.layout(plan.seed(), plan.floor(), plan)) {
			out.append(trap.x()).append(',').append(trap.y()).append(',').append(trap.z()).append(';');
		}
		out.append('\n');
		for (int y = TowerVolume.LOW; y <= TowerVolume.HIGH; y++) {
			out.append("y=").append(y).append('\n');
			for (int z = 0; z < TowerLayout.SIZE; z++) {
				for (int x = 0; x < TowerLayout.SIZE; x++) {
					out.append((char) ('a' + volume.kind(x, y, z)));
				}
				out.append('\n');
			}
		}
		return out.toString();
	}
}
