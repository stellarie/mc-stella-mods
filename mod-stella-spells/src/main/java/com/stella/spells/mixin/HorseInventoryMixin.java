package com.stella.spells.mixin;

import com.stella.spells.AbstractHorseInventory;
import com.stella.spells.SummonSkeleton;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the mount's own pack to this mod, so a servant's horse armor and pack save with the servant's kit. A
 * skeleton horse carries nothing by default, so a summoned one is given the three rows its own tagged kind asks
 * for, and the container is rebuilt at that size before the armor and the pack are put back into it.
 */
@Mixin(AbstractHorse.class)
public abstract class HorseInventoryMixin implements AbstractHorseInventory {
	@Shadow
	protected SimpleContainer inventory;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void stellaSpells$sizeTheServantMountPack(final CallbackInfo callback) {
		if ((Object) this instanceof net.minecraft.world.entity.animal.equine.SkeletonHorse) {
			this.inventory = new SimpleContainer(SummonSkeleton.HORSE_PACK_SIZE);
		}
	}

	@Override
	public SimpleContainer stellaSpells$inventory() {
		return this.inventory;
	}
}
