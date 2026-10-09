package com.stella.equips;

import com.stella.equips.imbue.NetherImbue;
import com.stella.equips.unique.UniqueDamage;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/** The imbue effects: a weapon withers and hits a withered target hard, and armor is immune to Wither. */
public final class NetherImbueEffectGameTest {
	private static final float HIT = 5.0F;
	private static final float EPSILON = 0.01F;

	private static ItemStack imbued(final ItemStack base, final int count) {
		ItemStack stack = base.copy();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(NetherImbue.KEY, count));
		return stack;
	}

	private static ServerPlayer holder(final GameTestHelper helper, final ItemStack mainHand) {
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.0, 1.0, 1.5));
		player.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
		return player;
	}

	private static <T extends Mob> T foe(final GameTestHelper helper, final EntityType<T> type, final double x) {
		T mob = helper.spawnWithNoFreeWill(type, new Vec3(x, 1.0, 5.5));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setItemSlot(slot, ItemStack.EMPTY);
		}
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		mob.setHealth(500.0F);
		return mob;
	}

	private static float hit(final ServerLevel level, final LivingEntity victim, final DamageSource source, final float amount) {
		victim.damageCooldownTime = 0;
		float before = victim.getHealth();
		victim.hurtServer(level, source, amount);
		return before - victim.getHealth();
	}

	private static MobEffectInstance wither(final LivingEntity entity) {
		return entity.getEffect(MobEffects.WITHER);
	}

	private static void applyWither(final LivingEntity entity) {
		entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 0));
	}

	@GameTest
	public void anImbuedHitWithersAtLevelTwoForOneHundredTicks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 1));
		var victim = foe(helper, EntityTypes.ZOMBIE, 2.5);
		hit(level, victim, level.damageSources().playerAttack(player), HIT);
		MobEffectInstance effect = wither(victim);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=anImbuedHitWithersAtLevelTwoForOneHundredTicks RESULT amplifier={} duration={}",
			effect == null ? null : effect.getAmplifier(), effect == null ? null : effect.getDuration());
		helper.assertTrue(effect != null, "the victim holds no Wither");
		helper.assertTrue(effect.getAmplifier() == NetherImbue.WITHER_AMPLIFIER && NetherImbue.WITHER_AMPLIFIER == 1, "amplifier " + effect.getAmplifier());
		helper.assertTrue(effect.getDuration() == NetherImbue.WITHER_TICKS && NetherImbue.WITHER_TICKS == 100, "duration " + effect.getDuration());
		helper.succeed();
	}

	@GameTest
	public void anUnimbuedHitDoesNotWither(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer imbuedHolder = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 1));
		ServerPlayer plainHolder = holder(helper, new ItemStack(Items.IRON_SWORD));
		var control = foe(helper, EntityTypes.ZOMBIE, 2.5);
		var victim = foe(helper, EntityTypes.ZOMBIE, 3.5);
		hit(level, control, level.damageSources().playerAttack(imbuedHolder), HIT);
		hit(level, victim, level.damageSources().playerAttack(plainHolder), HIT);
		helper.assertTrue(wither(control) != null, "the control sword did not wither");
		helper.assertTrue(wither(victim) == null, "a plain sword withered the victim");
		helper.succeed();
	}

	@GameTest
	public void aHitOnAWitheredVictimDealsThreeTimesTheDamage(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 1));
		var victim = foe(helper, EntityTypes.ZOMBIE, 2.5);
		float first = hit(level, victim, level.damageSources().playerAttack(player), HIT);
		float second = hit(level, victim, level.damageSources().playerAttack(player), HIT);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=aHitOnAWitheredVictimDealsThreeTimesTheDamage RESULT first={} second={} multiplier={}",
			first, second, NetherImbue.BONUS_MULTIPLIER);
		helper.assertTrue(NetherImbue.BONUS_MULTIPLIER == 3.0F, "the multiplier is " + NetherImbue.BONUS_MULTIPLIER);
		helper.assertTrue(Math.abs(first - HIT) < EPSILON, "the first hit dealt " + first);
		helper.assertTrue(Math.abs(second - HIT * NetherImbue.BONUS_MULTIPLIER) < EPSILON, "the second hit dealt " + second);
		helper.succeed();
	}

	@GameTest
	public void theBonusDoesNotGrowWithTheImbueCount(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 4));
		var victim = foe(helper, EntityTypes.ZOMBIE, 2.5);
		hit(level, victim, level.damageSources().playerAttack(player), HIT);
		float second = hit(level, victim, level.damageSources().playerAttack(player), HIT);
		helper.assertTrue(Math.abs(second - HIT * NetherImbue.BONUS_MULTIPLIER) < EPSILON, "four imbues dealt " + second);
		helper.succeed();
	}

	@GameTest
	public void anUnimbuedWeaponGetsNoBonusOnAWitheredVictim(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer imbuedHolder = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 1));
		ServerPlayer plainHolder = holder(helper, new ItemStack(Items.IRON_SWORD));
		var control = foe(helper, EntityTypes.ZOMBIE, 2.5);
		var victim = foe(helper, EntityTypes.ZOMBIE, 3.5);
		applyWither(control);
		applyWither(victim);
		float imbuedHit = hit(level, control, level.damageSources().playerAttack(imbuedHolder), HIT);
		float plainHit = hit(level, victim, level.damageSources().playerAttack(plainHolder), HIT);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=anUnimbuedWeaponGetsNoBonusOnAWitheredVictim RESULT imbued={} plain={}", imbuedHit, plainHit);
		helper.assertTrue(Math.abs(imbuedHit - HIT * NetherImbue.BONUS_MULTIPLIER) < EPSILON, "the control imbued hit dealt " + imbuedHit);
		helper.assertTrue(Math.abs(plainHit - HIT) < EPSILON, "a plain hit dealt " + plainHit);
		helper.succeed();
	}

	@GameTest
	public void aWitherSkeletonIsNotWitheredAndGetsNoBonus(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 1));
		var control = foe(helper, EntityTypes.ZOMBIE, 2.5);
		var victim = foe(helper, EntityTypes.WITHER_SKELETON, 3.5);
		hit(level, control, level.damageSources().playerAttack(player), HIT);
		float first = hit(level, victim, level.damageSources().playerAttack(player), HIT);
		float second = hit(level, victim, level.damageSources().playerAttack(player), HIT);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=aWitherSkeletonIsNotWitheredAndGetsNoBonus RESULT first={} second={} withered={}", first, second, wither(victim) != null);
		helper.assertTrue(wither(control) != null, "the control zombie was not withered");
		helper.assertTrue(wither(victim) == null, "the wither skeleton was withered");
		helper.assertTrue(Math.abs(second - first) < EPSILON, "the second hit dealt " + second + " after " + first);
		helper.succeed();
	}

	@GameTest
	public void uniqueThornsAndSpellDamageDoNotTrigger(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 1));
		var control = foe(helper, EntityTypes.ZOMBIE, 2.5);
		var unique = foe(helper, EntityTypes.ZOMBIE, 3.5);
		var thorns = foe(helper, EntityTypes.ZOMBIE, 4.5);
		var spell = foe(helper, EntityTypes.ZOMBIE, 5.5);
		hit(level, control, level.damageSources().playerAttack(player), HIT);
		UniqueDamage.deal(level, unique, player, HIT, false);
		hit(level, thorns, level.damageSources().thorns(player), HIT);
		hit(level, spell, level.damageSources().indirectMagic(player, player), HIT);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=uniqueThornsAndSpellDamageDoNotTrigger RESULT control={} unique={} thorns={} spell={}",
			wither(control) != null, wither(unique) != null, wither(thorns) != null, wither(spell) != null);
		helper.assertTrue(wither(control) != null, "the control melee hit did not wither");
		helper.assertTrue(unique.getHealth() < 500.0F, "the unique damage did not land");
		helper.assertTrue(wither(unique) == null && wither(thorns) == null && wither(spell) == null, "an excluded source withered a victim");
		helper.succeed();
	}

	@GameTest
	public void uniqueThornsAndSpellDamageGetNoBonusOnAWitheredVictim(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, imbued(new ItemStack(Items.IRON_SWORD), 1));
		var control = foe(helper, EntityTypes.ZOMBIE, 2.5);
		var thorns = foe(helper, EntityTypes.ZOMBIE, 3.5);
		var spell = foe(helper, EntityTypes.ZOMBIE, 4.5);
		applyWither(control);
		applyWither(thorns);
		applyWither(spell);
		float melee = hit(level, control, level.damageSources().playerAttack(player), HIT);
		float thornsHit = hit(level, thorns, level.damageSources().thorns(player), HIT);
		float spellHit = hit(level, spell, level.damageSources().indirectMagic(player, player), HIT);
		helper.assertTrue(Math.abs(melee - HIT * NetherImbue.BONUS_MULTIPLIER) < EPSILON, "the control melee hit dealt " + melee);
		helper.assertTrue(Math.abs(thornsHit - HIT) < EPSILON && Math.abs(spellHit - HIT) < EPSILON, "thorns dealt " + thornsHit + " and the spell " + spellHit);
		helper.succeed();
	}

	@GameTest
	public void anArrowFromAnImbuedBowWithersAndTripleHitsAWitheredVictim(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = holder(helper, new ItemStack(Items.AIR));
		ItemStack imbuedBow = imbued(new ItemStack(Items.BOW), 1);
		Arrow imbuedArrow = new Arrow(level, player, new ItemStack(Items.ARROW), imbuedBow);
		Arrow plainArrow = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		var victim = foe(helper, EntityTypes.ZOMBIE, 2.5);
		var plainVictim = foe(helper, EntityTypes.ZOMBIE, 3.5);
		float first = hit(level, victim, level.damageSources().arrow(imbuedArrow, player), HIT);
		float second = hit(level, victim, level.damageSources().arrow(imbuedArrow, player), HIT);
		hit(level, plainVictim, level.damageSources().arrow(plainArrow, player), HIT);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=anArrowFromAnImbuedBowWithersAndTripleHitsAWitheredVictim RESULT first={} second={} plainWithered={}",
			first, second, wither(plainVictim) != null);
		helper.assertTrue(wither(victim) != null, "the imbued arrow did not wither");
		helper.assertTrue(Math.abs(second - HIT * NetherImbue.BONUS_MULTIPLIER) < EPSILON, "the second arrow dealt " + second);
		helper.assertTrue(wither(plainVictim) == null, "a plain arrow withered the victim");
		helper.succeed();
	}

	@GameTest
	public void oneImbuedPieceInAnyArmorSlotBlocksWither(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<EquipmentSlot> slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		List<ItemStack> pieces = List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
			new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS));
		for (int i = 0; i < slots.size(); i++) {
			var wearer = foe(helper, EntityTypes.ZOMBIE, 2.5 + i);
			wearer.setItemSlot(slots.get(i), imbued(pieces.get(i), 1));
			boolean landed = wearer.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
			if (landed || wither(wearer) != null) {
				wrong.add(slots.get(i) + " let Wither land");
			}
		}
		var bare = foe(helper, EntityTypes.ZOMBIE, 7.5);
		boolean bareLanded = bare.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=oneImbuedPieceInAnyArmorSlotBlocksWither RESULT wrong={} controlLanded={}", wrong, bareLanded);
		helper.assertTrue(bareLanded, "the bare control was immune to Wither");
		helper.assertTrue(wrong.isEmpty(), "wrong: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void anImbuedWeaponInHandGivesNoImmunityAndOtherEffectsStillLand(final GameTestHelper helper) {
		var armed = foe(helper, EntityTypes.ZOMBIE, 2.5);
		armed.setItemSlot(EquipmentSlot.MAINHAND, imbued(new ItemStack(Items.IRON_SWORD), 1));
		var armored = foe(helper, EntityTypes.ZOMBIE, 3.5);
		armored.setItemSlot(EquipmentSlot.CHEST, imbued(new ItemStack(Items.IRON_CHESTPLATE), 1));
		boolean armedWithered = armed.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0));
		boolean slowed = armored.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
		boolean armoredWithered = armored.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0));
		helper.assertTrue(armedWithered, "an imbued sword in hand blocked Wither");
		helper.assertTrue(slowed, "imbued armor blocked Slowness");
		helper.assertTrue(!armoredWithered, "imbued armor let Wither land");
		helper.succeed();
	}

	@GameTest
	public void cureRemovesWitherFromAWearerAndOnlyFromAWearer(final GameTestHelper helper) {
		var wearer = foe(helper, EntityTypes.ZOMBIE, 2.5);
		var bare = foe(helper, EntityTypes.ZOMBIE, 3.5);
		applyWither(wearer);
		applyWither(bare);
		wearer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
		wearer.setItemSlot(EquipmentSlot.FEET, imbued(new ItemStack(Items.IRON_BOOTS), 1));
		NetherImbue.cure(wearer);
		NetherImbue.cure(bare);
		helper.assertTrue(wither(wearer) == null, "the wearer kept Wither");
		helper.assertTrue(wearer.hasEffect(MobEffects.SLOWNESS), "the cure took Slowness too");
		helper.assertTrue(wither(bare) != null, "the cure took Wither from a bare mob");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void theServerTickCuresAnOnlinePlayerWhoPutOnImbuedArmor(final GameTestHelper helper) {
		ServerPlayer wearer = UniqueTestKit.connected(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer bare = UniqueTestKit.connected(helper, new Vec3(3.5, 1.0, 1.5));
		applyWither(wearer);
		applyWither(bare);
		helper.assertTrue(wither(wearer) != null && wither(bare) != null, "the players did not take Wither to start");
		wearer.setItemSlot(EquipmentSlot.CHEST, imbued(new ItemStack(Items.IRON_CHESTPLATE), 1));
		helper.runAfterDelay(NetherImbue.CURE_INTERVAL + 5, () -> {
			StellaEquips.LOGGER.info("[BEHAVIOR] test=theServerTickCuresAnOnlinePlayerWhoPutOnImbuedArmor RESULT wearerWithered={} bareWithered={}",
				wither(wearer) != null, wither(bare) != null);
			helper.assertTrue(wither(wearer) == null, "the wearer still held Wither after " + (NetherImbue.CURE_INTERVAL + 5) + " ticks");
			helper.assertTrue(wither(bare) != null, "the bare player lost Wither");
			helper.succeed();
		});
	}
}
