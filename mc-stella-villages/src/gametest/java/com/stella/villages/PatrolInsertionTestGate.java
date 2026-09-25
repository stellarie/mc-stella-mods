package com.stella.villages;

import com.stella.villages.patrol.PatrolState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class PatrolInsertionTestGate {
	private static boolean rejectNextPatrolInsertion;
	private static int rejectedPatrolInsertions;

	private PatrolInsertionTestGate() {
	}

	public static void rejectNextPatrolInsertion() {
		rejectNextPatrolInsertion = true;
		rejectedPatrolInsertions = 0;
	}

	public static int rejectedPatrolInsertions() {
		return rejectedPatrolInsertions;
	}

	public static void clear() {
		rejectNextPatrolInsertion = false;
	}

	public static boolean shouldReject(final Entity entity) {
		if (!rejectNextPatrolInsertion || !(entity instanceof Mob mob) || !PatrolState.isMarked(mob)) {
			return false;
		}

		rejectNextPatrolInsertion = false;
		rejectedPatrolInsertions++;
		return true;
	}
}
