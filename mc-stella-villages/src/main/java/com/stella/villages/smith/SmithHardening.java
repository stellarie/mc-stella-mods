package com.stella.villages.smith;

import com.stella.villages.McStellaVillages;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/** A village smith hardens a weapon: sneak and use the smith while holding it, and pay emeralds for more attack damage. */
public final class SmithHardening {
	public static final int COST_EMERALDS = 12;
	public static final double DAMAGE_PER_LEVEL = 1.0;
	public static final int MAX_LEVEL = 5;
	public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "hardening");
	private static final List<ResourceKey<VillagerProfession>> SMITHS = List.of(
		VillagerProfession.WEAPONSMITH, VillagerProfession.TOOLSMITH, VillagerProfession.ARMORER
	);

	private SmithHardening() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(SmithHardening::onUseEntity);
	}

	/** How many times the stack was hardened, read from its own modifier. */
	public static int level(final ItemStack stack) {
		double[] amount = {0.0};
		modifiers(stack).modifiers().forEach(entry -> {
			if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(MODIFIER_ID)) {
				amount[0] = entry.modifier().amount();
			}
		});
		return (int) Math.round(amount[0] / DAMAGE_PER_LEVEL);
	}

	private static InteractionResult onUseEntity(
		final Player player, final Level world, final InteractionHand hand, final Entity entity, final EntityHitResult hit
	) {
		if (!(player instanceof ServerPlayer customer) || !(world instanceof ServerLevel level) || hand != InteractionHand.MAIN_HAND
			|| !player.isShiftKeyDown() || player.isSpectator() || !(entity instanceof Villager smith) || smith.isBaby() || !isSmith(smith)) {
			return InteractionResult.PASS;
		}

		ItemStack weapon = player.getMainHandItem();
		if (!isWeapon(weapon)) {
			return InteractionResult.PASS;
		}

		int current = level(weapon);
		if (current >= MAX_LEVEL) {
			customer.sendSystemMessage(Component.literal("This weapon cannot be hardened any further (+" + MAX_LEVEL + ")"), true);
			log(customer, smith, weapon, current, "capped");
			return InteractionResult.SUCCESS;
		}
		if (player.getInventory().countItem(Items.EMERALD) < COST_EMERALDS) {
			customer.sendSystemMessage(Component.literal("Hardening costs " + COST_EMERALDS + " emeralds"), true);
			log(customer, smith, weapon, current, "short_of_emeralds");
			return InteractionResult.SUCCESS;
		}

		takeEmeralds(player.getInventory(), COST_EMERALDS);
		weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers(weapon).withModifierAdded(
			Attributes.ATTACK_DAMAGE,
			new AttributeModifier(MODIFIER_ID, (current + 1) * DAMAGE_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.MAINHAND
		));
		level.playSound(null, smith.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		customer.sendSystemMessage(Component.literal("Hardened: +" + (current + 1) + " attack damage"), true);
		log(customer, smith, weapon, current + 1, "hardened");
		return InteractionResult.SUCCESS;
	}

	private static boolean isSmith(final Villager villager) {
		return SMITHS.stream().anyMatch(villager.getVillagerData().profession()::is);
	}

	// A weapon is anything whose own attack damage the game adds to the hand holding it.
	private static boolean isWeapon(final ItemStack stack) {
		return !stack.isEmpty() && modifiers(stack).modifiers().stream().anyMatch(entry ->
			entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)
		);
	}

	private static ItemAttributeModifiers modifiers(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
	}

	private static void takeEmeralds(final Inventory inventory, final int count) {
		int left = count;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
		inventory.setChanged();
	}

	private static void log(final ServerPlayer customer, final Villager smith, final ItemStack weapon, final int level, final String outcome) {
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=smith_hardening outcome={} player={} smith={} item={} level={}",
			McStellaVillages.MOD_ID, outcome, customer.getUUID(), smith.getUUID(), weapon.getItem(), level
		);
	}
}
