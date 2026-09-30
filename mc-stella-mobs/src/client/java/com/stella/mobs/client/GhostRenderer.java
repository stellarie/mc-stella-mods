package com.stella.mobs.client;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.entity.Ghost;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** The vanilla blaze model in a grey texture, drawn translucent. The ghost is not a blaze. */
final class GhostRenderer extends MobRenderer<Ghost, LivingEntityRenderState, BlazeModel> {
	private static final Identifier TEXTURE =
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "textures/entity/ghost/ghost.png");
	private static final float SHADOW_RADIUS = 0.3F;
	/** ARGB: alpha 0x99 over white, so the model shows through at about 60 percent. */
	private static final int TINT = 0x99FFFFFF;

	GhostRenderer(final EntityRendererProvider.Context context) {
		super(context, new BlazeModel(context.bakeLayer(ModelLayers.BLAZE)), SHADOW_RADIUS);
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
	protected RenderType getRenderType(
		final LivingEntityRenderState state, final boolean visible, final boolean translucent, final boolean glowing
	) {
		return RenderTypes.entityTranslucent(getTextureLocation(state));
	}
}
