package com.stella.equips.readout;

import java.util.UUID;

/** One player's readout state: the opt-in flag and the payload the client last received. */
public final class ReadoutOwner {
	private ReadoutPayload current;
	private UUID currentMobId;
	private boolean enabled;

	public ReadoutOwner(final boolean enabled) {
		this.enabled = enabled;
	}

	public boolean enabled() {
		return this.enabled;
	}

	public void setEnabled(final boolean value) {
		this.enabled = value;
	}

	/** The payload the client shows now, or null when no readout is up. */
	public ReadoutPayload current() {
		return this.current;
	}

	public UUID currentMobId() {
		return this.currentMobId;
	}

	public void set(final ReadoutPayload payload, final UUID mobId) {
		this.current = payload;
		this.currentMobId = mobId;
	}

	public void clear() {
		this.current = null;
		this.currentMobId = null;
	}
}
