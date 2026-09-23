package com.stella.equips.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Caps the hearts the vanilla bar draws, so a levelled player keeps a readable bar and
 * his armour strip stays with it. The real total travels as text on the server channel.
 */
@Mixin(Hud.class)
public class HudMixin {
	/** The bar packs ten hearts to a row, so 100 containers is ten rows of ten. */
	private static final int MAX_DRAWN_CONTAINERS = 100;
	private static final float MAX_DRAWN_HEALTH = MAX_DRAWN_CONTAINERS * 2.0F;

	/**
	 * Caps the value the row count, the row height, the regeneration offset, the armour
	 * strip and the heart loop all derive from, so the whole bar shrinks together. The
	 * caller takes the greater of the attribute and the health on screen, and this caps
	 * whichever of the two won.
	 */
	@ModifyExpressionValue(
		method = "extractPlayerHealth",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F")
	)
	private static float stellaEquipsCapLayout(final float maxHealth) {
		return Math.min(maxHealth, MAX_DRAWN_HEALTH);
	}

	/**
	 * The containers the heart loop walks, capped to the same limit so the two cannot
	 * disagree. The value is already capped above, so this holds only if that cap is gone.
	 */
	@ModifyExpressionValue(
		method = "extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;ceil(D)I", ordinal = 0)
	)
	private static int stellaEquipsCapHearts(final int containers) {
		return Math.min(containers, MAX_DRAWN_CONTAINERS);
	}
}
