package com.stella.learn.model;

import com.stella.learn.Habit;
import java.util.List;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;

/**
 * What one model-driven archer remembers between ticks: its draw, its volley timer, its last action, the last place it
 * saw its target, and the goals it gave up. A test can build one to read an observation from a mob that no driver owns.
 */
public final class ArcherState {
	public static final int VOLLEY_COOLDOWN_TICKS = 120;
	/** Ticks since the target was seen stop counting here, and stand for "never seen". */
	public static final int SIGHT_CAP_TICKS = 200;
	static final int PREVIOUS_SIZE = 6;
	/** How many of the archer's own arrow hits the mean covers. */
	public static final int HIT_WINDOW = 5;

	/** Ticks this goal has run. */
	int goalTicks;
	int drawTicks;
	/** A fire decision commits the shot; it leaves at a full draw whatever the bow head says meanwhile. */
	boolean committed;
	boolean volleyWanted;
	int idleStreak;
	int sinceVolley = VOLLEY_COOLDOWN_TICKS;
	ArcherAction action = ArcherAction.STILL;
	final float[] previous = new float[PREVIOUS_SIZE];
	/** The model's own pitch in degrees. The mob's look control resets its pitch, so the mob's value is not kept. */
	float pitch;
	boolean seen;
	/** Whether the target was in sight at the last decision, and where its aim point was the last time it was. */
	boolean visible;
	double aimX;
	double aimY;
	double aimZ;
	double seenX;
	double seenY;
	double seenZ;
	int seenTick;
	final float[] rays = new float[Rays.COUNT];
	boolean raysValid;
	double rayX;
	double rayY;
	double rayZ;
	float rayYaw;
	int rayTick;
	final float[] profile = new float[Habit.values().length];
	boolean profileValid;
	int profileTick;
	final float[] hitFractions = new float[HIT_WINDOW];
	int hitCount;
	float lastHit;
	int lastHitTick;
	int nextActLog;
	int releases;
	int volleys;
	boolean retiring;
	List<WrappedGoal> removed = List.of();
	LearnedArcherGoal goal;
	private float[] observation = new float[0];

	/** Notes an arrow of this archer that hurt its target: the damage as a fraction of the target's maximum health. */
	public void recordHit(final float damage, final float targetMaxHealth) {
		float fraction = damage / Math.max(targetMaxHealth, 1.0F);
		this.hitFractions[this.hitCount % HIT_WINDOW] = fraction;
		this.hitCount++;
		this.lastHit = fraction;
		this.lastHitTick = this.goalTicks;
	}

	public int drawTicks() {
		return this.drawTicks;
	}

	public int releases() {
		return this.releases;
	}

	public int volleys() {
		return this.volleys;
	}

	public ArcherAction lastAction() {
		return this.action;
	}

	float[] observation(final int size) {
		if (this.observation.length != size) {
			this.observation = new float[size];
		}

		return this.observation;
	}

	/** The model's look direction: yaw from the mob, pitch from this state. */
	Vec3 lookVector(final float yawDegrees) {
		double pitch = Math.toRadians(this.pitch);
		double yaw = -Math.toRadians(yawDegrees);
		double cosYaw = Math.cos(yaw);
		double sinYaw = Math.sin(yaw);
		double cosPitch = Math.cos(pitch);
		return new Vec3(sinYaw * cosPitch, -Math.sin(pitch), cosYaw * cosPitch);
	}
}
