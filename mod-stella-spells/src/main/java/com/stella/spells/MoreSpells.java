package com.stella.spells;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The second catalogue: twenty more spells, from Ice Lance to Flame Ring. */
public final class MoreSpells {
	public static final float ICE_LANCE_DAMAGE = 5.0F;
	public static final int ICE_LANCE_FREEZE_TICKS = 140;
	public static final float SOUL_SIPHON_DAMAGE = 4.0F;
	public static final float VENOM_DAMAGE = 2.0F;
	public static final int VENOM_TICKS = 100;
	public static final int VENOM_AMPLIFIER = 1;
	public static final int HEX_TICKS = 160;
	public static final int HEX_AMPLIFIER = 1;
	public static final int SKYHOOK_TICKS = 40;
	public static final int SKYHOOK_AMPLIFIER = 1;
	public static final int FLASH_BLIND_TICKS = 100;
	public static final int FLASH_GLOW_TICKS = 200;
	public static final float SMITE_DAMAGE = 8.0F;
	public static final float EMBER_DAMAGE = 2.0F;
	public static final int EMBER_BURN_TICKS = 200;
	public static final int QUICKSILVER_TICKS = 400;
	public static final int QUICKSILVER_AMPLIFIER = 1;
	public static final int TIDE_BREATH_TICKS = 1200;
	public static final int TIDE_GRACE_TICKS = 400;
	public static final int BERSERK_TICKS = 200;
	public static final int BERSERK_AMPLIFIER = 1;
	public static final int HASTE_TICKS = 1200;
	public static final int HASTE_AMPLIFIER = 1;
	public static final int VEIL_TICKS = 600;
	public static final float SANCTUARY_SELF_HEAL = 6.0F;
	public static final float SANCTUARY_ALLY_HEAL = 4.0F;
	public static final double SANCTUARY_RADIUS = 6.0;
	public static final double FROST_NOVA_RADIUS = 5.0;
	public static final float FROST_NOVA_DAMAGE = 3.0F;
	public static final int FROST_NOVA_SLOW_TICKS = 60;
	public static final int FROST_NOVA_SLOW_AMPLIFIER = 3;
	public static final double EARTHSPIKE_RADIUS = 3.0;
	public static final float EARTHSPIKE_DAMAGE = 4.0F;
	public static final double EARTHSPIKE_LIFT = 1.0;
	public static final float PLAGUE_RADIUS = 3.0F;
	public static final int PLAGUE_CLOUD_TICKS = 100;
	public static final int PLAGUE_POISON_TICKS = 100;
	public static final float SPRING_RADIUS = 3.0F;
	public static final int SPRING_CLOUD_TICKS = 120;
	public static final int SPRING_REGEN_TICKS = 100;
	public static final int SPRING_REGEN_AMPLIFIER = 1;
	public static final double FLAME_RING_RADIUS = 4.0;
	public static final float FLAME_RING_DAMAGE = 3.0F;
	public static final int FLAME_RING_BURN_TICKS = 120;

	/** Test seam: each cloud a spell left, as it was added to the level. */
	public interface Recorder {
		void cloud(ServerPlayer caster, AreaEffectCloud cloud, MobEffectInstance effect);
	}

	private MoreSpells() {
	}

	private static final Recorder SILENT = (caster, cloud, effect) -> {
	};
	private static volatile Recorder recorder = SILENT;

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	static boolean applyIceLance(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		hurt(caster, target, ICE_LANCE_DAMAGE * power(modifiers));
		freeze(target, modifiers.durationTicks(ICE_LANCE_FREEZE_TICKS, false));
		return true;
	}

	// The caster gains the health the mob actually lost.
	static boolean applySoulSiphon(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		float before = target.getHealth();
		hurt(caster, target, SOUL_SIPHON_DAMAGE * power(modifiers));
		caster.heal(before - Math.max(0.0F, target.getHealth()));
		return true;
	}

	// The dart itself always stings, so an undead mob, which shrugs off Poison, still feels it.
	static boolean applyVenomDart(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		hurt(caster, target, VENOM_DAMAGE * power(modifiers));
		target.addEffect(new MobEffectInstance(MobEffects.POISON, modifiers.durationTicks(VENOM_TICKS, false), VENOM_AMPLIFIER), caster);
		return true;
	}

	static boolean applySappingHex(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return target != null && target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, modifiers.durationTicks(HEX_TICKS, false), HEX_AMPLIFIER), caster);
	}

	static boolean applySkyhook(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return target != null && target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, modifiers.durationTicks(SKYHOOK_TICKS, false), SKYHOOK_AMPLIFIER), caster);
	}

	static boolean applyBlindingFlash(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, modifiers.durationTicks(FLASH_BLIND_TICKS, false), 0), caster);
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, modifiers.durationTicks(FLASH_GLOW_TICKS, false), 0), caster);
		return true;
	}

	// A storm bolt for the look, which starts no fire and touches no block, then the spell's own damage.
	static boolean applySmite(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(caster.level(), EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setVisualOnly(true);
			bolt.addTag(SpellStorms.STORM_BOLT_TAG);
			bolt.snapTo(target.getX(), target.getY(), target.getZ());
			caster.level().addFreshEntity(bolt);
		}
		hurt(caster, target, SMITE_DAMAGE * power(modifiers));
		return true;
	}

	static boolean applyEmberBrand(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		hurt(caster, target, EMBER_DAMAGE * power(modifiers));
		target.igniteForTicks(modifiers.durationTicks(EMBER_BURN_TICKS, false));
		return true;
	}

	static boolean applyQuicksilver(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return caster.addEffect(new MobEffectInstance(MobEffects.SPEED, modifiers.durationTicks(QUICKSILVER_TICKS, true), QUICKSILVER_AMPLIFIER), caster);
	}

	static boolean applyTideLungs(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		caster.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, modifiers.durationTicks(TIDE_BREATH_TICKS, true), 0), caster);
		caster.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, modifiers.durationTicks(TIDE_GRACE_TICKS, true), 0), caster);
		return true;
	}

	static boolean applyBerserk(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return caster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, modifiers.durationTicks(BERSERK_TICKS, true), BERSERK_AMPLIFIER), caster);
	}

	static boolean applyHasteRune(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return caster.addEffect(new MobEffectInstance(MobEffects.HASTE, modifiers.durationTicks(HASTE_TICKS, true), HASTE_AMPLIFIER), caster);
	}

	static boolean applyShadowVeil(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, modifiers.durationTicks(VEIL_TICKS, true), 0), caster);
	}

	// The caster and every other player close by; a cast that heals nobody does not count.
	static boolean applySanctuary(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		boolean healed = heal(caster, SANCTUARY_SELF_HEAL * power(modifiers));
		for (ServerPlayer ally : caster.level().players()) {
			if (ally != caster && ally.isAlive() && ally.distanceToSqr(caster) <= SANCTUARY_RADIUS * SANCTUARY_RADIUS) {
				healed |= heal(ally, SANCTUARY_ALLY_HEAL * power(modifiers));
			}
		}
		return healed;
	}

	// Harmful effects only; a cast with nothing to clear does not count.
	static boolean applyPurify(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		List<Holder<MobEffect>> harmful = caster.getActiveEffects().stream()
			.map(MobEffectInstance::getEffect)
			.filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL)
			.toList();
		harmful.forEach(caster::removeEffect);
		return !harmful.isEmpty();
	}

	static boolean applyFrostNova(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		for (Mob hostile : hostiles(caster.level(), caster.position(), FROST_NOVA_RADIUS * modifiers.radiusMultiplier())) {
			hurt(caster, hostile, FROST_NOVA_DAMAGE * power(modifiers));
			hostile.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, modifiers.durationTicks(FROST_NOVA_SLOW_TICKS, false), FROST_NOVA_SLOW_AMPLIFIER), caster);
		}
		return true;
	}

	// The lift is set after the damage, so the hurt knockback cannot flatten it.
	static boolean applyEarthspike(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (point == null) {
			return false;
		}
		for (Mob hostile : hostiles(caster.level(), point, EARTHSPIKE_RADIUS * modifiers.radiusMultiplier())) {
			hurt(caster, hostile, EARTHSPIKE_DAMAGE * power(modifiers));
			Vec3 motion = hostile.getDeltaMovement();
			hostile.setDeltaMovement(motion.x, EARTHSPIKE_LIFT, motion.z);
		}
		return true;
	}

	static boolean applyPlagueCloud(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return point != null && cloud(caster, point, PLAGUE_RADIUS * (float) modifiers.radiusMultiplier(), modifiers.durationTicks(PLAGUE_CLOUD_TICKS, false),
			new MobEffectInstance(MobEffects.POISON, PLAGUE_POISON_TICKS, 0));
	}

	static boolean applyHealingSpring(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return point != null && cloud(caster, point, SPRING_RADIUS * (float) modifiers.radiusMultiplier(), modifiers.durationTicks(SPRING_CLOUD_TICKS, true),
			new MobEffectInstance(MobEffects.REGENERATION, SPRING_REGEN_TICKS, SPRING_REGEN_AMPLIFIER));
	}

	static boolean applyFlameRing(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (point == null) {
			return false;
		}
		for (Mob hostile : hostiles(caster.level(), point, FLAME_RING_RADIUS * modifiers.radiusMultiplier())) {
			hurt(caster, hostile, FLAME_RING_DAMAGE * power(modifiers));
			hostile.igniteForTicks(modifiers.durationTicks(FLAME_RING_BURN_TICKS, false));
		}
		return true;
	}

	/**
	 * Freezes a body solid for the given ticks. Outside powder snow a body thaws two frozen ticks for every
	 * tick, so the freeze is set to twice the time above the point at which it counts as frozen solid.
	 */
	static void freeze(final net.minecraft.world.entity.LivingEntity body, final int ticks) {
		body.setTicksFrozen(Math.max(body.getTicksFrozen(), body.getTicksRequiredToFreeze() + 2 * ticks));
	}

	private static float power(final SpellModifiers.CastModifiers modifiers) {
		return (float) modifiers.powerMultiplier();
	}

	// Magic damage, as every spell deals; the hurt cooldown is cleared so a spell right after a hit still lands.
	private static void hurt(final ServerPlayer caster, final Mob target, final float amount) {
		target.damageCooldownTime = 0;
		target.hurtServer(caster.level(), caster.level().damageSources().magic(), amount);
	}

	private static boolean heal(final ServerPlayer player, final float amount) {
		float before = player.getHealth();
		player.heal(amount);
		return player.getHealth() > before;
	}

	private static List<Mob> hostiles(final ServerLevel level, final Vec3 center, final double radius) {
		return level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius),
			candidate -> candidate.isAlive() && candidate instanceof Enemy && candidate.position().distanceToSqr(center) <= radius * radius);
	}

	private static boolean cloud(final ServerPlayer caster, final Vec3 at, final float radius, final int ticks, final MobEffectInstance effect) {
		AreaEffectCloud cloud = new AreaEffectCloud(caster.level(), at.x, at.y, at.z);
		cloud.setOwner(caster);
		// A caster standing in their own cloud is never touched by it: a vanilla cloud touches its owner too.
		((com.stella.spells.mixin.AreaEffectCloudAccessor) cloud).stellaSpells$victims().put(caster, Integer.MAX_VALUE);
		cloud.setRadius(radius);
		cloud.setDuration(ticks);
		cloud.setWaitTime(0);
		cloud.setRadiusPerTick(0.0F);
		cloud.addEffect(effect);
		if (!caster.level().addFreshEntity(cloud)) {
			return false;
		}
		recorder.cloud(caster, cloud, effect);
		return true;
	}
}
