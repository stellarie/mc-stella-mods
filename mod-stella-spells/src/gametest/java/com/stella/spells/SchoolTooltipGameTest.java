package com.stella.spells;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A skill book's tooltip number comes from its own school's stat, as the cast does. A fighter reads Strength,
 * a priest reads Vitality, and neither reads the MATK that a mage's tooltip shows.
 */
public final class SchoolTooltipGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.schoolTooltip");
	private static final double K = 10.0;
	private static final int DAMAGE_ARG = 0;
	private static final int HEAL_ARG = 1;
	private static final int ALLY_HEAL_ARG = 2;
	private static final double HALF = 0.5;
	/** What one flat point of a skill hit is worth in the stand-in, in vanilla units, for each school's stat. */
	private static final double STRENGTH_ROLL = 10.0;
	private static final double VITALITY_HEAL = 20.0;
	private static final double INTELLIGENCE_HEAL = 40.0;
	private static final double MATK_ROLL = 50.0;

	/** An RPG stand-in whose school stats answer with numbers a mage's MATK cannot produce. */
	private static final class SchoolStats implements RpgBridge.Stats {
		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double spellDamage(final LivingEntity caster, final double skillPercent) {
			return skillPercent / 100.0 * MATK_ROLL / K;
		}

		@Override
		public double spellHeal(final LivingEntity caster, final double coefficient) {
			return coefficient * INTELLIGENCE_HEAL / K;
		}

		@Override
		public double skillDamage(final LivingEntity caster, final String statKey, final double skillPercent) {
			return skillPercent / 100.0 * STRENGTH_ROLL / K;
		}

		@Override
		public double[] skillDamageRange(final LivingEntity caster, final String statKey, final double skillPercent) {
			double roll = this.skillDamage(caster, statKey, skillPercent);
			return new double[] {roll * 0.9, roll * 1.1};
		}

		@Override
		public double skillHeal(final LivingEntity caster, final double coefficient) {
			return coefficient * VITALITY_HEAL / K;
		}

		@Override
		public double matkMin(final LivingEntity caster) {
			return MATK_ROLL;
		}

		@Override
		public double matkMax(final LivingEntity caster) {
			return MATK_ROLL * 2.0;
		}
	}

	@GameTest(maxTicks = 100)
	public void aFighterBookShowsItsStrengthRollAndTheCastDealsIt(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aFighterBookShowsItsStrengthRollAndTheCastDealsIt");
		Object savedScale = FabricLoader.getInstance().getObjectShare().get(SummonSkeleton.HEALTH_SCALE_SHARE);
		try {
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, K);
			RpgBridge.setStats(new SchoolStats());
			ServerPlayer caster = FighterBooksGameTest.caster(helper);
			Mob foe = FighterBooksGameTest.foe(helper, 1.5, 3.5);
			String shown = argument(SpellTooltip.lines(caster, new ItemStack(SkillSchoolGameTest.book("execute"))).get(0), DAMAGE_ARG);
			double[] range = range(shown);
			double flatPercent = SpellNumbers.EXECUTE.damage() * RpgBridge.SKILL_PERCENT_PER_FLAT;
			double expected = flatPercent / 100.0 * STRENGTH_ROLL;
			float before = foe.getHealth();
			helper.assertTrue(SkillSchoolGameTest.cast(caster, "execute", foe, false), "execute did not cast");
			double taken = (before - foe.getHealth()) * K;
			LOG.info("[SPELLTEST] test=fighterTooltip RESULT tooltip={} realCast={} strengthRoll={} matkRange={}-{}",
				shown, taken, expected, flatPercent / 100.0 * MATK_ROLL, flatPercent / 100.0 * MATK_ROLL * 2.0);
			helper.assertTrue(Math.abs(range[0] - expected * 0.9) <= HALF && Math.abs(range[1] - expected * 1.1) <= HALF,
				"the tooltip shows " + shown + ", not the Strength roll " + expected * 0.9 + "-" + expected * 1.1);
			helper.assertTrue(taken >= range[0] - HALF && taken <= range[1] + HALF, "the cast dealt " + taken + ", outside the tooltip " + shown);
			foe.discard();
		} finally {
			RpgBridge.setStats(null);
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, savedScale == null ? Double.valueOf(1.0) : savedScale);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aPriestBookShowsItsVitalityHealAndHolyHitAndTheCastGivesThem(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aPriestBookShowsItsVitalityHealAndHolyHitAndTheCastGivesThem");
		Object savedScale = FabricLoader.getInstance().getObjectShare().get(SummonSkeleton.HEALTH_SCALE_SHARE);
		try {
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, K);
			RpgBridge.setStats(new SchoolStats());
			SkillSchoolGameTest.floor(helper);
			ServerPlayer caster = FighterBooksGameTest.caster(helper);

			String heal = argument(SpellTooltip.lines(caster, new ItemStack(SkillSchoolGameTest.book("holy_light"))).get(0), HEAL_ARG);
			net.minecraft.world.entity.npc.villager.Villager ally = PriestBooksGameTest.ally(helper, 1.5, 5.5);
			ally.setHealth((float) (ally.getMaxHealth() * HALF));
			float before = ally.getHealth();
			helper.assertTrue(PriestBooksGameTest.castAtAlly(caster, ally, "holy_light", false), "holy light did not cast");
			double healed = (ally.getHealth() - before) * K;
			double expectedHeal = PriestSkills.HOLY_LIGHT_HEAL * RpgBridge.HEAL_COEFFICIENT_PER_FLAT * VITALITY_HEAL;
			LOG.info("[SPELLTEST] test=priestTooltip RESULT tooltipHeal={} realHeal={} vitalityHeal={}", heal, healed, expectedHeal);
			helper.assertTrue(Math.abs(Double.parseDouble(heal) - expectedHeal) <= HALF, "the tooltip heals " + heal + ", not the Vitality heal " + expectedHeal);
			helper.assertTrue(Math.abs(healed - Double.parseDouble(heal)) <= HALF, "the cast healed " + healed + ", the tooltip says " + heal);
			ally.discard();

			String holy = argument(SpellTooltip.lines(caster, new ItemStack(SkillSchoolGameTest.book("smite"))).get(0), DAMAGE_ARG);
			Mob foe = FighterBooksGameTest.foe(helper, 1.5, 3.5);
			float healthBefore = foe.getHealth();
			helper.assertTrue(SkillSchoolGameTest.cast(caster, "smite", foe, false), "smite did not cast");
			double struck = (healthBefore - foe.getHealth()) * K / PriestSkills.undeadFactor(foe);
			double expectedHit = PriestSkills.SMITE_DAMAGE * RpgBridge.HEAL_COEFFICIENT_PER_FLAT * VITALITY_HEAL;
			LOG.info("[SPELLTEST] test=priestTooltip RESULT tooltipHoly={} realHoly={} vitalityHit={}", holy, struck, expectedHit);
			helper.assertTrue(Math.abs(Double.parseDouble(holy) - expectedHit) <= HALF, "the tooltip shows " + holy + " holy damage, not " + expectedHit);
			helper.assertTrue(Math.abs(struck - Double.parseDouble(holy)) <= HALF, "the cast struck for " + struck + ", the tooltip says " + holy);
			foe.discard();

			Component shieldLine = SpellTooltip.lines(caster, new ItemStack(SkillSchoolGameTest.book("divine_shield"))).get(0);
			String shield = argument(shieldLine, ALLY_HEAL_ARG);
			String shieldText = DescriptionSupport.withShippedText(shieldLine::getString);
			helper.assertTrue(shieldText.contains("ward of " + shield + " health"), "the shipped text does not print the ward " + shield + ": " + shieldText);
			net.minecraft.world.entity.npc.villager.Villager warded = PriestBooksGameTest.ally(helper, 1.5, 5.5);
			helper.assertTrue(PriestBooksGameTest.castAtAlly(caster, warded, "divine_shield", false), "divine shield did not cast");
			double ward = PriestBooksGameTest.wardOf(warded) * K;
			double expectedWard = PriestSkills.DIVINE_SHIELD_WARD * RpgBridge.HEAL_COEFFICIENT_PER_FLAT * VITALITY_HEAL;
			LOG.info("[SPELLTEST] test=priestTooltip RESULT tooltipWard={} realWard={} vitalityWard={}", shield, ward, expectedWard);
			helper.assertTrue(Math.abs(Double.parseDouble(shield) - expectedWard) <= HALF, "the tooltip shows a ward of " + shield + ", not " + expectedWard);
			helper.assertTrue(Math.abs(ward - Double.parseDouble(shield)) <= HALF, "the cast warded " + ward + ", the tooltip says " + shield);
			warded.discard();
		} finally {
			RpgBridge.setStats(null);
			FabricLoader.getInstance().getObjectShare().put(SummonSkeleton.HEALTH_SCALE_SHARE, savedScale == null ? Double.valueOf(1.0) : savedScale);
		}
		helper.succeed();
	}

	private static String argument(final Component line, final int index) {
		return (String) DescriptionSupport.translatable(line).getArgs()[index];
	}

	private static double[] range(final String text) {
		List<String> parts = List.of(text.split("-"));
		return new double[] {Double.parseDouble(parts.get(0)), Double.parseDouble(parts.get(parts.size() - 1))};
	}
}
