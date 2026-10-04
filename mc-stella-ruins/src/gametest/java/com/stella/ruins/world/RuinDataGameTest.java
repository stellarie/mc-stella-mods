package com.stella.ruins.world;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Item 14: what a core knows survives a save and a load. */
public class RuinDataGameTest {
	static final BlockPos ACTIVE_CORE = new BlockPos(11, 64, -22);
	static final BlockPos DONE_CORE = new BlockPos(-300, 12, 4096);

	static RuinData sample(final UUID a, final UUID b, final UUID c) {
		RuinData data = new RuinData();
		RuinData.Entry active = data.entryFor(ACTIVE_CORE, "test_surface");
		active.setPhase(RuinPhase.ACTIVE);
		active.addMob(a);
		active.addMob(b);
		active.addMob(c);
		CompoundTag objective = new CompoundTag();
		objective.putInt("wave", 2);
		active.setObjective(objective);
		RuinData.Entry done = data.entryFor(DONE_CORE, "test_reset");
		done.setPhase(RuinPhase.COMPLETE);
		done.setClaimTime(987_654_321_012L);
		return data;
	}

	static RuinData roundTrip(final RuinData data) {
		Tag saved = RuinData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		return RuinData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
	}

	@GameTest
	public void phaseRosterAndClaimTimeSurviveSaveAndLoad(final GameTestHelper helper) {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		RuinData loaded = roundTrip(sample(a, b, c));
		RuinData.Entry active = loaded.entry(ACTIVE_CORE);
		RuinData.Entry done = loaded.entry(DONE_CORE);
		WorldTestSupport.result("ruinDataRoundTrip", "cores=" + loaded.size() + " phase=" + (active == null ? "none" : active.phase()) + " roster=" + (active == null ? 0 : active.roster().size())
			+ " claimTime=" + (done == null ? -1 : done.claimTime()));
		helper.assertTrue(loaded.size() == 2, "the load holds " + loaded.size() + " cores, not 2");
		helper.assertTrue(active != null && active.site().equals("test_surface") && active.phase() == RuinPhase.ACTIVE, "the active core lost its site or phase");
		helper.assertTrue(active.roster().equals(List.of(a, b, c)), "the roster changed: " + active.roster());
		helper.assertTrue(active.objective().getIntOr("wave", -1) == 2, "the objective state was lost");
		helper.assertTrue(done != null && done.phase() == RuinPhase.COMPLETE, "the completed core lost its phase");
		helper.assertTrue(done.claimTime() == 987_654_321_012L, "the claim time changed: " + done.claimTime());
		helper.succeed();
	}

	@GameTest
	public void aLoadedRosterStillMapsEachMobToItsCore(final GameTestHelper helper) {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		RuinData loaded = roundTrip(sample(a, b, c));
		helper.assertTrue(ACTIVE_CORE.equals(loaded.coreOf(b)), "a loaded mob lost its core");
		RuinData.Entry owner = loaded.removeMob(b);
		helper.assertTrue(owner != null && owner.roster().equals(List.of(a, c)), "removing a mob left the roster " + (owner == null ? "null" : owner.roster()));
		helper.assertTrue(loaded.coreOf(b) == null, "a removed mob still maps to a core");
		helper.assertTrue(loaded.removeMob(UUID.randomUUID()) == null, "an unknown mob matched a core");
		helper.succeed();
	}

	@GameTest
	public void aRecordLeftByAnotherSiteIsReplacedNotReused(final GameTestHelper helper) {
		UUID a = UUID.randomUUID();
		RuinData data = sample(a, UUID.randomUUID(), UUID.randomUUID());
		RuinData.Entry fresh = data.entryFor(ACTIVE_CORE, "test_reset");
		helper.assertTrue(fresh.phase() == RuinPhase.DORMANT && fresh.roster().isEmpty(), "a record of another site was reused");
		helper.assertTrue(data.coreOf(a) == null, "the old roster still maps to the spot");
		helper.succeed();
	}
}
