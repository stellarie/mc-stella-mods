package com.stella.ruins.site.ambushcamp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The tent each camp hid its Chief in last time. The core wipes the objective state on every reset, so the
 * exclusion of the previous tent needs a memory of its own.
 */
public final class CampMemory extends SavedData {
	private record Saved(BlockPos core, int tent) {
		private static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("core").forGetter(Saved::core),
			Codec.INT.fieldOf("tent").forGetter(Saved::tent)
		).apply(instance, Saved::new));
	}

	public static final Codec<CampMemory> CODEC = Saved.CODEC.listOf().fieldOf("camps").xmap(CampMemory::new, CampMemory::snapshot).codec();
	public static final SavedDataType<CampMemory> TYPE = new SavedDataType<>(StellaRuins.id("ambush_camps"), CampMemory::new, CODEC, DataFixTypes.LEVEL);

	private final Map<BlockPos, Integer> last = new LinkedHashMap<>();

	public CampMemory() {
	}

	private CampMemory(final List<Saved> saved) {
		for (Saved s : saved) {
			this.last.put(s.core(), s.tent());
		}
	}

	private List<Saved> snapshot() {
		List<Saved> list = new ArrayList<>();
		this.last.forEach((core, tent) -> list.add(new Saved(core, tent)));
		return list;
	}

	public static CampMemory get(final ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The tent index of the last Chief of the camp at this core, or -1 when it never had one. */
	public int last(final BlockPos core) {
		return this.last.getOrDefault(core, -1);
	}

	public void remember(final BlockPos core, final int tent) {
		this.last.put(core.immutable(), tent);
		setDirty();
	}
}
