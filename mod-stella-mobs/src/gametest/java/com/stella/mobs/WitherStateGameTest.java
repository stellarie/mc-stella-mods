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
		WitherState state = new WitherState(true, 71.5, 25.2, true, false, WitherState.RAGE_BARRAGE, 431, List.of(UUID.randomUUID(), UUID.randomUUID()));
		Tag encoded = WitherState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
		WitherState decoded = WitherState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		WitherState empty = WitherState.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherStateCodec RESULT decoded={} empty={}", decoded, empty);
		helper.assertTrue(decoded.equals(state), "the state changed in a round trip: " + decoded);
		helper.assertTrue(empty.equals(WitherState.fresh()), "an empty save did not give the defaults: " + empty);
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
