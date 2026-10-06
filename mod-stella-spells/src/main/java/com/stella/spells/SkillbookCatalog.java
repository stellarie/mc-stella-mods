package com.stella.spells;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The skill books: fighter, archer and priest, each one cast from the quick slot and scaled by its own stat. */
public final class SkillbookCatalog {
	/** Every skill book, in registration order; the mage books come first in {@link SpellbookCatalog#DEFINITIONS}. */
	public static final List<SpellDefinition> DEFINITIONS = List.of(
		definition("cleave", Items.IRON_AXE, 100, 11_653_955, TargetMode.SELF, FighterSkills::applyCleave),
		definition("leap_slam", Items.PISTON, 240, 9_267_931, TargetMode.HOSTILE, FighterSkills::applyLeapSlam),
		definition("whirlwind", Items.BREEZE_ROD, 300, 8_860_204, TargetMode.SELF, FighterSkills::applyWhirlwind),
		definition("shield_bash", Items.SHIELD, 160, 9_267_158, TargetMode.HOSTILE, FighterSkills::applyShieldBash),
		definition("charge", Items.LEATHER, 200, 11_329_321, TargetMode.SELF, FighterSkills::applyCharge),
		definition("execute", Items.IRON_SWORD, 240, 14_746_249, TargetMode.HOSTILE, FighterSkills::applyExecute),
		definition("war_cry", Items.NOTE_BLOCK, 400, 16_851_253, TargetMode.SELF, FighterSkills::applyWarCry),
		definition("counter_stance", Items.TRIPWIRE_HOOK, 200, 15_102_646, TargetMode.SELF, FighterSkills::applyCounterStance),
		definition("iron_skin", Items.RAW_IRON, 600, 9_530_554, TargetMode.SELF, FighterSkills::applyIronSkin),
		definition("earthsplitter", Items.TUFF, 240, 9_137_998, TargetMode.SELF, FighterSkills::applyEarthsplitter)
	);

	private SkillbookCatalog() {
	}

	private static SpellDefinition definition(
		final String path, final Item ingredient, final int cooldown, final int tint,
		final TargetMode targetMode, final SpellEffect effect
	) {
		return SpellbookCatalog.definition(path, ingredient, cooldown, tint, targetMode, effect, School.FIGHTER);
	}
}
