package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The dragon's death: the rewards, the thirty minute ban on rooftop spawns, and its return after two hours or a vanilla summon. */
public class DragonDeathGameTest {
	/** A killing blow, struck as the player: the dragon's health set low, then one hit at its full health. */
	static void slay(final ServerLevel end, final EnderDragon dragon, final ServerPlayer killer) {
		dragon.setHealth(1.0F);
		dragon.damageCooldownTime = 0;
		dragon.hurt(end, dragon.head, end.damageSources().explosion(null, killer), dragon.getMaxHealth());
	}

	static int count(final ServerLevel end, final List<UUID> drops, final net.minecraft.world.item.Item kind) {
		int total = 0;
		for (UUID id : drops) {
			if (end.getEntity(id) instanceof ItemEntity item && item.getItem().is(kind)) {
				total += item.getItem().getCount();
			}
		}
		return total;
	}

	@GameTest(environment = "mod-stella-end-test:death_rewards", maxTicks = 300)
	public void slayingTheDragonDropsOneEggAndFourScalesAndPaysRooftopPlayersTheExperienceOfALevelOneTwentyKill(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		ServerPlayer first = EndTestSupport.joinOnRoof(helper, end, 8.0, 8.0);
		ServerPlayer second = EndTestSupport.joinOnRoof(helper, end, -8.0, 8.0);
		ServerPlayer below = EndTestSupport.joinOnFloor(helper, end, 5);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		DragonState state = EndDragons.state(dragon);
		int[] before = {first.totalExperience, second.totalExperience, below.totalExperience};
		int curve = XpBridge.needFor(DragonRules.XP_LEVEL).orElse(-1);
		helper.startSequence()
			.thenIdle(30)
			.thenExecute(() -> slay(end, dragon, first))
			.thenWaitUntil(() -> helper.assertTrue(!EndDragons.deaths().isEmpty(), "no death was recorded"))
			.thenExecute(() -> {
				EndDragons.Death death = EndDragons.deaths().get(0);
				int eggs = count(end, death.drops(), Items.DRAGON_EGG);
				int scales = count(end, death.drops(), DraconicGear.item(DraconicGear.DRAGON_SCALE));
				int paidFirst = first.totalExperience - before[0];
				int paidSecond = second.totalExperience - before[1];
				int paidBelow = below.totalExperience - before[2];
				EndState saved = EndCastles.state(end);
				boolean banned = Rooftop.spawnsBlocked(end);
				int side = 0;
				for (Entity entity : EndTestSupport.entities(end)) {
					side += entity.entityTags().contains(EndDragons.SIDE_TAG) && entity.isAlive() ? 1 : 0;
				}
				EndTestSupport.result("dragonDeath", "eggs=" + eggs + " scales=" + scales + " curve=" + curve + " paid=" + paidFirst + "/" + paidSecond + "/" + paidBelow
					+ " banned=" + banned + " deadAt=" + saved.dragonDeadAt() + " sideLeft=" + side);
				EndTestSupport.fresh(helper);
				helper.assertTrue(eggs == 1, "the dragon dropped " + eggs + " dragon eggs");
				helper.assertTrue(scales == DragonRules.SCALES, "the dragon dropped " + scales + " dragon scales");
				helper.assertTrue(curve > 0, "the rpg curve is absent in a run that has the rpg mod");
				helper.assertTrue(paidFirst == curve / 2 && paidSecond == curve / 2, "the roof players were paid " + paidFirst + " and " + paidSecond + ", half of " + curve);
				helper.assertTrue(paidBelow == 0, "a player below the roof was paid " + paidBelow);
				helper.assertTrue(saved.dragonDead() && saved.dragonDeadAt() != EndState.NEVER, "the death was not stored");
				helper.assertTrue(banned, "rooftop spawns are not barred after the death");
				helper.assertTrue(side == 0, side + " of the dragon's side outlived it");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:death_ban", maxTicks = 400)
	public void rooftopSpawnsStayBarredForThirtySixThousandTicksAfterTheDeathAndThenResume(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EndCastles.setState(end, EndCastles.state(end).withDragonDead(EndClock.now(end)));
		long diedAt = EndCastles.state(end).dragonDeadAt();
		// A dragon summoned the vanilla way in the ban
		// stands and brings no wyverns.
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		DragonState state = EndDragons.state(dragon);
		int[] counts = new int[3];
		helper.startSequence()
			.thenIdle(40)
			.thenExecute(() -> {
				counts[0] = WyvernGameTest.escorts(end, state).size();
				helper.assertTrue(Rooftop.spawnsBlocked(end), "the ban does not hold");
				EndClock.advance(end, Rooftop.SPAWN_BAN_TICKS - (EndClock.now(end) - diedAt) - 10);
			})
			.thenIdle(5)
			.thenExecute(() -> {
				counts[1] = WyvernGameTest.escorts(end, state).size();
				helper.assertTrue(Rooftop.spawnsBlocked(end), "the ban lifted early");
				EndClock.advance(end, 20);
			})
			.thenWaitUntil(() -> helper.assertTrue(WyvernGameTest.escorts(end, state).size() == DragonRules.WYVERNS, "the wyverns have not come"))
			.thenExecute(() -> {
				counts[2] = WyvernGameTest.escorts(end, state).size();
				boolean lifted = !Rooftop.spawnsBlocked(end);
				EndTestSupport.result("deathBan", "inBan=" + counts[0] + "/" + counts[1] + " after=" + counts[2] + " lifted=" + lifted);
				EndTestSupport.fresh(helper);
				helper.assertTrue(counts[0] == 0 && counts[1] == 0, "wyverns spawned inside the ban: " + counts[0] + ", " + counts[1]);
				helper.assertTrue(lifted && counts[2] == DragonRules.WYVERNS, "the ban did not lift");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:death_ban_roost", maxTicks = 300)
	public void aRoostInsideTheBanSummonsNothing(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 5);
		EndCastles.setState(end, EndCastles.state(end).withDragonDead(EndClock.now(end)));
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		DragonState state = EndDragons.state(dragon);
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				dragon.setHealth(dragon.getMaxHealth() * 0.51F);
				dragon.damageCooldownTime = 0;
				dragon.hurt(end, dragon.head, end.damageSources().explosion(null, player), dragon.getMaxHealth() * 0.02F);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(state.mode() == DragonState.Mode.ROOST, "the dragon is " + state.mode());
				EndClock.advance(end, DragonRules.ROOST_SUMMON_PERIOD * 3);
			})
			.thenIdle(4)
			.thenExecute(() -> {
				int summons = state.summons().size();
				EndTestSupport.result("deathBanRoost", "summons=" + summons);
				EndTestSupport.fresh(helper);
				helper.assertTrue(summons == 0, "the roost summoned " + summons + " inside the ban");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:revive_timer", maxTicks = 500)
	public void aDeadDragonReturnsAfterOneHundredFortyFourThousandTicksWithNoSummon(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		normalizeRoof(end);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 5);
		EnderDragon first = EndDragons.spawn(end);
		helper.assertTrue(first != null, "no dragon was made");
		UUID[] firstId = {first.getUUID()};
		boolean[] early = new boolean[1];
		helper.startSequence()
			.thenIdle(10)
			.thenExecute(() -> slay(end, first, player))
			.thenWaitUntil(() -> helper.assertTrue(EndCastles.state(end).dragonDead(), "the death was not stored"))
			.thenExecute(() -> {
				long left = EndCastles.state(end).dragonDeadAt() + DragonRules.REVIVE_TICKS - EndClock.now(end);
				EndClock.advance(end, left - 10);
			})
			.thenIdle(4)
			.thenExecute(() -> {
				EnderDragon standing = EndDragons.current(end);
				early[0] = standing != null && !standing.getUUID().equals(firstId[0]);
				EndClock.advance(end, 10);
			})
			.thenWaitUntil(() -> {
				EnderDragon standing = EndDragons.current(end);
				helper.assertTrue(standing != null && !standing.getUUID().equals(firstId[0]), "no new dragon has risen");
			})
			.thenExecute(() -> {
				EnderDragon second = EndDragons.current(end);
				EndTestSupport.result("reviveTimer", "tooEarly=" + early[0] + " risen=" + (second != null) + " level=" + LevelBridge.read(second) + " dead=" + EndCastles.state(end).dragonDead());
				int level = LevelBridge.read(second);
				boolean dead = EndCastles.state(end).dragonDead();
				double height = second.getY();
				EndTestSupport.fresh(helper);
				normalizeRoof(end);
				helper.assertTrue(!early[0], "the dragon came back before " + DragonRules.REVIVE_TICKS + " ticks passed");
				helper.assertTrue(level == EndLevels.DRAGON, "the revived dragon reads level " + level);
				helper.assertTrue(!dead, "the dead flag stayed set");
				helper.assertTrue(height >= Rooftop.DECK_Y + Rooftop.HOVER_HEIGHT - 1.0, "the revived dragon appeared at height " + height);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:revive_summon", maxTicks = 300)
	public void aDragonSummonedTheVanillaWayEndsTheWaitSoTheTimerMakesNoSecondDragon(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EndTestSupport.joinOnFloor(helper, end, 5);
		EndCastles.setState(end, EndCastles.state(end).withDragonDead(EndClock.now(end)));
		EnderDragon summoned = EndDragons.spawn(end);
		helper.assertTrue(summoned != null, "no dragon was made");
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				helper.assertTrue(!EndCastles.state(end).dragonDead(), "the summoned dragon did not end the wait");
				EndClock.advance(end, DragonRules.REVIVE_TICKS + 100);
			})
			.thenIdle(10)
			.thenExecute(() -> {
				int dragons = 0;
				for (Entity entity : EndTestSupport.entities(end)) {
					dragons += entity instanceof EnderDragon dragon && dragon.isAlive() ? 1 : 0;
				}
				EndTestSupport.result("reviveSummon", "dragons=" + dragons);
				EndTestSupport.fresh(helper);
				helper.assertTrue(dragons == 1, dragons + " dragons stand after the timer ran out");
			})
			.thenSucceed();
	}

	/** Puts the rooftop centre back as the castle builds it: a clear deck under an open sky, where vanilla's podium had been. */
	static void restoreRoof(final ServerLevel end) {
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = Rooftop.DECK_Y - 1; y <= Rooftop.DECK_Y + 36; y++) {
					boolean deck = y == Rooftop.DECK_Y;
					end.setBlock(new BlockPos(x, y, z), deck ? Blocks.END_STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** The roof as a world that has not seen a kill leaves it: a clear deck under the fight's shut podium. */
	static void normalizeRoof(final ServerLevel end) {
		restoreRoof(end);
		FightHandle.reset(end);
		FightHandle.podium(end);
	}

	@GameTest(environment = "mod-stella-end-test:dragon_fight_flow", maxTicks = 1500)
	public void withTheVanillaFightRunningItsDragonIsHeldOverTheRoofSlainByAPlayerAndLeavesTheActivePortalAndNoEggBlock(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		restoreRoof(end);
		FightHandle.reset(end);
		EndGameTestInit.dragonFightRuns = true;
		ServerPlayer player = EndTestSupport.joinOnRoof(helper, end, 8.0, 8.0);
		double[] lowest = {Double.MAX_VALUE};
		boolean[] seen = new boolean[1];
		List<String> bad = new ArrayList<>();
		helper.onEachTick(() -> {
			EnderDragon standing = EndDragons.current(end);
			if (standing != null && standing.tickCount < 3 && !seen[0]) {
				seen[0] = true;
				if (standing.getY() < Rooftop.DECK_Y + Rooftop.HOVER_HEIGHT - 1.0) {
					bad.add("the fight's dragon loaded at " + standing.position());
				}
			}
			if (standing != null) {
				lowest[0] = Math.min(lowest[0], standing.getY());
			}
		});
		BlockPos ring = new BlockPos(1, Rooftop.DECK_Y, 0);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(EndDragons.current(end) != null, "the fight has made no dragon yet"))
			.thenIdle(40)
			.thenExecute(() -> {
				EnderDragon dragon = EndDragons.current(end);
				EndTestSupport.result("dragonFightFlow", "level=" + LevelBridge.read(dragon) + " lowest=" + lowest[0] + " bad=" + bad.size());
				helper.assertTrue(LevelBridge.read(dragon) == EndLevels.DRAGON, "the fight's dragon reads level " + LevelBridge.read(dragon));
				helper.assertTrue(lowest[0] >= Rooftop.DECK_Y, "the fight's dragon dipped to " + lowest[0] + ", under the deck at " + Rooftop.DECK_Y);
				slay(end, dragon, player);
			})
			.thenWaitUntil(() -> helper.assertTrue(end.getBlockState(ring).is(Blocks.END_PORTAL), "the exit portal is not active yet: " + end.getBlockState(ring)))
			.thenExecute(() -> {
				int eggBlocks = 0;
				for (int x = -6; x <= 6; x++) {
					for (int z = -6; z <= 6; z++) {
						for (int y = Rooftop.DECK_Y; y <= Rooftop.DECK_Y + 8; y++) {
							eggBlocks += end.getBlockState(new BlockPos(x, y, z)).is(Blocks.DRAGON_EGG) ? 1 : 0;
						}
					}
				}
				EndDragons.Death death = EndDragons.deaths().isEmpty() ? null : EndDragons.deaths().get(0);
				int eggs = death == null ? -1 : count(end, death.drops(), Items.DRAGON_EGG);
				EndTestSupport.result("dragonFightFlowEnd", "eggBlocks=" + eggBlocks + " eggItems=" + eggs);
				EndTestSupport.fresh(helper);
				normalizeRoof(end);
				helper.assertTrue(bad.isEmpty(), "flow defects: " + bad);
				helper.assertTrue(eggBlocks == 0, "vanilla placed " + eggBlocks + " dragon egg blocks");
				helper.assertTrue(eggs == 1, "the dragon dropped " + eggs + " egg items");
			})
			.thenSucceed();
	}
}
