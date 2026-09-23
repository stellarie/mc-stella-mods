package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;

/**
 * B01 and B02 as a named surface, so a test reads the roll's two rules without reaching into the world:
 * eligibility, and whether a chunk already holds a boss.
 */
public final class BossRoll {
	private BossRoll() {
	}

	public static boolean eligible(final Mob mob) {
		return Bosses.canBeBoss(mob);
	}

	public static boolean chunkHasBoss(final Mob mob) {
		return Bosses.chunkHasBoss(mob);
	}

	/** True when two mobs share one chunk, which is what the guard test has to prove before it means anything. */
	public static boolean sameChunk(final Mob first, final Mob second) {
		return first.chunkPosition().equals(second.chunkPosition());
	}
}
