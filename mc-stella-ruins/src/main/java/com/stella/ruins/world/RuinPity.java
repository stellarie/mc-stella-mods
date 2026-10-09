package com.stella.ruins.world;

import com.mojang.serialization.Codec;
import com.stella.ruins.StellaRuins;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Clears without a trophy, for each site kind of one dimension. The count raises the next trophy chance. */
public final class RuinPity extends SavedData {
	public static final Codec<RuinPity> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(RuinPity::new, pity -> Map.copyOf(pity.dry));
	public static final SavedDataType<RuinPity> TYPE = new SavedDataType<>(StellaRuins.id("pity"), RuinPity::new, CODEC, DataFixTypes.LEVEL);

	private final Map<String, Integer> dry = new HashMap<>();

	public RuinPity() {
	}

	private RuinPity(final Map<String, Integer> saved) {
		this.dry.putAll(saved);
	}

	public static RuinPity get(final ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Clears of this kind since its last trophy. */
	public int dry(final String site) {
		return this.dry.getOrDefault(site, 0);
	}

	public void miss(final String site) {
		this.dry.merge(site, 1, Integer::sum);
		setDirty();
	}

	public void reset(final String site) {
		if (this.dry.remove(site) != null) {
			setDirty();
		}
	}
}
