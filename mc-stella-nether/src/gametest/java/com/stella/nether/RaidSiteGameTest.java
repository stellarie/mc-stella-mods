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
 * Every run's arrival lies within reach of both a fortress and a bastion. The check scans every chunk in
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
	public void everyRunArrivesWithinReachOfAFortressAndABastion(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = NetherTestSupport.raid(helper);
		int within = 0;
		StringBuilder runs = new StringBuilder();
		for (int i = 0; i < RUNS; i++) {
			NetherTestSupport.fresh(helper);
			BlockPos arrival = RaidInstance.arrival(server);
			double fortress = nearestStart(raid, BuiltinStructures.FORTRESS, arrival);
			double bastion = nearestStart(raid, BuiltinStructures.BASTION_REMNANT, arrival);
			runs.append(String.format(" run%d=%.0f/%.0f", RaidInstance.state(server).run(), fortress, bastion));
			if (fortress <= RaidInstance.STRUCTURE_REACH && bastion <= RaidInstance.STRUCTURE_REACH) {
				within++;
			}
		}
		NetherTestSupport.result("structuresInReach", "within=" + within + " of=" + RUNS + runs);
		helper.assertTrue(within == RUNS, within + " of " + RUNS + " runs had both structures in reach:" + runs);
		helper.succeed();
	}
}
