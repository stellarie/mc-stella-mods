package com.stella.spells;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a spell leaves running after its cast: Flame Ring's burning aura, Gravity's hold on a mob's jump,
 * and Shadow Veil waiting for its wearer's first blow. All of it is keyed on the server's tick counter,
 * and all of it is forgotten when the server stops.
 */
public final class SpellTimers {
	public static final Identifier GRAVITY_MODIFIER_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "gravity");

	record Aura(int endsAt, int nextPulse, double radius, float damage, int burnTicks, CastHits.PendingCredit credit) {
	}

	record Zone(ResourceKey<Level> dimension, UUID caster, Vec3 center, double radius, int endsAt, int nextPulse, double power,
		CastHits.PendingCredit credit) {
	}

	private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();
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
			ZONES.clear();
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
		AURAS.put(caster.getUUID(), new Aura(now + ticks, now + 1, radius, damage, burnTicks, CastHits.deferred(caster)));
	}

	/** The caster's running aura, or null; a test seam. */
	static @Nullable Aura flameAuraOf(final UUID caster) {
		return AURAS.get(caster);
	}

	/** The caster's running zones; a test seam. */
	static List<Zone> zonesOf(final UUID caster) {
		return ZONES.stream().filter(zone -> zone.caster().equals(caster)).toList();
	}

	/** The zones running now; a test seam. */
	static int sanctuaryZones() {
		return ZONES.size();
	}

	static void sanctuary(final ServerPlayer caster, final Vec3 center, final double radius, final int ticks, final double power) {
		int now = caster.level().getServer().getTickCount();
		ZONES.add(new Zone(caster.level().dimension(), caster.getUUID(), center, radius, now + ticks, now + 1, power, CastHits.deferred(caster)));
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
					if (aura.credit() != null) {
						aura.credit().fire(server);
					}
				}
				entry.setValue(new Aura(aura.endsAt(), now + MoreSpells.FLAME_AURA_PULSE_TICKS, aura.radius(), aura.damage(), aura.burnTicks(), aura.credit()));
			}
		}

		tickZones(server, now);

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

	// A zone ends with its time, its caster, or its level; it never follows the caster.
	private static void tickZones(final MinecraftServer server, final int now) {
		for (Zone zone : ZONES) {
			ServerLevel level = server.getLevel(zone.dimension());
			ServerPlayer caster = server.getPlayerList().getPlayer(zone.caster());
			if (level == null || caster == null || !caster.isAlive() || now >= zone.endsAt()) {
				ZONES.remove(zone);
				continue;
			}
			if (now % 4 == 0) {
				edge(level, zone);
			}
			if (now < zone.nextPulse()) {
				continue;
			}
			ZONES.set(ZONES.indexOf(zone), new Zone(zone.dimension(), zone.caster(), zone.center(), zone.radius(),
				zone.endsAt(), now + MoreSpells.SANCTUARY_PULSE_TICKS, zone.power(), zone.credit()));
			double reach = zone.radius() * zone.radius();
			for (ServerPlayer player : level.players()) {
				if (player.isAlive() && player.position().distanceToSqr(zone.center()) <= reach) {
					boolean touched = player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MoreSpells.SANCTUARY_REGEN_TICKS, 0), caster);
					if (touched && zone.credit() != null) {
						zone.credit().fire(server);
					}
				}
			}
			AABB box = new AABB(zone.center(), zone.center()).inflate(zone.radius());
			// A summoned servant is no undead to this zone: Sanctuary mends its own side rather than burning it.
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, other -> other.isAlive() && other.is(EntityTypeTags.UNDEAD)
				&& !SummonSkeleton.isServant(other) && other.position().distanceToSqr(zone.center()) <= reach)) {
				MoreSpells.hurt(caster, mob, RpgBridge.damage(caster, MoreSpells.SANCTUARY_UNDEAD_DAMAGE, zone.power()));
				if (zone.credit() != null) {
					zone.credit().fire(server);
				}
			}
		}
	}

	private static void edge(final ServerLevel level, final Zone zone) {
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2.0 * i / 16.0;
			level.sendParticles(ParticleTypes.END_ROD, zone.center().x + Math.cos(angle) * zone.radius(), zone.center().y + 0.1,
				zone.center().z + Math.sin(angle) * zone.radius(), 1, 0.0, 0.05, 0.0, 0.0);
		}
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
		victim.hurtServer(level, SpellCasting.spellSource(attacker), damage * (float) (MoreSpells.VEIL_BREAK_DAMAGE_MULTIPLIER - 1.0));
		victim.damageCooldownTime = Math.max(victim.damageCooldownTime, hurtCooldown);
		level.sendParticles(ParticleTypes.WHITE_ASH, victim.getX(), victim.getY(1.0), victim.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
		Spellbooks.LOGGER.info("[SPELL] veil_broken player={} victim={} damage={}", attacker.getUUID(), victim.getUUID(), damage);
	}
}
