package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RPG cooldown multiplier acts on every cast of every school after the book refine cut. The stub answers 0.6,
 * the multiplier the RPG mod gives at INT + DEX = 200. Without the RPG mod a cast rests for the book's base.
 */
public final class CooldownReductionGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.cooldownreduction");
	private static final double MULTIPLIER_AT_TWO_HUNDRED = 0.6;
	private static final int WEAPON_SLOT = 35;
	private static final int REFINE_COOLDOWN_LEVEL = 7;
	/** A mage book, a fighter book and an archer book. */
	private static final String[] BOOKS = {"quicksilver", "iron_skin", "eagle_eye"};

	@GameTest(maxTicks = 40)
	public void aCastRestsSixtyPercentOfTheBaseAtTwoHundredIntAndDexInEverySchool(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		StringBuilder row = new StringBuilder();
		for (String path : BOOKS) {
			int base = baseCooldown(path);
			int rested = castRest(caster, new ItemStack(SkillSchoolGameTest.book(path)), MULTIPLIER_AT_TWO_HUNDRED);
			row.append(' ').append(path).append('=').append(rested).append('/').append(base);
			helper.assertTrue(rested == Math.round(base * MULTIPLIER_AT_TWO_HUNDRED),
				path + " rested " + rested + " ticks against a base of " + base);
		}
		LOG.info("[BEHAVIOR] test=aCastRestsSixtyPercentOfTheBaseAtTwoHundredIntAndDexInEverySchool RESULT{}", row);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theCutComesAfterTheRefineCooldownCut(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		int base = baseCooldown("iron_skin");
		int refined = BookRefine.cooldown(base, SpellbookCatalog.get(SkillSchoolGameTest.id("iron_skin")), REFINE_COOLDOWN_LEVEL);
		int rested = castRest(caster, BookRefineGameTest.refined("iron_skin", REFINE_COOLDOWN_LEVEL), MULTIPLIER_AT_TWO_HUNDRED);
		LOG.info("[BEHAVIOR] test=theCutComesAfterTheRefineCooldownCut RESULT base={} refined={} rested={}", base, refined, rested);
		helper.assertTrue(refined < base, "the refine cut did nothing: " + refined + " against " + base);
		helper.assertTrue(rested == Math.round(refined * MULTIPLIER_AT_TWO_HUNDRED),
			"a +" + REFINE_COOLDOWN_LEVEL + " book rested " + rested + " ticks, not 60% of " + refined);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void withoutTheRpgModACastRestsForTheBaseCooldown(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		StringBuilder row = new StringBuilder();
		for (String path : BOOKS) {
			int base = baseCooldown(path);
			int rested = castRest(caster, new ItemStack(SkillSchoolGameTest.book(path)), Double.NaN);
			row.append(' ').append(path).append('=').append(rested).append('/').append(base);
			helper.assertTrue(rested == base, path + " rested " + rested + " ticks without the RPG mod, not " + base);
		}
		int base = baseCooldown("iron_skin");
		int refined = BookRefine.cooldown(base, SpellbookCatalog.get(SkillSchoolGameTest.id("iron_skin")), REFINE_COOLDOWN_LEVEL);
		int rested = castRest(caster, BookRefineGameTest.refined("iron_skin", REFINE_COOLDOWN_LEVEL), Double.NaN);
		LOG.info("[BEHAVIOR] test=withoutTheRpgModACastRestsForTheBaseCooldown RESULT{} refined={}/{}", row, rested, refined);
		helper.assertTrue(rested == refined, "a +" + REFINE_COOLDOWN_LEVEL + " book rested " + rested + " ticks without the RPG mod, not " + refined);
		helper.succeed();
	}

	private static int baseCooldown(final String path) {
		return SpellbookCatalog.get(SkillSchoolGameTest.id(path)).baseCooldownTicks();
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		return caster;
	}

	/** Casts the book with the RPG cooldown multiplier in force (NaN for no RPG mod) and counts the ticks it rests. */
	private static int castRest(final ServerPlayer caster, final ItemStack book, final double multiplier) {
		if (!Double.isNaN(multiplier)) {
			RpgBridge.setStats(new Multiplier(multiplier));
		}
		SpellCasting.CastResult result;
		try {
			result = BookRefineGameTest.cast(caster, book);
		} finally {
			RpgBridge.setStats(null);
		}
		if (result != SpellCasting.CastResult.CAST) {
			throw new IllegalStateException(book + " cast was " + result);
		}
		ItemStack held = caster.getMainHandItem();
		int ticks = 0;
		while (caster.getCooldowns().isOnCooldown(held) && ticks < 10_000) {
			caster.getCooldowns().tick();
			ticks++;
		}
		return ticks;
	}

	private record Multiplier(double value) implements RpgBridge.Stats {
		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return this.value;
		}
	}
}
