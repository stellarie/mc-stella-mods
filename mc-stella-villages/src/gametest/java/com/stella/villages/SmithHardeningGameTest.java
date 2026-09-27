package com.stella.villages;

import com.stella.villages.smith.SmithHardening;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

/** A weaponsmith, toolsmith or armorer hardens the weapon a sneaking player holds, for emeralds, up to a cap. */
public class SmithHardeningGameTest {
	private static final List<ResourceKey<VillagerProfession>> SMITHS = List.of(
		VillagerProfession.WEAPONSMITH, VillagerProfession.TOOLSMITH, VillagerProfession.ARMORER
	);

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void everySmithHardensAWeaponForItsPrice(final GameTestHelper helper) {
		for (ResourceKey<VillagerProfession> profession : SMITHS) {
			Villager smith = smith(helper, profession);
			ServerPlayer player = customer(helper, new ItemStack(Items.IRON_SWORD), SmithHardening.COST_EMERALDS, true);
			double before = damage(player.getMainHandItem());
			use(helper, player, smith);
			double gained = damage(player.getMainHandItem()) - before;
			helper.assertTrue(gained == SmithHardening.DAMAGE_PER_LEVEL, profession.identifier() + " hardened the sword by " + gained);
			helper.assertTrue(emeralds(player) == 0, profession.identifier() + " left " + emeralds(player) + " emeralds of the price");
			PatrolTestSupport.result("everySmithHardensAWeaponForItsPrice", "smith=" + profession.identifier() + " gained=" + gained + " level=" + SmithHardening.level(player.getMainHandItem()));
		}
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void hardeningWithTooFewEmeraldsChangesNothing(final GameTestHelper helper) {
		Villager smith = smith(helper, VillagerProfession.WEAPONSMITH);
		ServerPlayer player = customer(helper, new ItemStack(Items.IRON_SWORD), SmithHardening.COST_EMERALDS - 1, true);
		double before = damage(player.getMainHandItem());
		use(helper, player, smith);
		helper.assertTrue(damage(player.getMainHandItem()) == before && emeralds(player) == SmithHardening.COST_EMERALDS - 1, "a short purse hardened the sword or lost emeralds");
		PatrolTestSupport.result("hardeningWithTooFewEmeraldsChangesNothing", "emeralds=" + emeralds(player));
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void hardeningStopsAtItsCap(final GameTestHelper helper) {
		Villager smith = smith(helper, VillagerProfession.WEAPONSMITH);
		ServerPlayer player = customer(helper, new ItemStack(Items.DIAMOND_AXE), SmithHardening.COST_EMERALDS * (SmithHardening.MAX_LEVEL + 1), true);
		double before = damage(player.getMainHandItem());
		for (int i = 0; i <= SmithHardening.MAX_LEVEL; i++) {
			use(helper, player, smith);
		}
		double gained = damage(player.getMainHandItem()) - before;
		helper.assertTrue(gained == SmithHardening.MAX_LEVEL * SmithHardening.DAMAGE_PER_LEVEL, "the axe gained " + gained + " past its cap");
		helper.assertTrue(emeralds(player) == SmithHardening.COST_EMERALDS, "a capped weapon still took " + (SmithHardening.COST_EMERALDS - emeralds(player)) + " emeralds");
		PatrolTestSupport.result("hardeningStopsAtItsCap", "gained=" + gained + " level=" + SmithHardening.level(player.getMainHandItem()) + " left=" + emeralds(player));
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void onlyASneakingPlayerAtASmithHardensAWeapon(final GameTestHelper helper) {
		ServerPlayer standing = customer(helper, new ItemStack(Items.IRON_SWORD), SmithHardening.COST_EMERALDS, false);
		use(helper, standing, smith(helper, VillagerProfession.WEAPONSMITH));
		ServerPlayer atFarmer = customer(helper, new ItemStack(Items.IRON_SWORD), SmithHardening.COST_EMERALDS, true);
		use(helper, atFarmer, smith(helper, VillagerProfession.FARMER));
		ServerPlayer withStick = customer(helper, new ItemStack(Items.STICK), SmithHardening.COST_EMERALDS, true);
		use(helper, withStick, smith(helper, VillagerProfession.WEAPONSMITH));
		// The in-level mock player is always creative, so the spectator is a mock built as one.
		ServerPlayer spectator = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SPECTATOR);
		spectator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		spectator.getInventory().add(new ItemStack(Items.EMERALD, SmithHardening.COST_EMERALDS));
		spectator.setShiftKeyDown(true);
		helper.assertTrue(spectator.isSpectator(), "the spectator mock is not a spectator, so the case proves nothing");
		use(helper, spectator, smith(helper, VillagerProfession.WEAPONSMITH));
		for (ServerPlayer player : List.of(standing, atFarmer, withStick, spectator)) {
			helper.assertTrue(SmithHardening.level(player.getMainHandItem()) == 0 && emeralds(player) == SmithHardening.COST_EMERALDS, "a hardening went through that should not have: " + player.getMainHandItem());
		}
		PatrolTestSupport.result("onlyASneakingPlayerAtASmithHardensAWeapon", "standing=0 farmer=0 stick=0 spectator=0");
		helper.succeed();
	}

	private static void use(final GameTestHelper helper, final ServerPlayer player, final Villager smith) {
		UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, smith, null);
	}

	private static Villager smith(final GameTestHelper helper, final ResourceKey<VillagerProfession> profession) {
		Villager villager = helper.spawnMob(EntityTypes.VILLAGER, 2, 2, 2).withNoFreeWill().spawn();
		villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), profession).withLevel(2));
		return villager;
	}

	private static ServerPlayer customer(final GameTestHelper helper, final ItemStack held, final int emeralds, final boolean sneaking) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		if (emeralds > 0) {
			player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		}
		player.setShiftKeyDown(sneaking);
		return player;
	}

	// The attack damage the stack gives a bare hand, as the game computes it.
	private static double damage(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
	}

	private static int emeralds(final ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}
}
