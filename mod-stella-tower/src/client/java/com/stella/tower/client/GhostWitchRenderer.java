package com.stella.tower.client;

import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

/** A ghost drawn as the vanilla kind it is, seen through. */
public final class GhostWitchRenderer extends WitchRenderer {
	public GhostWitchRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getModelTint(final WitchRenderState state) {
		return GhostLook.TINT;
	}

	@Override
	protected RenderType getRenderType(final WitchRenderState state, final boolean bodyVisible, final boolean translucent, final boolean glowing) {
		return GhostLook.translucent(this.getTextureLocation(state));
	}
}
