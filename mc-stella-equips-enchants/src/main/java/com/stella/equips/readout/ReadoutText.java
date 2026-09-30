package com.stella.equips.readout;

import com.stella.equips.power.PowerCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

/** The words and colors of the plate. Layout and drawing live in the client. */
public final class ReadoutText {
	private ReadoutText() {
	}

	/** The name line reads `<Elite> Zombie`; an ordinary mob carries no tag. */
	public static String label(final ReadoutRank rank, final String mobName) {
		return rank.tag().isEmpty() ? mobName : rank.tag() + " " + mobName;
	}

	/** Only the name carries the hostility color; the threat ring keeps the band color. */
	public static ChatFormatting nameColor(final boolean hostile) {
		return hostile ? ChatFormatting.RED : ChatFormatting.GREEN;
	}

	/** The 24 bit RGB of a legacy color. */
	public static int rgb(final ChatFormatting color) {
		return TextColor.fromLegacyFormat(color).getValue();
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
}
