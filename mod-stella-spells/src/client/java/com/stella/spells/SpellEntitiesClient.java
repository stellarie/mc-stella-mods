package com.stella.spells;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.WolfRenderer;

/**
 * Draws the falcons and the summoned wolf: each falcon in its own renderer, brown for Blitz Beat and black for the
 * Falcon Guard, and the wolf in the vanilla wolf renderer.
 */
public final class SpellEntitiesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(SpellEntities.FALCON, context -> new FalconRenderer(context, FalconRenderer.BROWN));
		EntityRendererRegistry.register(SpellEntities.GUARD_FALCON, context -> new FalconRenderer(context, FalconRenderer.BLACK));
		EntityRendererRegistry.register(SpellEntities.SUMMONED_WOLF, WolfRenderer::new);
	}
}
