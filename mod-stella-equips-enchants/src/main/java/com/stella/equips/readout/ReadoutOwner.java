package com.stella.equips.readout;

import com.stella.equips.power.PowerCurve;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Display;

public final class ReadoutOwner {
	/**
	 * One readout line. The display stands outside the level, so its id is the only handle a client has.
	 */
	public record Readout(
		UUID mobId,
		int mobEntityId,
		Display.TextDisplay display,
		int threat,
		PowerCurve.Band band,
		String text
	) {
	}

	private final Map<UUID, Readout> readouts = new LinkedHashMap<>();
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

	public Map<UUID, Readout> readouts() {
		return Collections.unmodifiableMap(this.readouts);
	}

	public Readout get(final UUID mobId) {
		return this.readouts.get(mobId);
	}

	public Readout put(final Readout readout) {
		return this.readouts.put(readout.mobId(), readout);
	}

	public Readout remove(final UUID mobId) {
		return this.readouts.remove(mobId);
	}

	public int size() {
		return this.readouts.size();
	}

	public void clear() {
		this.readouts.clear();
	}
}
