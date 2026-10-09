package com.stella.ruins.reward;

import com.stella.ruins.site.deepforge.DeepForgeItems;
import com.stella.ruins.site.spidernest.NestItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The six shipped hybrid bonuses: two pieces of one set and two of another, across sites. */
public class RuinHybridsGameTest {
	/** Each pair, as the set that fills the head and chest slots and the set that fills the legs and feet. */
	static final List<String[]> PAIRS = List.of(
		new String[] {"pilgrim", "champion"},
		new String[] {"hawkeye", "starweave"},
		new String[] {"silkweave", "deepsteel"},
		new String[] {"pilgrim", "hawkeye"},
		new String[] {"champion", "deepsteel"},
		new String[] {"starweave", "silkweave"});

	private static void wear(final ServerPlayer player, final String setId, final EquipmentSlot... slots) {
		RuinSets.SetDefinition set = RuinSets.get(setId).orElseThrow();
		for (EquipmentSlot slot : slots) {
			player.setItemSlot(slot, new ItemStack(set.pieces().get(slot)));
		}
	}

	@GameTest
	public void deepsteelAndSilkweaveAreFourPieceSetsOverTheirOwnItems(final GameTestHelper helper) {
		RuinSets.SetDefinition deepsteel = RuinSets.get("deepsteel").orElse(null);
		RuinSets.SetDefinition silkweave = RuinSets.get("silkweave").orElse(null);
		helper.assertTrue(deepsteel != null && deepsteel.pieces().size() == 4, "deepsteel is not a four piece set");
		helper.assertTrue(silkweave != null && silkweave.pieces().size() == 4, "silkweave is not a four piece set");
		helper.assertTrue(deepsteel.pieces().get(EquipmentSlot.HEAD) == DeepForgeItems.HELMET && deepsteel.pieces().get(EquipmentSlot.FEET) == DeepForgeItems.BOOTS, "deepsteel counts other items than its own");
		helper.assertTrue(silkweave.pieces().get(EquipmentSlot.CHEST) == NestItems.SILKWEAVE_CHESTPLATE && silkweave.pieces().get(EquipmentSlot.LEGS) == NestItems.SILKWEAVE_LEGGINGS, "silkweave counts other items than its own");
		helper.assertTrue(RuinSets.pieceOf(new ItemStack(DeepForgeItems.CHESTPLATE)).map(piece -> piece.setId().equals("deepsteel") && piece.slot() == EquipmentSlot.CHEST).orElse(false), "a deepsteel piece does not know its set");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void eachShippedPairGivesItsBonusAtTwoPlusTwoAndLosesItWhenAPieceLeaves(final GameTestHelper helper) {
		ServerPlayer player = RewardTestSupport.join(helper);
		List<String> checked = new ArrayList<>();
		for (String[] pair : PAIRS) {
			HybridBonus hybrid = RuinSets.hybrids().stream().filter(h -> h.requires().equals(Map.of(pair[0], 2, pair[1], 2))).findFirst().orElse(null);
			helper.assertTrue(hybrid != null, "no hybrid bonus for " + pair[0] + " and " + pair[1]);
			helper.assertTrue(!hybrid.attributes().isEmpty() || hybrid.every20Ticks() != null, hybrid.id() + " gives nothing");
			Identifier id = RuinSets.hybridModifierId(hybrid.id());
			wear(player, pair[0], EquipmentSlot.HEAD, EquipmentSlot.CHEST);
			wear(player, pair[1], EquipmentSlot.LEGS, EquipmentSlot.FEET);
			RuinSets.refresh(player);
			for (AttributeBonus bonus : hybrid.attributes()) {
				helper.assertTrue(player.getAttribute(bonus.attribute()).getModifier(id) != null, hybrid.id() + " gave no " + bonus.attribute().getRegisteredName() + " at 2 + 2");
			}
			player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			RuinSets.refresh(player);
			for (AttributeBonus bonus : hybrid.attributes()) {
				helper.assertTrue(player.getAttribute(bonus.attribute()).getModifier(id) == null, hybrid.id() + " kept " + bonus.attribute().getRegisteredName() + " after a piece left");
			}
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
			RuinSets.refresh(player);
			checked.add(hybrid.id());
		}
		List<String> shipped = RuinSets.hybrids().stream().map(HybridBonus::id).filter(id -> !id.startsWith("test_")).toList();
		helper.assertTrue(shipped.size() == PAIRS.size(), "the shipped hybrids are " + shipped + ", not just " + PAIRS.size() + " pairs");
		RewardTestSupport.result("shippedHybrids", "checked=" + checked);
		RewardTestSupport.leave(helper, player);
		helper.succeed();
	}
}
