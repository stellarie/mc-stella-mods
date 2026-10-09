package com.stella.ruins.reward;

import com.stella.ruins.StellaRuins;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Registers the test-only reward content: two sets, four affixes, five unique items, and gear built from one
 * spec. Every id starts with {@code test_}. The jar of the mod never carries any of it.
 */
public final class RewardTestInit implements ModInitializer {
	public static final String PROBE = "test_probe";
	public static final String THROWER = "test_thrower";
	public static final String RECURSE = "test_recurse";

	public static final GearSpec GEAR = new GearSpec(
		700, 7.0F, 2.5F, BlockTags.INCORRECT_FOR_IRON_TOOL, 21, 3, 8, 6, 3, 1.5F, 0.05F, 12,
		ItemTags.REPAIRS_IRON_ARMOR, StellaRuins.id("test_gear")
	);

	public static RuinSets.SetDefinition alpha;
	public static RuinSets.SetDefinition beta;
	public static RuinAffixes.Affix keen;
	public static RuinAffixes.Affix sharp;
	public static RuinAffixes.Affix ward;
	public static RuinAffixes.Affix echo;
	public static Item uniqueSword;
	public static Item uniqueHelm;
	public static Item uniqueBow;
	public static Item uniqueCharm;
	public static Item uniqueThrower;
	public static Item uniqueRecurse;
	public static Item uniqueOrphan;
	public static Item gearSword;
	public static Item gearAxe;
	public static Item gearPickaxe;
	public static Item gearChest;

	@Override
	public void onInitialize() {
		alpha = RuinSets.define("test_alpha", SetSpec.builder()
			.durabilityFactor(25).defense(3, 8, 6, 3).toughness(1.0F).knockbackResistance(0.0F).enchantValue(12)
			.repair(ItemTags.REPAIRS_IRON_ARMOR).equipmentAsset(StellaRuins.id("test_alpha"))
			.bonus(new SetBonus(2, List.of(AttributeBonus.add(Attributes.LUCK, 1.0))))
			.bonus(new SetBonus(4, List.of(AttributeBonus.add(Attributes.MAX_HEALTH, 4.0)), new PeriodicEffect(MobEffects.SPEED, 0, 60)))
			.build());
		beta = RuinSets.define("test_beta", SetSpec.builder()
			.repair(ItemTags.REPAIRS_IRON_ARMOR).equipmentAsset(StellaRuins.id("test_beta"))
			.bonus(new SetBonus(2, List.of(AttributeBonus.add(Attributes.LUCK, 2.0))))
			.build());
		RuinSets.defineHybrid(new HybridBonus("test_alpha_beta", java.util.Map.of("test_alpha", 2, "test_beta", 2),
			List.of(AttributeBonus.add(Attributes.SAFE_FALL_DISTANCE, 5.0)), null));

		keen = RuinAffixes.define("test_keen", new AffixSpec(
			stack -> stack.is(ItemTags.SWORDS), Ingredient.of(Items.AMETHYST_SHARD),
			List.of(AttributeBonus.add(Attributes.ATTACK_DAMAGE, 2.0)), null));
		sharp = RuinAffixes.define("test_sharp", new AffixSpec(
			stack -> stack.is(ItemTags.SWORDS), Ingredient.of(Items.QUARTZ),
			List.of(AttributeBonus.add(Attributes.ATTACK_DAMAGE, 4.0), AttributeBonus.multiplyBase(Attributes.ATTACK_SPEED, 0.1)), null));
		ward = RuinAffixes.define("test_ward", new AffixSpec(
			stack -> stack.is(ItemTags.CHEST_ARMOR), Ingredient.of(Items.IRON_NUGGET),
			List.of(AttributeBonus.add(Attributes.ARMOR_TOUGHNESS, 2.0)), null));
		echo = RuinAffixes.define("test_echo", new AffixSpec(
			stack -> stack.is(ItemTags.SWORDS), Ingredient.of(Items.COPPER_INGOT),
			List.of(), PROBE));

		UniqueEffects.define(PROBE, new EffectHook() {
			@Override
			public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
				RewardTestSupport.record(user.getUUID(), "attack:" + target.getUUID() + ":" + dealt);
			}

			@Override
			public void onHurt(final LivingEntity user, final DamageSource source, final float taken) {
				RewardTestSupport.record(user.getUUID(), "hurt:" + taken);
			}

			@Override
			public void onKill(final LivingEntity user, final LivingEntity victim) {
				RewardTestSupport.record(user.getUUID(), "kill:" + victim.getUUID());
			}

			@Override
			public void onTick(final LivingEntity user) {
				RewardTestSupport.record(user.getUUID(), "tick:" + user.tickCount);
			}

			@Override
			public void onArrowFired(final LivingEntity user, final AbstractArrow arrow) {
				RewardTestSupport.record(user.getUUID(), "arrow:" + arrow.getUUID());
			}
		});
		UniqueEffects.define(THROWER, new EffectHook() {
			@Override
			public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
				throw new IllegalStateException("test hook failure");
			}
		});
		UniqueEffects.define(RECURSE, new EffectHook() {
			@Override
			public void onAttack(final LivingEntity user, final LivingEntity target, final DamageSource source, final float dealt) {
				RewardTestSupport.record(user.getUUID(), "attack:" + target.getUUID() + ":" + dealt);
				if (RewardTestSupport.count(user.getUUID(), "attack") < 5 && target.level() instanceof net.minecraft.server.level.ServerLevel level) {
					target.damageCooldownTime = 0;
					target.hurtServer(level, source, 1.0F);
				}
			}
		});

		uniqueSword = RuinUniques.register("test_unique_sword", Item::new,
			new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -2.4F), PROBE);
		uniqueHelm = RuinUniques.register("test_unique_helm", Item::new,
			new Item.Properties().humanoidArmor(RuinGear.armorMaterial(GEAR), ArmorType.HELMET), PROBE);
		uniqueBow = RuinUniques.register("test_unique_bow", BowItem::new, new Item.Properties().durability(384), PROBE);
		uniqueCharm = RuinUniques.register("test_unique_charm", Item::new, new Item.Properties().stacksTo(1), PROBE);
		uniqueThrower = RuinUniques.register("test_unique_thrower", Item::new, new Item.Properties().stacksTo(1), THROWER);
		uniqueRecurse = RuinUniques.register("test_unique_recurse", Item::new, new Item.Properties().stacksTo(1), RECURSE);
		uniqueOrphan = RuinUniques.register("test_unique_orphan", Item::new, new Item.Properties().stacksTo(1), "test_undefined");

		gearSword = RuinGear.sword("test_gear_sword", GEAR);
		gearAxe = RuinGear.axe("test_gear_axe", GEAR);
		gearPickaxe = RuinGear.pickaxe("test_gear_pickaxe", GEAR);
		gearChest = RuinGear.armorPiece("test_gear_chestplate", GEAR, ArmorType.CHESTPLATE);
	}
}
