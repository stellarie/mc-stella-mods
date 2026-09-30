package com.stella.learn.model;

import com.stella.learn.Habit;
import com.stella.learn.LearnClock;
import com.stella.learn.LearnConfig;
import com.stella.learn.LearnConfigStore;
import com.stella.learn.PlayerProfile;
import com.stella.learn.Profiles;
import com.stella.mobs.behavior.ArcherKit;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Elite;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Builds the archer observation for the contract. The raw values are physical: blocks, ticks, fractions. The last step
 * divides each by its contract scale and clamps to plus or minus four. The field order lives in the contract JSON;
 * {@link #mismatch} checks that this class still writes that order.
 */
public final class ArcherObservation {
	private static final String[] SELF = {
		"health_fraction", "max_health", "speed", "vel_x", "vel_y", "vel_z", "on_ground", "in_water", "pitch",
		"draw_ticks", "volley_ready", "volley_size", "elite", "boss", "ticks_since_hurt"
	};
	private static final String[] TARGET = {
		"visible", "rel_x", "rel_y", "rel_z", "vel_x", "vel_y", "vel_z", "distance", "height_diff", "facing", "health_fraction",
		"armor", "blocking", "drawing_bow", "airborne", "held_sword", "held_axe", "held_bow", "held_crossbow", "held_shield",
		"held_other", "ticks_since_seen"
	};
	/** The archer's memory of its own hits on the target; all zero before the first one. */
	private static final String[] HITS = {"last_hit", "mean_hit", "hits_needed", "ticks_since_hit", "has_hit"};
	private static final String[] ARROW = {"present", "rel_x", "rel_y", "rel_z", "vel_x", "vel_y", "vel_z"};
	private static final String[] ALLY = {"present", "rel_x", "rel_y", "rel_z", "vel_x", "vel_y", "vel_z", "health_fraction", "drawing_bow"};
	private static final String[] PREVIOUS = {"forward", "strafe", "yaw", "pitch", "jump", "bow"};
	private static final int ALLY_SLOTS = 2;
	private static final Habit[] HABITS = Habit.values();
	private static final List<String> LAYOUT = layout();
	private static final int HURT_CAP = 200;
	private static final int PROFILE_REFRESH_TICKS = 10;

	private ArcherObservation() {
	}

	/** Every field name in the order this class writes it. */
	public static List<String> layout() {
		List<String> names = new ArrayList<>();
		for (String field : SELF) {
			names.add("self." + field);
		}

		for (String field : TARGET) {
			names.add("target." + field);
		}

		for (String field : HITS) {
			names.add("target." + field);
		}

		for (int i = 0; i < Rays.HORIZONTAL; i++) {
			names.add("ray.h" + i);
		}

		for (int i = 0; i < Rays.DOWN; i++) {
			names.add("ray.d" + i);
		}

		for (int i = 0; i < Rays.UP; i++) {
			names.add("ray.u" + i);
		}

		for (int i = 0; i < Rays.DIAGONAL; i++) {
			names.add("ray.diag" + i);
		}

		for (int slot = 0; slot < PlayerArrows.SLOTS; slot++) {
			for (String field : ARROW) {
				names.add("arrow" + slot + "." + field);
			}
		}

		for (Habit habit : Habit.values()) {
			names.add("profile." + habit.name().toLowerCase(Locale.ROOT));
		}

		for (int slot = 0; slot < ALLY_SLOTS; slot++) {
			for (String field : ALLY) {
				names.add("ally" + slot + "." + field);
			}
		}

		for (String field : PREVIOUS) {
			names.add("prev." + field);
		}

		return List.copyOf(names);
	}

	/** Null when the contract lists exactly the fields this class writes, in order; else the first difference. */
	public static @Nullable String mismatch(final Contract contract) {
		if (contract.observationSize() != LAYOUT.size()) {
			return "contract holds " + contract.observationSize() + " fields, the builder writes " + LAYOUT.size();
		}

		for (int i = 0; i < LAYOUT.size(); i++) {
			if (!LAYOUT.get(i).equals(contract.name(i))) {
				return "field " + i + " is " + contract.name(i) + " in the contract and " + LAYOUT.get(i) + " in the builder";
			}
		}

		return null;
	}

	public static float[] build(final Mob mob, final @Nullable LivingEntity target, final ArcherState state) {
		float[] out = new float[Contract.archer().observationSize()];
		build(mob, target, state, out);
		return out;
	}

	/** Fills `out`, whose length is the contract size, with the scaled observation of `mob` looking at `target`. */
	public static void build(final Mob mob, final @Nullable LivingEntity target, final ArcherState state, final float[] out) {
		Contract contract = Contract.archer();
		LearnConfig config = LearnConfigStore.get();
		double yaw = Math.toRadians(mob.getYRot());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double rightX = -Math.cos(yaw);
		double rightZ = -Math.sin(yaw);
		int at = self(mob, state, out, 0, forwardX, forwardZ, rightX, rightZ);
		at = target(mob, target, state, out, at, forwardX, forwardZ, rightX, rightZ);
		at = hits(target, state, out, at);
		at = rays(mob, state, out, at);
		at = PlayerArrows.fill(mob, out, at, forwardX, forwardZ, rightX, rightZ);
		at = profile(target, state, config, out, at);
		for (int i = 0; i < ALLY_SLOTS * ALLY.length; i++) {
			out[at++] = 0.0F;
		}

		for (int i = 0; i < ArcherState.PREVIOUS_SIZE; i++) {
			out[at++] = state.previous[i];
		}

		for (int i = 0; i < out.length; i++) {
			float scaled = out[i] / contract.scale(i);
			out[i] = Float.isFinite(scaled) ? Mth.clamp(scaled, -Contract.CLAMP, Contract.CLAMP) : 0.0F;
		}
	}

	private static int self(
		final Mob mob, final ArcherState state, final float[] out, final int start,
		final double forwardX, final double forwardZ, final double rightX, final double rightZ
	) {
		int i = start;
		Vec3 motion = mob.getDeltaMovement();
		out[i++] = mob.getHealth() / Math.max(mob.getMaxHealth(), 1.0F);
		out[i++] = mob.getMaxHealth();
		out[i++] = (float) mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
		out[i++] = (float) (motion.x * rightX + motion.z * rightZ);
		out[i++] = (float) motion.y;
		out[i++] = (float) (motion.x * forwardX + motion.z * forwardZ);
		out[i++] = mob.onGround() ? 1.0F : 0.0F;
		out[i++] = mob.isInWater() ? 1.0F : 0.0F;
		out[i++] = state.pitch;
		out[i++] = state.drawTicks;
		out[i++] = Math.min(1.0F, (float) state.sinceVolley / ArcherState.VOLLEY_COOLDOWN_TICKS);
		out[i++] = ArcherKit.volleySize(mob);
		out[i++] = Elite.isElite(mob) ? 1.0F : 0.0F;
		out[i++] = Bosses.isBoss(mob) ? 1.0F : 0.0F;
		// A timestamp of zero means the mob was never hurt by a mob or player.
		int hurt = mob.getLastHurtByMobTimestamp();
		out[i++] = hurt == 0 ? HURT_CAP : Math.min(HURT_CAP, Math.max(0, mob.tickCount - hurt));
		return i;
	}

	private static int target(
		final Mob mob, final @Nullable LivingEntity target, final ArcherState state, final float[] out, final int start,
		final double forwardX, final double forwardZ, final double rightX, final double rightZ
	) {
		int i = start;
		for (int k = 0; k < TARGET.length; k++) {
			out[i + k] = 0.0F;
		}

		Vec3 eye = mob.getEyePosition();
		long sightBegan = ModelPerf.recording() ? System.nanoTime() : 0L;
		boolean visible = target != null && Rays.sees(mob, target);
		if (sightBegan != 0L) {
			ModelPerf.phase(ModelPerf.Phase.SIGHT, System.nanoTime() - sightBegan);
		}

		if (visible) {
			Vec3 center = target.getBoundingBox().getCenter();
			state.seen = true;
			state.seenX = center.x;
			state.seenY = center.y;
			state.seenZ = center.z;
			state.seenTick = state.goalTicks;
			Vec3 relative = center.subtract(eye);
			Vec3 motion = target.getKnownMovement().subtract(mob.getDeltaMovement());
			Vec3 look = target.getLookAngle();
			Vec3 toMob = eye.subtract(target.getEyePosition());
			double length = toMob.length();
			out[i++] = 1.0F;
			out[i++] = (float) (relative.x * rightX + relative.z * rightZ);
			out[i++] = (float) relative.y;
			out[i++] = (float) (relative.x * forwardX + relative.z * forwardZ);
			out[i++] = (float) (motion.x * rightX + motion.z * rightZ);
			out[i++] = (float) motion.y;
			out[i++] = (float) (motion.x * forwardX + motion.z * forwardZ);
			out[i++] = mob.distanceTo(target);
			out[i++] = (float) (target.getY() - mob.getY());
			out[i++] = length < 1.0E-6 ? 0.0F : (float) (look.dot(toMob) / length);
			out[i++] = target.getHealth() / Math.max(target.getMaxHealth(), 1.0F);
			out[i++] = target.getArmorValue();
			out[i++] = target.isBlocking() ? 1.0F : 0.0F;
			out[i++] = target.isUsingItem() && target.getUseItem().is(Items.BOW) ? 1.0F : 0.0F;
			out[i++] = target.onGround() ? 0.0F : 1.0F;
			out[i + heldSlot(target.getMainHandItem())] = 1.0F;
			i += 6;
			out[i++] = 0.0F;
			return i;
		}

		// A hidden target keeps only the last place it was seen and how long ago.
		i++;
		if (state.seen) {
			out[i] = (float) ((state.seenX - eye.x) * rightX + (state.seenZ - eye.z) * rightZ);
			out[i + 1] = (float) (state.seenY - eye.y);
			out[i + 2] = (float) ((state.seenX - eye.x) * forwardX + (state.seenZ - eye.z) * forwardZ);
		}

		i = start + TARGET.length - 1;
		out[i++] = state.seen ? Math.min(ArcherState.SIGHT_CAP_TICKS, state.goalTicks - state.seenTick) : ArcherState.SIGHT_CAP_TICKS;
		return i;
	}

	private static int hits(final @Nullable LivingEntity target, final ArcherState state, final float[] out, final int start) {
		int i = start;
		if (state.hitCount == 0) {
			for (int k = 0; k < HITS.length; k++) {
				out[i++] = 0.0F;
			}

			return i;
		}

		int counted = Math.min(state.hitCount, ArcherState.HIT_WINDOW);
		float sum = 0.0F;
		for (int k = 0; k < counted; k++) {
			sum += state.hitFractions[k];
		}

		float mean = sum / counted;
		out[i++] = state.lastHit;
		out[i++] = mean;
		// Health left over the mean hit, in whole hits: the target's current fraction of its health over the mean fraction.
		out[i++] = target == null || mean <= 0.0F ? 0.0F : target.getHealth() / Math.max(target.getMaxHealth(), 1.0F) / mean;
		out[i++] = Math.max(0, state.goalTicks - state.lastHitTick);
		out[i++] = 1.0F;
		return i;
	}

	private static int heldSlot(final ItemStack held) {
		if (held.is(ItemTags.SWORDS)) {
			return 0;
		}

		if (held.is(ItemTags.AXES)) {
			return 1;
		}

		if (held.is(Items.BOW)) {
			return 2;
		}

		if (held.is(Items.CROSSBOW)) {
			return 3;
		}

		return held.is(Items.SHIELD) ? 4 : 5;
	}

	private static int rays(final Mob mob, final ArcherState state, final float[] out, final int start) {
		float yaw = mob.getYRot();
		boolean reuse = state.raysValid
			&& distanceSqr(mob, state) < Rays.REUSE_BLOCKS_SQR
			&& Math.abs(Mth.wrapDegrees(yaw - state.rayYaw)) < Rays.REUSE_DEGREES
			&& state.goalTicks - state.rayTick < Rays.REUSE_TICKS && state.goalTicks >= state.rayTick;
		if (!reuse) {
			long began = ModelPerf.recording() ? System.nanoTime() : 0L;
			Rays.fill(mob, state.rays);
			if (began != 0L) {
				ModelPerf.phase(ModelPerf.Phase.RAYS, System.nanoTime() - began);
			}

			state.raysValid = true;
			state.rayX = mob.getX();
			state.rayY = mob.getY();
			state.rayZ = mob.getZ();
			state.rayYaw = yaw;
			state.rayTick = state.goalTicks;
		}

		System.arraycopy(state.rays, 0, out, start, Rays.COUNT);
		return start + Rays.COUNT;
	}

	private static double distanceSqr(final Mob mob, final ArcherState state) {
		double dx = mob.getX() - state.rayX;
		double dy = mob.getY() - state.rayY;
		double dz = mob.getZ() - state.rayZ;
		return dx * dx + dy * dy + dz * dz;
	}

	/** The nine pressures change by whole counts against a half life of minutes, so an archer reads them once in a while. */
	private static int profile(final @Nullable LivingEntity target, final ArcherState state, final LearnConfig config, final float[] out, final int start) {
		int i = start;
		if (!(target instanceof ServerPlayer player)) {
			for (int k = 0; k < HABITS.length; k++) {
				out[i++] = 0.0F;
			}

			return i;
		}

		if (!state.profileValid || state.goalTicks - state.profileTick >= PROFILE_REFRESH_TICKS || state.goalTicks < state.profileTick) {
			PlayerProfile profile = Profiles.get(player);
			long now = LearnClock.now();
			long halfLife = config.profileHalfLifeTicks();
			for (Habit habit : HABITS) {
				state.profile[habit.ordinal()] = (float) Mth.clamp(profile.value(habit, now, halfLife) / saturation(config, habit), 0.0, 1.0);
			}

			state.profileValid = true;
			state.profileTick = state.goalTicks;
		}

		System.arraycopy(state.profile, 0, out, i, HABITS.length);
		return i + HABITS.length;
	}

	private static double saturation(final LearnConfig config, final Habit habit) {
		return switch (habit) {
			case BLOCK -> config.shieldSaturation();
			case MELEE -> config.rushSaturation();
			case RANGED -> config.rangedSaturation();
			case RETREAT -> config.retreatSaturation();
			case STRAFE_LEFT, STRAFE_RIGHT, CHARGE, KITE -> config.moveSaturation();
			case CRIT -> config.critSaturation();
		};
	}
}
