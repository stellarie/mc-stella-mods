package com.stella.end.client.mixin;

import com.stella.end.client.UndeadTint;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the End's grey from the entity to its render. */
@Mixin(LivingEntityRenderState.class)
public abstract class UndeadRenderStateMixin implements UndeadTint {
	@Unique
	private int stellaEnd$tint;

	@Override
	public int stellaEnd$tint() {
		return this.stellaEnd$tint;
	}

	@Override
	public void stellaEnd$setTint(final int tint) {
		this.stellaEnd$tint = tint;
	}
}
