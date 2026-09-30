package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** An armor rune's signature, element by element. */
public class TowerRuneArmorGameTest {
	private static final int LOW = 5;
	private static final int TOP = 1_000;

	private static ServerPlayer wearer(final GameTestHelper helper, final TowerElement element) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		TowerTestSupport.vulnerable(player);
		player.setItemSlot(EquipmentSlot.CHEST, TowerRunes.apply(new ItemStack(Items.IRON_CHESTPLATE), new RuneData(element, TOP)));
		return player;
	}

	private static Zombie zombie(final GameTestHelper helper, final int floor, final TowerRank rank) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.WIND, rank);
		zombie.snapTo(helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)));
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		return zombie;
	}

	/** A mob of the world, not the tower's: every signature works on it too. */
	private static Zombie plain(final GameTestHelper helper, final double x) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		zombie.snapTo(helper.absoluteVec(new Vec3(x, 2.0, 4.5)));
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		return zombie;
	}

	/** Fire armor stores the fire it takes; the next blow that lands carries it, unmultiplied by the weapon's rune. */
	@GameTest
	public void fireArmorStoresHeatForTheNextBlow(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, TowerElement.FIRE);
		player.hurtServer(level, level.damageSources().inFire(), 4.0F);
		float stored = RuneGuards.storedHeat(player);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, TowerRunes.apply(new ItemStack(Items.DIAMOND_SWORD), new RuneData(TowerElement.ICE, TOP)));
		net.minecraft.world.entity.animal.pig.Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
		pig.snapTo(helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)));
		pig.setNoAi(true);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		pig.setHealth(pig.getMaxHealth());
		level.addFreshEntity(pig);
		// A blow turned aside spends nothing.
		// Within the cooldown of a greater blow, a lesser one is turned aside whole.
		pig.damageCooldownTime = 20;
		((com.stella.tower.mixin.LivingEntityAccessor) pig).stellaTower$setLastHurt(1.0E6F);
		pig.hurtServer(level, player.damageSources().playerAttack(player), 1.0F);
		float kept = RuneGuards.storedHeat(player);
		pig.damageCooldownTime = 0;
		float before = pig.getHealth();
		pig.hurtServer(level, player.damageSources().playerAttack(player), 1.0F);
		double blow = before - pig.getHealth();
		double want = TowerRunes.weaponMultiplier(TOP) + stored;
		float after = RuneGuards.storedHeat(player);
		TowerTestSupport.result("fireArmor", "stored=" + stored + " kept=" + kept + " blow=" + blow + " want=" + want + " after=" + after);
		pig.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(stored > 0.0F, "fire armor stored no heat from a burn");
		helper.assertTrue(kept == stored, "a blow turned aside spent the heat: " + kept + " of " + stored + " left");
		helper.assertTrue(Math.abs(blow - want) < want * 1.0E-4 && after == 0.0F, "the next blow was " + blow + ", not " + want + "; heat after " + after);
		helper.succeed();
	}

	/** A mob wearing runed armor gains none of its signatures: a zombie in Lightning armor still takes a bolt. */
	@GameTest
	public void onlyAPlayerCarriesArmorSignatures(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = plain(helper, 4.5);
		zombie.setItemSlot(EquipmentSlot.CHEST, TowerRunes.apply(new ItemStack(Items.IRON_CHESTPLATE), new RuneData(TowerElement.LIGHTNING, TOP)));
		float before = zombie.getHealth();
		zombie.hurtServer(level, level.damageSources().lightningBolt(), 5.0F);
		float lost = before - zombie.getHealth();
		TowerTestSupport.result("mobWearer", "lost=" + lost);
		zombie.discard();
		helper.assertTrue(lost > 0.0F, "a zombie in lightning armor shrugged off a bolt");
		helper.succeed();
	}

	/** A spread blow is saved with the player, so logging out does not shed it. */
	@GameTest
	public void aPendingWoundIsSavedWithThePlayer(final GameTestHelper helper) {
		boolean saved = RuneGuards.WOUND.isPersistent();
		TowerTestSupport.result("woundSaved", "persistent=" + saved);
		helper.assertTrue(saved, "a pending wound is not saved with the player");
		helper.succeed();
	}

	@GameTest
	public void iceArmorHalvesSlowAndFrost(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, TowerElement.ICE);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
		int slow = player.getEffect(MobEffects.SLOWNESS).getDuration();
		player.setTicksFrozen(100);
		int frost = player.getTicksFrozen();
		TowerTestSupport.result("iceArmor", "slow=" + slow + " frost=" + frost);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(slow == 50 && frost == 50, "ice armor left slowness " + slow + " and frost " + frost + ", not half of 100");
		helper.succeed();
	}

	/** Water armor takes a big blow a quarter now, the rest spread over three seconds. */
	@GameTest(maxTicks = 120)
	public void waterArmorSpreadsABigBlow(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, TowerElement.WATER);
		Zombie zombie = zombie(helper, LOW, TowerRank.MOB);
		float before = player.getHealth();
		float blow = 12.0F;
		double landed = TowerCurve.damage(blow, LOW) / Math.pow(TowerRunes.armorDivisor(TOP), 1);
		player.hurtServer(level, zombie.damageSources().mobAttack(zombie), blow);
		float now = before - player.getHealth();
		float pending = RuneGuards.pendingWound(player);
		helper.runAfterDelay((long) RuneGuards.SPREAD_PARTS * RuneGuards.SPREAD_TICKS + 20L, () -> {
			float total = before - player.getHealth();
			TowerTestSupport.result("waterArmor", "now=" + now + " pending=" + pending + " total=" + total + " landed=" + landed);
			zombie.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(Math.abs(now - landed * RuneGuards.BIG_HIT_NOW) < 0.01, "the big blow struck " + now + " at once, not a quarter of " + landed);
			helper.assertTrue(Math.abs(total - landed) < 0.05 && RuneGuards.pendingWound(player) == 0.0F, "the spread blow came to " + total + ", not " + landed);
			helper.succeed();
		});
	}

	/** The spread part of a blow meets the wearer's resistance as the first part did, not in full. */
	@GameTest(maxTicks = 120)
	public void waterArmorsSpreadBlowMeetsResistance(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, TowerElement.WATER);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 2000, 1));
		Zombie zombie = zombie(helper, LOW, TowerRank.MOB);
		float before = player.getHealth();
		float blow = 12.0F;
		double landed = TowerCurve.damage(blow, LOW) / TowerRunes.armorDivisor(TOP);
		// Resistance II turns 40% of every blow.
		double want = landed * (1.0 - 0.4);
		player.hurtServer(level, zombie.damageSources().mobAttack(zombie), blow);
		helper.runAfterDelay((long) RuneGuards.SPREAD_PARTS * RuneGuards.SPREAD_TICKS + 20L, () -> {
			float total = before - player.getHealth();
			TowerTestSupport.result("waterResisted", "total=" + total + " want=" + want + " landed=" + landed);
			zombie.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(Math.abs(total - want) < 0.05, "a resisted spread blow came to " + total + ", not " + want);
			helper.succeed();
		});
	}

	@GameTest
	public void lightningArmorShrugsOffLightningAndStoresCharges(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, TowerElement.LIGHTNING);
		float before = player.getHealth();
		player.hurtServer(level, level.damageSources().lightningBolt(), 5.0F);
		float lost = before - player.getHealth();
		Zombie zombie = plain(helper, 4.5);
		player.damageCooldownTime = 0;
		player.hurtServer(level, zombie.damageSources().mobAttack(zombie), 1.0F);
		int charges = RuneEffects.chargesOf(player);
		TowerTestSupport.result("lightningArmor", "lost=" + lost + " charges=" + charges);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(lost == 0.0F && charges == 1, "lightning armor lost " + lost + " to a bolt and stored " + charges);
		helper.succeed();
	}

	@GameTest
	public void windArmorTurnsShots(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, TowerElement.WIND);
		net.minecraft.world.entity.projectile.arrow.Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
		float shot = TowerMobs.scaleDamage(player, level.damageSources().arrow(arrow, null), 10.0F);
		float blow = TowerMobs.scaleDamage(player, level.damageSources().generic(), 10.0F);
		double want = 10.0 * (1.0 - RuneGuards.windCut(TowerRunes.power(TOP)));
		TowerTestSupport.result("windArmor", "shot=" + shot + " want=" + want + " blow=" + blow);
		arrow.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(shot - want) < 1.0E-3 && blow == 10.0F, "wind armor let a shot through at " + shot + ", not " + want + "; a plain blow at " + blow);
		helper.succeed();
	}

	@GameTest
	public void holyArmorShortensHarm(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, TowerElement.HOLY);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0));
		int poison = player.getEffect(MobEffects.POISON).getDuration();
		int speed = player.getEffect(MobEffects.SPEED).getDuration();
		int want = (int) Math.round(100 * (1.0 - RuneGuards.holyCut(TowerRunes.power(TOP))));
		TowerTestSupport.result("holyArmor", "poison=" + poison + " want=" + want + " speed=" + speed);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(poison == want && speed == 100, "holy armor left poison " + poison + " (not " + want + ") and speed " + speed);
		helper.succeed();
	}

	/** Dark armor weakens a mob's blow by each mob its wearer has slain of late, anywhere. */
	@GameTest
	public void darkArmorWeakensMobsByTheRecentlySlain(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, TowerElement.DARK);
		int slain = 7;
		// Chickens are no foes: they do not count.
		for (int i = 0; i < 3; i++) {
			net.minecraft.world.entity.animal.chicken.Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
			chicken.snapTo(helper.absoluteVec(new Vec3(4.5, 2.0, 6.5)));
			level.addFreshEntity(chicken);
			chicken.hurtServer(level, player.damageSources().playerAttack(player), 1.0E30F);
		}
		for (int i = 0; i < slain; i++) {
			Zombie victim = plain(helper, 4.5);
			victim.hurtServer(level, player.damageSources().playerAttack(player), 1.0E30F);
		}
		Zombie attacker = plain(helper, 6.5);
		float blow = TowerMobs.scaleDamage(player, attacker.damageSources().mobAttack(attacker), 3.0F);
		double want = 3.0 * (1.0 - Math.min(RuneGuards.DARK_CAP, RuneGuards.DARK_PER_KILL * slain));
		int kills = RuneGuards.recentKills(player);
		TowerTestSupport.result("darkArmor", "kills=" + kills + " blow=" + blow + " want=" + want);
		attacker.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(kills == slain && Math.abs(blow - want) < want * 1.0E-4, "dark armor let a blow through at " + blow + ", not " + want + " after " + kills + " kills");
		helper.succeed();
	}

	/** Void armor halves a boon given to a hostile mob near its wearer, in or out of the tower. */
	@GameTest
	public void voidArmorHalvesAWorldMobsBoon(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, TowerElement.VOID);
		Zombie near = plain(helper, 4.5);
		near.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 4));
		int amplifier = near.getEffect(MobEffects.STRENGTH).getAmplifier();
		TowerTestSupport.result("voidArmorWorld", "amplifier=" + amplifier);
		near.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(amplifier == 4 / 2, "a zombie beside void armor holds strength amplifier " + amplifier + ", not " + 4 / 2);
		helper.succeed();
	}

	/** Void armor halves the boss-floor buffs of the mobs near its wearer. */
	@GameTest(maxTicks = 100)
	public void voidArmorHalvesNearbyBossBuffs(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		// The mob beside the arrival zone, not in it: a tower mob inside a zone is swept out within five ticks.
		TowerTestSupport.ZoneSpots spots = TowerTestSupport.spotsBesideZone(TowerPlan.at(tower, floor));
		player.teleportTo(tower, spots.outside().x, spots.outside().y, spots.outside().z, java.util.Set.of(), 0.0F, 0.0F, true);
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		player.setItemSlot(EquipmentSlot.CHEST, TowerRunes.apply(new ItemStack(Items.IRON_CHESTPLATE), new RuneData(TowerElement.VOID, TOP)));
		int own = 450;
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, own, TowerElement.WIND);
		zombie.snapTo(spots.mob());
		zombie.setNoAi(true);
		tower.addFreshEntity(zombie);
		TowerBuffs.pinMobBuffs(own, List.of(TowerBuffs.MobBuff.STRENGTH, TowerBuffs.MobBuff.SPEED));
		helper.runAfterDelay(TowerBuffs.REFRESH_TICKS * 2L, () -> {
			MobEffectInstance strength;
			try {
				strength = zombie.getEffect(MobEffects.STRENGTH);
			} finally {
				TowerBuffs.pinMobBuffs(own, null);
			}
			int full = TowerBuffs.MobBuff.STRENGTH.amplifier(own);
			TowerTestSupport.result("voidArmor", "strength=" + strength + " full=" + full);
			zombie.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(full > 1, "the buff is too weak to halve, so the case proves nothing");
			helper.assertTrue(strength != null && strength.getAmplifier() == full / 2, "a mob beside void armor holds strength " + strength + ", not " + full / 2);
			helper.succeed();
		});
	}
}
