package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SpellCasting {
	static final double RANGE = SpellModifiers.BASE_TARGET_RANGE_BLOCKS;
	static final double STATIC_LASH_RADIUS = 4.0;
	static final double WARDENS_ROAR_RADIUS = 5.0;
	static final double TIME_LOCK_RADIUS = 5.0;
	static final double VOID_WELL_RADIUS = 5.0;
	static final double DRAGONFALL_RADIUS = 3.0;
	/** How far Riftwalk looks down a wall for ground to land on. */
	static final int RIFTWALK_MAX_DROP_BLOCKS = 4;
	/** A scroll's own cooldown, just long enough that a held button cannot burn a stack in a second. */
	public static final int SCROLL_COOLDOWN_TICKS = 20;
	static final float DAMAGE = 3.0f;
	static final float BURN_SECONDS = 2.0f;

	/** Doublecast: the chance a cast with a Doublecast helmet lands twice. */
	public static final float DOUBLECAST_CHANCE = 0.5F;
	private static final ThreadLocal<Boolean> SKIP_COOLDOWN = new ThreadLocal<>();
	/** How many doublecasts have landed since the server started; a test seam. */
	static volatile int doublecasts;

	/** Whether this cast lands twice; the test seam can force either side for one caster. */
	public interface DoublecastRoll {
		boolean twice(ServerPlayer caster);
	}

	private static final DoublecastRoll FAIR_DOUBLECAST = caster -> caster.getRandom().nextFloat() < DOUBLECAST_CHANCE;
	private static volatile DoublecastRoll doublecastRoll = FAIR_DOUBLECAST;

	private SpellCasting() {
	}

	public static void setDoublecastRoll(final DoublecastRoll roll) {
		doublecastRoll = roll == null ? FAIR_DOUBLECAST : roll;
	}

	// The second landing: the target's hurt cooldown from the first is set aside, so both hits count.
	private static boolean landAgain(final ServerPlayer caster, final SpellDefinition definition, final Mob target, final Vec3 point,
		final SpellModifiers.CastModifiers modifiers) {
		if (target != null) {
			target.damageCooldownTime = 0;
		}
		return definition.effect().apply(caster, target, point, modifiers);
	}

	/** Called by a spell that did something lesser than its full cast, such as calling a summon back: no cooldown follows. */
	static void skipCooldown() {
		SKIP_COOLDOWN.set(Boolean.TRUE);
	}

	public static CastResult cast(final ServerPlayer caster, final SpellbookItem book, final InteractionHand hand) {
		return cast(caster, book.definition(), caster.getItemInHand(hand), false);
	}

	/** A scroll casts through the same path, keeps its own short cooldown, and crumbles only when the spell lands. */
	public static CastResult castScroll(final ServerPlayer caster, final SpellScrollItem scroll, final InteractionHand hand) {
		ItemStack stack = caster.getItemInHand(hand);
		CastResult result = cast(caster, scroll.definition(), stack, true);
		if (result == CastResult.CAST) {
			stack.consume(1, caster);
		}
		return result;
	}

	private static CastResult cast(
		final ServerPlayer caster, final SpellDefinition definition, final ItemStack stack, final boolean scroll
	) {
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(
			stack, caster.getItemBySlot(EquipmentSlot.HEAD), caster.getItemBySlot(EquipmentSlot.CHEST)
		);
		modifiers = TowerBridge.grow(stack, definition.id().getPath(), modifiers);
		Mob target = null;
		Vec3 point = null;
		switch (definition.targetMode()) {
			case SELF -> point = caster.position();
			case HOSTILE -> target = findTarget(caster, modifiers.targetRangeBlocks());
			case BLOCK -> point = findBlockTarget(caster, modifiers.targetRangeBlocks());
			case SAFE_TELEPORT -> point = findRiftwalkDestination(caster, modifiers.targetRangeBlocks());
		}

		// A summon's recall answers even while the book rests, since calling a servant back is not a new summon.
		boolean recall = definition.id().equals(SummonSkeleton.ID) && SummonSkeleton.find(caster.level().getServer(), caster.getUUID()) != null;
		if (caster.getCooldowns().isOnCooldown(stack) && !recall) {
			log(caster, definition, CastResult.COOLDOWN, target, modifiers, stack);
			return CastResult.COOLDOWN;
		}

		if (!hasValidTarget(definition.targetMode(), target, point)) {
			log(caster, definition, CastResult.NO_TARGET, target, modifiers, stack);
			return CastResult.NO_TARGET;
		}

		Vec3 departure = caster.position();
		SKIP_COOLDOWN.remove();
		if (!definition.effect().apply(caster, target, point, modifiers)) {
			SKIP_COOLDOWN.remove();
			log(caster, definition, CastResult.NO_TARGET, target, modifiers, stack);
			return CastResult.NO_TARGET;
		}

		boolean skip = SKIP_COOLDOWN.get() != null;
		SKIP_COOLDOWN.remove();
		SpellVisuals.play(caster, definition, target, point, departure, modifiers);
		if (!skip) {
			caster.getCooldowns().addCooldown(stack, scroll ? SCROLL_COOLDOWN_TICKS : TowerBridge.cooldown(caster, modifiers.cooldownTicks()));
		}
		log(caster, definition, CastResult.CAST, target, modifiers, stack);
		SpellCombos.afterCast(caster, definition, target, point, modifiers);
		// Doublecast: the same spell, on the same target, once more, combos included; never a second summon.
		if (!skip && SpellModifiers.doublecast(caster.getItemBySlot(EquipmentSlot.HEAD)) && !Spellbooks.BOOK_ONLY.contains(definition.id())
			&& (target == null || target.isAlive()) && doublecastRoll.twice(caster)
			&& landAgain(caster, definition, target, point, modifiers)) {
			SpellVisuals.play(caster, definition, target, point, caster.position(), modifiers);
			SpellCombos.afterCast(caster, definition, target, point, modifiers);
			doublecasts++;
			Spellbooks.LOGGER.info("[SPELL] spell_id={} result=DOUBLECAST caster={}", definition.id(), caster.getScoreboardName());
		}
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
		double radius = WARDENS_ROAR_RADIUS * modifiers.radiusMultiplier();
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			caster.getBoundingBox().inflate(radius),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
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
		double radius = STATIC_LASH_RADIUS * modifiers.radiusMultiplier();
		primary.hurt(damage, DAMAGE * power);
		List<Mob> candidates = new ArrayList<>(caster.level().getEntitiesOfClass(
			Mob.class,
			primary.getBoundingBox().inflate(radius),
			candidate -> candidate != primary
				&& candidate.isAlive()
				&& Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(primary.position()) <= radius * radius
		));
		candidates.sort(
			Comparator.comparingDouble((Mob candidate) -> candidate.position().distanceToSqr(primary.position()))
				.thenComparingInt(Mob::getId)
		);
		for (int i = 0; i < Math.min(2, candidates.size()); i++) {
			Mob chained = candidates.get(i);
			chained.hurt(damage, DAMAGE * power);
			SpellVisuals.linkAffected(caster, "static_lash",
				primary.getBoundingBox().getCenter(), chained.getBoundingBox().getCenter());
		}
		return true;
	}

	static boolean applyChainstorm(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		return point != null && SpellStorms.start(caster, point, modifiers);
	}

	static boolean applyTimeLock(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}

		double radius = TIME_LOCK_RADIUS * modifiers.radiusMultiplier();
		int duration = effectDurationTicks(modifiers, 100, false);
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& Friends.isFoe(candidate)
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

		double radius = VOID_WELL_RADIUS * modifiers.radiusMultiplier();
		int duration = effectDurationTicks(modifiers, 60, false);
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& Friends.isFoe(candidate)
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
		caster.resetFallDistance();
		return true;
	}

	static boolean applyDragonfall(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}

		double radius = DRAGONFALL_RADIUS * modifiers.radiusMultiplier();
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& Friends.isFoe(candidate)
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

	// The block face under the crosshair: standing on it when it is a floor, in front of it and down to its ground otherwise.
	private static Vec3 findRiftwalkDestination(final ServerPlayer caster, final int rangeBlocks) {
		ServerLevel level = caster.level();
		Vec3 start = caster.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(
			start, start.add(caster.getLookAngle().scale(rangeBlocks)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster
		));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockPos feet = hit.getBlockPos();
		double floor = hit.getLocation().y;
		if (hit.getDirection() != Direction.UP) {
			feet = feet.relative(hit.getDirection());
			floor = floorTop(level, feet);
			for (int drop = 0; drop < RIFTWALK_MAX_DROP_BLOCKS && Double.isNaN(floor); drop++) {
				feet = feet.below();
				floor = floorTop(level, feet);
			}
			if (Double.isNaN(floor)) {
				return null;
			}
		}

		Vec3 candidate = new Vec3(feet.getX() + 0.5, floor, feet.getZ() + 0.5);
		return fits(caster, candidate) ? candidate : null;
	}

	// Where a caster in this cell would stand: on a low block inside it, such as a slab or a carpet, or on a full-height block under it.
	private static double floorTop(final ServerLevel level, final BlockPos feet) {
		VoxelShape here = level.getBlockState(feet).getCollisionShape(level, feet);
		if (!here.isEmpty()) {
			double top = here.max(Direction.Axis.Y);
			return top < 1.0 ? feet.getY() + top : Double.NaN;
		}

		BlockPos below = feet.below();
		VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
		return !under.isEmpty() && under.max(Direction.Axis.Y) >= 1.0 ? feet.getY() : Double.NaN;
	}

	/** True when the caster can stand at the spot: solid ground, no block, mob or fluid in the way. */
	static boolean fits(final ServerPlayer caster, final Vec3 spot) {
		ServerLevel level = caster.level();
		AABB moved = caster.getBoundingBox().move(spot.subtract(caster.position()));
		AABB underFoot = new AABB(moved.minX, spot.y - 0.05, moved.minZ, moved.maxX, spot.y, moved.maxZ);
		return !level.noBlockCollision(caster, underFoot)
			&& level.noCollision(caster, moved)
			&& level.getEntities(caster, moved, EntitySelector.ENTITY_STILL_ALIVE).isEmpty()
			&& !containsFluid(level, moved);
	}

	private static boolean isSturdyFloor(final ServerLevel level, final BlockPos pos) {
		return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
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
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
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
