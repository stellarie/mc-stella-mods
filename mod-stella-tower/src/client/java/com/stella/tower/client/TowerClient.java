package com.stella.tower.client;

import com.stella.tower.GhostSquads;
import com.stella.tower.TowerGhosts;
import com.stella.tower.TowerMerchants;
import com.stella.tower.TowerPhantoms;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** The tower's client side: how its ghosts and its merchant are drawn. */
public final class TowerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(TowerGhosts.GHOST, GhostRenderer::new);
		EntityRendererRegistry.register(GhostSquads.VINDICATOR, GhostVindicatorRenderer::new);
		EntityRendererRegistry.register(GhostSquads.PILLAGER, GhostPillagerRenderer::new);
		EntityRendererRegistry.register(GhostSquads.EVOKER, GhostEvokerRenderer::new);
		EntityRendererRegistry.register(GhostSquads.WITCH, GhostWitchRenderer::new);
		EntityRendererRegistry.register(GhostSquads.VEX, GhostVexRenderer::new);
		EntityRendererRegistry.register(TowerMerchants.MERCHANT, GhostMerchantRenderer::new);
		EntityRendererRegistry.register(TowerPhantoms.PHANTOM, net.minecraft.client.renderer.entity.PhantomRenderer::new);
	}
}
