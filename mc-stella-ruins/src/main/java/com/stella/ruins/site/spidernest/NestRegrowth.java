package com.stella.ruins.site.spidernest;

import com.mojang.serialization.Codec;
import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Grows the nest back. A core that completed is remembered here. When the framework rearms it, the sacs, the webs,
 * and the cocoons grow back into air, at the cells of the plan. Rock that a player mined stays mined.
 * <p>
 * The framework gives a site no hook for a rearm, so a server tick event polls the remembered cores. It acts only
 * when the core's chunk is loaded, and it keeps a core on the list until it can act.
 */
public final class NestRegrowth {
	/** Ticks between two polls. */
	public static final int POLL_TICKS = 10;

	/** The cores that completed and have not grown back yet, in one dimension. */
	public static final class Data extends SavedData {
		private final Set<BlockPos> cores = new LinkedHashSet<>();

		public Data() {
		}

		private Data(final List<BlockPos> saved) {
			this.cores.addAll(saved);
		}

		private List<BlockPos> snapshot() {
			return new ArrayList<>(this.cores);
		}

		public List<BlockPos> cores() {
			return List.copyOf(this.cores);
		}

		void add(final BlockPos core) {
			if (this.cores.add(core.immutable())) {
				setDirty();
			}
		}

		void remove(final BlockPos core) {
			if (this.cores.remove(core)) {
				setDirty();
			}
		}
	}

	public static final Codec<Data> CODEC = BlockPos.CODEC.listOf().fieldOf("cores").xmap(Data::new, Data::snapshot).codec();
	public static final SavedDataType<Data> TYPE = new SavedDataType<>(StellaRuins.id("nest_regrowth"), Data::new, CODEC, DataFixTypes.LEVEL);

	private static long counter;

	private NestRegrowth() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++counter % POLL_TICKS == 0) {
				pollAll(server);
			}
		});
	}

	public static Data data(final ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Remembers a core that just completed. */
	public static void remember(final ServerLevel level, final BlockPos core) {
		data(level).add(core);
	}

	/** Drops a core from the list. */
	public static void forget(final ServerLevel level, final BlockPos core) {
		data(level).remove(core);
	}

	static void pollAll(final MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			poll(level);
		}
	}

	/** Grows back every remembered core that the framework has rearmed. Returns how many grew back. */
	public static int poll(final ServerLevel level) {
		int grown = 0;
		for (BlockPos core : data(level).cores()) {
			if (pollCore(level, core)) {
				grown++;
			}
		}
		return grown;
	}

	/** The poll for one core. Returns true when its nest grew back and it left the list. */
	public static boolean pollCore(final ServerLevel level, final BlockPos core) {
		Data data = data(level);
		if (!data.cores().contains(core)) {
			return false;
		}
		RuinData.Entry entry = RuinData.get(level).entry(core);
		if (entry == null) {
			data.remove(core);
			return false;
		}
		boolean rearmed = entry.phase() == RuinPhase.DORMANT || entry.phase() == RuinPhase.COOLDOWN;
		if (rearmed && level.isLoaded(core) && regrow(level, core)) {
			data.remove(core);
			return true;
		}
		return false;
	}

	/** Draws the growing blocks of the core's nest into the world. Returns false when the core has no frame yet. */
	public static boolean regrow(final ServerLevel level, final BlockPos core) {
		if (!(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block) || block.frame() == null) {
			return false;
		}
		RuinFrame frame = block.frame();
		NestLayout.regrow(LayoutContext.forWorld(frame, level, frame.box()));
		StellaRuins.LOGGER.info("[RUINS] nest core={} grew back", core.toShortString());
		return true;
	}
}
