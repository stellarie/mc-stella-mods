package com.stella.villages.mission;

import com.stella.villages.DescriptionLines;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What the job menu tells a player about each job: what the job does, the stat bonuses it gives, and its innate skill.
 * The numbers repeat the rules of the rpg mod and the spells mod, which this mod cannot read: +2 to the main stat and
 * +1 to the second stat for each level, the health factor, +5 and +1 for a boss trial, and the innate of the job.
 */
final class JobInfo {
	private static final int MAIN_PER_LEVEL = 2;
	private static final int SECOND_PER_LEVEL = 1;
	private static final int TRIAL_MAIN = 5;
	private static final int TRIAL_OTHER = 1;

	private record Info(
		String title, String description, String main, String second, String health, String innate, String innateText
	) {
	}

	private static final Map<String, Info> JOBS = Map.of(
		"archer", new Info("an Archer", "Strikes from afar with a bow.", "DEX", "AGI", "1.0", "Falcon Guard",
			"A black falcon circles above you. Every 2 seconds it has a 35% chance to strike a nearby mob 3 times,"
				+ " each hit your average bow roll. A kill gives you 25% of the mob's experience."),
		"fighter", new Info("a Fighter", "Wins in close combat. Tough.", "STR", "VIT", "1.3", "Double Strike",
			"Each attack has a 25% chance to hit twice. Both hits can crit."
				+ " The second hit gives Nausea for 0.5 seconds."),
		"mage", new Info("a Mage", "Casts spells from books. Frail.", "INT", "DEX", "0.85", "Arcane Ward",
			"A shield of MATK/100 x 10% of your max health. It refills after 15 seconds without a hit."
				+ " When it breaks, mobs within 3 blocks take MATK/100 x 5% of your max health."),
		"priest", new Info("a Priest", "Heals allies and fights the undead.", "VIT", "INT", "1.1", "Sanctuary",
			"Within 3 blocks, you and your allies heal 1.5% of max health per second,"
				+ " and hostile mobs lose 1.5% of theirs.")
	);

	private JobInfo() {
	}

	/** The name of the menu button, such as "Become an Archer". */
	static String choiceTitle(final String job) {
		Info info = JOBS.get(job);
		return "Become " + (info == null ? "a " + job : info.title());
	}

	/** The tooltip of the menu button: the job, its bonuses, its innate, and what a choice does. */
	static List<Component> lore(final String job) {
		List<Component> lore = new ArrayList<>();
		Info info = JOBS.get(job);
		if (info != null) {
			lore.addAll(DescriptionLines.lines(info.description(), ChatFormatting.GRAY));
			lore.add(Component.empty());
			lore.addAll(DescriptionLines.lines("Bonuses", ChatFormatting.YELLOW));
			lore.addAll(DescriptionLines.lines("+" + MAIN_PER_LEVEL + " " + info.main() + ", +" + SECOND_PER_LEVEL + " " + info.second() + " per level",
				ChatFormatting.GREEN));
			lore.addAll(DescriptionLines.lines("Max health x" + info.health(), ChatFormatting.GREEN));
			lore.addAll(DescriptionLines.lines("Trial: +" + TRIAL_MAIN + " " + info.main() + ", +" + TRIAL_OTHER + " others", ChatFormatting.GREEN));
			lore.add(Component.empty());
			lore.addAll(DescriptionLines.lines("Innate: " + info.innate(), ChatFormatting.GOLD));
			lore.addAll(DescriptionLines.lines(info.innateText(), ChatFormatting.GRAY));
			lore.add(Component.empty());
		}
		lore.addAll(DescriptionLines.lines("You also get its item", ChatFormatting.DARK_GRAY));
		lore.addAll(DescriptionLines.lines("Click to choose, forever", ChatFormatting.WHITE));
		return lore;
	}
}
