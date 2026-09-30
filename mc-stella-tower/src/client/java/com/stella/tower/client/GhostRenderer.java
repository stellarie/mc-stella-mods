package com.stella.tower.client;

import com.stella.tower.TowerGhost;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** A ghost is drawn in a blaze's form, pale grey and half see-through. */
public final class GhostRenderer extends MobRenderer<TowerGhost, LivingEntityRenderState, BlazeModel> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/blaze/blaze.png");
	/** Alpha then red, green and blue: two thirds seen through, washed to a cold grey. */
	private static final int TINT = 0x99B8C0CC;

	public GhostRenderer(final EntityRendererProvider.Context context) {
		super(context, new BlazeModel(context.bakeLayer(ModelLayers.BLAZE)), 0.3F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(final LivingEntityRenderState state) {
		return TEXTURE;
	}

	@Override
	protected int getModelTint(final LivingEntityRenderState state) {
		return TINT;
	}

	@Override
	protected RenderType getRenderType(final LivingEntityRenderState state, final boolean bodyVisible, final boolean translucent, final boolean glowing) {
		return RenderTypes.entityTranslucent(this.getTextureLocation(state));
	}
}
