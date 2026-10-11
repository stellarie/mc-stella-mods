package com.stella.tower.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;

/** The ghost merchant drawn as a wandering trader, seen through. */
public final class GhostMerchantRenderer extends WanderingTraderRenderer {
	public GhostMerchantRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getModelTint(final VillagerRenderState state) {
		return GhostLook.MERCHANT_TINT;
	}

	@Override
	protected RenderType getRenderType(final VillagerRenderState state, final boolean bodyVisible, final boolean translucent, final boolean glowing) {
		return GhostLook.translucent(this.getTextureLocation(state));
	}
}
