package com.stella.villages.patrol;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.stella.villages.McStellaVillages;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * A village head's kit: full netherite, every piece enchanted, and a netherite axe. With the spells mod
 * loaded, the axe carries one offensive spell, chosen at random, which the head casts on what it strikes.
 * The kit never drops, so a head that falls and returns a minute later is no netherite farm.
 */
public final class HeadKit {
	/** The spells mod's imbue component, named here so this mod needs no compile-time link to it. */
	public static final Identifier IMBUED_COMPONENT = Identifier.fromNamespaceAndPath("mod-stella-spells", "imbued_spell");
	/** The spells that strike a single foe, which a mob's imbued weapon casts. */
	public static final List<Identifier> OFFENSIVE_SPELLS = List.of(
		spell("spark_shot"), spell("frostbind"), spell("briar_snare"), spell("static_lash"), spell("tidal_push"),
		spell("wither_seal"), spell("ice_lance"), spell("soul_siphon"), spell("venom_dart"), spell("blinding_flash"),
		spell("ember_brand")
	);
	private static final Map<EquipmentSlot, Item> PIECES = Map.of(
		EquipmentSlot.HEAD, Items.NETHERITE_HELMET,
		EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE,
		EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS,
		EquipmentSlot.FEET, Items.NETHERITE_BOOTS,
		EquipmentSlot.MAINHAND, Items.NETHERITE_AXE
	);

	private HeadKit() {
	}

	private static Identifier spell(final String path) {
		return Identifier.fromNamespaceAndPath("mod-stella-spells", path);
	}

	public static boolean wears(final Mob head) {
		return head.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE);
	}

	public static void apply(final Mob head) {
		for (Map.Entry<EquipmentSlot, Item> piece : PIECES.entrySet()) {
			ItemStack stack = new ItemStack(piece.getValue());
			if (piece.getKey() == EquipmentSlot.MAINHAND) {
				enchant(head, stack, Enchantments.SHARPNESS, 5);
				enchant(head, stack, Enchantments.UNBREAKING, 3);
				imbue(head, stack);
			} else {
				enchant(head, stack, Enchantments.PROTECTION, 4);
				enchant(head, stack, Enchantments.UNBREAKING, 3);
				if (piece.getKey() == EquipmentSlot.FEET) {
					enchant(head, stack, Enchantments.FEATHER_FALLING, 4);
				}
			}
			head.setItemSlot(piece.getKey(), stack);
			head.setDropChance(piece.getKey(), 0.0F);
		}
	}

	private static void enchant(final Mob head, final ItemStack stack, final ResourceKey<Enchantment> key, final int level) {
		Holder<Enchantment> enchantment = head.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		stack.enchant(enchantment, level);
	}

	// The spells mod's component is read from the registry and filled through its own codec.
	@SuppressWarnings("unchecked")
	private static void imbue(final Mob head, final ItemStack axe) {
		if (!FabricLoader.getInstance().isModLoaded("mod-stella-spells")) {
			return;
		}
		DataComponentType<Object> type = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(IMBUED_COMPONENT);
		if (type == null || type.codec() == null) {
			return;
		}
		Identifier spell = OFFENSIVE_SPELLS.get(head.getRandom().nextInt(OFFENSIVE_SPELLS.size()));
		type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(spell.toString()))
			.ifSuccess(value -> {
				axe.set(type, value);
				McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=head_axe_imbued head={} spell={}", McStellaVillages.MOD_ID, head.getUUID(), spell);
			});
	}
}
