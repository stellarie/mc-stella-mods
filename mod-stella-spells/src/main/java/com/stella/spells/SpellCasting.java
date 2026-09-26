package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SpellCasting {
	static final double RANGE = SpellModifiers.BASE_TARGET_RANGE_BLOCKS;
	static final double RIFTWALK_RANGE_BLOCKS = 12.0;
	static final double RIFTWALK_STEP_BLOCKS = 0.5;
	private static final float DAMAGE = 3.0f;
	private static final float BURN_SECONDS = 2.0f;

	private SpellCasting() {
	}

	public static CastResult cast(final ServerPlayer caster, final SpellbookItem book, final InteractionHand hand) {
		ItemStack stack = caster.getItemInHand(hand);
		SpellDefinition definition = book.definition();
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(
			stack, caster.getItemBySlot(EquipmentSlot.HEAD), caster.getItemBySlot(EquipmentSlot.CHEST)
		);
		Mob target = null;
		Vec3 point = null;
		switch (definition.targetMode()) {
			case SELF -> point = caster.position();
			case HOSTILE -> target = findTarget(caster, modifiers.targetRangeBlocks());
			case BLOCK -> point = findBlockTarget(caster, modifiers.targetRangeBlocks());
			case SAFE_TELEPORT -> point = findRiftwalkDestination(caster);
		}

		if (caster.getCooldowns().isOnCooldown(stack)) {
			log(caster, definition, CastResult.COOLDOWN, target, modifiers, stack);
			return CastResult.COOLDOWN;
		}

		if (!hasValidTarget(definition.targetMode(), target, point)) {
			log(caster, definition, CastResult.NO_TARGET, target, modifiers, stack);
			return CastResult.NO_TARGET;
		}

		Vec3 departure = caster.position();
		if (!definition.effect().apply(caster, target, point, modifiers)) {
			log(caster, definition, CastResult.NO_TARGET, target, modifiers, stack);
			return CastResult.NO_TARGET;
		}

		SpellVisuals.play(caster, definition, target, point, departure, modifiers);
		caster.getCooldowns().addCooldown(stack, modifiers.cooldownTicks());
		log(caster, definition, CastResult.CAST, target, modifiers, stack);
		SpellCombos.afterCast(caster, definition, target, point, modifiers);
		return CastResult.CAST;
	}

	static boolean applySparkShot(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}

		target.hurt(caster.level().damageSources().magic(), DAMAGE * (float) modifiers.powerMultiplier());
		target.igniteForSeconds(effectDurationTicks(modifiers, (int) (BURN_SECONDS * 20.0f), false) / 20.0f);
		return true;
	}

	static boolean applyFrostbind(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return target != null && target.addEffect(
			new MobEffectInstance(MobEffects.SLOWNESS, effectDurationTicks(modifiers, 80, false), 1), caster
		);
	}

	static boolean applyBriarSnare(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return target != null && target.addEffect(
			new MobEffectInstance(MobEffects.SLOWNESS, effectDurationTicks(modifiers, 80, false), 3), caster
		);
	}

	static boolean applyGaleStep(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		Vec3 look = caster.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0, look.z).normalize();
		caster.setDeltaMovement(horizontal.x * 1.2, 0.45, horizontal.z * 1.2);
		caster.addEffect(
			new MobEffectInstance(MobEffects.SLOW_FALLING, effectDurationTicks(modifiers, 40, false), 0), caster
		);
		return true;
	}

	static boolean applyMendingWord(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		float healthBefore = caster.getHealth();
		caster.heal(4.0f * (float) modifiers.powerMultiplier());
		return caster.getHealth() > healthBefore;
	}

	static boolean applyStoneguard(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		caster.addEffect(
			new MobEffectInstance(MobEffects.RESISTANCE, effectDurationTicks(modifiers, 80, true), 0), caster
		);
		return true;
	}

	static boolean applyOwlsGaze(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		caster.addEffect(
			new MobEffectInstance(MobEffects.NIGHT_VISION, effectDurationTicks(modifiers, 900, false), 0), caster
		);
		return true;
	}

	static boolean applySlimebound(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		int duration = effectDurationTicks(modifiers, 160, false);
		caster.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, duration, 1), caster);
		caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, 0), caster);
		return true;
	}

	static boolean applyWitherSeal(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return target != null && target.addEffect(
			new MobEffectInstance(MobEffects.WITHER, effectDurationTicks(modifiers, 100, false), 1), caster
		);
	}

	static boolean applyPhoenixMantle(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		caster.addEffect(
			new MobEffectInstance(MobEffects.FIRE_RESISTANCE, effectDurationTicks(modifiers, 600, true), 0), caster
		);
		caster.heal(4.0f * (float) modifiers.powerMultiplier());
		return true;
	}

	static boolean applyLifeBloom(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		caster.heal(8.0f * (float) modifiers.powerMultiplier());
		caster.addEffect(
			new MobEffectInstance(MobEffects.REGENERATION, effectDurationTicks(modifiers, 100, true), 0), caster
		);
		return true;
	}

	static boolean applyAegisOfTheDeep(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		int duration = effectDurationTicks(modifiers, 80, true);
		caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 1), caster);
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0), caster);
		return true;
	}

	static boolean applyWardensRoar(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		double radius = 5.0 * modifiers.radiusMultiplier();
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			caster.getBoundingBox().inflate(radius),
			candidate -> candidate.isAlive() && candidate instanceof Enemy
				&& candidate.position().distanceToSqr(caster.position()) <= radius * radius
		)) {
			hostile.hurt(caster.level().damageSources().magic(), 6.0f * (float) modifiers.powerMultiplier());
			Vec3 away = hostile.position().subtract(caster.position());
			Vec3 horizontal = new Vec3(away.x, 0.0, away.z).normalize();
			hostile.setDeltaMovement(horizontal.x, 0.4, horizontal.z);
		}
		return true;
	}

	static boolean applyStaticLash(
		final ServerPlayer caster, final Mob primary, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (primary == null) {
			return false;
		}

		var damage = caster.level().damageSources().magic();
		float power = (float) modifiers.powerMultiplier();
		double radius = 4.0 * modifiers.radiusMultiplier();
		primary.hurt(damage, DAMAGE * power);
		List<Mob> candidates = new ArrayList<>(caster.level().getEntitiesOfClass(
			Mob.class,
			primary.getBoundingBox().inflate(radius),
			candidate -> candidate != primary
				&& candidate.isAlive()
				&& candidate instanceof Enemy
				&& candidate.position().distanceToSqr(primary.position()) <= radius * radius
		));
		candidates.sort(
			Comparator.comparingDouble((Mob candidate) -> candidate.position().distanceToSqr(primary.position()))
				.thenComparingInt(Mob::getId)
		);
		for (int i = 0; i < Math.min(2, candidates.size()); i++) {
			candidates.get(i).hurt(damage, DAMAGE * power);
		}
		return true;
	}

	static boolean applyChainstorm(
		final ServerPlayer caster, final Mob primary, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (primary == null) {
			return false;
		}

		var damage = caster.level().damageSources().magic();
		float power = (float) modifiers.powerMultiplier();
		double radius = 4.0 * modifiers.radiusMultiplier();
		primary.hurt(damage, 7.0f * power);
		Set<Mob> hitTargets = new HashSet<>();
		hitTargets.add(primary);
		Mob previous = primary;
		for (int i = 0; i < 2; i++) {
			Mob chainOrigin = previous;
			List<Mob> candidates = new ArrayList<>(caster.level().getEntitiesOfClass(
				Mob.class,
				chainOrigin.getBoundingBox().inflate(radius),
				candidate -> candidate.isAlive()
					&& candidate instanceof Enemy
					&& !hitTargets.contains(candidate)
					&& candidate.position().distanceToSqr(chainOrigin.position()) <= radius * radius
			));
			candidates.sort(
				Comparator.comparingDouble((Mob candidate) -> candidate.position().distanceToSqr(chainOrigin.position()))
					.thenComparingInt(Mob::getId)
			);
			if (candidates.isEmpty()) {
				break;
			}

			previous = candidates.get(0);
			hitTargets.add(previous);
			previous.hurt(damage, 4.0f * power);
		}
		return true;
	}

	static boolean applyTimeLock(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}

		double radius = 5.0 * modifiers.radiusMultiplier();
		int duration = effectDurationTicks(modifiers, 100, false);
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& candidate instanceof Enemy
				&& candidate.position().distanceToSqr(point) <= radius * radius
		)) {
			hostile.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 1), caster);
		}
		return true;
	}

	static boolean applyVoidWell(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}

		double radius = 5.0 * modifiers.radiusMultiplier();
		int duration = effectDurationTicks(modifiers, 60, false);
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& candidate instanceof Enemy
				&& candidate.position().distanceToSqr(point) <= radius * radius
		)) {
			Vec3 toward = point.subtract(hostile.position());
			Vec3 horizontal = new Vec3(toward.x, 0.0, toward.z).normalize();
			hostile.setDeltaMovement(horizontal.x * 0.8, 0.1, horizontal.z * 0.8);
			hostile.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 1), caster);
		}
		return true;
	}

	static boolean applyTidalPush(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}

		Vec3 away = target.position().subtract(caster.position());
		Vec3 horizontal = new Vec3(away.x, 0.0, away.z).normalize();
		target.setDeltaMovement(horizontal.x * 1.2, 0.2, horizontal.z * 1.2);
		return true;
	}

	private static boolean hasValidTarget(final TargetMode mode, final Mob target, final Vec3 point) {
		return switch (mode) {
			case SELF -> true;
			case HOSTILE -> target != null && target.isAlive() && !target.isInvulnerable();
			case BLOCK, SAFE_TELEPORT -> point != null;
		};
	}

	static boolean applyRiftwalk(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (point == null) {
			return false;
		}

		caster.teleportTo(point.x, point.y, point.z);
		return true;
	}

	static boolean applyDragonfall(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}

		double radius = 3.0 * modifiers.radiusMultiplier();
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& candidate instanceof Enemy
				&& candidate.position().distanceToSqr(point) <= radius * radius
		)) {
			hostile.hurt(caster.level().damageSources().magic(), 10.0f * (float) modifiers.powerMultiplier());
		}
		return true;
	}

	private static int effectDurationTicks(
		final SpellModifiers.CastModifiers modifiers,
		final int baseTicks,
		final boolean defensive
	) {
		return modifiers.durationTicks(baseTicks, defensive);
	}

	private static Vec3 findRiftwalkDestination(final ServerPlayer caster) {
		ServerLevel level = caster.level();
		Vec3 start = caster.position();
		Vec3 look = caster.getLookAngle();
		int sampleCount = (int) Math.ceil(RIFTWALK_RANGE_BLOCKS / RIFTWALK_STEP_BLOCKS);
		for (int i = 1; i <= sampleCount; i++) {
			Vec3 candidate = start.add(look.scale(i * RIFTWALK_STEP_BLOCKS));
			BlockPos ground = BlockPos.containing(candidate.x, candidate.y - 0.01, candidate.z);
			if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
				continue;
			}

			AABB moved = caster.getBoundingBox().move(candidate.subtract(start));
			if (!level.noCollision(caster, moved)
				|| !level.getEntities(caster, moved, EntitySelector.ENTITY_STILL_ALIVE).isEmpty()
				|| containsFluid(level, moved)) {
				continue;
			}
			return candidate;
		}
		return null;
	}

	private static boolean containsFluid(final ServerLevel level, final AABB box) {
		for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
			for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
				for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
					if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static Vec3 findBlockTarget(final ServerPlayer caster, final int rangeBlocks) {
		ServerLevel level = caster.level();
		Vec3 start = caster.getEyePosition();
		Vec3 end = start.add(caster.getLookAngle().scale(rangeBlocks));
		BlockHitResult blockHit = level.clip(
			new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)
		);
		return blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : null;
	}

	private static Mob findTarget(final ServerPlayer caster, final int rangeBlocks) {
		ServerLevel level = caster.level();
		Vec3 start = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		Vec3 end = start.add(look.scale(rangeBlocks));
		BlockHitResult blockHit = level.clip(
			new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)
		);
		double maxDistance = blockHit.getType() == HitResult.Type.MISS
			? rangeBlocks
			: start.distanceTo(blockHit.getLocation());
		double maxDistanceSquared = maxDistance * maxDistance;
		AABB search = caster.getBoundingBox().expandTowards(look.scale(rangeBlocks)).inflate(1.0);
		Mob nearest = null;
		double nearestDistanceSquared = maxDistanceSquared;

		for (Mob mob : level.getEntitiesOfClass(
			Mob.class,
			search,
			candidate -> candidate.isAlive() && candidate instanceof Enemy
		)) {
			Optional<Vec3> hit = mob.getBoundingBox().inflate(mob.getPickRadius()).clip(start, end);
			if (hit.isEmpty()) {
				continue;
			}

			double distanceSquared = start.distanceToSqr(hit.orElseThrow());
			if (distanceSquared < nearestDistanceSquared) {
				nearest = mob;
				nearestDistanceSquared = distanceSquared;
			}
		}

		return nearest;
	}

	private static void log(
		final ServerPlayer caster,
		final SpellDefinition definition,
		final CastResult result,
		final Mob target,
		final SpellModifiers.CastModifiers modifiers,
		final ItemStack book
	) {
		Spellbooks.LOGGER.info(
			"[SPELL] spell_id={} result={} target={} cooldown_ticks={} book_enchants={} head_enchants={} chest_enchants={} caster={}",
			definition.id(),
			result,
			target == null ? "none" : target.getUUID(),
			modifiers.cooldownTicks(),
			enchantments(book),
			enchantments(caster.getItemBySlot(EquipmentSlot.HEAD)),
			enchantments(caster.getItemBySlot(EquipmentSlot.CHEST)),
			caster.getUUID()
		);
	}

	private static String enchantments(final ItemStack stack) {
		ItemEnchantments values = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		List<String> entries = new ArrayList<>();
		for (Holder<Enchantment> enchantment : values.keySet()) {
			String id = enchantment.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse(null);
			if (id != null) {
				entries.add(id + ":" + values.getLevel(enchantment));
			}
		}
		entries.sort(Comparator.naturalOrder());
		return entries.toString();
	}

	public enum CastResult {
		CAST,
		NO_TARGET,
		COOLDOWN
	}
}
