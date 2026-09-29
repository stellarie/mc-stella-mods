package com.stella.villages.mixin;

import com.stella.villages.patrol.VillageWitchSplash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a hostile village-witch splash from applying vanilla effects to allies. */
@Mixin(ThrownSplashPotion.class)
public abstract class VillageWitchSplashMixin {
	@Inject(method = "onHitAsPotion", at = @At("HEAD"), cancellable = true)
	private void mcStellaVillages$filterHostileSplash(final ServerLevel level, final ItemStack stack,
		final HitResult hit, final CallbackInfo ci) {
		ThrownSplashPotion splash = (ThrownSplashPotion) (Object) this;
		if (!splash.entityTags().contains(VillageWitchSplash.OFFENSIVE_TAG)) {
			return;
		}
		VillageWitchSplash.apply(splash, level, stack);
		ci.cancel();
	}
}
