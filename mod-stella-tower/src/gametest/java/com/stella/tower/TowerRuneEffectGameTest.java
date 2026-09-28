package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A weapon rune's signature, element by element. */
public class TowerRuneEffectGameTest {
	private static final int LOW = 5;
	/** A floor-1000 rune's power, so every signature shows plainly. */
	private static final int TOP = 1_000;

	private static ServerPlayer wielder(final GameTestHelper helper, final TowerElement element, final int floor) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, TowerRunes.apply(new ItemStack(Items.DIAMOND_SWORD), new RuneData(element, floor)));
		return player;
	}

	private static Zombie zombie(final GameTestHelper helper, final double x) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, LOW, TowerElement.WIND);
		zombie.snapTo(helper.absoluteVec(new Vec3(x, 2.0, 3.5)));
		zombie.setNoAi(true);
		// Sturdy enough to outlast the case's blows: a rune's mark is left by a hit that does not kill.
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(zombie.getMaxHealth());
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static void strike(final GameTestHelper helper, final ServerPlayer player, final Zombie zombie, final float amount) {
		zombie.damageCooldownTime = 0;
		zombie.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), amount);
	}

	/** Heat builds with each Fire hit; the fifth bursts for a share of the target's whole health. */
	@GameTest
	public void fireHeatBurstsAtItsFifthStack(final GameTestHelper helper) {
		int floor = 1;
		ServerPlayer player = wielder(helper, TowerElement.FIRE, floor);
		Zombie zombie = zombie(helper, 3.5);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		TowerMobs.makeTowerMob(zombie, LOW, TowerElement.WIND);
		float[] losses = new float[RuneEffects.HEAT_STACKS];
		int heatBefore = 0;
		for (int i = 0; i < RuneEffects.HEAT_STACKS; i++) {
			float before = zombie.getHealth();
			if (i == RuneEffects.HEAT_STACKS - 1) {
				heatBefore = RuneEffects.heatOf(zombie);
			}
			strike(helper, player, zombie, 1.0F);
			losses[i] = before - zombie.getHealth();
		}
		double burst = losses[RuneEffects.HEAT_STACKS - 1] - losses[0];
		double want = RuneEffects.combustShare(TowerRunes.power(floor)) * zombie.getMaxHealth();
		int heatAfter = RuneEffects.heatOf(zombie);
		TowerTestSupport.result("fireRune", "heatBefore=" + heatBefore + " burst=" + burst + " want=" + want + " heatAfter=" + heatAfter);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(heatBefore == RuneEffects.HEAT_STACKS - 1, "heat stood at " + heatBefore + " before the fifth hit");
		helper.assertTrue(Math.abs(burst - want) < want * 0.02 && heatAfter == 0, "the fifth hit burst for " + burst + ", not " + want + "; heat after " + heatAfter);
		helper.succeed();
	}

	@GameTest
	public void iceStrikesHarderAtTheSlowed(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, TowerElement.ICE, TOP);
		Zombie plain = zombie(helper, 3.5);
		Zombie slowed = zombie(helper, 5.5);
		slowed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
		float a = TowerMobs.scaleDamage(plain, player.damageSources().playerAttack(player), 10.0F);
		float b = TowerMobs.scaleDamage(slowed, player.damageSources().playerAttack(player), 10.0F);
		double want = 1.0 + RuneEffects.iceBonus(TowerRunes.power(TOP));
		TowerTestSupport.result("iceRune", "ratio=" + (b / a) + " want=" + want);
		plain.discard();
		slowed.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(b / a - want) < 1.0E-3, "ice struck the slowed at x" + (b / a) + ", not x" + want);
		helper.succeed();
	}

	@GameTest
	public void windStrikesHarderAtTheAirborne(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, TowerElement.WIND, TOP);
		Zombie grounded = zombie(helper, 3.5);
		Zombie flying = zombie(helper, 5.5);
		grounded.setOnGround(true);
		flying.setOnGround(false);
		float a = TowerMobs.scaleDamage(grounded, player.damageSources().playerAttack(player), 10.0F);
		float b = TowerMobs.scaleDamage(flying, player.damageSources().playerAttack(player), 10.0F);
		double want = 1.0 + RuneEffects.windBonus(TowerRunes.power(TOP));
		TowerTestSupport.result("windRune", "ratio=" + (b / a) + " want=" + want);
		grounded.discard();
		flying.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(b / a - want) < 1.0E-3, "wind struck the airborne at x" + (b / a) + ", not x" + want);
		helper.succeed();
	}

	/** A Water hit soaks: the wet take a quarter more from lightning, and their fire goes out. */
	@GameTest
	public void waterSoaksForLightning(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, TowerElement.WATER, TOP);
		Zombie dry = zombie(helper, 3.5);
		Zombie soaked = zombie(helper, 5.5);
		soaked.igniteForSeconds(5.0F);
		strike(helper, player, soaked, 0.01F);
		boolean wet = RuneEffects.isWet(soaked);
		boolean burning = soaked.isOnFire();
		float a = TowerMobs.scaleDamage(dry, helper.getLevel().damageSources().lightningBolt(), 10.0F);
		float b = TowerMobs.scaleDamage(soaked, helper.getLevel().damageSources().lightningBolt(), 10.0F);
		TowerTestSupport.result("waterRune", "wet=" + wet + " burning=" + burning + " ratio=" + (b / a));
		dry.discard();
		soaked.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(wet && !burning, "a water hit left wet=" + wet + " burning=" + burning);
		helper.assertTrue(Math.abs(b / a - RuneEffects.WET_LIGHTNING) < 1.0E-3, "lightning struck the wet at x" + (b / a));
		helper.succeed();
	}

	@GameTest
	public void voidShrinksWhatItStrikes(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, TowerElement.VOID, TOP);
		Zombie zombie = zombie(helper, 3.5);
		// Well under the game's cap of 1024, so the shrink shows in full.
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(zombie.getMaxHealth());
		double full = zombie.getMaxHealth();
		strike(helper, player, zombie, 0.01F);
		strike(helper, player, zombie, 0.01F);
		double twice = zombie.getMaxHealth() / full;
		for (int i = 0; i < 200; i++) {
			strike(helper, player, zombie, 0.01F);
		}
		double most = zombie.getMaxHealth() / full;
		double want = 1.0 - 2.0 * RuneEffects.shrinkPerHit(TowerRunes.power(TOP));
		TowerTestSupport.result("voidRune", "twice=" + twice + " want=" + want + " most=" + most);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(twice - want) < 1.0E-6, "two void hits left x" + twice + " of its health, not x" + want);
		helper.assertTrue(Math.abs(most - (1.0 - RuneEffects.SHRINK_CAP)) < 1.0E-6, "many void hits left x" + most + ", past the cap");
		helper.succeed();
	}

	@GameTest
	public void darkShredsArmorToItsCap(final GameTestHelper helper) {
		// A floor 1 rune shreds a little over one a hit, so ten stacks fit under the game's armor cap of 30.
		int floor = 1;
		ServerPlayer player = wielder(helper, TowerElement.DARK, floor);
		Zombie zombie = zombie(helper, 3.5);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(25.0);
		double before = zombie.getAttribute(Attributes.ARMOR).getValue();
		strike(helper, player, zombie, 0.01F);
		double once = before - zombie.getAttribute(Attributes.ARMOR).getValue();
		for (int i = 0; i < 20; i++) {
			strike(helper, player, zombie, 0.01F);
		}
		double most = before - zombie.getAttribute(Attributes.ARMOR).getValue();
		double per = RuneEffects.shredPerHit(TowerRunes.power(floor));
		TowerTestSupport.result("darkRune", "once=" + once + " per=" + per + " most=" + most);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(once - per) < 1.0E-6 && Math.abs(most - per * RuneEffects.SHRED_STACKS) < 1.0E-6,
			"dark shred " + once + " once and " + most + " at most, not " + per + " and " + per * RuneEffects.SHRED_STACKS);
		helper.succeed();
	}

	/** A Holy hit strikes one boss floor buff from a mob, and rests before the next. */
	@GameTest
	public void holyStripsABossFloorBuff(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.setItemInHand(InteractionHand.MAIN_HAND, TowerRunes.apply(new ItemStack(Items.DIAMOND_SWORD), new RuneData(TowerElement.HOLY, TOP)));
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.WIND);
		zombie.snapTo(player.position().add(1.0, 0.0, 0.0));
		zombie.setNoAi(true);
		tower.addFreshEntity(zombie);
		int before;
		int once;
		int twice;
		try {
			TowerBuffs.pinMobBuffs(floor, List.of(TowerBuffs.MobBuff.SPEED, TowerBuffs.MobBuff.STRENGTH));
			before = TowerBuffs.mobBuffsOf(zombie).size();
			zombie.damageCooldownTime = 0;
			zombie.hurtServer(tower, player.damageSources().playerAttack(player), 1.0F);
			once = TowerBuffs.mobBuffsOf(zombie).size();
			zombie.damageCooldownTime = 0;
			zombie.hurtServer(tower, player.damageSources().playerAttack(player), 1.0F);
			twice = TowerBuffs.mobBuffsOf(zombie).size();
		} finally {
			TowerBuffs.pinMobBuffs(floor, null);
		}
		TowerTestSupport.result("holyRune", "before=" + before + " once=" + once + " twice=" + twice);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(before == 2 && once == 1, "a holy hit left " + once + " of " + before + " buffs");
		helper.assertTrue(twice == 1, "a second holy hit within its rest left " + twice + " buffs");
		helper.succeed();
	}

	/** Out of the tower, a Holy hit strikes the strongest boon a mob holds. */
	@GameTest
	public void holyStripsAWorldMobsBoon(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, TowerElement.HOLY, TOP);
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		zombie.snapTo(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(zombie.getMaxHealth());
		level.addFreshEntity(zombie);
		zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
		zombie.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 2));
		strike(helper, player, zombie, 0.01F);
		boolean strength = zombie.hasEffect(MobEffects.STRENGTH);
		boolean speed = zombie.hasEffect(MobEffects.SPEED);
		TowerTestSupport.result("holyRuneWorld", "strength=" + strength + " speed=" + speed);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!strength && speed, "a holy hit left strength=" + strength + " speed=" + speed);
		helper.succeed();
	}

	@GameTest
	public void lightningStoresChargesToItsCap(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, TowerElement.LIGHTNING, TOP);
		Zombie zombie = zombie(helper, 3.5);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		strike(helper, player, zombie, 0.01F);
		int one = RuneEffects.chargesOf(player);
		for (int i = 0; i < 10; i++) {
			strike(helper, player, zombie, 0.01F);
		}
		int most = RuneEffects.chargesOf(player);
		TowerTestSupport.result("lightningRune", "one=" + one + " most=" + most);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(one == 1 && most == RuneEffects.MAX_CHARGES, "lightning stored " + one + " and at most " + most);
		helper.succeed();
	}
}
