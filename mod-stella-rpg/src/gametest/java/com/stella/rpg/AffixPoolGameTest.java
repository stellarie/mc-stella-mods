package com.stella.rpg;

import com.stella.rpg.roll.WeaponRolls;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Which affixes and stats each kind of item rolls, and the boots speed affix. */
public final class AffixPoolGameTest {
	private static final int ROLLS = 200;
	private static final Set<String> WEAPON = Set.of("damage_flat", "damage_pct", "armor_pen");
	private static final Set<String> ARMOR = Set.of("hp_flat", "hp_pct", "reduction_flat", "reduction_pct", "magic_res_pct");
	private static final Set<String> BOOTS = Set.of("hp_flat", "hp_pct", "reduction_flat", "reduction_pct", "magic_res_pct", "move_speed_pct");
	private static final Set<String> BOOK = Set.of("matk_flat", "matk_pct", "magic_pen");

	@GameTest
	public void poolsPerKind(final GameTestHelper helper) {
		RpgTestSupport.withAffixChance(0.5, () -> {
			RandomSource random = RandomSource.create(20261003L);
			Set<String> weaponStats = new HashSet<>();
			Set<String> bookStats = new HashSet<>();
			Set<String> weapon = rolled(helper, Items.IRON_SWORD, random, weaponStats);
			Set<String> chest = rolled(helper, Items.DIAMOND_CHESTPLATE, random, new HashSet<>());
			Set<String> helmet = rolled(helper, Items.IRON_HELMET, random, new HashSet<>());
			Set<String> boots = rolled(helper, Items.IRON_BOOTS, random, new HashSet<>());
			Set<String> book = rolled(helper, Items.BOOK, random, bookStats);
			RpgTestSupport.result("poolsPerKind", "weapon=" + weapon + " chest=" + chest + " boots=" + boots + " book=" + book
				+ " bookStats=" + bookStats);
			helper.assertTrue(weapon.equals(WEAPON), "weapon affixes were " + weapon);
			helper.assertTrue(weaponStats.size() == 6, "weapon stats were " + weaponStats);
			helper.assertTrue(chest.equals(ARMOR) && helmet.equals(ARMOR), "armor affixes were " + chest + " " + helmet);
			helper.assertTrue(boots.equals(BOOTS), "boots affixes were " + boots);
			helper.assertTrue(book.equals(BOOK), "spellbook affixes were " + book);
			helper.assertTrue(bookStats.equals(Set.of("int")), "spellbook stats were " + bookStats);
		});
		helper.succeed();
	}

	@GameTest
	public void bootsSpeedApplies(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		double base = player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		RpgAttributes.refresh(player);
		double bare = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		player.setItemSlot(EquipmentSlot.FEET, RpgTestSupport.withAffixes(Items.IRON_BOOTS, Map.of("move_speed_pct", 100)));
		RpgAttributes.refresh(player);
		double worn = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		RpgAttributes.refresh(player);
		double removed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		RpgTestSupport.result("bootsSpeedApplies", "base=" + base + " bare=" + bare + " worn=" + worn + " removed=" + removed);
		helper.assertTrue(Math.abs(bare - base) < 1e-9, "speed changed without boots: " + bare);
		helper.assertTrue(Math.abs(worn - base * 1.10) < 1e-9, "10 percent boots gave " + worn + " expected " + base * 1.10);
		helper.assertTrue(Math.abs(removed - base) < 1e-9, "speed stayed after the boots came off: " + removed);
		helper.succeed();
	}

	private static Set<String> rolled(final GameTestHelper helper, final Item item, final RandomSource random, final Set<String> stats) {
		Set<String> affixes = new HashSet<>();
		for (int i = 0; i < ROLLS; i++) {
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(WeaponRolls.roll(stack, random), item + " did not roll");
			Map<String, Integer> found = stack.get(WeaponRolls.AFFIX_ROLLS);
			if (found != null) {
				affixes.addAll(found.keySet());
			}
			stats.addAll(stack.get(WeaponRolls.STAT_ROLLS).keySet());
		}
		return affixes;
	}
}
