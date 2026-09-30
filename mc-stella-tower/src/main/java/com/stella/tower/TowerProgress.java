package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * How far up the tower the world has climbed: the highest floor any player has stood on. Nought means no
 * player has entered, and the tower is still locked for the whole world. Saved with the overworld.
 */
public final class TowerProgress extends SavedData {
	public static final int CHECK_TICKS = 20;
	public static final Codec<TowerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("highest").forGetter(progress -> progress.highest)
	).apply(instance, TowerProgress::new));
	public static final SavedDataType<TowerProgress> TYPE = new SavedDataType<>(
		StellaTower.id("progress"), TowerProgress::new, CODEC, DataFixTypes.LEVEL
	);

	private static volatile @Nullable Integer pinned;

	private int highest;

	public TowerProgress() {
	}

	private TowerProgress(final int highest) {
		this.highest = highest;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerProgress::tick);
	}

	public static TowerProgress get(final MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The highest floor reached by anyone; nought while the tower is locked. */
	public static int highest(final MinecraftServer server) {
		Integer pin = pinned;
		return pin != null ? pin : get(server).highest;
	}

	/** The test seam: pins the highest floor, or clears the pin with null. */
	public static void pinHighest(final @Nullable Integer floor) {
		pinned = floor;
	}

	/** The saved record alone, beneath any pin. */
	public int recorded() {
		return this.highest;
	}

	public void reach(final int floor) {
		if (floor > this.highest) {
			this.highest = floor;
			setDirty();
			StellaTower.LOGGER.debug("The tower is open to floor {}", floor);
		}
	}

	/** Whether this player, where they stand, raises the world's highest floor. */
	public static boolean counts(final ServerPlayer player, final ServerLevel tower) {
		int floor = TowerLayout.floorAt(player.blockPosition());
		// A creative flyer, or a teleport onto a floor never built, has climbed nothing.
		return floor > 0 && !player.isSpectator() && !player.isCreative() && TowerFloors.isBuilt(tower, floor);
	}

	/** A player standing on a floor has reached it; the whole world's tower opens that far. */
	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		TowerProgress progress = get(server);
		for (ServerPlayer player : tower.players()) {
			if (counts(player, tower)) {
				progress.reach(TowerLayout.floorAt(player.blockPosition()));
			}
		}
	}
}
