package com.stella.spells;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What a spell leaves running after its cast: Flame Ring's burning aura, Gravity's hold on a mob's jump,
 * and Shadow Veil waiting for its wearer's first blow. All of it is keyed on the server's tick counter,
 * and all of it is forgotten when the server stops.
 */
public final class SpellTimers {
	public static final Identifier GRAVITY_MODIFIER_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "gravity");

	private record Aura(int endsAt, int nextPulse, double radius, float damage, int burnTicks) {
	}

	private static final Map<UUID, Aura> AURAS = new ConcurrentHashMap<>();
	/** Each grounded mob, and the tick its jump comes back. The modifier is transient, so a reload clears it too. */
	private static final Map<Mob, Integer> GROUNDED = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> VEILED = new ConcurrentHashMap<>();

	private SpellTimers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpellTimers::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			AURAS.clear();
			GROUNDED.clear();
			VEILED.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SpellTimers::afterDamage);
	}

	public static boolean hasFlameAura(final ServerPlayer player) {
		return AURAS.containsKey(player.getUUID());
	}

	static void flameAura(final ServerPlayer caster, final int ticks, final double radius, final float damage, final int burnTicks) {
		int now = caster.level().getServer().getTickCount();
		AURAS.put(caster.getUUID(), new Aura(now + ticks, now + 1, radius, damage, burnTicks));
	}

	static void ground(final Mob mob, final int ticks) {
		AttributeInstance jump = mob.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		jump.addOrUpdateTransientModifier(new AttributeModifier(
			GRAVITY_MODIFIER_ID, MoreSpells.GRAVITY_JUMP_FACTOR - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		));
		GROUNDED.put(mob, level.getServer().getTickCount() + ticks);
	}

	static void veil(final ServerPlayer caster, final int ticks) {
		VEILED.put(caster.getUUID(), caster.level().getServer().getTickCount() + ticks);
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Aura>> it = AURAS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Aura> entry = it.next();
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
			Aura aura = entry.getValue();
			if (caster == null || !caster.isAlive() || now >= aura.endsAt()) {
				it.remove();
				continue;
			}
			if (now % 4 == 0) {
				ring(caster, aura.radius());
			}
			if (now >= aura.nextPulse()) {
				for (Mob hostile : MoreSpells.hostiles(caster.level(), caster.position(), aura.radius())) {
					MoreSpells.hurt(caster, hostile, aura.damage());
					hostile.igniteForTicks(aura.burnTicks());
				}
				entry.setValue(new Aura(aura.endsAt(), now + MoreSpells.FLAME_AURA_PULSE_TICKS, aura.radius(), aura.damage(), aura.burnTicks()));
			}
		}

		for (Iterator<Map.Entry<Mob, Integer>> it = GROUNDED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Integer> entry = it.next();
			Mob mob = entry.getKey();
			if (mob.isRemoved() || now >= entry.getValue()) {
				AttributeInstance jump = mob.getAttribute(Attributes.JUMP_STRENGTH);
				if (jump != null) {
					jump.removeModifier(GRAVITY_MODIFIER_ID);
				}
				it.remove();
			}
		}

		VEILED.values().removeIf(endsAt -> now >= endsAt);
	}

	// A ring of flame at the wearer's feet, so the aura's reach can be seen.
	private static void ring(final ServerPlayer caster, final double radius) {
		ServerLevel level = caster.level();
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2.0 * i / 12.0;
			level.sendParticles(ParticleTypes.FLAME, caster.getX() + Math.cos(angle) * radius, caster.getY() + 0.1,
				caster.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.05, 0.0, 0.0);
		}
	}

	// The first blow from under the veil breaks it: it strikes double and stops its victim dead.
	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || victim == attacker || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		Integer endsAt = VEILED.remove(attacker.getUUID());
		if (endsAt == null || level.getServer().getTickCount() >= endsAt || !attacker.hasEffect(MobEffects.INVISIBILITY)) {
			return;
		}
		attacker.removeEffect(MobEffects.INVISIBILITY);
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MoreSpells.VEIL_BREAK_SLOW_TICKS, MoreSpells.VEIL_BREAK_SLOW_AMPLIFIER), attacker);
		int hurtCooldown = victim.damageCooldownTime;
		victim.damageCooldownTime = 0;
		victim.hurtServer(level, level.damageSources().magic(), damage * (float) (MoreSpells.VEIL_BREAK_DAMAGE_MULTIPLIER - 1.0));
		victim.damageCooldownTime = Math.max(victim.damageCooldownTime, hurtCooldown);
		level.sendParticles(ParticleTypes.WHITE_ASH, victim.getX(), victim.getY(1.0), victim.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
		Spellbooks.LOGGER.info("[SPELL] veil_broken player={} victim={} damage={}", attacker.getUUID(), victim.getUUID(), damage);
	}
}
