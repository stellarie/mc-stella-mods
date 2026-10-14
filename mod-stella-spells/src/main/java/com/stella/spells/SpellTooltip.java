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
	private static final double SECONDS_PER_MINUTE = 60.0;
	private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private SpellTooltip() {
	}

	/** The translation key of a spell's live Desc text, which takes the arguments {@link #arguments} builds. */
	public static String liveEffectKey(final SpellDefinition definition) {
		return "item." + definition.id().getNamespace() + "." + definition.id().getPath() + ".desc_live";
	}

	/** The translation key of the static Desc text that the live one replaces. */
	static String staticEffectKey(final SpellDefinition definition) {
		return "item." + definition.id().getNamespace() + "." + definition.id().getPath() + ".desc";
	}

	/** The key that names a tooltip line: its own translation key, or the one in its value when a label comes first. */
	static String lineKey(final Component line) {
		if (line.getContents() instanceof TranslatableContents contents) {
			return contents.getKey();
		}
		for (Component part : line.getSiblings()) {
			if (part.getContents() instanceof TranslatableContents contents) {
				return contents.getKey();
			}
		}
		return "";
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

	/** A refined book's lines: its level, its bonus and each unlocked milestone. An unrefined book or a scroll shows none. */
	public static List<Component> refineLines(final ItemStack stack) {
		List<Component> lines = new java.util.ArrayList<>();
		for (Component line : BookRefine.tooltip(stack)) {
			DescriptionLines.add(lines, line);
		}
		return lines;
	}

	/** A book's awakening lines: its cast count and what is left, or Awakened. A scroll shows none. */
	public static List<Component> awakeningLines(final ItemStack stack) {
		if (!(stack.getItem() instanceof SpellbookItem)) {
			return List.of();
		}
		if (Awakening.isAwakened(stack)) {
			return List.of(Spellbooks.line(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".awakened_state"), ChatFormatting.GOLD));
		}
		return List.of(
			Spellbooks.line(Component.translatable(
				"spellbook." + Spellbooks.MOD_ID + ".awakening", Awakening.count(stack), Awakening.CASTS_TO_AWAKEN
			), ChatFormatting.GRAY),
			Spellbooks.line(Component.translatable(
				"spellbook." + Spellbooks.MOD_ID + ".awakens_in", Awakening.castsLeft(stack)
			), ChatFormatting.GRAY)
		);
	}

	/**
	 * Everything the client adds to an item tooltip, in order: the live lines, the refine lines, the
	 * awakening lines and the imbue lines. The client callback calls this one method, so a server test sees the same tooltip.
	 */
	public static void decorate(final @Nullable Player viewer, final ItemStack stack, final List<Component> tooltip) {
		apply(viewer, stack, tooltip);
		tooltip.addAll(refineLines(stack));
		tooltip.addAll(awakeningLines(stack));
		tooltip.addAll(SpellImbue.tooltipLines(stack));
		DescriptionLines.wrapMarked(tooltip);
	}

	/** Swaps the static effect and cooldown lines in a tooltip for the live ones; touches nothing else. */
	public static void apply(final @Nullable Player viewer, final ItemStack stack, final List<Component> tooltip) {
		// The equips station once wrote a Refine line into a book lore; the refine lines replace it.
		if (stack.getItem() instanceof SpellbookItem) {
			tooltip.removeIf(BookRefine::isOldRefineLine);
		}
		Map<String, Component> live = live(viewer, stack);
		if (live.isEmpty()) {
			return;
		}
		for (int i = 0; i < tooltip.size(); i++) {
			Component replacement = live.get(lineKey(tooltip.get(i)));
			if (replacement != null) {
				tooltip.set(i, replacement);
			}
		}
	}

	/** The cast modifiers a cast from this stack by the viewer would use, less the tower's cooldown, which a cast spends. */
	static SpellModifiers.CastModifiers modifiers(final Player viewer, final ItemStack stack, final SpellDefinition definition) {
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(
			stack, viewer.getItemBySlot(EquipmentSlot.HEAD), viewer.getItemBySlot(EquipmentSlot.CHEST)
		);
		modifiers = TowerBridge.grow(stack, definition.id().getPath(), modifiers);
		return RpgBridge.grow(viewer, definition.id().getPath(), modifiers, definition.school());
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
			live.put(staticEffectKey(definition), DescriptionLines.desc(
				Component.translatable(liveEffectKey(definition), (Object[]) arguments(viewer, definition.school(), numbers, modifiers, definition.id().getPath())).withStyle(ChatFormatting.AQUA)
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
	static String[] arguments(final Player viewer, final School school, final SpellNumbers numbers, final SpellModifiers.CastModifiers modifiers) {
		return arguments(viewer, school, numbers, modifiers, "");
	}

	static String[] arguments(
		final Player viewer, final School school, final SpellNumbers numbers, final SpellModifiers.CastModifiers modifiers, final String path
	) {
		double scale = SummonSkeleton.healthScale();
		double power = modifiers.powerMultiplier();
		boolean capped = numbers.capTicks() > 0;
		return new String[] {
			damage(viewer, school, numbers.damage(), power, scale, path),
			heal(viewer, school, numbers.heal(), power, scale),
			heal(viewer, school, numbers.allyHeal(), power, scale),
			numbers.duration().ticks() > 0 ? duration(numbers.durationTicks(modifiers)) : "0",
			capped ? duration(numbers.duration().scaled(numbers.capTicks(), modifiers))
				: numbers.extra().ticks() > 0 ? duration(numbers.extraTicks(modifiers)) : "0",
			tenths(numbers.radius(modifiers)),
			Integer.toString(numbers.targets()),
			roman(numbers.amplifier() + 1),
			roman(numbers.extraAmplifier() + 1)
		};
	}

	// A range from the caster's lowest to highest roll; the flat number times the power when the RPG mod gives none.
	// A mage reads its MATK. A fighter and an archer read their own stat. A priest's holy hit reads the heal formula.
	private static String damage(final Player viewer, final School school, final float flat, final double power, final double scale, final String path) {
		if (flat <= 0.0f) {
			return "0";
		}
		if ("arrow_rain".equals(path)) {
			double arrow = RpgBridge.averageAtk(viewer, true);
			if (Double.isFinite(arrow) && arrow >= 0.0) {
				return number(arrow);
			}
		}
		if (school == School.PRIEST) {
			return number(RpgBridge.skillHeal(viewer, school, flat, power) * scale);
		}
		if (school != School.MAGE) {
			double[] rolls = RpgBridge.skillDamageRange(viewer, school, flat, power);
			double factor = school == School.ARCHER ? ArcherSkills.eagleEyeFactor(viewer) * ArcherSkills.quiverDamageFactor(viewer) : 1.0;
			String lowest = number(rolls[0] * scale * factor);
			String highest = number(rolls[1] * scale * factor);
			return lowest.equals(highest) ? lowest : lowest + "-" + highest;
		}
		double percent = flat * RpgBridge.SKILL_PERCENT_PER_FLAT;
		double hit = RpgBridge.matkHit(viewer, percent);
		if (!(hit > 0.0) || !Double.isFinite(hit)) {
			return number(flat * power * scale);
		}
		return number(hit * power);
	}

	// The school's own heal formula: a mage reads Intelligence, the other schools read Vitality.
	private static String heal(final Player viewer, final School school, final float flat, final double power, final double scale) {
		return flat <= 0.0f ? "0" : number(RpgBridge.skillHeal(viewer, school, flat, power) * scale);
	}

	private static String seconds(final int ticks) {
		return tenths(ticks / TICKS_PER_SECOND);
	}

	// A minute or more reads in minutes, so a 30-minute buff is not 1800 seconds.
	static String duration(final int ticks) {
		double seconds = ticks / TICKS_PER_SECOND;
		if (seconds >= SECONDS_PER_MINUTE) {
			String minutes = tenths(seconds / SECONDS_PER_MINUTE);
			return minutes + ("1".equals(minutes) ? " minute" : " minutes");
		}
		String shown = tenths(seconds);
		return shown + ("1".equals(shown) ? " second" : " seconds");
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
