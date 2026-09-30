package com.stella.mobs.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ghost: a slow, grey, blaze-shaped spirit that follows one player and slows everything near it.
 * It never attacks. Food from a player purifies it and pays a buff. Killing it costs the killer a debuff.
 */
public final class Ghost extends PathfinderMob {
	public static final double MAX_HEALTH = 5.0;
	/** The ghost takes a tenth of the damage it receives. */
	public static final float DAMAGE_FACTOR = 0.1F;
	public static final double SIGHT_RANGE = 24.0;
	public static final double FLY_SPEED = 0.06;
	/** The ghost hovers this far from the player it follows. */
	public static final double HOVER_DISTANCE = 2.5;
	public static final double AURA_RADIUS = 6.0;
	public static final int AURA_INTERVAL_TICKS = 20;
	public static final int AURA_DURATION_TICKS = 40;
	public static final int BUFF_TICKS = 18000;
	public static final int DEBUFF_TICKS = 1200;
	public static final int PURIFY_PARTICLES = 30;

	public static final List<Holder<MobEffect>> BUFFS = List.of(
		MobEffects.SPEED, MobEffects.HASTE, MobEffects.STRENGTH, MobEffects.REGENERATION,
		MobEffects.RESISTANCE, MobEffects.FIRE_RESISTANCE, MobEffects.NIGHT_VISION, MobEffects.LUCK
	);
	public static final List<Holder<MobEffect>> DEBUFFS = List.of(
		MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS,
		MobEffects.HUNGER, MobEffects.NAUSEA, MobEffects.BLINDNESS
	);

	/** Player UUID to the UUID of the ghost that follows the player. Server side only. */
	private static final Map<UUID, UUID> ATTACHED = new HashMap<>();

	private UUID attachedPlayer;

	public Ghost(final EntityType<? extends PathfinderMob> type, final Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, FLY_SPEED)
			.add(Attributes.FOLLOW_RANGE, SIGHT_RANGE);
	}

	/** Whether the player already has a live ghost. */
	public static boolean hasGhost(final UUID player) {
		return ATTACHED.containsKey(player);
	}

	public static void clearAttachments() {
		ATTACHED.clear();
	}

	public UUID attachedPlayer() {
		return this.attachedPlayer;
	}

	@Override
	public void tick() {
		this.noPhysics = true;
		setNoGravity(true);
		super.tick();
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);

		if (this.attachedPlayer == null) {
			attachToNearest(level);
		}

		Player target = this.attachedPlayer == null ? null : level.getPlayerByUUID(this.attachedPlayer);
		if (target == null || !target.isAlive()) {
			// The player left or died: release the slot so a new ghost can attach.
			release();
			setDeltaMovement(getDeltaMovement().scale(0.8));
		} else {
			flyToward(target);
		}

		if (this.tickCount % AURA_INTERVAL_TICKS == 0) {
			pulseAura(level);
		}
	}

	private void attachToNearest(final ServerLevel level) {
		Player best = null;
		double bestDistance = SIGHT_RANGE * SIGHT_RANGE;
		for (Player player : level.players()) {
			double distance = player.distanceToSqr(this);
			if (!player.isAlive() || player.isSpectator() || distance > bestDistance
				|| ATTACHED.containsKey(player.getUUID()) || !hasLineOfSight(player)) {
				continue;
			}
			best = player;
			bestDistance = distance;
		}

		if (best != null) {
			this.attachedPlayer = best.getUUID();
			ATTACHED.put(best.getUUID(), getUUID());
		}
	}

	private void flyToward(final Player target) {
		Vec3 goal = target.position().add(0.0, target.getEyeHeight(), 0.0);
		Vec3 offset = goal.subtract(position());
		double distance = offset.length();
		if (distance <= HOVER_DISTANCE) {
			setDeltaMovement(getDeltaMovement().scale(0.7));
		} else {
			setDeltaMovement(offset.scale(FLY_SPEED / distance));
		}
		lookAt(target, 30.0F, 30.0F);
	}

	private void pulseAura(final ServerLevel level) {
		AABB box = getBoundingBox().inflate(AURA_RADIUS);
		double limit = AURA_RADIUS * AURA_RADIUS;
		// A box query is not trusted to return players, so the player list is walked as well.
		Set<LivingEntity> targets = new HashSet<>(
			level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != this && entity.isAlive())
		);
		for (Player player : level.players()) {
			if (player.isAlive() && !player.isSpectator()) {
				targets.add(player);
			}
		}
		for (LivingEntity entity : targets) {
			if (entity != this && entity.distanceToSqr(this) <= limit) {
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AURA_DURATION_TICKS, 0, true, false), this);
			}
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		// The void and /kill still work: they bypass invulnerability.
		float scaled = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount : amount * DAMAGE_FACTOR;
		return super.hurtServer(level, source, scaled);
	}

	@Override
	protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.has(DataComponents.FOOD)) {
			return super.mobInteract(player, hand);
		}
		if (!(level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}

		stack.consume(1, player);
		level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.5, getZ(), PURIFY_PARTICLES, 0.3, 0.5, 0.3, 0.02);
		giveBuff(player, this.random);
		discard();
		return InteractionResult.SUCCESS;
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (source.getEntity() instanceof ServerPlayer killer) {
			killer.addEffect(new MobEffectInstance(pick(DEBUFFS, this.random), DEBUFF_TICKS, 0));
		}
	}

	/** Adds one random buff. A buff the player already has is left as it is, never extended. */
	public static boolean giveBuff(final Player player, final RandomSource random) {
		Holder<MobEffect> effect = pick(BUFFS, random);
		if (player.hasEffect(effect)) {
			return false;
		}
		return player.addEffect(new MobEffectInstance(effect, BUFF_TICKS, 0));
	}

	private static Holder<MobEffect> pick(final List<Holder<MobEffect>> pool, final RandomSource random) {
		return pool.get(random.nextInt(pool.size()));
	}

	@Override
	public void remove(final Entity.RemovalReason reason) {
		release();
		super.remove(reason);
	}

	private void release() {
		if (this.attachedPlayer != null) {
			ATTACHED.remove(this.attachedPlayer, getUUID());
			this.attachedPlayer = null;
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
