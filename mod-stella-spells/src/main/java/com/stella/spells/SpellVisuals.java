package com.stella.spells;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Each spell draws its own particles where it lands, sent from the server so any client sees them. */
public final class SpellVisuals {
	static final double BEAM_STEP_BLOCKS = 0.5;
	static final double VIEW_RADIUS_BLOCKS = 32.0;
	static final int RING_POINTS = 24;
	static final int BURST_PARTICLES = 24;
	static final int SPIRAL_POINTS = 20;
	static final int INNER_RING_POINTS = 12;
	static final int CIRCLE_SPOKES = 8;
	static final int CIRCLE_SPOKE_POINTS = 2;
	static final int CIRCLE_PULSE_POINTS = 12;
	static final int MAX_MAGIC_CIRCLE_PARTICLES_PER_CAST =
		RING_POINTS + INNER_RING_POINTS + CIRCLE_SPOKES * CIRCLE_SPOKE_POINTS + CIRCLE_PULSE_POINTS * 2;
	private static final int CIRCLE_INNER_FRACTION = 72;
	private static final int[] CIRCLE_SPOKE_FRACTIONS = {32, 56};
	private static final int ANIMATION_DELAY_TICKS = 2;
	private static final int MAX_PENDING_ANIMATIONS = 1024;

	public interface Recorder {
		void played(UUID caster, Identifier spell, ParticleOptions particle, Vec3 at, int particles, boolean casterSaw);
	}

	/** The shape drawn around the landing spot; ringRadius 0 draws a spiral on the caster instead. */
	public record Visual(ParticleOptions primary, ParticleOptions secondary, double ringRadius, Integer elementColor) {
		public Visual {
			if ((ringRadius > 0.0) != (elementColor != null)) {
				throw new IllegalArgumentException("Area visuals require an element color");
			}
		}
	}

	private static final Recorder SILENT = (caster, spell, particle, at, particles, casterSaw) -> {
	};
	private static volatile Recorder recorder = SILENT;
	private static final List<Animation> PENDING_ANIMATIONS = new ArrayList<>();
	private static final Map<Identifier, Visual> VISUALS = Map.ofEntries(
		visual("spark_shot", ParticleTypes.FLAME, ParticleTypes.SMOKE, 0.0),
		visual("frostbind", ParticleTypes.SNOWFLAKE, ParticleTypes.ITEM_SNOWBALL, 0.0),
		visual("gale_step", ParticleTypes.GUST, ParticleTypes.CLOUD, 0.0),
		visual("mending_word", ParticleTypes.HEART, ParticleTypes.HAPPY_VILLAGER, 0.0),
		visual("stoneguard", ParticleTypes.CRIT, ParticleTypes.ASH, 0.0),
		areaVisual("static_lash", ParticleTypes.ELECTRIC_SPARK, ParticleTypes.WAX_OFF, SpellCasting.STATIC_LASH_RADIUS, 0xF5D35E),
		visual("briar_snare", ParticleTypes.COMPOSTER, ParticleTypes.SPORE_BLOSSOM_AIR, 0.0),
		visual("tidal_push", ParticleTypes.SPLASH, ParticleTypes.BUBBLE_POP, 0.0),
		visual("owls_gaze", ParticleTypes.ENCHANT, ParticleTypes.GLOW, 0.0),
		visual("slimebound", ParticleTypes.ITEM_SLIME, ParticleTypes.ITEM_SLIME, 0.0),
		areaVisual("chainstorm", ParticleTypes.FIREWORK, ParticleTypes.ELECTRIC_SPARK, SpellStorms.RADIUS, 0xF5DC65),
		visual("riftwalk", ParticleTypes.PORTAL, ParticleTypes.REVERSE_PORTAL, 0.0),
		visual("phoenix_mantle", ParticleTypes.LAVA, ParticleTypes.SMALL_FLAME, 0.0),
		visual("life_bloom", ParticleTypes.CHERRY_LEAVES, ParticleTypes.HEART, 0.0),
		areaVisual("void_well", ParticleTypes.REVERSE_PORTAL, ParticleTypes.SQUID_INK, SpellCasting.VOID_WELL_RADIUS, 0x7A4BA5),
		areaVisual("wardens_roar", ParticleTypes.SONIC_BOOM, ParticleTypes.SCULK_SOUL, SpellCasting.WARDENS_ROAR_RADIUS, 0x46E4C0),
		visual("aegis_of_the_deep", ParticleTypes.NAUTILUS, ParticleTypes.BUBBLE, 0.0),
		visual("wither_seal", ParticleTypes.LARGE_SMOKE, ParticleTypes.SOUL, 0.0),
		areaVisual("time_lock", ParticleTypes.END_ROD, ParticleTypes.WAX_ON, SpellCasting.TIME_LOCK_RADIUS, 0xD16DE6),
		areaVisual("dragonfall", ParticleTypes.WITCH, ParticleTypes.EXPLOSION, SpellCasting.DRAGONFALL_RADIUS, 0xFF6A3D),
		visual("ice_lance", ParticleTypes.ITEM_SNOWBALL, ParticleTypes.SNOWFLAKE, 0.0),
		visual("soul_siphon", ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL, 0.0),
		visual("venom_dart", ParticleTypes.SNEEZE, ParticleTypes.ITEM_SLIME, 0.0),
		areaVisual("sapping_hex", ParticleTypes.SQUID_INK, ParticleTypes.LARGE_SMOKE, MoreSpells.HEX_RADIUS, 0xA0D84B),
		visual("blinding_flash", ParticleTypes.GLOW_SQUID_INK, ParticleTypes.GLOW, 0.0),
		visual("ember_brand", ParticleTypes.DRIPPING_LAVA, ParticleTypes.SMALL_FLAME, 0.0),
		visual("quicksilver", ParticleTypes.POOF, ParticleTypes.CLOUD, 0.0),
		visual("tide_lungs", ParticleTypes.DOLPHIN, ParticleTypes.BUBBLE, 0.0),
		visual("berserk", ParticleTypes.ANGRY_VILLAGER, ParticleTypes.CRIT, 0.0),
		visual("haste_rune", ParticleTypes.SCRAPE, ParticleTypes.WAX_OFF, 0.0),
		visual("shadow_veil", ParticleTypes.WHITE_ASH, ParticleTypes.SMOKE, 0.0),
		areaVisual("sanctuary", ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.HAPPY_VILLAGER, MoreSpells.SANCTUARY_RADIUS, 0x6ABCCB),
		areaVisual("purify", ParticleTypes.DRIPPING_WATER, ParticleTypes.HAPPY_VILLAGER, MoreSpells.PURIFY_RADIUS, 0xF2F2B2),
		areaVisual("frost_nova", ParticleTypes.WHITE_SMOKE, ParticleTypes.SNOWFLAKE, MoreSpells.FROST_NOVA_RADIUS, 0x74D7FF),
		areaVisual("earthspike", ParticleTypes.MYCELIUM, ParticleTypes.CRIT, MoreSpells.EARTHSPIKE_RADIUS, 0x9A704A),
		areaVisual("plague_cloud", ParticleTypes.WARPED_SPORE, ParticleTypes.ITEM_SLIME, MoreSpells.PLAGUE_RADIUS, 0xA0D84B),
		areaVisual("flame_ring", ParticleTypes.FALLING_LAVA, ParticleTypes.FLAME, MoreSpells.FLAME_RING_RADIUS, 0xFF6A3D),
		areaVisual("gravity", ParticleTypes.CURRENT_DOWN, ParticleTypes.REVERSE_PORTAL, MoreSpells.GRAVITY_RADIUS, 0x7A4BA5),
		areaVisual("bulwark_hymn", ParticleTypes.FALLING_HONEY, ParticleTypes.WAX_ON, MoreSpells.SUPPORT_RADIUS, 0x6ABCCB),
		areaVisual("tailwind", ParticleTypes.SMALL_GUST, ParticleTypes.CLOUD, MoreSpells.SUPPORT_RADIUS, 0xB8E8FF),
		areaVisual("battle_hymn", ParticleTypes.NOTE, ParticleTypes.CRIT, MoreSpells.SUPPORT_RADIUS, 0xF5DC65),
		visual("summon_skeleton", ParticleTypes.SOUL, ParticleTypes.ASH, 0.0)
	);

	static {
		ServerTickEvents.END_SERVER_TICK.register(SpellVisuals::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(SpellVisuals::clearAnimations);
	}

	private SpellVisuals() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static Visual visual(final Identifier spell) {
		return VISUALS.get(spell);
	}

	static java.util.Set<Identifier> areaSpells() {
		return VISUALS.entrySet().stream()
			.filter(entry -> entry.getValue().ringRadius() > 0.0)
			.map(Map.Entry::getKey)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
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
		int circleColor = radius > 0.0 ? visual.elementColor() : definition.tintRgb();
		Vec3 animationCenter = departure;
		switch (definition.targetMode()) {
			case HOSTILE -> {
				Vec3 center = target.getBoundingBox().getCenter();
				beam(level, caster, spell, visual.primary(), caster.getEyePosition(), center);
				burst(level, caster, spell, visual.primary(), center, target.getBbWidth());
				burst(level, caster, spell, visual.secondary(), center, target.getBbWidth());
				if (radius > 0.0) {
					animationCenter = target.position();
					magicCircle(level, caster, spell, circleColor, animationCenter, radius);
				} else {
					animationCenter = center;
				}
			}
			case SELF -> {
				animationCenter = departure;
				if (radius > 0.0) {
					magicCircle(level, caster, spell, circleColor, departure, radius);
					burst(level, caster, spell, visual.primary(), departure.add(0.0, 0.2, 0.0), 0.4);
					burst(level, caster, spell, visual.secondary(), departure.add(0.0, 1.0, 0.0), radius);
				} else {
					spiral(level, caster, spell, visual.primary(), departure);
					burst(level, caster, spell, visual.secondary(), departure.add(0.0, 1.0, 0.0), 0.6);
				}
			}
			case BLOCK -> {
				animationCenter = point;
				if (radius > 0.0) {
					magicCircle(level, caster, spell, circleColor, point, radius);
				}
				burst(level, caster, spell, visual.primary(), point, 0.5);
				burst(level, caster, spell, visual.secondary(), point.add(0.0, 0.5, 0.0), Math.max(0.5, radius / 2.0));
			}
			case SAFE_TELEPORT -> {
				animationCenter = point;
				spiral(level, caster, spell, visual.primary(), departure);
				spiral(level, caster, spell, visual.primary(), point);
				burst(level, caster, spell, visual.secondary(), point.add(0.0, 1.0, 0.0), 0.6);
			}
		}
		scheduleAnimations(level, caster, spell, visual, circleColor, animationCenter, radius);
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

	/** A filled landing glyph: colored circumference, second ring, and eight rune spokes. */
	private static void magicCircle(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final int elementColor,
		final Vec3 center, final double radius
	) {
		DustParticleOptions dust = new DustParticleOptions(elementColor, 1.0F);
		for (int i = 0; i < RING_POINTS; i++) {
			double angle = 2.0 * Math.PI * i / RING_POINTS;
			emitRecorded(level, caster, spell, dust,
				center.add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius));
		}
		for (int i = 0; i < INNER_RING_POINTS; i++) {
			double angle = 2.0 * Math.PI * i / INNER_RING_POINTS;
			emitRecorded(level, caster, spell, dust,
				center.add(Math.cos(angle) * radius * CIRCLE_INNER_FRACTION / 100.0, 0.12,
					Math.sin(angle) * radius * CIRCLE_INNER_FRACTION / 100.0));
		}
		for (int spoke = 0; spoke < CIRCLE_SPOKES; spoke++) {
			double angle = 2.0 * Math.PI * spoke / CIRCLE_SPOKES;
			for (int point = 0; point < CIRCLE_SPOKE_POINTS; point++) {
				double fraction = CIRCLE_SPOKE_FRACTIONS[point] / 100.0;
				emitRecorded(level, caster, spell, dust,
					center.add(Math.cos(angle) * radius * fraction, 0.14,
						Math.sin(angle) * radius * fraction));
			}
		}
	}

	private static Emitted emitRecorded(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell,
		final ParticleOptions particle, final Vec3 at
	) {
		Emitted emitted = emit(level, caster, particle, at, 1, 0.0, 0.0);
		recorder.played(caster.getUUID(), spell, particle, at, emitted.particles(), emitted.casterSaw());
		return emitted;
	}

	private static void scheduleAnimations(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final Visual visual,
		final int elementColor, final Vec3 center, final double radius
	) {
		int now = level.getServer().getTickCount();
		synchronized (PENDING_ANIMATIONS) {
			if (PENDING_ANIMATIONS.size() + 2 > MAX_PENDING_ANIMATIONS) {
				return;
			}
			for (int phase = 1; phase <= 2; phase++) {
				PENDING_ANIMATIONS.add(new Animation(
					level, caster, spell, visual, elementColor, center, radius,
					now + phase * ANIMATION_DELAY_TICKS, phase
				));
			}
		}
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		List<Animation> due = new ArrayList<>();
		synchronized (PENDING_ANIMATIONS) {
			for (Iterator<Animation> iterator = PENDING_ANIMATIONS.iterator(); iterator.hasNext(); ) {
				Animation animation = iterator.next();
				if (animation.level().getServer() != server) {
					iterator.remove();
				} else if (animation.dueTick() <= now) {
					due.add(animation);
					iterator.remove();
				}
			}
		}
		for (Animation animation : due) {
			if (animation.level().getServer() != server || animation.caster().level() != animation.level()
				|| !animation.caster().isAlive()) {
				continue;
			}
			animate(animation);
		}
	}

	private static void clearAnimations(final MinecraftServer server) {
		synchronized (PENDING_ANIMATIONS) {
			PENDING_ANIMATIONS.removeIf(animation -> animation.level().getServer() == server);
		}
	}

	private static void animate(final Animation animation) {
		if (animation.radius() > 0.0) {
			circlePulse(animation);
			return;
		}
		String path = animation.spell().getPath();
		if (path.equals("gale_step") || path.equals("riftwalk")) {
			spiral(animation.level(), animation.caster(), animation.spell(), animation.visual().secondary(), animation.center());
		} else {
			burst(animation.level(), animation.caster(), animation.spell(), animation.visual().secondary(),
				animation.center().add(0.0, 0.6 + animation.phase() * 0.15, 0.0),
				0.35 + animation.phase() * 0.15);
		}
	}

	private static void circlePulse(final Animation animation) {
		DustParticleOptions dust = new DustParticleOptions(animation.elementColor(), 1.0F);
		for (int i = 0; i < CIRCLE_PULSE_POINTS; i++) {
			double angle = 2.0 * Math.PI * (i + animation.phase() * 3) / CIRCLE_PULSE_POINTS;
			Vec3 at = animation.center().add(
				Math.cos(angle) * animation.radius(), 0.18 + animation.phase() * 0.04,
				Math.sin(angle) * animation.radius()
			);
			emitRecorded(animation.level(), animation.caster(), animation.spell(), dust, at);
		}
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
			if (viewer.position().distanceToSqr(at) > VIEW_RADIUS_BLOCKS * VIEW_RADIUS_BLOCKS) {
				continue;
			}
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
		return Map.entry(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path), new Visual(primary, secondary, ringRadius, null));
	}

	private static Map.Entry<Identifier, Visual> areaVisual(
		final String path, final ParticleOptions primary, final ParticleOptions secondary,
		final double radius, final int elementColor
	) {
		return Map.entry(
			Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path),
			new Visual(primary, secondary, radius, elementColor)
		);
	}

	private record Animation(
		ServerLevel level, ServerPlayer caster, Identifier spell, Visual visual, int elementColor,
		Vec3 center, double radius, int dueTick, int phase
	) {
	}

	private record Emitted(int particles, boolean casterSaw) {
	}
}
