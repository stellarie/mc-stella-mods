package com.stella.mobs.mixin;

import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The encounter Wither: no block breaking after the charge-up, no flight above its ceiling, and arrows always hurt. */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
	@Shadow
	private int destroyBlocksTick;

	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void stellaMobs$keepBlocks(final ServerLevel level, final CallbackInfo callbackInfo) {
		// Vanilla breaks the blocks around the body 20 ticks after each hit. Zero keeps that countdown idle.
		if (WitherEncounter.isActive((WitherBoss) (Object) this)) {
			this.destroyBlocksTick = 0;
		}
	}

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void stellaMobs$tick(final ServerLevel level, final CallbackInfo callbackInfo) {
		WitherEncounter.tick((WitherBoss) (Object) this);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void stellaMobs$ceiling(final CallbackInfo callbackInfo) {
		WitherBoss wither = (WitherBoss) (Object) this;
		if (!wither.level().isClientSide()) {
			WitherEncounter.clampToCeiling(wither);
		}
	}

	@ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 9.0))
	private double stellaMobs$stopDistance(final double vanilla) {
		// Vanilla drags the Wither to within 3 blocks of its target. The encounter Wither stops at its own distance.
		WitherBoss wither = (WitherBoss) (Object) this;
		if (!WitherEncounter.isEncounter(wither)) {
			return vanilla;
		}
		double stop = WitherConfigStore.get().get(WitherConfig.MOVE_STOP_DISTANCE);
		return stop * stop;
	}

	@Shadow
	protected abstract void performRangedAttack(int head, double x, double y, double z, boolean dangerous);

	@Shadow
	protected abstract void performRangedAttack(int head, LivingEntity target);

	@Redirect(
		method = "customServerAiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;performRangedAttack(IDDDZ)V")
	)
	private void stellaMobs$noIdleShot(final WitherBoss wither, final int head, final double x, final double y, final double z, final boolean dangerous) {
		// Vanilla fires the side heads at random points. An encounter Wither fires only on its own volleys.
		if (!WitherEncounter.isEncounter(wither)) {
			this.performRangedAttack(head, x, y, z, dangerous);
		}
	}

	@Redirect(
		method = "customServerAiStep",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V")
	)
	private void stellaMobs$noHeadShot(final WitherBoss wither, final int head, final LivingEntity target) {
		if (!WitherEncounter.isEncounter(wither)) {
			this.performRangedAttack(head, target);
		}
	}

	@Redirect(
		method = "hurtServer",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;isPowered()Z")
	)
	private boolean stellaMobs$arrowsHurt(final WitherBoss wither) {
		// Vanilla ignores arrows at half health. The archer school must be able to fight this one.
		return !WitherEncounter.isEncounter(wither) && wither.isPowered();
	}
}
