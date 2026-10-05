package com.stella.equips.unique;

/** The RPG job a unique belongs to. The job is information only: any player can use any unique. */
public enum UniqueJob {
	ARCHER("Archer"),
	FIGHTER("Fighter"),
	MAGE("Mage"),
	PRIEST("Priest");

	private final String title;

	UniqueJob(final String title) {
		this.title = title;
	}

	public String title() {
		return this.title;
	}
}
