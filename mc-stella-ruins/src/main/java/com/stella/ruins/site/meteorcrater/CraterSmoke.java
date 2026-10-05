package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The beacon of a hot crater. While the meteor is hot, its core sends a tall column of smoke every 10 ticks, to
 * every player within {@link #RANGE} blocks, so the crater can be found from afar and its state can be read. The
 * smoke stops when the meteor cools.
 */
public final class CraterSmoke {
	public static final int PERIOD = 10;
	/** The farthest a player is from the core and still gets the smoke. The column shows from 48 blocks. */
	public static final double RANGE = 64.0;
	/** The cores that are loaded, as the world has told us. */
	private static final Set<RuinCoreBlockEntity> CORES = ConcurrentHashMap.newKeySet();
	private static int counter;

	private CraterSmoke() {
	}

	public static void register() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof RuinCoreBlockEntity core) {
				CORES.add(core);
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof RuinCoreBlockEntity core) {
				CORES.remove(core);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++counter % PERIOD == 0) {
				server.getAllLevels().forEach(CraterSmoke::emit);
			}
		});
	}

	/** True when the crater at {@code core} still has hot ore: its core has not completed. */
	public static boolean hot(final ServerLevel level, final BlockPos core) {
		RuinData.Entry entry = RuinData.get(level).entry(core);
		return entry == null || entry.phase() != RuinPhase.COMPLETE;
	}

	/** True when the world has told us about the core at {@code pos}. */
	public static boolean tracked(final BlockPos pos) {
		for (RuinCoreBlockEntity core : CORES) {
			if (core.getBlockPos().equals(pos) && !core.isRemoved()) {
				return true;
			}
		}
		return false;
	}

	/** Sends one puff of smoke from every hot crater of the level. Returns how many sends went to a player. */
	public static int emit(final ServerLevel level) {
		int sent = 0;
		for (RuinCoreBlockEntity core : new ArrayList<>(CORES)) {
			if (core.isRemoved()) {
				CORES.remove(core);
				continue;
			}
			sent += emitFrom(level, core);
		}
		return sent;
	}

	/** One puff from one core, if it belongs to a hot crater of this level. Returns how many players got it. */
	public static int emitFrom(final ServerLevel level, final RuinCoreBlockEntity core) {
		RuinFrame frame = core.frame();
		if (core.getLevel() != level || frame == null || !MeteorCraterSite.ID.equals(frame.site()) || !hot(level, core.getBlockPos())) {
			return 0;
		}
		int sent = 0;
		BlockPos at = core.getBlockPos();
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) <= RANGE * RANGE
				&& level.sendParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, at.getX() + 0.5, at.getY() + 3.0, at.getZ() + 0.5, 2, 0.4, 0.0, 0.4, 0.02)) {
				sent++;
			}
		}
		return sent;
	}
}
