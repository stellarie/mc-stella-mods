package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Surprise, spells: two spells cast in the right order land a combo. The second spell must follow the
 * first within five seconds, on the same mob for hostile spells or near the same spot for area spells.
 * A landed combo spends the pair, so one opener never feeds two combos.
 */
public final class SpellCombos {
	public static final int WINDOW_TICKS = 100;
	public static final double SAME_SPOT_BLOCKS = 3.0;
	public static final float THERMAL_SHOCK_DAMAGE = 4.0f;
	public static final float RIPTIDE_DAMAGE = 3.0f;
	public static final float FROZEN_METEOR_DAMAGE = 5.0f;
	public static final double FROZEN_METEOR_RADIUS = 3.0;
	public static final int BLIGHT_TICKS = 100;
	public static final int RIPTIDE_SLOW_TICKS = 60;
	public static final int BASTION_TICKS = 160;

	/** One combo: the spell that opens it, the spell that finishes it, and its name. */
	public record Combo(Identifier first, Identifier second, String name) {
	}

	/** Test seam: each combo a caster landed. */
	public interface Recorder {
		void landed(ServerPlayer caster, Combo combo, LivingEntity target);
	}

	private record Cast(Identifier spell, @Nullable UUID target, @Nullable Vec3 point, int serverTick) {
	}

	private static final Recorder SILENT = (caster, combo, target) -> {
	};
	private static volatile Recorder recorder = SILENT;
	private static final Map<UUID, Cast> LAST = new ConcurrentHashMap<>();
	private static final Combo THERMAL_SHOCK = combo("frostbind", "spark_shot", "Thermal Shock");
	private static final Combo BLIGHT_BLOOM = combo("briar_snare", "wither_seal", "Blight Bloom");
	private static final Combo RIPTIDE = combo("static_lash", "tidal_push", "Riptide Current");
	private static final Combo BASTION = combo("stoneguard", "aegis_of_the_deep", "Bastion");
	private static final Combo FROZEN_METEOR = combo("time_lock", "dragonfall", "Frozen Meteor");
	private static final List<Combo> COMBOS = List.of(THERMAL_SHOCK, BLIGHT_BLOOM, RIPTIDE, BASTION, FROZEN_METEOR);

	private SpellCombos() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static List<Combo> combos() {
		return COMBOS;
	}

	/** The combo this spell opens or finishes, if any, for its tooltip. */
	public static Optional<Combo> involving(final Identifier spell) {
		return COMBOS.stream().filter(combo -> combo.first().equals(spell) || combo.second().equals(spell)).findFirst();
	}

	/** Called after every successful cast; lands a combo when this cast finishes one. */
	static void afterCast(
		final ServerPlayer caster, final SpellDefinition definition, final @Nullable Mob target, final @Nullable Vec3 point,
		final SpellModifiers.CastModifiers modifiers
	) {
		int now = caster.level().getServer().getTickCount();
		Cast previous = LAST.get(caster.getUUID());
		Cast current = new Cast(definition.id(), target == null ? null : target.getUUID(), point, now);
		Combo combo = previous == null || now - previous.serverTick() > WINDOW_TICKS ? null : match(previous, current);
		if (combo == null) {
			LAST.put(caster.getUUID(), current);
			return;
		}

		LAST.remove(caster.getUUID());
		LivingEntity struck = land(caster, combo, target, point, modifiers);
		recorder.landed(caster, combo, struck);
		caster.sendSystemMessage(Component.literal("Combo: " + combo.name() + "!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		ServerLevel level = caster.level();
		Vec3 at = struck.position().add(0.0, struck.getBbHeight() / 2.0, 0.0);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 30, 0.5, 0.6, 0.5, 0.3);
		level.playSound(null, struck.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		Spellbooks.LOGGER.info("[SPELL] combo={} caster={} target={}", combo.name(), caster.getUUID(), struck.getUUID());
	}

	private static @Nullable Combo match(final Cast previous, final Cast current) {
		for (Combo combo : COMBOS) {
			if (!combo.first().equals(previous.spell()) || !combo.second().equals(current.spell())) {
				continue;
			}

			boolean sameMob = previous.target() != null && previous.target().equals(current.target());
			boolean sameSpot = previous.point() != null && current.point() != null
				&& previous.point().distanceTo(current.point()) <= SAME_SPOT_BLOCKS;
			boolean self = combo == BASTION;
			if (sameMob || sameSpot || self) {
				return combo;
			}
		}
		return null;
	}

	private static LivingEntity land(
		final ServerPlayer caster, final Combo combo, final @Nullable Mob target, final @Nullable Vec3 point,
		final SpellModifiers.CastModifiers modifiers
	) {
		var magic = SpellCasting.spellSource(caster);
		double power = modifiers.powerMultiplier();
		if (combo == THERMAL_SHOCK && target != null) {
			// The spell's own hit set the hurt cooldown, so it is cleared: the combo's hit is its own, not a top-up.
			target.damageCooldownTime = 0;
			target.hurtServer(caster.level(), magic, RpgBridge.damage(caster, THERMAL_SHOCK_DAMAGE, power));
			return target;
		}

		if (combo == BLIGHT_BLOOM && target != null) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, BLIGHT_TICKS, 1), caster);
			return target;
		}

		if (combo == RIPTIDE && target != null) {
			target.damageCooldownTime = 0;
			target.hurtServer(caster.level(), magic, RpgBridge.damage(caster, RIPTIDE_DAMAGE, power));
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RIPTIDE_SLOW_TICKS, 2), caster);
			SpellStatusApi.soak(target, RIPTIDE_SLOW_TICKS, caster);
			return target;
		}

		if (combo == FROZEN_METEOR && point != null) {
			for (Mob hostile : caster.level().getEntitiesOfClass(
				Mob.class, new AABB(point, point).inflate(FROZEN_METEOR_RADIUS),
				candidate -> candidate.isAlive() && Friends.isFoe(candidate)
					&& candidate.position().distanceToSqr(point) <= FROZEN_METEOR_RADIUS * FROZEN_METEOR_RADIUS
			)) {
				hostile.damageCooldownTime = 0;
				hostile.hurtServer(caster.level(), magic, RpgBridge.damage(caster, FROZEN_METEOR_DAMAGE, power));
			}
			return caster;
		}

		caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BASTION_TICKS, 3), caster);
		return caster;
	}

	private static Combo combo(final String first, final String second, final String name) {
		return new Combo(
			Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, first), Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, second), name
		);
	}
}
