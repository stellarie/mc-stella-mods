package com.stella.equips.refine;

import com.stella.equips.StellaEquips;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Repairable;

/**
 * Refining, after Ragnarok Online Renewal: a weapon, a piece of armour or a spellbook climbs from +0 to +15,
 * one step at a time, each step a gamble whose odds fall with the level and with the gear's grade. The level
 * lives in the stack's own custom data and its bonus rides an ordinary attribute modifier, so it is shown like
 * an enchantment without being one, and never takes an enchantment's place. A spellbook gets no modifier: the
 * level is all it keeps, for mod-stella-rpg to read.
 */
public final class Refine {
	public static final int MAX_LEVEL = 15;
	public static final String LEVEL_KEY = "stella_refine";
	public static final double DAMAGE_PER_LEVEL = 0.75;
	public static final double ARMOR_PER_LEVEL = 0.5;
	public static final int KEEN_LEVEL = 7;
	public static final int SEARING_LEVEL = 10;
	public static final int RENDING_LEVEL = 12;
	public static final int ASCENDED_LEVEL = 15;
	public static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refine_damage");
	public static final Identifier ARMOR_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refine_armor");
	/** The id vigor had before each piece got its own; a piece refined then still carries it until its next refine. */
	public static final Identifier VIGOR_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refine_vigor");
	public static final Identifier FORTITUDE_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refine_fortitude");
	public static final Identifier STRIDE_ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refine_stride");
	/** +10 iron armour: this share of base max health more for each such piece worn. */
	public static final double VIGOR_MULTIPLIER = 0.02;
	/** +7 chestplate: the same kind of bonus. */
	public static final double FORTITUDE_MULTIPLIER = 0.03;
	/** +7 leggings: this share of base movement speed more. */
	public static final double STRIDE_MULTIPLIER = 0.05;
	/** What a spellbook is refined with. */
	public static final Item SPELLBOOK_MATERIAL = Items.AMETHYST_SHARD;
	private static final List<String> ARMOR_SLOT_NAMES = List.of("head", "chest", "legs", "feet");
	private static final String SPELLBOOK_CLASS = "com.stella.spells.SpellbookItem";
	private static final String LORE_PREFIX = "Refine +";

	/** The gear's grade: weapons by material, as Ragnarok's weapon levels, and armour on its own table. */
	public enum Grade {
		WEAPON_1(100, 100, 100, 100, 100, 100, 100, 60, 40, 19, 20, 20, 16, 16, 15),
		WEAPON_2(100, 100, 100, 100, 100, 100, 60, 40, 20, 19, 20, 20, 16, 16, 15),
		WEAPON_3(100, 100, 100, 100, 100, 60, 50, 20, 20, 19, 20, 20, 16, 16, 15),
		WEAPON_4(100, 100, 100, 100, 60, 40, 40, 20, 20, 9, 20, 20, 16, 16, 15),
		ARMOR(100, 100, 100, 100, 60, 40, 40, 20, 20, 9, 20, 20, 16, 16, 15);

		private final int[] percent;

		Grade(final int... percent) {
			this.percent = percent;
		}

		/** The chance in percent that refining to this level succeeds. */
		public int chanceTo(final int level) {
			return this.percent[level - 1];
		}
	}

	private Refine() {
	}

	public static boolean isArmor(final ItemStack stack) {
		return stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR);
	}

	public static boolean isRanged(final ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
	}

	public static boolean isWeapon(final ItemStack stack) {
		return isRanged(stack) || stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID));
	}

	/**
	 * A spellbook of mod-stella-spells, found by its class name so this mod needs no dependency on that one.
	 * Its scrolls are told apart by stack size: a book holds one, a scroll stacks.
	 */
	public static boolean isSpellbook(final ItemStack stack) {
		return !stack.isEmpty() && stack.getMaxStackSize() == 1 && stack.getItem().getClass().getName().equals(SPELLBOOK_CLASS);
	}

	public static boolean canRefine(final ItemStack stack) {
		return !stack.isEmpty() && (isSpellbook(stack) || stack.isDamageableItem() && (isWeapon(stack) || isArmor(stack)));
	}

	public static int level(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(LEVEL_KEY, 0);
	}

	/** The grade by what the piece is mended with: wood, stone, gold and copper are grade 1, iron 2, diamond 3, netherite 4. */
	public static Grade grade(final ItemStack stack) {
		if (isArmor(stack)) {
			return Grade.ARMOR;
		}
		if (isSpellbook(stack)) {
			return Grade.WEAPON_3;
		}
		Item material = material(stack);
		if (material == Items.NETHERITE_INGOT) {
			return Grade.WEAPON_4;
		}
		if (material == Items.DIAMOND || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) {
			return Grade.WEAPON_3;
		}
		if (material == Items.IRON_INGOT) {
			return Grade.WEAPON_2;
		}
		return Grade.WEAPON_1;
	}

	/** The piece's own repair material, the "ingot" a refine eats; iron for anything that has none. */
	public static Item material(final ItemStack stack) {
		if (isSpellbook(stack)) {
			return SPELLBOOK_MATERIAL;
		}
		Repairable repairable = stack.get(DataComponents.REPAIRABLE);
		if (repairable != null) {
			for (Holder<Item> item : repairable.items()) {
				return item.value();
			}
		}
		return Items.IRON_INGOT;
	}

	/** Whether a stack counts as the piece's material: any item its repair set holds. */
	public static boolean isMaterial(final ItemStack piece, final ItemStack candidate) {
		if (isSpellbook(piece)) {
			return candidate.is(SPELLBOOK_MATERIAL);
		}
		Repairable repairable = piece.get(DataComponents.REPAIRABLE);
		return repairable != null ? repairable.isValidRepairItem(candidate) : candidate.is(Items.IRON_INGOT);
	}

	/** How much material the next step costs: one more for every three levels the piece already has. */
	public static int cost(final int currentLevel) {
		return 1 + currentLevel / 3;
	}

	/** Writes a level and brings the piece's bonus, tooltip line and glint in line with it. */
	public static void setLevel(final ItemStack stack, final int level) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(LEVEL_KEY, level));
		if (!isSpellbook(stack)) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, withBonuses(stack, level));
		}
		stack.set(DataComponents.LORE, withRefineLines(stack, level));
		if (level > 0) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		} else {
			stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		}
	}

	private static ItemAttributeModifiers withBonuses(final ItemStack stack, final int level) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			Identifier id = entry.modifier().id();
			if (!id.equals(DAMAGE_ID) && !id.equals(ARMOR_ID) && !id.equals(VIGOR_ID) && !id.equals(FORTITUDE_ID) && !id.equals(STRIDE_ID)
				&& !ARMOR_SLOT_NAMES.stream().anyMatch(name -> id.equals(vigorId(name)))) {
				builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
			}
		}
		if (level > 0) {
			if (isArmor(stack)) {
				EquipmentSlotGroup slot = armorSlot(stack);
				add(builder, Attributes.ARMOR, ARMOR_ID, level * ARMOR_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE, slot);
				List<RefineSpecials.Special> specials = RefineSpecials.of(stack, level);
				if (specials.contains(RefineSpecials.Special.FORTITUDE)) {
					add(builder, Attributes.MAX_HEALTH, FORTITUDE_ID, FORTITUDE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
				}
				if (specials.contains(RefineSpecials.Special.STRIDE)) {
					add(builder, Attributes.MOVEMENT_SPEED, STRIDE_ID, STRIDE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
				}
				if (specials.contains(RefineSpecials.Special.VIGOR)) {
					add(builder, Attributes.MAX_HEALTH, vigorId(armorSlotOf(stack).getName()), VIGOR_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
				}
			} else if (!isRanged(stack)) {
				add(builder, Attributes.ATTACK_DAMAGE, DAMAGE_ID, level * DAMAGE_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.MAINHAND);
			}
		}
		return builder.build();
	}

	private static void add(final ItemAttributeModifiers.Builder builder, final Holder<Attribute> attribute, final Identifier id, final double amount,
		final AttributeModifier.Operation operation, final EquipmentSlotGroup slot) {
		builder.add(attribute, new AttributeModifier(id, amount, operation), slot);
	}

	private static EquipmentSlotGroup armorSlot(final ItemStack stack) {
		return switch (armorSlotOf(stack)) {
			case HEAD -> EquipmentSlotGroup.HEAD;
			case CHEST -> EquipmentSlotGroup.CHEST;
			case LEGS -> EquipmentSlotGroup.LEGS;
			default -> EquipmentSlotGroup.FEET;
		};
	}

	/** The slot a piece of armour is worn in. */
	public static EquipmentSlot armorSlotOf(final ItemStack stack) {
		if (stack.is(ItemTags.HEAD_ARMOR)) {
			return EquipmentSlot.HEAD;
		}
		if (stack.is(ItemTags.CHEST_ARMOR)) {
			return EquipmentSlot.CHEST;
		}
		if (stack.is(ItemTags.LEG_ARMOR)) {
			return EquipmentSlot.LEGS;
		}
		return EquipmentSlot.FEET;
	}

	/** The id of the vigor modifier of the armour worn in a slot. */
	public static Identifier vigorId(final EquipmentSlot slot) {
		return vigorId(slot.getName());
	}

	private static Identifier vigorId(final String slot) {
		return Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refine_vigor_" + slot);
	}

	/** The special effects a level brings, by name, for the tooltip. */
	public static List<String> effects(final ItemStack stack, final int level) {
		List<String> effects = new ArrayList<>();
		for (RefineSpecials.Special special : RefineSpecials.of(stack, level)) {
			effects.add(RefineSpecials.describe(special, stack));
		}
		return effects;
	}

	// The refine line sits first, as an enchantment would, and the effect lines follow it.
	private static ItemLore withRefineLines(final ItemStack stack, final int level) {
		List<Component> kept = new ArrayList<>();
		for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			String text = line.getString();
			if (!text.startsWith(LORE_PREFIX) && Arrays.stream(RefineSpecials.Special.values()).noneMatch(special -> text.startsWith(special.title() + ":"))) {
				kept.add(line);
			}
		}
		List<Component> lines = new ArrayList<>();
		if (level > 0) {
			lines.add(Component.literal(LORE_PREFIX + level).withStyle(style -> style.withColor(ChatFormatting.AQUA).withItalic(false)));
			for (String effect : effects(stack, level)) {
				lines.add(Component.literal(effect).withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
			}
		}
		lines.addAll(kept);
		return new ItemLore(lines);
	}
}
