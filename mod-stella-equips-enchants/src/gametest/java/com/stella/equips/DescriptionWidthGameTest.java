package com.stella.equips;

import com.stella.equips.compare.GearCompare;
import com.stella.equips.imbue.NetherImbue;
import com.stella.equips.mastery.WeaponMastery;
import com.stella.equips.refine.Refine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Every description line an item or a book of this mod shows is at most 24 characters wide. */
public final class DescriptionWidthGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final int[] REFINES = {0, 7, 10, 12, 15};
	private static final List<Item> GEAR = List.of(
		Items.LEATHER_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.IRON_LEGGINGS, Items.GOLDEN_BOOTS, Items.DIAMOND_HELMET,
		Items.NETHERITE_CHESTPLATE, Items.WOODEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_AXE, Items.NETHERITE_SWORD,
		Items.BOW, Items.CROSSBOW, Items.SHIELD, Items.TRIDENT, Items.MACE, Items.DIAMOND_PICKAXE
	);

	@GameTest
	public void everyEnchantmentBookShowsShortDescriptionLines(final GameTestHelper helper) {
		List<String> tooLong = new ArrayList<>();
		int books = 0;
		int shown = 0;
		for (Holder.Reference<Enchantment> holder : helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
			if (!MOD_ID.equals(holder.key().identifier().getNamespace())) {
				continue;
			}
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			stored.set(holder, holder.value().getMaxLevel());
			EnchantmentHelper.setEnchantments(book, stored.toImmutable());
			List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, book));
			books++;
			shown += lines.size();
			tooLong.addAll(DescriptionSupport.tooLong(holder.key().identifier().toString(), lines));
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=descriptionWidthBooks RESULT books={} lines={} tooLong={}", books, shown, tooLong.size());
		helper.assertTrue(books > 0 && shown >= books, "the books showed " + shown + " lines for " + books + " enchantments");
		helper.assertTrue(tooLong.isEmpty(), "description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		helper.succeed();
	}

	@GameTest
	public void everyRegisteredItemAndRefinedGearShowsShortDescriptionLines(final GameTestHelper helper) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (MOD_ID.equals(id.getNamespace())) {
				stacks.add(new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
			}
		}
		for (Item gear : GEAR) {
			stacks.add(new ItemStack(gear));
		}
		int registered = stacks.size();
		List<ItemStack> variants = new ArrayList<>();
		for (ItemStack stack : stacks) {
			for (int level : REFINES) {
				if (level > 0) {
					ItemStack refined = stack.copy();
					Refine.setLevel(refined, level);
					variants.add(refined);
				}
			}
			if (WeaponMastery.isWeapon(stack)) {
				ItemStack mastered = stack.copy();
				WeaponMastery.setKills(mastered, 400);
				variants.add(mastered);
			}
			if (NetherImbue.eligible(stack)) {
				variants.add(NetherImbue.imbued(stack));
			}
		}
		stacks.addAll(variants);
		List<String> tooLong = new ArrayList<>();
		int shown = 0;
		for (ItemStack stack : stacks) {
			List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, stack));
			shown += lines.size();
			tooLong.addAll(DescriptionSupport.tooLong(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " +" + Refine.level(stack), lines));
		}
		ItemStack sample = new ItemStack(Items.DIAMOND_CHESTPLATE);
		Refine.setLevel(sample, 15);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=descriptionWidthSample RESULT refinedChestplate={}", DescriptionSupport.text(DescriptionSupport.lines(helper, sample)));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=descriptionWidthItems RESULT registered={} stacks={} lines={} tooLong={}", registered, stacks.size(), shown, tooLong.size());
		helper.assertTrue(shown > registered, "the stacks showed only " + shown + " lines");
		helper.assertTrue(tooLong.isEmpty(), "description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong.stream().distinct().toList());
		helper.succeed();
	}

	@GameTest
	public void theCompareAndHeartLinesFit(final GameTestHelper helper) {
		GearCompare.Rolls held = new GearCompare.Rolls(Map.of("str", 1500), Map.of("reduction_pct", 12345, "magic_pen", 9999, "damage_flat", 700));
		List<GearCompare.Line> diff = GearCompare.diff(held, GearCompare.Rolls.EMPTY);
		List<String> compare = new ArrayList<>();
		compare.addAll(DescriptionSupport.text(GearCompare.tooltipLines(diff, true)));
		compare.addAll(DescriptionSupport.text(GearCompare.tooltipLines(diff, false)));
		ItemStack bag = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MOD_ID, "emerald_bag")));
		List<String> bagLines = DescriptionSupport.text(DescriptionSupport.lines(helper, bag));
		ItemStack piece = new ItemStack(Items.DIAMOND_CHESTPLATE);
		piece.set(com.stella.equips.heart.SlimeHeart.INSERTED, true);
		List<String> heartLines = DescriptionSupport.text(DescriptionSupport.lines(helper, piece));
		List<String> all = new ArrayList<>(compare);
		all.addAll(bagLines);
		all.addAll(heartLines);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=descriptionWidthExtras RESULT compare={} bag={} heart={}", compare, bagLines, heartLines);
		helper.assertTrue(compare.size() > 3 && !heartLines.isEmpty() && !bagLines.isEmpty(), "an extra tooltip showed no line");
		helper.assertTrue(DescriptionSupport.tooLong(all).isEmpty(), "extra lines over " + DescriptionSupport.LIMIT + " characters: " + DescriptionSupport.tooLong(all));
		helper.succeed();
	}
}
