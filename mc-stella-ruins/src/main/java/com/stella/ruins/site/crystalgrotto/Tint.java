package com.stella.ruins.site.crystalgrotto;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The six colours of a crystal pillar. A colour keeps one pitch in every grotto, so a player can solve a round by
 * ear. The pitch is two to the power of the semitones over twelve.
 */
public enum Tint implements StringRepresentable {
	BLUE("blue", -5, 0x4D7CF5),
	MAGENTA("magenta", -3, 0xE04BD0),
	CYAN("cyan", 0, 0x35D3E6),
	LIME("lime", 2, 0x98E64A),
	YELLOW("yellow", 4, 0xF4D03A),
	ORANGE("orange", 7, 0xF58A3B);

	private final String id;
	private final int semitones;
	private final int rgb;

	Tint(final String id, final int semitones, final int rgb) {
		this.id = id;
		this.semitones = semitones;
		this.rgb = rgb;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	public int semitones() {
		return this.semitones;
	}

	/** The pitch multiplier of the chime sound. */
	public float pitch() {
		return (float) Math.pow(2.0, this.semitones / 12.0);
	}

	public int rgb() {
		return this.rgb;
	}

	/** The stained glass of this colour, for the floor spokes. */
	public Block glass() {
		return switch (this) {
			case BLUE -> Blocks.STAINED_GLASS.blue();
			case MAGENTA -> Blocks.STAINED_GLASS.magenta();
			case CYAN -> Blocks.STAINED_GLASS.cyan();
			case LIME -> Blocks.STAINED_GLASS.lime();
			case YELLOW -> Blocks.STAINED_GLASS.yellow();
			case ORANGE -> Blocks.STAINED_GLASS.orange();
		};
	}
}
