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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The second catalogue: twenty more spells, from Ice Lance to Flame Ring. */
public final class MoreSpells {
	public static final float ICE_LANCE_DAMAGE = 5.0F;
	public static final int ICE_LANCE_FREEZE_TICKS = 140;
	/** How far the lance flies past its caster, and how close to its line a hostile must stand to be struck. */
	public static final double ICE_LANCE_RANGE = 16.0;
	public static final double ICE_LANCE_WIDTH = 1.0;
	/** One puff of snow mist every half block along the lance's line. */
	public static final double ICE_LANCE_TRAIL_STEP = 0.5;
	public static final float SOUL_SIPHON_DAMAGE = 4.0F;
	public static final float VENOM_DAMAGE = 2.0F;
	public static final int VENOM_TICKS = 100;
	public static final int VENOM_AMPLIFIER = 1;
	public static final double HEX_RADIUS = 6.0;
	/** The hex lasts this long for every hostile it weakens, up to its cap. */
	public static final int HEX_TICKS_PER_FOE = 60;
	public static final int HEX_MAX_TICKS = 300;
	public static final int HEX_AMPLIFIER = 1;
	public static final int HEX_STRENGTH_AMPLIFIER = 1;
	public static final double GRAVITY_RADIUS = 6.0;
	public static final int GRAVITY_TICKS = 160;
	public static final int GRAVITY_SLOW_AMPLIFIER = 1;
	public static final double GRAVITY_JUMP_FACTOR = 0.5;
	public static final int FLASH_BLIND_TICKS = 100;
	public static final int FLASH_GLOW_TICKS = 200;
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
	/** The blow that breaks the veil strikes this many times over and stops its victim dead. */
	public static final double VEIL_BREAK_DAMAGE_MULTIPLIER = 2.0;
	public static final int VEIL_BREAK_SLOW_TICKS = 40;
	/** Slowness VII: seven times fifteen percent, so the victim cannot move at all. */
	public static final int VEIL_BREAK_SLOW_AMPLIFIER = 6;
	public static final double PURIFY_RADIUS = 6.0;
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
	public static final double FLAME_RING_RADIUS = 3.0;
	public static final float FLAME_RING_DAMAGE = 3.0F;
	public static final int FLAME_RING_BURN_TICKS = 60;
	/** How long the caster wears the burning aura, and how often it scorches the hostiles around. */
	public static final int FLAME_AURA_TICKS = 200;
	public static final int FLAME_AURA_PULSE_TICKS = 20;
	public static final double SUPPORT_RADIUS = 8.0;
	public static final int BULWARK_TICKS = 400;
	public static final int BULWARK_AMPLIFIER = 1;
	public static final int TAILWIND_TICKS = 600;
	public static final int TAILWIND_AMPLIFIER = 1;
	public static final int BATTLE_HYMN_TICKS = 400;
	public static final int BATTLE_HYMN_AMPLIFIER = 0;

	/** Test seam: each cloud a spell left, as it was added to the level. */
	public interface Recorder {
		void cloud(ServerPlayer caster, AreaEffectCloud cloud, MobEffectInstance effect);
	}

	private MoreSpells() {
	}

	/** Test seam: the puffs of mist each Ice Lance left along its line. */
	public interface TrailRecorder {
		void trail(ServerPlayer caster, int puffs);
	}

	private static final Recorder SILENT = (caster, cloud, effect) -> {
	};
	private static final TrailRecorder SILENT_TRAIL = (caster, puffs) -> {
	};
	private static volatile TrailRecorder trailRecorder = SILENT_TRAIL;

	public static void setTrailRecorder(final TrailRecorder newRecorder) {
		trailRecorder = newRecorder == null ? SILENT_TRAIL : newRecorder;
	}
	private static volatile Recorder recorder = SILENT;

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	// The lance flies from the caster's eyes through the mob and on to its full range, piercing every hostile
	// that stands on its line, and leaves a trail of snow mist behind it.
	static boolean applyIceLance(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		ServerLevel level = caster.level();
		Vec3 from = caster.getEyePosition();
		Vec3 toward = target.getBoundingBox().getCenter().subtract(from);
		if (toward.lengthSqr() < 1.0e-6) {
			return false;
		}
		Vec3 to = from.add(toward.normalize().scale(ICE_LANCE_RANGE * modifiers.radiusMultiplier()));
		List<Mob> struck = new java.util.ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(from, to).inflate(ICE_LANCE_WIDTH),
			candidate -> candidate != target && candidate.isAlive() && Friends.isFoe(candidate)
				&& distanceToSegment(candidate.getBoundingBox().getCenter(), from, to) <= ICE_LANCE_WIDTH + candidate.getBbWidth() / 2.0));
		struck.add(0, target);
		for (Mob mob : struck) {
			hurt(caster, mob, ICE_LANCE_DAMAGE * power(modifiers));
			freeze(mob, modifiers.durationTicks(ICE_LANCE_FREEZE_TICKS, false));
		}
		int puffs = 0;
		double length = from.distanceTo(to);
		for (double along = ICE_LANCE_TRAIL_STEP; along <= length + 1.0e-6; along += ICE_LANCE_TRAIL_STEP) {
			Vec3 at = from.add(to.subtract(from).scale(along / length));
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
			puffs++;
		}
		trailRecorder.trail(caster, puffs);
		return true;
	}

	private static double distanceToSegment(final Vec3 point, final Vec3 from, final Vec3 to) {
		Vec3 line = to.subtract(from);
		double t = Math.max(0.0, Math.min(1.0, point.subtract(from).dot(line) / line.lengthSqr()));
		return point.distanceTo(from.add(line.scale(t)));
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

	// Every hostile around the caster is weakened, and the caster grows stronger; both last longer for each one hexed.
	static boolean applySappingHex(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		List<Mob> hexed = hostiles(caster.level(), caster.position(), HEX_RADIUS * modifiers.radiusMultiplier());
		if (hexed.isEmpty()) {
			return false;
		}
		int ticks = Math.min(HEX_MAX_TICKS, HEX_TICKS_PER_FOE * hexed.size());
		for (Mob mob : hexed) {
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, modifiers.durationTicks(ticks, false), HEX_AMPLIFIER), caster);
		}
		caster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, modifiers.durationTicks(ticks, true), HEX_STRENGTH_AMPLIFIER), caster);
		return true;
	}

	// Every hostile around the caster is slowed and can jump only half as high until it wears off.
	static boolean applyGravity(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		int ticks = modifiers.durationTicks(GRAVITY_TICKS, false);
		for (Mob mob : hostiles(caster.level(), caster.position(), GRAVITY_RADIUS * modifiers.radiusMultiplier())) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, GRAVITY_SLOW_AMPLIFIER), caster);
			SpellTimers.ground(mob, ticks);
		}
		return true;
	}

	static boolean applyBlindingFlash(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, modifiers.durationTicks(FLASH_BLIND_TICKS, false), 0), caster);
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, modifiers.durationTicks(FLASH_GLOW_TICKS, false), 0), caster);
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

	// The veil holds until it runs out or its wearer strikes, and that first blow is the breaking one.
	static boolean applyShadowVeil(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		int ticks = modifiers.durationTicks(VEIL_TICKS, true);
		boolean veiled = caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0), caster);
		if (veiled) {
			SpellTimers.veil(caster, ticks);
		}
		return veiled;
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

	// Harmful effects only, from the caster and every player close by; a cast with nothing to clear does not count.
	static boolean applyPurify(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		double radius = PURIFY_RADIUS * modifiers.radiusMultiplier();
		boolean cleared = false;
		for (ServerPlayer player : caster.level().players()) {
			if (player.isAlive() && (player == caster || player.distanceToSqr(caster) <= radius * radius)) {
				List<Holder<MobEffect>> harmful = player.getActiveEffects().stream()
					.map(MobEffectInstance::getEffect)
					.filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL)
					.toList();
				harmful.forEach(player::removeEffect);
				cleared |= !harmful.isEmpty();
			}
		}
		return cleared;
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

	// The caster wears a burning aura: every second it scorches the hostiles close around and never its wearer.
	static boolean applyFlameRing(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellTimers.flameAura(caster, modifiers.durationTicks(FLAME_AURA_TICKS, true), FLAME_RING_RADIUS * modifiers.radiusMultiplier(),
			FLAME_RING_DAMAGE * power(modifiers), modifiers.durationTicks(FLAME_RING_BURN_TICKS, false));
		return true;
	}

	static boolean applyBulwarkHymn(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return bless(caster, modifiers, new MobEffectInstance(MobEffects.ABSORPTION, modifiers.durationTicks(BULWARK_TICKS, true), BULWARK_AMPLIFIER));
	}

	static boolean applyTailwind(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return bless(caster, modifiers, new MobEffectInstance(MobEffects.SPEED, modifiers.durationTicks(TAILWIND_TICKS, true), TAILWIND_AMPLIFIER));
	}

	static boolean applyBattleHymn(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return bless(caster, modifiers, new MobEffectInstance(MobEffects.STRENGTH, modifiers.durationTicks(BATTLE_HYMN_TICKS, true), BATTLE_HYMN_AMPLIFIER));
	}

	// The caster and every ally around: players, villagers, golems and tamed pets, never a hostile.
	private static boolean bless(final ServerPlayer caster, final SpellModifiers.CastModifiers modifiers, final MobEffectInstance effect) {
		double radius = SUPPORT_RADIUS * modifiers.radiusMultiplier();
		for (net.minecraft.world.entity.LivingEntity ally : caster.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
			caster.getBoundingBox().inflate(radius), candidate -> isAlly(candidate) && candidate.distanceToSqr(caster) <= radius * radius)) {
			ally.addEffect(new MobEffectInstance(effect), caster);
		}
		caster.addEffect(new MobEffectInstance(effect), caster);
		return true;
	}

	static boolean isAlly(final net.minecraft.world.entity.LivingEntity entity) {
		return entity.isAlive() && (!(entity instanceof Enemy) || Friends.isFriend(entity))
			&& (Friends.isFriend(entity) || entity instanceof net.minecraft.world.entity.player.Player
				|| entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
				|| entity instanceof net.minecraft.world.entity.animal.golem.AbstractGolem
				|| entity instanceof net.minecraft.world.entity.OwnableEntity ownable && ownable.getOwnerReference() != null);
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
	static void hurt(final ServerPlayer caster, final Mob target, final float amount) {
		target.damageCooldownTime = 0;
		target.hurtServer(caster.level(), caster.level().damageSources().magic(), amount);
	}

	private static boolean heal(final ServerPlayer player, final float amount) {
		float before = player.getHealth();
		player.heal(amount);
		return player.getHealth() > before;
	}

	static List<Mob> hostiles(final ServerLevel level, final Vec3 center, final double radius) {
		return level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate) && candidate.position().distanceToSqr(center) <= radius * radius);
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
