package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/** The dragon's three attacks, each forced by hand and judged by what it did to the stand-ins on the roof. */
public class DragonAttackGameTest {
	/** The roof cell the player stands on, and its height, as the dragon sees them. */
	static final double PLAYER_X = 8.5;
	static final double PLAYER_Z = 8.5;
	static final double DECK_TOP = Rooftop.DECK_Y + 1;

	record Arena(ServerLevel end, EnderDragon dragon, DragonState state, ServerPlayer player) {
	}

	static Arena arena(final GameTestHelper helper, final int... crystals) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		for (int spike : crystals) {
			EndTestSupport.crystal(end, spike);
		}
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		ServerPlayer player = EndTestSupport.joinOnRoof(helper, end, PLAYER_X - 0.5, PLAYER_Z - 0.5);
		return new Arena(end, dragon, EndDragons.state(dragon), player);
	}

	static void leave(final GameTestHelper helper, final Arena arena) {
		EndTestSupport.releaseCrystals(arena.end());
		EndTestSupport.fresh(helper);
	}

	static String effect(final Zombie zombie, final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		MobEffectInstance instance = zombie.getEffect(effect);
		return instance == null ? "none" : instance.getAmplifier() + "/" + instance.getDuration();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_swoop", maxTicks = 400)
	public void aSwoopFliesThroughThePlayerAndEveryLivingThingInItsPathTakesThirtyDamageAndSlownessTwo(final GameTestHelper helper) {
		Arena arena = arena(helper);
		Zombie dummy = EndTestSupport.dummy(arena.end(), new Vec3(PLAYER_X + 1.5, DECK_TOP, PLAYER_Z));
		Zombie bystander = EndTestSupport.dummy(arena.end(), new Vec3(-12.5, DECK_TOP, -12.5));
		float before = dummy.getHealth();
		EndDragons.force(arena.dragon(), DragonState.Attack.SWOOP);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(arena.state().swoops().stream().anyMatch(swoop -> swoop.struck().contains(dummy.getUUID())),
				"the swoop has not struck the stand-in: " + arena.state().swoops() + " dragon at " + arena.dragon().position()))
			.thenExecute(() -> {
				DragonState.Swoop swoop = arena.state().swoops().get(0);
				float lost = before - dummy.getHealth();
				String slowed = effect(dummy, MobEffects.SLOWNESS);
				boolean sideStruck = swoop.struck().stream().anyMatch(id -> {
					Entity struck = arena.end().getEntity(id);
					return struck != null && struck.entityTags().contains(EndDragons.SIDE_TAG);
				});
				List<Float> blows = swoop.blows();
				EndTestSupport.result("dragonSwoop", "lost=" + lost + " blows=" + blows + " slowed=" + slowed + " struck=" + swoop.struck().size() + " bystander=" + (bystander.getHealth() == bystander.getMaxHealth()));
				boolean bystanderHurt = bystander.getHealth() < bystander.getMaxHealth() || bystander.hasEffect(MobEffects.SLOWNESS);
				MobEffectInstance slowness = dummy.getEffect(MobEffects.SLOWNESS);
				leave(helper, arena);
				helper.assertTrue(blows.size() >= 1 && blows.stream().allMatch(blow -> blow == DragonRules.SWOOP_DAMAGE), "the swoop handed the game " + blows + ", not blows of " + DragonRules.SWOOP_DAMAGE);
				helper.assertTrue(lost > 0.0F, "the swoop did no damage");
				helper.assertTrue(slowness != null && slowness.getAmplifier() == DragonRules.SWOOP_SLOW_AMPLIFIER && slowness.getDuration() > DragonRules.SWOOP_SLOW_TICKS - 20,
					"the swoop left " + slowed);
				helper.assertTrue(!sideStruck, "the swoop struck the dragon's own side");
				helper.assertTrue(!bystanderHurt, "the swoop hurt something far off its path");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_swoop_floor", maxTicks = 400)
	public void aSwoopNeverCarriesTheDragonBelowTheDeckIntoTheCastle(final GameTestHelper helper) {
		Arena arena = arena(helper);
		EndTestSupport.dummy(arena.end(), new Vec3(PLAYER_X + 1.5, DECK_TOP, PLAYER_Z));
		double[] lowest = {Double.MAX_VALUE};
		helper.onEachTick(() -> lowest[0] = Math.min(lowest[0], arena.dragon().getY()));
		EndDragons.force(arena.dragon(), DragonState.Attack.SWOOP);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!arena.state().swoops().isEmpty(), "the swoop has not begun"))
			.thenIdle(120)
			.thenExecute(() -> {
				EndTestSupport.result("dragonSwoopFloor", "lowest=" + lowest[0] + " deck=" + DECK_TOP);
				double low = lowest[0];
				leave(helper, arena);
				helper.assertTrue(low >= DECK_TOP - 1.0E-6, "the swoop carried the dragon down to " + low + ", under the deck at " + DECK_TOP);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_swoop_buffed", maxTicks = 500)
	public void withTheDamageAndSlownessCrystalsASwoopHitsTwentyPercentHarderAndSlowsToLevelFour(final GameTestHelper helper) {
		Arena arena = arena(helper, DragonRules.DAMAGE - 1, DragonRules.SLOWNESS - 1);
		Zombie dummy = EndTestSupport.dummy(arena.end(), new Vec3(PLAYER_X + 1.5, DECK_TOP, PLAYER_Z));
		float before = dummy.getHealth();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(DragonBuffs.of(arena.dragon()).containsAll(List.of(DragonRules.DAMAGE, DragonRules.SLOWNESS)), "buffs " + DragonBuffs.of(arena.dragon())))
			.thenExecute(() -> EndDragons.force(arena.dragon(), DragonState.Attack.SWOOP))
			.thenWaitUntil(() -> helper.assertTrue(arena.state().swoops().stream().anyMatch(swoop -> swoop.struck().contains(dummy.getUUID())), "the swoop has not struck the stand-in"))
			.thenExecute(() -> {
				float lost = before - dummy.getHealth();
				List<Float> blows = arena.state().swoops().get(0).blows();
				MobEffectInstance slowness = dummy.getEffect(MobEffects.SLOWNESS);
				EndTestSupport.result("dragonSwoopBuffed", "lost=" + lost + " blows=" + blows + " slowness=" + effect(dummy, MobEffects.SLOWNESS));
				leave(helper, arena);
				helper.assertTrue(blows.size() >= 1 && blows.stream().allMatch(blow -> blow == DragonRules.SWOOP_DAMAGE * DragonRules.DAMAGE_BUFF), "the buffed swoop handed the game " + blows);
				helper.assertTrue(slowness != null && slowness.getAmplifier() == DragonRules.SWOOP_SLOW_AMPLIFIER_BUFFED, "the buffed swoop left " + slowness);
			})
			.thenSucceed();
	}

	/** Where a flying thing meets the deck, going on as it flies; null if it flies level or up. */
	static Vec3 landing(final Entity flyer) {
		Vec3 motion = flyer.getDeltaMovement();
		if (motion.y >= -1.0E-3) {
			return null;
		}
		double t = (DECK_TOP - flyer.getY()) / motion.y;
		return flyer.position().add(motion.scale(t));
	}

	static void barrage(final GameTestHelper helper, final String environment, final int expected, final int... crystals) {
		Arena arena = arena(helper, crystals);
		List<String> bad = new ArrayList<>();
		int[] seen = new int[1];
		helper.onEachTick(() -> {
			List<DragonState.Shot> shots = arena.state().shots();
			for (int i = seen[0]; i < shots.size(); i++) {
				Entity shot = arena.end().getEntity(shots.get(i).id());
				Vec3 lands = shot == null ? null : landing(shot);
				boolean onRoof = lands != null && Math.abs(lands.x - 0.5) <= 17.0 && Math.abs(lands.z - 0.5) <= 17.0;
				if (!(shot instanceof DragonFireball) || !onRoof) {
					bad.add("shot " + i + " is " + shot + " landing " + lands);
				}
			}
			seen[0] = shots.size();
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!crystalsMissing(arena, crystals), "the crystals' buffs have not arrived: " + DragonBuffs.of(arena.dragon())))
			.thenExecute(() -> EndDragons.force(arena.dragon(), DragonState.Attack.BARRAGE))
			.thenWaitUntil(() -> helper.assertTrue(arena.state().shots().size() >= expected, "only " + arena.state().shots().size() + " shots have left"))
			.thenIdle(10)
			.thenExecute(() -> {
				List<DragonState.Shot> shots = arena.state().shots();
				List<Long> gaps = new ArrayList<>();
				for (int i = 1; i < shots.size(); i++) {
					gaps.add(shots.get(i).at() - shots.get(i - 1).at());
				}
				boolean evenly = gaps.stream().allMatch(gap -> gap == DragonRules.BARRAGE_GAP);
				EndTestSupport.result(environment, "shots=" + shots.size() + " gaps=" + gaps + " bad=" + bad.size());
				int count = shots.size();
				leave(helper, arena);
				helper.assertTrue(count == expected, "the barrage fired " + count + " shots, not " + expected);
				helper.assertTrue(evenly, "the shots were " + gaps + " ticks apart");
				helper.assertTrue(bad.isEmpty(), "barrage defects: " + bad);
			})
			.thenSucceed();
	}

	static boolean crystalsMissing(final Arena arena, final int... crystals) {
		for (int spike : crystals) {
			if (!DragonBuffs.of(arena.dragon()).contains(spike + 1)) {
				return true;
			}
		}
		return false;
	}

	@GameTest(environment = "mod-stella-end-test:dragon_barrage", maxTicks = 500)
	public void aBreathBarrageFiresTwelveDragonFireballsFourTicksApartAtPointsOnTheRoof(final GameTestHelper helper) {
		barrage(helper, "dragonBarrage", DragonRules.BARRAGE_SHOTS);
	}

	@GameTest(environment = "mod-stella-end-test:dragon_barrage_buffed", maxTicks = 500)
	public void withTheBarrageCrystalTheBreathFiresSixteenShots(final GameTestHelper helper) {
		barrage(helper, "dragonBarrageBuffed", DragonRules.BARRAGE_SHOTS_BUFFED, DragonRules.BARRAGE - 1);
	}

	static void dive(final GameTestHelper helper, final String environment, final boolean fire, final int... crystals) {
		Arena arena = arena(helper, crystals);
		Zombie near = EndTestSupport.mobileDummy(arena.end(), new Vec3(PLAYER_X + 2.0, DECK_TOP, PLAYER_Z));
		Zombie far = EndTestSupport.mobileDummy(arena.end(), new Vec3(-12.5, DECK_TOP, -12.5));
		double[] peakNear = new double[1];
		double[] peakFar = new double[1];
		int[] fireballs = new int[1];
		double[] liftNear = {Double.NaN};
		double[] liftFar = {Double.NaN};
		boolean[] counted = new boolean[1];
		helper.onEachTick(() -> {
			// A hurt dragon flings nothing by its wings.
			// Vanilla's push would throw the stand-ins
			// out of reach before the dive lands.
			// This test does not measure that push.
			arena.dragon().hurtTime = 10;
			peakNear[0] = Math.max(peakNear[0], near.getY() - DECK_TOP);
			peakFar[0] = Math.max(peakFar[0], far.getY() - DECK_TOP);
			if (!counted[0] && !arena.state().dives().isEmpty()) {
				counted[0] = true;
				// At most one tick of gravity and drag
				// has worn the 1.6 down.
				liftNear[0] = near.getDeltaMovement().y;
				liftFar[0] = far.getDeltaMovement().y;
				for (UUID id : arena.state().dives().get(0).charges()) {
					fireballs[0] += arena.end().getEntity(id) instanceof SmallFireball ? 1 : 0;
				}
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!crystalsMissing(arena, crystals), "the crystals' buffs have not arrived"))
			.thenExecute(() -> EndDragons.force(arena.dragon(), DragonState.Attack.DIVE))
			.thenWaitUntil(() -> helper.assertTrue(!arena.state().dives().isEmpty(), "the dragon has not landed: " + arena.dragon().position()))
			.thenIdle(15)
			.thenExecute(() -> {
				DragonState.Dive landed = arena.state().dives().get(0);
				boolean liftedNear = landed.lifted().contains(near.getUUID());
				boolean liftedPlayer = landed.lifted().contains(arena.player().getUUID());
				boolean liftedFar = landed.lifted().contains(far.getUUID());
				int thrown = fireballs[0];
				EndTestSupport.result(environment, "impact=" + landed.impact() + " lifted=" + landed.lifted().size() + " liftNear=" + liftNear[0] + " liftFar=" + liftFar[0] + " peakNear=" + peakNear[0] + " peakFar=" + peakFar[0]
					+ " charges=" + landed.charges().size());
				double impactHeight = landed.impact().y;
				leave(helper, arena);
				helper.assertTrue(impactHeight <= DECK_TOP + 3.0, "the dragon dived to " + impactHeight + ", deck top " + DECK_TOP);
				helper.assertTrue(liftedNear && liftedPlayer, "the dive lifted near " + liftedNear + ", player " + liftedPlayer);
				helper.assertTrue(!liftedFar, "the dive lifted a stand-in beyond " + DragonRules.DIVE_RADIUS + " blocks");
				helper.assertTrue(liftNear[0] > DragonRules.DIVE_LIFT - 0.2 && liftNear[0] <= DragonRules.DIVE_LIFT + 1.0E-6, "the lifted stand-in went up at " + liftNear[0]);
				helper.assertTrue(Math.abs(liftFar[0]) < 0.3, "the far stand-in went up at " + liftFar[0]);
				helper.assertTrue(peakNear[0] > 1.0, "the lifted stand-in rose only " + peakNear[0]);
				helper.assertTrue(peakFar[0] < 0.5, "the far stand-in rose " + peakFar[0]);
				helper.assertTrue(fire ? thrown == DragonRules.DIVE_CHARGES : landed.charges().isEmpty(), "the dive threw " + landed.charges().size() + " fire charges");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:dragon_dive", maxTicks = 500)
	public void aDiveDropsOntoTheRoofAndThrowsEveryoneWithinEightBlocksUpAtOnePointSix(final GameTestHelper helper) {
		dive(helper, "dragonDive", false);
	}

	@GameTest(environment = "mod-stella-end-test:dragon_dive_fire", maxTicks = 500)
	public void withTheDiveCrystalTheDragonAlsoThrowsFireChargesWhereItLands(final GameTestHelper helper) {
		dive(helper, "dragonDiveFire", true, DragonRules.DIVE_FIRE - 1);
	}

	/** How far a dragon thrown to the edge of its leash flies in twenty ticks of its way home. */
	static double homeward(final EnderDragon dragon) {
		dragon.setPos(Rooftop.hoverPoint().x + 44.0, Rooftop.hoverPoint().y, Rooftop.hoverPoint().z);
		return dragon.position().x;
	}

	@GameTest(environment = "mod-stella-end-test:dragon_speed", maxTicks = 300)
	public void theSpeedCrystalMakesTheDragonFlyTwentyPercentFaster(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		double[] flown = new double[2];
		helper.startSequence()
			.thenIdle(10)
			.thenExecute(() -> {
				homeward(dragon);
			})
			.thenIdle(20)
			.thenExecute(() -> {
				flown[0] = Rooftop.hoverPoint().x + 44.0 - dragon.getX();
				EndTestSupport.crystal(end, DragonRules.SPEED - 1);
			})
			.thenWaitUntil(() -> helper.assertTrue(DragonBuffs.of(dragon).contains(DragonRules.SPEED), "the speed buff has not arrived"))
			.thenExecute(() -> {
				dragon.setPos(Rooftop.hoverPoint());
			})
			.thenIdle(30)
			.thenExecute(() -> {
				homeward(dragon);
			})
			.thenIdle(20)
			.thenExecute(() -> {
				flown[1] = Rooftop.hoverPoint().x + 44.0 - dragon.getX();
				EndTestSupport.result("dragonSpeed", "plain=" + flown[0] + " quick=" + flown[1] + " ratio=" + flown[1] / flown[0]);
				EndTestSupport.releaseCrystals(end);
				EndTestSupport.fresh(helper);
				helper.assertTrue(flown[0] > 10.0, "the plain dragon flew only " + flown[0] + " blocks");
				helper.assertTrue(Math.abs(flown[1] / flown[0] - DragonRules.SPEED_BUFF) < 0.06, "the buffed dragon flew " + flown[1] / flown[0] + " times as far");
			})
			.thenSucceed();
	}
}
