package com.stella.tower.client;

import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

/** A ghost drawn as the vanilla kind it is, seen through. */
public final class GhostVexRenderer extends VexRenderer {
	public GhostVexRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getModelTint(final VexRenderState state) {
		return GhostLook.TINT;
	}

	@Override
	protected RenderType getRenderType(final VexRenderState state, final boolean bodyVisible, final boolean translucent, final boolean glowing) {
		return GhostLook.translucent(this.getTextureLocation(state));
	}
}
