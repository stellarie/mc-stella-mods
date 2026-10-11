package com.stella.nether.client.mixin;

import com.stella.nether.client.SovereignTint;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the nether raid's tint from the entity to its render. */
@Mixin(LivingEntityRenderState.class)
public abstract class SovereignRenderStateMixin implements SovereignTint {
	@Unique
	private int stellaNether$tint;

	@Override
	public int stellaNether$tint() {
		return this.stellaNether$tint;
	}

	@Override
	public void stellaNether$setTint(final int tint) {
		this.stellaNether$tint = tint;
	}
}
