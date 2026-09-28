package com.stella.tower.mixin;

import com.stella.tower.RuneGuards;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Frost gathers on an entity only through here, so Ice armor halves it wherever it comes from. */
@Mixin(Entity.class)
public abstract class RuneGuardFrostMixin {
	@Shadow
	public abstract int getTicksFrozen();

	@ModifyVariable(method = "setTicksFrozen", at = @At("HEAD"), argsOnly = true)
	private int stellaTower$thaw(final int ticks) {
		return (Object) this instanceof LivingEntity living ? RuneGuards.thaw(living, this.getTicksFrozen(), ticks) : ticks;
	}
}
