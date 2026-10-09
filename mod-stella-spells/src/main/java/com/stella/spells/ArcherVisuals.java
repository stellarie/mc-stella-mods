package com.stella.spells;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The look and sound of the archer skills: a coloured trail, a ring, an impact burst and a layered sound.
 * Everything goes through {@link SpellVisuals}, so a case can count what played. A skill fed by a quiver arrow
 * takes that kind colour.
 */
final class ArcherVisuals {
	/** The colour of a trail that no special arrow feeds. */
	static final int PLAIN_COLOR = 0xF2D86B;
	static final int FIRE_COLOR = 0xFF8A1F;
	static final int FROST_COLOR = 0x4FD8FF;
	static final int VENOM_COLOR = 0x5FD35A;
	static final int BLAST_COLOR = 0xE0303A;
	static final int DIAMOND_COLOR = 0xFFFFFF;
	static final int KEEN_COLOR = 0xA855F7;
	/** Blitz Beat falcon strikes. */
	static final int FALCON_COLOR = 0xE0303A;
	static final float TRAIL_SIZE = 1.2F;
	private static final double TRAIL_STEP_BLOCKS = 0.35;
	private static final int MAX_TRAIL_POINTS = 96;
	private static final int RING_POINTS = 16;
	private static final int IMPACT_RING_POINTS = 12;

	/** The arrow a skill has just spent in a cast, so the cast flourish can take its colour. */
	private static final Map<UUID, ItemStack> NOTED = new ConcurrentHashMap<>();

	private ArcherVisuals() {
	}

	/** The colour of the trail an arrow feeds. */
	static int colorOf(final ItemStack arrow) {
		if (arrow.getItem() instanceof CraftedArrowItem crafted) {
			return switch (crafted.kind()) {
				case FIRE -> FIRE_COLOR;
				case FROST -> FROST_COLOR;
				case VENOM -> VENOM_COLOR;
				case BLAST -> BLAST_COLOR;
				case DIAMOND -> DIAMOND_COLOR;
				case KEEN -> KEEN_COLOR;
				default -> PLAIN_COLOR;
			};
		}
		return PLAIN_COLOR;
	}

	/** Remembers the arrow a cast spent. An empty stack is ignored. */
	static void note(final ServerPlayer caster, final ItemStack arrow) {
		if (!arrow.isEmpty()) {
			NOTED.putIfAbsent(caster.getUUID(), arrow);
		}
	}

	static void forget(final ServerPlayer caster) {
		NOTED.remove(caster.getUUID());
	}

	static void clear() {
		NOTED.clear();
	}

	private static int takeNotedColor(final ServerPlayer caster) {
		ItemStack arrow = NOTED.remove(caster.getUUID());
		return arrow == null ? PLAIN_COLOR : colorOf(arrow);
	}

	/**
	 * The flourish every archer cast gets: a ring around the caster, sparks, an impact burst where it landed
	 * and a layered sound. Called once by the cast, after the skill spent its arrows.
	 */
	static void cast(final ServerLevel level, final ServerPlayer caster, final Identifier spell, final Vec3 casterAt, final Vec3 landing) {
		int color = takeNotedColor(caster);
		ring(level, caster, spell, casterAt.add(0.0, 0.1, 0.0), 1.1, color, RING_POINTS);
		SpellVisuals.particle(level, caster, spell, ParticleTypes.END_ROD, casterAt.add(0.0, 1.0, 0.0), 6, 0.4, 0.04);
		SpellVisuals.sound(level, caster, spell, SoundEvents.ARROW_SHOOT, casterAt, 0.6F, 1.3F);
		SpellVisuals.sound(level, caster, spell, SoundEvents.AMETHYST_BLOCK_CHIME, casterAt, 0.5F, 1.5F);
		if (landing.distanceToSqr(casterAt) > 1.0) {
			impact(level, caster, spell, landing, color);
		}
	}

	/** A coloured trail from one point to another, with glints and sparks along it. */
	static void trail(final ServerLevel level, final ServerPlayer caster, final Identifier spell, final Vec3 from, final Vec3 to, final int color) {
		double reach = from.distanceTo(to);
		if (reach < 1.0E-6) {
			return;
		}
		Vec3 step = to.subtract(from).scale(1.0 / reach);
		DustParticleOptions dust = new DustParticleOptions(color, TRAIL_SIZE);
		int placed = 0;
		for (double flown = TRAIL_STEP_BLOCKS; flown <= reach && placed < MAX_TRAIL_POINTS; flown += TRAIL_STEP_BLOCKS, placed++) {
			Vec3 at = from.add(step.scale(flown));
			SpellVisuals.particle(level, caster, spell, dust, at, 1, 0.0, 0.0);
			if (placed % 3 == 0) {
				SpellVisuals.particle(level, caster, spell, ParticleTypes.CRIT, at, 1, 0.05, 0.0);
			}
		}
		SpellVisuals.particle(level, caster, spell, ParticleTypes.END_ROD, from.add(step.scale(Math.min(1.0, reach))), 3, 0.1, 0.02);
	}

	/** A burst where a shot lands: a coloured ring, sparks, and two layered sounds. */
	static void impact(final ServerLevel level, final ServerPlayer caster, final Identifier spell, final Vec3 at, final int color) {
		ring(level, caster, spell, at, 0.7, color, IMPACT_RING_POINTS);
		SpellVisuals.particle(level, caster, spell, ParticleTypes.FIREWORK, at, 10, 0.3, 0.06);
		SpellVisuals.particle(level, caster, spell, ParticleTypes.ENCHANTED_HIT, at, 8, 0.3, 0.1);
		SpellVisuals.sound(level, caster, spell, SoundEvents.ARROW_HIT, at, 0.7F, 1.4F);
		SpellVisuals.sound(level, caster, spell, SoundEvents.FIREWORK_ROCKET_TWINKLE, at, 0.5F, 1.2F);
	}

	/** A flat ring of coloured dust. */
	static void ring(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final Vec3 center,
		final double radius, final int color, final int points
	) {
		DustParticleOptions dust = new DustParticleOptions(color, TRAIL_SIZE);
		for (int i = 0; i < points; i++) {
			double angle = 2.0 * Math.PI * i / points;
			SpellVisuals.particle(level, caster, spell, dust, center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius), 1, 0.0, 0.0);
		}
	}
}
