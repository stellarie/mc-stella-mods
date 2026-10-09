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
		definition("earthsplitter", Items.TUFF, 240, 9_137_998, TargetMode.SELF, FighterSkills::applyEarthsplitter),
		definition("multishot", Items.ARROW, 100, 12_073_291, TargetMode.SELF, ArcherSkills::applyGrapple, School.ARCHER),
		definition("piercing_shot", Items.SPECTRAL_ARROW, 160, 7_148_392, TargetMode.SELF, ArcherSkills::applyPiercingShot, School.ARCHER),
		definition("arrow_rain", Items.DISPENSER, 300, 3_986_402, TargetMode.BLOCK, ArcherSkills::applyArrowRain, School.ARCHER),
		definition("rapid_fire", Items.STRING, 120, 15_750_933, TargetMode.HOSTILE, ArcherSkills::applyDoubleStrafe, School.ARCHER),
		definition("hunters_mark", Items.TARGET, 200, 8_619_217, TargetMode.HOSTILE, ArcherSkills::applyBlitzBeat, School.ARCHER),
		definition("ankle_snare", Items.LEAD, 200, 6_908_265, TargetMode.BLOCK, ArcherSkills::applyAnkleSnare, School.ARCHER),
		definition("arrow_repel", Items.STICKY_PISTON, 160, 11_184_810, TargetMode.HOSTILE, ArcherSkills::applyArrowRepel, School.ARCHER),
		definition("explosive_arrow", Items.TNT, 200, 1_428_765, TargetMode.SELF, ArcherSkills::applyExplosiveArrow, School.ARCHER),
		definition("disengage", Items.RABBIT_HIDE, 160, 10_409_338, TargetMode.SELF, ArcherSkills::applyDisengage, School.ARCHER),
		definition("snare_trap", Items.COBWEB, 200, 5_372_846, TargetMode.SELF, ArcherSkills::applySnareTrap, School.ARCHER),
		definition("aimed_bolt", Items.TIPPED_ARROW, 200, 12_073_292, TargetMode.SELF, ArcherSkills::applyAimedBolt, School.ARCHER),
		definition("triangle_shot", Items.FIREWORK_ROCKET, 300, 12_073_293, TargetMode.SELF, ArcherSkills::applyTriangleShot, School.ARCHER),
		definition("eagle_eye", Items.SPYGLASS, 600, 13_964_170, TargetMode.SELF, ArcherSkills::applyEagleEye, School.ARCHER),
		definition("elemental_quiver", Items.BOW, 400, 2_935_071, TargetMode.SELF, ArcherSkills::applyElementalQuiver, School.ARCHER),
		// The priest books: VIT, healing, and the holy damage this stage doubles against the undead.
		definition("holy_light", Items.GLOW_BERRIES, 100, 16_711_834, TargetMode.ALLY, PriestSkills::applyHolyLight, School.PRIEST),
		definition("prayer_of_mending", Items.PINK_PETALS, 200, 16_420_267, TargetMode.ALLY, PriestSkills::applyPrayerOfMending, School.PRIEST),
		definition("renew", Items.SWEET_BERRIES, 200, 13_978_027, TargetMode.ALLY, PriestSkills::applyRenew, School.PRIEST),
		definition("divine_shield", Items.GOLD_NUGGET, 300, 14_909_210, TargetMode.ALLY, PriestSkills::applyDivineShield, School.PRIEST),
		definition("holy_arms", Items.GOLDEN_SWORD, 600, 16_777_536, TargetMode.SELF, PriestSkills::applyHolyArms, School.PRIEST),
		definition("blessing_of_fortitude", Items.GOLDEN_APPLE, 600, 16_712_832, TargetMode.SELF, PriestSkills::applyBlessingOfFortitude, School.PRIEST),
		definition("smite", Items.GOLDEN_AXE, 160, 16_711_475, TargetMode.HOSTILE, PriestSkills::applySmite, School.PRIEST),
		definition("resurrection_ward", Items.TOTEM_OF_UNDYING, 2400, 15_908_520, TargetMode.ALLY, PriestSkills::applyResurrectionWard, School.PRIEST),
		definition("consecrate", Items.CANDLE, 400, 16_647_360, TargetMode.BLOCK, PriestSkills::applyConsecrate, School.PRIEST),
		definition("guardian_angel", Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 400, 16_774_338, TargetMode.ALLY, PriestSkills::applyGuardianAngel, School.PRIEST),
		definition("unbroken_vow", Items.ANVIL, 600, 13_161_182, TargetMode.SELF, PriestSkills::applyUnbrokenVow, School.PRIEST),
		definition("steadfast_prayer", Items.IRON_BARS, 600, 15_787_704, TargetMode.SELF, PriestSkills::applySteadfastPrayer, School.PRIEST)
	);

	private SkillbookCatalog() {
	}

	private static SpellDefinition definition(
		final String path, final Item ingredient, final int cooldown, final int tint,
		final TargetMode targetMode, final SpellEffect effect
	) {
		return definition(path, ingredient, cooldown, tint, targetMode, effect, School.FIGHTER);
	}

	private static SpellDefinition definition(
		final String path, final Item ingredient, final int cooldown, final int tint,
		final TargetMode targetMode, final SpellEffect effect, final School school
	) {
		return SpellbookCatalog.definition(path, ingredient, cooldown, tint, targetMode, effect, school);
	}
}
