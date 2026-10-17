package com.stella.end;

import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import net.fabricmc.loader.api.FabricLoader;

/** The optional link to the rpg mod's experience curve, reached only through ObjectShare. */
public final class XpBridge {
	public static final String CURVE_KEY = "mod-stella-rpg:xp-curve";

	private XpBridge() {
	}

	/** The experience a player needs at a level, or empty when the rpg mod is absent. */
	public static OptionalInt needFor(final int level) {
		if (FabricLoader.getInstance().getObjectShare().get(CURVE_KEY) instanceof IntUnaryOperator curve) {
			return OptionalInt.of(curve.applyAsInt(level));
		}
		return OptionalInt.empty();
	}
}
