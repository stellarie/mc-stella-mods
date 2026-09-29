package com.stella.nether.client.mixin;

import com.stella.nether.client.SovereignTint;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the Sovereign's mark from the entity to its render. */
@Mixin(LivingEntityRenderState.class)
public abstract class SovereignRenderStateMixin implements SovereignTint {
	@Unique
	private boolean stellaNether$sovereign;

	@Override
	public boolean stellaNether$sovereign() {
		return this.stellaNether$sovereign;
	}

	@Override
	public void stellaNether$setSovereign(final boolean sovereign) {
		this.stellaNether$sovereign = sovereign;
	}
}
