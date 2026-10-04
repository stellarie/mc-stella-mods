package com.stella.ruins.reward;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

/** The affix path end to end: a real smithing menu, the recipe from the data file, and the wielder. */
public class RuinAffixesGameTest {
	private static SmithingMenu menu(final ServerPlayer player, final ItemStack template, final ItemStack base, final ItemStack addition) {
		SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template);
		menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition);
		return menu;
	}

	private static ItemStack smith(final ServerPlayer player, final ItemStack template, final ItemStack base, final ItemStack addition) {
		return menu(player, template, base, addition).getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
	}

	private static ItemStack keen(final ServerPlayer player, final ItemStack base) {
		return smith(player, new ItemStack(RewardTestInit.keen.template()), base, new ItemStack(Items.AMETHYST_SHARD));
	}

	private static ItemStack sharp(final ServerPlayer player, final ItemStack base) {
		return smith(player, new ItemStack(RewardTestInit.sharp.template()), base, new ItemStack(Items.QUARTZ));
	}

	private static List<ItemAttributeModifiers.Entry> affixEntries(final ItemStack stack, final String affixId) {
		List<ItemAttributeModifiers.Entry> out = new ArrayList<>();
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.modifier().id().getPath().startsWith("affix/" + affixId + "/")) {
				out.add(entry);
			}
		}
		return out;
	}

	private static List<String> affixLore(final ItemStack stack) {
		List<String> keys = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (line.getContents() instanceof TranslatableContents contents && contents.getKey().startsWith(RuinAffixes.LORE_KEY_PREFIX)) {
					keys.add(contents.getKey());
				}
			}
		}
		return keys;
	}

	private static boolean keepsVanillaAttack(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(entry -> entry.modifier().is(Item.BASE_ATTACK_DAMAGE_ID));
	}

	@GameTest
	public void theSmithingTableWritesAnAffix(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack result = keen(player, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(!result.isEmpty(), "the smithing table gave no result");
		helper.assertTrue("test_keen".equals(RuinAffixes.affixOf(result)), "affix component is " + RuinAffixes.affixOf(result));
		List<ItemAttributeModifiers.Entry> entries = affixEntries(result, "test_keen");
		helper.assertTrue(entries.size() == 1, "affix modifiers: " + entries.size());
		ItemAttributeModifiers.Entry entry = entries.getFirst();
		helper.assertTrue(entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.modifier().amount() == 2.0 && entry.slot() == EquipmentSlotGroup.MAINHAND,
			"modifier is " + entry);
		helper.assertTrue(entry.modifier().id().equals(StellaRuins.id("affix/test_keen/mainhand/attack_damage")), "modifier id is " + entry.modifier().id());
		helper.assertTrue(keepsVanillaAttack(result), "the sword lost its own attack damage");
		helper.assertTrue(affixLore(result).equals(List.of("affix.mod-stella-ruins.test_keen")), "affix lore is " + affixLore(result));
		helper.assertTrue(result.getCount() == 1, "result count " + result.getCount());
		RewardTestSupport.result("affixSmith", "affix=" + RuinAffixes.affixOf(result) + " modifier=" + entry.modifier().id() + " lore=" + affixLore(result));
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aSecondAffixReplacesTheFirst(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack first = keen(player, new ItemStack(Items.IRON_SWORD));
		ItemStack second = sharp(player, first);
		helper.assertTrue(!second.isEmpty(), "the second affix gave no result");
		helper.assertTrue("test_sharp".equals(RuinAffixes.affixOf(second)), "affix component is " + RuinAffixes.affixOf(second));
		helper.assertTrue(affixEntries(second, "test_keen").isEmpty(), "the old modifiers stayed");
		helper.assertTrue(affixEntries(second, "test_sharp").size() == 2, "new modifiers: " + affixEntries(second, "test_sharp").size());
		helper.assertTrue(affixLore(second).equals(List.of("affix.mod-stella-ruins.test_sharp")), "affix lore is " + affixLore(second));
		helper.assertTrue(keepsVanillaAttack(second), "the sword lost its own attack damage");
		RewardTestSupport.result("affixReplace", "affix=" + RuinAffixes.affixOf(second) + " lore=" + affixLore(second));
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theSameAffixAgainGivesNothing(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack first = keen(player, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(!first.isEmpty(), "the first smith gave no result");
		helper.assertTrue(keen(player, first).isEmpty(), "the same affix gave a result");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aBaseThatTheAffixRejectsGivesNothing(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		SmithingMenu menu = menu(player, new ItemStack(RewardTestInit.keen.template()), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.AMETHYST_SHARD));
		helper.assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "a pickaxe took the sword affix");
		helper.assertTrue(menu.getSlot(SmithingMenu.BASE_SLOT).mayPlace(new ItemStack(Items.IRON_SWORD)), "the base slot refuses a sword");
		helper.assertTrue(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).mayPlace(new ItemStack(RewardTestInit.keen.template())), "the template slot refuses the affix");
		helper.assertTrue(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).mayPlace(new ItemStack(Items.AMETHYST_SHARD)), "the addition slot refuses the material");
		helper.assertTrue(!menu.getSlot(SmithingMenu.BASE_SLOT).mayPlace(new ItemStack(Items.DIRT)), "the base slot accepts dirt");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void aWrongMaterialOrTemplateGivesNothing(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack wrongMaterial = smith(player, new ItemStack(RewardTestInit.keen.template()), sword, new ItemStack(Items.DIRT));
		helper.assertTrue(wrongMaterial.isEmpty(), "dirt paid for the affix");
		ItemStack wrongTemplate = smith(player, new ItemStack(Items.PAPER), sword, new ItemStack(Items.AMETHYST_SHARD));
		helper.assertTrue(wrongTemplate.isEmpty(), "paper worked as a template");
		ItemStack otherAffixMaterial = smith(player, new ItemStack(RewardTestInit.keen.template()), sword, new ItemStack(Items.QUARTZ));
		helper.assertTrue(otherAffixMaterial.isEmpty(), "the material of another affix paid for this one");
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void smithingKeepsTheRestOfTheItem(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack base = new ItemStack(Items.IRON_SWORD);
		base.set(DataComponents.CUSTOM_NAME, Component.literal("Old Friend"));
		base.setDamageValue(17);
		ItemStack result = keen(player, base);
		helper.assertTrue(result.getHoverName().getString().equals("Old Friend"), "name is " + result.getHoverName().getString());
		helper.assertTrue(result.getDamageValue() == 17, "damage is " + result.getDamageValue());
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theAffixReachesTheWielder(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack affixed = keen(player, new ItemStack(Items.IRON_SWORD));
		double[] plain = new double[1];
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				plain[0] = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
				player.setItemSlot(EquipmentSlot.MAINHAND, affixed);
			})
			.thenWaitUntil(() -> helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == plain[0] + 2.0,
				"attack damage " + player.getAttributeValue(Attributes.ATTACK_DAMAGE) + ", want " + (plain[0] + 2.0)))
			.thenExecute(() -> {
				RewardTestSupport.result("affixWielder", "plain=" + plain[0] + " affixed=" + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
				RewardTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void anArmorAffixSitsInTheArmorSlot(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		ItemStack result = smith(player, new ItemStack(RewardTestInit.ward.template()), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_NUGGET));
		helper.assertTrue(!result.isEmpty(), "the smithing table gave no result");
		List<ItemAttributeModifiers.Entry> entries = affixEntries(result, "test_ward");
		helper.assertTrue(entries.size() == 1 && entries.getFirst().slot() == EquipmentSlotGroup.CHEST, "entries: " + entries);
		double before = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		player.setItemSlot(EquipmentSlot.CHEST, result);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == before + 2.0,
				"toughness " + player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) + ", want " + (before + 2.0)))
			.thenExecute(() -> RewardTestSupport.leave(helper, player))
			.thenSucceed();
	}

	@GameTest
	public void aRecipeNamingAnUnknownAffixIsRejected(final GameTestHelper helper) {
		JsonObject unknown = new JsonObject();
		unknown.addProperty("type", "mod-stella-ruins:affix");
		unknown.addProperty("affix", "test_missing");
		JsonObject known = new JsonObject();
		known.addProperty("type", "mod-stella-ruins:affix");
		known.addProperty("affix", "test_keen");
		helper.assertTrue(AffixRecipe.MAP_CODEC.codec().parse(JsonOps.INSTANCE, unknown).isError(), "an unknown affix parsed");
		helper.assertTrue(AffixRecipe.MAP_CODEC.codec().parse(JsonOps.INSTANCE, known).isSuccess(), "a known affix did not parse");
		helper.succeed();
	}
}
