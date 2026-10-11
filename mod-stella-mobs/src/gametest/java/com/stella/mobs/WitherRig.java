package com.stella.mobs;

import com.stella.mobs.behavior.WitherState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
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

	/** Every small wither in the level that names this Wither as its parent, found by the link and not by the record. */
	static List<WitherBoss> smallWithersOf(final ServerLevel level, final UUID parent) {
		List<WitherBoss> found = new ArrayList<>();
		level.getEntities(EntityTypeTest.forClass(WitherBoss.class), other -> {
			WitherState state = WitherState.find(other);
			return state != null && state.parent().filter(parent::equals).isPresent();
		}).forEach(found::add);
		return found;
	}

	/** The small withers in the parent's own record that still stand in the level. */
	static List<WitherBoss> recordedSmallWithers(final ServerLevel level, final WitherBoss parent) {
		List<WitherBoss> found = new ArrayList<>();
		for (UUID id : WitherState.find(parent).spawnlings()) {
			if (level.getEntity(id) instanceof WitherBoss small) {
				found.add(small);
			}
		}
		return found;
	}
}
