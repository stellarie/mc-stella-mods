package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Each elite and boss post, saved with the tower: who holds it and when the next may rise. Time is the
 * tower's own count of ticks, saved too, so a slain guardian's rest outlives a restart.
 */
public final class GuardianData extends SavedData {
	/** One post: its living guardian if one was raised, and the tick the next may rise. */
	public record Post(Optional<UUID> guardian, long nextRise) {
		public static final Codec<Post> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.optionalFieldOf("guardian").forGetter(Post::guardian),
			Codec.LONG.fieldOf("next_rise").forGetter(Post::nextRise)
		).apply(instance, Post::new));
	}

	private record Snapshot(long ticks, Map<String, Post> posts) {
		private static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("ticks").forGetter(Snapshot::ticks),
			Codec.unboundedMap(Codec.STRING, Post.CODEC).fieldOf("posts").forGetter(Snapshot::posts)
		).apply(instance, Snapshot::new));
	}

	public static final Codec<GuardianData> CODEC = Snapshot.CODEC.xmap(
		snapshot -> new GuardianData(snapshot.ticks(), snapshot.posts()),
		data -> new Snapshot(data.ticks, Map.copyOf(data.posts))
	);
	public static final SavedDataType<GuardianData> TYPE = new SavedDataType<>(
		StellaTower.id("guardians"), GuardianData::new, CODEC, DataFixTypes.LEVEL
	);

	private long ticks;
	private final Map<String, Post> posts = new HashMap<>();

	public GuardianData() {
	}

	private GuardianData(final long ticks, final Map<String, Post> posts) {
		this.ticks = ticks;
		this.posts.putAll(posts);
	}

	public static GuardianData get(final ServerLevel tower) {
		return tower.getDataStorage().computeIfAbsent(TYPE);
	}

	private static String key(final int floor, final TowerRank rank) {
		return floor + ":" + rank.getSerializedName();
	}

	public long ticks() {
		return this.ticks;
	}

	public void tick() {
		this.ticks++;
		if (this.ticks % 200 == 0) {
			setDirty();
		}
	}

	public @Nullable Post post(final int floor, final TowerRank rank) {
		return this.posts.get(key(floor, rank));
	}

	public void set(final int floor, final TowerRank rank, final Post post) {
		this.posts.put(key(floor, rank), post);
		setDirty();
	}
}
