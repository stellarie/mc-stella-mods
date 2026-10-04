package com.stella.ruins.site.spidernest;

import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinSites;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Turns the loss of an egg sac into a count on the objective of the core beside it. The block calls it on removal. */
public final class SacBreaks {
	/** How far from a sac the core can stand. The nest is 30 wide and its core stands near the middle. */
	private static final int SCAN_XZ = 20;
	private static final int SCAN_DOWN = 12;

	private SacBreaks() {
	}

	/** Reads one removed sac at {@code pos} against the objective of the core beside it. */
	public static SacObjective.@Nullable Outcome onRemoved(final ServerLevel level, final BlockPos pos) {
		BlockPos core = findCore(level, pos);
		if (core == null) {
			return null;
		}
		RuinData.Entry entry = RuinData.get(level).entry(core);
		RuinSite site = entry == null ? null : RuinSites.get(entry.site());
		if (entry == null || site == null || entry.phase() != RuinPhase.ACTIVE || !(site.newObjective() instanceof SacObjective objective)) {
			return null;
		}
		if (!(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block)) {
			return null;
		}
		Map<String, List<BlockPos>> markers = block.markers();
		if (!markers.getOrDefault(NestMarkers.SAC, List.of()).contains(pos)) {
			return null;
		}
		objective.load(entry.objective());
		SacObjective.Outcome outcome = objective.sacBroken(level, core, markers, RuinClock.now(level));
		entry.setObjective(objective.save());
		return outcome;
	}

	/** The nearest core within reach of a sac. */
	static @Nullable BlockPos findCore(final ServerLevel level, final BlockPos pos) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-SCAN_XZ, -SCAN_DOWN, -SCAN_XZ), pos.offset(SCAN_XZ, 2, SCAN_XZ))) {
			if (level.getBlockState(probe).is(RuinBlocks.CORE)) {
				double distance = probe.distSqr(pos);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = probe.immutable();
				}
			}
		}
		return best;
	}
}
