package com.stella.nether.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stella.nether.SovereignHits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A Sovereign's hit is struck as two halves, a physical one and a fire one. The wrap runs outside every other change to
 * the amount, so each half is scaled once, and it calls the vanilla hit directly, so no half is split again.
 */
@Mixin(LivingEntity.class)
public abstract class SovereignHitMixin {
	@WrapMethod(method = "hurtServer")
	private boolean stellaNether$splitHit(final ServerLevel level, final DamageSource source, final float damage, final Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!SovereignHits.splits(self, source, damage)) {
			return original.call(level, source, damage);
		}
		return SovereignHits.hurtInHalves(self, level, source, damage, (part, amount) -> original.call(level, part, amount));
	}
}
