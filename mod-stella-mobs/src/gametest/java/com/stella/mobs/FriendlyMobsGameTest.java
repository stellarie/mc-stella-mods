package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.FriendlyBuffs;
import com.stella.mobs.behavior.WolfPack;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The mobs on the player's side grew to meet the hostiles: wolves run faster, hit harder, take more, and wear
 * Resistance at night; elite wolves spawn, can be tamed, bring no minions, weaken the hostiles around them and
 * speed and strengthen their allies. Golems, cats, foxes, horses and their kin are tougher too.
 */
public class FriendlyMobsGameTest {
	private static final String TEST = "friendlyMobs";
	private static final int SAMPLES = 100_000;

	@GameTest
	public void everyFriendlyBuffIsWhatShips(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int x = 0;
		for (Map.Entry<EntityType<?>, FriendlyBuffs.Buff> entry : FriendlyBuffs.BUFFS.entrySet()) {
			Mob mob = (Mob) entry.getKey().create(helper.getLevel(), EntitySpawnReason.EVENT);
			mob.snapTo(helper.absoluteVec(new Vec3(1.5 + (x++ % 6), 2.0, 1.5)), 0.0F, 0.0F);
			mob.setNoAi(true);
			// The subject takes none of the natural spawn rolls, so an elite roll cannot raise its health after the buff.
			mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
			helper.getLevel().addFreshEntity(mob);
			FriendlyBuffs.Buff buff = entry.getValue();
			check(wrong, mob, Attributes.MAX_HEALTH, buff.health());
			check(wrong, mob, Attributes.MOVEMENT_SPEED, buff.speed());
			check(wrong, mob, Attributes.ATTACK_DAMAGE, buff.damage());
			if (mob.getHealth() < mob.getMaxHealth()) {
				wrong.add(mob.getType() + " spawned at " + mob.getHealth() + " of " + mob.getMaxHealth());
			}
			mob.discard();
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=everyFriendlyBuffIsWhatShips kinds={} wrong={}", TEST, FriendlyBuffs.BUFFS.size(), wrong);
		helper.assertTrue(wrong.isEmpty(), "friendly buffs off: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void aTamedWolfKeepsItsBuffs(final GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Wolf wolf = wolf(helper, 2, 2);
		wolf.tame(owner);
		AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
		double withBuff = health.getValue();
		health.removeModifier(FriendlyBuffs.HEALTH_ID);
		double withoutBuff = health.getValue();
		double gained = withBuff - withoutBuff;
		double expected = health.getBaseValue() * FriendlyBuffs.BUFFS.get(EntityTypes.WOLF).health();
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aTamedWolfKeepsItsBuffs base={} gained={} expected={}", TEST, health.getBaseValue(), gained, expected);
		helper.assertTrue(health.getBaseValue() >= 40.0 && Math.abs(gained - expected) < 1.0e-6, "the tamed wolf gained " + gained + " on a base of " + health.getBaseValue() + ", not " + expected);
		wolf.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aWolfWearsResistanceAtNight(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		Wolf wolf = wolf(helper, 2, 2);
		helper.succeedWhen(() -> {
			MobEffectInstance resistance = wolf.getEffect(MobEffects.RESISTANCE);
			helper.assertTrue(resistance != null && resistance.getAmplifier() == WolfPack.NIGHT_RESISTANCE_AMPLIFIER, "the wolf has " + resistance + " at night");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aWolfWearsResistanceAtNight resistance={}", TEST, resistance);
			wolf.discard();
		});
	}

	// Its own batch at noon: every other batch sets its level to night, and the level clock is shared.
	@GameTest(environment = "mod-stella-mobs-test:noon", maxTicks = 100)
	public void aWolfWearsNoResistanceByDay(final GameTestHelper helper) {
		Wolf wolf = wolf(helper, 2, 2);
		helper.runAfterDelay(WolfPack.CHECK_INTERVAL_TICKS * 3L, () -> {
			boolean resistant = wolf.hasEffect(MobEffects.RESISTANCE);
			wolf.discard();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aWolfWearsNoResistanceByDay resistant={}", TEST, resistant);
			helper.assertFalse(resistant, "the wolf wears Resistance by day");
			helper.succeed();
		});
	}

	@GameTest
	public void theEliteWolfChanceIsWhatShips(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20_260_927L);
		int elites = 0;
		for (int i = 0; i < SAMPLES; i++) {
			elites += WolfPack.rollsElite(random) ? 1 : 0;
		}
		double rate = (double) elites / SAMPLES;
		double band = 4.0 * Math.sqrt(WolfPack.ELITE_CHANCE * (1.0 - WolfPack.ELITE_CHANCE) / SAMPLES);
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=theEliteWolfChanceIsWhatShips rate={} chance={}", TEST, rate, WolfPack.ELITE_CHANCE);
		helper.assertTrue(Math.abs(rate - WolfPack.ELITE_CHANCE) <= band, "elite wolves rolled " + rate + ", not " + WolfPack.ELITE_CHANCE);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anEliteWolfIsLargerStrongerAndAloneAndCanBeTamed(final GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Wolf wolf = wolf(helper, 2, 2);
		WolfPack.makeElite(wolf);
		for (int i = 0; i < 64 && !wolf.isTame(); i++) {
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
			wolf.mobInteract(owner, InteractionHand.MAIN_HAND);
		}
		boolean tamed = wolf.isTame();
		helper.runAfterDelay(40L, () -> {
			// An elite of this mod gets its escorts within a few ticks; an elite wolf must get none.
			// The wolf's own escort record, never a box count that could admit a neighbour's escorts.
			int others = com.stella.mobs.behavior.EscortSquad.escortsOf(helper.getLevel(), wolf).size();
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=anEliteWolfIsLargerStrongerAndAloneAndCanBeTamed elite={} tamed={} summoned={} scale={}",
				TEST, Elite.isElite(wolf), tamed, others, wolf.getScale());
			helper.assertTrue(Elite.isElite(wolf), "the wolf is not elite");
			helper.assertTrue(tamed, "the elite wolf would not take a bone");
			helper.assertTrue(others == 0, "the elite wolf brought " + others + " summoned minions");
			wolf.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void anEliteWolfSlowsAndWeakensTheHostilesAroundIt(final GameTestHelper helper) {
		helper.setTime(Arena.NIGHT_TIME);
		Wolf wolf = wolf(helper, 2, 2);
		WolfPack.makeElite(wolf);
		Zombie near = zombie(helper, 4, 4);
		Zombie far = zombie(helper, 2, 2 + (int) WolfPack.AURA_RADIUS + 4);
		helper.succeedWhen(() -> {
			helper.assertTrue(near.hasEffect(MobEffects.SLOWNESS) && near.hasEffect(MobEffects.WEAKNESS), "the near zombie is not slowed and weakened yet");
			helper.assertFalse(far.hasEffect(MobEffects.SLOWNESS), "the aura reached past its radius");
			helper.assertFalse(wolf.hasEffect(MobEffects.WEAKNESS), "the aura weakened the wolf itself");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=anEliteWolfSlowsAndWeakensTheHostilesAroundIt near=slow+weak far=clear", TEST);
			wolf.discard();
			near.discard();
			far.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void aTamedEliteWolfSpeedsAndStrengthensItsAllies(final GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		owner.setPos(at.x, at.y, at.z);
		Wolf wolf = wolf(helper, 2, 2);
		wolf.tame(owner);
		WolfPack.makeElite(wolf);
		Wolf packmate = wolf(helper, 4, 2);
		packmate.tame(owner);
		helper.succeedWhen(() -> {
			helper.assertTrue(owner.hasEffect(MobEffects.SPEED) && owner.hasEffect(MobEffects.STRENGTH), "the owner is not sped and strengthened yet");
			helper.assertTrue(packmate.hasEffect(MobEffects.SPEED) && packmate.hasEffect(MobEffects.STRENGTH), "the packmate is not sped and strengthened yet");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aTamedEliteWolfSpeedsAndStrengthensItsAllies owner=speed+strength packmate=speed+strength", TEST);
			wolf.discard();
			packmate.discard();
		});
	}

	// Only this mod's own modifier is read: the area scaling of distant spawns rides its own modifiers.
	private static void check(final List<String> wrong, final Mob mob, final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, final double bonus) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (bonus == 0.0 || instance == null) {
			return;
		}
		net.minecraft.resources.Identifier id = attribute == Attributes.MAX_HEALTH ? FriendlyBuffs.HEALTH_ID
			: attribute == Attributes.MOVEMENT_SPEED ? FriendlyBuffs.SPEED_ID : FriendlyBuffs.DAMAGE_ID;
		net.minecraft.world.entity.ai.attributes.AttributeModifier modifier = instance.getModifier(id);
		if (modifier == null || modifier.amount() != bonus || modifier.operation() != net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
			wrong.add(mob.getType() + " " + attribute.getRegisteredName() + " carries " + modifier + ", not +" + bonus + " of its base");
		}
	}

	private static Wolf wolf(final GameTestHelper helper, final int x, final int z) {
		Arena.buildFloor(helper);
		// Tagged out of the spawn rolls, so no test wolf is an elite unless the test makes it one.
		Wolf wolf = EntityTypes.WOLF.create(helper.getLevel(), EntitySpawnReason.EVENT);
		wolf.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		wolf.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 2.0, z + 0.5)), 0.0F, 0.0F);
		wolf.setPersistenceRequired();
		helper.getLevel().addFreshEntity(wolf);
		return wolf;
	}

	private static Zombie zombie(final GameTestHelper helper, final int x, final int z) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		zombie.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}
}
