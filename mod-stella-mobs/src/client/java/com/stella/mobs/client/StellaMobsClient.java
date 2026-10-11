package com.stella.mobs.client;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.entity.StellaEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.Identifier;

public final class StellaMobsClient implements ClientModInitializer {
	private static final String SLIME_TEXTURES = "textures/entity/slime/";

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(StellaEntities.BLUE_SLIME, context -> new SlimeVariantRenderer(context, slimeTexture("blue_slime")));
		EntityRendererRegistry.register(StellaEntities.WHITE_SLIME, context -> new SlimeVariantRenderer(context, slimeTexture("white_slime")));
		EntityRendererRegistry.register(StellaEntities.BLACK_SLIME, context -> new SlimeVariantRenderer(context, slimeTexture("black_slime")));
		EntityRendererRegistry.register(StellaEntities.RED_SLIME, context -> new SlimeVariantRenderer(context, slimeTexture("red_slime")));
		EntityRendererRegistry.register(StellaEntities.SKELETON_WORKER, SkeletonRenderer::new);
		EntityRendererRegistry.register(StellaEntities.GHOST, GhostRenderer::new);
		StatusFlashClient.register();
	}

	private static Identifier slimeTexture(final String name) {
		return Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, SLIME_TEXTURES + name + ".png");
	}
}
