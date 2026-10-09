package com.stella.rpg;

import com.stella.rpg.api.BonusGrantShare;
import com.stella.rpg.net.StatsSyncPayload;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A granted bonus reaches the effective stat and the stats screen payload while the item is held or worn. */
public final class StatBonusReachGameTest {
	@SuppressWarnings("unchecked")
	private static void grant(final GameTestHelper helper, final ItemStack stack, final Map<String, Integer> delta) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(BonusGrantShare.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof Function<?, ?>, "the grant share is " + shared);
		helper.assertTrue(((Function<Object[], Boolean>) shared).apply(new Object[] {stack, delta}), "the grant was refused");
	}

	@GameTest
	public void worn_or_held_raises_stat(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.setStat(player, Stat.DEX, 5);
		int baseEffective = StatBonuses.effective(player, Stat.DEX);
		int basePayload = StatsSyncPayload.of(player).bonus()[Stat.DEX.ordinal()];

		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		grant(helper, sword, Map.of("dex", 12));
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		int held = StatBonuses.effective(player, Stat.DEX);
		int heldPayload = StatsSyncPayload.of(player).bonus()[Stat.DEX.ordinal()];

		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		grant(helper, chest, Map.of("dex", 3));
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		int worn = StatBonuses.effective(player, Stat.DEX);
		int wornPayload = StatsSyncPayload.of(player).bonus()[Stat.DEX.ordinal()];

		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		player.getInventory().setItem(9, sword.copy());
		int packed = StatBonuses.effective(player, Stat.DEX);

		RpgTestSupport.result("statBonusReach", "base=" + baseEffective + " held=" + held + " worn=" + worn + " packed=" + packed
			+ " payload=" + basePayload + "/" + heldPayload + "/" + wornPayload);
		helper.assertTrue(held == baseEffective + 12, "held " + held + " base " + baseEffective);
		helper.assertTrue(worn == baseEffective + 15, "worn " + worn + " base " + baseEffective);
		helper.assertTrue(packed == baseEffective + 3, "a sword in the pack still counted: " + packed);
		helper.assertTrue(heldPayload == basePayload + 12 && wornPayload == basePayload + 15,
			"payload " + basePayload + "/" + heldPayload + "/" + wornPayload);
		helper.succeed();
	}
}
