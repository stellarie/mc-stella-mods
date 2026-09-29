package com.stella.learn;

import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/**
 * Counts a player falling back from a monster that is after them. Every few ticks it compares where each player
 * stands with where they stood at the last sample, and a step of a block or more straight away from the nearest
 * monster that targets them counts once.
 */
public final class RetreatRecorder {
	/** Blocks a player must have moved straight away from the monster since the last sample to count as a retreat. */
	public static final double STEP = 1.0;
	/** Blocks within which a monster after the player counts. */
	public static final double RANGE = 12.0;
	public static final int SAMPLE_TICKS = 10;
	private static final AttachmentType<Vec3> LAST = AttachmentRegistry.create(StellaLearn.id("retreat_last"));

	private RetreatRecorder() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SAMPLE_TICKS != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Vec3 previous = ((AttachmentTarget) player).getAttached(LAST);
				if (previous != null) {
					sampleFrom(player, previous);
				}

				((AttachmentTarget) player).setAttached(LAST, player.position());
			}
		});
	}

	/** Compares the player's position with where they stood at the last sample; true if it counted a retreat. */
	static boolean sampleFrom(final ServerPlayer player, final Vec3 previous) {
		List<Monster> hunters = player.level().getEntitiesOfClass(
			Monster.class, player.getBoundingBox().inflate(RANGE), monster -> monster.isAlive() && monster.getTarget() == player
		);
		Monster nearest = hunters.stream().filter(monster -> monster.distanceTo(player) <= RANGE)
			.min(Comparator.comparingDouble(monster -> monster.distanceToSqr(player))).orElse(null);
		if (nearest == null) {
			return false;
		}

		Vec3 away = new Vec3(player.getX() - nearest.getX(), 0.0, player.getZ() - nearest.getZ());
		if (away.lengthSqr() < 1.0E-6) {
			return false;
		}

		Vec3 moved = new Vec3(player.getX() - previous.x, 0.0, player.getZ() - previous.z);
		if (moved.dot(away.normalize()) < STEP) {
			return false;
		}

		Profiles.record(player, Habit.RETREAT);
		return true;
	}
}
