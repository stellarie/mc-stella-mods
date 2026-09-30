package com.stella.rpg;

import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.ItemTiers;
import com.stella.rpg.roll.WeaponRolls;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every weapon and armor piece rolls, from every source. The tier comes from the repair material. */
public final class RollCoverageGameTest {
	private static final Map<TagKey<Item>, Affix.Kind> TAGS = new LinkedHashMap<>();

	static {
		TAGS.put(ItemTags.SWORDS, Affix.Kind.WEAPON);
		TAGS.put(ItemTags.AXES, Affix.Kind.WEAPON);
		TAGS.put(ItemTags.SPEARS, Affix.Kind.WEAPON);
		TAGS.put(ItemTags.HEAD_ARMOR, Affix.Kind.ARMOR);
		TAGS.put(ItemTags.CHEST_ARMOR, Affix.Kind.ARMOR);
		TAGS.put(ItemTags.LEG_ARMOR, Affix.Kind.ARMOR);
		TAGS.put(ItemTags.FOOT_ARMOR, Affix.Kind.ARMOR);
	}

	@GameTest
	public void everyTaggedItemHasATier(final GameTestHelper helper) {
		var items = helper.getLevel().registryAccess().lookupOrThrow(Registries.ITEM);
		RandomSource random = RandomSource.create(11L);
		StringBuilder log = new StringBuilder();
		for (Map.Entry<TagKey<Item>, Affix.Kind> tag : TAGS.entrySet()) {
			HolderSet.Named<Item> members = items.get(tag.getKey()).orElse(null);
			helper.assertTrue(members != null && members.size() > 0, "the tag " + tag.getKey().location() + " is empty");
			int count = 0;
			for (Holder<Item> holder : members) {
				if (!holder.unwrapKey().orElseThrow().identifier().getNamespace().equals("minecraft")) {
					continue;
				}
				ItemStack stack = new ItemStack(holder.value());
				ItemTiers.Info info = ItemTiers.of(stack);
				helper.assertTrue(info != null, holder.getRegisteredName() + " has no tier");
				helper.assertTrue(info.kind() == tag.getValue(), holder.getRegisteredName() + " is kind " + info.kind());
				helper.assertTrue(WeaponRolls.roll(stack, random) && stack.has(WeaponRolls.STAT_ROLLS), holder.getRegisteredName() + " did not roll");
				count++;
			}
			log.append(tag.getKey().location().getPath()).append('=').append(count).append(' ');
		}
		for (Item item : List.of(Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.SHIELD, Items.ELYTRA)) {
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(ItemTiers.of(stack) != null, item + " has no tier");
			helper.assertTrue(WeaponRolls.roll(stack, random), item + " did not roll");
		}
		helper.assertTrue(ItemTiers.of(new ItemStack(Items.IRON_PICKAXE)) == null && ItemTiers.of(new ItemStack(Items.IRON_INGOT)) == null,
			"a tool or an ingot has a tier");
		RpgTestSupport.result("everyTaggedItemHasATier", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void tierFromMaterial(final GameTestHelper helper) {
		String[] materials = {"wood", "stone", "copper", "gold", "iron", "diamond", "netherite"};
		Item[][] rows = {
			{Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD},
			{Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE},
			{Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.GOLDEN_SPEAR, Items.IRON_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR},
			{Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.COPPER_HELMET, Items.GOLDEN_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET},
			{Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.COPPER_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
			{Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.COPPER_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
			{Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.COPPER_BOOTS, Items.GOLDEN_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS}
		};
		// Leather maps to wood and chainmail to stone. The row order of the armor rows is leather, chainmail, copper.
		String[] armorTiers = {"wood", "stone", "copper", "gold", "iron", "diamond", "netherite"};
		for (int row = 0; row < rows.length; row++) {
			String[] expected = row < 3 ? materials : armorTiers;
			for (int column = 0; column < 7; column++) {
				ItemTiers.Info info = ItemTiers.of(new ItemStack(rows[row][column]));
				helper.assertTrue(info != null && info.tier().equals(expected[column]) && info.material().equals(expected[column]),
					rows[row][column] + " resolved to " + info + " expected " + expected[column]);
			}
		}
		ItemTiers.Info turtle = ItemTiers.of(new ItemStack(Items.TURTLE_HELMET));
		helper.assertTrue(turtle != null && turtle.tier().equals("stone") && turtle.kind() == Affix.Kind.ARMOR, "turtle helmet " + turtle);
		for (Item item : List.of(Items.SHIELD, Items.ELYTRA)) {
			ItemTiers.Info info = ItemTiers.of(new ItemStack(item));
			helper.assertTrue(info != null && info.tier().equals("iron") && info.kind() == Affix.Kind.ARMOR, item + " resolved to " + info);
		}
		for (Item item : List.of(Items.BOW, Items.CROSSBOW)) {
			ItemTiers.Info info = ItemTiers.of(new ItemStack(item));
			helper.assertTrue(info != null && info.tier().equals("ranged") && info.material().equals("iron"), item + " resolved to " + info);
		}
		for (Item item : List.of(Items.TRIDENT, Items.MACE)) {
			ItemTiers.Info info = ItemTiers.of(new ItemStack(item));
			helper.assertTrue(info != null && info.tier().equals("heavy") && info.material().equals("diamond"), item + " resolved to " + info);
		}

		List<String> voidGear = new ArrayList<>();
		for (String name : List.of("void_sword", "void_axe", "void_spear", "void_helmet", "void_chestplate", "void_leggings", "void_boots")) {
			ItemTiers.Info info = ItemTiers.byId(Identifier.fromNamespaceAndPath("mod-stella-tower", name));
			helper.assertTrue(info != null && info.tier().equals("void") && info.material().equals("void"), name + " resolved to " + info);
			voidGear.add(name + "=" + info.kind());
		}
		helper.assertTrue(ItemTiers.byId(Identifier.fromNamespaceAndPath("mod-stella-tower", "void_key")) == null
			&& ItemTiers.byId(Identifier.fromNamespaceAndPath("mod-stella-tower", "void_ingot")) == null
			&& ItemTiers.byId(Identifier.fromNamespaceAndPath("othermod", "void_sword")) == null, "a void item that is not gear has a tier");
		helper.assertTrue(ItemTiers.byId(Identifier.fromNamespaceAndPath("mod-stella-tower", "void_sword")).kind() == Affix.Kind.WEAPON
			&& ItemTiers.byId(Identifier.fromNamespaceAndPath("mod-stella-tower", "void_boots")).kind() == Affix.Kind.ARMOR, "void gear kind");

		RpgConfig.RollTier voidTier = RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().get("void");
		RpgConfig.RollTier netherite = RpgConfig.DEFAULT_WEAPON_ROLLS.tiers().get("netherite");
		helper.assertTrue(voidTier.min() == 6 && voidTier.max() == 12, "void stat range " + voidTier.min() + ".." + voidTier.max());
		for (Affix affix : Affix.values()) {
			RpgConfig.AffixRange expected = netherite.affixes().get(affix);
			RpgConfig.AffixRange actual = voidTier.affixes().get(affix);
			helper.assertTrue(Math.abs(actual.min() - expected.min() * 1.5) < 1e-9 && Math.abs(actual.max() - expected.max() * 1.5) < 1e-9,
				"void " + affix + " " + actual + " is not netherite " + expected + " x 1.5");
		}
		RpgTestSupport.result("tierFromMaterial", "void=" + voidGear);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void rollsOnPossession(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
		player.getInventory().setItem(1, new ItemStack(Items.COPPER_SPEAR));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		ItemStack rolled = new ItemStack(Items.DIAMOND_SWORD);
		rolled.set(WeaponRolls.STAT_ROLLS, Map.of("str", 9, "vit", 9, "luk", 9));
		player.getInventory().setItem(2, rolled);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		player.getInventory().setItem(3, pickaxe);

		// The scan runs every 20 server ticks, so 25 ticks always include one run.
		helper.runAfterDelay(25, () -> {
			List<ItemStack> held = new ArrayList<>(player.getInventory().getNonEquipmentItems());
			held.add(player.getItemBySlot(EquipmentSlot.HEAD));
			held.add(player.getItemBySlot(EquipmentSlot.OFFHAND));
			int rolls = 0;
			for (ItemStack stack : held) {
				if (stack.isEmpty() || stack.is(Items.IRON_PICKAXE)) {
					continue;
				}
				helper.assertTrue(stack.has(WeaponRolls.STAT_ROLLS), stack + " did not roll within 25 ticks");
				rolls++;
			}
			helper.assertTrue(rolled.get(WeaponRolls.STAT_ROLLS).equals(Map.of("str", 9, "vit", 9, "luk", 9)), "a rolled item rerolled");
			helper.assertTrue(!pickaxe.has(WeaponRolls.STAT_ROLLS), "a pickaxe rolled");
			Map<String, Integer> first = new LinkedHashMap<>(player.getInventory().getNonEquipmentItems().stream()
				.filter(stack -> stack.is(Items.IRON_SWORD)).findFirst().orElseThrow().get(WeaponRolls.STAT_ROLLS));
			WeaponRolls.rollPossessed(player);
			WeaponRolls.rollPossessed(player);
			Map<String, Integer> second = player.getInventory().getNonEquipmentItems().stream()
				.filter(stack -> stack.is(Items.IRON_SWORD)).findFirst().orElseThrow().get(WeaponRolls.STAT_ROLLS);
			RpgTestSupport.result("rollsOnPossession", "rolled=" + rolls + " first=" + first + " second=" + second);
			helper.assertTrue(first.equals(second), "a second scan rerolled " + first + " to " + second);
			helper.succeed();
		});
	}
}
