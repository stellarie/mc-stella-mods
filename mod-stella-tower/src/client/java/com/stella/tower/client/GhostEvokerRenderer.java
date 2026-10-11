package com.stella.tower.client;

import com.stella.tower.GhostEvoker;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

/** A ghost evoker drawn as an evoker, seen through. */
public final class GhostEvokerRenderer extends EvokerRenderer<GhostEvoker> {
	public GhostEvokerRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getModelTint(final EvokerRenderState state) {
		return GhostLook.TINT;
	}

	@Override
	protected RenderType getRenderType(final EvokerRenderState state, final boolean bodyVisible, final boolean translucent, final boolean glowing) {
		return GhostLook.translucent(this.getTextureLocation(state));
	}
}
