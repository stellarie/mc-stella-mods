package com.stella.end;

import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/** The ultimate gear: what it is made of, what it gives, and what wearing all of it grants. */
public final class DraconicGear {
	public static final String DRAGON_SCALE = "dragon_scale";
	public static final String BLADE = "draconic_blade";
	public static final String BOW = "draconic_bow";
	public static final String STAFF = "draconic_staff";
	public static final String HELMET = "draconic_helmet";
	public static final String CHESTPLATE = "draconic_chestplate";
	public static final String LEGGINGS = "draconic_leggings";
	public static final String BOOTS = "draconic_boots";
	public static final List<String> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
	public static final List<String> WEAPON_IDS = List.of(BLADE, BOW, STAFF);
	public static final List<String> ALL = List.of(BLADE, BOW, STAFF, HELMET, CHESTPLATE, LEGGINGS, BOOTS);

	public static final double BLADE_DAMAGE = 16.0;
	public static final double BLADE_SPEED = 1.6;
	public static final double END_BONUS = 0.25;
	public static final double ARROW_FACTOR = 2.0;
	public static final double SPELL_POWER_BONUS = 0.6;
	/** The magic attack of the strongest staff of the equips mod, the base the draconic staff adds its bonus to. */
	public static final int STAFF_BASE_MATK = 50;
	public static final int[] ARMOR_POINTS = {4, 9, 7, 4};
	public static final double ARMOR_TOUGHNESS = 5.0;
	public static final double ARMOR_KNOCKBACK = 0.15;
	public static final double ARMOR_HEALTH = 4.0;
	/** What each piece costs: one dragon egg for a weapon, and scales for armour. */
	public static final Map<String, Integer> COST = Map.of(
		BLADE, 1, BOW, 1, STAFF, 1, HELMET, 5, CHESTPLATE, 8, LEGGINGS, 7, BOOTS, 4
	);

	private DraconicGear() {
	}

	public static Item item(final String id) {
		return BuiltInRegistries.ITEM.getValue(StellaEnd.id(id));
	}

	/** Whether a creature wears all four pieces. */
	public static boolean wearsFullSet(final LivingEntity wearer) {
		for (String piece : ARMOR) {
			if (!wearer.getItemBySlot(slotOf(piece)).is(item(piece))) {
				return false;
			}
		}
		return true;
	}

	public static EquipmentSlot slotOf(final String armor) {
		return switch (armor) {
			case HELMET -> EquipmentSlot.HEAD;
			case CHESTPLATE -> EquipmentSlot.CHEST;
			case LEGGINGS -> EquipmentSlot.LEGS;
			default -> EquipmentSlot.FEET;
		};
	}
}
