package com.stella.rpg;

import com.stella.rpg.job.JobItems;
import com.stella.rpg.roll.Affix;
import com.stella.rpg.roll.BonusTooltip;
import com.stella.rpg.roll.WeaponRolls;
import com.stella.rpg.tomb.GraveCompass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every description line an item of this mod shows is at most 24 characters wide. */
public final class DescriptionWidthGameTest {
	private static final String MOD_ID = "mod-stella-rpg";

	@GameTest
	public void everyRegisteredItemShowsShortDescriptionLines(final GameTestHelper helper) {
		List<String> tooLong = new ArrayList<>();
		int items = 0;
		int shown = 0;
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			items++;
			List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, new ItemStack(BuiltInRegistries.ITEM.getValue(id))));
			shown += lines.size();
			tooLong.addAll(DescriptionSupport.tooLong(lines));
		}
		RpgTestSupport.result("descriptionWidthItems", "items=" + items + " lines=" + shown + " tooLong=" + tooLong.size());
		helper.assertTrue(shown > 0, "no registered item showed a description line");
		helper.assertTrue(tooLong.isEmpty(), "description lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		helper.succeed();
	}

	@GameTest
	public void jobItemDescriptionsKeepEveryWordAfterTheSplit(final GameTestHelper helper) {
		List<String> knight = DescriptionSupport.text(DescriptionSupport.lines(helper, new ItemStack(JobItems.KNIGHT_SWORD)));
		List<String> bow = DescriptionSupport.text(DescriptionSupport.lines(helper, new ItemStack(JobItems.MECHANICAL_BOW)));
		RpgTestSupport.result("descriptionWidthJobItems", "knight=" + knight + " bow=" + bow);
		helper.assertTrue(String.join(" ", knight).contains("Ignores 10% more armor"), "the sword lost its armor line: " + knight);
		helper.assertTrue(String.join(" ", knight).contains("10% chance to bleed the target"), "the sword lost its bleed line: " + knight);
		helper.assertTrue(String.join(" ", bow).contains("25% chance to fire a free arrow"), "the bow lost its free arrow line: " + bow);
		helper.succeed();
	}

	@GameTest
	public void rolledBonusLinesFitOnMaterialsAndGear(final GameTestHelper helper) {
		Map<String, Integer> affixes = new HashMap<>();
		for (Affix affix : Affix.values()) {
			affixes.put(affix.key(), affix.percent() ? 125 : 12);
		}
		Map<String, Integer> stats = new HashMap<>();
		for (Stat stat : Stat.values()) {
			stats.put(stat.key(), 3);
		}
		List<String> tooLong = new ArrayList<>();
		int shown = 0;
		for (net.minecraft.world.item.Item item : List.of(Items.IRON_INGOT, Items.DIAMOND_SWORD, Items.NETHERITE_CHESTPLATE)) {
			ItemStack stack = new ItemStack(item);
			stack.set(WeaponRolls.STAT_ROLLS, stats);
			stack.set(WeaponRolls.AFFIX_ROLLS, affixes);
			List<String> lines = DescriptionSupport.text(BonusTooltip.lines(stack));
			shown += lines.size();
			tooLong.addAll(DescriptionSupport.tooLong(lines));
		}
		RpgTestSupport.result("descriptionWidthRolls", "lines=" + shown + " tooLong=" + tooLong.size());
		helper.assertTrue(shown > 0, "a rolled stack showed no bonus line");
		helper.assertTrue(tooLong.isEmpty(), "bonus lines over " + DescriptionSupport.LIMIT + " characters: " + tooLong);
		helper.succeed();
	}

	@GameTest
	public void graveCompassLoreFitsAndKeepsTheCoordinates(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		BlockPos tomb = new BlockPos(-1234, 64, 5678);
		player.setAttached(GraveCompass.LAST_TOMB, GlobalPos.of(helper.getLevel().dimension(), tomb));
		GraveCompass.give(player, player);
		ItemStack compass = ItemStack.EMPTY;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (GraveCompass.isGraveCompass(player.getInventory().getItem(slot))) {
				compass = player.getInventory().getItem(slot);
			}
		}
		helper.assertTrue(!compass.isEmpty(), "the player received no grave compass");
		List<String> lines = DescriptionSupport.text(DescriptionSupport.lines(helper, compass));
		RpgTestSupport.result("descriptionWidthCompass", "lines=" + lines);
		helper.assertTrue(DescriptionSupport.tooLong(lines).isEmpty(), "grave compass lines over " + DescriptionSupport.LIMIT + ": " + lines);
		String joined = String.join(" ", lines);
		helper.assertTrue(joined.contains("Points to your tombstone"), "the compass lost its pointer line: " + lines);
		helper.assertTrue(joined.contains("-1234, 64, 5678 in "), "the compass lost its coordinates: " + lines);
		helper.succeed();
	}
}
