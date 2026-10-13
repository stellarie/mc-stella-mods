package com.stella.equips.unique;

import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

/** The item kind of a unique. The tag name is the last part of the slice item tag of that kind. */
public enum UniqueKind {
	BOW("bows", null),
	CROSSBOW("crossbows", null),
	SWORD("swords", null),
	AXE("axes", null),
	MACE("maces", null),
	SPEAR("spears", null),
	HELMET("head", EquipmentSlot.HEAD),
	CHESTPLATE("chest", EquipmentSlot.CHEST),
	LEGGINGS("legs", EquipmentSlot.LEGS),
	BOOTS("feet", EquipmentSlot.FEET);

	private final String tagName;
	private final @Nullable EquipmentSlot slot;

	UniqueKind(final String tagName, final @Nullable EquipmentSlot slot) {
		this.tagName = tagName;
		this.slot = slot;
	}

	public String tagName() {
		return this.tagName;
	}

	/** The armor slot of an armor kind, or null for a weapon kind. */
	public @Nullable EquipmentSlot slot() {
		return this.slot;
	}

	public boolean isArmor() {
		return this.slot != null;
	}

	/** What a unique of this kind needs: the weapon in hand, or the armor worn in its slot. */
	public String requirement() {
		return switch (this) {
			case BOW -> "bow in hand";
			case CROSSBOW -> "crossbow in hand";
			case SWORD -> "sword in hand";
			case AXE -> "axe in hand";
			case MACE -> "mace in hand";
			case SPEAR -> "spear in hand";
			case HELMET -> "worn on head";
			case CHESTPLATE -> "worn on chest";
			case LEGGINGS -> "worn on legs";
			case BOOTS -> "worn on feet";
		};
	}

	public boolean isRanged() {
		return this == BOW || this == CROSSBOW;
	}
}
