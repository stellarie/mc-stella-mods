package com.stella.learn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Counts how a player moves against the monster after them. Every few ticks it compares where each player stands with
 * where they stood at the last sample, split into the part toward the nearest monster that targets them and the part
 * across it: a sideways step counts as a strafe left or right as the player sees it facing that monster, and a step
 * straight at it counts as a charge. The sample also leaves the player's velocity, which the intercept goal reads.
 */
public final class MovementRecorder {
	/** Blocks sideways in one sample that count as a strafe. */
	public static final double STRAFE_STEP = 0.6;
	/** Blocks straight toward the monster in one sample that count as a charge. */
	public static final double CHARGE_STEP = 0.8;
	public static final int SAMPLE_TICKS = 5;
	static final AttachmentType<Vec3> VELOCITY = AttachmentRegistry.create(StellaLearn.id("player_velocity"));
	private static final AttachmentType<Vec3> LAST = AttachmentRegistry.create(StellaLearn.id("movement_last"));

	private MovementRecorder() {
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

	/** The player's velocity in blocks per tick as of the last sample, or null before the first. */
	public static @Nullable Vec3 velocityOf(final ServerPlayer player) {
		return ((AttachmentTarget) player).getAttached(VELOCITY);
	}

	/** Compares the player's position with where they stood at the last sample; returns what it counted. */
	static List<Habit> sampleFrom(final ServerPlayer player, final Vec3 previous) {
		Vec3 moved = new Vec3(player.getX() - previous.x, 0.0, player.getZ() - previous.z);
		((AttachmentTarget) player).setAttached(VELOCITY, moved.scale(1.0 / SAMPLE_TICKS));
		List<Habit> counted = new ArrayList<>();
		Monster nearest = player.level().getEntitiesOfClass(
			Monster.class, player.getBoundingBox().inflate(RetreatRecorder.RANGE), monster -> monster.isAlive() && monster.getTarget() == player
		).stream().filter(monster -> monster.distanceTo(player) <= RetreatRecorder.RANGE)
			.min(Comparator.comparingDouble(monster -> monster.distanceToSqr(player))).orElse(null);
		if (nearest == null) {
			return counted;
		}

		Vec3 toward = new Vec3(nearest.getX() - player.getX(), 0.0, nearest.getZ() - player.getZ());
		if (toward.lengthSqr() < 1.0E-6) {
			return counted;
		}

		toward = toward.normalize();
		// Facing the monster, the player's right hand is the direction turned a quarter turn clockwise.
		Vec3 right = new Vec3(-toward.z, 0.0, toward.x);
		double lateral = moved.dot(right);
		if (lateral >= STRAFE_STEP) {
			counted.add(Habit.STRAFE_RIGHT);
		} else if (lateral <= -STRAFE_STEP) {
			counted.add(Habit.STRAFE_LEFT);
		}

		if (moved.dot(toward) >= CHARGE_STEP) {
			counted.add(Habit.CHARGE);
		}

		counted.forEach(habit -> Profiles.record(player, habit));
		return counted;
	}
}
