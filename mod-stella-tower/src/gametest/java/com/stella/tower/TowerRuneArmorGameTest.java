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

	/** Fire armor stores the fire it takes, and the wearer's next blow carries it. */
	@GameTest
	public void fireArmorStoresHeatForTheNextBlow(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, TowerElement.FIRE);
		player.hurtServer(level, level.damageSources().inFire(), 4.0F);
		float stored = RuneGuards.storedHeat(player);
		Zombie zombie = zombie(helper, LOW, TowerRank.MOB);
		double divisor = TowerMobs.stateOf(zombie).healthDivisor() * TowerBuffs.healthFactor(zombie);
		double blow = TowerMobs.scaleDamage(zombie, player.damageSources().playerAttack(player), 1.0F) * divisor;
		float after = RuneGuards.storedHeat(player);
		TowerTestSupport.result("fireArmor", "stored=" + stored + " blow=" + blow + " after=" + after);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(stored > 0.0F, "fire armor stored no heat from a burn");
		helper.assertTrue(Math.abs(blow - (1.0 + stored)) < 1.0E-3 && after == 0.0F, "the next blow was " + blow + ", not " + (1.0 + stored) + "; heat after " + after);
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
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		player.setItemSlot(EquipmentSlot.CHEST, TowerRunes.apply(new ItemStack(Items.IRON_CHESTPLATE), new RuneData(TowerElement.VOID, TOP)));
		int own = 450;
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, own, TowerElement.WIND);
		zombie.snapTo(player.position().add(1.0, 0.0, 0.0));
		zombie.setNoAi(true);
		tower.addFreshEntity(zombie);
		TowerBuffs.pinMobBuffs(own, List.of(TowerBuffs.MobBuff.STRENGTH, TowerBuffs.MobBuff.SPEED));
		helper.runAfterDelay(TowerBuffs.REFRESH_TICKS * 2L, () -> {
			TowerBuffs.pinMobBuffs(own, null);
			MobEffectInstance strength = zombie.getEffect(MobEffects.STRENGTH);
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
