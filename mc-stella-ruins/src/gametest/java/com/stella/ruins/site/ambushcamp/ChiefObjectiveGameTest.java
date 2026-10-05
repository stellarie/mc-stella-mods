package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinPhase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 3 to 6 and the sketch review additions: the hidden Chief, the tell, the reveal, the call, and the reset. */
public class ChiefObjectiveGameTest {
	static final int TICKS = CampTestSupport.ARENA_TICKS;

	private static boolean at(final Mob mob, final BlockPos marker) {
		return mob.blockPosition().equals(marker);
	}

	private static void killChief(final CampTestSupport.Arena arena, final ServerPlayer player) {
		Mob chief = arena.chief();
		chief.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 1_000_000.0F);
	}

	@GameTest(maxTicks = TICKS)
	public void activationSpawnsTheBanditsTheLookoutsAndAnInvisibleChiefInHisTent(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the camp did not wake: " + arena.phase());
			int tents = arena.tents();
			helper.assertTrue(arena.rosterOf("bandit").size() == CampPlan.BANDITS, "bandits " + arena.rosterOf("bandit").size());
			helper.assertTrue(arena.rosterOf("lookout").size() == CampPlan.LOOKOUTS, "lookouts " + arena.rosterOf("lookout").size());
			helper.assertTrue(arena.rosterOf("bandit_chief").size() == 1, "chiefs " + arena.rosterOf("bandit_chief").size());
			helper.assertTrue(arena.entry().roster().size() == CampPlan.BANDITS + CampPlan.LOOKOUTS + 1, "roster " + arena.entry().roster().size());
			ChiefObjective objective = arena.objective();
			int chiefTent = objective.chiefTent();
			Mob chief = arena.chief();
			helper.assertTrue(chiefTent >= 0 && chiefTent < tents && at(chief, arena.tent(chiefTent + 1)), "the Chief is not in tent " + (chiefTent + 1) + ": " + chief.blockPosition());
			helper.assertTrue(CampMobs.isHidden(chief) && chief.hasEffect(MobEffects.INVISIBILITY) && chief.isNoAi(), "the Chief is not hidden");
			helper.assertTrue(chief.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && chief.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the hidden Chief wears gear that gives him away");
			int decoys = tents - 1;
			for (int tent = 0; tent < tents; tent++) {
				final int id = tent + 1;
				long inTent = arena.rosterOf("bandit").stream().filter(m -> at(m, arena.tent(id))).count();
				helper.assertTrue(inTent == (tent == chiefTent ? 0 : 1), "tent " + id + " holds " + inTent + " bandits");
			}
			List<BlockPos> patrol = arena.markers(CampMarkers.PATROL);
			int onPatrol = 0;
			for (BlockPos spot : patrol) {
				onPatrol += (int) arena.rosterOf("bandit").stream().filter(m -> at(m, spot)).count();
			}
			helper.assertTrue(onPatrol == patrol.size() && patrol.size() == CampPlan.BANDITS - decoys, "patrol " + onPatrol + " of " + patrol.size() + " markers, " + decoys + " decoys");
			for (BlockPos spot : arena.markers(CampMarkers.LOOKOUT)) {
				helper.assertTrue(arena.rosterOf("lookout").stream().filter(m -> at(m, spot)).count() == 1, "no lookout at " + spot);
			}
			CampTestSupport.result("activation", "tents=" + tents + " chiefTent=" + (chiefTent + 1) + " decoys=" + decoys + " patrol=" + onPatrol + " lookouts=2 roster=" + arena.entry().roster().size());
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aPlayerWithinFourBlocksRevealsTheChiefAndAFarPlayerDoesNot(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			Mob chief = arena.chief();
			double far = player.distanceTo(chief);
			helper.assertTrue(far > CampMobs.REVEAL_RADIUS + 1.0, "the test player stands only " + far + " blocks from the Chief");
			CampMobs.CHIEF.ability().tick(chief, arena.level());
			helper.assertTrue(CampMobs.isHidden(chief), "a player " + far + " blocks away revealed the Chief");
			Vec3 chiefAt = chief.position();
			player.teleportTo(arena.level(), chiefAt.x + CampMobs.REVEAL_RADIUS + 0.5, chiefAt.y, chiefAt.z, java.util.Set.of(), 0.0F, 0.0F, true);
			CampMobs.CHIEF.ability().tick(chief, arena.level());
			helper.assertTrue(CampMobs.isHidden(chief), "a player just outside the radius revealed the Chief: " + player.distanceTo(chief));
			player.teleportTo(arena.level(), chiefAt.x + CampMobs.REVEAL_RADIUS - 0.5, chiefAt.y, chiefAt.z, java.util.Set.of(), 0.0F, 0.0F, true);
			CampMobs.CHIEF.ability().tick(chief, arena.level());
			CampTestSupport.result("revealRadius", "far=" + far + " inside=" + player.distanceTo(chief) + " hidden=" + CampMobs.isHidden(chief));
			helper.assertTrue(!CampMobs.isHidden(chief) && !chief.hasEffect(MobEffects.INVISIBILITY) && !chief.isNoAi(), "a player inside the radius did not reveal the Chief");
			helper.assertTrue(chief.getItemBySlot(EquipmentSlot.MAINHAND).is(net.minecraft.world.item.Items.IRON_AXE) && !chief.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the revealed Chief has no hood and axe");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aHitOnTheHiddenChiefRevealsHimAndCallsEveryBanditAndLookout(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			Mob chief = arena.chief();
			int flaresBefore = CampMobs.flaresFired();
			chief.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 1.0F);
			helper.assertTrue(!CampMobs.isHidden(chief) && !chief.hasEffect(MobEffects.INVISIBILITY), "a hit did not reveal the Chief");
			int called = 0;
			for (Mob mob : arena.rosterOf("bandit")) {
				called += mob.getTarget() == player ? 1 : 0;
			}
			for (Mob mob : arena.rosterOf("lookout")) {
				called += mob.getTarget() == player ? 1 : 0;
			}
			int flares = CampMobs.flaresFired() - flaresBefore;
			CampTestSupport.result("reveal", "called=" + called + " flares=" + flares + " chiefTarget=" + (chief.getTarget() == player));
			helper.assertTrue(called == CampPlan.BANDITS + CampPlan.LOOKOUTS, "only " + called + " of " + (CampPlan.BANDITS + CampPlan.LOOKOUTS) + " heard the call");
			helper.assertTrue(flares == CampPlan.LOOKOUTS, "the lookouts fired " + flares + " flares");
			helper.assertTrue(CampMobs.reveal(chief, player) == false, "a revealed Chief revealed twice");
			helper.assertTrue(CampMobs.flaresFired() - flaresBefore == CampPlan.LOOKOUTS, "a second reveal fired more flares");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void twoMoreBanditsArriveAtTheTrailAHundredTicksAfterTheReveal(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			long[] now = {2_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				int before = arena.entry().roster().size();
				now[0] += 500L;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == before, "reinforcements came before any reveal");
				Mob chief = arena.chief();
				chief.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 1.0F);
				helper.assertTrue(arena.objective().revealedAt() == now[0], "the reveal time is " + arena.objective().revealedAt());
				now[0] += ChiefObjective.REINFORCE_DELAY - 1L;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == before && !arena.objective().hasReinforced(), "reinforcements came one tick early");
				now[0] += 1L;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == before + ChiefObjective.REINFORCEMENTS && arena.objective().hasReinforced(), "roster " + arena.entry().roster().size() + " after the delay");
				BlockPos trail = arena.markers(CampMarkers.TRAIL).get(0);
				int arrived = 0;
				for (Mob mob : arena.rosterOf("bandit")) {
					if (mob.blockPosition().distManhattan(trail) <= 3) {
						arrived++;
					}
				}
				helper.assertTrue(arrived == ChiefObjective.REINFORCEMENTS, arrived + " bandits stand at the trail");
				arena.core().pulse(arena.level());
				now[0] += 1000L;
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.entry().roster().size() == before + ChiefObjective.REINFORCEMENTS, "a second wave came: " + arena.entry().roster().size());
				CampTestSupport.result("reinforce", "before=" + before + " after=" + arena.entry().roster().size() + " atTrail=" + arrived);
				arena.leave(player);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void killingTheChiefCompletesTheCoreWhileOtherBanditsLive(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			helper.assertTrue(arena.vault().sealed(), "the vault stood open before the kill");
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the camp is " + arena.phase());
			killChief(arena, player);
			int living = arena.rosterOf("bandit").size() + arena.rosterOf("lookout").size();
			CampTestSupport.result("chiefKill", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed() + " living=" + living);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the core is " + arena.phase() + ", the vault sealed " + arena.vault().sealed());
			helper.assertTrue(living == CampPlan.BANDITS + CampPlan.LOOKOUTS, "the kill took other bandits with him: " + living + " live");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aLitCandleMarksTheChiefsDoorAndOneDecoyDoor(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			ChiefObjective objective = arena.objective();
			int tents = arena.tents();
			helper.assertTrue(objective.tellTent() >= 0 && objective.tellTent() != objective.chiefTent(), "the tell is in tent " + objective.tellTent() + ", the Chief in " + objective.chiefTent());
			List<Integer> lit = new ArrayList<>();
			for (int tent = 0; tent < tents; tent++) {
				var state = arena.level().getBlockState(arena.door(tent + 1));
				if (state.is(Blocks.CANDLE)) {
					helper.assertTrue(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT), "the candle at door " + (tent + 1) + " is not lit");
					lit.add(tent);
				}
			}
			CampTestSupport.result("candles", "lit=" + lit + " chief=" + objective.chiefTent() + " tell=" + objective.tellTent());
			helper.assertTrue(lit.size() == 2 && lit.contains(objective.chiefTent()) && lit.contains(objective.tellTent()), "lit doors " + lit);
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theCandlesGoOutWhenTheCoreCompletes(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			int tents = arena.tents();
			int burning = 0;
			for (int tent = 0; tent < tents; tent++) {
				burning += arena.level().getBlockState(arena.door(tent + 1)).is(Blocks.CANDLE) ? 1 : 0;
			}
			helper.assertTrue(burning == 2, burning + " candles burn before the kill");
			killChief(arena, player);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase());
			for (int tent = 0; tent < tents; tent++) {
				helper.assertTrue(!arena.level().getBlockState(arena.door(tent + 1)).is(Blocks.CANDLE), "a candle still burns at door " + (tent + 1));
			}
			CampTestSupport.result("candlesOut", "burningBefore=" + burning + " burningAfter=0");
			arena.leave(player);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aCompletedCampRearmsAfterTwoDaysWithTheChiefInADifferentTent(final GameTestHelper helper) {
		CampTestSupport.inArena(helper, CampTestSupport.SEED, arena -> {
			long[] now = {1_000_000L};
			RuinClock.Source saved = RuinClock.replace(level -> now[0]);
			try {
				List<Integer> chiefTents = new ArrayList<>();
				Set<Integer> distinct = new HashSet<>();
				for (int cycle = 0; cycle < 12; cycle++) {
					ServerPlayer player = arena.wake();
					arena.freeze();
					helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "cycle " + cycle + " woke wrong: " + arena.phase());
					int tent = arena.objective().chiefTent();
					helper.assertTrue(CampMemory.get(arena.level()).last(arena.corePos()) == tent, "the camp forgot the Chief's tent");
					helper.assertTrue(at(arena.chief(), arena.tent(tent + 1)), "cycle " + cycle + ": the Chief is not in tent " + (tent + 1));
					helper.assertTrue(arena.rosterOf("bandit_chief").size() == 1 && arena.rosterOf("bandit").size() == CampPlan.BANDITS, "cycle " + cycle + " roster is wrong");
					chiefTents.add(tent);
					distinct.add(tent);
					killChief(arena, player);
					helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "cycle " + cycle + " did not complete: " + arena.phase());
					now[0] += 2L * 24_000L - 1L;
					arena.core().pulse(arena.level());
					helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the camp rearmed one tick early");
					now[0] += 1L;
					arena.leave(player);
					arena.clearMobs();
					arena.core().pulse(arena.level());
					helper.assertTrue(arena.phase() == RuinPhase.DORMANT && arena.vault().sealed(), "cycle " + cycle + " after two days: " + arena.phase() + " sealed " + arena.vault().sealed());
				}
				CampTestSupport.result("rearm", "chiefTents=" + chiefTents + " distinct=" + distinct.size() + " tents=" + arena.tents());
				for (int i = 1; i < chiefTents.size(); i++) {
					helper.assertTrue(!chiefTents.get(i).equals(chiefTents.get(i - 1)), "the Chief hid in tent " + (chiefTents.get(i) + 1) + " twice in a row: " + chiefTents);
				}
				helper.assertTrue(distinct.size() >= 3, "twelve resets used only " + distinct.size() + " tents: " + chiefTents);
			} finally {
				RuinClock.replace(saved);
			}
		});
	}

	@GameTest
	public void theObjectiveStateRoundTripsThroughItsTag(final GameTestHelper helper) {
		ChiefObjective source = new ChiefObjective();
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putInt("chief", 3);
		tag.putInt("tell", 1);
		tag.putBoolean("done", true);
		tag.putBoolean("reinforced", true);
		tag.putLong("revealedAt", 4242L);
		source.load(tag);
		ChiefObjective copy = new ChiefObjective();
		copy.load(source.save());
		helper.assertTrue(copy.chiefTent() == 3 && copy.tellTent() == 1 && copy.isDone() && copy.hasReinforced() && copy.revealedAt() == 4242L, "the state did not survive: " + source.save());
		ChiefObjective fresh = new ChiefObjective();
		fresh.load(new net.minecraft.nbt.CompoundTag());
		helper.assertTrue(fresh.chiefTent() == -1 && fresh.tellTent() == -1 && !fresh.isDone() && !fresh.hasReinforced() && fresh.revealedAt() == -1L, "an empty tag is not a fresh objective");
		helper.succeed();
	}
}
