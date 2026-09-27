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
			apply(level, wielder, definition.id().getPath(), victim);
		} finally {
			CASTING.remove();
		}
		return true;
	}

	private static void apply(final ServerLevel level, final Mob wielder, final String spell, final LivingEntity victim) {
		switch (spell) {
			case "spark_shot" -> {
				hurt(level, wielder, victim, SpellCasting.DAMAGE);
				victim.igniteForTicks(ticks(SpellCasting.BURN_SECONDS * 20.0F));
			}
			case "frostbind" -> effect(wielder, victim, new MobEffectInstance(MobEffects.SLOWNESS, ticks(80), 1));
			case "briar_snare" -> effect(wielder, victim, new MobEffectInstance(MobEffects.SLOWNESS, ticks(80), 3));
			case "static_lash" -> hurt(level, wielder, victim, SpellCasting.DAMAGE);
			case "tidal_push" -> {
				Vec3 away = victim.position().subtract(wielder.position());
				Vec3 horizontal = new Vec3(away.x, 0.0, away.z);
				if (horizontal.lengthSqr() > 1.0e-6) {
					horizontal = horizontal.normalize();
					victim.push(horizontal.x * 1.2 * SpellImbue.IMBUE_POWER, 0.2, horizontal.z * 1.2 * SpellImbue.IMBUE_POWER);
					victim.needsSync = true;
				}
			}
			case "wither_seal" -> effect(wielder, victim, new MobEffectInstance(MobEffects.WITHER, ticks(100), 1));
			case "ice_lance" -> {
				hurt(level, wielder, victim, MoreSpells.ICE_LANCE_DAMAGE);
				MoreSpells.freeze(victim, ticks(MoreSpells.ICE_LANCE_FREEZE_TICKS));
			}
			case "soul_siphon" -> {
				float before = victim.getHealth();
				hurt(level, wielder, victim, MoreSpells.SOUL_SIPHON_DAMAGE);
				wielder.heal(before - Math.max(0.0F, victim.getHealth()));
			}
			case "venom_dart" -> {
				hurt(level, wielder, victim, MoreSpells.VENOM_DAMAGE);
				effect(wielder, victim, new MobEffectInstance(MobEffects.POISON, ticks(MoreSpells.VENOM_TICKS), MoreSpells.VENOM_AMPLIFIER));
			}
			case "blinding_flash" -> {
				effect(wielder, victim, new MobEffectInstance(MobEffects.BLINDNESS, ticks(MoreSpells.FLASH_BLIND_TICKS), 0));
				effect(wielder, victim, new MobEffectInstance(MobEffects.GLOWING, ticks(MoreSpells.FLASH_GLOW_TICKS), 0));
			}
			case "ember_brand" -> {
				hurt(level, wielder, victim, MoreSpells.EMBER_DAMAGE);
				victim.igniteForTicks(ticks(MoreSpells.EMBER_BURN_TICKS));
			}
			default -> {
			}
		}
	}

	private static int ticks(final float baseTicks) {
		return Math.max(1, Math.round(baseTicks * (float) SpellImbue.IMBUE_DURATION));
	}

	private static void effect(final Mob wielder, final LivingEntity victim, final MobEffectInstance effect) {
		victim.addEffect(effect, wielder);
	}

	// Magic from the wielder, so the victim knows who cast it; the melee hit's own cooldown is set aside for it.
	private static void hurt(final ServerLevel level, final Mob wielder, final LivingEntity victim, final float amount) {
		DamageSource magic = level.damageSources().indirectMagic(wielder, wielder);
		victim.damageCooldownTime = 0;
		victim.hurtServer(level, magic, amount * (float) SpellImbue.IMBUE_POWER);
	}
}
