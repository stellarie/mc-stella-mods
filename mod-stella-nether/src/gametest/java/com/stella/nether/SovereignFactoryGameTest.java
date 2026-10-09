package com.stella.nether;

import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

/** The free-standing Sovereign: built through the object share, outside any raid, with state of its own. */
public class SovereignFactoryGameTest {
	static final int LEVEL = 40;

	@SuppressWarnings("unchecked")
	static BiFunction<ServerLevel, CompoundTag, Mob> factory(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(SovereignFactory.SHARE_KEY);
		helper.assertTrue(shared instanceof BiFunction<?, ?, ?>, "the factory share is missing: " + shared);
		return (BiFunction<ServerLevel, CompoundTag, Mob>) shared;
	}

	static CompoundTag tag(final int level) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("level", level);
		return tag;
	}

	/** Builds a Sovereign through the share and adds it, held still, where a test stands. */
	static Blaze place(final GameTestHelper helper, final Vec3 relative, final int level) {
		Mob mob = factory(helper).apply(helper.getLevel(), tag(level));
		helper.assertTrue(mob instanceof Blaze && BlazeSovereign.isSovereign(mob), "the factory returned " + mob);
		Blaze blaze = (Blaze) mob;
		Vec3 at = helper.absoluteVec(relative);
		blaze.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		blaze.setNoAi(true);
		helper.getLevel().addFreshEntity(blaze);
		return blaze;
	}

	/** A player for a Sovereign to fight: the mobs mod's rules drop any other kind of target. */
	static ServerPlayer foe(final GameTestHelper helper, final Vec3 relative) {
		return NetherTestSupport.join(helper, relative.x, relative.z);
	}

	@GameTest
	public void theShareBuildsASovereignForAValidTag(final GameTestHelper helper) {
		Mob mob = factory(helper).apply(helper.getLevel(), tag(LEVEL));
		helper.assertTrue(mob instanceof Blaze, "the factory returned " + mob);
		Blaze blaze = (Blaze) mob;
		double expected = BlazeSovereign.BASE_HEALTH * SpawnScaling.healthMultiplier(LEVEL);
		double effective = BlazeSovereign.effectiveHealth(blaze);
		boolean unplaced = helper.getLevel().getEntity(blaze.getUUID()) == null;
		String detail = "sovereign=" + BlazeSovereign.isSovereign(blaze) + " level=" + ThreatState.level(blaze)
			+ " effective=" + effective + " expected=" + expected + " unplaced=" + unplaced;
		NetherTestSupport.result("factoryBuilds", detail);
		helper.assertTrue(BlazeSovereign.isSovereign(blaze) && ThreatState.level(blaze) == LEVEL && Math.abs(effective - expected) < 1.0 && unplaced, detail);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void aFactorySovereignKeepsItsOwnBarOutsideTheRaid(final GameTestHelper helper) {
		helper.assertTrue(!helper.getLevel().dimension().equals(RaidTravel.RAID), "the test level is the raid");
		ServerPlayer player = NetherTestSupport.join(helper, 1.0, 1.0);
		Blaze blaze = place(helper, new Vec3(2.0, 1.0, 2.0), LEVEL);
		// Held from its charges, whose rush and volley would fall on the player the bar is shown to.
		SovereignSpecials.pause(blaze);
		helper.succeedWhen(() -> {
			ServerBossEvent bar = SovereignFactory.barOf(blaze);
			boolean shown = bar != null && bar.getPlayers().contains(player);
			helper.assertTrue(shown && bar != BlazeSovereign.bar(), "the player does not see the factory Sovereign's own bar");
			NetherTestSupport.result("factoryBar", "own=" + (bar != null) + " shown=" + shown + " raidBar=" + BlazeSovereign.bar());
			blaze.discard();
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(maxTicks = 300)
	public void aFactorySovereignRunsItsSpecialsOutsideTheRaid(final GameTestHelper helper) {
		helper.assertTrue(!helper.getLevel().dimension().equals(RaidTravel.RAID), "the test level is the raid");
		Blaze blaze = place(helper, new Vec3(2.0, 1.0, 2.0), LEVEL);
		ServerPlayer foe = foe(helper, new Vec3(6.0, 1.0, 6.0));
		blaze.setTarget(foe);
		helper.succeedWhen(() -> {
			List<SovereignSpecials.Special> history = SovereignSpecials.history(blaze);
			helper.assertTrue(!history.isEmpty(), "no special has run yet: target=" + blaze.getTarget() + " alive=" + blaze.isAlive() + " bar=" + (SovereignFactory.barOf(blaze) != null) + " distance=" + (blaze.getTarget() == null ? -1 : blaze.getTarget().distanceTo(blaze)) + " charging=" + SovereignCharge.running(blaze) + " paused=" + SovereignSpecials.paused(blaze));
			NetherTestSupport.result("factorySpecials", "history=" + history);
			blaze.discard();
			NetherTestSupport.leave(helper, foe);
		});
	}

	@GameTest(maxTicks = 300)
	public void twoFactorySovereignsKeepSeparateBarsTargetsAndTimers(final GameTestHelper helper) {
		Blaze first = place(helper, new Vec3(2.0, 1.0, 2.0), LEVEL);
		Blaze second = place(helper, new Vec3(2.0, 1.0, 40.0), LEVEL);
		ServerPlayer firstFoe = foe(helper, new Vec3(6.0, 1.0, 2.0));
		ServerPlayer secondFoe = foe(helper, new Vec3(6.0, 1.0, 40.0));
		first.setTarget(firstFoe);
		second.setTarget(secondFoe);
		// The first one's timers are held and its health is cut; the second must run on, untouched.
		SovereignSpecials.pause(first);
		first.setHealth(first.getMaxHealth() / 4.0F);
		helper.succeedWhen(() -> {
			ServerBossEvent firstBar = SovereignFactory.barOf(first);
			ServerBossEvent secondBar = SovereignFactory.barOf(second);
			helper.assertTrue(firstBar != null && secondBar != null, "a Sovereign has no bar of its own");
			helper.assertTrue(firstBar != secondBar && !firstBar.getId().equals(secondBar.getId()), "the Sovereigns share one bar");
			helper.assertTrue(!SovereignSpecials.history(second).isEmpty(), "the second Sovereign has not cast");
			boolean timersApart = SovereignSpecials.history(first).isEmpty() && SovereignSpecials.lastCast(first) == null;
			boolean targetsApart = first.getTarget() == firstFoe && second.getTarget() == secondFoe;
			boolean barsApart = firstBar.getProgress() < 0.5F && secondBar.getProgress() > 0.9F;
			String detail = "timersApart=" + timersApart + " targetsApart=" + targetsApart + " first=" + firstBar.getProgress() + " second=" + secondBar.getProgress();
			NetherTestSupport.result("factoryTwo", detail);
			first.discard();
			second.discard();
			NetherTestSupport.leave(helper, firstFoe);
			NetherTestSupport.leave(helper, secondFoe);
			helper.assertTrue(timersApart && targetsApart && barsApart, detail);
		});
	}

	@GameTest(maxTicks = 200)
	public void aFactorySovereignChangesNoRaidState(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		MinecraftServer server = helper.getLevel().getServer();
		RaidState before = RaidInstance.state(server);
		Blaze blaze = place(helper, new Vec3(2.0, 1.0, 2.0), LEVEL);
		ServerPlayer foe = foe(helper, new Vec3(6.0, 1.0, 6.0));
		blaze.setTarget(foe);
		helper.runAfterDelay(150, () -> {
			RaidState after = RaidInstance.state(server);
			boolean quiet = after.equals(before) && !after.active() && !after.sovereignRisen()
				&& BlazeSovereign.find(server) == null && BlazeSovereign.bar() == null;
			List<SovereignSpecials.Special> cast = SovereignSpecials.history(blaze);
			String detail = "before=" + before + " after=" + after + " cast=" + cast;
			NetherTestSupport.result("factoryRaidState", detail);
			blaze.discard();
			NetherTestSupport.leave(helper, foe);
			helper.assertTrue(quiet && !cast.isEmpty(), detail);
			helper.succeed();
		});
	}

	@GameTest
	public void aBadTagReturnsNullWithOneLogLine(final GameTestHelper helper) {
		BiFunction<ServerLevel, CompoundTag, Mob> factory = factory(helper);
		CompoundTag text = new CompoundTag();
		text.putString("level", "forty");
		List<CompoundTag> bad = new ArrayList<>();
		bad.add(null);
		bad.add(new CompoundTag());
		bad.add(text);
		bad.add(tag(0));
		bad.add(tag(-5));
		List<String> lines = new ArrayList<>();
		java.util.function.Consumer<String> shipped = SovereignFactory.refusalSink;
		SovereignFactory.refusalSink = lines::add;
		try {
			List<String> outcomes = new ArrayList<>();
			for (CompoundTag candidate : bad) {
				int before = lines.size();
				Mob result = factory.apply(helper.getLevel(), candidate);
				outcomes.add((result == null ? "null" : "mob") + "/" + (lines.size() - before));
			}
			int beforeLevel = lines.size();
			Mob noLevel = factory.apply(null, tag(LEVEL));
			outcomes.add((noLevel == null ? "null" : "mob") + "/" + (lines.size() - beforeLevel));
			String detail = "outcomes=" + outcomes + " lines=" + lines;
			NetherTestSupport.result("factoryBadTag", detail);
			helper.assertTrue(outcomes.stream().allMatch("null/1"::equals), detail);
		} finally {
			SovereignFactory.refusalSink = shipped;
		}
		helper.succeed();
	}
}
