package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.SiteRun;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Kill the whole garrison. The objective spawns one mob at each marker of the tower: the markers decide how many,
 * because the tower has 4 to 6 floors and a breach that costs it posts. Each mob is tagged with the box of the
 * shaft's inside and its own post, so that it cannot be carried out through the breach.
 */
public final class GarrisonObjective implements Objective {
	@Override
	public void start(final SiteRun run) {
		List<BlockPos> low = run.markers(WatchtowerMobs.BOUND_MIN);
		List<BlockPos> high = run.markers(WatchtowerMobs.BOUND_MAX);
		BlockPos a = low.isEmpty() || high.isEmpty() ? null : low.get(0);
		BlockPos b = a == null ? null : high.get(0);
		spawnAll(run, WatchtowerPlan.ARCHER, WatchtowerMobs.WATCH_ARCHER, a, b);
		spawnAll(run, WatchtowerPlan.CROSSBOW, WatchtowerMobs.TOWER_CROSSBOWMAN, a, b);
		spawnAll(run, WatchtowerPlan.CAPTAIN, WatchtowerMobs.WATCH_CAPTAIN, a, b);
	}

	private static void spawnAll(final SiteRun run, final String marker, final RuinMobKind kind, final BlockPos low, final BlockPos high) {
		for (BlockPos spot : run.markers(marker)) {
			Mob mob = run.spawn(kind, new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
			if (mob != null && low != null) {
				mob.addTag(WatchtowerMobs.garrisonTag(
					Math.min(low.getX(), high.getX()), Math.min(low.getZ(), high.getZ()),
					Math.max(low.getX(), high.getX()), Math.max(low.getZ(), high.getZ()),
					spot.getX(), spot.getY(), spot.getZ()));
			}
		}
	}

	@Override
	public Progress tick(final SiteRun run) {
		return run.alive() == 0 ? Progress.COMPLETE : Progress.RUNNING;
	}

	@Override
	public CompoundTag save() {
		return new CompoundTag();
	}

	@Override
	public void load(final CompoundTag tag) {
	}
}
