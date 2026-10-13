package com.stella.equips.merchant;

/** What a merchant weapon is. It picks the item class and the effect. */
public enum MerchantKind {
	SWORD,
	AXE,
	BOW,
	CROSSBOW,
	SPEAR,
	STAFF;

	/** What the weapon needs: it works in the hand. */
	public String requirement() {
		return switch (this) {
			case SWORD -> "sword in hand";
			case AXE -> "axe in hand";
			case BOW -> "bow in hand";
			case CROSSBOW -> "crossbow in hand";
			case SPEAR -> "spear in hand";
			case STAFF -> "staff in hand";
		};
	}
}
