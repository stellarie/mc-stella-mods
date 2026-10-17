package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;

/** The tweaked wyverns: ten blocks between any two, and a freed one that breathes and swoops now and then. */
public class WyvernTweaksGameTest {
	/** The shortest horizontal gap among a set of wyverns. */
	static double tightest(final List<WyvernGameTest.Escort> pairs) {
		double tightest = Double.MAX_VALUE;
		for (int a = 0; a < pairs.size(); a++) {
			for (int b = a + 1; b < pairs.size(); b++) {
				Phantom one = pairs.get(a).wyvern();
				Phantom other = pairs.get(b).wyvern();
				tightest = Math.min(tightest, Math.hypot(one.getX() - other.getX(), one.getZ() - other.getZ()));
			}
		}
		return tightest;
	}

	/** Waits for the pairs and for the dragon to wake, then holds back its attacks and its perch so only the wyverns act. */
	static void hushDragon(final GameTestHelper helper, final WyvernGameTest.Stand stand) {
		helper.assertTrue(WyvernGameTest.escorts(stand.end(), stand.state()).size() == DragonRules.WYVERNS, "the pairs have not arrived");
		helper.assertTrue(stand.state().mode() == DragonState.Mode.AWAKE, "the dragon is " + stand.state().mode());
		stand.state().nextAttackAt = DragonTweaksGameTest.FAR;
		stand.state().perchAt = DragonTweaksGameTest.FAR;
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_space", maxTicks = 700)
	public void fourWyvernsNeverStandNearerThanTenBlocksEvenWhenSqueezedOntoOnePoint(final GameTestHelper helper) {
		WyvernGameTest.Stand stand = WyvernGameTest.stand(helper);
		double[] tightest = {Double.MAX_VALUE};
		int[] watched = new int[1];
		boolean[] watching = new boolean[1];
		helper.onEachTick(() -> {
			if (watching[0]) {
				List<WyvernGameTest.Escort> pairs = WyvernGameTest.escorts(stand.end(), stand.state());
				if (pairs.size() == DragonRules.WYVERNS) {
					watched[0]++;
					tightest[0] = Math.min(tightest[0], tightest(pairs));
				}
			}
		});
		double[] squeezed = new double[2];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(WyvernGameTest.escorts(stand.end(), stand.state()).size() == DragonRules.WYVERNS, "the pairs have not arrived"))
			.thenExecute(() -> watching[0] = true)
			.thenIdle(300)
			.thenExecute(() -> {
				watching[0] = false;
				// All four onto one spot, as a wing or a crowd might leave them.
				List<WyvernGameTest.Escort> pairs = WyvernGameTest.escorts(stand.end(), stand.state());
				Vec3 spot = pairs.get(0).wyvern().position();
				for (WyvernGameTest.Escort pair : pairs) {
					pair.wyvern().setPos(spot.x + 0.5 * pairs.indexOf(pair), spot.y, spot.z);
				}
				squeezed[0] = tightest(pairs);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				squeezed[1] = tightest(WyvernGameTest.escorts(stand.end(), stand.state()));
				EndTestSupport.result("wyvernSpace", "spacing=" + DragonRules.WYVERN_SPACING + " watched=" + watched[0] + " tightest=" + tightest[0] + " squeezed=" + squeezed[0] + " after=" + squeezed[1]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(DragonRules.WYVERN_SPACING == 10.0, "the spacing constant is " + DragonRules.WYVERN_SPACING);
				helper.assertTrue(watched[0] >= 250, "only " + watched[0] + " ticks were watched");
				helper.assertTrue(tightest[0] >= DragonRules.WYVERN_SPACING - 1.0E-6, "two wyverns came within " + tightest[0] + " blocks");
				helper.assertTrue(squeezed[0] < 2.0, "the squeeze did not squeeze: " + squeezed[0]);
				helper.assertTrue(squeezed[1] >= DragonRules.WYVERN_SPACING - 1.0E-6, "squeezed wyverns stood " + squeezed[1] + " blocks apart three ticks later");
			})
			.thenSucceed();
	}

	/** A cow with plenty of health. A breath cloud heals the undead, so a zombie would show nothing. */
	static Cow cow(final ServerLevel end, final Vec3 at) {
		Cow cow = EntityTypes.COW.create(end, EntitySpawnReason.SPAWNER);
		cow.addTag(EndTestSupport.DUMMY_TAG);
		cow.setNoAi(true);
		cow.setPersistenceRequired();
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0E6);
		cow.setHealth(cow.getMaxHealth());
		cow.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		end.addFreshEntity(cow);
		return cow;
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_breath", maxTicks = 500)
	public void aFreedWyvernBreathesADragonBreathCloudAtAPlayerNowAndThenWhileOneWithARiderNeverDoes(final GameTestHelper helper) {
		WyvernGameTest.Stand stand = WyvernGameTest.stand(helper);
		ServerPlayer player = EndTestSupport.joinOnRoof(helper, stand.end(), 6.0, 6.0);
		Vec3 home = player.position();
		Cow cow = cow(stand.end(), home);
		// The dragon's wings throw a player about, and the cow must stay where the breath is aimed.
		helper.onEachTick(() -> {
			player.setPos(home);
			player.setDeltaMovement(Vec3.ZERO);
		});
		WyvernGameTest.Escort[] freed = new WyvernGameTest.Escort[1];
		long[] freedAt = new long[1];
		long[] due = new long[2];
		float[] lost = new float[1];
		int[] cloudLife = new int[1];
		boolean[] cloudSeen = new boolean[1];
		AreaEffectCloud[] cloud = new AreaEffectCloud[1];
		helper.startSequence()
			.thenWaitUntil(() -> hushDragon(helper, stand))
			.thenExecute(() -> {
				freed[0] = WyvernGameTest.escorts(stand.end(), stand.state()).get(0);
				freed[0].rider().discard();
				freedAt[0] = EndClock.now(stand.end());
			})
			.thenWaitUntil(() -> helper.assertTrue(stand.state().slot(0).freedAt() != EndState.NEVER, "the wyvern is not recorded as freed"))
			.thenExecute(() -> {
				due[0] = stand.state().slot(0).nextBreathAt() - stand.state().slot(0).freedAt();
				due[1] = stand.state().slot(1).freedAt();
				// A wyvern with a rider gets no breath, whatever the clock says.
				EndClock.advance(stand.end(), DragonRules.WYVERN_BREATH_MAX + 5);
			})
			.thenWaitUntil(() -> helper.assertTrue(!stand.state().breaths().isEmpty(), "the freed wyvern has not breathed, next breath at " + stand.state().slot(0).nextBreathAt()
				+ ", clock " + EndClock.now(stand.end())))
			.thenExecute(() -> {
				DragonState.Breath breath = stand.state().breaths().get(0);
				Object found = stand.end().getEntity(breath.cloud());
				cloudSeen[0] = found instanceof AreaEffectCloud;
				if (found instanceof AreaEffectCloud made) {
					cloud[0] = made;
					cloudLife[0] = made.getDuration() + made.getWaitTime();
				}
			})
			.thenIdle(30)
			.thenExecute(() -> {
				DragonState.Breath breath = stand.state().breaths().get(0);
				lost[0] = cow.getMaxHealth() - cow.getHealth();
				double off = cloud[0] == null ? Double.NaN : Math.hypot(cloud[0].getX() - player.getX(), cloud[0].getZ() - player.getZ());
				boolean owned = cloud[0] != null && cloud[0].getOwner() == freed[0].wyvern();
				boolean aimed = breath.target().equals(player.getUUID());
				boolean onlyFreed = stand.state().breaths().stream().allMatch(each -> each.slot() == 0);
				EndTestSupport.result("wyvernBreath", "due=" + due[0] + " slot=" + breath.slot() + " cloud=" + cloudSeen[0] + " life=" + cloudLife[0] + " off=" + off + " owned=" + owned + " cowOff=" + (cloud[0] == null ? Double.NaN : Math.hypot(cloud[0].getX() - cow.getX(), cloud[0].getZ() - cow.getZ())) + " cowLost=" + lost[0]
					+ " breaths=" + stand.state().breaths().size());
				double limit = DragonRules.WYVERN_BREATH_SCATTER * Math.sqrt(2.0) + 1.0E-6;
				EndTestSupport.fresh(helper);
				helper.assertTrue(due[0] >= DragonRules.WYVERN_BREATH_MIN && due[0] <= DragonRules.WYVERN_BREATH_MAX, "the breath was set for " + due[0] + " ticks after the freeing");
				helper.assertTrue(due[1] == EndState.NEVER, "a wyvern with a rider was marked freed");
				helper.assertTrue(onlyFreed && breath.slot() == 0, "a breath came from slot " + breath.slot());
				helper.assertTrue(cloudSeen[0] && owned, "the breath made no cloud of the wyvern's");
				helper.assertTrue(cloudLife[0] == DragonRules.BREATH_TICKS, "the cloud lasts " + cloudLife[0] + " ticks, not " + DragonRules.BREATH_TICKS);
				helper.assertTrue(aimed && off <= limit, "the cloud fell " + off + " blocks from the player");
				helper.assertTrue(lost[0] > 0.0F, "the cloud hurt the cow standing in it by " + lost[0]);
			})
			.thenSucceed();
	}

	// One bite kills the test player, and the vanilla bite comes as well as the swoop's.
	// A swoop at a dead player ends, so a second one may have to follow before one reaches a live player.
	@GameTest(environment = "mod-stella-end-test:wyvern_swoop", maxTicks = 1000)
	public void aFreedWyvernSwoopsAtAPlayerNowAndThenAndReachesThemWhileOthersWithRidersDoNot(final GameTestHelper helper) {
		WyvernGameTest.Stand stand = WyvernGameTest.stand(helper);
		ServerPlayer player = EndTestSupport.joinOnRoof(helper, stand.end(), 6.0, 6.0);
		// A test player takes no damage; this one must show the bite.
		player.removeTag(EndGameTestInit.TEST_PLAYER_TAG);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0E6);
		player.setHealth(player.getMaxHealth());
		Vec3 home = player.position();
		WyvernGameTest.Escort[] freed = new WyvernGameTest.Escort[1];
		long[] due = new long[2];
		double[] closest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			// The dragon's wings and the bites throw a player about and wear them down. A swoop at a player off the roof
			// or dead ends, and this test is not about that.
			player.setPos(home);
			player.setDeltaMovement(Vec3.ZERO);
			player.setHealth(player.getMaxHealth());
			if (freed[0] != null && freed[0].wyvern().isAlive()) {
				closest[0] = Math.min(closest[0], freed[0].wyvern().distanceTo(player));
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> hushDragon(helper, stand))
			.thenExecute(() -> {
				freed[0] = WyvernGameTest.escorts(stand.end(), stand.state()).get(0);
				freed[0].rider().discard();
			})
			.thenWaitUntil(() -> helper.assertTrue(stand.state().slot(0).freedAt() != EndState.NEVER, "the wyvern is not recorded as freed"))
			.thenExecute(() -> {
				due[0] = stand.state().slot(0).nextSwoopAt() - stand.state().slot(0).freedAt();
				due[1] = stand.state().slot(1).nextSwoopAt();
				freed[0].wyvern().setPos(player.getX() + 30.0, player.getY() + 10.0, player.getZ());
				EndClock.advance(stand.end(), DragonRules.WYVERN_SWOOP_MAX + 5);
			})
			.thenWaitUntil(() -> helper.assertTrue(stand.state().wyvernSwoops().stream().anyMatch(swoop -> swoop.hitAt() != EndState.NEVER),
				"no swoop has reached the player: " + stand.state().wyvernSwoops().size() + " swoops, the wyvern " + freed[0].wyvern().distanceTo(player) + " blocks off"))
			.thenExecute(() -> {
				DragonState.WyvernSwoop swoop = stand.state().wyvernSwoops().stream().filter(each -> each.hitAt() != EndState.NEVER).findFirst().orElseThrow();
				boolean onlyFreed = stand.state().wyvernSwoops().stream().allMatch(each -> each.slot() == 0);
				EndTestSupport.result("wyvernSwoop", "due=" + due[0] + " swoops=" + stand.state().wyvernSwoops().size() + " slot=" + swoop.slot() + " startedAt=" + swoop.startedAt()
					+ " hitAt=" + swoop.hitAt() + " dealt=" + swoop.dealt() + " maxHealth=" + player.getMaxHealth() + " closest=" + closest[0] + " ridersFree=" + due[1]);
				boolean aimed = swoop.target().equals(player.getUUID());
				EndTestSupport.fresh(helper);
				helper.assertTrue(due[0] >= DragonRules.WYVERN_SWOOP_MIN && due[0] <= DragonRules.WYVERN_SWOOP_MAX, "the swoop was set for " + due[0] + " ticks after the freeing");
				helper.assertTrue(due[1] == EndState.NEVER, "a wyvern with a rider was given a swoop time");
				helper.assertTrue(onlyFreed, "a wyvern with a rider swooped");
				helper.assertTrue(aimed, "the swoop was not aimed at the player");
				helper.assertTrue(swoop.hitAt() >= swoop.startedAt(), "the swoop hit before it began");
				helper.assertTrue(swoop.dealt() > 0.0F, "the bite took " + swoop.dealt() + " health from the player");
				helper.assertTrue(closest[0] < 4.5, "the wyvern came no nearer than " + closest[0] + " blocks to the player");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_quiet", maxTicks = 500)
	public void wyvernsWithRidersNeitherBreatheNorSwoopWhateverTheClockSays(final GameTestHelper helper) {
		WyvernGameTest.Stand stand = WyvernGameTest.stand(helper);
		EndTestSupport.joinOnRoof(helper, stand.end(), 6.0, 6.0);
		List<String> bad = new ArrayList<>();
		helper.startSequence()
			.thenWaitUntil(() -> hushDragon(helper, stand))
			// A few ticks let the controller set every timer before the clock jumps past them.
			.thenIdle(5)
			.thenExecute(() -> EndClock.advance(stand.end(), DragonRules.WYVERN_SWOOP_MAX + DragonRules.WYVERN_BREATH_MAX))
			.thenIdle(120)
			.thenExecute(() -> {
				if (!stand.state().breaths().isEmpty()) {
					bad.add("breaths " + stand.state().breaths().size());
				}
				if (!stand.state().wyvernSwoops().isEmpty()) {
					bad.add("swoops " + stand.state().wyvernSwoops().size());
				}
				EndTestSupport.result("wyvernQuiet", "bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "wyverns with riders acted: " + bad);
			})
			.thenSucceed();
	}
}
