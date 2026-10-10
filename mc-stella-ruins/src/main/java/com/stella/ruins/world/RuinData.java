package com.stella.ruins.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every core of one dimension: its site, phase, roster, objective state and claim time. A core keeps nothing
 * else, so a reload cannot lose a run.
 */
public final class RuinData extends SavedData {
	/** One core, as saved. */
	private record Saved(BlockPos pos, String site, String phase, List<UUID> roster, CompoundTag objective, long claimTime, int tier, List<String> omens) {
		private static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Saved::pos),
			Codec.STRING.fieldOf("site").forGetter(Saved::site),
			Codec.STRING.fieldOf("phase").forGetter(Saved::phase),
			UUIDUtil.CODEC.listOf().optionalFieldOf("roster", List.of()).forGetter(Saved::roster),
			CompoundTag.CODEC.optionalFieldOf("objective", new CompoundTag()).forGetter(Saved::objective),
			Codec.LONG.optionalFieldOf("claim_time", -1L).forGetter(Saved::claimTime),
			Codec.INT.optionalFieldOf("tier", 0).forGetter(Saved::tier),
			Codec.STRING.listOf().optionalFieldOf("omens", List.of()).forGetter(Saved::omens)
		).apply(instance, Saved::new));
	}

	public static final Codec<RuinData> CODEC = Saved.CODEC.listOf().fieldOf("cores").xmap(RuinData::new, RuinData::snapshot).codec();
	public static final SavedDataType<RuinData> TYPE = new SavedDataType<>(StellaRuins.id("ruins"), RuinData::new, CODEC, DataFixTypes.LEVEL);

	/** One core's record. Setters mark the data dirty. */
	public final class Entry {
		private final BlockPos pos;
		private final String site;
		private RuinPhase phase = RuinPhase.DORMANT;
		private final List<UUID> roster = new ArrayList<>();
		private CompoundTag objective = new CompoundTag();
		private long claimTime = -1L;
		private int tier;
		private List<String> omens = new ArrayList<>();

		private Entry(final BlockPos pos, final String site) {
			this.pos = pos;
			this.site = site;
		}

		public BlockPos pos() {
			return this.pos;
		}

		public String site() {
			return this.site;
		}

		public RuinPhase phase() {
			return this.phase;
		}

		/** The roster mobs that still live, by uuid. */
		public List<UUID> roster() {
			return List.copyOf(this.roster);
		}

		public CompoundTag objective() {
			return this.objective.copy();
		}

		/** The tick the core completed at, or -1 before that. */
		public long claimTime() {
			return this.claimTime;
		}

		/** How many times this reset site has rearmed, up to the configured cap. Zero for a once site. */
		public int tier() {
			return this.tier;
		}

		/** The ids of the omens of the run now under way, or of the last run. */
		public List<String> omens() {
			return List.copyOf(this.omens);
		}

		public void setTier(final int value) {
			this.tier = Math.max(0, value);
			setDirty();
		}

		public void setOmens(final List<String> value) {
			this.omens = new ArrayList<>(value);
			setDirty();
		}

		public void setPhase(final RuinPhase value) {
			this.phase = value;
			setDirty();
		}

		public void setObjective(final CompoundTag value) {
			this.objective = value.copy();
			setDirty();
		}

		public void setClaimTime(final long value) {
			this.claimTime = value;
			setDirty();
		}

		/** Records a spawned mob, and the core it belongs to. */
		public void addMob(final UUID mob) {
			if (!this.roster.contains(mob)) {
				this.roster.add(mob);
				mobIndex.put(mob, this.pos);
				setDirty();
			}
		}

		public void clearRoster() {
			this.roster.forEach(mobIndex::remove);
			this.roster.clear();
			setDirty();
		}
	}

	private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();
	private final Map<UUID, BlockPos> mobIndex = new HashMap<>();

	public RuinData() {
	}

	private RuinData(final List<Saved> saved) {
		for (Saved s : saved) {
			Entry entry = new Entry(s.pos(), s.site());
			entry.phase = RuinPhase.byId(s.phase());
			entry.roster.addAll(s.roster());
			entry.objective = s.objective().copy();
			entry.claimTime = s.claimTime();
			entry.tier = Math.max(0, s.tier());
			entry.omens = new ArrayList<>(s.omens());
			this.entries.put(s.pos(), entry);
			s.roster().forEach(mob -> this.mobIndex.put(mob, s.pos()));
		}
	}

	private List<Saved> snapshot() {
		List<Saved> list = new ArrayList<>();
		for (Entry e : this.entries.values()) {
			list.add(new Saved(e.pos, e.site, e.phase.id(), List.copyOf(e.roster), e.objective.copy(), e.claimTime, e.tier, List.copyOf(e.omens)));
		}
		return list;
	}

	public static RuinData get(final ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable Entry entry(final BlockPos pos) {
		return this.entries.get(pos);
	}

	/**
	 * The record of the core at a spot. A record left by another site at the same spot, as when a world is
	 * regenerated over old data, is replaced by a fresh dormant one.
	 */
	public Entry entryFor(final BlockPos pos, final String site) {
		Entry entry = this.entries.get(pos);
		if (entry == null || !entry.site.equals(site)) {
			if (entry != null) {
				entry.clearRoster();
			}
			entry = new Entry(pos.immutable(), site);
			this.entries.put(entry.pos, entry);
			setDirty();
		}
		return entry;
	}

	/** True when a core of this site within the radius, flat, has completed. Callers use it for once sites only. */
	public boolean completedNear(final String site, final BlockPos pos, final int radius) {
		long limit = (long) radius * radius;
		for (Entry entry : this.entries.values()) {
			if (entry.phase == RuinPhase.COMPLETE && entry.site.equals(site)) {
				long dx = entry.pos.getX() - pos.getX();
				long dz = entry.pos.getZ() - pos.getZ();
				if (dx * dx + dz * dz <= limit) {
					return true;
				}
			}
		}
		return false;
	}

	/** The core nearest to a spot within a flat radius, or null when none stands there. */
	public @Nullable Entry nearest(final BlockPos pos, final int radius) {
		Entry best = null;
		long bestDistance = (long) radius * radius;
		for (Entry entry : this.entries.values()) {
			long dx = entry.pos.getX() - pos.getX();
			long dz = entry.pos.getZ() - pos.getZ();
			long distance = dx * dx + dz * dz;
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = entry;
			}
		}
		return best;
	}

	/** Drops a core's record and its roster index. */
	public void forget(final BlockPos pos) {
		Entry entry = this.entries.remove(pos);
		if (entry != null) {
			entry.clearRoster();
			setDirty();
		}
	}

	/** The core a mob belongs to, or null for a mob the data does not know. */
	public @Nullable BlockPos coreOf(final UUID mob) {
		return this.mobIndex.get(mob);
	}

	/** Takes a dead mob off its core's roster and returns that core's record, or null when it had none. */
	public @Nullable Entry removeMob(final UUID mob) {
		BlockPos core = this.mobIndex.remove(mob);
		if (core == null) {
			return null;
		}
		Entry entry = this.entries.get(core);
		if (entry != null && entry.roster.remove(mob)) {
			setDirty();
		}
		return entry;
	}

	public int size() {
		return this.entries.size();
	}
}
