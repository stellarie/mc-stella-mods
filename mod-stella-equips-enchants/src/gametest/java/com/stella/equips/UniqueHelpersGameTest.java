package com.stella.equips;

import com.stella.equips.mixin.LivingEntityHurtAccessor;
import com.stella.equips.unique.UniqueCooldowns;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.UniqueEffects;
import com.stella.equips.unique.UniqueFields;
import com.stella.equips.unique.UniqueScheduler;
import com.stella.equips.unique.UniqueTargets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The shared helpers of a unique: extra damage, area damage, foes, allies, cooldowns, fields, harmful effects, the scheduler. */
public class UniqueHelpersGameTest {
	private static final float EPSILON = 1.0E-4F;

	private static Zombie zombie(final GameTestHelper helper, final double x, final double z, final boolean armored) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1.0, z));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		if (armored) {
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		}
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(armored ? 20.0 : 0.0);
		zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(armored ? 8.0 : 0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		zombie.setHealth(500.0F);
		return zombie;
	}

	private static float lost(final LivingEntity entity) {
		return entity.getMaxHealth() - entity.getHealth();
	}

	/** An extra hit must neither reset nor take the hurt cooldown, so a real hit after it still lands in full. */
	@GameTest
	public void anExtraHitKeepsTheHurtCooldownAndTheNextRealHitLands(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Zombie fresh = zombie(helper, 2.5, 3.5, false);
		Zombie control = zombie(helper, 4.5, 3.5, false);
		boolean hurt = UniqueDamage.deal(level, fresh, player, 6.0F, false);
		float extra = lost(fresh);
		int cooldownAfterExtra = fresh.damageCooldownTime;
		float lastHurtAfterExtra = ((LivingEntityHurtAccessor) fresh).stellaEquips$getLastHurt();
		fresh.hurtServer(level, level.damageSources().playerAttack(player), 5.0F);
		control.hurtServer(level, level.damageSources().playerAttack(player), 5.0F);
		float real = lost(fresh) - extra;
		float controlReal = lost(control);
		Zombie struck = zombie(helper, 6.5, 3.5, false);
		struck.hurtServer(level, level.damageSources().playerAttack(player), 8.0F);
		int cooldownBefore = struck.damageCooldownTime;
		float lastBefore = ((LivingEntityHurtAccessor) struck).stellaEquips$getLastHurt();
		float healthBefore = struck.getHealth();
		boolean second = UniqueDamage.deal(level, struck, player, 3.0F, false);
		float inCooldown = healthBefore - struck.getHealth();
		UniqueTestKit.result("helpersExtraHit", "hurt=" + hurt + " extra=" + extra + " cooldownAfter=" + cooldownAfterExtra + " lastHurtAfter=" + lastHurtAfterExtra
			+ " real=" + real + " controlReal=" + controlReal + " inCooldown=" + inCooldown + " cooldownKept=" + (struck.damageCooldownTime == cooldownBefore));
		helper.assertTrue(hurt && Math.abs(extra - 6.0F) < EPSILON, "the extra hit lost " + extra);
		helper.assertTrue(cooldownAfterExtra == 0 && lastHurtAfterExtra == 0.0F, "the extra hit left cooldown " + cooldownAfterExtra + " and last hurt " + lastHurtAfterExtra);
		helper.assertTrue(controlReal > 0.0F && Math.abs(real - controlReal) < EPSILON, "a real hit after an extra hit lost " + real + ", on a fresh target " + controlReal);
		helper.assertTrue(second && Math.abs(inCooldown - 3.0F) < EPSILON, "an extra hit during the cooldown lost " + inCooldown);
		helper.assertTrue(struck.damageCooldownTime == cooldownBefore && ((LivingEntityHurtAccessor) struck).stellaEquips$getLastHurt() == lastBefore,
			"the extra hit moved the cooldown from " + cooldownBefore + " to " + struck.damageCooldownTime);
		helper.succeed();
	}

	@GameTest
	public void pierceDamageIgnoresArmorAndPlainDamageDoesNot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Zombie pierced = zombie(helper, 2.5, 3.5, true);
		Zombie plain = zombie(helper, 4.5, 3.5, true);
		UniqueDamage.deal(level, pierced, player, 10.0F, true);
		UniqueDamage.deal(level, plain, player, 10.0F, false);
		var types = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
		boolean pierceTag = types.getOrThrow(UniqueDamage.UNIQUE_PIERCE).is(DamageTypeTags.BYPASSES_ARMOR);
		boolean plainTag = types.getOrThrow(UniqueDamage.UNIQUE).is(DamageTypeTags.BYPASSES_ARMOR);
		boolean cooldownTag = types.getOrThrow(UniqueDamage.UNIQUE).is(DamageTypeTags.BYPASSES_COOLDOWN) && types.getOrThrow(UniqueDamage.UNIQUE_PIERCE).is(DamageTypeTags.BYPASSES_COOLDOWN);
		UniqueTestKit.result("helpersPierce", "pierced=" + lost(pierced) + " plain=" + lost(plain) + " pierceTag=" + pierceTag + " plainTag=" + plainTag);
		helper.assertTrue(Math.abs(lost(pierced) - 10.0F) < EPSILON, "pierce damage lost " + lost(pierced) + " of 10");
		helper.assertTrue(lost(plain) < 9.0F && lost(plain) > 0.0F, "plain damage on armor lost " + lost(plain));
		helper.assertTrue(pierceTag && !plainTag && cooldownTag, "damage type tags: pierce " + pierceTag + ", plain " + plainTag + ", cooldown " + cooldownTag);
		helper.succeed();
	}

	@GameTest
	public void areaDamageHitsFoesAndNeverFriends(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer bystander = helper.makeMockServerPlayerInLevel();
		Vec3 spot = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		bystander.setPos(spot.x, spot.y, spot.z);
		bystander.getAbilities().invulnerable = false;
		try {
			Zombie near = zombie(helper, 3.5, 4.5, false);
			Zombie skipped = zombie(helper, 5.5, 4.5, false);
			Zombie far = zombie(helper, 3.5, 8.5, false);
			Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(4.5, 1.0, 3.5));
			Wolf wolf = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(3.5, 1.0, 3.5));
			wolf.tame(holder);
			Vec3 center = helper.absoluteVec(new Vec3(4.5, 1.5, 4.5));
			float bystanderBefore = bystander.getHealth();
			float villagerBefore = villager.getHealth();
			float wolfBefore = wolf.getHealth();
			int hit = UniqueDamage.area(level, center, 3.0, holder, 4.0F, true, skipped);
			UniqueTestKit.result("helpersArea", "hit=" + hit + " near=" + lost(near) + " skipped=" + lost(skipped) + " far=" + lost(far) + " villager=" + (villagerBefore - villager.getHealth())
				+ " bystander=" + (bystanderBefore - bystander.getHealth()) + " wolf=" + (wolfBefore - wolf.getHealth()));
			helper.assertTrue(hit == 1, "the area hit " + hit + " targets");
			helper.assertTrue(Math.abs(lost(near) - 4.0F) < EPSILON, "the zombie in range lost " + lost(near));
			helper.assertTrue(lost(skipped) == 0.0F, "the skipped zombie lost " + lost(skipped));
			helper.assertTrue(lost(far) == 0.0F, "the zombie out of range lost " + lost(far));
			helper.assertTrue(villager.getHealth() == villagerBefore, "the villager was hurt");
			helper.assertTrue(bystander.getHealth() == bystanderBefore, "a second player was hurt");
			helper.assertTrue(wolf.getHealth() == wolfBefore, "the holder's tamed wolf was hurt");
			helper.assertTrue(holder.getHealth() == holder.getMaxHealth(), "the holder was hurt");
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(bystander);
		}
	}

	@GameTest
	public void isFoeFollowsTheFriendlyRules(final GameTestHelper helper) {
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer other = UniqueTestKit.survivor(helper, new Vec3(2.5, 1.0, 1.5));
		Zombie hostile = zombie(helper, 3.5, 3.5, false);
		Zombie harmless = zombie(helper, 4.5, 3.5, false);
		harmless.addTag("mod-stella-mobs.harmless");
		Zombie summoned = zombie(helper, 5.5, 3.5, false);
		summoned.addTag("mod-stella-spells.summoned");
		Zombie dead = zombie(helper, 6.5, 3.5, false);
		Cow idle = helper.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(3.5, 1.0, 5.5));
		Cow hunting = helper.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(4.5, 1.0, 5.5));
		hunting.setTarget(holder);
		Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(5.5, 1.0, 5.5));
		villager.setTarget(holder);
		Wolf pet = helper.spawnWithNoFreeWill(EntityTypes.WOLF, new Vec3(6.5, 1.0, 5.5));
		pet.tame(holder);
		pet.setTarget(holder);
		dead.setHealth(0.0F);
		List<Boolean> seen = List.of(UniqueTargets.isFoe(holder, hostile), UniqueTargets.isFoe(holder, harmless), UniqueTargets.isFoe(holder, summoned),
			UniqueTargets.isFoe(holder, idle), UniqueTargets.isFoe(holder, hunting), UniqueTargets.isFoe(holder, villager), UniqueTargets.isFoe(holder, pet),
			UniqueTargets.isFoe(holder, other), UniqueTargets.isFoe(holder, holder), UniqueTargets.isFoe(holder, dead));
		UniqueTestKit.result("helpersFoe", "seen=" + seen);
		helper.assertTrue(seen.get(0), "a hostile zombie is not a foe");
		helper.assertTrue(!seen.get(1), "a harmless-tagged mob is a foe");
		helper.assertTrue(!seen.get(2), "a summoned ally is a foe");
		helper.assertTrue(!seen.get(3), "an idle cow is a foe");
		helper.assertTrue(seen.get(4), "a cow that targets the holder is not a foe");
		helper.assertTrue(!seen.get(5), "a villager is a foe");
		helper.assertTrue(!seen.get(6), "the holder's tamed wolf is a foe");
		helper.assertTrue(!seen.get(7) && !seen.get(8), "a player is a foe");
		helper.assertTrue(!seen.get(9), "a dead zombie is a foe");
		helper.succeed();
	}

	@GameTest
	public void alliesAreLivingPlayersInsideTheRadius(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer holder = helper.makeMockServerPlayerInLevel();
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		ServerPlayer far = helper.makeMockServerPlayerInLevel();
		ServerPlayer watcher = UniqueTestKit.spectator(helper);
		ServerPlayer fallen = helper.makeMockServerPlayerInLevel();
		try {
			Vec3 origin = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
			holder.setPos(origin.x, origin.y, origin.z);
			near.setPos(origin.x + 3.0, origin.y, origin.z);
			far.setPos(origin.x + 30.0, origin.y, origin.z);
			watcher.setPos(origin.x + 1.0, origin.y, origin.z);
			fallen.setPos(origin.x + 2.0, origin.y, origin.z);
			fallen.setHealth(0.0F);
			List<ServerPlayer> allies = UniqueTargets.allies(holder, 6.0);
			UniqueTestKit.result("helpersAllies", "count=" + allies.size());
			helper.assertTrue(allies.contains(holder), "the holder is not an ally of itself");
			helper.assertTrue(allies.contains(near), "a player 3 blocks away is not an ally");
			helper.assertTrue(!allies.contains(far), "a player 30 blocks away is an ally");
			helper.assertTrue(!allies.contains(watcher), "a spectator is an ally");
			helper.assertTrue(!allies.contains(fallen), "a dead player is an ally");
			helper.assertTrue(UniqueTargets.allies(holder, 2.0).size() == 1, "a radius of 2 holds " + UniqueTargets.allies(holder, 2.0).size());
			helper.succeed();
		} finally {
			for (ServerPlayer player : List.of(holder, near, far, watcher, fallen)) {
				level.getServer().getPlayerList().remove(player);
			}
		}
	}

	@GameTest(maxTicks = 100)
	public void aCooldownRestartsWhenItIsReadyAndNotBefore(final GameTestHelper helper) {
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer other = UniqueTestKit.survivor(helper, new Vec3(2.5, 1.0, 1.5));
		boolean first = UniqueCooldowns.ready(player, "test.cooldown", 10);
		boolean again = UniqueCooldowns.ready(player, "test.cooldown", 10);
		boolean otherKey = UniqueCooldowns.ready(player, "test.other", 10);
		boolean otherPlayer = UniqueCooldowns.ready(other, "test.cooldown", 10);
		helper.runAfterDelay(4L, () -> helper.assertTrue(!UniqueCooldowns.ready(player, "test.cooldown", 10), "the cooldown ended after 4 of 10 ticks"));
		helper.runAfterDelay(14L, () -> {
			boolean later = UniqueCooldowns.ready(player, "test.cooldown", 10);
			boolean restarted = !UniqueCooldowns.ready(player, "test.cooldown", 10);
			UniqueTestKit.result("helpersCooldown", "first=" + first + " again=" + again + " otherKey=" + otherKey + " otherPlayer=" + otherPlayer + " later=" + later + " restarted=" + restarted);
			helper.assertTrue(first && !again, "the first call must pass and the second must not: " + first + " " + again);
			helper.assertTrue(otherKey && otherPlayer, "another key or player shares the cooldown");
			helper.assertTrue(later, "the cooldown never ended");
			helper.assertTrue(restarted, "the cooldown did not restart when it was ready");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void aBreathFieldHurtsFoesEveryTwentyTicksAndEnds(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Zombie inside = zombie(helper, 4.5, 4.5, false);
		Zombie outside = zombie(helper, 4.5, 7.5, false);
		Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(3.5, 1.0, 4.5));
		float villagerBefore = villager.getHealth();
		Vec3 center = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		List<List<LivingEntity>> pulses = new CopyOnWriteArrayList<>();
		UniqueFields.field(level, center, 2.5, 50, 4.0F, holder, pulses::add);
		boolean registered = UniqueFields.active().stream().anyMatch(field -> field.level() == level && field.center().equals(center) && field.radius() == 2.5 && field.perSecond() == 4.0F);
		helper.runAfterDelay(45L, () -> {
			float midway = lost(inside);
			helper.assertTrue(registered, "the field is not listed as active");
			helper.assertTrue(Math.abs(midway - 8.0F) < EPSILON, "after 45 ticks the zombie lost " + midway + ", expected two pulses of 4");
			helper.assertTrue(lost(outside) == 0.0F && villager.getHealth() == villagerBefore, "the field hurt something outside or friendly");
			helper.assertTrue(pulses.size() == 2 && pulses.stream().allMatch(foes -> foes.contains(inside) && !foes.contains(villager) && !foes.contains(outside)),
				"the pulses saw " + pulses);
		});
		helper.runAfterDelay(80L, () -> {
			boolean gone = UniqueFields.active().stream().noneMatch(field -> field.center().equals(center));
			UniqueTestKit.result("helpersField", "registered=" + registered + " lost=" + lost(inside) + " pulses=" + pulses.size() + " gone=" + gone);
			helper.assertTrue(gone, "the field outlived its 50 ticks");
			helper.assertTrue(Math.abs(lost(inside) - 8.0F) < EPSILON, "the zombie lost " + lost(inside) + " in total, expected 8");
			helper.succeed();
		});
	}

	@GameTest
	public void addHarmfulNeverLowersAStrongerEffect(final GameTestHelper helper) {
		Cow target = helper.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(3.5, 1.0, 3.5));
		boolean added = UniqueEffects.addHarmful(target, MobEffects.POISON, 100, 1);
		boolean weaker = UniqueEffects.addHarmful(target, MobEffects.POISON, 300, 0);
		MobEffectInstance afterWeaker = target.getEffect(MobEffects.POISON);
		int amplifierAfterWeaker = afterWeaker.getAmplifier();
		int durationAfterWeaker = afterWeaker.getDuration();
		boolean stronger = UniqueEffects.addHarmful(target, MobEffects.POISON, 50, 2);
		MobEffectInstance afterStronger = target.getEffect(MobEffects.POISON);
		UniqueEffects.addHarmful(target, MobEffects.SLOWNESS, 40, 0);
		UniqueEffects.addHarmful(target, MobEffects.SLOWNESS, 100, 0);
		int extended = target.getEffect(MobEffects.SLOWNESS).getDuration();
		UniqueEffects.addHarmful(target, MobEffects.SLOWNESS, 10, 0);
		int kept = target.getEffect(MobEffects.SLOWNESS).getDuration();
		UniqueTestKit.result("helpersAddHarmful", "added=" + added + " weaker=" + weaker + " amp=" + amplifierAfterWeaker + "/" + durationAfterWeaker + " stronger=" + stronger
			+ " strongerAmp=" + afterStronger.getAmplifier() + " extended=" + extended + " kept=" + kept);
		helper.assertTrue(added, "the first effect was not applied");
		helper.assertTrue(!weaker && amplifierAfterWeaker == 1 && durationAfterWeaker >= 99, "a weaker effect changed the poison to " + amplifierAfterWeaker + " for " + durationAfterWeaker);
		helper.assertTrue(stronger && afterStronger.getAmplifier() == 2, "a stronger effect was not applied: " + afterStronger.getAmplifier());
		helper.assertTrue(extended >= 99, "a longer effect of the same level lasts " + extended);
		helper.assertTrue(kept >= 99, "a shorter effect of the same level cut the duration to " + kept);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void theSchedulerRunsTasksAfterTheirDelayInOrder(final GameTestHelper helper) {
		List<String> ran = new CopyOnWriteArrayList<>();
		UniqueScheduler.after(helper.getLevel().getServer(), 3, () -> ran.add("a"));
		UniqueScheduler.after(helper.getLevel().getServer(), 8, () -> ran.add("b"));
		UniqueScheduler.after(helper.getLevel().getServer(), 3, () -> ran.add("c"));
		List<String> now = new ArrayList<>(ran);
		helper.runAfterDelay(1L, () -> helper.assertTrue(ran.isEmpty(), "a task ran after 1 of 3 ticks: " + ran));
		helper.runAfterDelay(5L, () -> helper.assertTrue(ran.equals(List.of("a", "c")), "after 5 ticks the tasks run were " + ran));
		helper.runAfterDelay(14L, () -> {
			UniqueTestKit.result("helpersScheduler", "now=" + now + " ran=" + ran);
			helper.assertTrue(now.isEmpty(), "a task ran at once: " + now);
			helper.assertTrue(ran.equals(List.of("a", "c", "b")), "the tasks ran as " + ran);
			helper.succeed();
		});
	}
}
