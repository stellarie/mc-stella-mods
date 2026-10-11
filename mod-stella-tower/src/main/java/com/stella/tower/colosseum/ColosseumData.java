package com.stella.tower.colosseum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.stella.tower.StellaTower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The world's one colosseum, saved with the overworld: where its floor is, once built, and which guardians live. A world
 * that holds a floor never builds a second colosseum.
 */
public final class ColosseumData extends SavedData {
	private record Snapshot(Optional<BlockPos> floor, Optional<BlockPos> spawn, List<UUID> guardians) {
		private static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.optionalFieldOf("floor").forGetter(Snapshot::floor),
			BlockPos.CODEC.optionalFieldOf("spawn").forGetter(Snapshot::spawn),
			UUIDUtil.CODEC.listOf().optionalFieldOf("guardians", List.of()).forGetter(Snapshot::guardians)
		).apply(instance, Snapshot::new));
	}

	public static final Codec<ColosseumData> CODEC = Snapshot.CODEC.xmap(
		snapshot -> new ColosseumData(snapshot.floor().orElse(null), snapshot.spawn().orElse(null), snapshot.guardians()),
		data -> new Snapshot(Optional.ofNullable(data.floor), Optional.ofNullable(data.spawn), List.copyOf(data.guardians))
	);
	public static final SavedDataType<ColosseumData> TYPE = new SavedDataType<>(
		StellaTower.id("colosseum"), ColosseumData::new, CODEC, DataFixTypes.LEVEL
	);

	private @Nullable BlockPos floor;
	private @Nullable BlockPos spawn;
	private final Set<UUID> guardians = new LinkedHashSet<>();

	public ColosseumData() {
	}

	private ColosseumData(final @Nullable BlockPos floor, final @Nullable BlockPos spawn, final List<UUID> guardians) {
		this.floor = floor;
		this.spawn = spawn;
		this.guardians.addAll(guardians);
	}

	public static ColosseumData get(final ServerLevel overworld) {
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The centre column at the floor layer, or null while no colosseum is built. */
	public @Nullable BlockPos floor() {
		return this.floor;
	}

	/** The world spawn the site was drawn from; it is kept because a test harness moves the spawn after the build. */
	public @Nullable BlockPos spawn() {
		return this.spawn;
	}

	public void claim(final BlockPos floor, final BlockPos spawn) {
		this.floor = floor.immutable();
		this.spawn = spawn.immutable();
		setDirty();
	}

	/** The guardians that live, by rider id. */
	public Set<UUID> guardians() {
		return Set.copyOf(this.guardians);
	}

	public void raised(final UUID rider) {
		if (this.guardians.add(rider)) {
			setDirty();
		}
	}

	public void fallen(final UUID rider) {
		if (this.guardians.remove(rider)) {
			setDirty();
		}
	}
}
