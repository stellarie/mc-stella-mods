package com.stella.tower;

import com.stella.mobs.scaling.ThreatState;
import com.stella.tower.colosseum.GuardianBrain;
import com.stella.tower.colosseum.GuardianKind;
import com.stella.tower.colosseum.Guardians;
import com.stella.tower.colosseum.MoveKind;
import com.stella.tower.mixin.EntityVisualFireAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Tower Guardians, on a rig of their own over the open sky. The numbers in the request are literals here, so a retune of
 * the code edits this file on purpose. Test players are not ticked: their effects, fire and knockback stay as the moves set
 * them, so each case reads the result of one move and nothing a clock would change.
 * <p>
 * Every case hands its scenario to {@link GuardianRig#ready}: the rig's chunks must tick before anything is spawned in them.
 */
public class TowerGuardianGameTest {
	/** The environment for the case that needs the sun: the overworld clock pinned to noon. */
	private static final String NOON = "mod-stella-tower-test:noon";
	private static final int SPEC_LEVEL = 50;
	private static final int SPEC_RIDER_VIT = 100;
	private static final int SPEC_HORSE_VIT = 150;
	private static final int SPEC_ARCHER_MIN_DEX = 50;
	private static final double SPEC_SCALE = 2.0;
	private static final int SPEC_NAUSEA_TICKS = 40;
	private static final int SPEC_VOLLEY_ARROWS = 8;
	private static final int SPEC_VOLLEY_GAP = 3;
	private static final int SPEC_LOB_ARROWS = 8;
	private static final int SPEC_INVISIBLE_TICKS = 40;
	private static final double SPEC_BLINK_DISTANCE = 8.0;
	private static final double SPEC_VANILLA_HORSE_SPEED = 0.3375;
	private static final double SPEC_VANILLA_HORSE_JUMP = 1.0;
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final EquipmentSlot[] KIT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private static double flat(final Vec3 a, final Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static Item item(final net.minecraft.resources.Identifier id) {
		return BuiltInRegistries.ITEM.getValue(id);
	}

	// ---------------------------------------------------------------- what they are

	/** Catches a missing level, stat, size, piece of kit, mark of fire, or Blitz Beat, and a guardian that does not stand where it belongs. */
	@GameTest(maxTicks = 700)
	public void theGuardiansStandAtLevelFiftyInNetheriteAtTwiceTheSizeWithoutFire(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 0);
		Guardians.Spawned[] pair = new Guardians.Spawned[2];
		rig.ready(() -> {
			pair[0] = rig.spawn(GuardianKind.MELEE, -11, 0);
			pair[1] = rig.spawn(GuardianKind.RANGED, 11, 0);
		});
		rig.after(6L, () -> {
			Guardians.Spawned melee = pair[0];
			Guardians.Spawned archer = pair[1];
			Mob[] all = {melee.rider(), melee.horse(), archer.rider(), archer.horse()};
			int wrongLevel = 0;
			int wrongScale = 0;
			int noFire = 0;
			for (Mob mob : all) {
				wrongLevel += ThreatState.level(mob) == SPEC_LEVEL ? 0 : 1;
				wrongScale += Math.abs(mob.getAttributeBaseValue(Attributes.SCALE) - SPEC_SCALE) < 1.0E-9 ? 0 : 1;
				noFire += !((EntityVisualFireAccessor) mob).stellaTower$hasVisualFire() && !mob.isOnFire() ? 0 : 1;
			}
			Map<String, Integer> meleeStats = TowerGameTestInit.STATS.get(melee.rider().getUUID());
			Map<String, Integer> archerStats = TowerGameTestInit.STATS.get(archer.rider().getUUID());
			helper.assertTrue(meleeStats != null && archerStats != null, "the rpg stat share was never given the riders");
			int meleeSpread = meleeStats.get("str") + meleeStats.get("agi") + meleeStats.get("dex") + meleeStats.get("int") + meleeStats.get("luk");
			int archerSpread = archerStats.get("str") + archerStats.get("agi") + archerStats.get("dex") + archerStats.get("int") + archerStats.get("luk");
			int wrongKit = 0;
			for (EquipmentSlot slot : ARMOUR) {
				wrongKit += melee.rider().getItemBySlot(slot).getItem() == archer.rider().getItemBySlot(slot).getItem()
					&& BuiltInRegistries.ITEM.getKey(melee.rider().getItemBySlot(slot).getItem()).getPath().startsWith("netherite_") ? 0 : 1;
			}
			wrongKit += melee.rider().getMainHandItem().is(Items.NETHERITE_SWORD) ? 0 : 1;
			wrongKit += archer.rider().getMainHandItem().is(item(TowerGameTestInit.RUDRA_BOW)) ? 0 : 1;
			int drops = 0;
			for (EquipmentSlot slot : KIT) {
				drops += melee.rider().getDropChances().byEquipment(slot) == 0.0F && archer.rider().getDropChances().byEquipment(slot) == 0.0F ? 0 : 1;
			}
			TowerTestSupport.result("guardianStats", "wrongLevel=" + wrongLevel + " wrongScale=" + wrongScale + " noFire=" + noFire
				+ " melee=" + meleeStats + " archer=" + archerStats + " horses=" + TowerGameTestInit.STATS.get(melee.horse().getUUID())
				+ "/" + TowerGameTestInit.STATS.get(archer.horse().getUUID()) + " wrongKit=" + wrongKit + " drops=" + drops
				+ " blitz=" + TowerGameTestInit.BLITZED.contains(archer.rider().getUUID()) + " ticked=" + melee.rider().tickCount);
			helper.assertTrue(wrongLevel == 0, wrongLevel + " of the four are not level " + SPEC_LEVEL);
			helper.assertTrue(wrongScale == 0, wrongScale + " of the four are not " + SPEC_SCALE + " times the size");
			helper.assertTrue(noFire == 0, noFire + " of the four look on fire; the wreath is particles only");
			helper.assertTrue(meleeStats.get("vit") == SPEC_RIDER_VIT && archerStats.get("vit") == SPEC_RIDER_VIT, "the riders' vitality is not " + SPEC_RIDER_VIT);
			helper.assertTrue(meleeSpread == SPEC_LEVEL, "the melee guardian's other stats add to " + meleeSpread + ", not the level");
			helper.assertTrue(archerStats.get("dex") >= SPEC_ARCHER_MIN_DEX, "the archer's dexterity is " + archerStats.get("dex"));
			helper.assertTrue(archerSpread > meleeSpread, "the archer is not slightly stronger: " + archerSpread + " against " + meleeSpread);
			helper.assertTrue(TowerGameTestInit.STATS.get(melee.horse().getUUID()).get("vit") == SPEC_HORSE_VIT
				&& TowerGameTestInit.STATS.get(archer.horse().getUUID()).get("vit") == SPEC_HORSE_VIT, "the horses' vitality is not " + SPEC_HORSE_VIT);
			helper.assertTrue(wrongKit == 0, wrongKit + " pieces of kit are not as specified");
			helper.assertTrue(drops == 0, "some kit has a drop chance");
			helper.assertTrue(TowerGameTestInit.BLITZED.contains(archer.rider().getUUID()) && !TowerGameTestInit.BLITZED.contains(melee.rider().getUUID()),
				"Blitz Beat is not the archer's alone");
			helper.assertTrue(melee.rider().getVehicle() == melee.horse() && archer.rider().getVehicle() == archer.horse(), "a rider is not on its horse");
			helper.assertTrue(flat(melee.rider().position(), Vec3.atBottomCenterOf(rig.center.offset(-11, 1, 0))) < 3.0, "the melee guardian is off its post");
			helper.succeed();
		});
	}

	/**
	 * Catches a guardian or a horse that burns, or loses a helmet, in full daylight; the control skeleton proves there is a sun.
	 * The case runs in an environment that pins the clock to noon, in a batch of its own, so no other case sees the time move.
	 */
	@GameTest(environment = NOON, maxTicks = 1000)
	public void neitherRiderNorHorseBurnsInSunlight(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 1);
		helper.assertTrue(burns(rig, new Vec3(0.5, GuardianRig.FLOOR_Y + 1.0, 0.5)), "the noon environment did not give the rig a sun");
		Guardians.Spawned[] pair = new Guardians.Spawned[2];
		Skeleton[] control = new Skeleton[1];
		boolean[] controlBurned = {false};
		int[] guardiansBurned = {0};
		float[] lowest = {Float.MAX_VALUE};
		rig.ready(() -> {
			pair[0] = rig.spawn(GuardianKind.MELEE, -11, 0);
			pair[1] = rig.spawn(GuardianKind.RANGED, 11, 0);
			Skeleton plain = EntityTypes.SKELETON.create(rig.level, EntitySpawnReason.EVENT);
			plain.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
			plain.setPersistenceRequired();
			plain.snapTo(rig.center.getX() + 0.5, rig.center.getY() + 1.0, rig.center.getZ() + 8.5, 0.0F, 0.0F);
			rig.level.addFreshEntity(plain);
			control[0] = plain;
		});
		helper.runBeforeTestEnd(() -> {
			if (control[0] != null) {
				control[0].discard();
			}
		});
		rig.each(() -> {
			controlBurned[0] |= control[0].getRemainingFireTicks() > 0 || control[0].getHealth() < control[0].getMaxHealth();
			for (Guardians.Spawned each : pair) {
				for (Mob mob : new Mob[] {each.rider(), each.horse()}) {
					guardiansBurned[0] += mob.getRemainingFireTicks() > 0 ? 1 : 0;
					lowest[0] = Math.min(lowest[0], mob.getHealth() / mob.getMaxHealth());
				}
			}
		});
		rig.after(250L, () -> {
			TowerTestSupport.result("sunlight", "controlBurned=" + controlBurned[0] + " guardianBurnTicks=" + guardiansBurned[0] + " lowestHealth=" + lowest[0]
				+ " helmets=" + pair[0].rider().getItemBySlot(EquipmentSlot.HEAD).getDamageValue() + "/" + pair[1].rider().getItemBySlot(EquipmentSlot.HEAD).getDamageValue());
			helper.assertTrue(controlBurned[0], "the plain skeleton never burned: the harness gave no sun, so this case proves nothing");
			helper.assertTrue(guardiansBurned[0] == 0, "a guardian or a horse was set alight in the sun for " + guardiansBurned[0] + " ticks");
			helper.assertTrue(lowest[0] == 1.0F, "a guardian or a horse lost health in the sun: " + lowest[0]);
			helper.assertTrue(!pair[0].rider().getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !pair[1].rider().getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
				"a helmet was lost to the sun");
			helper.assertTrue(pair[0].rider().getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 0 && pair[1].rider().getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 0,
				"the sun wore a helmet down");
			helper.succeed();
		});
	}

	private static boolean burns(final GuardianRig rig, final Vec3 at) {
		return Boolean.TRUE.equals(rig.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MONSTERS_BURN, at));
	}

	/** Catches a boss bar that never reaches a player near a guardian, or that does not follow its health. */
	@GameTest(maxTicks = 700)
	public void aBossBarShowsTheGuardiansHealthToPlayersNearIt(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 2);
		Guardians.Spawned[] melee = new Guardians.Spawned[1];
		ServerPlayer[] player = new ServerPlayer[1];
		rig.ready(() -> {
			melee[0] = rig.spawn(GuardianKind.MELEE, -11, 0);
			player[0] = rig.player(0, 0, 0.0F);
		});
		rig.after(12L, () -> {
			GuardianBrain brain = GuardianBrain.of(melee[0].rider());
			helper.assertTrue(brain != null, "the guardian has no brain");
			helper.assertTrue(brain.bar().getPlayers().contains(player[0]), "a player 11 blocks away does not see the bar");
			helper.assertTrue(Math.abs(brain.bar().getProgress() - 1.0F) < 0.01F, "a whole guardian shows " + brain.bar().getProgress());
			melee[0].rider().setHealth(melee[0].rider().getMaxHealth() / 2.0F);
		});
		rig.after(30L, () -> {
			GuardianBrain brain = GuardianBrain.of(melee[0].rider());
			float expected = melee[0].rider().getHealth() / melee[0].rider().getMaxHealth();
			TowerTestSupport.result("bossBar", "progress=" + brain.bar().getProgress() + " health=" + expected);
			helper.assertTrue(Math.abs(brain.bar().getProgress() - expected) < 0.02F, "the bar shows " + brain.bar().getProgress() + " for a guardian at " + expected);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the arena

	/**
	 * Catches a guardian that follows a player out of the arena, one that ignores a blow from outside, one that keeps hunting
	 * after the memory of the blow lapses, and one that stays out instead of going home.
	 */
	@GameTest(maxTicks = 1200)
	public void aGuardianNeverLeavesTheArenaForAPlayerButHuntsWhoHitsIt(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 3);
		Guardians.Spawned[] melee = new Guardians.Spawned[1];
		ServerPlayer[] outsider = new ServerPlayer[1];
		Vec3 post = Vec3.atBottomCenterOf(rig.center.offset(-11, 1, 0));
		Vec3 middle = Vec3.atBottomCenterOf(rig.center);
		long hitAt = 60L;
		double[] farthest = {0.0};
		double[] nearest = {Double.MAX_VALUE};
		rig.ready(() -> {
			melee[0] = rig.spawn(GuardianKind.MELEE, -11, 0);
			// The guardian strikes whoever hit it, so the player must outlast the hunt.
			outsider[0] = rig.sturdy(rig.player(-20, 0, 270.0F));
		});
		rig.each(() -> {
			long tick = rig.elapsed();
			GuardianBrain brain = GuardianBrain.of(melee[0].rider());
			if (tick < hitAt) {
				farthest[0] = Math.max(farthest[0], flat(melee[0].rider().position(), middle));
				helper.assertTrue(brain.focus() == null, "the guardian took a player outside the arena as its target");
			} else if (tick == hitAt) {
				float before = melee[0].rider().getHealth();
				boolean landed = melee[0].rider().hurtServer(rig.level, rig.level.damageSources().playerAttack(outsider[0]), 2.0F);
				TowerTestSupport.result("guardianLeashHit", "landed=" + landed + " health=" + before + "->" + melee[0].rider().getHealth());
			} else if (tick <= hitAt + 100L) {
				nearest[0] = Math.min(nearest[0], flat(melee[0].rider().position(), outsider[0].position()));
				if (tick == hitAt + 100L) {
					helper.assertTrue(brain.focus() == outsider[0], "a blow from outside the arena did not make the guardian hunt the player: focus " + brain.focus()
						+ ", alive " + outsider[0].isAlive() + ", sameLevel " + (outsider[0].level() == melee[0].rider().level()) + ", at " + melee[0].rider().position());
				}
			}
		});
		rig.after(hitAt + 100L + GuardianBrain.RETALIATE_TICKS + 20L, () -> {
			helper.assertTrue(GuardianBrain.of(melee[0].rider()).focus() == null, "the guardian still hunts a player who struck it " + GuardianBrain.RETALIATE_TICKS + " ticks ago");
		});
		rig.after(hitAt + 100L + GuardianBrain.RETALIATE_TICKS + 160L, () -> {
			double home = flat(melee[0].rider().position(), post);
			TowerTestSupport.result("guardianLeash", "farthestBeforeHit=" + farthest[0] + " nearestToOutsider=" + nearest[0] + " backAtPost=" + home);
			helper.assertTrue(farthest[0] <= GuardianRig.ARENA, "the guardian left the arena with nobody to hunt: " + farthest[0]);
			helper.assertTrue(nearest[0] <= 6.0, "the guardian never reached the player who hit it from outside: " + nearest[0]);
			helper.assertTrue(home <= 6.0, "the guardian did not go back to its post: " + home);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- auras

	/** Catches an aura of the wrong kind, one that reaches too far, and one that is missing. */
	@GameTest(maxTicks = 700)
	public void theMeleeGuardianSlowsAndTheArcherWeakensPlayersAroundThem(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 4);
		ServerPlayer[] players = new ServerPlayer[3];
		rig.ready(() -> {
			rig.spawn(GuardianKind.MELEE, -11, 0);
			rig.spawn(GuardianKind.RANGED, 11, 0);
			players[0] = rig.player(-14, 0, 0.0F);
			players[1] = rig.player(14, 0, 0.0F);
			players[2] = rig.player(0, 20, 0.0F);
		});
		rig.after(45L, () -> {
			ServerPlayer nearMelee = players[0];
			ServerPlayer nearArcher = players[1];
			ServerPlayer far = players[2];
			TowerTestSupport.result("guardianAuras", "nearMelee=" + nearMelee.getActiveEffects().size() + " nearArcher=" + nearArcher.getActiveEffects().size()
				+ " far=" + far.getActiveEffects().size());
			helper.assertTrue(nearMelee.hasEffect(MobEffects.SLOWNESS) && !nearMelee.hasEffect(MobEffects.WEAKNESS), "a player by the melee guardian is not only slowed");
			helper.assertTrue(nearArcher.hasEffect(MobEffects.WEAKNESS) && !nearArcher.hasEffect(MobEffects.SLOWNESS), "a player by the archer is not only weakened");
			helper.assertTrue(!far.hasEffect(MobEffects.SLOWNESS) && !far.hasEffect(MobEffects.WEAKNESS), "a player far from both has an aura effect");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- melee moves

	/** Catches a blink that lands in front of its target, one that does not hit, one that moves the rider off its horse, and one that starts under 8 blocks. */
	@GameTest(maxTicks = 700)
	public void theMeleeGuardianBlinksBehindAPlayerEightBlocksAwayAndHits(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 5);
		Guardians.Spawned[] melee = new Guardians.Spawned[1];
		ServerPlayer[] player = new ServerPlayer[1];
		double[] last = {-1.0};
		double[] before = {0.0};
		Vec3[] landed = {null};
		rig.ready(() -> {
			melee[0] = rig.spawn(GuardianKind.MELEE, -11, 0);
			player[0] = rig.sturdy(rig.player(1, 0, 90.0F));
			before[0] = flat(melee[0].rider().position(), player[0].position());
			last[0] = before[0];
			helper.assertTrue(before[0] >= SPEC_BLINK_DISTANCE, "the case starts " + before[0] + " blocks away, under the blink distance");
			helper.assertTrue(GuardianBrain.of(melee[0].rider()).begin(MoveKind.BLINK, player[0]), "the guardian would not begin to blink");
		});
		rig.each(() -> {
			double now = flat(melee[0].rider().position(), player[0].position());
			if (landed[0] == null && last[0] - now > 5.0) {
				landed[0] = melee[0].rider().position();
			}
			last[0] = now;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(landed[0] != null, "the guardian never blinked");
			double away = flat(landed[0], player[0].position());
			helper.assertTrue(player[0].getHealth() < player[0].getMaxHealth(), "the blink did not hit");
			TowerTestSupport.result("guardianBlink", "from=" + before[0] + " landedAt=" + away + " behind=" + (landed[0].x - player[0].getX())
				+ " health=" + player[0].getHealth() + " riding=" + (melee[0].rider().getVehicle() == melee[0].horse()));
			helper.assertTrue(landed[0].x > player[0].getX() + 1.0, "the guardian did not land behind a player who faces it: " + (landed[0].x - player[0].getX()));
			helper.assertTrue(away >= 1.5 && away <= 5.0, "the guardian landed " + away + " blocks from the player");
			helper.assertTrue(melee[0].rider().getVehicle() == melee[0].horse(), "the rider was left behind by its horse");
		});
	}

	/** Catches a cone that misses a player ahead, burns one beside the guardian, or burns without hurting. */
	@GameTest(maxTicks = 700)
	public void theFireConeBurnsWhatStandsAheadAndNothingBeside(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 6);
		Guardians.Spawned[] melee = new Guardians.Spawned[1];
		ServerPlayer[] players = new ServerPlayer[2];
		GuardianBrain[] brain = new GuardianBrain[1];
		rig.ready(() -> {
			melee[0] = rig.spawn(GuardianKind.MELEE, -11, 0);
			players[0] = rig.sturdy(rig.player(-6, 0, 0.0F));
			players[1] = rig.sturdy(rig.player(-11, 5, 0.0F));
			brain[0] = GuardianBrain.of(melee[0].rider());
			helper.assertTrue(brain[0].begin(MoveKind.FIRE_CONE, players[0]), "the guardian would not begin the cone");
		});
		rig.after(40L, () -> {
			helper.assertTrue(brain[0].current() == null, "the cone is still being made");
			ServerPlayer ahead = players[0];
			ServerPlayer beside = players[1];
			TowerTestSupport.result("guardianCone", "aheadBurning=" + ahead.isOnFire() + " aheadHealth=" + ahead.getHealth() + " besideBurning=" + beside.isOnFire()
				+ " besideHealth=" + beside.getHealth() + " guardianBurning=" + (melee[0].rider().getRemainingFireTicks() > 0));
			helper.assertTrue(ahead.isOnFire() && ahead.getHealth() < ahead.getMaxHealth(), "the player ahead was not hurt and set alight");
			helper.assertTrue(!beside.isOnFire() && beside.getHealth() == beside.getMaxHealth(), "the player beside the guardian was caught by the cone");
			helper.succeed();
		});
	}

	/** Catches a slam that misses the strip, hits outside it, forgets the knockback or the Nausea, or puts the wrong time on the Nausea. */
	@GameTest(maxTicks = 700)
	public void theGroundSlamHurtsPushesAndSickensAlongItsStripOnly(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 7);
		ServerPlayer[] players = new ServerPlayer[3];
		GuardianBrain[] brain = new GuardianBrain[1];
		rig.ready(() -> {
			Guardians.Spawned melee = rig.spawn(GuardianKind.MELEE, -11, 0);
			players[0] = rig.sturdy(rig.player(-1, 0, 0.0F));
			players[1] = rig.sturdy(rig.player(-6, 4, 0.0F));
			players[2] = rig.sturdy(rig.player(9, 0, 0.0F));
			brain[0] = GuardianBrain.of(melee.rider());
			helper.assertTrue(brain[0].begin(MoveKind.SLAM, players[0]), "the guardian would not begin the slam");
		});
		rig.after(40L, () -> {
			helper.assertTrue(brain[0].current() == null, "the slam is still being made: " + brain[0].current());
			ServerPlayer inStrip = players[0];
			ServerPlayer wide = players[1];
			ServerPlayer past = players[2];
			MobEffectInstance sick = inStrip.getEffect(MobEffects.NAUSEA);
			Vec3 push = inStrip.getDeltaMovement();
			TowerTestSupport.result("guardianSlam", "health=" + inStrip.getHealth() + " push=" + push + " nausea=" + (sick == null ? "none" : sick.getDuration())
				+ " wideHealth=" + wide.getHealth() + " pastHealth=" + past.getHealth());
			helper.assertTrue(inStrip.getHealth() < inStrip.getMaxHealth(), "the player in the strip was not hurt");
			helper.assertTrue(push.x > 0.2, "the player in the strip was not knocked away from the guardian: " + push);
			helper.assertTrue(sick != null && sick.getDuration() == SPEC_NAUSEA_TICKS, "the Nausea is not " + SPEC_NAUSEA_TICKS + " ticks: " + sick);
			helper.assertTrue(wide.getHealth() == wide.getMaxHealth() && !wide.hasEffect(MobEffects.NAUSEA), "a player beside the 4 block strip was hit");
			helper.assertTrue(past.getHealth() == past.getMaxHealth() && !past.hasEffect(MobEffects.NAUSEA), "a player past the 16 block strip was hit");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- ranged moves

	/** Catches a vanish that hides only the rider or only the horse, one that lasts the wrong time, and one with no attack after it. */
	@GameTest(maxTicks = 700)
	public void theArcherVanishesWithItsHorseForTwoSecondsThenAttacks(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 8);
		Guardians.Spawned[] archer = new Guardians.Spawned[1];
		GuardianBrain[] brain = new GuardianBrain[1];
		int[] seen = {-1, -1};
		boolean[] hidAtOnce = {false};
		boolean[] over = {false};
		rig.ready(() -> {
			archer[0] = rig.spawn(GuardianKind.RANGED, 11, 0);
			ServerPlayer player = rig.player(0, 0, 0.0F);
			brain[0] = GuardianBrain.of(archer[0].rider());
			helper.assertTrue(brain[0].begin(MoveKind.VANISH, player), "the archer would not begin to vanish");
		});
		rig.each(() -> {
			if (rig.elapsed() == 3L) {
				MobEffectInstance rider = archer[0].rider().getEffect(MobEffects.INVISIBILITY);
				MobEffectInstance horse = archer[0].horse().getEffect(MobEffects.INVISIBILITY);
				seen[0] = rider == null ? -1 : rider.getDuration();
				seen[1] = horse == null ? -1 : horse.getDuration();
				hidAtOnce[0] = archer[0].rider().isInvisible() && archer[0].horse().isInvisible();
			}
			if (!over[0] && rig.elapsed() > 3L && brain[0].current() == null) {
				over[0] = true;
				TowerTestSupport.result("guardianVanish", "riderLeft=" + seen[0] + " horseLeft=" + seen[1] + " loosed=" + brain[0].loosed().size()
					+ " unseenAtTheEnd=" + (archer[0].rider().isInvisible() || archer[0].horse().isInvisible()));
				helper.assertTrue(hidAtOnce[0], "the rider and the horse were not both invisible three ticks in");
				helper.assertTrue(seen[0] > SPEC_INVISIBLE_TICKS - 8 && seen[0] <= SPEC_INVISIBLE_TICKS && seen[1] > SPEC_INVISIBLE_TICKS - 8 && seen[1] <= SPEC_INVISIBLE_TICKS,
					"the invisibility is not " + SPEC_INVISIBLE_TICKS + " ticks: rider " + seen[0] + ", horse " + seen[1]);
				helper.assertTrue(!archer[0].rider().isInvisible() && !archer[0].horse().isInvisible(), "the archer is still unseen when the move ends");
				helper.assertTrue(brain[0].loosed().size() == 1, "the archer did not attack once after it showed itself: " + brain[0].loosed().size());
				helper.succeed();
			}
		});
	}

	/** Catches a volley of the wrong size, arrows at the wrong pace, arrows that fly away from the target, and a volley that carries on past eight. */
	@GameTest(maxTicks = 700)
	public void theArcherLoosesEightArrowsThreeTicksApartAtItsTarget(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 9);
		ServerPlayer[] player = new ServerPlayer[1];
		GuardianBrain[] brain = new GuardianBrain[1];
		List<Long> releases = new ArrayList<>();
		double[] worst = {0.0};
		int[] counted = {0};
		boolean[] over = {false};
		rig.ready(() -> {
			Guardians.Spawned archer = rig.spawn(GuardianKind.RANGED, 11, 0);
			player[0] = rig.player(-3, 0, 0.0F);
			brain[0] = GuardianBrain.of(archer.rider());
			helper.assertTrue(brain[0].begin(MoveKind.VOLLEY, player[0]), "the archer would not begin the volley");
		});
		rig.each(() -> {
			int now = brain[0].loosed().size();
			for (int i = counted[0]; i < now; i++) {
				releases.add(rig.elapsed());
				if (rig.level.getEntity(brain[0].loosed().get(i)) instanceof AbstractArrow arrow) {
					Vec3 heading = arrow.getDeltaMovement();
					Vec3 wanted = player[0].getBoundingBox().getCenter().subtract(arrow.position());
					double off = Math.abs(Math.toDegrees(Math.atan2(heading.z, heading.x) - Math.atan2(wanted.z, wanted.x)));
					worst[0] = Math.max(worst[0], off > 180.0 ? 360.0 - off : off);
				}
			}
			counted[0] = now;
			if (!over[0] && brain[0].current() == null) {
				over[0] = true;
				TowerTestSupport.result("guardianVolley", "arrows=" + brain[0].loosed().size() + " releases=" + releases + " worstBearingOff=" + worst[0]);
				helper.assertTrue(brain[0].loosed().size() == SPEC_VOLLEY_ARROWS, "the volley has " + brain[0].loosed().size() + " arrows, not " + SPEC_VOLLEY_ARROWS);
				for (int i = 1; i < releases.size(); i++) {
					helper.assertTrue(releases.get(i) - releases.get(i - 1) == SPEC_VOLLEY_GAP, "arrows " + i + " and " + (i + 1) + " are " + (releases.get(i) - releases.get(i - 1)) + " ticks apart");
				}
				helper.assertTrue(worst[0] < 12.0, "an arrow flew " + worst[0] + " degrees off the target");
				helper.succeed();
			}
		});
	}

	/** Catches lobbed arrows that do not burn, that burst nowhere, that break blocks, or that set no fire round the target. */
	@GameTest(maxTicks = 900)
	public void theArcherLobsEightBurningArrowsThatBurstAndSetTheGroundAlight(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 10);
		ServerPlayer[] player = new ServerPlayer[1];
		GuardianBrain[] brain = new GuardianBrain[1];
		BlockPos[] spot = new BlockPos[1];
		int[] burning = {0};
		int[] counted = {0};
		int[] fires = {0};
		rig.ready(() -> {
			Guardians.Spawned archer = rig.spawn(GuardianKind.RANGED, 11, 0);
			player[0] = rig.sturdy(rig.player(-2, 0, 0.0F));
			spot[0] = player[0].blockPosition();
			brain[0] = GuardianBrain.of(archer.rider());
			helper.assertTrue(brain[0].begin(MoveKind.LOB, player[0]), "the archer would not begin to lob");
		});
		rig.each(() -> {
			int now = brain[0].lobs().size();
			for (int i = counted[0]; i < now; i++) {
				burning[0] += rig.level.getEntity(brain[0].lobs().get(i)) instanceof AbstractArrow arrow && arrow.isOnFire() ? 1 : 0;
			}
			counted[0] = now;
			int lit = 0;
			for (BlockPos pos : BlockPos.betweenClosed(spot[0].offset(-7, -1, -7), spot[0].offset(7, 3, 7))) {
				lit += rig.level.getBlockState(pos).is(Blocks.FIRE) ? 1 : 0;
			}
			fires[0] = Math.max(fires[0], lit);
		});
		boolean[] over = {false};
		rig.each(() -> {
			if (over[0] || brain[0].detonations() < SPEC_LOB_ARROWS) {
				return;
			}
			over[0] = true;
			int craters = 0;
			for (BlockPos pos : BlockPos.betweenClosed(spot[0].offset(-7, -1, -7), spot[0].offset(7, -1, 7))) {
				craters += rig.level.getBlockState(pos).is(Blocks.STONE) ? 0 : 1;
			}
			TowerTestSupport.result("guardianLob", "lobbed=" + brain[0].lobs().size() + " burning=" + burning[0] + " bursts=" + brain[0].detonations()
				+ " mostFires=" + fires[0] + " craters=" + craters + " health=" + player[0].getHealth() + " alight=" + player[0].isOnFire());
			helper.assertTrue(brain[0].lobs().size() == SPEC_LOB_ARROWS && burning[0] == SPEC_LOB_ARROWS, "not all " + SPEC_LOB_ARROWS + " arrows were lobbed alight: "
				+ brain[0].lobs().size() + " lobbed, " + burning[0] + " burning");
			helper.assertTrue(craters == 0, "the bursts broke " + craters + " blocks of the floor");
			helper.assertTrue(fires[0] >= 3, "the bursts set only " + fires[0] + " blocks alight");
			helper.assertTrue(player[0].getHealth() < player[0].getMaxHealth() && player[0].isOnFire(), "the target stood in the bursts and was neither hurt nor burning");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the pair

	/**
	 * Catches a guardian hurt by the other's blow, arrow, or burst, and one that takes the other for its target. The archer
	 * lobs at a player who stands against the melee guardian, so the bursts fall on it.
	 */
	@GameTest(maxTicks = 900)
	public void guardiansNeverHurtOrHuntEachOther(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 11);
		Guardians.Spawned[] pair = new Guardians.Spawned[2];
		ServerPlayer[] bait = new ServerPlayer[1];
		GuardianBrain[] brains = new GuardianBrain[2];
		boolean[] hit = new boolean[3];
		float[] healths = new float[2];
		boolean[] hunted = {false};
		rig.ready(() -> {
			pair[0] = rig.spawn(GuardianKind.MELEE, -3, 0);
			pair[1] = rig.spawn(GuardianKind.RANGED, 12, 0);
			// The melee guardian strikes the bait all the while, so the bait must outlast the bursts.
			bait[0] = rig.sturdy(rig.player(-4, 1, 0.0F));
			brains[0] = GuardianBrain.of(pair[0].rider());
			brains[1] = GuardianBrain.of(pair[1].rider());
			healths[0] = pair[0].rider().getHealth();
			healths[1] = pair[0].horse().getHealth();
			hit[0] = pair[0].rider().hurtServer(rig.level, rig.level.damageSources().mobAttack(pair[1].rider()), 30.0F);
			hit[1] = pair[0].horse().hurtServer(rig.level, rig.level.damageSources().mobAttack(pair[1].rider()), 30.0F);
			hit[2] = pair[1].rider().hurtServer(rig.level, rig.level.damageSources().mobAttack(pair[0].rider()), 30.0F);
			helper.assertTrue(brains[1].begin(MoveKind.LOB, bait[0]), "the archer would not begin to lob");
		});
		rig.each(() -> {
			hunted[0] |= Guardians.isPart(brains[0].focus()) || Guardians.isPart(brains[1].focus());
			hunted[0] |= Guardians.isPart(pair[0].rider().getTarget()) || Guardians.isPart(pair[1].rider().getTarget());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brains[1] != null && brains[1].detonations() == SPEC_LOB_ARROWS, "only " + (brains[1] == null ? 0 : brains[1].detonations()) + " bursts have fallen");
			TowerTestSupport.result("guardianFriendlyFire", "hitRider=" + hit[0] + " hitHorse=" + hit[1] + " hitArcher=" + hit[2]
				+ " riderHealth=" + pair[0].rider().getHealth() + "/" + healths[0] + " horseHealth=" + pair[0].horse().getHealth() + "/" + healths[1]
				+ " archerHealth=" + pair[1].rider().getHealth() + "/" + pair[1].rider().getMaxHealth() + " hunted=" + hunted[0] + " baitHealth=" + bait[0].getHealth());
			helper.assertTrue(!hit[0] && !hit[1] && !hit[2], "one guardian's blow landed on another");
			helper.assertTrue(pair[0].rider().getHealth() == healths[0] && pair[0].horse().getHealth() == healths[1], "the bursts or arrows hurt the melee guardian or its horse");
			helper.assertTrue(pair[1].rider().getHealth() == pair[1].rider().getMaxHealth(), "the melee guardian hurt the archer");
			helper.assertTrue(!hunted[0], "a guardian took the other for its target");
			helper.assertTrue(bait[0].getHealth() < bait[0].getMaxHealth(), "the bursts did not reach the player, so the case proved nothing");
		});
	}

	// ---------------------------------------------------------------- deaths

	/** Catches a missing drop, a drop twice, a drop from the wrong guardian, and gear that falls with them. */
	@GameTest(maxTicks = 700)
	public void eachGuardianDropsItsOwnItemOnceAndNeverItsGear(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 12);
		Item shield = item(TowerGameTestInit.BONE_SHIELD);
		Item crown = item(TowerGameTestInit.SKELETON_CROWN);
		AABB around = new AABB(rig.center).inflate(24.0, 12.0, 24.0);
		Guardians.Spawned[] pair = new Guardians.Spawned[2];
		ServerPlayer[] killer = new ServerPlayer[1];
		int[] afterMelee = {-1, -1};
		rig.ready(() -> {
			pair[0] = rig.spawn(GuardianKind.MELEE, -6, 0);
			pair[1] = rig.spawn(GuardianKind.RANGED, 6, 0);
			killer[0] = rig.player(0, 0, 0.0F);
			pair[0].rider().setLastHurtByPlayer(killer[0], 100);
			pair[0].rider().kill(rig.level);
		});
		rig.after(4L, () -> {
			afterMelee[0] = count(rig, around, shield);
			afterMelee[1] = count(rig, around, crown);
			pair[1].rider().setLastHurtByPlayer(killer[0], 100);
			pair[1].rider().kill(rig.level);
		});
		rig.after(8L, () -> {
			int shields = count(rig, around, shield);
			int crowns = count(rig, around, crown);
			int gear = 0;
			for (ItemEntity entity : rig.level.getEntitiesOfClass(ItemEntity.class, around)) {
				String path = BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).getPath();
				gear += path.startsWith("netherite_") || entity.getItem().is(Items.BOW) || path.equals("rudra_bow") ? 1 : 0;
			}
			TowerTestSupport.result("guardianDrops", "shieldAfterMelee=" + afterMelee[0] + " crownAfterMelee=" + afterMelee[1] + " shields=" + shields
				+ " crowns=" + crowns + " gear=" + gear);
			helper.assertTrue(afterMelee[0] == 1 && afterMelee[1] == 0, "the melee guardian dropped " + afterMelee[0] + " Bone Shields and " + afterMelee[1] + " crowns");
			helper.assertTrue(shields == 1 && crowns == 1, "the pair dropped " + shields + " Bone Shields and " + crowns + " Skeleton Crowns, not one of each");
			helper.assertTrue(gear == 0, gear + " pieces of their gear fell");
			helper.succeed();
		});
	}

	private static int count(final GuardianRig rig, final AABB around, final Item item) {
		int total = 0;
		for (ItemEntity entity : rig.level.getEntitiesOfClass(ItemEntity.class, around)) {
			total += entity.getItem().is(item) ? entity.getItem().getCount() : 0;
		}
		return total;
	}

	/**
	 * Catches a horse that stays a guardian's, one that cannot be tamed or ridden, and one that loses its speed and jump, or
	 * keeps its size and its mark, once tamed.
	 */
	@GameTest(maxTicks = 700)
	public void aFallenRidersHorseCanBeTamedAndRiddenAndKeepsItsSpeedAndJump(final GameTestHelper helper) {
		GuardianRig rig = GuardianRig.build(helper, 13);
		Guardians.Spawned[] melee = new Guardians.Spawned[1];
		ServerPlayer[] player = new ServerPlayer[1];
		rig.ready(() -> {
			melee[0] = rig.spawn(GuardianKind.MELEE, -6, 0);
			player[0] = rig.player(0, 0, 0.0F);
			AbstractHorse horse = (AbstractHorse) melee[0].horse();
			helper.assertTrue(horse.isTamed(), "a guardian's horse starts untamed, so it would buck its rider");
			melee[0].rider().kill(rig.level);
			helper.assertTrue(horse.isAlive(), "the horse died with its rider");
			helper.assertTrue(!horse.isTamed() && horse.entityTags().contains(Guardians.TAG_FREED), "the fallen rider's horse is not free to tame");
			helper.assertTrue(horse.getAttributeBaseValue(Attributes.SCALE) == SPEC_SCALE, "the horse shrank before it was tamed");
			helper.assertTrue(player[0].startRiding(horse), "a player cannot get on the fallen rider's horse");
			helper.assertTrue(horse.tameWithName(player[0]), "the horse could not be tamed");
		});
		rig.after(6L, () -> {
			AbstractHorse horse = (AbstractHorse) melee[0].horse();
			double speed = horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
			double jump = horse.getAttributeBaseValue(Attributes.JUMP_STRENGTH);
			double scale = horse.getAttributeBaseValue(Attributes.SCALE);
			TowerTestSupport.result("guardianHorse", "tamed=" + horse.isTamed() + " speed=" + speed + " jump=" + jump + " scale=" + scale
				+ " guardian=" + Guardians.isPart(horse) + " riding=" + (horse.getFirstPassenger() == player[0]));
			helper.assertTrue(horse.isTamed() && horse.getFirstPassenger() == player[0], "the tamed horse is not ridden");
			helper.assertTrue(speed > SPEC_VANILLA_HORSE_SPEED, "the tamed horse is not faster than any vanilla horse: " + speed);
			helper.assertTrue(jump > SPEC_VANILLA_HORSE_JUMP, "the tamed horse does not jump higher than any vanilla horse: " + jump);
			helper.assertTrue(scale == 1.0, "the tamed horse is still " + scale + " times the size");
			helper.assertTrue(!Guardians.isPart(horse) && !horse.entityTags().contains(Guardians.TAG_FREED), "the tamed horse is still marked a guardian's");
			helper.succeed();
		});
	}
}
