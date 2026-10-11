package com.stella.tower.client;

import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

/** A ghost drawn as the vanilla kind it is, seen through. */
public final class GhostPillagerRenderer extends PillagerRenderer {
	public GhostPillagerRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getModelTint(final IllagerRenderState state) {
		return GhostLook.TINT;
	}

	@Override
	protected RenderType getRenderType(final IllagerRenderState state, final boolean bodyVisible, final boolean translucent, final boolean glowing) {
		return GhostLook.translucent(this.getTextureLocation(state));
	}
}
