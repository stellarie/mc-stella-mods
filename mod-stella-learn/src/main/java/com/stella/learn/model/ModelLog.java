package com.stella.learn.model;

import com.stella.learn.LearnLog;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The `learnModel` lines. Each names the mob, its tick, and the numbers behind the event; a fault or a fall back
 * carries a `reason=` from a fixed list: missing, contract, format, nan, switch, gear, error. ACT is a sampled
 * per-decision line, so it goes to the debug log; everything else is an event at INFO.
 */
public final class ModelLog {
	static final String TEST = "learnModel";

	private ModelLog() {
	}

	static void load(final String status, final String hash, final int size, final String detail) {
		LearnLog.info(TEST, "LOAD", "status=" + status + " hash=" + hash + " size=" + size + (detail.isEmpty() ? "" : " " + detail));
	}

	static void install(final Mob mob, final List<String> removed, final String source) {
		LearnLog.info(TEST, "INSTALL", LearnLog.who(mob) + " tick=" + mob.tickCount + " source=" + source + " removed=" + removed);
	}

	static void fallback(final Mob mob, final String reason, final String detail) {
		LearnLog.info(TEST, "FALLBACK", LearnLog.who(mob) + " tick=" + mob.tickCount + " reason=" + reason + (detail.isEmpty() ? "" : " " + detail));
	}

	static void act(final Mob mob, final LivingEntity target, final ArcherAction action, final int goalTicks) {
		String who = target instanceof ServerPlayer player ? LearnLog.who(player) : "target=" + target.getType();
		LearnLog.sampled(TEST, "ACT", LearnLog.who(mob) + " " + who + " tick=" + mob.tickCount + " goal_tick=" + goalTicks
			+ " forward=" + hundredths(action.forward()) + " strafe=" + hundredths(action.strafe())
			+ " yaw=" + hundredths(action.yaw()) + " pitch=" + hundredths(action.pitch())
			+ " jump=" + action.jump() + " bow=" + action.bow() + " distance=" + hundredths(mob.distanceTo(target)));
	}

	static void release(final Mob mob, final int drawTicks, final float power, final boolean volleyAsked, final boolean volley, final int arrows) {
		LearnLog.info(TEST, "RELEASE", LearnLog.who(mob) + " tick=" + mob.tickCount + " draw_ticks=" + drawTicks
			+ " power=" + LearnLog.num(power) + " volley_asked=" + volleyAsked + " volley=" + volley + " arrows=" + arrows);
	}

	static void latch(final Mob mob, final boolean volley, final int drawTicks) {
		LearnLog.sampled(TEST, "FIRE_LATCH", LearnLog.who(mob) + " tick=" + mob.tickCount + " volley=" + volley + " draw_ticks=" + drawTicks);
	}

	static void cancel(final Mob mob, final int drawTicks) {
		LearnLog.sampled(TEST, "CANCEL", LearnLog.who(mob) + " tick=" + mob.tickCount + " draw_ticks=" + drawTicks + " reason=idle");
	}

	/** Debug level: an archer type that was not made model driven, and why. A seated archer is rare and worth an info line. */
	static void skip(final Mob mob, final String reason) {
		String detail = LearnLog.who(mob) + " tick=" + mob.tickCount + " reason=" + reason;
		if ("passenger".equals(reason)) {
			LearnLog.info(TEST, "SKIP", detail);
		} else {
			LearnLog.debug(TEST, "SKIP", detail);
		}
	}

	static void perf(final int archers, final int ticks, final double meanMs, final double p95Ms, final double maxMs, final double[] phaseMeanMs) {
		LearnLog.info(TEST, "PERF", "archers=" + archers + " ticks=" + ticks + " mean_ms=" + ms(meanMs) + " p95_ms=" + ms(p95Ms) + " max_ms=" + ms(maxMs)
			+ " observe_ms=" + ms(phaseMeanMs[0]) + " decide_ms=" + ms(phaseMeanMs[1]) + " act_ms=" + ms(phaseMeanMs[2])
			+ " sight_ms=" + ms(phaseMeanMs[3]) + " rays_ms=" + ms(phaseMeanMs[4]) + " release_ms=" + ms(phaseMeanMs[5]));
	}

	/** A number to two places without the format machinery, since a busy server writes thousands of these. */
	private static String hundredths(final double value) {
		return Double.toString(Math.round(value * 100.0) / 100.0);
	}

	private static String ms(final double value) {
		return String.format(java.util.Locale.ROOT, "%.4f", value);
	}
}
