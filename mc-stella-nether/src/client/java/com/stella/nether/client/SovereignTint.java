package com.stella.nether.client;

/** A render state that knows whether it draws the Blaze Sovereign. */
public interface SovereignTint {
	/** The Sovereign's colour over a blaze's own: a cold blue flame. */
	int TINT = 0xFF5AA0FF;

	boolean stellaNether$sovereign();

	void stellaNether$setSovereign(boolean sovereign);
}
