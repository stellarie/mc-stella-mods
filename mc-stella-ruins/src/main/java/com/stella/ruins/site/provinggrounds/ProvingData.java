package com.stella.ruins.site.provinggrounds;

import com.mojang.serialization.Codec;
import com.stella.ruins.StellaRuins;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The claim times of every proving grounds core, one for each tier. The framework core keeps one claim for the whole
 * site, and that would let tier I block tiers II and III for a day. This data keeps a claim for each tier, keyed by
 * core position, so completing one tier leaves the others open.
 */
public final class ProvingData extends SavedData {
	public static final Codec<ProvingData> CODEC = CompoundTag.CODEC.xmap(ProvingData::fromTag, ProvingData::toTag);
	public static final SavedDataType<ProvingData> TYPE = new SavedDataType<>(StellaRuins.id("proving_grounds"), ProvingData::new, CODEC, DataFixTypes.LEVEL);
	/** The value of a tier that no one has claimed. */
	public static final long NONE = -1L;

	private final Map<Long, long[]> claims = new HashMap<>();

	public ProvingData() {
	}

	public static ProvingData get(final ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	private static ProvingData fromTag(final CompoundTag tag) {
		ProvingData data = new ProvingData();
		for (String key : tag.keySet()) {
			CompoundTag core = tag.getCompoundOrEmpty(key);
			long[] times = new long[TierObjective.TIERS];
			for (int i = 0; i < times.length; i++) {
				times[i] = core.getLongOr("tier" + (i + 1), NONE);
			}
			data.claims.put(Long.parseLong(key), times);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		this.claims.forEach((pos, times) -> {
			CompoundTag core = new CompoundTag();
			for (int i = 0; i < times.length; i++) {
				core.putLong("tier" + (i + 1), times[i]);
			}
			tag.put(Long.toString(pos), core);
		});
		return tag;
	}

	/** The tick a tier was claimed at, or {@link #NONE}. A tier is 1 to 3. */
	public long claimTime(final BlockPos core, final int tier) {
		long[] times = this.claims.get(core.asLong());
		return times == null ? NONE : times[tier - 1];
	}

	public void claim(final BlockPos core, final int tier, final long now) {
		this.claims.computeIfAbsent(core.asLong(), key -> newTimes())[tier - 1] = now;
		setDirty();
	}

	/** Forgets a core's claims, as when its site is removed. */
	public void forget(final BlockPos core) {
		if (this.claims.remove(core.asLong()) != null) {
			setDirty();
		}
	}

	private static long[] newTimes() {
		long[] times = new long[TierObjective.TIERS];
		java.util.Arrays.fill(times, NONE);
		return times;
	}

	/** True while the tier's claim is younger than {@code delay} ticks. */
	public boolean claimed(final BlockPos core, final int tier, final long now, final long delay) {
		long at = claimTime(core, tier);
		return at != NONE && now - at < delay;
	}
}
