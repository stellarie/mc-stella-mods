package com.stella.villages.mixin;

import com.stella.villages.patrol.MobPatrolAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public abstract class MobPatrolMixin implements MobPatrolAccess {
	@Unique
	private static final EntityDataAccessor<Boolean> MC_STELLA_VILLAGES$PATROL_MARKER =
		SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BOOLEAN);

	@Accessor("targetSelector")
	protected abstract GoalSelector mcStellaVillages$targetSelector();

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void mcStellaVillages$definePatrolMarker(
		final SynchedEntityData.Builder builder,
		final CallbackInfo callbackInfo
	) {
		builder.define(MC_STELLA_VILLAGES$PATROL_MARKER, false);
	}

	// A patrol member never takes a fellow member as its target.
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$ignoreFellows(final net.minecraft.world.entity.LivingEntity target, final CallbackInfo callbackInfo) {
		if (com.stella.villages.patrol.PatrolService.areFellows((Mob) (Object) this, target)) {
			callbackInfo.cancel();
		}
	}

	// A patrol member belongs to its village: it never despawns.
	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$neverDespawn(final CallbackInfo callbackInfo) {
		Mob self = (Mob) (Object) this;
		// On Peaceful a member leaves as any illager does.
		if (com.stella.villages.patrol.PatrolState.isMarked(self) && self.level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
			callbackInfo.cancel();
		}
	}

	// A golem never takes a patrol member as its target.
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$golemsSparePatrols(final net.minecraft.world.entity.LivingEntity target, final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callbackInfo) {
		if ((Object) this instanceof net.minecraft.world.entity.animal.golem.AbstractGolem && target instanceof Mob member
			&& com.stella.villages.patrol.PatrolState.isMarked(member)) {
			callbackInfo.setReturnValue(false);
		}
	}

	// The natural spawner skips a mob that needs custom persistence, so a member never fills the mob cap.
	@Inject(method = "requiresCustomPersistence", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$outOfTheMobCap(final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callbackInfo) {
		if (com.stella.villages.patrol.PatrolState.isMarked((Mob) (Object) this)) {
			callbackInfo.setReturnValue(true);
		}
	}

	@Override
	public void setPatrolMarker(final boolean marked) {
		((Mob) (Object) this).getEntityData().set(MC_STELLA_VILLAGES$PATROL_MARKER, marked);
	}

	@Override
	public boolean isPatrolMarker() {
		return ((Mob) (Object) this).getEntityData().get(MC_STELLA_VILLAGES$PATROL_MARKER);
	}

	@Override
	public GoalSelector patrolTargetSelector() {
		return mcStellaVillages$targetSelector();
	}
}
