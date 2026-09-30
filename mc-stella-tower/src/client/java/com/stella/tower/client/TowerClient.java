package com.stella.tower.client;

import com.stella.tower.TowerGhosts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** The tower's client side: how its ghosts are drawn. */
public final class TowerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(TowerGhosts.GHOST, GhostRenderer::new);
	}
}
