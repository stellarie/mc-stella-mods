package com.stella.equips.client;

import com.stella.equips.readout.ReadoutPayload;

/** The payload the plate draws. The network handler writes it and the render pass reads it. */
final class ReadoutPlateState {
	private static volatile ReadoutPayload current;

	private ReadoutPlateState() {
	}

	static ReadoutPayload current() {
		return current;
	}

	static void set(final ReadoutPayload payload) {
		current = payload;
	}
}
