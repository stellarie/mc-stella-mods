package com.stella.mobs;

import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** The encounter state survives a save, and an old save without a field still reads. */
public final class WitherStateGameTest {
	@GameTest
	public void codecRoundTrips(final GameTestHelper helper) {
		WitherState state = new WitherState(true, 71.5, 25.2, true, false, 37, List.of(UUID.randomUUID(), UUID.randomUUID()), List.of(UUID.randomUUID()), java.util.Optional.of(UUID.randomUUID()));
		Tag encoded = WitherState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
		WitherState decoded = WitherState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		WitherState empty = WitherState.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherStateCodec RESULT decoded={} empty={}", decoded, empty);
		helper.assertTrue(decoded.equals(state), "the state changed in a round trip: " + decoded);
		helper.assertTrue(empty.equals(WitherState.fresh()), "an empty save did not give the defaults: " + empty);
		helper.succeed();
	}

	/** A save from before the rework holds rage fields. They are ignored, and the fields that remain still read. */
	@GameTest
	public void anOldSaveWithRageFieldsStillReads(final GameTestHelper helper) {
		CompoundTag old = new CompoundTag();
		old.putBoolean("activated", true);
		old.putDouble("anchor_y", 64.0);
		old.putBoolean("rage_a_used", true);
		old.putInt("rage_kind", 2);
		old.putInt("rage_ticks_left", 300);
		WitherState decoded = WitherState.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(decoded.activated() && decoded.anchorY() == 64.0 && decoded.rageAUsed() && !decoded.rageBUsed(), "an old save lost a field: " + decoded);
		helper.assertTrue(!decoded.small() && decoded.spawnlings().isEmpty() && decoded.sphereTicks() == 0, "an old save gained a small wither field: " + decoded);
		helper.succeed();
	}

	@GameTest
	public void encounterAndLevelAreSaved(final GameTestHelper helper) {
		WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 12.0, 1.5));
		wither.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(wither);
		int level = ThreatState.level(wither);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		wither.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		WitherBoss loaded = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.LOAD);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherStateSaved RESULT flag={} level={} savedLevel={}",
			WitherState.hasWitherFlag(loaded), ThreatState.level(loaded), level);
		wither.discard();
		helper.assertTrue(WitherState.hasWitherFlag(loaded), "a saved encounter Wither loaded without its state");
		helper.assertTrue(ThreatState.level(loaded) == level, "the level changed across a save: " + ThreatState.level(loaded) + " from " + level);
		helper.succeed();
	}
}
