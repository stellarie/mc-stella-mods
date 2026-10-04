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

	public boolean isRanged() {
		return this == BOW || this == CROSSBOW;
	}
}
