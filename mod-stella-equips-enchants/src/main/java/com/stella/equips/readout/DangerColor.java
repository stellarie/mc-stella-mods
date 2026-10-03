package com.stella.equips.readout;

import net.minecraft.ChatFormatting;

/** How dangerous a mob is to a viewer, from the level gap alone. */
public enum DangerColor {
	GREEN(ChatFormatting.GREEN),
	WHITE(ChatFormatting.WHITE),
	YELLOW(ChatFormatting.YELLOW),
	RED(ChatFormatting.RED);

	/** The mob is this many levels above the viewer, or more, for yellow. */
	public static final int YELLOW_ABOVE = 5;
	public static final int RED_ABOVE = 10;
	/** The mob is this many levels below the viewer, or more, for green. */
	public static final int GREEN_BELOW = 5;

	private final ChatFormatting format;

	DangerColor(final ChatFormatting format) {
		this.format = format;
	}

	public ChatFormatting format() {
		return this.format;
	}

	public static DangerColor of(final int mobLevel, final int playerLevel) {
		int gap = mobLevel - playerLevel;

		if (gap >= RED_ABOVE) {
			return RED;
		}

		if (gap >= YELLOW_ABOVE) {
			return YELLOW;
		}

		return gap <= -GREEN_BELOW ? GREEN : WHITE;
	}
}
