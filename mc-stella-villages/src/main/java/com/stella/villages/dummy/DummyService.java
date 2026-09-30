package com.stella.villages.dummy;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.patrol.PatrolService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Every 10 seconds, each recorded village with a loaded bell gets its training dummy back if it is gone. */
public final class DummyService {
	public static final int PASS_TICKS = 200;

	private DummyService() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % PASS_TICKS == 0) {
				pass(level);
			}
		});
	}

	public static void pass(final ServerLevel level) {
		for (VillageWorldData.VillageRecord village : VillageWorldData.get(level).villages()) {
			if (!village.dimension().equals(level.dimension())) {
				continue;
			}
			BlockPos bell = PatrolService.bellOf(level, village);
			if (!level.isLoaded(bell) || !level.getBlockState(bell).is(net.minecraft.world.level.block.Blocks.BELL)) {
				continue;
			}
			TrainingDummy.ensure(level, village.id(), bell);
		}
	}
}
