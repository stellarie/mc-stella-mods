package com.stella.rpg.api;

import com.stella.rpg.XpCurve;
import java.util.function.IntUnaryOperator;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Publishes the player experience curve under {@code mod-stella-rpg:xp-curve} as an
 * {@code IntUnaryOperator} that answers {@code needFor(level)}. A reader needs no class from this mod.
 */
public final class XpCurveShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:xp-curve";

	private XpCurveShare() {
	}

	public static void publish() {
		IntUnaryOperator need = XpCurve::needFor;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, need);
	}
}
