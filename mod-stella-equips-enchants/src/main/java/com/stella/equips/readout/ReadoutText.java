package com.stella.equips.readout;

import com.stella.equips.power.PowerCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ReadoutText {
	private ReadoutText() {
	}

	public static Component build(final int mobLevel, final Component mobName, final PowerCurve.Band band) {
		MutableComponent text = Component.literal("[" + mobLevel + "] ").append(mobName);
		return text.withStyle(color(band));
	}

	/** The mob under the crosshair also shows its health, so the line reads `[32] Cow 14/20`. */
	public static Component build(
		final int mobLevel,
		final Component mobName,
		final PowerCurve.Band band,
		final float health,
		final float maxHealth
	) {
		MutableComponent text = Component.literal("[" + mobLevel + "] ")
			.append(mobName)
			.append(" " + shown(health) + "/" + shown(maxHealth));
		return text.withStyle(color(band));
	}

	/** A live mob never reads as zero, and a whole number never grows a decimal tail. */
	public static int shown(final float health) {
		return (int) Math.ceil(health);
	}

	public static ChatFormatting color(final PowerCurve.Band band) {
		return switch (band) {
			case RED -> ChatFormatting.RED;
			case YELLOW -> ChatFormatting.YELLOW;
			case ORANGE -> ChatFormatting.GOLD;
			case GREEN -> ChatFormatting.GREEN;
			case WHITE -> ChatFormatting.WHITE;
		};
	}

	public static String colorName(final PowerCurve.Band band) {
		return switch (band) {
			case RED -> "red";
			case YELLOW -> "yellow";
			case ORANGE -> "orange";
			case GREEN -> "green";
			case WHITE -> "white";
		};
	}
}
