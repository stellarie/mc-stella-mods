package com.stella.ruins.world;

/**
 * Draws a site in its own frame. The game calls it once for every chunk the site crosses, so it must draw the
 * same plan each time: take every random choice from {@link LayoutContext#random()}.
 */
@FunctionalInterface
public interface SiteLayout {
	void draw(LayoutContext ctx);
}
