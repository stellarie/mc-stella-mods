package com.stella.tower;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** The look of a tower lamp: a vanilla light block's model. The first value is the old sea lantern look. */
public enum TowerLampSkin implements StringRepresentable {
	SEA_LANTERN,
	GLOWSTONE,
	SHROOMLIGHT,
	OCHRE_FROGLIGHT,
	PEARLESCENT_FROGLIGHT,
	VERDANT_FROGLIGHT;

	private final String name = this.name().toLowerCase(Locale.ROOT);

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
