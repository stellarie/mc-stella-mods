package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.LayoutContext;

/** Draws the wayshrine: plans it whole, then emits the cells of the current chunk. */
public final class WayshrineLayout {
	private WayshrineLayout() {
	}

	public static void draw(final LayoutContext ctx) {
		plan(ctx).emit(ctx);
	}

	/** The plan this context would draw. Tests read it to see what a seed makes. */
	public static WayshrinePlan plan(final LayoutContext ctx) {
		return new WayshrinePlan(ctx.random(), ctx::random);
	}
}
