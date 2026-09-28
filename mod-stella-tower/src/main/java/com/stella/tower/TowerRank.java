package com.stella.tower;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

/** A tower mob's standing: an ordinary mob, a floor's elite, or a boss floor's boss. */
public enum TowerRank implements StringRepresentable {
	MOB("mob", "", ChatFormatting.WHITE, 1.0, 1.0),
	ELITE("elite", "Elite", ChatFormatting.LIGHT_PURPLE, 3.0, 1.5),
	BOSS("boss", "Boss", ChatFormatting.DARK_RED, 10.0, 2.0);

	public static final Codec<TowerRank> CODEC = StringRepresentable.fromEnum(TowerRank::values);

	private final String id;
	private final String label;
	private final ChatFormatting color;
	private final double healthFactor;
	private final double damageFactor;

	TowerRank(final String id, final String label, final ChatFormatting color, final double healthFactor, final double damageFactor) {
		this.id = id;
		this.label = label;
		this.color = color;
		this.healthFactor = healthFactor;
		this.damageFactor = damageFactor;
	}

	public String label() {
		return this.label;
	}

	public ChatFormatting color() {
		return this.color;
	}

	/** On top of the floor's own health. */
	public double healthFactor() {
		return this.healthFactor;
	}

	/** On top of the floor's own damage. */
	public double damageFactor() {
		return this.damageFactor;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
