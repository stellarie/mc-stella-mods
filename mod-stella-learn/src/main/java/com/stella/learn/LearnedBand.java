package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * A ranged or magic mob's shooting band, pushed outward against a player who rushes. The mob's own band is kept
 * beside it, so the learned band is always the base plus a shift and never drifts by compounding.
 *
 * <p>The base is not saved. The mobs mod builds a mob's band again each time the mob loads, and a saved base could
 * only be older than that band. Saves from builds that did write it keep loading: the entry is skipped.
 */
public final class LearnedBand {
	/** The mob's own band, as the mobs mod built it. */
	public record Base(double near, double far) {
	}

	/** Blocks the band must move from the last logged value before a new line is written. */
	static final double LOG_STEP = 0.5;
	private static final AttachmentType<Double> LOGGED_SHIFT = AttachmentRegistry.create(StellaLearn.id("band_logged_shift"));
	/**
	 * The id stays as older builds saved it.
	 * An old save then still names a known type.
	 */
	public static final AttachmentType<Base> BASE = AttachmentRegistry.create(StellaLearn.id("band_base"));

	private LearnedBand() {
	}

	public static void register() {
		StellaLearn.LOGGER.debug("Registered the band base attachment {}", BASE.identifier());
	}

	/** How much of a full rusher this player is, from 0 to 1. */
	public static double rushPressure(final ServerPlayer player) {
		return Mth.clamp(Profiles.value(player, Habit.MELEE) / LearnConfigStore.get().rushSaturation(), 0.0, 1.0);
	}

	public static void apply(final Mob mob, final ServerPlayer player) {
		ReactiveState state = ReactiveState.get(mob);
		if (state.getRole() == CombatRole.MELEE) {
			return;
		}

		Base base = baseOf(mob, state);
		double pressure = rushPressure(player);
		double shift = LearnConfigStore.get().rushBandGain() * pressure;
		set(state, base.near() + shift, base.far() + shift);
		logShift(mob, player, base, state, pressure, shift);
	}

	/** A band that has moved by half a block since the last line written is a change worth a line; a creep is not. */
	private static void logShift(final Mob mob, final ServerPlayer player, final Base base, final ReactiveState state, final double pressure, final double shift) {
		Double last = ((AttachmentTarget) mob).getAttached(LOGGED_SHIFT);
		double before = last == null ? 0.0 : last;
		if (Math.abs(shift - before) < LOG_STEP) {
			return;
		}

		((AttachmentTarget) mob).setAttached(LOGGED_SHIFT, shift);
		LearnLog.info("learnBand", "SHIFT", LearnLog.who(mob) + " " + LearnLog.who(player) + " rush=" + LearnLog.num(pressure)
			+ " near=" + LearnLog.num(base.near() + before) + "->" + LearnLog.num(state.getNearDistance())
			+ " far=" + LearnLog.num(base.far() + before) + "->" + LearnLog.num(state.getFarDistance()));
	}

	/** Puts the mob's own band back, for a mob whose target is not a player. */
	public static void restore(final Mob mob) {
		Base base = ((AttachmentTarget) mob).getAttached(BASE);
		if (base != null) {
			ReactiveState state = ReactiveState.get(mob);
			set(state, base.near(), base.far());
			Double last = ((AttachmentTarget) mob).getAttached(LOGGED_SHIFT);
			if (last != null && Math.abs(last) >= LOG_STEP) {
				LearnLog.info("learnBand", "RESTORE", LearnLog.who(mob) + " near=" + LearnLog.num(base.near() + last) + "->" + LearnLog.num(base.near())
					+ " far=" + LearnLog.num(base.far() + last) + "->" + LearnLog.num(base.far()));
			}

			((AttachmentTarget) mob).setAttached(LOGGED_SHIFT, 0.0);
		}
	}

	private static Base baseOf(final Mob mob, final ReactiveState state) {
		Base base = ((AttachmentTarget) mob).getAttached(BASE);
		if (base == null) {
			base = new Base(state.getNearDistance(), state.getFarDistance());
			((AttachmentTarget) mob).setAttached(BASE, base);
		}

		return base;
	}

	private static void set(final ReactiveState state, final double near, final double far) {
		if (state.getNearDistance() != near || state.getFarDistance() != far) {
			state.setRoleSpec(new RoleSpec(state.getRole(), near, far));
		}
	}

	/** The mob's own band, or null if this mod has never shifted it. */
	public static @Nullable Base baseBand(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(BASE);
	}
}
