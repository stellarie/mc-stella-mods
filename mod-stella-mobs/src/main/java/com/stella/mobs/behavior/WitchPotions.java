package com.stella.mobs.behavior;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * The one witch potion list. The throws and the death drop both read it, so a potion cannot exist
 * in one and be missing from the other.
 */
public final class WitchPotions {
	/** One carried potion: its name, whether it is offensive, its effect level, effect, and duration. */
	public record Carried(String name, boolean offensive, int level, Holder<MobEffect> effect, int durationTicks) {
	}

	/** W12: the vanilla strong poison value, which is what a lingering cloud carries. */
	public static final int LINGER_POISON_TICKS = 432;
	public static final int LINGER_POISON_LEVEL = 1;
	// Vanilla names Speed potions after Swiftness, so its lang key uses that word.
	private static final Map<String, String> VANILLA_WORD = Map.of("speed", "swiftness");

	public static final List<Carried> CARRIED = List.of(
		new Carried("invisibility", false, 0, MobEffects.INVISIBILITY, 400),
		new Carried("healing", false, 0, MobEffects.INSTANT_HEALTH, 1),
		new Carried("harming", true, 0, MobEffects.INSTANT_DAMAGE, 1),
		new Carried("poison", true, 0, MobEffects.POISON, 900),
		new Carried("slowness", true, 0, MobEffects.SLOWNESS, 1800),
		new Carried("blindness", true, 0, MobEffects.BLINDNESS, 300),
		new Carried("levitation", true, 0, MobEffects.LEVITATION, 300),
		// W11: the two strong potions, at the vanilla strong-potion level and duration.
		new Carried("speed", false, 1, MobEffects.SPEED, 1800),
		new Carried("strength", false, 1, MobEffects.STRENGTH, 1800)
	);

	private WitchPotions() {
	}

	/** The offensive view of the one list, in list order. */
	public static List<Carried> offensive() {
		return CARRIED.stream().filter(Carried::offensive).toList();
	}

	/** W11: the three ally buffs, in the order the support goal cycles them. */
	public static List<Carried> buffs() {
		return List.of(invisibility(), swiftness(), strength());
	}

	public static Carried invisibility() {
		return find("invisibility");
	}

	public static Carried healing() {
		return find("healing");
	}

	/** The harm splash. Undead invert it, so this is the entry that heals an undead ally. */
	public static Carried harming() {
		return find("harming");
	}

	public static Carried swiftness() {
		return find("speed");
	}

	public static Carried strength() {
		return find("strength");
	}

	/** A splash potion carrying one custom effect, at the level the entry names. */
	public static ItemStack stack(final Carried carried) {
		return named(potion(Items.SPLASH_POTION, carried.effect(), carried.level(), carried.durationTicks()), carried.name());
	}

	/** W12: a lingering potion with the elite's poison payload. Vanilla sets every cloud number on hit. */
	public static ItemStack lingeringPoison() {
		return named(potion(Items.LINGERING_POTION, MobEffects.POISON, LINGER_POISON_LEVEL, LINGER_POISON_TICKS), "poison");
	}

	/** Names a mod potion after its effect; the English fallback covers effects vanilla has no potion for. */
	private static ItemStack named(final ItemStack stack, final String name) {
		String word = VANILLA_WORD.getOrDefault(name, name);
		boolean lingering = stack.is(Items.LINGERING_POTION);
		String key = "item.minecraft." + (lingering ? "lingering_potion" : "splash_potion") + ".effect." + word;
		String fallback = (lingering ? "Lingering Potion of " : "Splash Potion of ")
			+ word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatableWithFallback(key, fallback).withStyle(style -> style.withItalic(false)));
		return stack;
	}

	private static ItemStack potion(
		final Item item, final Holder<MobEffect> effect, final int level, final int durationTicks
	) {
		MobEffectInstance instance = new MobEffectInstance(effect, durationTicks, level);
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY.withEffectAdded(instance));
		return stack;
	}

	private static Carried find(final String name) {
		return CARRIED.stream()
			.filter(carried -> carried.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no carried potion named " + name));
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
