package com.stella.spells;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An imbued weapon in a mob's hand: each spell that strikes a single foe has a mob's version, cast at the
 * imbued strength on whatever the mob struck, a player included. It keeps the spell's own cooldown.
 */
final class MobImbue {
	/** Server tick at which each mob's imbued spell is ready again. */
	private static final Map<UUID, Map<Identifier, Integer>> READY_AT = new ConcurrentHashMap<>();

	private static final ThreadLocal<Boolean> CASTING = new ThreadLocal<>();

	private MobImbue() {
	}

	/** True while a mob's imbued spell is landing, so its own damage never counts as a fresh melee hit. */
	static boolean casting() {
		return CASTING.get() != null;
	}

	static void clear() {
		READY_AT.clear();
	}

	static boolean tracks(final UUID mob) {
		return READY_AT.containsKey(mob);
	}

	/** A mob's record leaves with the mob, so the map holds only the living. */
	static void forget(final UUID mob) {
		READY_AT.remove(mob);
	}

	static int readyEntries() {
		return READY_AT.size();
	}

	/** Casts when the spell is one a mob can cast and it is off cooldown; true when it cast. */
	static boolean cast(final Mob wielder, final SpellDefinition definition, final LivingEntity victim) {
		if (casting() || !SpellImbue.MOB_SPELLS.contains(definition.id()) || !(wielder.level() instanceof ServerLevel level)) {
			return false;
		}

		int now = level.getServer().getTickCount();
		Map<Identifier, Integer> ready = READY_AT.computeIfAbsent(wielder.getUUID(), key -> new ConcurrentHashMap<>());
		if (now < ready.getOrDefault(definition.id(), Integer.MIN_VALUE)) {
			return false;
		}

		// The cooldown starts first, and the spell's own damage is not a melee hit, so it never casts again.
		ready.put(definition.id(), now + definition.baseCooldownTicks());
		CASTING.set(Boolean.TRUE);
		try {
			apply(level, wielder, definition.id().getPath(), victim, SpellImbue.IMBUE_POWER, SpellImbue.IMBUE_DURATION);
		} finally {
			CASTING.remove();
		}
		return true;
	}

	/** One mob spell on one foe, at the given share of the spell's power and duration; false for a spell with no mob form. */
	static boolean apply(final ServerLevel level, final Mob wielder, final String spell, final LivingEntity victim, final double power, final double duration) {
		return apply(level, wielder, null, spell, victim, power, duration);
	}

	/** As above; a non-null owner sets the damage by its MATK while the wielder stays the hit's source. */
	static boolean apply(
		final ServerLevel level, final Mob wielder, final @Nullable LivingEntity owner, final String spell,
		final LivingEntity victim, final double power, final double duration
	) {
		switch (spell) {
			case "spark_shot" -> {
				hurt(level, wielder, owner, victim, power, SpellCasting.DAMAGE);
				victim.igniteForTicks(ticks(duration, MobSpellTimings.SPARK_BURN_TICKS));
			}
			case "frostbind" -> effect(wielder, victim, new MobEffectInstance(MobEffects.SLOWNESS, ticks(duration, 80), 1));
			case "briar_snare" -> effect(wielder, victim, new MobEffectInstance(MobEffects.SLOWNESS, ticks(duration, 80), 3));
			case "static_lash" -> hurt(level, wielder, owner, victim, power, SpellCasting.DAMAGE);
			case "tidal_push" -> {
				Vec3 away = victim.position().subtract(wielder.position());
				Vec3 horizontal = new Vec3(away.x, 0.0, away.z);
				if (horizontal.lengthSqr() > 1.0e-6) {
					horizontal = horizontal.normalize();
					victim.push(horizontal.x * 1.2 * power, 0.2, horizontal.z * 1.2 * power);
					victim.needsSync = true;
				}
			}
			case "wither_seal" -> effect(wielder, victim, new MobEffectInstance(MobEffects.WITHER, ticks(duration, 100), 1));
			case "ice_lance" -> {
				hurt(level, wielder, owner, victim, power, MoreSpells.ICE_LANCE_DAMAGE);
				MoreSpells.freeze(victim, ticks(duration, MobSpellTimings.ICE_LANCE_FREEZE_TICKS));
			}
			case "soul_siphon" -> {
				float before = victim.getHealth();
				hurt(level, wielder, owner, victim, power, MoreSpells.SOUL_SIPHON_DAMAGE);
				wielder.heal(RpgBridge.vanilla(before - Math.max(0.0F, victim.getHealth())));
			}
			case "venom_dart" -> {
				hurt(level, wielder, owner, victim, power, MoreSpells.VENOM_DAMAGE);
				effect(wielder, victim, new MobEffectInstance(MobEffects.POISON, ticks(duration, MobSpellTimings.VENOM_TICKS), MoreSpells.VENOM_AMPLIFIER));
			}
			case "blinding_flash" -> {
				effect(wielder, victim, new MobEffectInstance(MobEffects.BLINDNESS, ticks(duration, MobSpellTimings.FLASH_BLIND_TICKS), 0));
				effect(wielder, victim, new MobEffectInstance(MobEffects.GLOWING, ticks(duration, MobSpellTimings.FLASH_GLOW_TICKS), 0));
			}
			case "ember_brand" -> {
				hurt(level, wielder, owner, victim, power, MoreSpells.EMBER_DAMAGE);
				victim.igniteForTicks(ticks(duration, MobSpellTimings.EMBER_BURN_TICKS));
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static int ticks(final double duration, final float baseTicks) {
		return Math.max(1, Math.round(baseTicks * (float) duration));
	}

	private static void effect(final Mob wielder, final LivingEntity victim, final MobEffectInstance effect) {
		victim.addEffect(effect, wielder);
	}

	// Magic from the wielder, so the victim knows who cast it; the melee hit's own cooldown is set aside for it.
	private static void hurt(
		final ServerLevel level, final Mob wielder, final @Nullable LivingEntity owner, final LivingEntity victim,
		final double power, final float amount
	) {
		DamageSource magic = level.damageSources().indirectMagic(wielder, wielder);
		victim.damageCooldownTime = 0;
		victim.hurtServer(level, magic, owner == null ? amount * (float) power : RpgBridge.damage(owner, amount, power));
	}
}
