package com.stella.end;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;

/** The Wither Drain enchantment: a hit withers, and the wither damage that follows heals the one who struck. */
public class WitherDrainGameTest {
	private static final Vec3 SPOT = SovereignGameTest.ANCHOR.add(0.0, 0.0, 6.0);
	private static final float WOUND = 100.0F;
	private static final float WITHER_BLOW = 3.0F;

	/** A weapon made at the anvil, so the test holds what a player holds. */
	static ItemStack drainSword(final GameTestHelper helper) {
		ItemStack made = CoreImbue.result(helper.getLevel().registryAccess(), new ItemStack(Items.NETHERITE_SWORD), new ItemStack(EndItems.witherCore()));
		helper.assertTrue(!made.isEmpty() && WitherDrain.has(made), "the anvil made no Wither Drain sword");
		return made;
	}

	/** A wielder hurt down to a known health, so a heal shows as the difference. */
	static Zombie wounded(final ServerLevel end, final Vec3 at, final ItemStack held) {
		Zombie zombie = WitheringArmsGameTest.wielder(end, at, held);
		zombie.setHealth(WOUND);
		return zombie;
	}

	@GameTest(environment = "mod-stella-end-test:drain_data", maxTicks = 20)
	public void theWitherDrainEnchantmentIsAtLevelOneOnWeaponsAndBowsAndNotOnArmour(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> drain = registry.get(WitherDrain.KEY).orElse(null);
		helper.assertTrue(drain != null, "the registry holds no mod-stella-end:wither_drain");
		boolean weapons = List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.BOW), new ItemStack(WitheringArms.sword()),
			new ItemStack(WitheringArms.bow())).stream().allMatch(stack -> drain.value().isSupportedItem(stack));
		boolean armour = List.of(new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.SHIELD), new ItemStack(Items.STICK)).stream().anyMatch(stack -> drain.value().isSupportedItem(stack));
		EndTestSupport.result("drainData", "max=" + drain.value().getMaxLevel() + " weapons=" + weapons + " armour=" + armour + " key=" + WitherDrain.KEY.identifier());
		helper.assertTrue(WitherDrain.KEY.identifier().toString().equals("mod-stella-end:wither_drain"), "the key is " + WitherDrain.KEY.identifier());
		helper.assertTrue(drain.value().getMaxLevel() == 1, "the enchantment tops at level " + drain.value().getMaxLevel());
		helper.assertTrue(weapons && !armour, "supported items: weapons " + weapons + ", armour or others " + armour);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:drain_wither", maxTicks = 40)
	public void aHitFromAWitherDrainWeaponAppliesWitherAndAHitFromAnIdenticalPlainWeaponDoesNot(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Zombie drainer = WitheringArmsGameTest.wielder(end, SPOT.add(2.0, 0.0, 0.0), drainSword(helper));
		Zombie plain = WitheringArmsGameTest.wielder(end, SPOT.add(-2.0, 0.0, 0.0), new ItemStack(Items.NETHERITE_SWORD));
		Zombie byDrain = WitheringArmsGameTest.target(end, SPOT.add(4.0, 0.0, 0.0));
		Zombie byPlain = WitheringArmsGameTest.target(end, SPOT.add(-4.0, 0.0, 0.0));
		// Other mods shorten harmful effects by the stats of the victim, so the victim is its own yardstick.
		int expected = SovereignGameTest.lengthOn(byDrain, WitheringArms.WITHER_TICKS, WitheringArms.WITHER_AMPLIFIER);
		byDrain.hurtServer(end, end.damageSources().mobAttack(drainer), 1.0F);
		byPlain.hurtServer(end, end.damageSources().mobAttack(plain), 1.0F);
		MobEffectInstance wither = byDrain.getEffect(MobEffects.WITHER);
		EndTestSupport.result("drainWither", "drain=" + (wither == null ? "none" : wither.getAmplifier() + "/" + wither.getDuration()) + " expected=" + expected + " plain=" + byPlain.hasEffect(MobEffects.WITHER));
		int length = wither == null ? -1 : wither.getDuration();
		int amplifier = wither == null ? -1 : wither.getAmplifier();
		boolean plainWithered = byPlain.hasEffect(MobEffects.WITHER);
		EndTestSupport.fresh(helper);
		helper.assertTrue(amplifier == WitheringArms.WITHER_AMPLIFIER && length == expected, "a drain hit left amplifier " + amplifier + " for " + length + " ticks, expected " + expected);
		helper.assertTrue(!plainWithered, "a plain netherite sword withered its target");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:drain_heal", maxTicks = 40)
	public void witherDamageOnADrainedTargetHealsTheAttackerByTheSameAmountAndOtherWitherAndOtherDamageHealsNobody(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		WitherDrain.reset();
		Zombie drainer = wounded(end, SPOT.add(2.0, 0.0, 0.0), drainSword(helper));
		Zombie bystander = wounded(end, SPOT.add(-2.0, 0.0, 0.0), drainSword(helper));
		Zombie drained = WitheringArmsGameTest.target(end, SPOT.add(4.0, 0.0, 0.0));
		Zombie plainWithered = WitheringArmsGameTest.target(end, SPOT.add(-4.0, 0.0, 0.0));
		drained.hurtServer(end, end.damageSources().mobAttack(drainer), 1.0F);
		// A plain wither is put on the second target by hand.
		plainWithered.addEffect(new MobEffectInstance(MobEffects.WITHER, WitheringArms.WITHER_TICKS, WitheringArms.WITHER_AMPLIFIER));
		helper.assertTrue(drained.hasEffect(MobEffects.WITHER), "the drain hit applied no wither");
		drainer.setHealth(WOUND);
		drained.damageCooldownTime = 0;
		float drainedBefore = drained.getHealth();
		drained.hurtServer(end, end.damageSources().wither(), WITHER_BLOW);
		float lost = drainedBefore - drained.getHealth();
		float healed = drainer.getHealth() - WOUND;
		float plainBefore = plainWithered.getHealth();
		plainWithered.hurtServer(end, end.damageSources().wither(), WITHER_BLOW);
		float plainLost = plainBefore - plainWithered.getHealth();
		float otherDamageBefore = drainer.getHealth();
		drained.damageCooldownTime = 0;
		drained.hurtServer(end, end.damageSources().magic(), WITHER_BLOW);
		float otherHeal = drainer.getHealth() - otherDamageBefore;
		List<WitherDrain.Heal> heals = WitherDrain.heals();
		WitherDrain.Heal last = heals.isEmpty() ? null : heals.get(heals.size() - 1);
		EndTestSupport.result("drainHeal", "lost=" + lost + " healed=" + healed + " plainLost=" + plainLost + " bystanderKept=" + (bystander.getHealth() == WOUND) + " otherDamageHeal=" + otherHeal
			+ " recorded=" + heals.size() + " last=" + (last == null ? "none" : last.amount()));
		float bystanderHeal = bystander.getHealth() - WOUND;
		boolean recordedRight = last != null && last.victim().equals(drained.getUUID()) && last.attacker().equals(drainer.getUUID()) && Math.abs(last.amount() - lost) < 1.0E-3F;
		EndTestSupport.fresh(helper);
		helper.assertTrue(lost > 0.0F, "the wither blow took no health: " + lost);
		helper.assertTrue(Math.abs(healed - lost) < 1.0E-3F, "the attacker healed " + healed + " for " + lost + " of wither damage");
		helper.assertTrue(plainLost > 0.0F && heals.size() == 1, "the plain wither took " + plainLost + " and " + heals.size() + " heals are recorded, wanted one");
		helper.assertTrue(bystanderHeal == 0.0F, "a Wither Drain wielder who did not strike this target healed " + bystanderHeal);
		helper.assertTrue(otherHeal == 0.0F, "damage of another kind on the drained target healed " + otherHeal);
		helper.assertTrue(recordedRight, "the heal record is " + last);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:drain_tick", maxTicks = 160)
	public void theWitherThatTicksOnADrainedTargetHealsTheAttackerForExactlyWhatItTook(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		WitherDrain.reset();
		Zombie drainer = wounded(end, SPOT.add(2.0, 0.0, 0.0), drainSword(helper));
		Zombie drained = WitheringArmsGameTest.target(end, SPOT.add(4.0, 0.0, 0.0));
		drained.hurtServer(end, end.damageSources().mobAttack(drainer), 1.0F);
		float[] start = {drained.getHealth()};
		drainer.setHealth(WOUND);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(drainer.getHealth() > WOUND, "no heal yet; wither " + drained.getEffect(MobEffects.WITHER) + " tracked=" + (end.getEntity(drained.getUUID()) == drained)
				+ " ticking=" + end.isPositionEntityTicking(drained.blockPosition()) + " tickCount=" + drained.tickCount))
			.thenIdle(20)
			.thenExecute(() -> {
				float lost = start[0] - drained.getHealth();
				float healed = drainer.getHealth() - WOUND;
				EndTestSupport.result("drainTick", "lost=" + lost + " healed=" + healed + " heals=" + WitherDrain.heals().size());
				EndTestSupport.fresh(helper);
				helper.assertTrue(lost > 0.0F && Math.abs(healed - lost) < 1.0E-3F, "the wither took " + lost + " and the attacker healed " + healed);
			})
			.thenSucceed();
	}
}
