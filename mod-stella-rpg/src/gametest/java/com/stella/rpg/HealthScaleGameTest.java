package com.stella.rpg;

import com.stella.rpg.api.HealthScaleShare;
import com.stella.rpg.api.OutgoingDamageShare;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** The health scale K: uncapped attributes, K on max health, damage, and heals, percent effects, and shares. */
public final class HealthScaleGameTest {
	private static final float TOLERANCE = 0.01F;
	private static final double VANILLA_BASE_300 = 20.0;
	private static final double VANILLA_BASE_3000 = 200.0;

	@GameTest
	public void attributesUncapped(final GameTestHelper helper) {
		List<String> caps = new ArrayList<>();
		for (Holder<Attribute> holder : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MAX_ABSORPTION)) {
			double max = ((RangedAttribute) holder.value()).getMaxValue();
			caps.add(holder.value().getDescriptionId() + "=" + max);
			helper.assertTrue(max == RpgScale.ATTRIBUTE_CAP, "cap of " + holder.value().getDescriptionId() + " is " + max);
		}
		helper.assertTrue(RpgScale.ATTRIBUTE_CAP == 1_000_000.0, "the shipped cap is " + RpgScale.ATTRIBUTE_CAP);
		Zombie zombie = zombie(helper);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50_000.0);
		zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5_000.0);
		helper.assertTrue(zombie.getMaxHealth() == 50_000.0F * RpgScale.kf(), "max health clamped to " + zombie.getMaxHealth());
		helper.assertTrue(zombie.getAttributeValue(Attributes.ATTACK_DAMAGE) == 5_000.0,
			"attack damage clamped to " + zombie.getAttributeValue(Attributes.ATTACK_DAMAGE));
		RpgTestSupport.result("attributesUncapped", String.join(" ", caps) + " zombieMax=" + zombie.getMaxHealth());
		helper.succeed();
	}

	@GameTest
	public void maxHealthTimesK(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Zombie zombie = zombie(helper);
		float expected = 20.0F * RpgScale.kf();
		float playerExpected = 30.0F * RpgScale.kf();
		RpgTestSupport.result("maxHealthTimesK", "player=" + player.getMaxHealth() + " zombie=" + zombie.getMaxHealth()
			+ " health=" + player.getHealth() + "/" + zombie.getHealth());
		helper.assertTrue(player.getMaxHealth() == playerExpected && playerExpected == 450.0F, "player max health " + player.getMaxHealth());
		helper.assertTrue(zombie.getMaxHealth() == expected, "zombie max health " + zombie.getMaxHealth());
		helper.assertTrue(player.getHealth() == playerExpected && zombie.getHealth() == expected,
			"health " + player.getHealth() + " " + zombie.getHealth());
		helper.succeed();
	}

	@GameTest
	public void damageTimesKAfterMitigation(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		Zombie zombie = zombie(helper);
		DamageSource generic = helper.getLevel().damageSources().generic();
		float playerLoss = loss(player, () -> player.hurtServer(helper.getLevel(), generic, 6.0F));
		float zombieLoss = loss(zombie, () -> zombie.hurtServer(helper.getLevel(), generic, 6.0F));
		float zombieExpected = RpgTestSupport.mobGuard(6.0F) * RpgScale.kf();
		RpgTestSupport.result("damageTimesKAfterMitigation", "player=" + playerLoss + " zombie=" + zombieLoss
			+ " zombieExpected=" + zombieExpected);
		helper.assertTrue(near(playerLoss, 90.0F), "a stat-less player lost " + playerLoss);
		helper.assertTrue(near(zombieLoss, zombieExpected), "the zombie lost " + zombieLoss + " expected " + zombieExpected);
		helper.succeed();
	}

	@GameTest
	public void armorMathUnchanged(final GameTestHelper helper) {
		Difficulty before = helper.getLevel().getDifficulty();
		float loss;
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			ServerPlayer player = RpgTestSupport.realPlayer(helper);
			RpgTestSupport.bare(player);
			player.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
			Zombie attacker = zombie(helper);
			RpgCombat.setRoller((roller, random, chance) -> false);
			RpgCombat.setDiceRoller((roller, random, dice) -> 0);
			try {
				loss = loss(player, () -> player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 10.0F));
			} finally {
				RpgCombat.setRoller(null);
				RpgCombat.setDiceRoller(null);
			}
		} finally {
			helper.getLevel().getServer().setDifficulty(before, true);
		}
		float hit = 10.0F + (float) RpgCombat.statusAtk(MobStats.base(RpgConfig.defaults(), MobStats.MELEE, Stat.STR, 0));
		float armor = Math.max(20.0F * 0.2F, Math.min(20.0F, 20.0F - hit / 2.0F));
		float afterArmor = hit * (1.0F - armor / 25.0F);
		RpgTestSupport.result("armorMathUnchanged", "loss=" + loss + " vanillaAfterArmor=" + afterArmor + " K=" + RpgScale.k());
		helper.assertTrue(near(loss, afterArmor * RpgScale.kf()), "loss=" + loss + " expected 15 x " + afterArmor);
		helper.succeed();
	}

	@GameTest
	public void healTimesK(final GameTestHelper helper) {
		Creeper creeper = creeper(helper);
		creeper.setHealth(100.0F);
		creeper.heal(4.0F);
		float direct = creeper.getHealth() - 100.0F;

		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.setHealth(100.0F);
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(0.0F);
		for (int tick = 0; tick < 80; tick++) {
			player.getFoodData().tick(player);
		}
		float natural = player.getHealth() - 100.0F;

		creeper.setHealth(100.0F);
		MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), null, null, creeper, 0, 1.0);
		float instant = creeper.getHealth() - 100.0F;

		RpgTestSupport.result("healTimesK", "direct=" + direct + " natural=" + natural + " instant=" + instant);
		helper.assertTrue(near(direct, 60.0F), "heal(4) restored " + direct);
		helper.assertTrue(near(natural, RpgScale.kf()), "natural regeneration restored " + natural);
		helper.assertTrue(near(instant, 60.0F), "Instant Health I restored " + instant);
		helper.succeed();
	}

	@GameTest
	public void percentDamageOverTime(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		StringBuilder log = new StringBuilder();
		for (double base : new double[] {VANILLA_BASE_300, VANILLA_BASE_3000}) {
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(base);
			float max = player.getMaxHealth();
			float expected = RpgScale.kf() * (float) Math.max(1.0, 0.03 * max / RpgScale.k());
			float poison = tickLoss(helper, player, MobEffects.POISON);
			float wither = tickLoss(helper, player, MobEffects.WITHER);
			player.setHealth(1.0F);
			player.damageCooldownTime = 0;
			MobEffects.REGENERATION.value().applyEffectTick(helper.getLevel(), player, 0);
			float regen = player.getHealth() - 1.0F;
			log.append("max=").append(max).append(" expected=").append(expected).append(" poison=").append(poison)
				.append(" wither=").append(wither).append(" regen=").append(regen).append(' ');
			helper.assertTrue(near(poison, expected), "poison removed " + poison + " at " + max + " expected " + expected);
			helper.assertTrue(near(wither, expected), "wither removed " + wither + " at " + max + " expected " + expected);
			helper.assertTrue(near(regen, expected), "regeneration healed " + regen + " at " + max + " expected " + expected);
		}
		RpgTestSupport.result("percentDamageOverTime", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void poisonNeverKillsWitherCan(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		player.setHealth(5.0F);
		player.damageCooldownTime = 0;
		MobEffects.POISON.value().applyEffectTick(helper.getLevel(), player, 0);
		float afterFirst = player.getHealth();
		player.damageCooldownTime = 0;
		MobEffects.POISON.value().applyEffectTick(helper.getLevel(), player, 0);
		float afterSecond = player.getHealth();

		Zombie zombie = zombie(helper);
		zombie.setHealth(5.0F);
		zombie.damageCooldownTime = 0;
		MobEffects.WITHER.value().applyEffectTick(helper.getLevel(), zombie, 0);
		RpgTestSupport.result("poisonNeverKillsWitherCan", "poison=" + afterFirst + "," + afterSecond
			+ " witherHealth=" + zombie.getHealth() + " witherDead=" + zombie.isDeadOrDying());
		helper.assertTrue(afterFirst > 0.99F && afterFirst < 1.01F, "poison left " + afterFirst + " health from 5");
		helper.assertTrue(afterSecond == afterFirst && !player.isDeadOrDying(), "a second poison tick changed health to " + afterSecond);
		helper.assertTrue(zombie.isDeadOrDying(), "a wither tick at health 5 left " + zombie.getHealth());
		helper.succeed();
	}

	@GameTest
	public void percentInstantAndAbsorption(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		StringBuilder log = new StringBuilder();
		for (double base : new double[] {VANILLA_BASE_300, VANILLA_BASE_3000}) {
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(base);
			float max = player.getMaxHealth();
			for (int amplifier = 0; amplifier < 2; amplifier++) {
				float doubling = 1 << amplifier;
				float heal = instantHeal(helper, player, amplifier);
				float harm = instantHarm(helper, player, amplifier);
				float absorption = absorptionGrant(player, amplifier);
				float expectedHeal = doubling * (float) Math.max(60.0, 0.15 * max);
				// A bare player resists 25 percent of magic.
				float expectedHarm = doubling * (float) Math.max(90.0, 0.20 * max) * 0.75F;
				float expectedAbsorption = (1 + amplifier) * (float) Math.max(60.0, 0.10 * max);
				log.append("max=").append(max).append(" amp=").append(amplifier).append(" heal=").append(heal)
					.append(" harm=").append(harm).append(" absorption=").append(absorption).append(' ');
				helper.assertTrue(near(heal, expectedHeal), "Instant Health " + amplifier + " healed " + heal + " at " + max + " expected " + expectedHeal);
				helper.assertTrue(near(harm, expectedHarm), "Instant Damage " + amplifier + " dealt " + harm + " at " + max + " expected " + expectedHarm);
				helper.assertTrue(near(absorption, expectedAbsorption),
					"Absorption " + amplifier + " granted " + absorption + " at " + max + " expected " + expectedAbsorption);
			}
		}
		RpgTestSupport.result("percentInstantAndAbsorption", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void bossesTakeQuarterPercent(final GameTestHelper helper) {
		MobsBridge.setFacts(null);
		try {
			Creeper normal = bigCreeper(helper);
			Creeper elite = bigCreeper(helper);
			elite.addTag("test.elite");
			Creeper boss = bigCreeper(helper);
			boss.addTag("test.boss");
			Creeper small = creeper(helper);
			small.addTag("test.elite");
			helper.assertTrue(!MobsBridge.eliteOrBoss(normal) && MobsBridge.eliteOrBoss(elite) && MobsBridge.eliteOrBoss(boss),
				"the bridge did not read the elite and boss flags");
			float max = normal.getMaxHealth();
			float k = RpgScale.kf();
			StringBuilder log = new StringBuilder("max=" + max + " ");
			for (Creeper target : List.of(elite, boss)) {
				float regen = healOf(helper, target, () -> MobEffects.REGENERATION.value().applyEffectTick(helper.getLevel(), target, 0));
				float heal = healOf(helper, target, () -> MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), null, null, target, 0, 1.0));
				float harm = loss(target, () -> MobEffects.INSTANT_DAMAGE.value().applyInstantaneousEffect(helper.getLevel(), null, null, target, 0, 1.0));
				float poison = tickLoss(helper, target, MobEffects.POISON);
				float wither = tickLoss(helper, target, MobEffects.WITHER);
				float absorption = absorptionGrant(target, 0);
				float quarterTick = (float) (0.03 * 0.25 * max / k);
				helper.assertTrue(near(regen, quarterTick * k), "regeneration on a " + target.entityTags() + " healed " + regen);
				helper.assertTrue(near(heal, (float) (0.15 * 0.25 * max)), "Instant Health healed " + heal);
				helper.assertTrue(near(harm, RpgTestSupport.mobMagicGuard((float) (0.20 * 0.25 * max / k)) * k), "Instant Damage dealt " + harm);
				helper.assertTrue(near(poison, RpgTestSupport.mobGuard(quarterTick) * k), "poison removed " + poison);
				helper.assertTrue(near(wither, RpgTestSupport.mobGuard(quarterTick) * k), "wither removed " + wither);
				helper.assertTrue(near(absorption, (float) (0.10 * 0.25 * max)), "Absorption granted " + absorption);
				log.append("regen=").append(regen).append(" heal=").append(heal).append(" harm=").append(harm)
					.append(" poison=").append(poison).append(" wither=").append(wither).append(" absorption=").append(absorption).append(' ');
			}
			float normalHeal = healOf(helper, normal, () -> MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), null, null, normal, 0, 1.0));
			helper.assertTrue(near(normalHeal, (float) (0.15 * max)), "a plain mob healed " + normalHeal);
			float floorHeal = healOf(helper, small, () -> MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), null, null, small, 0, 1.0));
			helper.assertTrue(near(floorHeal, 60.0F), "the flat floor changed to " + floorHeal + " for a small elite");
			RpgTestSupport.result("bossesTakeQuarterPercent", log + "normalHeal=" + normalHeal + " floorHeal=" + floorHeal);
		} finally {
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void environmentPercent(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		DamageSource fall = helper.getLevel().damageSources().fall();
		float plain = loss(player, () -> player.causeFallDamage(10.0, 1.0F, fall));
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2137.0 / RpgScale.k());
		player.setHealth(player.getMaxHealth());
		float scaled = loss(player, () -> player.causeFallDamage(10.0, 1.0F, fall));
		float share = scaled / player.getMaxHealth();
		Zombie zombie = bigZombie(helper);
		float mobLoss = loss(zombie, () -> zombie.hurtServer(helper.getLevel(), fall, 7.0F));
		HolderSet.Named<DamageType> tag = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
			.getOrThrow(RpgDamageTypes.ENVIRONMENTAL);
		RpgTestSupport.result("environmentPercent", "plain=" + plain + " scaled=" + scaled + " share=" + share
			+ " mob=" + mobLoss + " tag=" + tag.size());
		helper.assertTrue(near(plain, 7.0F * 1.5F * RpgScale.kf()), "a fall at 450 max health removed " + plain);
		helper.assertTrue(Math.abs(share - 0.35F) < 0.001F, "a 10 block fall at 2137 max health removed " + share + " of it");
		helper.assertTrue(near(mobLoss, RpgTestSupport.mobGuard(7.0F) * RpgScale.kf()), "a mob fall removed " + mobLoss);
		helper.assertTrue(tag.size() == 13, "the environmental tag holds " + tag.size() + " types");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("unchecked")
	public void shareIncludesK(final GameTestHelper helper) {
		Object outgoing = FabricLoader.getInstance().getObjectShare().get(OutgoingDamageShare.OBJECT_SHARE_KEY);
		Object scale = FabricLoader.getInstance().getObjectShare().get(HealthScaleShare.OBJECT_SHARE_KEY);
		helper.assertTrue(outgoing instanceof Function<?, ?>, "the outgoing damage share is missing");
		helper.assertTrue(scale instanceof Double && (Double) scale == 15.0, "the health-scale share is " + scale);
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Zombie victim = zombie(helper);
		DamageSource source = helper.getLevel().damageSources().playerAttack(player);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		float viaShare;
		float direct;
		try {
			viaShare = ((Function<Object[], Float>) outgoing).apply(new Object[] {victim, source, 5.0F});
			direct = RpgCombat.scaleOutgoing(victim, source, 5.0F);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		RpgTestSupport.result("shareIncludesK", "share=" + viaShare + " direct=" + direct + " k=" + scale);
		helper.assertTrue(direct > 5.0F && viaShare == direct * 15.0F, "share=" + viaShare + " direct=" + direct);
		helper.succeed();
	}

	@GameTest
	public void shareFollowsConfig(final GameTestHelper helper) {
		RpgConfig before = RpgConfigStore.current();
		RpgConfig doubled = new RpgConfig(before.curves(), before.critMultiplier(), before.dice(), before.mobStats(),
			before.mobAmplifier(), before.armorSets(), before.weaponRolls(), 10.0, before.mobDiceShare(), before.tables(),
			before.xpCurve(), before.magic(), before.scaling(), before.hp(), before.materialRolls());
		Object published;
		try {
			RpgConfigStore.set(doubled);
			HealthScaleShare.publish();
			published = FabricLoader.getInstance().getObjectShare().get(HealthScaleShare.OBJECT_SHARE_KEY);
			helper.assertTrue(RpgScale.k() == 10.0, "K is " + RpgScale.k());
		} finally {
			RpgConfigStore.set(before);
			HealthScaleShare.publish();
		}
		List<String> errors = new ArrayList<>();
		double parsed = RpgConfigStore.parse("{\"health_scale\": 10}", errors::add).healthScale();
		double bad = RpgConfigStore.parse("{\"health_scale\": 0.5}", errors::add).healthScale();
		RpgTestSupport.result("shareFollowsConfig", "published=" + published + " parsed=" + parsed + " bad=" + bad + " errors=" + errors.size());
		helper.assertTrue(published instanceof Double && (Double) published == 10.0, "published " + published);
		helper.assertTrue(parsed == 10.0 && bad == RpgConfig.DEFAULT_HEALTH_SCALE && errors.size() == 1, "parsed=" + parsed + " bad=" + bad + " errors=" + errors);
		helper.assertTrue(FabricLoader.getInstance().getObjectShare().get(HealthScaleShare.OBJECT_SHARE_KEY).equals(15.0), "the share was not restored");
		helper.succeed();
	}

	@GameTest
	public void loadedEntityKeepsFraction(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		helper.assertTrue(RpgScale.hasModifier(zombie, Attributes.MAX_HEALTH), "a spawned zombie has no K modifier");
		AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance absorption = zombie.getAttribute(Attributes.MAX_ABSORPTION);
		health.removeModifier(RpgScale.MODIFIER_ID);
		absorption.removeModifier(RpgScale.MODIFIER_ID);
		absorption.setBaseValue(4.0);
		zombie.setHealth(5.0F);
		zombie.setAbsorptionAmount(2.0F);
		float oldMax = zombie.getMaxHealth();
		RpgScale.apply(zombie);
		float healthAfter = zombie.getHealth();
		float maxAfter = zombie.getMaxHealth();
		float absorptionAfter = zombie.getAbsorptionAmount();
		RpgScale.apply(zombie);
		RpgScale.apply(zombie);
		RpgTestSupport.result("loadedEntityKeepsFraction", "oldMax=" + oldMax + " health=" + healthAfter + "/" + maxAfter
			+ " absorption=" + absorptionAfter + " again=" + zombie.getHealth() + "/" + zombie.getMaxHealth()
			+ " modifiers=" + health.getModifiers().size());
		helper.assertTrue(oldMax == 20.0F && near(maxAfter, 300.0F), "max health " + oldMax + " -> " + maxAfter);
		helper.assertTrue(near(healthAfter, 75.0F), "a quarter of health became " + healthAfter);
		helper.assertTrue(near(absorptionAfter, 30.0F), "half of absorption became " + absorptionAfter);
		helper.assertTrue(near(zombie.getHealth(), 75.0F) && near(zombie.getMaxHealth(), 300.0F), "a second apply changed the entity");
		long scaleModifiers = health.getModifiers().stream().filter(modifier -> modifier.id().equals(RpgScale.MODIFIER_ID)).count();
		helper.assertTrue(scaleModifiers == 1 && health.getBaseValue() == 20.0, "the modifier count or base changed");
		helper.succeed();
	}

	@GameTest
	public void armorStandBreaksAtScale(final GameTestHelper helper) {
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, 1, 2, 1);
		float max = stand.getMaxHealth();
		float burn = loss(stand, () -> stand.hurtServer(helper.getLevel(), helper.getLevel().damageSources().onFire(), 1.0F));
		RpgTestSupport.result("armorStandBreaksAtScale", "max=" + max + " burn=" + burn);
		helper.assertTrue(near(max, 20.0F * RpgScale.kf()), "armor stand max health " + max);
		helper.assertTrue(near(burn, 4.0F * RpgScale.kf()), "a burn tick removed " + burn);
		helper.succeed();
	}

	@GameTest
	public void healthTextFitsLargeValues(final GameTestHelper helper) {
		String level1 = HudLayout.healthText(300.0F, 300.0F);
		String level1024 = HudLayout.healthText(38_650.0F, 38_650.0F);
		RpgTestSupport.result("healthTextFitsLargeValues", level1 + " | " + level1024);
		helper.assertTrue(level1.equals("300 / 300") && level1024.equals("38650 / 38650"), level1 + " | " + level1024);
		helper.succeed();
	}

	private static Zombie zombie(final GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		return zombie;
	}

	/** A zombie is undead, so Instant Health hurts it. A creeper is the neutral target for effect tests. */
	private static Creeper creeper(final GameTestHelper helper) {
		Creeper creeper = helper.spawnWithNoFreeWill(EntityTypes.CREEPER, 1, 2, 1);
		RpgTestSupport.bare(creeper);
		return creeper;
	}

	private static Creeper bigCreeper(final GameTestHelper helper) {
		Creeper creeper = creeper(helper);
		creeper.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VANILLA_BASE_3000);
		creeper.setHealth(creeper.getMaxHealth());
		return creeper;
	}

	private static Zombie bigZombie(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VANILLA_BASE_3000);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	private static float loss(final LivingEntity entity, final Runnable action) {
		entity.damageCooldownTime = 0;
		entity.setHealth(entity.getMaxHealth());
		float before = entity.getHealth();
		action.run();
		return before - entity.getHealth();
	}

	private static float healOf(final GameTestHelper helper, final LivingEntity entity, final Runnable action) {
		entity.setHealth(1.0F);
		float before = entity.getHealth();
		action.run();
		return entity.getHealth() - before;
	}

	private static float tickLoss(final GameTestHelper helper, final LivingEntity entity, final Holder<MobEffect> effect) {
		return loss(entity, () -> effect.value().applyEffectTick(helper.getLevel(), entity, 0));
	}

	private static float instantHeal(final GameTestHelper helper, final LivingEntity entity, final int amplifier) {
		return healOf(helper, entity, () -> MobEffects.INSTANT_HEALTH.value()
			.applyInstantaneousEffect(helper.getLevel(), null, null, entity, amplifier, 1.0));
	}

	private static float instantHarm(final GameTestHelper helper, final LivingEntity entity, final int amplifier) {
		return loss(entity, () -> MobEffects.INSTANT_DAMAGE.value()
			.applyInstantaneousEffect(helper.getLevel(), null, null, entity, amplifier, 1.0));
	}

	private static float absorptionGrant(final LivingEntity entity, final int amplifier) {
		entity.removeAllEffects();
		entity.setAbsorptionAmount(0.0F);
		entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, amplifier));
		float grant = entity.getAbsorptionAmount();
		entity.removeAllEffects();
		entity.setAbsorptionAmount(0.0F);
		return grant;
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < TOLERANCE * Math.max(1.0F, Math.abs(expected) / 100.0F);
	}
}
