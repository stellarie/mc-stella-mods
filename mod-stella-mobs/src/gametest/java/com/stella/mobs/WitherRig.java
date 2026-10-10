package com.stella.mobs;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/** Shared set-up for the Wither boss tests. A body it builds is removed by the arena's `finish`. */
final class WitherRig {
	static final double BIG_HEALTH = 10_000_000.0;

	private WitherRig() {
	}

	/** An encounter Wither: a fresh Wither through the load path, so it takes its level and state. */
	static WitherBoss wither(final GameTestHelper helper, final WideArena arena, final double x, final double y, final double z, final int chargeTicks) {
		WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 at = arena.at(x, y, z);
		wither.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		wither.setPersistenceRequired();
		wither.setInvulnerableTicks(chargeTicks);
		helper.getLevel().addFreshEntity(wither);
		arena.track(wither);
		return wither;
	}

	/** A cow that stays put and cannot die, with the health of a body that takes every test hit. */
	static Cow cow(final GameTestHelper helper, final WideArena arena, final double x, final double y, final double z) {
		Cow cow = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 at = arena.at(x, y, z);
		cow.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		cow.setNoAi(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BIG_HEALTH);
		cow.setHealth((float) BIG_HEALTH);
		helper.getLevel().addFreshEntity(cow);
		arena.track(cow);
		return cow;
	}
}
