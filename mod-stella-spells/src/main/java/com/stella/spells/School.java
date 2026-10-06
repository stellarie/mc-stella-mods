package com.stella.spells;

/** The stat a book's numbers scale with, and the key the RPG mod knows that stat by. */
public enum School {
	MAGE("int"),
	FIGHTER("str"),
	ARCHER("dex"),
	PRIEST("vit");

	private final String statKey;

	School(final String statKey) {
		this.statKey = statKey;
	}

	public String statKey() {
		return this.statKey;
	}
}
