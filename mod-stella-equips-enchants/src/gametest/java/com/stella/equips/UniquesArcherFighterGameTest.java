package com.stella.equips;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.Uniques;
import com.stella.equips.unique.af.AncientEyeBow;
import com.stella.equips.unique.af.MireBow;
import com.stella.equips.unique.af.NightwingBow;
import com.stella.equips.unique.af.AfChance;
import com.stella.equips.unique.af.WailingBow;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.stella.equips.unique.af.RimefrostBow;
import com.stella.equips.unique.af.SunfireBow;
import com.stella.equips.unique.af.SunscorchBow;
import com.stella.equips.unique.af.WitherboneLongbow;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;

/** Outcome tests for the archer and fighter uniques. */
public class UniquesArcherFighterGameTest {
	private static final Map<UUID, Boolean> CHANCE = new ConcurrentHashMap<>();

	static {
		AfChance.setRoller((holder, id, chance) -> {
			Boolean forced = CHANCE.get(holder.getUUID());
			return forced != null ? forced : holder.getRandom().nextDouble() < chance;
		});
	}

	private static UniqueContext context(final GameTestHelper helper, final String id, final int refine) {
		UniqueDef def = Uniques.byId(id);
		helper.assertTrue(def != null, "missing unique " + id);
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(1.5, 2.0, 1.5));
		return new UniqueContext(holder, UniqueTestKit.stack(def, refine), refine);
	}

	private static Mob foe(final GameTestHelper helper, final double x) {
		return helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new Vec3(x, 2.0, 3.5));
	}

	private static void potionBase(final GameTestHelper helper, final String id, final Holder<MobEffect> effect,
		final int ticks, final int amplifier) {
		UniqueContext c = context(helper, id, 0);
		Mob target = foe(helper, 3.5);
		Uniques.byId(id).effect().afterArrow(c, target, null, 4.0F, false);
		MobEffectInstance actual = target.getEffect(effect);
		UniqueTestKit.result(id + "Base", "effect=" + actual);
		helper.assertTrue(actual != null && actual.getAmplifier() == amplifier && actual.getDuration() == ticks, id + " base effect differs");
		helper.succeed();
	}

	private static void spreadAt15(final GameTestHelper helper, final String id, final Holder<MobEffect> effect,
		final int ticks) {
		UniqueContext low = context(helper, id, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, id, Uniques.PLUS_15);
		Mob victimLow = foe(helper, 2.5);
		Mob nearLow = foe(helper, 3.5);
		Mob victimHigh = foe(helper, 8.5);
		Mob nearHigh = foe(helper, 9.5);
		victimLow.addEffect(new MobEffectInstance(effect, 100));
		victimHigh.addEffect(new MobEffectInstance(effect, 100));
		UniqueDef def = Uniques.byId(id);
		def.effect().afterArrow(low, victimLow, null, 4.0F, true);
		def.effect().afterArrow(high, victimHigh, null, 4.0F, true);
		MobEffectInstance applied = nearHigh.getEffect(effect);
		UniqueTestKit.result(id + "Plus15", "low=" + nearLow.hasEffect(effect) + " high=" + applied);
		helper.assertTrue(!nearLow.hasEffect(effect) && applied != null && applied.getDuration() == ticks, id + " +15 spread differs");
		helper.succeed();
	}

	@GameTest public void rimefrostBase(final GameTestHelper helper) {
		potionBase(helper, RimefrostBow.ID, MobEffects.SLOWNESS, RimefrostBow.TUNING.ticks(), 0);
	}

	@GameTest public void rimefrostPlus15(final GameTestHelper helper) {
		spreadAt15(helper, RimefrostBow.ID, MobEffects.SLOWNESS, RimefrostBow.TUNING.killTicks());
	}

	@GameTest public void mireBase(final GameTestHelper helper) {
		potionBase(helper, MireBow.ID, MobEffects.POISON, MireBow.TUNING.ticks(), 0);
	}

	@GameTest public void mirePlus15(final GameTestHelper helper) {
		UniqueContext low = context(helper, MireBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, MireBow.ID, Uniques.PLUS_15);
		Mob target = foe(helper, 3.5);
		target.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
		UniqueDef def = Uniques.byId(MireBow.ID);
		float below = def.effect().outgoingArrow(low, target, null, 10.0F);
		float at = def.effect().outgoingArrow(high, target, null, 10.0F);
		UniqueTestKit.result("mirePlus15", "below=" + below + " at=" + at);
		helper.assertTrue(below == 10.0F && Math.abs(at - 10.0F * (1.0F + (float) MireBow.TUNING.poisonedBonus())) < 0.001F, "Mire damage gate differs");
		helper.succeed();
	}

	@GameTest public void sunscorchBase(final GameTestHelper helper) {
		UniqueContext c = context(helper, SunscorchBow.ID, 0);
		Mob target = foe(helper, 3.5);
		Uniques.byId(SunscorchBow.ID).effect().afterArrow(c, target, null, 4.0F, false);
		UniqueTestKit.result("sunscorchBase", "fire=" + target.getRemainingFireTicks());
		helper.assertTrue(target.getRemainingFireTicks() >= 20 * SunscorchBow.TUNING.seconds(), "Sunscorch fire differs");
		helper.succeed();
	}

	@GameTest public void sunscorchPlus15(final GameTestHelper helper) {
		UniqueContext low = context(helper, SunscorchBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, SunscorchBow.ID, Uniques.PLUS_15);
		Mob victimLow = foe(helper, 2.5);
		Mob nearLow = foe(helper, 3.5);
		Mob victimHigh = foe(helper, 8.5);
		Mob nearHigh = foe(helper, 9.5);
		victimLow.igniteForSeconds(5);
		victimHigh.igniteForSeconds(5);
		float beforeLow = nearLow.getHealth();
		float beforeHigh = nearHigh.getHealth();
		UniqueDef def = Uniques.byId(SunscorchBow.ID);
		def.effect().afterArrow(low, victimLow, null, 8.0F, true);
		def.effect().afterArrow(high, victimHigh, null, 8.0F, true);
		UniqueTestKit.result("sunscorchPlus15", "low=" + nearLow.getHealth() + " high=" + nearHigh.getHealth());
		helper.assertTrue(nearLow.getHealth() == beforeLow && nearHigh.getHealth() < beforeHigh, "Sunscorch burst gate differs");
		helper.succeed();
	}

	@GameTest public void witherboneBase(final GameTestHelper helper) {
		potionBase(helper, WitherboneLongbow.ID, MobEffects.WITHER, WitherboneLongbow.TUNING.ticks(), WitherboneLongbow.TUNING.amplifier());
	}

	@GameTest public void witherbonePlus15(final GameTestHelper helper) {
		spreadAt15(helper, WitherboneLongbow.ID, MobEffects.WITHER, WitherboneLongbow.TUNING.killTicks());
	}

	@GameTest public void ancientEyeBase(final GameTestHelper helper) {
		potionBase(helper, AncientEyeBow.ID, MobEffects.MINING_FATIGUE, AncientEyeBow.TUNING.ticks(), AncientEyeBow.TUNING.amplifier());
	}

	@GameTest public void ancientEyePlus15(final GameTestHelper helper) {
		UniqueContext low = context(helper, AncientEyeBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, AncientEyeBow.ID, Uniques.PLUS_15);
		Mob targetLow = foe(helper, 3.5);
		Mob targetHigh = foe(helper, 8.5);
		UniqueDef def = Uniques.byId(AncientEyeBow.ID);
		def.effect().afterArrow(low, targetLow, null, 4.0F, false);
		def.effect().afterArrow(high, targetHigh, null, 4.0F, false);
		MobEffectInstance weak = targetHigh.getEffect(MobEffects.WEAKNESS);
		UniqueTestKit.result("ancientEyePlus15", "low=" + targetLow.hasEffect(MobEffects.WEAKNESS) + " high=" + weak);
		helper.assertTrue(!targetLow.hasEffect(MobEffects.WEAKNESS) && weak != null && weak.getDuration() == AncientEyeBow.TUNING.weaknessTicks(), "Ancient Eye gate differs");
		helper.succeed();
	}

	@GameTest public void sunfireBase(final GameTestHelper helper) {
		UniqueContext c = context(helper, SunfireBow.ID, 0);
		Mob target = foe(helper, 3.5);
		UniqueDef def = Uniques.byId(SunfireBow.ID);
		float before = def.effect().outgoingArrow(c, target, null, 10.0F);
		def.effect().afterArrow(c, target, null, 4.0F, false);
		float after = def.effect().outgoingArrow(c, target, null, 10.0F);
		UniqueTestKit.result("sunfireBase", "before=" + before + " after=" + after + " fire=" + target.getRemainingFireTicks());
		helper.assertTrue(before == 10.0F && target.isOnFire() && Math.abs(after - 10.0F * (1.0F + (float) SunfireBow.TUNING.bonus())) < 0.001F, "Sunfire base differs");
		helper.succeed();
	}

	@GameTest public void sunfirePlus15(final GameTestHelper helper) {
		UniqueContext low = context(helper, SunfireBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, SunfireBow.ID, Uniques.PLUS_15);
		var blaze = helper.spawnWithNoFreeWill(EntityTypes.BLAZE, new Vec3(3.5, 2.0, 3.5));
		UniqueDef def = Uniques.byId(SunfireBow.ID);
		float below = def.effect().outgoingArrow(low, blaze, null, 10.0F);
		float at = def.effect().outgoingArrow(high, blaze, null, 10.0F);
		UniqueTestKit.result("sunfirePlus15", "below=" + below + " at=" + at);
		helper.assertTrue(below == 10.0F && Math.abs(at - 10.0F * (1.0F + (float) SunfireBow.TUNING.bonusPlus12())) < 0.001F, "Sunfire fire immunity gate differs");
		helper.succeed();
	}

	@GameTest public void nightwingBaseBoostsOnlyAirborneArrow(final GameTestHelper helper) {
		UniqueContext c = context(helper, NightwingBow.ID, 0);
		Mob target = foe(helper, 3.5);
		Arrow ground = new Arrow(helper.getLevel(), c.holder(), new ItemStack(Items.ARROW), c.stack());
		Arrow air = new Arrow(helper.getLevel(), c.holder(), new ItemStack(Items.ARROW), c.stack());
		c.holder().setOnGround(true);
		Uniques.byId(NightwingBow.ID).effect().onShot(c, ground, 3.0F);
		c.holder().setOnGround(false);
		Uniques.byId(NightwingBow.ID).effect().onShot(c, air, 3.0F);
		float low = Uniques.byId(NightwingBow.ID).effect().outgoingArrow(c, target, ground, 10.0F);
		float high = Uniques.byId(NightwingBow.ID).effect().outgoingArrow(c, target, air, 10.0F);
		UniqueTestKit.result("nightwingBase", "ground=" + low + " air=" + high);
		helper.assertTrue(low == 10.0F && Math.abs(high - 10.0F * (1.0F + (float) NightwingBow.TUNING.bonus())) < 0.001F, "airborne damage differs");
		helper.succeed();
	}

	@GameTest public void nightwingPlus15MarksCriticalOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = context(helper, NightwingBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, NightwingBow.ID, Uniques.PLUS_15);
		low.holder().setOnGround(false);
		high.holder().setOnGround(false);
		Arrow below = new Arrow(helper.getLevel(), low.holder(), new ItemStack(Items.ARROW), low.stack());
		Arrow at = new Arrow(helper.getLevel(), high.holder(), new ItemStack(Items.ARROW), high.stack());
		Uniques.byId(NightwingBow.ID).effect().onShot(low, below, 2.0F);
		Uniques.byId(NightwingBow.ID).effect().onShot(high, at, 2.0F);
		UniqueTestKit.result("nightwingPlus15", "below=" + below.isCritArrow() + " at=" + at.isCritArrow());
		helper.assertTrue(!below.isCritArrow() && at.isCritArrow(), "critical arrow gate differs");
		helper.succeed();
	}

	@GameTest public void wailingBaseBurstsOnPassingRoll(final GameTestHelper helper) {
		UniqueContext c = context(helper, WailingBow.ID, 0);
		CHANCE.put(c.holder().getUUID(), true);
		Mob victim = foe(helper, 3.5);
		Mob near = foe(helper, 4.5);
		float before = near.getHealth();
		Arrow shot = new Arrow(helper.getLevel(), c.holder(), new ItemStack(Items.ARROW), c.stack());
		Uniques.byId(WailingBow.ID).effect().onShot(c, shot, 3.0F);
		Uniques.byId(WailingBow.ID).effect().afterArrow(c, victim, shot, 8.0F, false);
		UniqueTestKit.result("wailingBase", "before=" + before + " after=" + near.getHealth());
		helper.assertTrue(near.getHealth() < before, "passing burst did not damage nearby foe");
		helper.succeed();
	}

	@GameTest public void wailingPlus15BurstsEveryFourthOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = context(helper, WailingBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, WailingBow.ID, Uniques.PLUS_15);
		CHANCE.put(low.holder().getUUID(), false);
		CHANCE.put(high.holder().getUUID(), false);
		Mob victimLow = foe(helper, 2.5);
		Mob victimHigh = foe(helper, 8.5);
		UniqueDef def = Uniques.byId(WailingBow.ID);
		for (int hit = 1; hit < WailingBow.TUNING.guaranteedEvery(); hit++) {
			def.effect().onShot(low, new Arrow(helper.getLevel(), low.holder(), new ItemStack(Items.ARROW), low.stack()), 3.0F);
			def.effect().onShot(high, new Arrow(helper.getLevel(), high.holder(), new ItemStack(Items.ARROW), high.stack()), 3.0F);
		}
		Mob nearLow = foe(helper, 3.5);
		Mob nearHigh = foe(helper, 9.5);
		float beforeLow = nearLow.getHealth();
		float beforeHigh = nearHigh.getHealth();
		Arrow lowFourth = new Arrow(helper.getLevel(), low.holder(), new ItemStack(Items.ARROW), low.stack());
		Arrow highFourth = new Arrow(helper.getLevel(), high.holder(), new ItemStack(Items.ARROW), high.stack());
		def.effect().onShot(low, lowFourth, 3.0F);
		def.effect().onShot(high, highFourth, 3.0F);
		def.effect().afterArrow(low, victimLow, lowFourth, 8.0F, false);
		def.effect().afterArrow(high, victimHigh, highFourth, 8.0F, false);
		UniqueTestKit.result("wailingPlus15", "low=" + nearLow.getHealth() + " high=" + nearHigh.getHealth());
		helper.assertTrue(nearLow.getHealth() == beforeLow && nearHigh.getHealth() < beforeHigh, "fourth burst gate differs");
		helper.succeed();
	}
}
