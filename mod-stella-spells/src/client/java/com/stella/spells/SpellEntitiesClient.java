package com.stella.spells;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.WolfRenderer;

/** Draws the falcon and the summoned wolf: the falcon in its own renderer, the wolf in the vanilla wolf renderer. */
public final class SpellEntitiesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(SpellEntities.FALCON, FalconRenderer::new);
		EntityRendererRegistry.register(SpellEntities.SUMMONED_WOLF, WolfRenderer::new);
	}
}
