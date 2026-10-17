package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** The castle's rooftop: who stands on it, where the dragon waits above it, and when spawns on it are barred. */
public final class Rooftop {
	/** The block layer of the deck; anyone on it stands one block higher. */
	public static final int DECK_Y = EndCastle.ROOF_Y;
	/** How far over the deck the dragon waits and circles. */
	public static final double HOVER_HEIGHT = 22.0;
	/** The ceiling of the rooftop: a player higher than this is out of the fight. */
	public static final double CEILING = 48.0;
	/** Rooftop spawns stay barred this long after the dragon dies. */
	public static final long SPAWN_BAN_TICKS = 36000L;

	private Rooftop() {
	}

	/** Whether a point lies over the deck's footprint, from the deck up to the ceiling. */
	public static boolean contains(final double x, final double y, final double z) {
		return x >= EndCastle.MIN_X && x < EndCastle.MIN_X + EndCastle.SIZE && z >= EndCastle.MIN_Z && z < EndCastle.MIN_Z + EndCastle.SIZE
			&& y >= DECK_Y + 0.5 && y <= DECK_Y + CEILING;
	}

	/** The players the dragon fights: alive, in survival or adventure, and on the rooftop. */
	public static List<ServerPlayer> players(final ServerLevel end) {
		List<ServerPlayer> found = new ArrayList<>();
		for (ServerPlayer player : end.players()) {
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && contains(player.getX(), player.getY(), player.getZ())) {
				found.add(player);
			}
		}
		return found;
	}

	/** Over the middle of the deck, where the dragon waits while nobody stands on the roof. */
	public static Vec3 hoverPoint() {
		return new Vec3(0.5, DECK_Y + HOVER_HEIGHT, 0.5);
	}

	/** Where the dragon sits to roost: the highest solid block at the middle of the deck, as vanilla lands it on its podium. */
	public static Vec3 roostPoint(final ServerLevel end) {
		BlockPos top = end.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
		return Vec3.atBottomCenterOf(top);
	}

	/** Whether the ban on rooftop spawns holds now: the dragon died less than {@link #SPAWN_BAN_TICKS} ago. */
	public static boolean spawnsBlocked(final ServerLevel end) {
		long diedAt = EndCastles.state(end).dragonDeadAt();
		return diedAt != EndState.NEVER && EndClock.now(end) < diedAt + SPAWN_BAN_TICKS;
	}
}
