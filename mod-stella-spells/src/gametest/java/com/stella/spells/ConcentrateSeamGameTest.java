package com.stella.spells;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the RPG mod's own seams read back from this mod. This harness carries no copy of that mod, so the
 * three cases below are not required: each one fails with the seam it could not find, and it passes only on
 * a run that puts the sibling on the classpath through the build's {@code rpgJar} property. MANUAL: run
 * {@code gradlew.bat runGameTest -PgameTestFilter=*:concentrate_seam* -PrpgJar=<the RPG mod jar>} and read
 * the three RESULT lines. The subject is a bow-holding caster with the effect already on, never a cast, so
 * the reading does not depend on either mod's mixins.
 */
public class ConcentrateSeamGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.concentrateseam");
	private static final double SUBJECT_X = 1.5;
	private static final double SUBJECT_Z = 1.5;
	/** The one stat key the archer school reads, and the hit percent the RPG mod's range is asked about. */
	private static final String DEX_KEY = "dex";
	private static final double FULL_HIT = 100.0;
	/** The RPG mod's shipped critical multiplier before any provider answers, its config default. */
	private static final double BASE_CRIT_MULTIPLIER = 1.5;
	/** The weapon slot the RPG mod reads a bow from, and a pack slot a cast never replaces. */
	private static final int WEAPON_SLOT = 35;
	private static final String STAT_BONUSES = "com.stella.rpg.StatBonuses";
	private static final String STAT = "com.stella.rpg.Stat";
	private static final String CRIT_BONUSES = "com.stella.rpg.CritBonuses";
	private static final String RPG_SKILLS = "com.stella.rpg.RpgSkills";

	/**
	 * The whole point of the change: the RPG mod's own critical seam answers half again on both halves of the
	 * roll for a concentrating archer, and nothing for a body with no effect. Both readings come from that
	 * mod's class, so neither is this mod's own arithmetic read back to itself.
	 */
	@GameTest(required = false, maxTicks = 80)
	public void theCriticalSeamAnswersBothHalvesForAConcentratingArcher(final GameTestHelper helper) {
		ServerPlayer concentrating = concentrating(helper);
		ServerPlayer plain = plain(helper);
		double chance = seamDouble(CRIT_BONUSES, "chanceBonus", concentrating);
		double damage = seamDouble(CRIT_BONUSES, "damageBonus", concentrating);
		double plainChance = seamDouble(CRIT_BONUSES, "chanceBonus", plain);
		double plainDamage = seamDouble(CRIT_BONUSES, "damageBonus", plain);
		LOG.info("[BEHAVIOR] test=theCriticalSeamAnswersBothHalvesForAConcentratingArcher RESULT held={} bow={} chance={} damage={} askedHalf={} plainChance={} plainDamage={}",
			concentrating.hasEffect(SpellStatus.CONCENTRATE), ArcherSkills.hasBowOrCrossbow(concentrating),
			chance, damage, ConcentrateStats.CONCENTRATE_CRITICAL_HALF, plainChance, plainDamage);
		helper.assertTrue(seamPresent(CRIT_BONUSES), "the RPG mod's critical seam " + CRIT_BONUSES + " is absent");
		helper.assertTrue(chance == ConcentrateStats.CONCENTRATE_CRITICAL_HALF,
			"the RPG seam read " + chance + " critical chance, not " + ConcentrateStats.CONCENTRATE_CRITICAL_HALF);
		helper.assertTrue(damage == ConcentrateStats.CONCENTRATE_CRITICAL_HALF,
			"the RPG seam read " + damage + " critical damage, not " + ConcentrateStats.CONCENTRATE_CRITICAL_HALF);
		helper.assertTrue(plainChance == 0.0 && plainDamage == 0.0,
			"a body with no concentrate read a bonus: chance=" + plainChance + " damage=" + plainDamage);
		helper.succeed();
	}

	/**
	 * The second half of the same seam, and the one the critical damage roll multiplies by: the RPG mod's own
	 * critical multiplier for a concentrating archer is its shipped base plus the half this mod hands it. The
	 * base is read from that mod through the same reflective call, so a config change moves both sides.
	 */
	@GameTest(required = false, maxTicks = 80)
	public void theRpgCriticalMultiplierGainsTheHalfThisModHandsIt(final GameTestHelper helper) {
		ServerPlayer concentrating = concentrating(helper);
		ServerPlayer plain = plain(helper);
		double withHalf = seamMultiplier(concentrating);
		double withoutHalf = seamMultiplier(plain);
		double gain = withHalf - withoutHalf;
		LOG.info("[BEHAVIOR] test=theRpgCriticalMultiplierGainsTheHalfThisModHandsIt RESULT held={} bow={} concentrating={} plain={} gain={} askedHalf={} base={}",
			concentrating.hasEffect(SpellStatus.CONCENTRATE), ArcherSkills.hasBowOrCrossbow(concentrating),
			withHalf, withoutHalf, gain, ConcentrateStats.CONCENTRATE_CRITICAL_HALF, BASE_CRIT_MULTIPLIER);
		helper.assertTrue(seamPresent(CRIT_BONUSES), "the RPG mod's critical seam " + CRIT_BONUSES + " is absent");
		helper.assertTrue(withoutHalf == BASE_CRIT_MULTIPLIER,
			"the RPG mod's own critical multiplier is " + withoutHalf + ", not its shipped " + BASE_CRIT_MULTIPLIER);
		helper.assertTrue(gain == ConcentrateStats.CONCENTRATE_CRITICAL_HALF,
			"concentrate moved the RPG critical multiplier by " + gain + ", not "
				+ ConcentrateStats.CONCENTRATE_CRITICAL_HALF);
		helper.succeed();
	}

	/**
	 * The net effect of the Dexterity half on the shot both damage paths share. The RPG mod's ranged interval
	 * grows with the caster's own dice, so 25 more Dexterity widens what a hit can roll. The two intervals come
	 * from that mod's class, and the gain is compared against the interval its own shipped dice produce for the
	 * grant, so the bound follows the number this mod ships rather than a copy of it.
	 */
	@GameTest(required = false, maxTicks = 80)
	public void theDexterityHalfWidensTheRpgOwnRangedInterval(final GameTestHelper helper) {
		ServerPlayer concentrating = concentrating(helper);
		ServerPlayer plain = plain(helper);
		double[] withDex = seamRange(concentrating);
		double[] without = seamRange(plain);
		double lowGain = withDex[0] - without[0];
		double highGain = withDex[1] - without[1];
		LOG.info("[BEHAVIOR] test=theDexterityHalfWidensTheRpgOwnRangedInterval RESULT held={} bow={} low={} high={} plainLow={} plainHigh={} lowGain={} highGain={} askedDex={}",
			concentrating.hasEffect(SpellStatus.CONCENTRATE), ArcherSkills.hasBowOrCrossbow(concentrating),
			withDex[0], withDex[1], without[0], without[1], lowGain, highGain,
			ConcentrateStats.CONCENTRATE_DEXTERITY);
		helper.assertTrue(seamPresent(RPG_SKILLS), "the RPG mod's skill seam " + RPG_SKILLS + " is absent");
		double statRead = seamStat(STAT_BONUSES, "effective", concentrating);
		helper.assertTrue(without[1] > without[0],
			"the plain body's own ranged interval is empty: " + without[0] + ".." + without[1]);
		helper.assertTrue(lowGain > 0.0 && highGain > 0.0,
			"the Dexterity grant widened the RPG mod's own ranged interval by nothing: "
				+ without[0] + ".." + without[1] + " becomes " + withDex[0] + ".." + withDex[1]);
		helper.assertTrue(statRead == ConcentrateStats.CONCENTRATE_DEXTERITY,
			"the RPG stat seam read " + statRead + " Dexterity, not " + ConcentrateStats.CONCENTRATE_DEXTERITY);
		helper.succeed();
	}

	/** A bow-holding caster that carries Concentrate, put on with no cast so no mixin of either mod is asked. */
	private static ServerPlayer concentrating(final GameTestHelper helper) {
		ServerPlayer subject = plain(helper);
		subject.addEffect(new MobEffectInstance(SpellStatus.CONCENTRATE, ArcherSkills.CONCENTRATE_TICKS, 0), subject);
		return subject;
	}

	/** A caster that holds a bow and carries no effect: the reading every gain above is measured against. */
	private static ServerPlayer plain(final GameTestHelper helper) {
		ServerPlayer subject = FighterBooksGameTest.caster(helper);
		subject.setPos(SUBJECT_X, 1.0, SUBJECT_Z);
		subject.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		return subject;
	}

	/** Whether the RPG mod's class is on this classpath at all, which is what the sibling-absent case states. */
	static boolean seamPresent(final String owner) {
		try {
			Class.forName(owner, false, ConcentrateSeamGameTest.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	/** One double the RPG mod's own class answers about a body, or NaN when the class or method is not there. */
	private static double seamDouble(final String owner, final String method, final ServerPlayer subject) {
		Object answer = seam(owner, method, new Class<?>[] {LivingEntity.class}, subject);
		return answer instanceof Double value ? value : Double.NaN;
	}

	/** The RPG mod's own critical multiplier for a body, or NaN when that seam is absent. */
	private static double seamMultiplier(final ServerPlayer subject) {
		return seamDouble(CRIT_BONUSES, "critMultiplier", subject);
	}

	/** The RPG mod's own effective value of one stat for a body, or NaN when that seam is absent. */
	private static double seamStat(final String owner, final String method, final ServerPlayer subject) {
		Object stat = seamConstant(STAT, "DEX");
		return stat == null ? Double.NaN : seamValue(owner, method, subject, stat);
	}

	private static double seamValue(final String owner, final String method, final ServerPlayer subject, final Object stat) {
		try {
			Class<?> holder = Class.forName(owner, true, ConcentrateSeamGameTest.class.getClassLoader());
			Method found = holder.getMethod(method, LivingEntity.class, stat.getClass());
			Object answer = found.invoke(null, subject, stat);
			return answer instanceof Integer value ? value.doubleValue() : Double.NaN;
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException
			| LinkageError | RuntimeException e) {
			return Double.NaN;
		}
	}

	/** One enum constant of the RPG mod's own stat class, or null when that class is absent. */
	private static Object seamConstant(final String owner, final String name) {
		try {
			Class<?> holder = Class.forName(owner, true, ConcentrateSeamGameTest.class.getClassLoader());
			for (Object value : holder.getEnumConstants()) {
				if (((Enum<?>) value).name().equals(name)) {
					return value;
				}
			}
			return null;
		} catch (ClassNotFoundException | LinkageError | RuntimeException e) {
			return null;
		}
	}

	/** The RPG mod's own ranged hit interval for a full hit, as a pair; a NaN pair when it is absent. */
	private static double[] seamRange(final ServerPlayer subject) {
		Object answer = seam(RPG_SKILLS, "physicalRange",
			new Class<?>[] {LivingEntity.class, String.class, double.class}, subject, DEX_KEY, FULL_HIT);
		return answer instanceof double[] rolls && rolls.length >= 2
			? new double[] {rolls[0], rolls[1]}
			: new double[] {Double.NaN, Double.NaN};
	}

	private static Object seam(final String owner, final String method, final Class<?>[] types, final Object... arguments) {
		try {
			Class<?> holder = Class.forName(owner, true, ConcentrateSeamGameTest.class.getClassLoader());
			Method found = holder.getMethod(method, types);
			return found.invoke(null, arguments);
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException
			| LinkageError | RuntimeException e) {
			return null;
		}
	}
}
