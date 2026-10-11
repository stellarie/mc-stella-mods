package com.stella.nether.client;

/** A render state that knows which tint, if any, the nether raid's blazes draw it in. */
public interface SovereignTint {
	/** The Sovereign's colour over a blaze's own: a cold blue flame. */
	int TINT = 0xFF5AA0FF;
	/** The Blaze Guardian's colour: a friendly green flame. */
	int GUARDIAN_TINT = 0xFF6BFF6B;
	/** No tint: the render state draws as it always did. */
	int NONE = 0;

	int stellaNether$tint();

	void stellaNether$setTint(int tint);
}
