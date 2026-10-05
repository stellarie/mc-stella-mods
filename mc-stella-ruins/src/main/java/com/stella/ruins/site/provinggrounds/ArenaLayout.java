package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.world.LayoutContext;

/** Draws the proving grounds: plans it whole, then emits the cells of the current chunk. */
public final class ArenaLayout {
	private ArenaLayout() {
	}

	public static void draw(final LayoutContext ctx) {
		plan(ctx).emit(ctx);
	}

	/** The plan this context would draw. Tests read it to see what a seed makes. */
	public static ArenaPlan plan(final LayoutContext ctx) {
		return new ArenaPlan(ctx.random(), ctx::random);
	}
}
