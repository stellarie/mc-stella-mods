package com.stella.learn.gym;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * What a bot did in one episode, counted from the world and from the inputs it applied. The roster test reads it to
 * check a brain's signature action, so it must not depend on what the brain says about itself.
 */
public final class BotStats {
	private int attacks;
	private int fullStrengthAttacks;
	private int fallingAttacks;
	private boolean axeSwung;
	private int axeTicks;
	private int blockingRun;
	private int maxBlockingRun;
	private int strafeRun;
	private int maxStrafeRun;
	private int strafeSign;
	private BlockPos cell;
	private int cellRun;
	private int maxCellRun;
	private BlockPos longestCell;
	private int bowReleases;
	private int retreatingReleases;
	private int highReleases;
	private double maxReleaseHeight;
	private int bowArrows;
	private int crossbowArrows;
	private int pearls;
	private int potions;
	private final List<BlockPos> placed = new ArrayList<>();
	private final List<BlockPos> broken = new ArrayList<>();
	private final List<BlockPos> brokenFrom = new ArrayList<>();

	void attack(final float strength, final boolean falling, final boolean axe) {
		attacks++;
		if (strength >= 1.0F) {
			fullStrengthAttacks++;
		}

		if (falling) {
			fallingAttacks++;
		}

		axeSwung |= axe;
	}

	void tick(final BlockPos at, final boolean blocking, final float strafe, final boolean axeHeld) {
		blockingRun = blocking ? blockingRun + 1 : 0;
		maxBlockingRun = Math.max(maxBlockingRun, blockingRun);
		int sign = strafe > 0.5F ? 1 : strafe < -0.5F ? -1 : 0;
		strafeRun = sign != 0 && sign == strafeSign ? strafeRun + 1 : sign != 0 ? 1 : 0;
		strafeSign = sign;
		maxStrafeRun = Math.max(maxStrafeRun, strafeRun);
		cellRun = at.equals(cell) ? cellRun + 1 : 1;
		cell = at.immutable();
		if (cellRun > maxCellRun) {
			maxCellRun = cellRun;
			longestCell = cell;
		}

		if (axeHeld) {
			axeTicks++;
		}
	}

	void bowShot(final double heightAbove, final boolean movingAway) {
		bowReleases++;
		if (movingAway) {
			retreatingReleases++;
		}

		if (heightAbove >= 3.0) {
			highReleases++;
		}

		maxReleaseHeight = Math.max(maxReleaseHeight, heightAbove);
	}

	void arrow(final boolean crossbow) {
		if (crossbow) {
			crossbowArrows++;
		} else {
			bowArrows++;
		}
	}

	void pearl() {
		pearls++;
	}

	void drank() {
		potions++;
	}

	void placed(final BlockPos at) {
		placed.add(at.immutable());
	}

	void broke(final BlockPos at, final BlockPos from) {
		broken.add(at.immutable());
		brokenFrom.add(from.immutable());
	}

	public int attacks() {
		return attacks;
	}

	public int fullStrengthAttacks() {
		return fullStrengthAttacks;
	}

	public int fallingAttacks() {
		return fallingAttacks;
	}

	public boolean axeSwung() {
		return axeSwung;
	}

	public int axeHeldTicks() {
		return axeTicks;
	}

	public int maxBlockingRun() {
		return maxBlockingRun;
	}

	public int maxStrafeRun() {
		return maxStrafeRun;
	}

	public int maxCellRun() {
		return maxCellRun;
	}

	public BlockPos longestCell() {
		return longestCell;
	}

	public int bowReleases() {
		return bowReleases;
	}

	public int retreatingReleases() {
		return retreatingReleases;
	}

	public int highReleases() {
		return highReleases;
	}

	public double maxReleaseHeight() {
		return maxReleaseHeight;
	}

	public int bowArrows() {
		return bowArrows;
	}

	public int crossbowArrows() {
		return crossbowArrows;
	}

	public int pearls() {
		return pearls;
	}

	public int potions() {
		return potions;
	}

	public List<BlockPos> placed() {
		return placed;
	}

	/** Blocks the bot broke, in order. */
	public List<BlockPos> broken() {
		return broken;
	}

	/** Where the bot stood when each block in {@link #broken()} broke. */
	public List<BlockPos> brokenFrom() {
		return brokenFrom;
	}

	public String summary() {
		return "attacks=" + attacks + " full=" + fullStrengthAttacks + " falling=" + fallingAttacks + " blockRun=" + maxBlockingRun
			+ " strafeRun=" + maxStrafeRun + " cellRun=" + maxCellRun + " bowReleases=" + bowReleases + " arrows=" + bowArrows
			+ " crossbowArrows=" + crossbowArrows + " placed=" + placed.size() + " broken=" + broken.size() + " pearls=" + pearls
			+ " potions=" + potions;
	}
}
