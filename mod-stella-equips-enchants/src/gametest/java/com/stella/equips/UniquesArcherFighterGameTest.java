package com.stella.equips;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueFields;
import com.stella.equips.unique.UniqueArrows;
import com.stella.equips.unique.UniqueCooldowns;
import com.stella.equips.unique.Uniques;
import com.stella.equips.unique.af.AncientEyeBow;
import com.stella.equips.unique.af.MireBow;
import com.stella.equips.unique.af.NightwingBow;
import com.stella.equips.unique.af.AfChance;
import com.stella.equips.unique.af.WailingBow;
import com.stella.equips.unique.af.LevitatorBow;
import com.stella.equips.unique.af.DragonbreathBow;
import com.stella.equips.unique.af.SonicBow;
import com.stella.equips.unique.af.PhasebiteBow;
import com.stella.equips.unique.af.RaiderRepeater;
import com.stella.equips.unique.af.GaleshotCrossbow;
import com.stella.equips.unique.af.TidewatcherCrossbow;
import com.stella.equips.unique.af.ForemanArbalest;
import com.stella.equips.unique.af.RotgutCleaver;
import com.stella.equips.unique.af.JohnnysAxe;
import com.stella.equips.unique.af.VenomDirk;
import com.stella.equips.unique.af.BurrowerShiv;
import com.stella.equips.unique.af.WitheringGreatsword;
import com.stella.equips.unique.UniqueRank;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Method;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
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
		return context(helper, id, refine, 1.5);
	}

	private static UniqueContext context(final GameTestHelper helper, final String id, final int refine, final double x) {
		UniqueDef def = Uniques.byId(id);
		helper.assertTrue(def != null, "missing unique " + id);
		ServerPlayer holder = UniqueTestKit.survivor(helper, new Vec3(x, 2.0, 1.5));
		return new UniqueContext(holder, UniqueTestKit.stack(def, refine), refine);
	}

	private static UniqueContext connectedContext(final GameTestHelper helper, final String id, final int refine, final double x) {
		UniqueDef def = Uniques.byId(id);
		helper.assertTrue(def != null, "missing unique " + id);
		ServerPlayer holder = UniqueTestKit.connected(helper, new Vec3(x, 2.0, 1.5));
		return new UniqueContext(holder, UniqueTestKit.stack(def, refine), refine);
	}

	private static Mob foe(final GameTestHelper helper, final double x) {
		return foeAt(helper, x, 3.5);
	}

	private static Mob foeAt(final GameTestHelper helper, final double x, final double z) {
		return helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new Vec3(x, 2.0, z));
	}

	private static int arrows(final ServerPlayer holder) {
		int count = 0;
		for (int slot = 0; slot < holder.getInventory().getContainerSize(); slot++) {
			ItemStack stack = holder.getInventory().getItem(slot);
			if (stack.is(Items.ARROW)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static Arrow shot(final GameTestHelper helper, final UniqueContext c) {
		Arrow arrow = new Arrow(helper.getLevel(), c.holder(), new ItemStack(Items.ARROW), c.stack());
		arrow.setDeltaMovement(0.0, 0.0, 3.0);
		arrow.setBaseDamage(2.0);
		return arrow;
	}

	private static AbstractArrow fireReal(final GameTestHelper helper, final UniqueContext c) {
		ServerPlayer holder = c.holder();
		holder.setYRot(0.0F);
		holder.setXRot(0.0F);
		holder.setItemInHand(InteractionHand.MAIN_HAND, c.stack());
		holder.getInventory().add(new ItemStack(Items.ARROW, 8));
		ItemStack bow = holder.getMainHandItem();
		bow.getItem().releaseUsing(bow, holder.level(), holder, 0);
		List<AbstractArrow> arrows = UniqueTestKit.arrowsOf(holder.level(), holder, holder.position());
		helper.assertTrue(arrows.size() == 1, "the real bow loosed " + arrows.size() + " arrows");
		return arrows.get(0);
	}

	private static void impact(final AbstractArrow arrow, final Mob target) throws ReflectiveOperationException {
		Method hit = AbstractArrow.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
		hit.setAccessible(true);
		hit.invoke(arrow, new EntityHitResult(target));
	}

	private static List<AbstractArrow> fireCrossbow(final GameTestHelper helper, final UniqueContext c) {
		ServerPlayer holder = c.holder();
		holder.setYRot(0.0F);
		holder.setXRot(0.0F);
		holder.setItemInHand(InteractionHand.MAIN_HAND, c.stack());
		c.stack().set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
		((CrossbowItem) c.stack().getItem()).performShooting(holder.level(), holder, InteractionHand.MAIN_HAND,
			c.stack(), CrossbowItem.MOB_ARROW_POWER, 0.0F, null);
		return UniqueTestKit.arrowsOf(holder.level(), holder, holder.position());
	}

	private static AbstractArrow oneBolt(final GameTestHelper helper, final UniqueContext c) {
		List<AbstractArrow> bolts = fireCrossbow(helper, c);
		helper.assertTrue(bolts.size() == 1, "expected one bolt, got " + bolts.size());
		return bolts.get(0);
	}

	private static Mob durableFoe(final GameTestHelper helper, final double x, final double z) {
		Mob target = foeAt(helper, x, z);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		target.setHealth(target.getMaxHealth());
		return target;
	}

	private static float melee(final GameTestHelper helper, final UniqueContext c, final Mob target, final float amount) {
		c.holder().setItemInHand(InteractionHand.MAIN_HAND, c.stack());
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		boolean hurt = target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(c.holder()), amount);
		helper.assertTrue(hurt && target.getHealth() < before, "the real melee damage must reach its subject");
		return before - target.getHealth();
	}

	private static float armoredDamage(final GameTestHelper helper, final UniqueContext c, final Mob target, final float amount) {
		return CombatRules.getDamageAfterAbsorb(target, amount, helper.getLevel().damageSources().playerAttack(c.holder()),
			target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
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

	@GameTest public void levitatorBaseLiftsOnPassingRoll(final GameTestHelper helper) {
		UniqueContext c = context(helper, LevitatorBow.ID, 0);
		CHANCE.put(c.holder().getUUID(), true);
		Mob target = foe(helper, 3.5);
		Uniques.byId(LevitatorBow.ID).effect().afterArrow(c, target, shot(helper, c), 6.0F, false);
		MobEffectInstance lift = target.getEffect(MobEffects.LEVITATION);
		UniqueTestKit.result("levitatorBase", "lift=" + lift);
		helper.assertTrue(lift != null && lift.getDuration() == LevitatorBow.TUNING.ticks(), "passing lift differs");
		helper.succeed();
	}

	@GameTest public void levitatorPlus15GivesArrowOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = context(helper, LevitatorBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, LevitatorBow.ID, Uniques.PLUS_15);
		CHANCE.put(low.holder().getUUID(), false);
		CHANCE.put(high.holder().getUUID(), false);
		Mob targetLow = foe(helper, 3.5);
		Mob targetHigh = foe(helper, 8.5);
		targetLow.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100));
		targetHigh.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100));
		Arrow lowArrow = shot(helper, low);
		Arrow highArrow = shot(helper, high);
		lowArrow.setCritArrow(true);
		highArrow.setCritArrow(true);
		UniqueDef def = Uniques.byId(LevitatorBow.ID);
		def.effect().afterArrow(low, targetLow, lowArrow, 6.0F, false);
		def.effect().afterArrow(high, targetHigh, highArrow, 6.0F, false);
		UniqueTestKit.result("levitatorPlus15", "below=" + arrows(low.holder()) + " at=" + arrows(high.holder()));
		helper.assertTrue(arrows(low.holder()) == 0 && arrows(high.holder()) == 1, "critical arrow grant gate differs");
		helper.succeed();
	}

	@GameTest public void dragonbreathBaseCreatesDamagingField(final GameTestHelper helper) {
		UniqueContext c = context(helper, DragonbreathBow.ID, 0);
		CHANCE.put(c.holder().getUUID(), true);
		Mob target = foe(helper, 3.5);
		Uniques.byId(DragonbreathBow.ID).effect().afterArrow(c, target, shot(helper, c), 10.0F, false);
		var field = UniqueFields.active().stream().filter(f -> f.center().equals(target.position())).findFirst().orElse(null);
		UniqueTestKit.result("dragonbreathBase", "field=" + field);
		helper.assertTrue(field != null && field.radius() == DragonbreathBow.TUNING.radius()
			&& field.ticksLeft() == DragonbreathBow.TUNING.ticks()
			&& field.perSecond() == 10.0F * DragonbreathBow.TUNING.damageShare(), "breath field differs");
		helper.succeed();
	}

	@GameTest(maxTicks = 80) public void dragonbreathPlus15PullsOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = context(helper, DragonbreathBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, DragonbreathBow.ID, Uniques.PLUS_15);
		CHANCE.put(low.holder().getUUID(), true);
		CHANCE.put(high.holder().getUUID(), true);
		Mob centerLow = foeAt(helper, 2.5, 3.5);
		Mob nearLow = foeAt(helper, 2.5, 5.5);
		Mob centerHigh = foeAt(helper, 6.5, 3.5);
		Mob nearHigh = foeAt(helper, 6.5, 5.5);
		double lowStart = nearLow.getZ();
		double highStart = nearHigh.getZ();
		UniqueDef def = Uniques.byId(DragonbreathBow.ID);
		def.effect().afterArrow(low, centerLow, shot(helper, low), 10.0F, false);
		def.effect().afterArrow(high, centerHigh, shot(helper, high), 10.0F, false);
		helper.runAfterDelay(30L, () -> {
			UniqueTestKit.result("dragonbreathPlus15", "lowDz=" + (nearLow.getZ() - lowStart) + " highDz=" + (nearHigh.getZ() - highStart)
				+ " highHealth=" + nearHigh.getHealth());
			double pulled = highStart - nearHigh.getZ();
			helper.assertTrue(Math.abs(nearLow.getZ() - lowStart) < 0.1
				&& Math.abs(pulled - DragonbreathBow.TUNING.pullBlocks()) <= 0.1, "breath pull gate differs");
			helper.succeed();
		});
	}

	@GameTest public void sonicBaseFifthFullDrawPiercesArmor(final GameTestHelper helper) {
		UniqueContext c = context(helper, SonicBow.ID, 0);
		c.holder().setYRot(0.0F);
		c.holder().setXRot(0.0F);
		Mob target = foe(helper, 1.5);
		target.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		float before = target.getHealth();
		UniqueDef def = Uniques.byId(SonicBow.ID);
		for (int count = 1; count <= SonicBow.TUNING.every(); count++) {
			def.effect().onShot(c, shot(helper, c), 3.0F);
			if (count < SonicBow.TUNING.every()) {
				helper.assertTrue(target.getHealth() == before, "sonic beam fired before fifth shot");
			}
		}
		UniqueTestKit.result("sonicBase", "lost=" + (before - target.getHealth()));
		helper.assertTrue(Math.abs(before - target.getHealth() - 6.0F * SonicBow.TUNING.damageShare()) < 0.001F, "fifth sonic beam or armor pierce differs");
		helper.succeed();
	}

	@GameTest public void sonicPlus15FiresEveryThirdOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = context(helper, SonicBow.ID, Uniques.PLUS_15 - 1);
		UniqueContext high = context(helper, SonicBow.ID, Uniques.PLUS_15, 8.5);
		low.holder().setYRot(0.0F);
		low.holder().setXRot(0.0F);
		high.holder().setYRot(0.0F);
		high.holder().setXRot(0.0F);
		Mob targetLow = foe(helper, 1.5);
		Mob targetHigh = foe(helper, 8.5);
		float beforeLow = targetLow.getHealth();
		float beforeHigh = targetHigh.getHealth();
		UniqueDef def = Uniques.byId(SonicBow.ID);
		for (int count = 0; count < SonicBow.TUNING.everyPlus15(); count++) {
			def.effect().onShot(low, shot(helper, low), 3.0F);
			def.effect().onShot(high, shot(helper, high), 3.0F);
		}
		UniqueTestKit.result("sonicPlus15", "low=" + targetLow.getHealth() + " high=" + targetHigh.getHealth());
		helper.assertTrue(targetLow.getHealth() == beforeLow && targetHigh.getHealth() < beforeHigh, "sonic interval gate differs");
		helper.succeed();
	}

	@GameTest public void realRimefrostArrowAppliesSlowness(final GameTestHelper helper) {
		UniqueContext c = context(helper, RimefrostBow.ID, 0);
		AbstractArrow arrow = fireReal(helper, c);
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		Mob target = foe(helper, 1.5);
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, c.holder()), 6.0F);
		MobEffectInstance slow = target.getEffect(MobEffects.SLOWNESS);
		UniqueTestKit.result("realRimefrost", "mark=" + mark + " slow=" + slow);
		helper.assertTrue(mark != null && mark.id().equals(RimefrostBow.ID) && slow != null, "real arrow missed Slowness dispatch");
		helper.succeed();
	}

	@GameTest public void realSunscorchKillingArrowBursts(final GameTestHelper helper) {
		UniqueContext c = context(helper, SunscorchBow.ID, Uniques.PLUS_15);
		AbstractArrow arrow = fireReal(helper, c);
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		Mob victim = foe(helper, 1.5);
		Mob near = foe(helper, 2.5);
		victim.setHealth(4.0F);
		victim.igniteForSeconds(5);
		float before = near.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, c.holder()), 30.0F);
		UniqueTestKit.result("realSunscorchKill", "mark=" + mark + " dead=" + !victim.isAlive() + " areaLoss=" + (before - near.getHealth()));
		helper.assertTrue(mark != null && mark.id().equals(SunscorchBow.ID) && !victim.isAlive() && near.getHealth() < before,
			"real killing arrow missed the area dispatch");
		helper.succeed();
	}

	@GameTest public void realNightwingShotMarksCritical(final GameTestHelper helper) {
		UniqueDef def = Uniques.byId(NightwingBow.ID);
		ServerPlayer holder = UniqueTestKit.connected(helper, new Vec3(1.5, 2.0, 1.5));
		UniqueContext c = new UniqueContext(holder, UniqueTestKit.stack(def, Uniques.PLUS_15), Uniques.PLUS_15);
		holder.setOnGround(false);
		AbstractArrow arrow = fireReal(helper, c);
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		UniqueTestKit.result("realNightwingShot", "mark=" + mark + " crit=" + arrow.isCritArrow()
			+ " slowFalling=" + holder.hasEffect(MobEffects.SLOW_FALLING));
		helper.assertTrue(mark != null && mark.id().equals(NightwingBow.ID) && arrow.isCritArrow()
			&& holder.hasEffect(MobEffects.SLOW_FALLING), "real airborne shot missed the shot hook");
		helper.succeed();
	}

	private static void sonicEnchantedShotMatchesArrow(final GameTestHelper helper, final int refine) throws ReflectiveOperationException {
		UniqueContext c = context(helper, SonicBow.ID, refine);
		c.stack().enchant(UniqueTestKit.enchantment(helper.getLevel(), Enchantments.POWER), 5);
		Mob target = foe(helper, 1.5);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		target.setHealth(500.0F);
		UniqueDef def = Uniques.byId(SonicBow.ID);
		for (int count = 1; count < SonicBow.every(refine); count++) {
			def.effect().onShot(c, shot(helper, c), 3.0F);
		}
		AbstractArrow arrow = fireReal(helper, c);
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		float beamLoss = 500.0F - target.getHealth();
		impact(arrow, target);
		float arrowLoss = 500.0F - target.getHealth() - beamLoss;
		UniqueTestKit.result("sonicEnchanted" + refine, "mark=" + mark + " beam=" + beamLoss + " arrow=" + arrowLoss);
		helper.assertTrue(mark != null && mark.id().equals(SonicBow.ID) && arrow.isCritArrow(), "the full-draw Sonic arrow lost its mark or critical state");
		helper.assertTrue(beamLoss > 6.0F * SonicBow.share(refine) && arrowLoss > 6.0F,
			"Power did not raise the beam and arrow above the plain shot");
		helper.assertTrue(Math.abs(beamLoss - arrowLoss * SonicBow.share(refine)) < 0.01F,
			"beam share differs from the actual arrow: " + beamLoss + " vs " + arrowLoss);
		helper.succeed();
	}

	@GameTest public void sonicRealPowerArrowMatchesBaseBeam(final GameTestHelper helper) throws ReflectiveOperationException {
		sonicEnchantedShotMatchesArrow(helper, 0);
	}

	@GameTest public void sonicRealPowerArrowMatchesPlus12Beam(final GameTestHelper helper) throws ReflectiveOperationException {
		sonicEnchantedShotMatchesArrow(helper, Uniques.PLUS_12);
	}

	@GameTest public void phasebiteBaseTeleportsOnArrowImpactAndStartsCooldown(final GameTestHelper helper) throws ReflectiveOperationException {
		UniqueContext c = connectedContext(helper, PhasebiteBow.ID, 0, 1.5);
		c.holder().setShiftKeyDown(true);
		Mob target = foeAt(helper, 1.5, 5.5);
		Vec3 origin = c.holder().position();
		Vec3 landing = target.position();
		AbstractArrow arrow = fireReal(helper, c);
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		impact(arrow, target);
		boolean cooling = !UniqueCooldowns.ready(c.holder(), PhasebiteBow.ID, PhasebiteBow.TUNING.cooldownTicks());
		UniqueTestKit.result("phasebiteBase", "mark=" + mark + " origin=1.5 landing=" + landing + " holder=" + c.holder().position() + " cooling=" + cooling);
		helper.assertTrue(mark != null && mark.id().equals(PhasebiteBow.ID) && c.holder().position().distanceTo(origin) > 3.0
			&& Math.abs(c.holder().getX() - landing.x) < 1.0
			&& Math.abs(c.holder().getZ() - landing.z) < 1.0 && cooling, "sneaking arrow missed the teleport or cooldown");
		helper.succeed();
	}

	@GameTest public void phasebitePlus15GivesSpeedOnlyAtGate(final GameTestHelper helper) throws ReflectiveOperationException {
		UniqueContext low = connectedContext(helper, PhasebiteBow.ID, Uniques.PLUS_15 - 1, 2.5);
		UniqueContext high = connectedContext(helper, PhasebiteBow.ID, Uniques.PLUS_15, 6.5);
		low.holder().setShiftKeyDown(true);
		high.holder().setShiftKeyDown(true);
		Mob targetLow = foeAt(helper, 2.5, 5.5);
		Mob targetHigh = foeAt(helper, 6.5, 5.5);
		impact(fireReal(helper, low), targetLow);
		impact(fireReal(helper, high), targetHigh);
		MobEffectInstance speed = high.holder().getEffect(MobEffects.SPEED);
		UniqueTestKit.result("phasebitePlus15", "low=" + low.holder().getEffect(MobEffects.SPEED) + " high=" + speed);
		helper.assertTrue(!low.holder().hasEffect(MobEffects.SPEED) && speed != null
			&& speed.getAmplifier() == PhasebiteBow.TUNING.speedAmplifier()
			&& speed.getDuration() == PhasebiteBow.TUNING.speedTicks(), "teleport Speed gate differs");
		helper.succeed();
	}

	@GameTest public void phasebitePlus10PreventsFallAfterWeaponSwitch(final GameTestHelper helper) throws ReflectiveOperationException {
		UniqueContext low = connectedContext(helper, PhasebiteBow.ID, Uniques.PLUS_10 - 1, 2.5);
		UniqueContext high = connectedContext(helper, PhasebiteBow.ID, Uniques.PLUS_10, 6.5);
		low.holder().setShiftKeyDown(true);
		high.holder().setShiftKeyDown(true);
		impact(fireReal(helper, low), foeAt(helper, 2.5, 5.5));
		impact(fireReal(helper, high), foeAt(helper, 6.5, 5.5));
		low.holder().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		high.holder().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		float lowBefore = low.holder().getHealth();
		float highBefore = high.holder().getHealth();
		low.holder().hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 6.0F);
		high.holder().hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 6.0F);
		UniqueTestKit.result("phasebitePlus10", "belowLoss=" + (lowBefore - low.holder().getHealth())
			+ " atLoss=" + (highBefore - high.holder().getHealth()));
		helper.assertTrue(low.holder().getHealth() < lowBefore && high.holder().getHealth() == highBefore,
			"post-teleport fall safety gate differs after switching the bow");
		helper.succeed();
	}

	@GameTest public void raiderBaseKeepsRealCrossbowLoadedOnPassingRoll(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, RaiderRepeater.ID, 0, 1.5);
		CHANCE.put(c.holder().getUUID(), true);
		AbstractArrow bolt = oneBolt(helper, c);
		boolean loaded = CrossbowItem.isCharged(c.stack());
		UniqueTestKit.result("raiderBase", "loaded=" + loaded + " mark=" + UniqueArrows.of(bolt));
		helper.assertTrue(loaded && RaiderRepeater.ID.equals(UniqueArrows.of(bolt).id()), "passing shot unloaded the repeater");
		helper.succeed();
	}

	@GameTest public void raiderPlus15BoostsOnlySprintShotsAtGate(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, RaiderRepeater.ID, Uniques.PLUS_15 - 1, 2.5);
		UniqueContext high = connectedContext(helper, RaiderRepeater.ID, Uniques.PLUS_15, 6.5);
		CHANCE.put(low.holder().getUUID(), false);
		CHANCE.put(high.holder().getUUID(), false);
		low.holder().setSprinting(true);
		high.holder().setSprinting(true);
		AbstractArrow below = oneBolt(helper, low);
		AbstractArrow at = oneBolt(helper, high);
		low.holder().setSprinting(false);
		high.holder().setSprinting(false);
		Mob target = foe(helper, 9.5);
		UniqueDef def = Uniques.byId(RaiderRepeater.ID);
		float lowDamage = def.effect().outgoingArrow(low, target, below, 10.0F);
		float highDamage = def.effect().outgoingArrow(high, target, at, 10.0F);
		UniqueTestKit.result("raiderPlus15", "below=" + lowDamage + " at=" + highDamage);
		helper.assertTrue(lowDamage == 10.0F && Math.abs(highDamage - 10.0F * (1 + RaiderRepeater.TUNING.sprintBonus())) < 0.001,
			"sprint shot damage gate differs after sprint ends");
		helper.succeed();
	}

	@GameTest public void raiderPlus10AddsPierceOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, RaiderRepeater.ID, Uniques.PLUS_10 - 1, 2.5);
		UniqueContext high = connectedContext(helper, RaiderRepeater.ID, Uniques.PLUS_10, 6.5);
		CHANCE.put(low.holder().getUUID(), false);
		CHANCE.put(high.holder().getUUID(), false);
		AbstractArrow below = oneBolt(helper, low);
		AbstractArrow at = oneBolt(helper, high);
		UniqueTestKit.result("raiderPlus10", "below=" + below.getPierceLevel() + " at=" + at.getPierceLevel());
		helper.assertTrue(below.getPierceLevel() == 0 && at.getPierceLevel() == RaiderRepeater.TUNING.piercePlus(), "piercing gate differs");
		helper.succeed();
	}

	@GameTest public void galeshotBaseDoublesActualBoltKnockback(final GameTestHelper helper) throws ReflectiveOperationException {
		UniqueContext plain = connectedContext(helper, GaleshotCrossbow.ID, 0, 2.5);
		UniqueContext gale = connectedContext(helper, GaleshotCrossbow.ID, 0, 6.5);
		Mob plainTarget = foeAt(helper, 2.5, 5.5);
		Mob galeTarget = foeAt(helper, 6.5, 5.5);
		AbstractArrow plainBolt = oneBolt(helper, plain);
		AbstractArrow galeBolt = oneBolt(helper, gale);
		plainBolt.setCritArrow(false);
		galeBolt.setCritArrow(false);
		UniqueArrows.mark(plainBolt, "test_no_gale", 0);
		impact(plainBolt, plainTarget);
		impact(galeBolt, galeTarget);
		double reference = plainTarget.getDeltaMovement().horizontalDistance();
		double actual = galeTarget.getDeltaMovement().horizontalDistance();
		UniqueTestKit.result("galeshotBase", "plain=" + reference + " gale=" + actual);
		helper.assertTrue(reference > 0 && Math.abs(actual - reference * GaleshotCrossbow.TUNING.knockback()) < 0.001,
			"real bolt did not double horizontal knockback");
		helper.succeed();
	}

	@GameTest public void galeshotPlus15LiftsNearbyFoesOnlyAtGate(final GameTestHelper helper) throws ReflectiveOperationException {
		UniqueContext low = connectedContext(helper, GaleshotCrossbow.ID, Uniques.PLUS_15 - 1, 2.5);
		UniqueContext high = connectedContext(helper, GaleshotCrossbow.ID, Uniques.PLUS_15, 8.5);
		Mob lowTarget = foeAt(helper, 2.5, 5.5);
		Mob highTarget = foeAt(helper, 8.5, 5.5);
		Mob lowNear = foeAt(helper, 3.5, 5.5);
		Mob highNear = foeAt(helper, 9.5, 5.5);
		var friendly = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(8.5, 2.0, 6.5));
		impact(oneBolt(helper, low), lowTarget);
		impact(oneBolt(helper, high), highTarget);
		MobEffectInstance lift = highNear.getEffect(MobEffects.LEVITATION);
		UniqueTestKit.result("galeshotPlus15", "below=" + lowNear.hasEffect(MobEffects.LEVITATION) + " at=" + lift
			+ " friendly=" + friendly.hasEffect(MobEffects.LEVITATION));
		helper.assertTrue(!lowNear.hasEffect(MobEffects.LEVITATION) && lift != null
			&& lift.getDuration() == GaleshotCrossbow.TUNING.liftTicks() && !friendly.hasEffect(MobEffects.LEVITATION), "area lift gate or foe filter differs");
		helper.succeed();
	}

	@GameTest public void galeshotPlus10RealShotGivesSpeedOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, GaleshotCrossbow.ID, Uniques.PLUS_10 - 1, 2.5);
		UniqueContext high = connectedContext(helper, GaleshotCrossbow.ID, Uniques.PLUS_10, 6.5);
		oneBolt(helper, low);
		oneBolt(helper, high);
		MobEffectInstance speed = high.holder().getEffect(MobEffects.SPEED);
		UniqueTestKit.result("galeshotPlus10", "below=" + low.holder().hasEffect(MobEffects.SPEED) + " at=" + speed);
		helper.assertTrue(!low.holder().hasEffect(MobEffects.SPEED) && speed != null
			&& speed.getDuration() == GaleshotCrossbow.TUNING.speedTicks(), "shot Speed gate differs");
		helper.succeed();
	}

	@GameTest public void tidewatcherBaseBoostsOnlyWetTarget(final GameTestHelper helper) {
		UniqueContext c = context(helper, TidewatcherCrossbow.ID, 0);
		Mob wet = foe(helper, 3.5);
		Mob dry = foe(helper, 7.5);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.WATER);
		wet.baseTick();
		helper.assertTrue(wet.isInWaterOrRain() && !dry.isInWaterOrRain(), "wet and dry fixtures differ");
		UniqueDef def = Uniques.byId(TidewatcherCrossbow.ID);
		float wetDamage = def.effect().outgoingArrow(c, wet, null, 10.0F);
		float dryDamage = def.effect().outgoingArrow(c, dry, null, 10.0F);
		UniqueTestKit.result("tidewatcherBase", "wet=" + wetDamage + " dry=" + dryDamage);
		helper.assertTrue(dryDamage == 10.0F && Math.abs(wetDamage - 10 * (1 + TidewatcherCrossbow.TUNING.bonus())) < 0.001, "wet target bonus differs");
		helper.succeed();
	}

	@GameTest public void tidewatcherPlus15RealBoltCursesOnlyAtGate(final GameTestHelper helper) throws ReflectiveOperationException {
		UniqueContext low = connectedContext(helper, TidewatcherCrossbow.ID, Uniques.PLUS_15 - 1, 2.5);
		UniqueContext high = connectedContext(helper, TidewatcherCrossbow.ID, Uniques.PLUS_15, 6.5);
		Mob lowTarget = foeAt(helper, 2.5, 5.5);
		Mob highTarget = foeAt(helper, 6.5, 5.5);
		lowTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		highTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		lowTarget.setHealth(lowTarget.getMaxHealth());
		highTarget.setHealth(highTarget.getMaxHealth());
		float lowBefore = lowTarget.getHealth();
		float highBefore = highTarget.getHealth();
		AbstractArrow lowBolt = oneBolt(helper, low);
		AbstractArrow highBolt = oneBolt(helper, high);
		impact(lowBolt, lowTarget);
		impact(highBolt, highTarget);
		MobEffectInstance fatigue = highTarget.getEffect(MobEffects.MINING_FATIGUE);
		MobEffectInstance slow = highTarget.getEffect(MobEffects.SLOWNESS);
		UniqueTestKit.result("tidewatcherPlus15", "below=" + lowTarget.hasEffect(MobEffects.MINING_FATIGUE) + " fatigue=" + fatigue + " slow=" + slow
			+ " belowHealth=" + lowBefore + "->" + lowTarget.getHealth() + " atHealth=" + highBefore + "->" + highTarget.getHealth()
			+ " belowDamage=" + (lowBefore - lowTarget.getHealth()) + " atDamage=" + (highBefore - highTarget.getHealth())
			+ " belowCrit=" + lowBolt.isCritArrow() + " atCrit=" + highBolt.isCritArrow()
			+ " belowBase=" + com.stella.equips.refine.RefineEffects.baseDamage(lowBolt)
			+ " atBase=" + com.stella.equips.refine.RefineEffects.baseDamage(highBolt)
			+ " belowMark=" + UniqueArrows.of(lowBolt) + " atMark=" + UniqueArrows.of(highBolt));
		helper.assertTrue(lowTarget.isAlive() && highTarget.isAlive() && lowTarget.getHealth() < lowBefore
			&& highTarget.getHealth() < highBefore, "real refined bolts must damage surviving subjects");
		UniqueArrows.Mark lowMark = UniqueArrows.of(lowBolt);
		UniqueArrows.Mark highMark = UniqueArrows.of(highBolt);
		helper.assertTrue(lowMark != null && highMark != null && TidewatcherCrossbow.ID.equals(lowMark.id())
			&& TidewatcherCrossbow.ID.equals(highMark.id()) && lowMark.refine() == Uniques.PLUS_15 - 1
			&& highMark.refine() == Uniques.PLUS_15, "real bolts must retain their unique and refine marks");
		helper.assertTrue(!lowTarget.hasEffect(MobEffects.MINING_FATIGUE) && !lowTarget.hasEffect(MobEffects.SLOWNESS)
			&& fatigue != null && slow != null && fatigue.getAmplifier() == TidewatcherCrossbow.TUNING.fatigueAmplifier()
			&& slow.getAmplifier() == TidewatcherCrossbow.TUNING.slowAmplifier()
			&& fatigue.getDuration() == TidewatcherCrossbow.TUNING.curseTicks() && slow.getDuration() == TidewatcherCrossbow.TUNING.curseTicks(), "bolt curse gate differs");
		helper.succeed();
	}

	@GameTest public void tidewatcherPlus10HeldBlessingsOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, TidewatcherCrossbow.ID, Uniques.PLUS_10 - 1, 2.5);
		UniqueContext high = connectedContext(helper, TidewatcherCrossbow.ID, Uniques.PLUS_10, 6.5);
		UniqueDef def = Uniques.byId(TidewatcherCrossbow.ID);
		def.effect().everySecond(low);
		def.effect().everySecond(high);
		UniqueTestKit.result("tidewatcherPlus10", "below=" + low.holder().hasEffect(MobEffects.WATER_BREATHING)
			+ " water=" + high.holder().hasEffect(MobEffects.WATER_BREATHING) + " dolphin=" + high.holder().hasEffect(MobEffects.DOLPHINS_GRACE));
		helper.assertTrue(!low.holder().hasEffect(MobEffects.WATER_BREATHING) && !low.holder().hasEffect(MobEffects.DOLPHINS_GRACE)
			&& high.holder().hasEffect(MobEffects.WATER_BREATHING) && high.holder().hasEffect(MobEffects.DOLPHINS_GRACE), "held blessing gate differs");
		helper.succeed();
	}

	@GameTest public void foremanBaseRealReloadUsesEveryTierReduction(final GameTestHelper helper) {
		UniqueContext c = context(helper, ForemanArbalest.ID, 0);
		int reference = CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), c.holder());
		int base = CrossbowItem.getChargeDuration(c.stack(), c.holder());
		int plus7 = CrossbowItem.getChargeDuration(UniqueTestKit.stack(Uniques.byId(ForemanArbalest.ID), Uniques.PLUS_7), c.holder());
		int plus12 = CrossbowItem.getChargeDuration(UniqueTestKit.stack(Uniques.byId(ForemanArbalest.ID), Uniques.PLUS_12), c.holder());
		UniqueTestKit.result("foremanBase", "plain=" + reference + " base=" + base + " plus7=" + plus7 + " plus12=" + plus12);
		helper.assertTrue(base == Math.max(1, (int) Math.floor(reference * (1 - ForemanArbalest.TUNING.reload())))
			&& plus7 == Math.max(1, (int) Math.floor(reference * (1 - ForemanArbalest.TUNING.reloadPlus7())))
			&& plus12 == Math.max(1, (int) Math.floor(reference * (1 - ForemanArbalest.TUNING.reloadPlus12()))), "reload reductions differ");
		helper.succeed();
	}

	@GameTest public void foremanPlus15RealShotLoosesThreeBoltsOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, ForemanArbalest.ID, Uniques.PLUS_15 - 1, 2.5);
		UniqueContext high = connectedContext(helper, ForemanArbalest.ID, Uniques.PLUS_15, 6.5);
		List<AbstractArrow> below = fireCrossbow(helper, low);
		List<AbstractArrow> at = fireCrossbow(helper, high);
		long pickup = at.stream().filter(a -> a.pickup == AbstractArrow.Pickup.ALLOWED).count();
		UniqueTestKit.result("foremanPlus15", "below=" + below.size() + " at=" + at.size() + " pickup=" + pickup);
		helper.assertTrue(below.size() == 1 && at.size() == ForemanArbalest.TUNING.bolts() && pickup <= 1
			&& at.stream().allMatch(a -> ForemanArbalest.ID.equals(UniqueArrows.of(a).id())), "three-bolt gate, marking, or pickup differs");
		helper.succeed();
	}

	@GameTest public void rotgutBaseHealsFromActualMeleeDamage(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, RotgutCleaver.ID, 0, 1.5);
		Mob target = durableFoe(helper, 2.5, 3.5);
		c.holder().setHealth(c.holder().getMaxHealth() / 2);
		float before = c.holder().getHealth();
		float dealt = melee(helper, c, target, 10.0F);
		float healed = c.holder().getHealth() - before;
		UniqueTestKit.result("rotgutBase", "dealt=" + dealt + " healed=" + healed);
		helper.assertTrue(Math.abs(healed - dealt * RotgutCleaver.TUNING.healShare()) < 0.001, "melee healing share differs");
		helper.succeed();
	}

	@GameTest public void rotgutPlus15DoublesHealingBelowThresholdOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, RotgutCleaver.ID, Uniques.PLUS_15 - 1, 1.5);
		UniqueContext high = connectedContext(helper, RotgutCleaver.ID, Uniques.PLUS_15, 5.5);
		float threshold = (float) (high.holder().getMaxHealth() * RotgutCleaver.TUNING.lowThreshold());
		low.holder().setHealth(Math.nextDown(threshold));
		high.holder().setHealth(Math.nextDown(threshold));
		float lowBefore = low.holder().getHealth();
		float highBefore = high.holder().getHealth();
		float lowDamage = melee(helper, low, durableFoe(helper, 2.5, 3.5), 10.0F);
		Mob highTarget = durableFoe(helper, 6.5, 3.5);
		float highDamage = melee(helper, high, highTarget, 10.0F);
		float lowHeal = low.holder().getHealth() - lowBefore;
		float highHeal = high.holder().getHealth() - highBefore;
		high.holder().setHealth(threshold);
		float thresholdDamage = melee(helper, high, highTarget, 10.0F);
		float thresholdHeal = high.holder().getHealth() - threshold;
		UniqueTestKit.result("rotgutPlus15", "below=" + lowHeal + " at=" + highHeal + " exactThreshold=" + thresholdHeal);
		helper.assertTrue(Math.abs(lowHeal - lowDamage * RotgutCleaver.TUNING.healPlus12()) < 0.001
			&& Math.abs(highHeal - highDamage * RotgutCleaver.TUNING.healPlus12() * RotgutCleaver.TUNING.lowMultiplier()) < 0.001
			&& Math.abs(thresholdHeal - thresholdDamage * RotgutCleaver.TUNING.healPlus12()) < 0.001, "low-health healing gate differs");
		helper.succeed();
	}

	@GameTest public void rotgutPlus10ConvertsOverflowAndCapsAbsorption(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, RotgutCleaver.ID, Uniques.PLUS_10 - 1, 1.5);
		UniqueContext high = connectedContext(helper, RotgutCleaver.ID, Uniques.PLUS_10, 5.5);
		// Overflow needs a full holder, so the fixture fills both rather than trusting the join.
		low.holder().setHealth(low.holder().getMaxHealth());
		high.holder().setHealth(high.holder().getMaxHealth());
		Mob lowTarget = durableFoe(helper, 2.5, 3.5);
		Mob highTarget = durableFoe(helper, 6.5, 3.5);
		float full = high.holder().getHealth();
		float damage = melee(helper, high, highTarget, 100.0F);
		float first = high.holder().getAbsorptionAmount();
		melee(helper, high, highTarget, 100.0F);
		melee(helper, low, lowTarget, 100.0F);
		float cap = (float) (high.holder().getMaxHealth() * RotgutCleaver.TUNING.absorptionCap());
		UniqueTestKit.result("rotgutPlus10", "below=" + low.holder().getAbsorptionAmount() + " first=" + first + " capped=" + high.holder().getAbsorptionAmount()
			+ " full=" + full + " max=" + high.holder().getMaxHealth()
			+ " limit=" + high.holder().getAttributeValue(Attributes.MAX_ABSORPTION));
		helper.assertTrue(low.holder().getAbsorptionAmount() == 0
			&& Math.abs(first - Math.min(cap, damage * RotgutCleaver.TUNING.healPlus7())) < 0.001
			&& Math.abs(high.holder().getAbsorptionAmount() - cap) < 0.001, "overflow absorption gate or cap differs");
		helper.succeed();
	}

	@GameTest public void johnnysBaseAppliesArmorBonusBeforeNativeMitigation(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, JohnnysAxe.ID, 0, 1.5);
		double capArmor = JohnnysAxe.TUNING.cap() / JohnnysAxe.TUNING.armorShare();
		Mob atCap = durableFoe(helper, 2.5, 3.5);
		Mob aboveCap = durableFoe(helper, 4.5, 3.5);
		atCap.getAttribute(Attributes.ARMOR).setBaseValue(capArmor);
		aboveCap.getAttribute(Attributes.ARMOR).setBaseValue(capArmor * 2);
		float outgoing = 10.0F * (1 + (float) JohnnysAxe.TUNING.cap());
		float expectedAt = armoredDamage(helper, c, atCap, outgoing);
		float expectedAbove = armoredDamage(helper, c, aboveCap, outgoing);
		float actualAt = melee(helper, c, atCap, 10.0F);
		float actualAbove = melee(helper, c, aboveCap, 10.0F);
		UniqueTestKit.result("johnnysBase", "at=" + actualAt + " expectedAt=" + expectedAt + " above=" + actualAbove + " expectedAbove=" + expectedAbove);
		helper.assertTrue(Math.abs(actualAt - expectedAt) < 0.001 && Math.abs(actualAbove - expectedAbove) < 0.001,
			"armor-point bonus or its pre-mitigation cap differs");
		helper.succeed();
	}

	@GameTest public void johnnysPlus15ExecutesBelowThresholdButNeverBosses(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, JohnnysAxe.ID, Uniques.PLUS_15 - 1, 1.5);
		UniqueContext high = connectedContext(helper, JohnnysAxe.ID, Uniques.PLUS_15, 5.5);
		Mob lowTarget = durableFoe(helper, 2.5, 3.5);
		Mob highTarget = durableFoe(helper, 4.5, 3.5);
		Mob exact = durableFoe(helper, 6.5, 3.5);
		var boss = helper.spawnWithNoFreeWill(EntityTypes.ELDER_GUARDIAN, new Vec3(3.5, 2.0, 6.5));
		lowTarget.setHealth(Math.nextDown((float) (lowTarget.getMaxHealth() * JohnnysAxe.TUNING.executeThreshold())));
		highTarget.setHealth(Math.nextDown((float) (highTarget.getMaxHealth() * JohnnysAxe.TUNING.executeThreshold())));
		exact.setHealth((float) (exact.getMaxHealth() * JohnnysAxe.TUNING.executeThreshold()));
		boss.setHealth(Math.nextDown((float) (boss.getMaxHealth() * JohnnysAxe.TUNING.executeThreshold())));
		melee(helper, low, lowTarget, 1.0F);
		melee(helper, high, highTarget, 1.0F);
		melee(helper, high, exact, 1.0F);
		melee(helper, high, boss, 1.0F);
		UniqueTestKit.result("johnnysPlus15", "belowAlive=" + lowTarget.isAlive() + " atAlive=" + highTarget.isAlive()
			+ " thresholdAlive=" + exact.isAlive() + " bossAlive=" + boss.isAlive() + " rank=" + UniqueRank.of(boss));
		helper.assertTrue(lowTarget.isAlive() && !highTarget.isAlive() && exact.isAlive() && boss.isAlive()
			&& UniqueRank.of(boss) == UniqueRank.BOSS, "execution refine, health, or boss gate differs");
		helper.succeed();
	}

	@GameTest public void venomBaseRealHitsStackPoisonToEachTierCap(final GameTestHelper helper) {
		int[] tiers = {0, Uniques.PLUS_7, Uniques.PLUS_12};
		boolean valid = true;
		StringBuilder outcome = new StringBuilder();
		for (int i = 0; i < tiers.length; i++) {
			int refine = tiers[i];
			UniqueContext c = connectedContext(helper, VenomDirk.ID, refine, 1.5 + i * 2);
			Mob target = durableFoe(helper, 2.5 + i * 2, 3.5);
			int cap = refine >= Uniques.PLUS_12 ? VenomDirk.TUNING.maxTicksPlus12() : VenomDirk.TUNING.maxTicks();
			int hits = (cap + VenomDirk.TUNING.stackTicks() - 1) / VenomDirk.TUNING.stackTicks() + 1;
			float lastDamage = 0;
			for (int hit = 0; hit < hits; hit++) {
				lastDamage = melee(helper, c, target, 1.0F);
			}
			MobEffectInstance poison = target.getEffect(MobEffects.POISON);
			int amplifier = refine >= Uniques.PLUS_7 ? VenomDirk.TUNING.amplifierPlus7() : VenomDirk.TUNING.amplifier();
			float expected = refine >= Uniques.PLUS_10 ? 1 + (float) VenomDirk.TUNING.poisonedBonus() : 1;
			outcome.append(" tier=").append(refine).append(" poison=").append(poison).append(" lastDamage=").append(lastDamage);
			valid &= poison != null && poison.getDuration() == cap && poison.getAmplifier() == amplifier
				&& Math.abs(lastDamage - expected) < 0.001;
		}
		UniqueTestKit.result("venomBase", outcome.toString());
		helper.assertTrue(valid, "poison stack, amplifier, or poisoned-hit bonus differs");
		helper.succeed();
	}

	private static void meleeKillSpreadAt15(final GameTestHelper helper, final String id, final Holder<MobEffect> effect,
		final int sourceTicks, final int sourceAmplifier, final int expectedTicks, final int expectedAmplifier, final double radius) {
		UniqueContext low = connectedContext(helper, id, Uniques.PLUS_15 - 1, 1.5);
		UniqueContext high = connectedContext(helper, id, Uniques.PLUS_15, 5.5);
		Mob lowVictim = durableFoe(helper, 3.5, 3.5);
		Mob lowNear = durableFoe(helper, 3.5 + Math.min(1, radius / 2), 3.5);
		lowVictim.addEffect(new MobEffectInstance(effect, sourceTicks, sourceAmplifier));
		lowVictim.setHealth(1.0F);
		melee(helper, low, lowVictim, 10.0F);
		boolean below = lowNear.hasEffect(effect);
		lowNear.discard();
		lowVictim.discard();
		Mob highVictim = durableFoe(helper, 3.5, 3.5);
		Mob highNear = durableFoe(helper, 3.5 + Math.min(1, radius / 2), 3.5);
		var friendly = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new Vec3(3.5, 2.0, 4.5));
		highVictim.addEffect(new MobEffectInstance(effect, sourceTicks, sourceAmplifier));
		highVictim.setHealth(1.0F);
		melee(helper, high, highVictim, 10.0F);
		MobEffectInstance actual = highNear.getEffect(effect);
		UniqueTestKit.result(id + "Plus15", "below=" + below + " at=" + actual + " friendly=" + friendly.hasEffect(effect));
		helper.assertTrue(!below && !highVictim.isAlive() && actual != null && actual.getDuration() == expectedTicks
			&& actual.getAmplifier() == expectedAmplifier && !friendly.hasEffect(effect), "real kill spread gate or foe filter differs");
		helper.succeed();
	}

	@GameTest public void venomPlus15RealKillTransfersItsPoisonOnlyToFoes(final GameTestHelper helper) {
		meleeKillSpreadAt15(helper, VenomDirk.ID, MobEffects.POISON, VenomDirk.TUNING.maxTicksPlus12(),
			VenomDirk.TUNING.amplifierPlus7(), VenomDirk.TUNING.maxTicksPlus12(), VenomDirk.TUNING.amplifierPlus7(), VenomDirk.TUNING.spreadRadius());
	}

	@GameTest(maxTicks = 200) public void burrowerBaseRealHitStrikesOnceAfterDelay(final GameTestHelper helper) {
		UniqueContext c = connectedContext(helper, BurrowerShiv.ID, 0, 1.5);
		CHANCE.put(c.holder().getUUID(), true);
		Mob target = durableFoe(helper, 2.5, 3.5);
		float before = target.getHealth();
		float primary = melee(helper, c, target, 10.0F);
		helper.runAfterDelay(BurrowerShiv.TUNING.delayTicks() - 1, () ->
			helper.assertTrue(Math.abs(before - target.getHealth() - primary) < 0.001, "the delayed strike landed early"));
		helper.runAfterDelay(BurrowerShiv.TUNING.delayTicks() * 2 + 2, () -> {
			float actual = before - target.getHealth();
			float expected = primary * (1 + BurrowerShiv.TUNING.strikeShare());
			UniqueTestKit.result("burrowerBase", "primary=" + primary + " total=" + actual + " expected=" + expected);
			helper.assertTrue(target.isAlive() && Math.abs(actual - expected) < 0.001, "the delayed strike was missing or repeated");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200) public void burrowerPlus15RepeatsTheSecondStrikeOnlyOnce(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, BurrowerShiv.ID, Uniques.PLUS_15 - 1, 1.5);
		UniqueContext high = connectedContext(helper, BurrowerShiv.ID, Uniques.PLUS_15, 5.5);
		CHANCE.put(low.holder().getUUID(), true);
		CHANCE.put(high.holder().getUUID(), true);
		Mob lowTarget = durableFoe(helper, 2.5, 3.5);
		Mob highTarget = durableFoe(helper, 6.5, 3.5);
		float lowBefore = lowTarget.getHealth();
		float highBefore = highTarget.getHealth();
		float lowPrimary = melee(helper, low, lowTarget, 10.0F);
		float highPrimary = melee(helper, high, highTarget, 10.0F);
		helper.runAfterDelay(BurrowerShiv.TUNING.delayTicks() * (BurrowerShiv.TUNING.maxExtraStrikes() + 1) + 2, () -> {
			float share = BurrowerShiv.TUNING.strikeShare();
			float below = lowBefore - lowTarget.getHealth();
			float at = highBefore - highTarget.getHealth();
			UniqueTestKit.result("burrowerPlus15", "below=" + below + " at=" + at);
			helper.assertTrue(Math.abs(below - lowPrimary * (1 + share)) < 0.001
				&& Math.abs(at - highPrimary * (1 + share + share * share)) < 0.001, "second-strike repeat gate or recursion bound differs");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200) public void burrowerPlus10DelayedStrikeIgnoresArmorOnlyAtGate(final GameTestHelper helper) {
		UniqueContext low = connectedContext(helper, BurrowerShiv.ID, Uniques.PLUS_10 - 1, 1.5);
		UniqueContext high = connectedContext(helper, BurrowerShiv.ID, Uniques.PLUS_10, 5.5);
		CHANCE.put(low.holder().getUUID(), true);
		CHANCE.put(high.holder().getUUID(), true);
		Mob lowTarget = durableFoe(helper, 2.5, 3.5);
		Mob highTarget = durableFoe(helper, 6.5, 3.5);
		lowTarget.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		highTarget.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		float lowPrimary = melee(helper, low, lowTarget, 10.0F);
		float highPrimary = melee(helper, high, highTarget, 10.0F);
		float lowBefore = lowTarget.getHealth();
		float highBefore = highTarget.getHealth();
		float expectedBelow = armoredDamage(helper, low, lowTarget, lowPrimary * BurrowerShiv.TUNING.strikeShare());
		helper.runAfterDelay(BurrowerShiv.TUNING.delayTicks() + 2, () -> {
			float below = lowBefore - lowTarget.getHealth();
			float at = highBefore - highTarget.getHealth();
			UniqueTestKit.result("burrowerPlus10", "below=" + below + " at=" + at + " expectedBelow=" + expectedBelow);
			helper.assertTrue(below > 0 && Math.abs(below - expectedBelow) < 0.001
				&& Math.abs(at - highPrimary * BurrowerShiv.TUNING.strikeShare()) < 0.001, "delayed armor bypass gate differs");
			helper.succeed();
		});
	}

	@GameTest public void witheringBaseRealHitsApplyEachTierAndBonus(final GameTestHelper helper) {
		int[] tiers = {0, Uniques.PLUS_7, Uniques.PLUS_12};
		boolean valid = true;
		StringBuilder outcome = new StringBuilder();
		for (int i = 0; i < tiers.length; i++) {
			int refine = tiers[i];
			UniqueContext c = connectedContext(helper, WitheringGreatsword.ID, refine, 1.5 + i * 2);
			Mob target = durableFoe(helper, 2.5 + i * 2, 3.5);
			melee(helper, c, target, 1.0F);
			MobEffectInstance wither = target.getEffect(MobEffects.WITHER);
			int ticks = refine >= Uniques.PLUS_12 ? WitheringGreatsword.TUNING.ticksPlus12() : WitheringGreatsword.TUNING.ticks();
			int amplifier = refine >= Uniques.PLUS_7 ? WitheringGreatsword.TUNING.amplifierPlus7() : WitheringGreatsword.TUNING.amplifier();
			float expected = refine >= Uniques.PLUS_10 ? 1 + (float) WitheringGreatsword.TUNING.bonus() : 1;
			float nextDamage = melee(helper, c, target, 1.0F);
			outcome.append(" tier=").append(refine).append(" wither=").append(wither).append(" nextDamage=").append(nextDamage);
			valid &= wither != null && wither.getDuration() == ticks && wither.getAmplifier() == amplifier
				&& Math.abs(nextDamage - expected) < 0.001;
		}
		UniqueTestKit.result("witheringBase", outcome.toString());
		helper.assertTrue(valid, "Wither tier or withered-target bonus differs");
		helper.succeed();
	}

	@GameTest public void witheringPlus15RealKillSpreadsFixedWitherOnlyToFoes(final GameTestHelper helper) {
		meleeKillSpreadAt15(helper, WitheringGreatsword.ID, MobEffects.WITHER, WitheringGreatsword.TUNING.ticksPlus12(),
			WitheringGreatsword.TUNING.amplifier(), WitheringGreatsword.TUNING.spreadTicks(), WitheringGreatsword.TUNING.spreadAmplifier(),
			WitheringGreatsword.TUNING.spreadRadius());
	}
}
