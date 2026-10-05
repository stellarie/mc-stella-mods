package com.stella.equips;

import com.stella.equips.unique.UniqueArrows;
import com.stella.equips.unique.UniqueDamage;
import com.stella.equips.unique.Uniques;
import java.lang.reflect.Field;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

/**
 * The hook dispatch: which hook fires for which hit, in which slot, and where the outgoing multiplier lands. The
 * fixture uniques record every call by holder, so tests that run side by side never read each other's calls.
 */
public class UniqueDispatchGameTest {
	private static final float EPSILON = 1.0E-4F;
	private static final float HIT = 10.0F;

	private static ServerPlayer holder(final GameTestHelper helper, final double x) {
		return UniqueTestKit.survivor(helper, new Vec3(x, 1.0, 1.5));
	}

	/** A holder that can be hurt and take effects, which needs a connection. */
	private static ServerPlayer wearer(final GameTestHelper helper, final double x) {
		return UniqueTestKit.connected(helper, new Vec3(x, 1.0, 1.5));
	}

	private static Zombie foe(final GameTestHelper helper, final double x, final boolean armored) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1.0, 5.5));
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

	private static float lost(final LivingEntity entity, final float before) {
		return before - entity.getHealth();
	}

	@GameTest
	public void theOutgoingMultiplierLandsBeforeArmor(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer unique = holder(helper, 1.5);
		ServerPlayer plain = holder(helper, 2.5);
		unique.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.sword, 7));
		UniqueFixtures.multiply(unique.getUUID(), 2.0F);
		Zombie hitByUnique = foe(helper, 1.5, true);
		Zombie doubledByHand = foe(helper, 3.5, true);
		Zombie single = foe(helper, 5.5, true);
		hitByUnique.hurtServer(level, level.damageSources().playerAttack(unique), HIT);
		doubledByHand.hurtServer(level, level.damageSources().playerAttack(plain), HIT * 2);
		single.hurtServer(level, level.damageSources().playerAttack(plain), HIT);
		float viaHook = lost(hitByUnique, 500.0F);
		float viaHand = lost(doubledByHand, 500.0F);
		float once = lost(single, 500.0F);
		List<UniqueFixtures.Call> calls = UniqueFixtures.calls(unique.getUUID(), "outgoingMelee");
		UniqueTestKit.result("dispatchOutgoing", "viaHook=" + viaHook + " viaHand=" + viaHand + " once=" + once + " calls=" + calls.size());
		helper.assertTrue(calls.size() == 1 && Math.abs(calls.get(0).amount() - HIT) < EPSILON && calls.get(0).refine() == 7,
			"the hook saw " + calls);
		helper.assertTrue(Math.abs(viaHook - viaHand) < EPSILON, "the doubled hit lost " + viaHook + ", a doubled swing loses " + viaHand);
		helper.assertTrue(viaHook > once * 2.0F + 0.1F, "armor shrinks 2x damage less than twice: " + viaHook + " against " + once + "x2. The multiplier landed after armor");
		helper.assertTrue(UniqueFixtures.calls(plain.getUUID()).isEmpty(), "a plain player fired hooks");
		helper.succeed();
	}

	@GameTest
	public void afterMeleeReportsTheDamageTheTargetTookAndTheKill(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, 1.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.sword, 0));
		Zombie target = foe(helper, 1.5, true);
		target.hurtServer(level, level.damageSources().playerAttack(player), HIT);
		float took = lost(target, 500.0F);
		List<UniqueFixtures.Call> after = UniqueFixtures.calls(player.getUUID(), "afterMelee");
		Zombie weak = foe(helper, 3.5, false);
		weak.setHealth(3.0F);
		weak.hurtServer(level, level.damageSources().playerAttack(player), 50.0F);
		List<UniqueFixtures.Call> killedCalls = UniqueFixtures.calls(player.getUUID(), "afterMeleeKilled");
		List<UniqueFixtures.Call> kills = UniqueFixtures.calls(player.getUUID(), "onKill");
		UniqueTestKit.result("dispatchAfterMelee", "took=" + took + " after=" + after.size() + " killedCalls=" + killedCalls.size() + " kills=" + kills.size());
		helper.assertTrue(after.size() >= 1 && Math.abs(after.get(0).amount() - took) < EPSILON && !after.get(0).flag(), "afterMelee saw " + after + " after a loss of " + took);
		helper.assertTrue(killedCalls.size() == 1, "the killing blow reported killed " + killedCalls.size() + " times");
		helper.assertTrue(kills.size() == 1 && kills.get(0).other().equals(weak.getUUID()), "onKill saw " + kills);
		helper.succeed();
	}

	@GameTest
	public void aFallingFullChargeBlowIsACritical(final GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, 1.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.sword, 0));
		Zombie plainTarget = foe(helper, 1.5, false);
		Zombie critTarget = foe(helper, 5.5, false);
		Field charge = LivingEntity.class.getDeclaredField("attackStrengthTicker");
		charge.setAccessible(true);
		charge.setInt(player, 100);
		player.setOnGround(true);
		player.fallDistance = 0.0;
		player.attack(plainTarget);
		charge.setInt(player, 100);
		player.setOnGround(false);
		player.fallDistance = 2.0;
		player.attack(critTarget);
		List<UniqueFixtures.Call> after = UniqueFixtures.calls(player.getUUID(), "afterMelee");
		UniqueTestKit.result("dispatchCrit", "after=" + after);
		helper.assertTrue(after.size() == 2, "two attacks fired " + after.size() + " afterMelee calls");
		boolean plainCrit = after.stream().filter(call -> call.other().equals(plainTarget.getUUID())).findFirst().orElseThrow().flag();
		boolean falling = after.stream().filter(call -> call.other().equals(critTarget.getUUID())).findFirst().orElseThrow().flag();
		helper.assertTrue(!plainCrit, "a blow from the ground is a critical");
		helper.assertTrue(falling, "a falling full charge blow is no critical");
		helper.succeed();
	}

	@GameTest
	public void armorHooksFireForMeleeAndSpellsToo(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, 1.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.sword, 0));
		player.setItemSlot(EquipmentSlot.HEAD, UniqueTestKit.stack(UniqueFixtures.helmet, 0));
		Zombie target = foe(helper, 1.5, false);
		target.hurtServer(level, level.damageSources().playerAttack(player), HIT);
		target.damageCooldownTime = 0;
		target.hurtServer(level, level.damageSources().indirectMagic(player, player), HIT);
		target.damageCooldownTime = 0;
		target.hurtServer(level, level.damageSources().magic(), HIT);
		List<String> melee = UniqueFixtures.calls(player.getUUID(), "outgoingMelee").stream().map(UniqueFixtures.Call::unique).sorted().toList();
		List<String> spell = UniqueFixtures.calls(player.getUUID(), "outgoingSpell").stream().map(UniqueFixtures.Call::unique).sorted().toList();
		List<String> afterSpell = UniqueFixtures.calls(player.getUUID(), "afterSpell").stream().map(UniqueFixtures.Call::unique).sorted().toList();
		UniqueTestKit.result("dispatchSpell", "melee=" + melee + " spell=" + spell + " afterSpell=" + afterSpell);
		List<String> both = List.of("test_recorder_helmet", "test_recorder_sword");
		helper.assertTrue(melee.equals(both), "a melee hit fired " + melee);
		helper.assertTrue(spell.equals(both), "a spell hit (indirect magic by the holder) fired " + spell + ", and a magic hit with no attacker must not");
		helper.assertTrue(afterSpell.equals(both), "afterSpell fired " + afterSpell);
		helper.succeed();
	}

	@GameTest
	public void onlyTheMainHandAndTheRightSlotsFire(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = wearer(helper, 1.5);
		player.setItemInHand(InteractionHand.OFF_HAND, UniqueTestKit.stack(UniqueFixtures.sword, 0));
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.helmet, 0));
		player.setItemSlot(EquipmentSlot.HEAD, UniqueTestKit.stack(UniqueFixtures.boots, 0));
		Zombie target = foe(helper, 1.5, false);
		target.hurtServer(level, level.damageSources().playerAttack(player), HIT);
		player.hurtServer(level, level.damageSources().mobAttack(target), HIT);
		List<UniqueFixtures.Call> calls = UniqueFixtures.calls(player.getUUID());
		UniqueTestKit.result("dispatchSlots", "calls=" + calls);
		helper.assertTrue(Uniques.active(player).isEmpty(), "active uniques: " + Uniques.active(player));
		helper.assertTrue(calls.isEmpty(), "hooks fired from the wrong slot: " + calls);
		helper.succeed();
	}

	@GameTest
	public void incomingHooksSeeTheHitAfterMitigationAndChangeIt(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer unique = wearer(helper, 1.5);
		ServerPlayer control = wearer(helper, 2.5);
		unique.setItemSlot(EquipmentSlot.HEAD, UniqueTestKit.stack(UniqueFixtures.helmet, 0));
		control.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		UniqueFixtures.multiply(unique.getUUID(), 0.5F);
		Zombie attacker = foe(helper, 3.5, false);
		float uniqueBefore = unique.getHealth();
		float controlBefore = control.getHealth();
		unique.hurtServer(level, level.damageSources().mobAttack(attacker), HIT);
		control.hurtServer(level, level.damageSources().mobAttack(attacker), HIT);
		float uniqueLost = lost(unique, uniqueBefore);
		float controlLost = lost(control, controlBefore);
		List<UniqueFixtures.Call> incoming = UniqueFixtures.calls(unique.getUUID(), "incoming");
		List<UniqueFixtures.Call> after = UniqueFixtures.calls(unique.getUUID(), "afterIncoming");
		UniqueTestKit.result("dispatchIncoming", "uniqueLost=" + uniqueLost + " controlLost=" + controlLost + " incoming=" + incoming + " after=" + after);
		helper.assertTrue(controlLost > 0.0F, "the control lost nothing");
		helper.assertTrue(incoming.size() == 1 && Math.abs(incoming.get(0).amount() - controlLost) < EPSILON, "incoming saw " + incoming + " against a plain loss of " + controlLost);
		helper.assertTrue(Math.abs(uniqueLost - controlLost * 0.5F) < EPSILON, "the halved hit cost " + uniqueLost + ", plain cost " + controlLost);
		helper.assertTrue(after.size() == 1 && Math.abs(after.get(0).amount() - uniqueLost) < EPSILON, "afterIncoming saw " + after + " after a loss of " + uniqueLost);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aUniqueBowMarksItsArrowsAndTheHitsRunTheArrowHooks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 2.0, 1.5));
		player.setXRot(-90.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.bow, 12));
		player.getInventory().add(new ItemStack(Items.ARROW, 8));
		UniqueFixtures.multiply(player.getUUID(), 2.0F);
		ItemStack bow = player.getMainHandItem();
		bow.getItem().releaseUsing(bow, level, player, 0);
		List<AbstractArrow> arrows = UniqueTestKit.arrowsOf(level, player, player.position());
		helper.assertTrue(arrows.size() == 1, "the shot loosed " + arrows.size() + " arrows");
		AbstractArrow arrow = arrows.get(0);
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		List<UniqueFixtures.Call> shots = UniqueFixtures.calls(player.getUUID(), "onShot");
		helper.assertTrue(mark != null && mark.id().equals("test_recorder_bow") && mark.refine() == 12, "the arrow mark is " + mark);
		helper.assertTrue(shots.size() == 1 && shots.get(0).refine() == 12 && shots.get(0).amount() > 0.0F && shots.get(0).other().equals(arrow.getUUID()),
			"onShot saw " + shots);
		Zombie target = foe(helper, 3.5, true);
		Zombie control = foe(helper, 5.5, true);
		Arrow plain = new Arrow(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		plain.setOwner(player);
		target.hurtServer(level, level.damageSources().arrow(arrow, player), HIT);
		control.hurtServer(level, level.damageSources().arrow(plain, player), HIT * 2);
		float marked = lost(target, 500.0F);
		float doubledPlain = lost(control, 500.0F);
		List<UniqueFixtures.Call> out = UniqueFixtures.calls(player.getUUID(), "outgoingArrow");
		List<UniqueFixtures.Call> after = UniqueFixtures.calls(player.getUUID(), "afterArrow");
		UniqueTestKit.result("dispatchArrow", "marked=" + marked + " doubledPlain=" + doubledPlain + " out=" + out.size() + " after=" + after.size());
		helper.assertTrue(out.size() == 1 && Math.abs(out.get(0).amount() - HIT) < EPSILON, "outgoingArrow saw " + out + " (an arrow of no unique must not fire it)");
		helper.assertTrue(after.size() == 1 && Math.abs(after.get(0).amount() - marked) < EPSILON, "afterArrow saw " + after + " after a loss of " + marked);
		helper.assertTrue(Math.abs(marked - doubledPlain) < EPSILON, "the doubled arrow lost " + marked + ", a doubled plain arrow loses " + doubledPlain);
		helper.assertTrue(UniqueFixtures.calls(player.getUUID(), "outgoingMelee").isEmpty(), "an arrow hit fired a melee hook");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aUniqueCrossbowMarksItsBoltsAndCallsTheShotHook(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 2.0, 1.5));
		player.setXRot(-90.0F);
		ItemStack crossbow = UniqueTestKit.stack(UniqueFixtures.crossbow, 10);
		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
		player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
		((CrossbowItem) crossbow.getItem()).performShooting(level, player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
		List<AbstractArrow> bolts = UniqueTestKit.arrowsOf(level, player, player.position());
		List<UniqueFixtures.Call> shots = UniqueFixtures.calls(player.getUUID(), "onShot");
		UniqueTestKit.result("dispatchCrossbow", "bolts=" + bolts.size() + " shots=" + shots);
		helper.assertTrue(bolts.size() == 1, "the crossbow loosed " + bolts.size() + " bolts");
		UniqueArrows.Mark mark = UniqueArrows.of(bolts.get(0));
		helper.assertTrue(mark != null && mark.id().equals("test_recorder_crossbow") && mark.refine() == 10, "the bolt mark is " + mark);
		helper.assertTrue(shots.size() == 1 && Math.abs(shots.get(0).amount() - 3.15F) < EPSILON, "onShot saw " + shots);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void everySecondFiresOnceEveryTwentyServerTicks(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.sword, 3));
		helper.runAfterDelay(66L, () -> {
			List<UniqueFixtures.Call> ticks = UniqueFixtures.calls(player.getUUID(), "everySecond");
			UniqueTestKit.result("dispatchEverySecond", "ticks=" + ticks.stream().map(call -> call.refine() + "@" + call.amount()).toList());
			server.getPlayerList().remove(player);
			helper.assertTrue(ticks.size() >= 3, "everySecond fired " + ticks.size() + " times in 66 ticks");
			for (int i = 0; i < ticks.size(); i++) {
				helper.assertTrue(ticks.get(i).refine() == 3, "everySecond saw refine " + ticks.get(i).refine());
				helper.assertTrue(((long) ticks.get(i).amount()) % 20L == 0L, "everySecond fired on server tick " + ticks.get(i).amount());
				if (i > 0) {
					helper.assertTrue(ticks.get(i).amount() - ticks.get(i - 1).amount() == 20.0F, "the gap was " + (ticks.get(i).amount() - ticks.get(i - 1).amount()));
				}
			}
			helper.succeed();
		});
	}

	@GameTest
	public void harmfulEffectsOnTheHolderPassThroughTheDurationHook(final GameTestHelper helper) {
		ServerPlayer player = wearer(helper, 1.5);
		player.setItemSlot(EquipmentSlot.HEAD, UniqueTestKit.stack(UniqueFixtures.helmet, 0));
		UniqueFixtures.shorten(player.getUUID(), 40);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0));
		player.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 0));
		MobEffectInstance poison = player.getEffect(MobEffects.POISON);
		MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
		List<UniqueFixtures.Call> calls = UniqueFixtures.calls(player.getUUID(), "harmfulDuration");
		UniqueTestKit.result("dispatchHarmful", "poison=" + (poison == null ? -1 : poison.getDuration()) + " speed=" + (speed == null ? -1 : speed.getDuration()) + " calls=" + calls.size());
		helper.assertTrue(poison != null && poison.getDuration() == 60, "poison lasts " + (poison == null ? "none" : poison.getDuration()));
		helper.assertTrue(speed != null && speed.getDuration() == 100, "a helpful effect was shortened to " + (speed == null ? "none" : speed.getDuration()));
		helper.assertTrue(calls.size() == 1 && calls.get(0).amount() == 100.0F, "the duration hook saw " + calls);
		helper.succeed();
	}

	@GameTest
	public void uniqueDamageDoesNotRunTheHitHooksAgain(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, 1.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueTestKit.stack(UniqueFixtures.sword, 0));
		player.setItemSlot(EquipmentSlot.HEAD, UniqueTestKit.stack(UniqueFixtures.helmet, 0));
		Zombie target = foe(helper, 1.5, false);
		target.damageCooldownTime = 0;
		UniqueDamage.deal(level, target, player, HIT, false);
		UniqueDamage.deal(level, target, player, HIT, true);
		List<UniqueFixtures.Call> calls = UniqueFixtures.calls(player.getUUID());
		UniqueTestKit.result("dispatchUniqueDamage", "lost=" + lost(target, 500.0F) + " calls=" + calls);
		helper.assertTrue(lost(target, 500.0F) > 0.0F, "the unique damage hurt nothing");
		helper.assertTrue(calls.isEmpty(), "unique damage ran hooks again: " + calls);
		helper.succeed();
	}
}
