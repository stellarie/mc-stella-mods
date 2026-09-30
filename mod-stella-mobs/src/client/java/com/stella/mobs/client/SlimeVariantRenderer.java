package com.stella.mobs.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

/** The vanilla slime model and shape with one texture per color. */
final class SlimeVariantRenderer extends SlimeRenderer {
	private final Identifier texture;

	SlimeVariantRenderer(final EntityRendererProvider.Context context, final Identifier texture) {
		super(context);
		this.texture = texture;
	}

	@Override
	public Identifier getTextureLocation(final SlimeRenderState state) {
		return this.texture;
	}
}
