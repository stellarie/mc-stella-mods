package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Quick bar mode acts on hotkey slots and keeps the weapon in hand; vanilla mode leaves the hotbar alone. */
public final class QuickbarModeGameTest {
	private static final int SLOT = WeaponSlot.SLOT;
	private static final BlockPos TARGET = new BlockPos(2, 1, 4);

	@GameTest
	public void switchingModes(final GameTestHelper helper) {
		ServerPlayer player = armed(helper);
		Inventory inventory = player.getInventory();
		inventory.setItem(4, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(QuickbarMode.isOn(player), "quick bar is not the default");

		QuickbarMode.set(player, false);
		helper.assertTrue(inventory.getSelectedSlot() == 0, "vanilla mode selected " + inventory.getSelectedSlot());
		helper.assertTrue(inventory.getItem(SLOT).is(Items.IRON_AXE), "index 35 lost its item: " + inventory.getItem(SLOT));
		WeaponSlot.Result vanilla = WeaponSlot.press(player, 4);
		helper.assertTrue(vanilla == WeaponSlot.Result.REFUSED, "the rule ran in vanilla mode: " + vanilla);
		helper.assertTrue(inventory.getItem(4).is(Items.IRON_SWORD) && inventory.getSelectedSlot() == 0, "vanilla mode moved an item");

		QuickbarMode.set(player, true);
		helper.assertTrue(inventory.getSelectedSlot() == SLOT && player.getMainHandItem().is(Items.IRON_AXE), "quick bar did not take the weapon");
		RpgTestSupport.result("switchingModes", "vanilla=" + vanilla);
		helper.succeed();
	}

	@GameTest
	public void blockPlacesAtTheTarget(final GameTestHelper helper) {
		ServerPlayer player = aimedAtTop(helper);
		player.getInventory().setItem(3, new ItemStack(Items.COBBLESTONE, 16));

		WeaponSlot.Result result = WeaponSlot.press(player, 3);
		helper.assertTrue(result == WeaponSlot.Result.USED, "result " + result);
		helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET.above());
		helper.assertTrue(player.getInventory().getItem(3).getCount() == 15, "count " + player.getInventory().getItem(3).getCount());
		helper.assertTrue(stillArmed(player), "the weapon left the hand");
		RpgTestSupport.result("blockPlacesAtTheTarget", "count=" + player.getInventory().getItem(3).getCount());
		helper.succeed();
	}

	@GameTest
	public void flintLightsTheTarget(final GameTestHelper helper) {
		ServerPlayer player = aimedAtTop(helper);
		player.getInventory().setItem(6, new ItemStack(Items.FLINT_AND_STEEL));

		WeaponSlot.Result result = WeaponSlot.press(player, 6);
		helper.assertTrue(result == WeaponSlot.Result.USED, "result " + result);
		helper.assertBlockPresent(Blocks.FIRE, TARGET.above());
		helper.assertTrue(player.getInventory().getItem(6).getDamageValue() == 1, "damage " + player.getInventory().getItem(6).getDamageValue());
		helper.assertTrue(stillArmed(player), "the weapon left the hand");
		RpgTestSupport.result("flintLightsTheTarget", "damage=" + player.getInventory().getItem(6).getDamageValue());
		helper.succeed();
	}

	@GameTest
	public void shearsShearTheTargetedSheep(final GameTestHelper helper) {
		ServerPlayer player = armed(helper);
		Sheep sheep = helper.spawnWithNoFreeWill(EntityTypes.SHEEP, 2, 1, 4);
		aimAtEntity(helper, player, sheep);
		player.getInventory().setItem(6, new ItemStack(Items.SHEARS));

		WeaponSlot.Result result = WeaponSlot.press(player, 6);
		helper.assertTrue(result == WeaponSlot.Result.USED, "result " + result);
		helper.assertTrue(sheep.isSheared(), "the sheep kept its wool");
		helper.assertTrue(player.getInventory().getItem(6).getDamageValue() == 1, "damage " + player.getInventory().getItem(6).getDamageValue());
		AABB around = sheep.getBoundingBox().inflate(3.0);
		boolean wool = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, item -> item.getItem().is(net.minecraft.tags.ItemTags.WOOL)).isEmpty();
		helper.assertTrue(wool, "no wool dropped");
		helper.assertTrue(stillArmed(player), "the weapon left the hand");
		RpgTestSupport.result("shearsShearTheTargetedSheep", "damage=" + player.getInventory().getItem(6).getDamageValue());
		helper.succeed();
	}

	@GameTest
	public void bucketMilksTheTargetedCow(final GameTestHelper helper) {
		ServerPlayer player = armed(helper);
		Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, 2, 1, 4);
		aimAtEntity(helper, player, cow);
		player.getInventory().setItem(7, new ItemStack(Items.BUCKET));

		WeaponSlot.Result result = WeaponSlot.press(player, 7);
		helper.assertTrue(result == WeaponSlot.Result.USED, "result " + result);
		helper.assertTrue(player.getInventory().getItem(7).is(Items.MILK_BUCKET), "slot 7 holds " + player.getInventory().getItem(7));
		helper.assertTrue(stillArmed(player), "the weapon left the hand");
		RpgTestSupport.result("bucketMilksTheTargetedCow", "slot7=" + player.getInventory().getItem(7));
		helper.succeed();
	}

	@GameTest
	public void foodIsEatenAtOnce(final GameTestHelper helper) {
		ServerPlayer player = hungry(helper);
		int before = player.getFoodData().getFoodLevel();
		WeaponSlot.Result result = WeaponSlot.hotkey(player, 2, true);
		int apples = player.getInventory().getItem(2).getCount();
		RpgTestSupport.result("foodIsEatenAtOnce", "result=" + result + " apples=" + apples + " food=" + before + "->" + player.getFoodData().getFoodLevel());
		helper.assertTrue(result == WeaponSlot.Result.USED && apples == 1, "result " + result + ", apples " + apples);
		helper.assertTrue(player.getFoodData().getFoodLevel() > before, "the food level did not rise");
		helper.assertTrue(stillArmed(player) && !player.isUsingItem(), "the weapon is not back in hand");
		helper.succeed();
	}

	@GameTest
	public void potionIsDrunkAtOnce(final GameTestHelper helper) {
		ServerPlayer player = armed(helper);
		player.getInventory().setItem(3, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.SWIFTNESS));
		WeaponSlot.Result result = WeaponSlot.hotkey(player, 3, true);
		boolean fast = player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED);
		RpgTestSupport.result("potionIsDrunkAtOnce", "result=" + result + " fast=" + fast + " slot3=" + player.getInventory().getItem(3));
		helper.assertTrue(result == WeaponSlot.Result.USED && fast, "result " + result + ", speed " + fast);
		helper.assertTrue(player.getInventory().getItem(3).is(Items.GLASS_BOTTLE), "slot 3 holds " + player.getInventory().getItem(3));
		helper.assertTrue(stillArmed(player), "the weapon is not back in hand");
		helper.succeed();
	}

	private static ServerPlayer armed(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.getInventory().clearContent();
		player.getInventory().setItem(SLOT, new ItemStack(Items.IRON_AXE));
		player.getInventory().setSelectedSlot(SLOT);
		return player;
	}

	private static ServerPlayer hungry(final GameTestHelper helper) {
		ServerPlayer player = armed(helper);
		player.getFoodData().setFoodLevel(10);
		player.getInventory().setItem(2, new ItemStack(Items.APPLE, 2));
		return player;
	}

	/** The player stands at (2.5, 1, 2.5) and looks at the top face of a stone block two blocks ahead. */
	private static ServerPlayer aimedAtTop(final GameTestHelper helper) {
		ServerPlayer player = armed(helper);
		helper.setBlock(TARGET, Blocks.STONE);
		helper.setBlock(TARGET.above(), Blocks.AIR);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.teleportTo(at.x, at.y, at.z);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(2.5, 2.0, 4.5)));
		return player;
	}

	/** The player stands at (2.5, 1, 2.5) and looks at the middle of the entity. */
	private static void aimAtEntity(final GameTestHelper helper, final ServerPlayer player, final net.minecraft.world.entity.Entity target) {
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.teleportTo(at.x, at.y, at.z);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
	}

	private static boolean stillArmed(final ServerPlayer player) {
		return player.getInventory().getSelectedSlot() == SLOT && player.getMainHandItem().is(Items.IRON_AXE);
	}

}
