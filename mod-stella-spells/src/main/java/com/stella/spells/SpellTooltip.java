package com.stella.spells;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A spell tooltip's effect and cooldown lines as the viewer's own cast would play them now: the book's
 * enchantments, the RPG mod's MATK and health scale, and the tower's growth all count. The math sits here, in
 * the main source set, and it touches no client class, so a server test proves it against a real cast.
 * Every number comes from {@link SpellNumbers}, the record the spell effect reads.
 */
public final class SpellTooltip {
	private static final double PERCENT = 100.0;
	private static final double TICKS_PER_SECOND = 20.0;
	private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private SpellTooltip() {
	}

	/** The translation key of a spell's live effect line, which takes the arguments {@link #arguments} builds. */
	public static String liveEffectKey(final SpellDefinition definition) {
		return "item." + definition.id().getNamespace() + "." + definition.id().getPath() + ".effect_live";
	}

	/** The translation key of the static effect line that the live one replaces. */
	static String staticEffectKey(final SpellDefinition definition) {
		return "item." + definition.id().getNamespace() + "." + definition.id().getPath() + ".effect";
	}

	static String cooldownKey() {
		return "spellbook." + Spellbooks.MOD_ID + ".cooldown";
	}

	/**
	 * The live lines for this stack in tooltip order: the effect line, then a book's cooldown line. The list is
	 * empty when there is no viewer or the stack is no spell, and then the static lore stays as it is.
	 */
	public static List<Component> lines(final @Nullable Player viewer, final ItemStack stack) {
		return List.copyOf(live(viewer, stack).values());
	}

	/** Swaps the static effect and cooldown lines in a tooltip for the live ones; touches nothing else. */
	public static void apply(final @Nullable Player viewer, final ItemStack stack, final List<Component> tooltip) {
		Map<String, Component> live = live(viewer, stack);
		if (live.isEmpty()) {
			return;
		}
		for (int i = 0; i < tooltip.size(); i++) {
			if (tooltip.get(i).getContents() instanceof TranslatableContents contents) {
				Component replacement = live.get(contents.getKey());
				if (replacement != null) {
					tooltip.set(i, replacement);
				}
			}
		}
	}

	/** The cast modifiers a cast from this stack by the viewer would use, less the tower's cooldown, which a cast spends. */
	static SpellModifiers.CastModifiers modifiers(final Player viewer, final ItemStack stack, final SpellDefinition definition) {
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(
			stack, viewer.getItemBySlot(EquipmentSlot.HEAD), viewer.getItemBySlot(EquipmentSlot.CHEST)
		);
		modifiers = TowerBridge.grow(stack, definition.id().getPath(), modifiers);
		return RpgBridge.grow(viewer, modifiers);
	}

	private static Map<String, Component> live(final @Nullable Player viewer, final ItemStack stack) {
		if (viewer == null) {
			return Map.of();
		}
		boolean book = stack.getItem() instanceof SpellbookItem;
		SpellDefinition definition = book
			? ((SpellbookItem) stack.getItem()).definition()
			: stack.getItem() instanceof SpellScrollItem scroll ? scroll.definition() : null;
		if (definition == null) {
			return Map.of();
		}
		SpellModifiers.CastModifiers modifiers = modifiers(viewer, stack, definition);
		Map<String, Component> live = new LinkedHashMap<>();
		SpellNumbers numbers = SpellNumbers.find(definition.id());
		if (numbers != null && !numbers.isEmpty()) {
			live.put(staticEffectKey(definition), Spellbooks.line(
				Component.translatable(liveEffectKey(definition), (Object[]) arguments(viewer, numbers, modifiers)), ChatFormatting.AQUA
			));
		}
		if (book) {
			live.put(cooldownKey(), Spellbooks.line(
				Component.translatable(cooldownKey(), seconds(modifiers.cooldownTicks())), ChatFormatting.DARK_GRAY
			));
		}
		return live;
	}

	/**
	 * The live effect line's arguments, each already formatted. A line picks the ones it needs by position:
	 * 1 damage, 2 heal, 3 ally heal, 4 duration, 5 extra duration or cap, 6 radius, 7 targets, 8 amplifier,
	 * 9 extra amplifier. Health and damage are in displayed units, the vanilla number times the health scale.
	 */
	static String[] arguments(final Player viewer, final SpellNumbers numbers, final SpellModifiers.CastModifiers modifiers) {
		double scale = SummonSkeleton.healthScale();
		double power = modifiers.powerMultiplier();
		boolean capped = numbers.capTicks() > 0;
		return new String[] {
			damage(viewer, numbers.damage(), power, scale),
			heal(viewer, numbers.heal(), power, scale),
			heal(viewer, numbers.allyHeal(), power, scale),
			numbers.duration().ticks() > 0 ? seconds(numbers.durationTicks(modifiers)) : "0",
			capped ? seconds(numbers.duration().scaled(numbers.capTicks(), modifiers))
				: numbers.extra().ticks() > 0 ? seconds(numbers.extraTicks(modifiers)) : "0",
			tenths(numbers.radius(modifiers)),
			Integer.toString(numbers.targets()),
			roman(numbers.amplifier() + 1),
			roman(numbers.extraAmplifier() + 1)
		};
	}

	// A range from the caster's lowest to highest MATK roll; the flat number times the power when MATK gives none.
	private static String damage(final Player viewer, final float flat, final double power, final double scale) {
		if (flat <= 0.0f) {
			return "0";
		}
		double percent = flat * RpgBridge.SKILL_PERCENT_PER_FLAT;
		double min = RpgBridge.matkMin(viewer);
		double max = RpgBridge.matkMax(viewer);
		if (!(min > 0.0) || !(max > 0.0) || !Double.isFinite(min) || !Double.isFinite(max)) {
			return number(flat * power * scale);
		}
		String low = number(shown(min, percent, power, scale));
		String high = number(shown(max, percent, power, scale));
		return low.equals(high) ? low : low + "-" + high;
	}

	// One roll as the cast deals it in vanilla units, then in displayed units.
	private static double shown(final double matk, final double percent, final double power, final double scale) {
		double vanilla = matk * percent / PERCENT / scale * power;
		return vanilla * scale;
	}

	private static String heal(final Player viewer, final float flat, final double power, final double scale) {
		return flat <= 0.0f ? "0" : number(RpgBridge.heal(viewer, flat, power) * scale);
	}

	private static String seconds(final int ticks) {
		return tenths(ticks / TICKS_PER_SECOND);
	}

	// Whole numbers from ten up; below that one decimal, dropped when it is zero.
	static String number(final double value) {
		return value >= 10.0 ? Long.toString(Math.round(value)) : tenths(value);
	}

	// One decimal, dropped when it is zero.
	static String tenths(final double value) {
		double rounded = Math.round(value * 10.0) / 10.0;
		return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : Double.toString(rounded);
	}

	private static String roman(final int level) {
		return level > 0 && level < ROMAN.length ? ROMAN[level] : Integer.toString(level);
	}
}
