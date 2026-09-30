package com.stella.rpg;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** A world without the bonus chest gives each new player one beginner sword. */
public final class StarterSword {
	public static final AttachmentType<Boolean> STARTER_GIVEN = AttachmentRegistry.create(
		StellaRpg.id("starter_given"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);
	public static final String NAME = "Beginner Sword";
	/** Total attack damage 5 with the player's base 1. Wood is 4 and iron is 6. */
	public static final double DAMAGE_BONUS = 4.0;
	public static final int MAX_DAMAGE = 400;

	private StarterSword() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			onJoin(handler.getPlayer(), server.getWorldGenSettings().options().generateBonusChest()));
	}

	/** Gives the sword when the world has no bonus chest and the player never got one. Returns true on a gift. */
	public static boolean onJoin(final ServerPlayer player, final boolean bonusChest) {
		if (bonusChest || player.getAttachedOrElse(STARTER_GIVEN, false)) {
			return false;
		}
		player.setAttached(STARTER_GIVEN, true);
		ItemStack sword = create();
		if (!player.getInventory().add(sword)) {
			player.drop(sword, false, Prediction.SERVER_ONLY);
		}
		return true;
	}

	/** An iron sword with more damage than wood, less than iron, and 400 durability. Repair and refine follow the iron sword. */
	public static ItemStack create() {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal(NAME).withStyle(style -> style.withItalic(false)));
		sword.set(DataComponents.MAX_DAMAGE, MAX_DAMAGE);
		sword.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers(sword));
		return sword;
	}

	private static ItemAttributeModifiers modifiers(final ItemStack sword) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder().add(
			Attributes.ATTACK_DAMAGE,
			new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, DAMAGE_BONUS, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.MAINHAND
		);
		sword.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.filter(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED))
			.forEach(entry -> builder.add(entry.attribute(), entry.modifier(), entry.slot()));
		return builder.build();
	}
}
