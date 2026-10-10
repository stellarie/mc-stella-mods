package com.stella.spells;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.world.entity.animal.parrot.Parrot;

/** The vanilla parrot model, drawn larger, in the grey parrot texture tinted brown. The falcon is not a parrot. */
final class FalconRenderer extends ParrotRenderer {
	private static final float SIZE = 1.8F;
	/** ARGB brown over the grey parrot texture. */
	private static final int TINT = 0xFF8B5A2B;

	FalconRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void extractRenderState(final Parrot falcon, final ParrotRenderState state, final float partialTick) {
		super.extractRenderState(falcon, state, partialTick);
		state.variant = Parrot.Variant.GRAY;
	}

	@Override
	protected void scale(final ParrotRenderState state, final PoseStack pose) {
		super.scale(state, pose);
		pose.scale(SIZE, SIZE, SIZE);
	}

	@Override
	protected int getModelTint(final ParrotRenderState state) {
		return TINT;
	}
}
