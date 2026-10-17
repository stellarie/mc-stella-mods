package com.stella.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The polished Sovereign: a fixed health, a trigger on the stair, a burst in every direction, and a rain from above. */
public class SovereignPolishGameTest {
	static final Vec3 ANCHOR = SovereignGameTest.ANCHOR;

	/** The block of one lane of step {@code k} of the floor 5 stair, in the world. */
	static BlockPos step(final int k, final int lane) {
		CastleLayout.Flight flight = CastleLayout.FLIGHT_5;
		return new BlockPos(EndCastle.MIN_X + flight.x(k, lane), EndCastle.slabY(5) + 1 + k, EndCastle.MIN_Z + flight.z(k, lane));
	}

	/** Where a player stands on a step. */
	static Vec3 onStep(final int k, final int lane) {
		return Vec3.atBottomCenterOf(step(k, lane).above());
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_health", maxTicks = 60)
	public void theSovereignHasExactlyThreeHundredFiftyThousandMaximumHealthWhateverItsLevelAdds(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = SovereignGameTest.raise(helper, end);
		double ceiling = ((RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
		helper.startSequence()
			// A tick later every load listener has run, the health scale of the rpg mod included.
			.thenIdle(4)
			.thenExecute(() -> {
				float max = sovereign.getMaxHealth();
				float health = sovereign.getHealth();
				int level = LevelBridge.read(sovereign);
				EndTestSupport.result("sovereignHealth", "max=" + max + " health=" + health + " level=" + level + " ceiling=" + ceiling + " constant=" + UndeadSovereign.MAX_HEALTH);
				EndTestSupport.fresh(helper);
				helper.assertTrue(UndeadSovereign.MAX_HEALTH == 350000.0F, "the constant reads " + UndeadSovereign.MAX_HEALTH);
				helper.assertTrue(ceiling >= 350000.0, "the health attribute stops at " + ceiling + ", so this run cannot hold the fixed health");
				helper.assertTrue(Math.abs(max - 350000.0F) < 1.0F, "the Sovereign has " + max + " maximum health");
				helper.assertTrue(Math.abs(health - max) < 1.0F, "the Sovereign stands at " + health + " of " + max);
				helper.assertTrue(level == EndLevels.SOVEREIGN, "the Sovereign reads level " + level + " but must keep its level");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_trigger", maxTicks = 220)
	public void theSovereignRisesWhenAPlayerReachesTheMiddleStepOfTheFloorFiveStairAndNotBefore(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		helper.assertTrue(UndeadSovereign.current(end) == null, "a Sovereign stood before anyone came");
		int middle = CastleLayout.Flight.STEPS / 2;
		helper.assertTrue(step(middle, CastleLayout.Flight.WIDTH / 2).equals(CastleLayout.sovereignTrigger()), "the test reads the wrong step: " + step(middle, 1));
		CastleLayout.FloorPlan hall = EndCastles.layout(end).floor(EndCastle.FLOORS);
		List<Vec3> before = List.of(
			onStep(-1, 1),
			onStep(middle / 2, 1),
			onStep(middle - 1, 1),
			onStep(middle - 1, 0),
			Vec3.atBottomCenterOf(new BlockPos(EndCastle.MIN_X + hall.landingX(), EndCastle.standY(EndCastle.FLOORS), EndCastle.MIN_Z + hall.landingZ())),
			Vec3.atBottomCenterOf(EndCastle.arenaAnchor()).add(4.0, 0.0, 0.0));
		ServerPlayer[] who = new ServerPlayer[1];
		List<String> early = new ArrayList<>();
		var sequence = helper.startSequence();
		for (Vec3 at : before) {
			sequence = sequence
				.thenExecute(() -> who[0] = EndTestSupport.join(helper, end, at))
				.thenIdle(15)
				.thenExecute(() -> {
					if (UndeadSovereign.current(end) != null) {
						early.add("a Sovereign rose for a player at " + at);
					}
					end.getServer().getPlayerList().remove(who[0]);
				});
		}
		sequence
			.thenExecute(() -> {
				helper.assertTrue(early.isEmpty(), "too early: " + early);
				who[0] = EndTestSupport.join(helper, end, Vec3.atBottomCenterOf(CastleLayout.sovereignTrigger().above()));
			})
			.thenWaitUntil(() -> helper.assertTrue(UndeadSovereign.current(end) != null, "no Sovereign rose for a player on the middle step"))
			.thenExecute(() -> {
				Blaze sovereign = UndeadSovereign.current(end);
				EndTestSupport.result("sovereignTrigger", "stepsTried=" + before.size() + " early=" + early.size() + " risenAt=" + sovereign.position() + " trigger=" + CastleLayout.sovereignTrigger());
				EndTestSupport.fresh(helper);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_trigger_lanes", maxTicks = 80)
	public void aPlayerOnEitherSideLaneOfTheMiddleStepRaisesItToo(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int middle = CastleLayout.Flight.STEPS / 2;
		ServerPlayer[] who = new ServerPlayer[1];
		List<Integer> risen = new ArrayList<>();
		var sequence = helper.startSequence();
		for (int lane : new int[] {0, CastleLayout.Flight.WIDTH - 1}) {
			sequence = sequence
				.thenExecute(() -> who[0] = EndTestSupport.join(helper, end, onStep(middle, lane)))
				.thenWaitUntil(() -> helper.assertTrue(UndeadSovereign.current(end) != null, "no Sovereign rose for a player in lane " + lane))
				.thenExecute(() -> {
					risen.add(lane);
					end.getServer().getPlayerList().remove(who[0]);
					UndeadSovereign.current(end).discard();
				})
				.thenIdle(2);
		}
		sequence
			.thenExecute(() -> {
				EndTestSupport.result("sovereignTriggerLanes", "lanes=" + risen);
				EndTestSupport.fresh(helper);
				helper.assertTrue(risen.size() == 2, "lanes that raised it: " + risen);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_burst", maxTicks = 120)
	public void theSovereignBurstsSoulChargesSixteenWaysRoundAndFourWaysUpAndDownInsteadOfBurningTheFloor(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = SovereignGameTest.raise(helper, end);
		SovereignGameTest.inArena(helper, end, 8.0, 0.0);
		List<Vec3> aims = new ArrayList<>();
		List<String> bad = new ArrayList<>();
		Set<UUID> sampled = new HashSet<>();
		helper.onEachTick(() -> {
			if (UndeadSovereign.bursts().isEmpty()) {
				return;
			}
			for (UUID id : UndeadSovereign.bursts().get(0).charges()) {
				if (sampled.add(id)) {
					Entity charge = end.getEntity(id);
					if (charge instanceof SmallFireball fireball && fireball.getOwner() == sovereign && SoulCharges.is(fireball)) {
						aims.add(fireball.getDeltaMovement().normalize());
					} else {
						bad.add("burst charge " + id + " is " + charge);
					}
				}
			}
		});
		helper.startSequence()
			.thenExecute(() -> EndClock.advance(end, UndeadSovereign.BURST_PERIOD))
			.thenWaitUntil(() -> helper.assertTrue(!UndeadSovereign.bursts().isEmpty(), "no burst yet"))
			.thenIdle(3)
			.thenExecute(() -> {
				Set<String> distinct = new HashSet<>();
				for (Vec3 aim : aims) {
					distinct.add(String.format("%.2f/%.2f/%.2f", aim.x, aim.y, aim.z));
				}
				long level = aims.stream().filter(aim -> Math.abs(aim.y) < 0.02).count();
				long up = aims.stream().filter(aim -> aim.y > 0.2).count();
				long down = aims.stream().filter(aim -> aim.y < -0.2).count();
				int flames = 0;
				int floorY = EndCastle.standY(EndCastle.FLOORS);
				for (int dx = -12; dx <= 12; dx++) {
					for (int dz = -12; dz <= 12; dz++) {
						flames += end.getBlockState(new BlockPos(Mth.floor(ANCHOR.x) + dx, floorY, Mth.floor(ANCHOR.z) + dz)).is(EndBlocks.soulFlame()) ? 1 : 0;
					}
				}
				int recorded = UndeadSovereign.bursts().get(0).charges().size();
				EndTestSupport.result("sovereignBurst", "recorded=" + recorded + " sampled=" + aims.size() + " distinct=" + distinct.size() + " level=" + level + " up=" + up + " down=" + down
					+ " flames=" + flames + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "burst defects: " + bad);
				helper.assertTrue(UndeadSovereign.BURST_RING == 16 && UndeadSovereign.BURST_PITCHES.size() == 4, "the burst constants are off");
				helper.assertTrue(recorded == UndeadSovereign.BURST_RING + UndeadSovereign.BURST_PITCHES.size(), "the burst holds " + recorded + " charges");
				helper.assertTrue(aims.size() == recorded, "only " + aims.size() + " of " + recorded + " charges were seen alive");
				helper.assertTrue(distinct.size() >= 12, "the charges fly in " + distinct.size() + " directions");
				helper.assertTrue(level == UndeadSovereign.BURST_RING, level + " charges fly level");
				helper.assertTrue(up >= 1 && down >= 1, "up " + up + ", down " + down);
				helper.assertTrue(flames == 0, flames + " soul flames burn on the arena floor, where the burst replaced them");
			})
			.thenSucceed();
	}

	/** How many cells above the floor of a column a charge can fly through, counted from the standing cell. */
	static int headroom(final ServerLevel end, final double x, final double z) {
		int y = EndCastle.standY(EndCastle.FLOORS);
		int free = 0;
		while (free < 40) {
			BlockPos at = new BlockPos(Mth.floor(x), y + free, Mth.floor(z));
			if (!end.getBlockState(at).getCollisionShape(end, at).isEmpty()) {
				break;
			}
			free++;
		}
		return free;
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_rain", maxTicks = 140)
	public void everyFortyTicksTheSovereignDropsSoulChargesFromTwelveToSixteenBlocksAboveTheArenaAndTheyFallOnIt(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = SovereignGameTest.raise(helper, end);
		SovereignGameTest.inArena(helper, end, 8.0, 0.0);
		int floorY = EndCastle.standY(EndCastle.FLOORS);
		Map<UUID, Vec3> first = new HashMap<>();
		Map<UUID, Vec3> motion = new HashMap<>();
		Map<UUID, Double> lowest = new HashMap<>();
		Set<UUID> gone = new HashSet<>();
		List<String> bad = new ArrayList<>();
		helper.onEachTick(() -> {
			if (UndeadSovereign.rains().isEmpty()) {
				return;
			}
			for (UUID id : UndeadSovereign.rains().get(0).charges()) {
				Entity charge = end.getEntity(id);
				if (charge instanceof SmallFireball fireball && fireball.getOwner() == sovereign && SoulCharges.is(fireball)) {
					first.putIfAbsent(id, fireball.position());
					motion.putIfAbsent(id, fireball.getDeltaMovement());
					lowest.merge(id, fireball.getY(), Math::min);
				} else if (first.containsKey(id)) {
					gone.add(id);
				} else {
					bad.add("rain charge " + id + " is " + charge);
				}
			}
		});
		helper.startSequence()
			.thenExecute(() -> EndClock.advance(end, UndeadSovereign.RAIN_PERIOD))
			.thenWaitUntil(() -> helper.assertTrue(!UndeadSovereign.rains().isEmpty(), "no rain yet"))
			.thenIdle(45)
			.thenExecute(() -> {
				List<UUID> charges = UndeadSovereign.rains().get(0).charges();
				int fell = 0;
				for (UUID id : charges) {
					Vec3 start = first.get(id);
					Vec3 aim = motion.get(id);
					if (start == null || aim == null) {
						bad.add("charge " + id + " was never seen");
						continue;
					}
					double reach = Math.hypot(start.x - ANCHOR.x, start.z - ANCHOR.z);
					double height = start.y - floorY;
					int room = headroom(end, start.x, start.z);
					if (reach > UndeadSovereign.RAIN_RADIUS + 0.5) {
						bad.add("charge " + id + " began " + reach + " blocks from the Sovereign");
					}
					if (height > UndeadSovereign.RAIN_MAX_HEIGHT + 0.5 || height < Math.min(UndeadSovereign.RAIN_MIN_HEIGHT, room) - 1.0 || height < 5.0) {
						bad.add("charge " + id + " began " + height + " above the floor with " + room + " cells of room");
					}
					if (aim.y > -0.01 || Math.hypot(aim.x, aim.z) > 0.01) {
						bad.add("charge " + id + " does not fall straight: " + aim);
					}
					fell += gone.contains(id) || lowest.get(id) <= floorY + 3.0 ? 1 : 0;
				}
				EndTestSupport.result("sovereignRain", "charges=" + charges.size() + " fell=" + fell + " bad=" + bad.size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(UndeadSovereign.RAIN_MIN_HEIGHT == 12 && UndeadSovereign.RAIN_MAX_HEIGHT == 16, "the rain height constants are off");
				helper.assertTrue(charges.size() == UndeadSovereign.RAIN_COUNT, "the rain holds " + charges.size() + " charges");
				helper.assertTrue(bad.isEmpty(), "rain defects: " + bad);
				helper.assertTrue(fell == charges.size(), "only " + fell + " of " + charges.size() + " charges fell onto the arena");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:soul_no_fire", maxTicks = 80)
	public void aSoulChargeSetsNoFireWhereItLandsWhileAPlainFireballStill(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = SovereignGameTest.raise(helper, end);
		Blaze plain = EntityTypes.BLAZE.create(end, EntitySpawnReason.SPAWNER);
		LevelBridge.claim(plain);
		plain.setNoAi(true);
		plain.snapTo(-3.5, EndCastle.standY(EndCastle.FLOORS) + 4, 3.5, 0.0F, 0.0F);
		end.addFreshEntity(plain);
		int floorY = EndCastle.standY(EndCastle.FLOORS);
		SmallFireball soul = SoulCharges.fire(end, sovereign, new Vec3(3.5, floorY + 4.0, 3.5), new Vec3(0.0, -1.0, 0.0), SoulCharges.SOVEREIGN_FACTOR);
		SmallFireball ordinary = new SmallFireball(end, plain, new Vec3(0.0, -1.0, 0.0));
		ordinary.setPos(-3.5, floorY + 4.0, 3.5);
		end.addFreshEntity(ordinary);
		helper.assertTrue(soul != null && SoulCharges.is(soul) && !SoulCharges.is(ordinary), "the charges are not told apart");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(soul.isRemoved() && ordinary.isRemoved(), "the fireballs have not landed"))
			.thenExecute(() -> {
				int soulFires = fires(end, 3, floorY);
				int ordinaryFires = fires(end, -4, floorY);
				plain.discard();
				EndTestSupport.result("soulNoFire", "soulFires=" + soulFires + " ordinaryFires=" + ordinaryFires);
				EndTestSupport.fresh(helper);
				helper.assertTrue(ordinaryFires > 0, "a plain fireball set no fire, so this run cannot tell: mob griefing may be off");
				helper.assertTrue(soulFires == 0, "a soul charge set " + soulFires + " fires");
			})
			.thenSucceed();
	}

	/** The fires standing in the 3 by 3 by 3 cells round a spot of the arena floor. */
	static int fires(final ServerLevel end, final int x, final int floorY) {
		int found = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = 0; dz <= 7; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					found += end.getBlockState(new BlockPos(x + dx, floorY + dy, dz)).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock ? 1 : 0;
				}
			}
		}
		return found;
	}

	@GameTest(environment = "mod-stella-end-test:sovereign_sealed", maxTicks = 160)
	public void theWayToTheRestAreaStaysSealedFromTheTriggerUntilTheSovereignAndBothKnightsDie(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		List<BlockPos> door = new ArrayList<>();
		int slab = EndCastle.slabY(EndCastle.FLOORS);
		for (CastleLayout.Door cells : EndCastles.layout(end).floor(EndCastle.FLOORS).doors()) {
			if (cells.barrier()) {
				for (int x = cells.x0(); x <= cells.x1(); x++) {
					for (int z = cells.z0(); z <= cells.z1(); z++) {
						for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
							door.add(EndTestSupport.world(EndCastle.min(), x, y, z));
						}
					}
				}
			}
		}
		helper.assertTrue(!door.isEmpty(), "floor 6 has no barrier door");
		long[] shut = new long[4];
		helper.startSequence()
			.thenExecute(() -> {
				shut[0] = door.stream().filter(pos -> end.getBlockState(pos).is(Blocks.BARRIER)).count();
				EndTestSupport.join(helper, end, Vec3.atBottomCenterOf(CastleLayout.sovereignTrigger().above()));
			})
			.thenWaitUntil(() -> helper.assertTrue(UndeadSovereign.current(end) != null, "no Sovereign rose"))
			.thenIdle(60)
			.thenExecute(() -> {
				shut[1] = door.stream().filter(pos -> end.getBlockState(pos).is(Blocks.BARRIER)).count();
				UndeadSovereign.current(end).kill(end);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				// The Sovereign rose with two knights, so its death alone leaves the way shut.
				shut[3] = door.stream().filter(pos -> end.getBlockState(pos).is(Blocks.BARRIER)).count();
				WitherKnights.alive(end).forEach(knight -> EndTestSupport.slay(end, knight));
			})
			.thenIdle(3)
			.thenExecute(() -> {
				shut[2] = door.stream().filter(pos -> end.getBlockState(pos).isAir()).count();
				EndTestSupport.result("sovereignSealed", "cells=" + door.size() + " beforeTrigger=" + shut[0] + " whileAlive=" + shut[1] + " afterSovereignDeath=" + shut[3] + " afterKnights=" + shut[2]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(shut[0] == door.size(), "the door was open before the Sovereign rose: " + shut[0] + " of " + door.size());
				helper.assertTrue(shut[1] == door.size(), "the door opened while the Sovereign lived: " + shut[1] + " of " + door.size());
				helper.assertTrue(shut[3] == door.size(), "the door opened with the knights alive: " + shut[3] + " of " + door.size());
				helper.assertTrue(shut[2] == door.size(), "the door stayed shut after the Sovereign and both knights died: " + shut[2] + " of " + door.size());
			})
			.thenSucceed();
	}
}
