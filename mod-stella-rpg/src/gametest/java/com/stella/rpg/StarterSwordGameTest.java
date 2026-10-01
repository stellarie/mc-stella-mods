package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** The beginner sword: one gift to a new player when the world has no bonus chest. */
public final class StarterSwordGameTest {
	@GameTest
	public void onceWithoutBonusChest(final GameTestHelper helper) {
		boolean worldChest = helper.getLevel().getServer().getWorldGenSettings().options().generateBonusChest();
		helper.assertTrue(!worldChest, "the test world has a bonus chest, so the join case below proves nothing");
		ServerPlayer joined = RpgTestSupport.realPlayer(helper);
		int afterJoin = swords(joined);
		boolean second = StarterSword.onJoin(joined, false);
		boolean third = StarterSword.onJoin(joined, false);

		ServerPlayer withChest = RpgTestSupport.realPlayer(helper);
		clear(withChest);
		withChest.removeAttached(StarterSword.STARTER_GIVEN);
		boolean chestGift = StarterSword.onJoin(withChest, true);
		int chestSwords = swords(withChest);
		boolean markedByChest = withChest.getAttachedOrElse(StarterSword.STARTER_GIVEN, false);
		boolean laterGift = StarterSword.onJoin(withChest, false);

		RpgTestSupport.result("onceWithoutBonusChest", "join=" + afterJoin + " second=" + second + " third=" + third
			+ " chestGift=" + chestGift + " chestSwords=" + chestSwords + " later=" + laterGift + " now=" + swords(withChest));
		helper.assertTrue(afterJoin == 1, "a first join gave " + afterJoin + " swords");
		helper.assertTrue(!second && !third && swords(joined) == 1, "a later join gave another sword");
		helper.assertTrue(!chestGift && chestSwords == 0 && !markedByChest, "a bonus chest world still gave a sword");
		helper.assertTrue(laterGift && swords(withChest) == 1, "a world without the chest did not give the sword to a player who had none");
		helper.succeed();
	}

	@GameTest
	public void dropsWhenFull(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		clear(player);
		player.removeAttached(StarterSword.STARTER_GIVEN);
		for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
			player.getInventory().getNonEquipmentItems().set(slot, new ItemStack(Items.DIRT));
		}
		for (int slot = 0; slot < com.stella.rpg.bag.Bag.SIZE; slot++) {
			com.stella.rpg.bag.Bag.of(player).set(slot, new ItemStack(Items.DIRT));
		}
		boolean gift = StarterSword.onJoin(player, false);
		long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4.0)).stream()
			.filter(entity -> isBeginner(entity.getItem())).count();
		RpgTestSupport.result("dropsWhenFull", "gift=" + gift + " inInventory=" + swords(player) + " dropped=" + dropped);
		helper.assertTrue(gift && swords(player) == 0 && dropped == 1, "a full inventory did not drop the sword: dropped=" + dropped);
		helper.succeed();
	}

	@GameTest
	public void swordStats(final GameTestHelper helper) {
		ItemStack sword = StarterSword.create();
		ItemStack iron = new ItemStack(Items.IRON_SWORD);
		double damage = sword.get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
		double speed = sword.get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ATTACK_SPEED, 4.0, EquipmentSlot.MAINHAND);
		double ironSpeed = iron.get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ATTACK_SPEED, 4.0, EquipmentSlot.MAINHAND);
		double wood = new ItemStack(Items.WOODEN_SWORD).get(DataComponents.ATTRIBUTE_MODIFIERS)
			.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
		double ironDamage = iron.get(DataComponents.ATTRIBUTE_MODIFIERS)
			.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
		long damageEntries = sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE)).count();
		RpgTestSupport.result("swordStats", "name=" + sword.getHoverName().getString() + " damage=" + damage + " speed=" + speed
			+ " maxDamage=" + sword.getMaxDamage() + " repair=" + sword.isValidRepairItem(new ItemStack(Items.IRON_INGOT)));
		helper.assertTrue(sword.is(Items.IRON_SWORD), "the sword is " + sword.getItem());
		helper.assertTrue(sword.getHoverName().getString().equals("Beginner Sword"), "name " + sword.getHoverName().getString());
		helper.assertTrue(damage == 5.0 && wood < damage && damage < ironDamage, "damage " + damage + " wood " + wood + " iron " + ironDamage);
		helper.assertTrue(damageEntries == 1, "the sword carries " + damageEntries + " damage modifiers");
		helper.assertTrue(speed == ironSpeed, "speed " + speed + " iron " + ironSpeed);
		helper.assertTrue(sword.getMaxDamage() == 400 && sword.getDamageValue() == 0, "durability " + sword.getMaxDamage());
		helper.assertTrue(sword.getEnchantments().isEmpty(), "the sword is enchanted");
		helper.assertTrue(sword.isValidRepairItem(new ItemStack(Items.IRON_INGOT)) && !sword.isValidRepairItem(new ItemStack(Items.COPPER_INGOT)),
			"repair item");
		helper.assertTrue(sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().allMatch(entry -> entry.slot() == EquipmentSlotGroup.MAINHAND),
			"a modifier is not for the main hand");
		helper.succeed();
	}

	private static boolean isBeginner(final ItemStack stack) {
		return stack.is(Items.IRON_SWORD) && stack.getHoverName().getString().equals(StarterSword.NAME);
	}

	private static int swords(final ServerPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			count += isBeginner(stack) ? stack.getCount() : 0;
		}
		return count;
	}

	private static void clear(final ServerPlayer player) {
		player.getInventory().clearContent();
	}
}
