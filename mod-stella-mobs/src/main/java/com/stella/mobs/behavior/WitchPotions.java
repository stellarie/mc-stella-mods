package com.stella.mobs.behavior;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * The one witch potion list. The throws and the death drop both read it, so a potion cannot exist
 * in one and be missing from the other.
 */
public final class WitchPotions {
	/** One carried potion: its name, whether it is offensive, its effect, and its duration. */
	public record Carried(String name, boolean offensive, Holder<MobEffect> effect, int durationTicks) {
	}

	public static final List<Carried> CARRIED = List.of(
		new Carried("invisibility", false, MobEffects.INVISIBILITY, 400),
		new Carried("healing", false, MobEffects.INSTANT_HEALTH, 1),
		new Carried("harming", true, MobEffects.INSTANT_DAMAGE, 1),
		new Carried("poison", true, MobEffects.POISON, 900),
		new Carried("slowness", true, MobEffects.SLOWNESS, 1800),
		new Carried("blindness", true, MobEffects.BLINDNESS, 300),
		new Carried("levitation", true, MobEffects.LEVITATION, 300)
	);

	private WitchPotions() {
	}

	/** The offensive view of the one list, in list order. */
	public static List<Carried> offensive() {
		return CARRIED.stream().filter(Carried::offensive).toList();
	}

	public static Carried invisibility() {
		return CARRIED.get(0);
	}

	public static Carried healing() {
		return CARRIED.get(1);
	}

	/** The harm splash. Undead invert it, so this is the entry that heals an undead ally. */
	public static Carried harming() {
		return CARRIED.get(2);
	}

	/** A splash potion carrying one custom effect, on an empty potion. */
	public static ItemStack stack(final Carried carried) {
		MobEffectInstance effect = new MobEffectInstance(carried.effect(), carried.durationTicks(), 0);
		ItemStack stack = new ItemStack(Items.SPLASH_POTION);
		stack.set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY.withEffectAdded(effect));
		return stack;
	}

	/**
	 * True for a mod throw and false for vanilla's own witch throw. The mod puts a custom effect on
	 * an empty potion; vanilla always carries a real potion.
	 */
	public static boolean isModThrow(final ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.potion().isEmpty() && contents.hasEffects();
	}
}
