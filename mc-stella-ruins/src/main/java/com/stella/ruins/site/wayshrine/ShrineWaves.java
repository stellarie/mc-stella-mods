package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.SiteRun;
import com.stella.ruins.world.WavePlan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Kills each wave in turn, like the library's wave objective, but every mob appears at a marker of its own kind.
 * A wave that needs more mobs of a kind than the kind has markers reuses the markers in order.
 */
public final class ShrineWaves implements Objective {
	private static final double FALLBACK_RING = 3.0;

	private final WavePlan plan;
	private int wave;

	public ShrineWaves(final WavePlan plan) {
		this.plan = plan;
	}

	@Override
	public void start(final SiteRun run) {
		this.wave = 0;
		if (!this.plan.waves().isEmpty()) {
			spawn(run, this.plan.waves().get(0));
		}
	}

	@Override
	public Progress tick(final SiteRun run) {
		if (this.plan.waves().isEmpty()) {
			return Progress.COMPLETE;
		}
		if (run.alive() > 0) {
			return Progress.RUNNING;
		}
		if (this.wave + 1 >= this.plan.waves().size()) {
			return Progress.COMPLETE;
		}
		this.wave++;
		spawn(run, this.plan.waves().get(this.wave));
		return Progress.RUNNING;
	}

	/** Spawns one wave at the markers of each kind. */
	static void spawn(final SiteRun run, final Roster roster) {
		Map<String, Integer> used = new HashMap<>();
		for (Roster.Entry entry : roster.entries()) {
			String kind = entry.kind().id();
			List<BlockPos> spots = run.markers(WayshrinePlan.spawnMarker(kind));
			for (int i = 0; i < entry.count(); i++) {
				int n = used.merge(kind, 1, Integer::sum) - 1;
				run.spawn(entry.kind(), spots.isEmpty() ? around(run, n) : atSpot(spots.get(n % spots.size())));
			}
		}
	}

	private static Vec3 atSpot(final BlockPos spot) {
		return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
	}

	/** A spot beside the core, for a layout that names no marker of the kind. */
	private static Vec3 around(final SiteRun run, final int n) {
		double angle = n * Math.PI / 2.0;
		BlockPos core = run.core();
		return new Vec3(core.getX() + 0.5 + Math.cos(angle) * FALLBACK_RING, core.getY() + 1.0, core.getZ() + 0.5 + Math.sin(angle) * FALLBACK_RING);
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("wave", this.wave);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.wave = tag.getIntOr("wave", 0);
	}
}
