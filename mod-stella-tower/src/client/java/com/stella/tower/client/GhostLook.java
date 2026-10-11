package com.stella.tower.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** How the tower's ghosts are drawn: their own model and skin, seen through, and washed to a cold blue. */
final class GhostLook {
	/** Alpha then red, green and blue: about three fifths seen through, washed toward soul-fire blue. */
	static final int TINT = 0x99A8D4F0;
	/** The ghost merchant is paler and more present, for it is the one ghost that is not to be fought. */
	static final int MERCHANT_TINT = 0xB8E8F4FF;

	private GhostLook() {
	}

	static RenderType translucent(final Identifier texture) {
		return RenderTypes.entityTranslucent(texture);
	}
}
