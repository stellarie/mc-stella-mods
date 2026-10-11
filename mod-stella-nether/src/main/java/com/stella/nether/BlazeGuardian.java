package com.stella.nether;

import com.stella.mobs.StellaMobs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Blaze Guardian: a friendly green blaze, 1.4 times a blaze's size, that stands beside the nether side of a
 * portal. It does not move, fight or take harm; using it opens the raid window. No hostile mob spawns near it.
 */
public class BlazeGuardian extends Blaze {
	public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, StellaNether.id("blaze_guardian"));
	/** MISC, so a guardian never counts against a mob cap, and a blaze's box and fire immunity. */
	public static final EntityType<BlazeGuardian> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.<BlazeGuardian>of(BlazeGuardian::new, MobCategory.MISC).sized(0.6F, 1.8F).fireImmune().clientTrackingRange(8).build(KEY));
	public static final double SCALE = 1.4;
	/** The colour of its outline and of the dust about it: a friendly green. */
	public static final int GREEN = 0x55FF55;
	/** How often, in ticks, the green dust about it is sent, and how much. */
	public static final int AURA_TICKS = 4;
	public static final int AURA_COUNT = 6;
	private static final DustParticleOptions AURA = new DustParticleOptions(GREEN, 1.2F);

	public BlazeGuardian(final EntityType<? extends Blaze> type, final Level level) {
		super(type, level);
		this.refreshDimensions();
	}

	public static AttributeSupplier.Builder createGuardianAttributes() {
		return Blaze.createAttributes().add(Attributes.SCALE, SCALE);
	}

	/** A guardian ready to stand: held in place, kept for good, named, and outside the mobs mod's rolls. Not yet added. */
	public static BlazeGuardian create(final ServerLevel level) {
		BlazeGuardian guardian = TYPE.create(level, EntitySpawnReason.EVENT);
		guardian.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		guardian.addTag(StellaMobs.SELF_SCALED_TAG);
		guardian.setNoAi(true);
		guardian.setPersistenceRequired();
		guardian.setCustomName(Component.translatable("entity.mod-stella-nether.blaze_guardian").withStyle(ChatFormatting.GREEN));
		guardian.setCustomNameVisible(true);
		return guardian;
	}

	/** No goals: it neither hunts nor wanders. */
	@Override
	protected void registerGoals() {
	}

	/** Every damage but the kill command and the void is turned away, a creative player's blow too. */
	@Override
	public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
		boolean removes = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD);
		return !removes || super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean ignoreExplosion(final Explosion explosion) {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** It never moves: not by its own weight, not by a push, a blast or a piston. */
	@Override
	public void move(final MoverType type, final Vec3 movement) {
		this.setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void push(final double x, final double y, final double z) {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(final double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean isPreventingPlayerRest(final ServerLevel level, final Player player) {
		return false;
	}

	/** Always outlined, in green, so a player sees it through the nether's rock and knows it is friendly. */
	@Override
	public boolean isCurrentlyGlowing() {
		return true;
	}

	@Override
	public int getTeamColor() {
		return GREEN;
	}

	@Override
	protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (this.level() instanceof ServerLevel && player instanceof ServerPlayer user) {
			GuardianHub.open(user, this);
		}
		return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.tickCount % AURA_TICKS == 0) {
			level.sendParticles(AURA, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), AURA_COUNT,
				this.getBbWidth() * 0.7, this.getBbHeight() * 0.4, this.getBbWidth() * 0.7, 0.0);
		}
	}

	@Override
	public void die(final DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			GuardianHub.forget(level, this.getUUID());
		}
		super.die(source);
	}

	@Override
	public void remove(final Entity.RemovalReason reason) {
		if (reason.shouldDestroy() && this.level() instanceof ServerLevel level) {
			GuardianHub.forget(level, this.getUUID());
		}
		super.remove(reason);
	}
}
