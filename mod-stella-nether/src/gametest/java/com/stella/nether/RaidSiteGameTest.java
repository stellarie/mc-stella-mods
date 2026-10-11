package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Every run's arrival lies within reach of a fortress start. The check scans every chunk in
 * reach for a structure start, so it does not lean on the site search it measures.
 */
public class RaidSiteGameTest {
	static final int RUNS = 12;

	static double nearestStart(final ServerLevel raid, final ResourceKey<Structure> key, final BlockPos from) {
		Structure structure = raid.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key).value();
		int reach = RaidInstance.STRUCTURE_REACH / 16 + 1;
		double best = Double.POSITIVE_INFINITY;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int cx = (from.getX() >> 4) + dx;
				int cz = (from.getZ() >> 4) + dz;
				ChunkAccess chunk = raid.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
				StructureStart start = chunk.getStartForStructure(structure);
				if (start != null && start.isValid()) {
					double x = (cx << 4) + 8 - from.getX();
					double z = (cz << 4) + 8 - from.getZ();
					best = Math.min(best, Math.sqrt(x * x + z * z));
				}
			}
		}
		return best;
	}

	@GameTest(environment = "mod-stella-nether-test:sites", maxTicks = 100)
	public void everyRunArrivesWithinSixtyFourBlocksOfItsFortressStart(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = NetherTestSupport.raid(helper);
		int within = 0;
		StringBuilder runs = new StringBuilder();
		for (int i = 0; i < RUNS; i++) {
			NetherTestSupport.fresh(helper);
			BlockPos arrival = RaidInstance.arrival(server, RaidMode.NORMAL);
			BlockPos fortressStart = RaidInstance.site(server, RaidMode.NORMAL).fortress();
			double toSite = RaidSites.gap(arrival, fortressStart);
			double toNearest = nearestStart(raid, BuiltinStructures.FORTRESS, arrival);
			runs.append(String.format(" run%d=%.0f/%.0f", RaidInstance.state(server, RaidMode.NORMAL).run(), toSite, toNearest));
			if (toSite <= RaidInstance.FORTRESS_REACH && toNearest <= RaidInstance.FORTRESS_REACH) {
				within++;
			}
		}
		NetherTestSupport.result("arrivalNearFortress", "within=" + within + " of=" + RUNS + " site/nearest=" + runs);
		helper.assertTrue(within == RUNS, within + " of " + RUNS + " runs arrived within " + RaidInstance.FORTRESS_REACH + " blocks of a fortress start:" + runs);
		helper.succeed();
	}
}
