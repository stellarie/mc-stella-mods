package com.stella.equips;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineSpecials.Special;
import com.stella.equips.refine.RefineTier;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refine specials scale with the material of the piece. A better material gives a bigger number for the same
 * special, in the effect itself and in the tooltip that names it.
 */
public class RefineTierGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.refine-tier");
	private static final double TOLERANCE = 1.0e-6;
	private static final float HIT = 100.0F;

	static {
		RefineRolls.install();
	}

	private record Tier(Item item, double factor) {
	}

	@GameTest
	public void factorsByMaterial(final GameTestHelper helper) {
		List<Tier> tiers = List.of(
			new Tier(Items.WOODEN_SWORD, 0.6), new Tier(Items.LEATHER_CHESTPLATE, 0.6),
			new Tier(Items.STONE_AXE, 0.8), new Tier(Items.CHAINMAIL_HELMET, 0.8), new Tier(Items.COPPER_SWORD, 0.8),
			new Tier(Items.COPPER_CHESTPLATE, 0.8), new Tier(Items.GOLDEN_SWORD, 0.8), new Tier(Items.GOLDEN_BOOTS, 0.8),
			new Tier(Items.IRON_SWORD, 1.0), new Tier(Items.IRON_LEGGINGS, 1.0),
			new Tier(Items.DIAMOND_SWORD, 1.35), new Tier(Items.DIAMOND_CHESTPLATE, 1.35),
			new Tier(Items.NETHERITE_SWORD, 1.7), new Tier(Items.NETHERITE_BOOTS, 1.7),
			new Tier(Items.BOW, 1.0), new Tier(Items.CROSSBOW, 1.0), new Tier(Items.SHIELD, 1.0));
		for (Tier tier : tiers) {
			double factor = RefineTier.factor(new ItemStack(tier.item));
			helper.assertTrue(Math.abs(factor - tier.factor) < TOLERANCE, tier.item + " has factor " + factor + ", not " + tier.factor);
		}
		helper.assertTrue(RefineTier.factor(new ItemStack(TestItems.BOOK)) == 1.0, "a spellbook is not 1.0");
		helper.assertTrue(RefineTier.factor(new ItemStack(Items.STICK)) == 1.0 && RefineTier.factor(ItemStack.EMPTY) == 1.0, "an unknown material is not 1.0");
		helper.assertTrue(RefineEffects.chance(80.0, 2.0) == 100.0, "a chance passed 100 percent: " + RefineEffects.chance(80.0, 2.0));
		LOG.info("[BEHAVIOR] test=factorsByMaterial RESULT checked={}", tiers.size());
		helper.succeed();
	}

	@GameTest(padding = 20)
	public void diamondBeatsIron(final GameTestHelper helper) {
		// Weapon: the Keen chance, seen through the roll the effect makes.
		ServerPlayer iron = holder(helper, refined(Items.IRON_SWORD, Refine.SEARING_LEVEL));
		ServerPlayer diamond = holder(helper, refined(Items.DIAMOND_SWORD, Refine.SEARING_LEVEL));
		RefineRolls.force(iron, false);
		RefineRolls.force(diamond, false);
		melee(helper, iron, spider(helper, 3, 3));
		melee(helper, diamond, spider(helper, 5, 3));
		helper.assertTrue(RefineRolls.rolls(iron).equals(List.of(RefineRolls.roll(Special.KEEN, RefineEffects.KEEN_CHANCE))), "iron Keen rolled " + RefineRolls.rolls(iron));
		helper.assertTrue(RefineRolls.rolls(diamond).equals(List.of(RefineRolls.roll(Special.KEEN, RefineEffects.KEEN_CHANCE * 1.35))), "diamond Keen rolled " + RefineRolls.rolls(diamond));
		// Armour: the Fortitude health bonus on the stack.
		double ironFort = fortitude(refined(Items.IRON_CHESTPLATE, Refine.SEARING_LEVEL));
		double diamondFort = fortitude(refined(Items.DIAMOND_CHESTPLATE, Refine.SEARING_LEVEL));
		double leatherFort = fortitude(refined(Items.LEATHER_CHESTPLATE, Refine.SEARING_LEVEL));
		LOG.info("[BEHAVIOR] test=diamondBeatsIron RESULT leather={} iron={} diamond={}", leatherFort, ironFort, diamondFort);
		helper.assertTrue(Math.abs(ironFort - Refine.FORTITUDE_MULTIPLIER) < TOLERANCE, "iron Fortitude was " + ironFort);
		helper.assertTrue(Math.abs(diamondFort - Refine.FORTITUDE_MULTIPLIER * 1.35) < TOLERANCE, "diamond Fortitude was " + diamondFort);
		helper.assertTrue(leatherFort < ironFort, "leather Fortitude was " + leatherFort);
		// Armour: the Thorned damage the attacker takes back at +12.
		float ironBack = thornsBack(helper, Items.IRON_CHESTPLATE, 3);
		float diamondBack = thornsBack(helper, Items.DIAMOND_CHESTPLATE, 5);
		helper.assertTrue(Math.abs(ironBack - HIT * RefineEffects.THORN_SHARE) < 1.0e-2, "iron Thorned dealt back " + ironBack);
		helper.assertTrue(Math.abs(diamondBack - HIT * RefineEffects.THORN_SHARE * 1.35) < 1.0e-2, "diamond Thorned dealt back " + diamondBack);
		helper.succeed();
	}

	@GameTest
	public void tooltipShowsTheScaledNumbers(final GameTestHelper helper) {
		List<String> iron = lore(refined(Items.IRON_CHESTPLATE, Refine.SEARING_LEVEL));
		List<String> diamond = lore(refined(Items.DIAMOND_CHESTPLATE, Refine.SEARING_LEVEL));
		List<String> ironSword = lore(refined(Items.IRON_SWORD, Refine.SEARING_LEVEL));
		List<String> netheriteSword = lore(refined(Items.NETHERITE_SWORD, Refine.SEARING_LEVEL));
		LOG.info("[BEHAVIOR] test=tooltipShowsTheScaledNumbers RESULT iron={} diamond={} ironSword={} netheriteSword={}", iron, diamond, ironSword, netheriteSword);
		helper.assertTrue(iron.get(1).equals("Fortitude: +3% max health"), "iron lore " + iron);
		helper.assertTrue(diamond.get(1).startsWith("Fortitude: +4") && !diamond.get(1).equals(iron.get(1)), "diamond lore " + diamond);
		helper.assertTrue(ironSword.get(1).equals("Keen: 5% chance to hit for 1.5 times the damage"), "iron sword lore " + ironSword);
		helper.assertTrue(netheriteSword.get(1).equals("Keen: 8.5% chance to hit for 1.5 times the damage"), "netherite sword lore " + netheriteSword);
		helper.assertTrue(ironSword.get(2).equals("Searing: hits set foes alight for 3 s") && netheriteSword.get(2).equals("Searing: hits set foes alight for 5.1 s"),
			"searing lines " + ironSword.get(2) + " / " + netheriteSword.get(2));
		helper.succeed();
	}

	private static double fortitude(final ItemStack chestplate) {
		return chestplate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.filter(entry -> entry.modifier().id().equals(Refine.FORTITUDE_ID)).mapToDouble(entry -> entry.modifier().amount()).sum();
	}

	private static float thornsBack(final GameTestHelper helper, final Item chestplate, final int z) {
		Mob wearer = helper.spawnMob(EntityTypes.PIG, 3, 2, z).withNoFreeWill().spawn();
		wearer.setPersistenceRequired();
		wearer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		wearer.setHealth(1000.0F);
		wearer.setItemSlot(EquipmentSlot.CHEST, refined(chestplate, Refine.RENDING_LEVEL));
		Mob attacker = spider(helper, 5, z);
		float before = attacker.getHealth();
		wearer.damageCooldownTime = 0;
		DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
		wearer.hurtServer(helper.getLevel(), source, HIT);
		return before - attacker.getHealth();
	}

	private static List<String> lore(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static ItemStack refined(final Item item, final int level) {
		ItemStack stack = new ItemStack(item);
		Refine.setLevel(stack, level);
		return stack;
	}

	private static ServerPlayer holder(final GameTestHelper helper, final ItemStack held) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static Mob spider(final GameTestHelper helper, final int x, final int z) {
		Mob spider = helper.spawnMob(EntityTypes.SPIDER, x, 2, z).withNoFreeWill().spawn();
		spider.setPersistenceRequired();
		spider.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		spider.setHealth(1000.0F);
		return spider;
	}

	private static void melee(final GameTestHelper helper, final ServerPlayer player, final Mob victim) {
		victim.damageCooldownTime = 0;
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
	}
}
