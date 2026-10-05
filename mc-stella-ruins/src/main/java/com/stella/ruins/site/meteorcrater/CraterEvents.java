package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinSites;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Turns a mined hot ore block into an Ember Slime and a started timer. A Fabric event carries it: no mixin. */
public final class CraterEvents {
	/** The core of a crater stands within this many blocks of every ore block of its meteor. */
	private static final int SCAN_XZ = 8;
	private static final int SCAN_DOWN = 6;
	private static final int SCAN_UP = 2;

	private CraterEvents() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(CraterEvents::afterBreak);
	}

	private static void afterBreak(final Level level, final Player player, final BlockPos pos, final BlockState state, final BlockEntity entity) {
		if (state.is(MeteorBlocks.STARMETAL_ORE) && level instanceof ServerLevel server && !player.isSpectator()) {
			oreMined(server, pos);
		}
	}

	/** One hot ore block was mined at {@code pos}. Returns true when an Ember Slime woke for it. */
	public static boolean oreMined(final ServerLevel level, final BlockPos pos) {
		BlockPos core = findCore(level, pos);
		if (core == null) {
			return false;
		}
		RuinData.Entry entry = RuinData.get(level).entry(core);
		RuinSite site = entry == null ? null : RuinSites.get(entry.site());
		if (entry == null || !CoolingObjective.isCrater(site) || entry.phase() != RuinPhase.ACTIVE) {
			return false;
		}
		CoolingObjective objective = (CoolingObjective) site.newObjective();
		objective.load(entry.objective());
		boolean woke = objective.oreMined(level, entry, pos);
		entry.setObjective(objective.save());
		return woke;
	}

	/** The nearest core within reach of an ore block. */
	static @Nullable BlockPos findCore(final ServerLevel level, final BlockPos pos) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-SCAN_XZ, -SCAN_DOWN, -SCAN_XZ), pos.offset(SCAN_XZ, SCAN_UP, SCAN_XZ))) {
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
