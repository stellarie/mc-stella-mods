package com.stella.end;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/** The dragon over the rooftop: held there from its first tick, passive until a player stands on the roof, leashed to the castle. */
public class DragonPassiveGameTest {
	static EnderDragon raise(final GameTestHelper helper, final ServerLevel end) {
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		return dragon;
	}

	@GameTest(environment = "mod-stella-end-test:dragon_held", maxTicks = 200)
	public void aDragonMadeTheVanillaWayIsOverTheRooftopTheMomentItLoadsAndNeverEntersTheCastle(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = raise(helper, end);
		// Vanilla puts it at (0, 128, 0) in floor 5.
		// Before one tick passes it must be elsewhere.
		Vec3 first = dragon.position();
		boolean hovering = dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.HOVERING;
		LevelCheck level = new LevelCheck(LevelBridge.read(dragon), dragon.entityTags().contains(EndDragons.TAG));
		double[] lowest = {Double.MAX_VALUE};
		double[] farthest = new double[1];
		helper.onEachTick(() -> {
			lowest[0] = Math.min(lowest[0], dragon.getY());
			farthest[0] = Math.max(farthest[0], Math.hypot(dragon.getX(), dragon.getZ()));
		});
		helper.startSequence()
			.thenIdle(100)
			.thenExecute(() -> {
				EndTestSupport.result("dragonHeld", "first=" + first + " lowest=" + lowest[0] + " farthest=" + farthest[0] + " hovering=" + hovering + " level=" + level.level());
				EndTestSupport.fresh(helper);
				helper.assertTrue(first.y >= Rooftop.DECK_Y + Rooftop.HOVER_HEIGHT - 1.0, "the dragon loaded at " + first);
				helper.assertTrue(lowest[0] >= Rooftop.DECK_Y + Rooftop.HOVER_HEIGHT - 1.0, "the dragon dipped to " + lowest[0] + ", deck at " + Rooftop.DECK_Y);
				helper.assertTrue(farthest[0] <= DragonRules.LEASH, "the dragon strayed " + farthest[0] + " blocks");
				helper.assertTrue(hovering, "the dragon is not in the hover phase");
				helper.assertTrue(level.level() == EndLevels.DRAGON, "the dragon reads level " + level.level());
				helper.assertTrue(level.tagged(), "the dragon carries no mark");
			})
			.thenSucceed();
	}

	record LevelCheck(int level, boolean tagged) {
	}

	@GameTest(environment = "mod-stella-end-test:dragon_passive", maxTicks = 400)
	public void aDragonStaysPassiveWhileNoPlayerStandsOnTheRoofEvenWithOneInTheCastleBelow(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EndTestSupport.joinOnFloor(helper, end, 5);
		EnderDragon dragon = raise(helper, end);
		DragonState state = EndDragons.state(dragon);
		helper.assertTrue(state != null, "the dragon has no state");
		double[] strayed = new double[1];
		helper.onEachTick(() -> strayed[0] = Math.max(strayed[0], dragon.position().distanceTo(Rooftop.hoverPoint())));
		helper.startSequence()
			.thenIdle(DragonRules.ATTACK_PERIOD * 2 + 50)
			.thenExecute(() -> {
				EndTestSupport.result("dragonPassive", "mode=" + state.mode() + " attacks=" + state.attacks().size() + " strayed=" + strayed[0]);
				DragonState.Mode mode = state.mode();
				int attacks = state.attacks().size();
				EndTestSupport.fresh(helper);
				helper.assertTrue(mode == DragonState.Mode.PASSIVE, "the dragon is " + mode);
				helper.assertTrue(attacks == 0, "the dragon attacked " + attacks + " times with nobody on the roof");
				helper.assertTrue(strayed[0] < 2.0, "the passive dragon left its hover point by " + strayed[0]);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_wake", maxTicks = 500)
	public void aPassiveDragonAttacksOnceAPlayerStandsOnTheRoof(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = raise(helper, end);
		DragonState state = EndDragons.state(dragon);
		long[] arrivedAt = new long[1];
		helper.startSequence()
			.thenIdle(30)
			.thenExecute(() -> {
				helper.assertTrue(state.attacks().isEmpty(), "the dragon attacked before anyone came");
				EndTestSupport.joinOnRoof(helper, end, 8.0, 8.0);
				arrivedAt[0] = EndClock.now(end);
			})
			.thenWaitUntil(() -> helper.assertTrue(!state.attacks().isEmpty(), "the dragon is " + state.mode() + " and has not attacked"))
			.thenExecute(() -> {
				DragonState.AttackRecord first = state.attacks().get(0);
				EndTestSupport.result("dragonWake", "mode=" + state.mode() + " first=" + first.attack() + " after=" + (first.at() - arrivedAt[0]));
				DragonState.Mode mode = state.mode();
				EndTestSupport.fresh(helper);
				helper.assertTrue(mode == DragonState.Mode.AWAKE, "the dragon is " + mode);
				helper.assertTrue(first.at() >= arrivedAt[0], "the attack came before the player");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_calm", maxTicks = 200)
	public void anAwakeDragonCalmsOnceTheRoofIsEmptyAgain(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = raise(helper, end);
		DragonState state = EndDragons.state(dragon);
		ServerPlayer player = EndTestSupport.joinOnRoof(helper, end, 8.0, 8.0);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(state.mode() == DragonState.Mode.AWAKE, "the dragon is " + state.mode()))
			.thenExecute(() -> {
				end.getServer().getPlayerList().remove(player);
				EndTestSupport.joinOnFloor(helper, end, 5);
				EndClock.advance(end, DragonRules.CALM_TICKS);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				DragonState.Mode mode = state.mode();
				EndTestSupport.result("dragonCalm", "mode=" + mode);
				EndTestSupport.fresh(helper);
				helper.assertTrue(mode == DragonState.Mode.PASSIVE, "the dragon is " + mode + " with the roof empty");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_leash", maxTicks = 60)
	public void aDragonThrownFarFromTheCastleIsBroughtBackWithinFortyEightBlocks(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = raise(helper, end);
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				dragon.setPos(Rooftop.hoverPoint().x + 60.0, Rooftop.DECK_Y + 30.0, 8.0);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				double reach = Math.hypot(dragon.getX(), dragon.getZ());
				EndTestSupport.result("dragonLeash", "reach=" + reach + " leash=" + DragonRules.LEASH);
				EndTestSupport.fresh(helper);
				helper.assertTrue(reach <= DragonRules.LEASH + 0.5, "the dragon stands " + reach + " blocks from the origin");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_coexist", maxTicks = 120)
	public void withTheMobsDragonMixinLiveASwellingCreeperNearTheDragonDoesNotMoveIt(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 5);
		EnderDragon dragon = raise(helper, end);
		Vec3 hover = Rooftop.hoverPoint();
		Creeper creeper = EntityTypes.CREEPER.create(end, EntitySpawnReason.SPAWNER);
		LevelBridge.claim(creeper);
		creeper.setNoAi(true);
		creeper.snapTo(hover.x + 4.0, hover.y - 2.0, hover.z, 0.0F, 0.0F);
		creeper.setTarget(player);
		creeper.setSwellDir(1);
		end.addFreshEntity(creeper);
		double[] strayed = new double[1];
		helper.onEachTick(() -> {
			creeper.setSwellDir(1);
			creeper.setTarget(player);
			strayed[0] = Math.max(strayed[0], dragon.position().distanceTo(hover));
		});
		helper.startSequence()
			.thenIdle(80)
			.thenExecute(() -> {
				EndTestSupport.result("dragonCoexist", "strayed=" + strayed[0] + " alive=" + dragon.isAlive());
				boolean alive = dragon.isAlive();
				EndTestSupport.fresh(helper);
				helper.assertTrue(alive, "the dragon died");
				helper.assertTrue(strayed[0] < 2.0, "the mobs mod's escape moved the dragon " + strayed[0] + " blocks off its hover point");
			})
			.thenSucceed();
	}
}
