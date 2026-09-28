package com.stella.tower;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

/** The eight elements. Each has one opposite; a hit on the opposite deals half again, on its own half. */
public enum TowerElement implements StringRepresentable {
	FIRE("fire", "Fire", ChatFormatting.RED),
	ICE("ice", "Ice", ChatFormatting.AQUA),
	WATER("water", "Water", ChatFormatting.BLUE),
	LIGHTNING("lightning", "Lightning", ChatFormatting.YELLOW),
	WIND("wind", "Wind", ChatFormatting.GREEN),
	VOID("void", "Void", ChatFormatting.DARK_PURPLE),
	HOLY("holy", "Holy", ChatFormatting.GOLD),
	DARK("dark", "Dark", ChatFormatting.DARK_GRAY);

	public static final Codec<TowerElement> CODEC = StringRepresentable.fromEnum(TowerElement::values);

	private final String id;
	private final String label;
	private final ChatFormatting color;

	TowerElement(final String id, final String label, final ChatFormatting color) {
		this.id = id;
		this.label = label;
		this.color = color;
	}

	public String label() {
		return this.label;
	}

	public ChatFormatting color() {
		return this.color;
	}

	/** Fire and Ice, Water and Lightning, Wind and Void, Holy and Dark. */
	public TowerElement opposite() {
		return switch (this) {
			case FIRE -> ICE;
			case ICE -> FIRE;
			case WATER -> LIGHTNING;
			case LIGHTNING -> WATER;
			case WIND -> VOID;
			case VOID -> WIND;
			case HOLY -> DARK;
			case DARK -> HOLY;
		};
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
