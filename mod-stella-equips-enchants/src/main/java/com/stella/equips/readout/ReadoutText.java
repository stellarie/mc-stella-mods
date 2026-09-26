package com.stella.equips.readout;

import com.stella.equips.power.PowerCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ReadoutText {
	private ReadoutText() {
	}

	/** The line reads `Threat 7 <Elite> Zombie`; an ordinary mob carries no tag. */
	public static Component build(
		final int threat, final ReadoutRank rank, final Component mobName, final PowerCurve.Band band
	) {
		return build(threat, rank, mobName, band, false);
	}

	/** Only the name carries the hostility color; the prefix keeps the band color. */
	public static Component build(
		final int threat, final ReadoutRank rank, final Component mobName, final PowerCurve.Band band, final boolean hostile
	) {
		return Component.empty()
			.append(prefix(threat, rank).withStyle(color(band)))
			.append(name(mobName, hostile));
	}

	/** Every readout also shows health, so the line reads `Threat 12 <Boss> Wither 200/200`. */
	public static Component build(
		final int threat,
		final ReadoutRank rank,
		final Component mobName,
		final PowerCurve.Band band,
		final float health,
		final float maxHealth
	) {
		return build(threat, rank, mobName, band, health, maxHealth, false);
	}

	public static Component build(
		final int threat,
		final ReadoutRank rank,
		final Component mobName,
		final PowerCurve.Band band,
		final float health,
		final float maxHealth,
		final boolean hostile
	) {
		return Component.empty()
			.append(prefix(threat, rank).withStyle(color(band)))
			.append(name(mobName, hostile))
			.append(Component.literal(" " + shown(health) + "/" + shown(maxHealth)).withStyle(color(band)));
	}

	/** The name is flattened so a colored custom name still reads red or green. */
	private static Component name(final Component mobName, final boolean hostile) {
		return Component.literal(mobName.getString()).withStyle(hostile ? ChatFormatting.RED : ChatFormatting.GREEN);
	}

	private static MutableComponent prefix(final int threat, final ReadoutRank rank) {
		String tag = rank.tag().isEmpty() ? "" : rank.tag() + " ";
		return Component.literal("Threat " + threat + " " + tag);
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
