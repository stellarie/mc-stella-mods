package com.stella.end;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Items;

/** The four wyverns: phantoms of three times the size, each ridden by a netherite skeleton, that fight only once the rider is dead. */
public class WyvernGameTest {
	static final double FAR_OFF = 40.0;
	static final long HUNT_WITHIN = 25;

	record Escort(Phantom wyvern, Skeleton rider) {
	}

	/** The pairs the dragon's places hold, read from the record and not from a box query. */
	static List<Escort> escorts(final ServerLevel end, final DragonState state) {
		List<Escort> found = new ArrayList<>();
		for (int i = 0; i < DragonRules.WYVERNS; i++) {
			DragonState.Slot slot = state.slot(i);
			Entity wyvern = slot.wyvern() == null ? null : end.getEntity(slot.wyvern());
			Entity rider = slot.rider() == null ? null : end.getEntity(slot.rider());
			if (wyvern instanceof Phantom phantom && phantom.isAlive() && rider instanceof Skeleton skeleton) {
				found.add(new Escort(phantom, skeleton));
			}
		}
		return found;
	}

	record Stand(ServerLevel end, EnderDragon dragon, DragonState state) {
	}

	static Stand stand(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		return new Stand(end, dragon, EndDragons.state(dragon));
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_four", maxTicks = 200)
	public void fourWyvernsStandEachAPhantomOfScaleThreeRiddenByANetheriteSkeletonOfLevelOneThirty(final GameTestHelper helper) {
		Stand stand = stand(helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(escorts(stand.end(), stand.state()).size() == DragonRules.WYVERNS,
				"only " + escorts(stand.end(), stand.state()).size() + " wyvern pairs stand"))
			.thenExecute(() -> {
				List<Escort> pairs = escorts(stand.end(), stand.state());
				Set<UUID> distinct = new HashSet<>();
				List<String> bad = new ArrayList<>();
				for (Escort pair : pairs) {
					distinct.add(pair.wyvern().getUUID());
					if (pair.wyvern().getAttributeValue(Attributes.SCALE) != DragonRules.WYVERN_SCALE) {
						bad.add("scale " + pair.wyvern().getAttributeValue(Attributes.SCALE));
					}
					if (!pair.rider().isPassenger() || pair.rider().getVehicle() != pair.wyvern()) {
						bad.add("rider " + pair.rider().getUUID() + " is not on its wyvern");
					}
					if (LevelBridge.read(pair.wyvern()) != EndLevels.WYVERN || LevelBridge.read(pair.rider()) != EndLevels.WYVERN) {
						bad.add("levels " + LevelBridge.read(pair.wyvern()) + "/" + LevelBridge.read(pair.rider()));
					}
					boolean armoured = pair.rider().getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
						&& pair.rider().getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
						&& pair.rider().getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
						&& pair.rider().getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS)
						&& pair.rider().getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW);
					if (!armoured) {
						bad.add("rider kit " + pair.rider().getItemBySlot(EquipmentSlot.MAINHAND));
					}
					boolean drops = false;
					for (EquipmentSlot slot : EquipmentSlot.values()) {
						drops |= pair.rider().getDropChances().byEquipment(slot) > 0.0F;
					}
					if (drops) {
						bad.add("the rider's kit can drop");
					}
					if (!pair.wyvern().entityTags().contains(EndDragons.SIDE_TAG) || !pair.rider().entityTags().contains(EndDragons.SIDE_TAG)) {
						bad.add("a pair without the side mark");
					}
				}
				EndTestSupport.result("wyvernFour", "pairs=" + pairs.size() + " distinct=" + distinct.size() + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(pairs.size() == DragonRules.WYVERNS && distinct.size() == DragonRules.WYVERNS, pairs.size() + " pairs, " + distinct.size() + " distinct");
				helper.assertTrue(bad.isEmpty(), "wyvern defects: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_idle", maxTicks = 400)
	public void aWyvernWithALiveRiderNeverKeepsAPlayerAsATargetEvenWhenSomethingSetsOne(final GameTestHelper helper) {
		Stand stand = stand(helper);
		ServerPlayer player = EndTestSupport.joinOnRoof(helper, stand.end(), 6.0, 6.0);
		List<String> bad = new ArrayList<>();
		Set<UUID> armed = new HashSet<>();
		int[] watched = new int[1];
		helper.onEachTick(() -> {
			for (Escort pair : escorts(stand.end(), stand.state())) {
				if (!pair.rider().isAlive()) {
					continue;
				}
				watched[0]++;
				// The game sets the target of a phantom
				// that finds a player.
				// The next tick must clear it.
				if (armed.contains(pair.wyvern().getUUID()) && pair.wyvern().getTarget() != null) {
					bad.add("a wyvern with a live rider kept " + pair.wyvern().getTarget());
				}
				pair.wyvern().setTarget(player);
				armed.add(pair.wyvern().getUUID());
			}
		});
		helper.startSequence()
			.thenIdle(250)
			.thenExecute(() -> {
				EndTestSupport.result("wyvernIdle", "watched=" + watched[0] + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(watched[0] > 500, "only " + watched[0] + " wyvern ticks were watched");
				helper.assertTrue(bad.isEmpty(), "idle defects: " + bad.subList(0, Math.min(3, bad.size())));
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_rider_dead", maxTicks = 500)
	public void onceItsRiderIsDeadAWyvernTargetsPlayersWhileTheOthersStillDoNot(final GameTestHelper helper) {
		Stand stand = stand(helper);
		ServerPlayer player = EndTestSupport.joinOnRoof(helper, stand.end(), 6.0, 6.0);
		Escort[] freed = new Escort[1];
		long[] freedAt = new long[1];
		long[] took = new long[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(escorts(stand.end(), stand.state()).size() == DragonRules.WYVERNS, "the pairs have not arrived"))
			.thenExecute(() -> {
				freed[0] = escorts(stand.end(), stand.state()).get(0);
				// A death alerts neighbours through the mobs mod.
				// So the rider only vanishes.
				// The wyvern then sits beyond the 16 blocks
				// a phantom scans itself.
				// Only the controller can send it at the player.
				freed[0].rider().discard();
				Phantom wyvern = freed[0].wyvern();
				wyvern.setPos(player.getX() + FAR_OFF, wyvern.getY(), player.getZ());
				freedAt[0] = helper.getTick();
			})
			.thenWaitUntil(() -> helper.assertTrue(freed[0].wyvern().isAlive() && freed[0].wyvern().getTarget() == player,
				"the freed wyvern targets " + freed[0].wyvern().getTarget() + " alive=" + freed[0].wyvern().isAlive()))
			.thenExecute(() -> {
				took[0] = helper.getTick() - freedAt[0];
				int stillTargeting = 0;
				for (Escort pair : escorts(stand.end(), stand.state())) {
					stillTargeting += pair.rider().isAlive() && pair.wyvern().getTarget() != null ? 1 : 0;
				}
				EndTestSupport.result("wyvernRiderDead", "freedTargets=" + (freed[0].wyvern().getTarget() == player) + " ridersStillBound=" + stillTargeting + " took=" + took[0]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(took[0] <= HUNT_WITHIN, "the freed wyvern took " + took[0] + " ticks to target the player");
				helper.assertTrue(stillTargeting == 0, stillTargeting + " wyverns with live riders target a player");
			})
			.thenSucceed();
	}

	static void respawn(final GameTestHelper helper, final String environment, final long expected, final int... crystals) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		for (int spike : crystals) {
			EndTestSupport.crystal(end, spike);
		}
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		DragonState state = EndDragons.state(dragon);
		UUID[] killed = new UUID[1];
		long[] killedAt = new long[1];
		long[] delay = new long[1];
		int slot = 1;
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(escorts(end, state).size() == DragonRules.WYVERNS
					&& java.util.Arrays.stream(crystals).allMatch(spike -> DragonBuffs.of(dragon).contains(spike + 1)),
				"the pairs or buffs have not arrived"))
			.thenExecute(() -> {
				Phantom wyvern = (Phantom) end.getEntity(state.slot(slot).wyvern());
				killed[0] = wyvern.getUUID();
				killedAt[0] = EndClock.now(end);
				wyvern.kill(end);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				long at = state.slot(slot).respawnAt();
				delay[0] = at - killedAt[0];
				helper.assertTrue(at != EndState.NEVER, "the dead wyvern's place waits for no respawn");
				helper.assertTrue(escorts(end, state).size() == DragonRules.WYVERNS - 1, "the dead wyvern still counts");
				EndClock.advance(end, at - EndClock.now(end) - 3);
			})
			.thenIdle(2)
			.thenExecute(() -> helper.assertTrue(escorts(end, state).size() == DragonRules.WYVERNS - 1, "the wyvern returned before its time"))
			.thenExecute(() -> EndClock.advance(end, 4))
			.thenWaitUntil(() -> helper.assertTrue(escorts(end, state).size() == DragonRules.WYVERNS, "the wyvern has not returned"))
			.thenExecute(() -> {
				UUID now = state.slot(slot).wyvern();
				EndTestSupport.result(environment, "expected=" + expected + " delay=" + delay[0] + " newBody=" + !killed[0].equals(now));
				EndTestSupport.releaseCrystals(end);
				EndTestSupport.fresh(helper);
				helper.assertTrue(delay[0] >= expected && delay[0] <= expected + 3, "the wyvern waited " + delay[0] + " ticks, not " + expected);
				helper.assertTrue(now != null && !now.equals(killed[0]), "the place holds the dead wyvern's identity");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_respawn", maxTicks = 400)
	public void aKilledWyvernReturnsSixHundredTicksLater(final GameTestHelper helper) {
		respawn(helper, "wyvernRespawn", DragonRules.WYVERN_RESPAWN);
	}

	@GameTest(environment = "mod-stella-end-test:wyvern_respawn_buffed", maxTicks = 400)
	public void withTheWyvernCrystalAKilledWyvernReturnsAfterThreeHundredTicks(final GameTestHelper helper) {
		respawn(helper, "wyvernRespawnBuffed", DragonRules.WYVERN_RESPAWN_BUFFED, DragonRules.WYVERN - 1);
	}
}
