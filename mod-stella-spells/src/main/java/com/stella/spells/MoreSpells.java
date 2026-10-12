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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The second catalogue includes combat, movement, and support spells. */
public final class MoreSpells {
	public static final float ICE_LANCE_DAMAGE = SpellNumbers.ICE_LANCE.damage();
	public static final int ICE_LANCE_FREEZE_TICKS = SpellNumbers.ICE_LANCE.duration().ticks();
	/** How far the lance flies past its caster, and how close to its line a hostile must stand to be struck. */
	public static final double ICE_LANCE_RANGE = SpellNumbers.ICE_LANCE.radius();
	public static final double ICE_LANCE_WIDTH = 1.0;
	/** One puff of snow mist every half block along the lance's line. */
	public static final double ICE_LANCE_TRAIL_STEP = 0.5;
	public static final float SOUL_SIPHON_DAMAGE = SpellNumbers.SOUL_SIPHON.damage();
	public static final float VENOM_DAMAGE = SpellNumbers.VENOM_DART.damage();
	public static final int VENOM_TICKS = SpellNumbers.VENOM_DART.duration().ticks();
	public static final int VENOM_AMPLIFIER = SpellNumbers.VENOM_DART.amplifier();
	public static final double HEX_RADIUS = SpellNumbers.SAPPING_HEX.radius();
	/** The hex lasts this long for every hostile it weakens, up to its cap. */
	public static final int HEX_TICKS_PER_FOE = SpellNumbers.SAPPING_HEX.duration().ticks();
	public static final int HEX_MAX_TICKS = SpellNumbers.SAPPING_HEX.capTicks();
	public static final int HEX_AMPLIFIER = SpellNumbers.SAPPING_HEX.amplifier();
	public static final int HEX_STRENGTH_AMPLIFIER = SpellNumbers.SAPPING_HEX.extraAmplifier();
	public static final double GRAVITY_RADIUS = SpellNumbers.GRAVITY.radius();
	public static final int GRAVITY_TICKS = SpellNumbers.GRAVITY.duration().ticks();
	public static final int GRAVITY_SLOW_AMPLIFIER = SpellNumbers.GRAVITY.amplifier();
	public static final double GRAVITY_JUMP_FACTOR = 0.5;
	public static final int FLASH_BLIND_TICKS = SpellNumbers.BLINDING_FLASH.duration().ticks();
	public static final int FLASH_GLOW_TICKS = SpellNumbers.BLINDING_FLASH.extra().ticks();
	public static final float EMBER_DAMAGE = SpellNumbers.EMBER_BRAND.damage();
	public static final int EMBER_BURN_TICKS = SpellNumbers.EMBER_BRAND.duration().ticks();
	public static final int QUICKSILVER_TICKS = SpellNumbers.QUICKSILVER.duration().ticks();
	public static final int QUICKSILVER_AMPLIFIER = SpellNumbers.QUICKSILVER.amplifier();
	public static final int TIDE_BREATH_TICKS = SpellNumbers.TIDE_LUNGS.duration().ticks();
	public static final int TIDE_GRACE_TICKS = SpellNumbers.TIDE_LUNGS.extra().ticks();
	public static final int BERSERK_TICKS = SpellNumbers.BERSERK.duration().ticks();
	public static final int BERSERK_AMPLIFIER = SpellNumbers.BERSERK.amplifier();
	public static final int HASTE_TICKS = SpellNumbers.HASTE_RUNE.duration().ticks();
	public static final int HASTE_AMPLIFIER = SpellNumbers.HASTE_RUNE.amplifier();
	public static final int VEIL_TICKS = SpellNumbers.SHADOW_VEIL.duration().ticks();
	/** The blow that breaks the veil strikes this many times over and stops its victim dead. */
	public static final double VEIL_BREAK_DAMAGE_MULTIPLIER = 2.0;
	public static final int VEIL_BREAK_SLOW_TICKS = SpellNumbers.SHADOW_VEIL.extra().ticks();
	/** Slowness VII: seven times fifteen percent, so the victim cannot move at all. */
	public static final int VEIL_BREAK_SLOW_AMPLIFIER = SpellNumbers.SHADOW_VEIL.extraAmplifier();
	public static final double PURIFY_RADIUS = SpellNumbers.PURIFY.radius();
	/** The instant heals of a mob's Sanctuary; the player's Sanctuary is a zone. */
	public static final float SANCTUARY_SELF_HEAL = SpellNumbers.SANCTUARY.heal();
	public static final float SANCTUARY_ALLY_HEAL = SpellNumbers.SANCTUARY.allyHeal();
	public static final double SANCTUARY_RADIUS = SpellNumbers.SANCTUARY.radius();
	public static final float SANCTUARY_UNDEAD_DAMAGE = SpellNumbers.SANCTUARY.damage();
	public static final int SANCTUARY_PULSE_TICKS = 20;
	public static final int SANCTUARY_REGEN_TICKS = 40;
	public static final double FROST_NOVA_RADIUS = SpellNumbers.FROST_NOVA.radius();
	public static final float FROST_NOVA_DAMAGE = SpellNumbers.FROST_NOVA.damage();
	public static final int FROST_NOVA_SLOW_TICKS = SpellNumbers.FROST_NOVA.duration().ticks();
	public static final int FROST_NOVA_SLOW_AMPLIFIER = SpellNumbers.FROST_NOVA.amplifier();
	public static final double EARTHSPIKE_RADIUS = SpellNumbers.EARTHSPIKE.radius();
	public static final float EARTHSPIKE_DAMAGE = SpellNumbers.EARTHSPIKE.damage();
	public static final double EARTHSPIKE_LIFT = 1.0;
	public static final float PLAGUE_RADIUS = (float) SpellNumbers.PLAGUE_CLOUD.radius();
	public static final int PLAGUE_CLOUD_TICKS = SpellNumbers.PLAGUE_CLOUD.duration().ticks();
	public static final int PLAGUE_POISON_TICKS = SpellNumbers.PLAGUE_CLOUD.extra().ticks();
	public static final double FLAME_RING_RADIUS = SpellNumbers.FLAME_RING.radius();
	public static final float FLAME_RING_DAMAGE = SpellNumbers.FLAME_RING.damage();
	public static final int FLAME_RING_BURN_TICKS = SpellNumbers.FLAME_RING.extra().ticks();
	/** How long the caster wears the burning aura, and how often it scorches the hostiles around. */
	public static final int FLAME_AURA_TICKS = SpellNumbers.FLAME_RING.duration().ticks();
	public static final int FLAME_AURA_PULSE_TICKS = 20;
	public static final double SUPPORT_RADIUS = SpellNumbers.BULWARK_HYMN.radius();
	public static final int BULWARK_TICKS = SpellNumbers.BULWARK_HYMN.duration().ticks();
	public static final int BULWARK_AMPLIFIER = SpellNumbers.BULWARK_HYMN.amplifier();
	public static final int TAILWIND_TICKS = SpellNumbers.TAILWIND.duration().ticks();
	public static final int TAILWIND_AMPLIFIER = SpellNumbers.TAILWIND.amplifier();
	public static final int BATTLE_HYMN_TICKS = SpellNumbers.BATTLE_HYMN.duration().ticks();
	public static final int BATTLE_HYMN_AMPLIFIER = SpellNumbers.BATTLE_HYMN.amplifier();

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
			hurt(caster, mob, RpgBridge.damage(caster, ICE_LANCE_DAMAGE, modifiers.powerMultiplier()));
			freeze(mob, SpellNumbers.ICE_LANCE.controlTicks(mob, SpellNumbers.ICE_LANCE.durationTicks(modifiers)));
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

	// The caster gains the health the mob lost; heal scales by K.
	static boolean applySoulSiphon(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		float before = target.getHealth();
		hurt(caster, target, RpgBridge.damage(caster, SOUL_SIPHON_DAMAGE, modifiers.powerMultiplier()));
		CastHits.heal(caster, caster, RpgBridge.vanilla(before - Math.max(0.0F, target.getHealth())));
		return true;
	}

	// The dart itself always stings, so an undead mob, which shrugs off Poison, still feels it.
	static boolean applyVenomDart(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		hurt(caster, target, RpgBridge.damage(caster, VENOM_DAMAGE, modifiers.powerMultiplier()));
		target.addEffect(new MobEffectInstance(MobEffects.POISON, SpellNumbers.VENOM_DART.durationTicks(modifiers), VENOM_AMPLIFIER), caster);
		return true;
	}

	// Every hostile around the caster is weakened, and the caster grows stronger; both last longer for each one hexed.
	static boolean applySappingHex(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		List<Mob> hexed = hostiles(caster.level(), caster.position(), HEX_RADIUS * modifiers.radiusMultiplier());
		if (hexed.isEmpty()) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.SAPPING_HEX;
		int ticks = Math.min(HEX_MAX_TICKS, HEX_TICKS_PER_FOE * hexed.size());
		for (Mob mob : hexed) {
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, numbers.duration().scaled(ticks, modifiers), HEX_AMPLIFIER), caster);
		}
		caster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, numbers.extra().scaled(ticks, modifiers), HEX_STRENGTH_AMPLIFIER), caster);
		return true;
	}

	// Every hostile around the caster is slowed and can jump only half as high until it wears off.
	static boolean applyGravity(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		int base = SpellNumbers.GRAVITY.durationTicks(modifiers);
		for (Mob mob : hostiles(caster.level(), caster.position(), GRAVITY_RADIUS * modifiers.radiusMultiplier())) {
			int ticks = SpellNumbers.GRAVITY.controlTicks(mob, base);
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, GRAVITY_SLOW_AMPLIFIER), caster);
			SpellTimers.ground(mob, ticks);
		}
		return true;
	}

	static boolean applyBlindingFlash(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SpellNumbers.BLINDING_FLASH.durationTicks(modifiers), 0), caster);
		SpellStatusApi.mark(target, SpellNumbers.BLINDING_FLASH.extraTicks(modifiers), caster);
		return true;
	}

	static boolean applyEmberBrand(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		hurt(caster, target, RpgBridge.damage(caster, EMBER_DAMAGE, modifiers.powerMultiplier()));
		// Fire boils the water off first, so a Soaked mob does not catch light on this cast.
		if (target.hasEffect(SpellStatus.SOAKED)) {
			target.removeEffect(SpellStatus.SOAKED);
			return true;
		}
		int burn = SpellNumbers.EMBER_BRAND.durationTicks(modifiers);
		target.igniteForTicks(burn);
		SpellStatusApi.scorch(target, burn, caster);
		return true;
	}

	static boolean applyQuicksilver(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return caster.addEffect(new MobEffectInstance(MobEffects.SPEED, SpellNumbers.QUICKSILVER.durationTicks(modifiers), QUICKSILVER_AMPLIFIER), caster);
	}

	static boolean applyTideLungs(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		caster.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, SpellNumbers.TIDE_LUNGS.durationTicks(modifiers), 0), caster);
		caster.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, SpellNumbers.TIDE_LUNGS.extraTicks(modifiers), 0), caster);
		return true;
	}

	static boolean applyBerserk(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return caster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, SpellNumbers.BERSERK.durationTicks(modifiers), BERSERK_AMPLIFIER), caster);
	}

	static boolean applyHasteRune(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return caster.addEffect(new MobEffectInstance(MobEffects.HASTE, SpellNumbers.HASTE_RUNE.durationTicks(modifiers), HASTE_AMPLIFIER), caster);
	}

	// The veil holds until it runs out or its wearer strikes, and that first blow is the breaking one.
	static boolean applyShadowVeil(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		int ticks = SpellNumbers.SHADOW_VEIL.durationTicks(modifiers);
		boolean veiled = caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0), caster);
		if (veiled) {
			SpellTimers.veil(caster, ticks);
		}
		return veiled;
	}

	// A zone fixed at the cast spot: it regenerates players inside and burns undead inside, each pulse.
	static boolean applySanctuary(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellTimers.sanctuary(caster, caster.position(), SANCTUARY_RADIUS * modifiers.radiusMultiplier(),
			SpellNumbers.SANCTUARY.durationTicks(modifiers), modifiers.powerMultiplier());
		return true;
	}

	// Harmful effects only, from the caster and every player close by; a cast with nothing to clear does not count.
	static boolean applyPurify(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		double radius = PURIFY_RADIUS * modifiers.radiusMultiplier();
		boolean cleared = false;
		int linked = 0;
		for (ServerPlayer player : caster.level().players()) {
			if (player.isAlive() && (player == caster || player.distanceToSqr(caster) <= radius * radius)) {
				List<Holder<MobEffect>> harmful = player.getActiveEffects().stream()
					.map(MobEffectInstance::getEffect)
					.filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL)
					.toList();
				harmful.forEach(player::removeEffect);
				cleared |= !harmful.isEmpty();
				if (!harmful.isEmpty()) {
					CastHits.touch(caster);
				}
				if (player != caster && !harmful.isEmpty() && linked < SpellVisuals.MAX_EFFECT_LINK_RECIPIENTS) {
					SpellVisuals.linkAffected(caster, "purify", caster.position().add(0.0, 1.0, 0.0), player.position().add(0.0, 1.0, 0.0));
					linked++;
				}
			}
		}
		return cleared;
	}

	static boolean applyFrostNova(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		for (Mob hostile : hostiles(caster.level(), caster.position(), FROST_NOVA_RADIUS * modifiers.radiusMultiplier())) {
			hurt(caster, hostile, RpgBridge.damage(caster, FROST_NOVA_DAMAGE, modifiers.powerMultiplier()));
			int slow = SpellNumbers.FROST_NOVA.controlTicks(hostile, SpellNumbers.FROST_NOVA.durationTicks(modifiers));
			hostile.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, FROST_NOVA_SLOW_AMPLIFIER), caster);
			SpellStatusApi.chill(hostile, slow, SpellStatusApi.CHILL_FREEZE_TICKS, caster);
		}
		return true;
	}

	// The lift is set after the damage, so the hurt knockback cannot flatten it.
	static boolean applyEarthspike(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (point == null) {
			return false;
		}
		for (Mob hostile : hostiles(caster.level(), point, EARTHSPIKE_RADIUS * modifiers.radiusMultiplier())) {
			hurt(caster, hostile, RpgBridge.damage(caster, EARTHSPIKE_DAMAGE, modifiers.powerMultiplier()));
			Vec3 motion = hostile.getDeltaMovement();
			hostile.setDeltaMovement(motion.x, EARTHSPIKE_LIFT, motion.z);
		}
		return true;
	}

	static boolean applyPlagueCloud(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return point != null && cloud(caster, point, PLAGUE_RADIUS * (float) modifiers.radiusMultiplier(), SpellNumbers.PLAGUE_CLOUD.durationTicks(modifiers),
			new MobEffectInstance(MobEffects.POISON, PLAGUE_POISON_TICKS, 0));
	}

	// The caster wears a burning aura: every second it scorches the hostiles close around and never its wearer.
	static boolean applyFlameRing(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellTimers.flameAura(caster, SpellNumbers.FLAME_RING.durationTicks(modifiers), FLAME_RING_RADIUS * modifiers.radiusMultiplier(),
			RpgBridge.damage(caster, FLAME_RING_DAMAGE, modifiers.powerMultiplier()), SpellNumbers.FLAME_RING.extraTicks(modifiers));
		return true;
	}

	static boolean applyBulwarkHymn(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return bless(caster, modifiers, new MobEffectInstance(MobEffects.ABSORPTION, SpellNumbers.BULWARK_HYMN.durationTicks(modifiers), BULWARK_AMPLIFIER), "bulwark_hymn");
	}

	static boolean applyTailwind(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return bless(caster, modifiers, new MobEffectInstance(MobEffects.SPEED, SpellNumbers.TAILWIND.durationTicks(modifiers), TAILWIND_AMPLIFIER), "tailwind");
	}

	static boolean applyBattleHymn(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return bless(caster, modifiers, new MobEffectInstance(MobEffects.STRENGTH, SpellNumbers.BATTLE_HYMN.durationTicks(modifiers), BATTLE_HYMN_AMPLIFIER), "battle_hymn");
	}

	// The caster and every ally around: players, villagers, golems and tamed pets, never a hostile.
	private static boolean bless(
		final ServerPlayer caster, final SpellModifiers.CastModifiers modifiers, final MobEffectInstance effect,
		final String spellPath
	) {
		double radius = SpellNumbers.of(spellPath).radius(modifiers);
		int linked = 0;
		for (net.minecraft.world.entity.LivingEntity ally : caster.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
			caster.getBoundingBox().inflate(radius), candidate -> isAlly(candidate) && candidate.distanceToSqr(caster) <= radius * radius)) {
			if (ally.addEffect(new MobEffectInstance(effect), caster) && linked < SpellVisuals.MAX_EFFECT_LINK_RECIPIENTS) {
				SpellVisuals.linkAffected(caster, spellPath,
					caster.position().add(0.0, 1.0, 0.0), ally.position().add(0.0, 1.0, 0.0));
				linked++;
			}
		}
		caster.addEffect(new MobEffectInstance(effect), caster);
		return true;
	}

	static boolean isAlly(final net.minecraft.world.entity.LivingEntity entity) {
		return entity.isAlive() && !Friends.isFoe(entity)
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

	// Magic damage, as every spell deals; the hurt cooldown is cleared so a spell right after a hit still lands.
	static void hurt(final ServerPlayer caster, final Mob target, final float amount) {
		target.damageCooldownTime = 0;
		target.hurtServer(caster.level(), SpellCasting.spellSource(caster), amount);
	}

	private static boolean heal(final ServerPlayer player, final float amount) {
		return CastHits.heal(player, player, amount);
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
		CastHits.deferTo(cloud, caster);
		recorder.cloud(caster, cloud, effect);
		return true;
	}
}
