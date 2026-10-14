package com.stella.rpg;

import com.stella.rpg.roll.Affixes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The MATK of a staff held in the main hand. A staff carries a {@value #KEY} compound in its custom data, so any
 * mod can define one without a link to this mod: the base MATK in K units, and one refine effect. The effect starts
 * at a base percent and grows by a step percent at each refine milestone. MATK grows with every refine level and
 * jumps at each milestone. Effects are {@value #CDR}, {@value #CRIT}, {@value #PEN} and {@value #DOUBLECAST}.
 */
public final class StaffMatk {
	public static final String KEY = "stella_staff";
	public static final String MATK = "matk";
	public static final String EFFECT = "effect";
	public static final String BASE = "base";
	public static final String STEP = "step";
	public static final String CDR = "cdr";
	public static final String CRIT = "crit";
	public static final String PEN = "pen";
	public static final String DOUBLECAST = "doublecast";
	public static final int[] MILESTONES = {7, 10, 12, 15};
	/** The share of the base MATK that each refine level adds. */
	public static final double PER_LEVEL = 0.10;
	/** The share of the base MATK that each refine milestone adds. */
	public static final double PER_MILESTONE = 0.25;

	private StaffMatk() {
	}

	/** The custom data of a staff item. A mod puts it in the item's default components. */
	public static CustomData profile(final int matk, final String effect, final int basePercent, final int stepPercent) {
		CompoundTag staff = new CompoundTag();
		staff.putInt(MATK, matk);
		staff.putString(EFFECT, effect);
		staff.putInt(BASE, basePercent);
		staff.putInt(STEP, stepPercent);
		CompoundTag root = new CompoundTag();
		root.put(KEY, staff);
		return CustomData.of(root);
	}

	public static boolean isStaff(final ItemStack stack) {
		return !stack.isEmpty() && compound(stack) != null;
	}

	public static int milestones(final int level) {
		int reached = 0;
		for (int milestone : MILESTONES) {
			if (level >= milestone) {
				reached++;
			}
		}
		return reached;
	}

	/** The MATK of a staff stack in K units at its refine level. Zero for a stack that is no staff. */
	public static double matk(final ItemStack stack) {
		CompoundTag staff = compound(stack);
		if (staff == null) {
			return 0.0;
		}
		int level = Affixes.refineLevel(stack);
		return staff.getIntOr(MATK, 0) * (1.0 + PER_LEVEL * level + PER_MILESTONE * milestones(level));
	}

	/** The fraction an effect gives at the staff's refine level. Zero for another effect or no staff. */
	public static double effect(final ItemStack stack, final String effect) {
		CompoundTag staff = compound(stack);
		if (staff == null || !effect.equals(staff.getStringOr(EFFECT, ""))) {
			return 0.0;
		}
		int percent = staff.getIntOr(BASE, 0) + staff.getIntOr(STEP, 0) * milestones(Affixes.refineLevel(stack));
		return percent / 100.0;
	}

	public static double heldMatk(final LivingEntity entity) {
		return matk(entity.getItemBySlot(EquipmentSlot.MAINHAND));
	}

	public static double heldEffect(final LivingEntity entity, final String effect) {
		return effect(entity.getItemBySlot(EquipmentSlot.MAINHAND), effect);
	}

	private static CompoundTag compound(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag staff = data.copyTag().getCompoundOrEmpty(KEY);
		return staff.isEmpty() ? null : staff;
	}
}
