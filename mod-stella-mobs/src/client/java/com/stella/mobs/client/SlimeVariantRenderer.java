package com.stella.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** The vanilla slime model and shape with one texture per color. */
final class SlimeVariantRenderer extends SlimeRenderer {
	private final Identifier texture;

	SlimeVariantRenderer(final EntityRendererProvider.Context context, final Identifier texture) {
		super(context);
		this.texture = texture;
		// Vanilla's outer layer hardcodes the green texture.
		this.layers.removeIf(layer -> layer instanceof SlimeOuterLayer);
		addLayer(new OuterLayer(this, context.getModelSet(), texture));
	}

	@Override
	public Identifier getTextureLocation(final SlimeRenderState state) {
		return this.texture;
	}

	/** {@link SlimeOuterLayer} with the variant texture. */
	private static final class OuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {
		private final SlimeModel model;
		private final Identifier texture;

		OuterLayer(
			final RenderLayerParent<SlimeRenderState, SlimeModel> parent, final EntityModelSet models,
			final Identifier texture
		) {
			super(parent);
			this.model = new SlimeModel(models.bakeLayer(ModelLayers.SLIME_OUTER));
			this.texture = texture;
		}

		@Override
		public void submit(
			final PoseStack pose, final SubmitNodeCollector collector, final int light, final SlimeRenderState state,
			final float yaw, final float pitch
		) {
			boolean outlineOnly = state.appearsGlowing() && state.isInvisible;
			if (state.isInvisible && !outlineOnly) {
				return;
			}

			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
			RenderType type = outlineOnly ? RenderTypes.outline(this.texture) : RenderTypes.entityTranslucent(this.texture);
			collector.order(1).submitModel(this.model, state, pose, type, light, overlay, state.outlineColor);
		}
	}
}
