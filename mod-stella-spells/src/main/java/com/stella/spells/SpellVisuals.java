package com.stella.spells;

import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Each spell draws its own particles where it lands, sent from the server so any client sees them. */
public final class SpellVisuals {
	static final double BEAM_STEP_BLOCKS = 0.5;
	static final int RING_POINTS = 24;
	static final int BURST_PARTICLES = 24;
	static final int SPIRAL_POINTS = 20;

	public interface Recorder {
		void played(UUID caster, Identifier spell, ParticleOptions particle, Vec3 at, int particles, boolean casterSaw);
	}

	/** The shape drawn around the landing spot; ringRadius 0 draws a spiral on the caster instead. */
	public record Visual(ParticleOptions primary, ParticleOptions secondary, double ringRadius) {
	}

	private static final Recorder SILENT = (caster, spell, particle, at, particles, casterSaw) -> {
	};
	private static volatile Recorder recorder = SILENT;
	private static final Map<Identifier, Visual> VISUALS = Map.ofEntries(
		visual("spark_shot", ParticleTypes.FLAME, ParticleTypes.SMOKE, 0.0),
		visual("frostbind", ParticleTypes.SNOWFLAKE, ParticleTypes.ITEM_SNOWBALL, 0.0),
		visual("gale_step", ParticleTypes.GUST, ParticleTypes.CLOUD, 0.0),
		visual("mending_word", ParticleTypes.HEART, ParticleTypes.HAPPY_VILLAGER, 0.0),
		visual("stoneguard", ParticleTypes.CRIT, ParticleTypes.ASH, 0.0),
		visual("static_lash", ParticleTypes.ELECTRIC_SPARK, ParticleTypes.WAX_OFF, 4.0),
		visual("briar_snare", ParticleTypes.COMPOSTER, ParticleTypes.SPORE_BLOSSOM_AIR, 0.0),
		visual("tidal_push", ParticleTypes.SPLASH, ParticleTypes.BUBBLE_POP, 0.0),
		visual("owls_gaze", ParticleTypes.ENCHANT, ParticleTypes.GLOW, 0.0),
		visual("slimebound", ParticleTypes.ITEM_SLIME, ParticleTypes.ITEM_SLIME, 0.0),
		visual("chainstorm", ParticleTypes.FIREWORK, ParticleTypes.ELECTRIC_SPARK, 4.0),
		visual("riftwalk", ParticleTypes.PORTAL, ParticleTypes.REVERSE_PORTAL, 0.0),
		visual("phoenix_mantle", ParticleTypes.LAVA, ParticleTypes.SMALL_FLAME, 0.0),
		visual("life_bloom", ParticleTypes.CHERRY_LEAVES, ParticleTypes.HEART, 0.0),
		visual("void_well", ParticleTypes.REVERSE_PORTAL, ParticleTypes.SQUID_INK, 5.0),
		visual("wardens_roar", ParticleTypes.SONIC_BOOM, ParticleTypes.SCULK_SOUL, 5.0),
		visual("aegis_of_the_deep", ParticleTypes.NAUTILUS, ParticleTypes.BUBBLE, 0.0),
		visual("wither_seal", ParticleTypes.LARGE_SMOKE, ParticleTypes.SOUL, 0.0),
		visual("time_lock", ParticleTypes.END_ROD, ParticleTypes.WAX_ON, 5.0),
		visual("dragonfall", ParticleTypes.WITCH, ParticleTypes.EXPLOSION, 3.0)
	);

	private SpellVisuals() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static Visual visual(final Identifier spell) {
		return VISUALS.get(spell);
	}

	/** Plays a cast that already succeeded; departure is where the caster stood before the spell applied. */
	static void play(
		final ServerPlayer caster, final SpellDefinition definition, final Mob target, final Vec3 point,
		final Vec3 departure, final SpellModifiers.CastModifiers modifiers
	) {
		Visual visual = VISUALS.get(definition.id());
		if (visual == null) {
			return;
		}

		ServerLevel level = caster.level();
		Identifier spell = definition.id();
		double radius = visual.ringRadius() * modifiers.radiusMultiplier();
		switch (definition.targetMode()) {
			case HOSTILE -> {
				Vec3 center = target.getBoundingBox().getCenter();
				beam(level, caster, spell, visual.primary(), caster.getEyePosition(), center);
				burst(level, caster, spell, visual.primary(), center, target.getBbWidth());
				burst(level, caster, spell, visual.secondary(), center, target.getBbWidth());
				if (radius > 0.0) {
					ring(level, caster, spell, visual.secondary(), target.position(), radius);
				}
			}
			case SELF -> {
				if (radius > 0.0) {
					ring(level, caster, spell, visual.primary(), departure, radius);
					burst(level, caster, spell, visual.secondary(), departure.add(0.0, 1.0, 0.0), radius);
				} else {
					spiral(level, caster, spell, visual.primary(), departure);
					burst(level, caster, spell, visual.secondary(), departure.add(0.0, 1.0, 0.0), 0.6);
				}
			}
			case BLOCK -> {
				ring(level, caster, spell, visual.primary(), point, radius);
				burst(level, caster, spell, visual.primary(), point, 0.5);
				burst(level, caster, spell, visual.secondary(), point.add(0.0, 0.5, 0.0), Math.max(0.5, radius / 2.0));
			}
			case SAFE_TELEPORT -> {
				spiral(level, caster, spell, visual.primary(), departure);
				spiral(level, caster, spell, visual.primary(), point);
				burst(level, caster, spell, visual.secondary(), point.add(0.0, 1.0, 0.0), 0.6);
			}
		}
	}

	private static void beam(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final ParticleOptions particle,
		final Vec3 from, final Vec3 to
	) {
		Vec3 offset = to.subtract(from);
		int steps = Math.max(1, (int) Math.ceil(offset.length() / BEAM_STEP_BLOCKS));
		int sent = 0;
		boolean casterSaw = false;
		for (int i = 1; i <= steps; i++) {
			Vec3 at = from.add(offset.scale((double) i / steps));
			Emitted emitted = emit(level, caster, particle, at, 1, 0.0, 0.0);
			sent += emitted.particles();
			casterSaw |= emitted.casterSaw();
		}
		recorder.played(caster.getUUID(), spell, particle, to, sent, casterSaw);
	}

	private static void burst(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final ParticleOptions particle,
		final Vec3 at, final double spread
	) {
		Emitted emitted = emit(level, caster, particle, at, BURST_PARTICLES, spread / 2.0, 0.05);
		recorder.played(caster.getUUID(), spell, particle, at, emitted.particles(), emitted.casterSaw());
	}

	private static void ring(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final ParticleOptions particle,
		final Vec3 center, final double radius
	) {
		int sent = 0;
		boolean casterSaw = false;
		for (int i = 0; i < RING_POINTS; i++) {
			double angle = 2.0 * Math.PI * i / RING_POINTS;
			Vec3 at = center.add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
			Emitted emitted = emit(level, caster, particle, at, 1, 0.0, 0.0);
			sent += emitted.particles();
			casterSaw |= emitted.casterSaw();
		}
		recorder.played(caster.getUUID(), spell, particle, center, sent, casterSaw);
	}

	// A rising helix around a standing body, two blocks tall.
	private static void spiral(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final ParticleOptions particle,
		final Vec3 feet
	) {
		int sent = 0;
		boolean casterSaw = false;
		for (int i = 0; i < SPIRAL_POINTS; i++) {
			double angle = 4.0 * Math.PI * i / SPIRAL_POINTS;
			Vec3 at = feet.add(Math.cos(angle) * 0.7, 2.0 * i / SPIRAL_POINTS, Math.sin(angle) * 0.7);
			Emitted emitted = emit(level, caster, particle, at, 1, 0.0, 0.0);
			sent += emitted.particles();
			casterSaw |= emitted.casterSaw();
		}
		recorder.played(caster.getUUID(), spell, particle, feet, sent, casterSaw);
	}

	// Sends to every player in range and reports whether the caster was among them.
	private static Emitted emit(
		final ServerLevel level, final ServerPlayer caster, final ParticleOptions particle, final Vec3 at,
		final int count, final double spread, final double speed
	) {
		int sent = 0;
		boolean casterSaw = false;
		for (ServerPlayer viewer : level.players()) {
			if (level.sendParticles(viewer, particle, false, true, at.x, at.y, at.z, count, spread, spread, spread, speed)) {
				sent += count;
				casterSaw |= viewer == caster;
			}
		}
		return new Emitted(sent, casterSaw);
	}

	private static Map.Entry<Identifier, Visual> visual(
		final String path, final ParticleOptions primary, final ParticleOptions secondary, final double ringRadius
	) {
		return Map.entry(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path), new Visual(primary, secondary, ringRadius));
	}

	private record Emitted(int particles, boolean casterSaw) {
	}
}
