package com.stella.tower.client;

import com.stella.tower.TowerGhosts;
import com.stella.tower.TowerPhantoms;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** The tower's client side: how its ghosts are drawn. */
public final class TowerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(TowerGhosts.GHOST, GhostRenderer::new);
		EntityRendererRegistry.register(TowerPhantoms.PHANTOM, net.minecraft.client.renderer.entity.PhantomRenderer::new);
	}
}
