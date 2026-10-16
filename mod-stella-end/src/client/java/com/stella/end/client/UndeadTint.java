package com.stella.end.client;

/** A render state that knows which grey, if any, the Sovereign and its undead blazes are drawn in. */
public interface UndeadTint {
	/** An undead blaze: the grey of ash. */
	int BLAZE = 0xFF8C8C8C;
	/** The Sovereign: a darker, colder grey. */
	int SOVEREIGN = 0xFF5C5C70;
	/** No tint: the render state draws as it always did. */
	int NONE = 0;

	int stellaEnd$tint();

	void stellaEnd$setTint(int tint);
}
