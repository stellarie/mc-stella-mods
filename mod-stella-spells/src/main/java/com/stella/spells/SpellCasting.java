package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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
import org.jspecify.annotations.Nullable;

public final class SpellCasting {
	/** A spell hit names its caster, so the RPG mod reads penetration and the spell crit from the attacker. */
	static DamageSource spellSource(final LivingEntity caster) {
		return caster instanceof Player ? caster.level().damageSources().indirectMagic(caster, caster) : caster.level().damageSources().magic();
	}

	static final double RANGE = SpellModifiers.BASE_TARGET_RANGE_BLOCKS;
	/** How long Tidal Push leaves a mob Soaked, before the cast's offensive duration multiplier. */
	static final int TIDAL_SOAK_TICKS = 200;
	static final double STATIC_LASH_RADIUS = SpellNumbers.STATIC_LASH.radius();
	static final double WARDENS_ROAR_RADIUS = SpellNumbers.WARDENS_ROAR.radius();
	static final double TIME_LOCK_RADIUS = SpellNumbers.TIME_LOCK.radius();
	static final double VOID_WELL_RADIUS = SpellNumbers.VOID_WELL.radius();
	static final double DRAGONFALL_RADIUS = SpellNumbers.DRAGONFALL.radius();
	/** How far Riftwalk looks down a wall for ground to land on. */
	static final int RIFTWALK_MAX_DROP_BLOCKS = 4;
	/** A scroll's own cooldown, just long enough that a held button cannot burn a stack in a second. */
	public static final int SCROLL_COOLDOWN_TICKS = 20;
	static final float DAMAGE = SpellNumbers.SPARK_SHOT.damage();

	/** Doublecast: the chance a cast with a Doublecast helmet or book lands twice. */
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
	/** A cast under Nausea fizzles at the same rate an attack fumbles in the RPG mod. */
	public static final double NAUSEA_FIZZLE_CHANCE = 0.70;
	private static final FizzleRoll FAIR_FIZZLE = caster -> caster.getRandom().nextDouble() < NAUSEA_FIZZLE_CHANCE;
	private static volatile FizzleRoll fizzleRoll = FAIR_FIZZLE;

	private SpellCasting() {
	}

	/** The roll now in force, so a test can put it back. */
	static DoublecastRoll doublecastRoll() {
		return doublecastRoll;
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

	/** Test seam: whether the cast on this thread asked for no cooldown. */
	public static boolean skipCooldownRequested() {
		return SKIP_COOLDOWN.get() != null;
	}

	public static CastResult cast(final ServerPlayer caster, final SpellbookItem book, final InteractionHand hand) {
		return cast(caster, book.definition(), caster.getItemInHand(hand), false);
	}

	/**
	 * The ally an {@code ALLY} cast resolves: the one the caster looks at within reach, else the caster
	 * itself. {@link SkillTargeting#isAlly} owns the rule, so a foe is never helped.
	 */
	static LivingEntity findAlly(final ServerPlayer caster, final int rangeBlocks) {
		LivingEntity looked = findAlongRay(caster, rangeBlocks, candidate -> MoreSpells.isAlly(candidate));
		return looked == null ? caster : looked;
	}

	// The first body on the caster's look ray, a block stopping it, that this filter accepts.
	private static @Nullable LivingEntity findAlongRay(final ServerPlayer caster, final int rangeBlocks, final java.util.function.Predicate<LivingEntity> wanted) {
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
		LivingEntity nearest = null;
		double nearestDistanceSquared = maxDistanceSquared;

		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, search, candidate -> candidate.isAlive() && wanted.test(candidate))) {
			Optional<Vec3> hit = body.getBoundingBox().inflate(body.getPickRadius()).clip(start, end);
			if (hit.isEmpty()) {
				continue;
			}

			double distanceSquared = start.distanceToSqr(hit.orElseThrow());
			if (distanceSquared < nearestDistanceSquared) {
				nearest = body;
				nearestDistanceSquared = distanceSquared;
			}
		}

		return nearest;
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

	/**
	 * One entry of a Compendium: cast like a scroll's spell, true when it landed. Neither the entry's cooldown
	 * nor the held stack's gates it, and none starts; the Compendium sets its own after every entry has cast.
	 */
	static boolean castStored(final ServerPlayer caster, final SpellDefinition definition, final ItemStack compendium) {
		return cast(caster, definition, compendium, true, true) == CastResult.CAST;
	}

	private static CastResult cast(
		final ServerPlayer caster, final SpellDefinition definition, final ItemStack stack, final boolean scroll
	) {
		return cast(caster, definition, stack, scroll, false);
	}

	private static CastResult cast(
		final ServerPlayer caster, final SpellDefinition definition, final ItemStack stack, final boolean scroll, final boolean free
	) {
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(
			stack, caster.getItemBySlot(EquipmentSlot.HEAD), caster.getItemBySlot(EquipmentSlot.CHEST)
		);
		modifiers = TowerBridge.grow(stack, definition.id().getPath(), modifiers);
		modifiers = RpgBridge.grow(caster, definition.id().getPath(), modifiers, definition.school());
		Mob target = null;
		Vec3 point = null;
		LivingEntity ally = null;
		// An archer skill reaches a fixed distance, whatever sharpens a mage's sight.
		int reach = definition.school() == School.ARCHER ? ArcherSkills.SKILL_RANGE_BLOCKS : modifiers.targetRangeBlocks();
		switch (definition.targetMode()) {
			case SELF -> point = caster.position();
			case HOSTILE -> target = findTarget(caster, reach);
			case BLOCK -> point = findBlockTarget(caster, reach);
			case SAFE_TELEPORT -> point = findRiftwalkDestination(caster, modifiers.targetRangeBlocks());
			case ALLY -> ally = findAlly(caster, modifiers.targetRangeBlocks());
		}

		// The cooldown applies to every cast of a spell, a summon's recall included, so a recast cannot heal or
		// move a living servant for free.
		if (!free && caster.getCooldowns().isOnCooldown(stack)) {
			log(caster, definition, CastResult.COOLDOWN, target, modifiers, stack);
			return CastResult.COOLDOWN;
		}

		if (!hasValidTarget(definition.targetMode(), target, point)) {
			log(caster, definition, CastResult.NO_TARGET, target, modifiers, stack);
			return CastResult.NO_TARGET;
		}

		if (caster.hasEffect(MobEffects.NAUSEA) && fizzleRoll.fizzles(caster)) {
			if (!free) {
				caster.getCooldowns().addCooldown(stack, scroll ? SCROLL_COOLDOWN_TICKS : TowerBridge.cooldown(caster, modifiers.cooldownTicks()));
			}
			caster.level().playSound(null, caster.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
			caster.sendSystemMessage(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".fizzle"), true);
			log(caster, definition, CastResult.FIZZLE, target, modifiers, stack);
			return CastResult.FIZZLE;
		}

		Vec3 departure = caster.position();
		SKIP_COOLDOWN.remove();
		Awakening.report(definition.id(), modifiers.awakened());
		boolean completed = false;
		CastHits.begin(caster, definition.id(), !scroll && !Awakening.isAwakened(stack));
		CastContext.setAlly(ally);
		try {
			boolean applied = definition.effect().apply(caster, target, point, modifiers);
			if (!applied) {
				log(caster, definition, CastResult.NO_TARGET, target, modifiers, stack);
				return CastResult.NO_TARGET;
			}

			boolean skip = SKIP_COOLDOWN.get() != null;
			SKIP_COOLDOWN.remove();
			SpellVisuals.play(caster, definition, target, point, departure, modifiers);
			if (definition.school() == School.ARCHER) {
				ArcherDraw.play(caster);
			}
			if (!skip && !free) {
				caster.getCooldowns().addCooldown(stack, scroll ? SCROLL_COOLDOWN_TICKS : TowerBridge.cooldown(caster, modifiers.cooldownTicks()));
			}
			log(caster, definition, CastResult.CAST, target, modifiers, stack);
			SpellCombos.afterCast(caster, definition, target, point, modifiers);
			// Both landings share one cast credit.
			if (!skip && SpellModifiers.doublecast(caster.getItemBySlot(EquipmentSlot.HEAD), stack) && !Spellbooks.BOOK_ONLY.contains(definition.id())
				&& (target == null || target.isAlive()) && doublecastRoll.twice(caster)
				&& landAgain(caster, definition, target, point, modifiers)) {
				SpellVisuals.play(caster, definition, target, point, caster.position(), modifiers);
				SpellCombos.afterCast(caster, definition, target, point, modifiers);
				doublecasts++;
				Spellbooks.LOGGER.info("[SPELL] spell_id={} result=DOUBLECAST caster={}", definition.id(), caster.getScoreboardName());
			}
			completed = true;
			return CastResult.CAST;
		} finally {
			boolean hit = CastHits.end(completed);
			SKIP_COOLDOWN.remove();
			CastContext.setAlly(null);
			if (hit && !scroll) {
				Awakening.recordCast(caster, stack);
			}
		}
	}

	static boolean applySparkShot(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}

		SpellNumbers numbers = SpellNumbers.SPARK_SHOT;
		target.hurt(spellSource(caster), RpgBridge.damage(caster, numbers.damage(), modifiers.powerMultiplier()));
		target.igniteForSeconds(numbers.extraTicks(modifiers) / 20.0f);
		return true;
	}

	static boolean applyFrostbind(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		int ticks = SpellNumbers.FROSTBIND.controlTicks(target, SpellNumbers.FROSTBIND.durationTicks(modifiers));
		boolean slowed = target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, SpellNumbers.FROSTBIND.amplifier()), caster);
		SpellStatusApi.chill(target, ticks, SpellStatusApi.CHILL_FREEZE_TICKS, caster);
		return slowed;
	}

	static boolean applyBriarSnare(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return target != null && target.addEffect(
			new MobEffectInstance(MobEffects.SLOWNESS, SpellNumbers.BRIAR_SNARE.controlTicks(target, SpellNumbers.BRIAR_SNARE.durationTicks(modifiers)),
				SpellNumbers.BRIAR_SNARE.amplifier()), caster
		);
	}

	static boolean applyGaleStep(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		Vec3 look = caster.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0, look.z).normalize();
		caster.setDeltaMovement(horizontal.x * 1.2, 0.45, horizontal.z * 1.2);
		CastHits.touch(caster);
		caster.addEffect(
			new MobEffectInstance(MobEffects.SLOW_FALLING, SpellNumbers.GALE_STEP.durationTicks(modifiers), SpellNumbers.GALE_STEP.amplifier()), caster
		);
		return true;
	}

	static boolean applyMendingWord(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		float healthBefore = caster.getHealth();
		CastHits.heal(caster, caster, RpgBridge.heal(caster, SpellNumbers.MENDING_WORD.heal(), modifiers.powerMultiplier()));
		return caster.getHealth() > healthBefore;
	}

	static boolean applyStoneguard(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		caster.addEffect(
			new MobEffectInstance(MobEffects.RESISTANCE, SpellNumbers.STONEGUARD.durationTicks(modifiers), SpellNumbers.STONEGUARD.amplifier()), caster
		);
		return true;
	}

	static boolean applyOwlsGaze(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		caster.addEffect(
			new MobEffectInstance(MobEffects.NIGHT_VISION, SpellNumbers.OWLS_GAZE.durationTicks(modifiers), SpellNumbers.OWLS_GAZE.amplifier()), caster
		);
		return true;
	}

	static boolean applySlimebound(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellNumbers numbers = SpellNumbers.SLIMEBOUND;
		int duration = numbers.durationTicks(modifiers);
		caster.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, duration, numbers.amplifier()), caster);
		caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, numbers.extraAmplifier()), caster);
		return true;
	}

	static boolean applyWitherSeal(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		return target != null && target.addEffect(
			new MobEffectInstance(MobEffects.WITHER, SpellNumbers.WITHER_SEAL.durationTicks(modifiers), SpellNumbers.WITHER_SEAL.amplifier()), caster
		);
	}

	static boolean applyPhoenixMantle(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		SpellNumbers numbers = SpellNumbers.PHOENIX_MANTLE;
		caster.addEffect(
			new MobEffectInstance(MobEffects.FIRE_RESISTANCE, numbers.durationTicks(modifiers), numbers.amplifier()), caster
		);
		CastHits.heal(caster, caster, RpgBridge.heal(caster, numbers.heal(), modifiers.powerMultiplier()));
		return true;
	}

	static boolean applyLifeBloom(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellNumbers numbers = SpellNumbers.LIFE_BLOOM;
		CastHits.heal(caster, caster, RpgBridge.heal(caster, numbers.heal(), modifiers.powerMultiplier()));
		caster.addEffect(
			new MobEffectInstance(MobEffects.REGENERATION, numbers.durationTicks(modifiers), numbers.amplifier()), caster
		);
		return true;
	}

	static boolean applyAegisOfTheDeep(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		SpellNumbers numbers = SpellNumbers.AEGIS_OF_THE_DEEP;
		int duration = numbers.durationTicks(modifiers);
		caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, numbers.amplifier()), caster);
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, numbers.extraAmplifier()), caster);
		return true;
	}

	static boolean applyWardensRoar(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		double radius = SpellNumbers.WARDENS_ROAR.radius(modifiers);
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			caster.getBoundingBox().inflate(radius),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(caster.position()) <= radius * radius
		)) {
			hostile.hurt(spellSource(caster), RpgBridge.damage(caster, SpellNumbers.WARDENS_ROAR.damage(), modifiers.powerMultiplier()));
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

		var damage = spellSource(caster);
		double power = modifiers.powerMultiplier();
		SpellNumbers numbers = SpellNumbers.STATIC_LASH;
		double radius = numbers.radius(modifiers);
		primary.hurt(damage, RpgBridge.damage(caster, numbers.damage(), power) * SpellStatusApi.lightningFactor(primary));
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
		for (int i = 0; i < Math.min(numbers.targets(), candidates.size()); i++) {
			Mob chained = candidates.get(i);
			chained.hurt(damage, RpgBridge.damage(caster, numbers.damage(), power) * SpellStatusApi.lightningFactor(chained));
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

		SpellNumbers numbers = SpellNumbers.TIME_LOCK;
		double radius = numbers.radius(modifiers);
		int duration = numbers.durationTicks(modifiers);
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(point) <= radius * radius
		)) {
			hostile.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, numbers.controlTicks(hostile, duration), numbers.amplifier()), caster);
		}
		return true;
	}

	static boolean applyVoidWell(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}

		SpellNumbers numbers = SpellNumbers.VOID_WELL;
		double radius = numbers.radius(modifiers);
		int duration = numbers.durationTicks(modifiers);
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
			hostile.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, numbers.controlTicks(hostile, duration), numbers.amplifier()), caster);
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
		SpellStatusApi.soak(target, modifiers.durationTicks(TIDAL_SOAK_TICKS, false), caster);
		CastHits.touch(caster);
		return true;
	}

	private static boolean hasValidTarget(final TargetMode mode, final Mob target, final Vec3 point) {
		return switch (mode) {
			case SELF, ALLY -> true;
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
		CastHits.touch(caster);
		return true;
	}

	static boolean applyDragonfall(
		final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers
	) {
		if (point == null) {
			return false;
		}

		double radius = SpellNumbers.DRAGONFALL.radius(modifiers);
		for (Mob hostile : caster.level().getEntitiesOfClass(
			Mob.class,
			new AABB(point, point).inflate(radius),
			candidate -> candidate.isAlive()
				&& Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(point) <= radius * radius
		)) {
			hostile.hurt(spellSource(caster), RpgBridge.damage(caster, SpellNumbers.DRAGONFALL.damage(), modifiers.powerMultiplier()));
		}
		return true;
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
		COOLDOWN,
		/** Nausea spoiled the cast: no effect, the cooldown runs, a scroll is kept. */
		FIZZLE
	}

	/** Whether a cast under Nausea fizzles; a test can set it. */
	@FunctionalInterface
	public interface FizzleRoll {
		boolean fizzles(ServerPlayer caster);
	}

	public static void setFizzleRoll(final FizzleRoll roll) {
		fizzleRoll = roll == null ? FAIR_FIZZLE : roll;
	}
}
