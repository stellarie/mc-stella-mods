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
