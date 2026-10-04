package com.stella.equips.refine;

import com.stella.equips.unique.Uniques;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Which special effects a refined piece has at a level. A weapon's follow its material's grade, a bow's are
 * its own, and armour's follow the piece at +7 and the material at +10. A shield has Parry alone, from +10.
 * Every level keeps the effects of the levels below it.
 */
public final class RefineSpecials {
	public static final int PIECE_LEVEL = Refine.KEEN_LEVEL;
	public static final int MATERIAL_LEVEL = Refine.SEARING_LEVEL;

	public enum Special {
		KEEN("Keen", PIECE_LEVEL),
		SPLINTER("Splinter", MATERIAL_LEVEL),
		SEARING("Searing", MATERIAL_LEVEL),
		FROSTBITE("Frostbite", MATERIAL_LEVEL),
		PIERCING("Piercing", MATERIAL_LEVEL),
		RENDING("Rending", Refine.RENDING_LEVEL),
		ASCENDED("Ascended", Refine.ASCENDED_LEVEL),
		VOLLEY("Volley", Refine.ASCENDED_LEVEL),
		CLARITY("Clarity", PIECE_LEVEL),
		FORTITUDE("Fortitude", PIECE_LEVEL),
		STRIDE("Stride", PIECE_LEVEL),
		FEATHERFALL("Featherfall", PIECE_LEVEL),
		EVASION("Evasion", MATERIAL_LEVEL),
		RIPOSTE("Riposte", MATERIAL_LEVEL),
		VIGOR("Vigor", MATERIAL_LEVEL),
		FORTUNE("Fortune", MATERIAL_LEVEL),
		BULWARK("Bulwark", MATERIAL_LEVEL),
		EMBERGUARD("Emberguard", MATERIAL_LEVEL),
		THORNED("Thorned", Refine.RENDING_LEVEL),
		BLESSED("Blessed", Refine.ASCENDED_LEVEL),
		PARRY("Parry", Refine.MILESTONE_FIRST);

		private final String title;
		private final int level;

		Special(final String title, final int level) {
			this.title = title;
			this.level = level;
		}

		public String title() {
			return this.title;
		}

		/** The refine level at which the effect starts. */
		public int level() {
			return this.level;
		}
	}

	private RefineSpecials() {
	}

	/** The effects a piece has at a level, lowest level first. Empty for a spellbook, a unique and anything unrefined. */
	public static List<Special> of(final ItemStack stack, final int level) {
		List<Special> all = new ArrayList<>();
		if (level <= 0 || stack.isEmpty() || Refine.isSpellbook(stack) || Uniques.def(stack) != null) {
			return all;
		}
		if (Refine.isArmor(stack)) {
			all.add(pieceSpecial(stack));
			Special material = materialSpecial(stack);
			if (material != null) {
				all.add(material);
			}
			all.add(Special.THORNED);
			all.add(Special.BLESSED);
		} else if (Refine.isRanged(stack)) {
			all.add(Special.KEEN);
			all.add(Special.PIERCING);
			all.add(Special.RENDING);
			all.add(Special.VOLLEY);
		} else if (Refine.isWeapon(stack)) {
			all.add(Special.KEEN);
			all.add(meleeSpecial(stack));
			all.add(Special.RENDING);
			all.add(Special.ASCENDED);
		} else if (Refine.isShield(stack)) {
			all.add(Special.PARRY);
		}
		all.removeIf(special -> special.level > level);
		return all;
	}

	/** Whether the piece has the effect at its own refine level. */
	public static boolean has(final ItemStack stack, final Special special) {
		return of(stack, Refine.level(stack)).contains(special);
	}

	private static Special meleeSpecial(final ItemStack stack) {
		return switch (Refine.grade(stack)) {
			case WEAPON_1 -> Special.SPLINTER;
			case WEAPON_3 -> Special.FROSTBITE;
			default -> Special.SEARING;
		};
	}

	private static Special pieceSpecial(final ItemStack stack) {
		return switch (Refine.armorSlotOf(stack)) {
			case HEAD -> Special.CLARITY;
			case CHEST -> Special.FORTITUDE;
			case LEGS -> Special.STRIDE;
			default -> Special.FEATHERFALL;
		};
	}

	private static Special materialSpecial(final ItemStack stack) {
		ResourceKey<EquipmentAsset> asset = armorAsset(stack);
		if (EquipmentAssets.LEATHER.equals(asset)) {
			return Special.EVASION;
		}
		if (EquipmentAssets.CHAINMAIL.equals(asset)) {
			return Special.RIPOSTE;
		}
		if (EquipmentAssets.IRON.equals(asset)) {
			return Special.VIGOR;
		}
		if (EquipmentAssets.GOLD.equals(asset)) {
			return Special.FORTUNE;
		}
		if (EquipmentAssets.DIAMOND.equals(asset)) {
			return Special.BULWARK;
		}
		if (EquipmentAssets.NETHERITE.equals(asset)) {
			return Special.EMBERGUARD;
		}
		return null;
	}

	private static ResourceKey<EquipmentAsset> armorAsset(final ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable == null ? null : equippable.assetId().orElse(null);
	}

	/** The tooltip line for an effect: its name, a colon, and what it does. */
	public static String describe(final Special special, final ItemStack stack) {
		double tier = RefineTier.factor(stack);
		return special.title + ": " + switch (special) {
			case KEEN -> percent(RefineEffects.chance(RefineEffects.KEEN_CHANCE, tier)) + " chance to hit for " + RefineEffects.KEEN_MULTIPLIER + " times the damage";
			case SPLINTER -> "hits bleed for " + percent(RefineEffects.SPLINTER_SHARE * tier * 100) + " of the hit each second, " + RefineEffects.SPLINTER_SECONDS + " s";
			case SEARING -> "hits set foes alight for " + seconds(RefineEffects.ticks(RefineEffects.SEARING_TICKS, stack));
			case FROSTBITE -> "hits give Slowness II for " + seconds(RefineEffects.ticks(RefineEffects.FROSTBITE_TICKS, stack));
			case PIERCING -> "arrows pass through one more mob";
			case RENDING -> "hits inflict Wither for " + seconds(RefineEffects.ticks(RefineEffects.RENDING_TICKS, stack));
			case ASCENDED -> "hits send a shockwave of " + percent(RefineEffects.SHOCKWAVE_SHARE * tier * 100) + " of the hit";
			case VOLLEY -> "each shot looses one more arrow at " + percent(RefineEffects.VOLLEY_SHARE * tier * 100) + " damage";
			case CLARITY -> "harmful effects on you last " + percent(RefineEffects.CLARITY_SHARE * tier * 100) + " less";
			case FORTITUDE -> "+" + percent(Refine.FORTITUDE_MULTIPLIER * tier * 100) + " max health";
			case STRIDE -> "+" + percent(Refine.STRIDE_MULTIPLIER * tier * 100) + " movement speed";
			case FEATHERFALL -> "-" + percent(RefineEffects.FEATHERFALL_SHARE * tier * 100) + " fall damage";
			case EVASION -> percent(RefineEffects.chance(RefineEffects.EVASION_CHANCE, tier)) + " chance to ignore a hit";
			case RIPOSTE -> percent(RefineEffects.chance(RefineEffects.RIPOSTE_CHANCE, tier)) + " chance to reflect " + percent(RefineEffects.RIPOSTE_SHARE * 100) + " of a blow";
			case VIGOR -> "+" + percent(Refine.VIGOR_MULTIPLIER * tier * 100) + " max health";
			case FORTUNE -> "+" + percent(RefineEffects.FORTUNE_SHARE * tier * 100) + " experience from kills";
			case BULWARK -> "-" + percent(RefineEffects.BULWARK_SHARE * tier * 100) + " damage taken";
			case EMBERGUARD -> "-" + percent(RefineEffects.EMBERGUARD_SHARE * tier * 100) + " fire damage";
			case THORNED -> "melee attackers take " + percent(RefineEffects.THORN_SHARE * tier * 100) + " of the blow";
			case BLESSED -> "restores " + percent(RefineEffects.BLESSING_SHARE * tier * 100) + " of max health every 5 s";
			case PARRY -> "a blocked melee hit sends " + percent(RefineEffects.parryShare(stack) * 100) + " of the blocked damage back";
		};
	}

	private static String seconds(final int ticks) {
		return trim(ticks / 20.0) + " s";
	}

	private static String trim(final double value) {
		double tenths = Math.round(value * 10) / 10.0;
		return tenths == Math.rint(tenths) ? String.valueOf((long) tenths) : String.valueOf(tenths);
	}

	private static String percent(final double value) {
		return trim(value) + "%";
	}
}
