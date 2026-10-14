package com.stella.spells;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The spell power of a mage staff held in the main hand. The staff comes from the equips mod and is found by the
 * item tag {@code mod-stella-equips-enchants:mage_staffs}, so this mod needs no class of that mod. A cast takes
 * the staff's damage share and its shorter cooldown, and each refine milestone grows both.
 */
public final class StaffPower {
	public static final TagKey<Item> MAGE_STAFFS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "mage_staffs"));
	/** The custom data key that the equips mod stores a refine level under. */
	public static final String REFINE_KEY = "stella_refine";
	/** The refine levels at which the staff grows. */
	public static final int[] MILESTONES = {7, 10, 12, 15};
	/** The extra spell damage of a staff, and what each milestone adds. */
	public static final double POWER_BASE = 0.25;
	public static final double POWER_PER_MILESTONE = 0.10;
	/** The share of the cooldown a staff removes, and what each milestone adds. */
	public static final double COOLDOWN_BASE = 0.20;
	public static final double COOLDOWN_PER_MILESTONE = 0.05;
	/** The staff compound of the RPG mod's staff profile, in custom data. This mod reads the Doublecast effect from it. */
	public static final String PROFILE_KEY = "stella_staff";
	public static final String DOUBLECAST_EFFECT = "doublecast";
	/** The shortest cooldown a staff leaves, as in the rest of the spell pipeline. */
	private static final int COOLDOWN_FLOOR = 20;

	private StaffPower() {
	}

	public static boolean isStaff(final ItemStack stack) {
		return !stack.isEmpty() && stack.is(MAGE_STAFFS);
	}

	/** The refine level of a stack, or 0 when it carries none. */
	public static int refineLevel(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : Math.max(0, data.copyTag().getIntOr(REFINE_KEY, 0));
	}

	/**
	 * The chance that a cast with the staff in the main hand lands twice, for every spell. A staff names its effect,
	 * a base percent and a step percent per refine milestone. Zero for another staff or no staff.
	 */
	public static double doublecastChance(final Player caster) {
		CustomData data = caster.getItemBySlot(EquipmentSlot.MAINHAND).get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return 0.0;
		}
		var tag = data.copyTag();
		var profile = tag.getCompoundOrEmpty(PROFILE_KEY);
		if (!DOUBLECAST_EFFECT.equals(profile.getStringOr("effect", ""))) {
			return 0.0;
		}
		int percent = profile.getIntOr("base", 0) + profile.getIntOr("step", 0) * milestones(Math.max(0, tag.getIntOr(REFINE_KEY, 0)));
		return Math.min(1.0, percent / 100.0);
	}

	/** How many milestones a refine level has reached. */
	public static int milestones(final int level) {
		int reached = 0;
		for (int milestone : MILESTONES) {
			if (level >= milestone) {
				reached++;
			}
		}
		return reached;
	}

	/** The spell damage multiplier of a staff stack: 1.25 at +0, and 0.10 more at each milestone. */
	public static double powerMultiplier(final ItemStack staff) {
		return 1.0 + POWER_BASE + POWER_PER_MILESTONE * milestones(refineLevel(staff));
	}

	/** The share of the cooldown that a staff stack removes: 0.20 at +0, and 0.05 more at each milestone. */
	public static double cooldownCut(final ItemStack staff) {
		return COOLDOWN_BASE + COOLDOWN_PER_MILESTONE * milestones(refineLevel(staff));
	}

	/** A cast's modifiers once the staff in the caster's main hand has had its say; the same object when there is none. */
	static SpellModifiers.CastModifiers grow(final Player caster, final String spellId, final SpellModifiers.CastModifiers base) {
		ItemStack staff = caster.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!isStaff(staff)) {
			return base;
		}
		int cooldown = SpellbookCatalog.FIXED_COOLDOWN.contains(spellId) ? base.cooldownTicks() : shorten(base.cooldownTicks(), cooldownCut(staff));
		return new SpellModifiers.CastModifiers(
			base.powerMultiplier() * powerMultiplier(staff), base.radiusMultiplier(), base.durationMultiplier(),
			base.defensiveDurationMultiplier(), base.targetRangeBlocks(), cooldown, base.awakened(), base.refineLevel()
		);
	}

	static int shorten(final int ticks, final double cut) {
		int shortened = (int) Math.round(ticks * (1.0 - cut));
		return Math.min(ticks, Math.max(COOLDOWN_FLOOR, shortened));
	}
}
