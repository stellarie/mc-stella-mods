package com.stella.spells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleSupplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each imbued book rolls its own chance on each hit: the base plus 1 percent per LUK. Every case runs its hits in
 * one call, with a seeded dice and a pinned LUK, so no other case sees the pinned values.
 */
public class ImbueProcGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.imbueproc");
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final Identifier FROSTBIND = Identifier.parse("mod-stella-spells:frostbind");
	private static final Identifier EMBER_BRAND = Identifier.parse("mod-stella-spells:ember_brand");
	private static final Identifier RAPID_FIRE = Identifier.parse("mod-stella-spells:rapid_fire");
	private static final int HITS = 1000;
	private static final long SEED = 20261014L;
	private static final double LUK = 20.0;
	private static final double NEAR = 0.05;
	private static final float HIT = 4.0F;
	private static final int WEAPON_SLOT = 35;
	/** Every other case wants a cast on each hit, so the dice rolls 0 unless a case pins it. */
	private static final DoubleSupplier ALWAYS = () -> 0.0;
	private static final Map<UUID, List<Identifier>> CASTS = new ConcurrentHashMap<>();

	static {
		SpellImbue.setProcRoll(ALWAYS);
		SpellImbue.addRecorder((player, spell, victim) ->
			CASTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add(spell));
	}

	/** How many hits cast each book, and how many cast the first two together. */
	private record Run(Map<Identifier, Integer> procs, int firstAndSecond) {
	}

	@GameTest(maxTicks = 100)
	public void eachBookOfASwordRollsItsOwnTenPercentAtZeroLuk(final GameTestHelper helper) {
		List<Identifier> books = List.of(SPARK_SHOT, FROSTBIND, EMBER_BRAND);
		Run run = thousandHits(helper, books, 0.0);
		double chance = SpellImbue.PROC_BASE_CHANCE;
		LOG.info("[SPELLTEST] test=eachBookOfASwordRollsItsOwnTenPercentAtZeroLuk RESULT hits={} procs={} firstAndSecond={}", HITS, run.procs(), run.firstAndSecond());
		for (Identifier book : books) {
			assertNear(helper, book, run.procs().getOrDefault(book, 0), chance);
		}
		// One shared roll would put the pair together on every proc of either; two rolls do so at the product.
		int together = (int) Math.round(HITS * chance * chance);
		helper.assertTrue(run.firstAndSecond() <= together * 3, "books 1 and 2 cast together on " + run.firstAndSecond() + " hits, so they share a roll");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void atTwentyLukABookCastsNearThirtyPercent(final GameTestHelper helper) {
		Run run = thousandHits(helper, List.of(SPARK_SHOT), LUK);
		double chance = SpellImbue.PROC_BASE_CHANCE + LUK * SpellImbue.PROC_CHANCE_PER_LUK;
		LOG.info("[SPELLTEST] test=atTwentyLukABookCastsNearThirtyPercent RESULT hits={} procs={} chance={}", HITS, run.procs(), chance);
		assertNear(helper, SPARK_SHOT, run.procs().getOrDefault(SPARK_SHOT, 0), chance);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aBowArrowHitRollsTheImbuedBook(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		ItemStack bow = SpellImbue.imbue(new ItemStack(Items.BOW), new ItemStack(SkillSchoolGameTest.book("rapid_fire")));
		helper.assertTrue(!bow.isEmpty(), "the bow refused " + RAPID_FIRE);
		caster.getInventory().setItem(WEAPON_SLOT, bow);
		Mob victim = FighterBooksGameTest.foe(helper, 1.5, 7.5);
		int missed;
		int landed;
		try {
			// A roll of 0.5 is above the chance at 0 LUK and below it at 50 LUK.
			SpellImbue.setProcRoll(() -> 0.5);
			SpellImbue.setLukSource((holder, key) -> 0.0);
			arrowHit(helper, caster, victim, bow);
			missed = casts(caster).size();
			victim.damageCooldownTime = 0;
			SpellImbue.setLukSource((holder, key) -> "luk".equals(key) ? 50.0 : 0.0);
			arrowHit(helper, caster, victim, bow);
			landed = casts(caster).size() - missed;
		} finally {
			SpellImbue.setProcRoll(ALWAYS);
			SpellImbue.setLukSource(null);
			ArcherSkills.clearFor(caster);
		}
		LOG.info("[SPELLTEST] test=aBowArrowHitRollsTheImbuedBook RESULT castsAtZeroLuk={} castsAtFiftyLuk={}", missed, landed);
		helper.assertTrue(missed == 0, "a roll above the chance still cast " + missed + " times from the bow");
		helper.assertTrue(landed == 1, "a roll under the chance cast " + landed + " times from the bow");
		victim.discard();
		helper.succeed();
	}

	@GameTest
	public void aThirdAndAFourthBookImbueAtTheSamePriceAndARepeatDoesNot(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack piece = new ItemStack(Items.IRON_SWORD);
		List<Identifier> books = List.of(SPARK_SHOT, FROSTBIND, EMBER_BRAND, Identifier.parse("mod-stella-spells:static_lash"));
		List<Integer> costs = new ArrayList<>();
		for (Identifier book : books) {
			piece = ImbueExpansionGameTest.anvilResult(helper, player, piece, book);
			costs.add(ImbueExpansionGameTest.lastCost);
		}
		ItemStack repeat = ImbueExpansionGameTest.anvilResult(helper, player, piece, SPARK_SHOT);
		LOG.info("[SPELLTEST] test=aThirdAndAFourthBookImbueAtTheSamePriceAndARepeatDoesNot RESULT costs={} spells={} repeat={}", costs, SpellImbue.spellsOf(piece), repeat);
		helper.assertTrue(SpellImbue.spellsOf(piece).equals(books), "the sword holds " + SpellImbue.spellsOf(piece));
		helper.assertTrue(costs.stream().allMatch(cost -> cost == SpellImbue.IMBUE_XP_COST), "the four imbues cost " + costs);
		helper.assertTrue(repeat.isEmpty(), "a book already on the sword imbued again: " + repeat);
		helper.succeed();
	}

	@GameTest
	public void theImbueTooltipIsARequiresAndDescBlockThenOneLinePerBook(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(List.of(SPARK_SHOT, FROSTBIND, EMBER_BRAND)));
		List<Component> lines = DescriptionSupport.lines(helper, null, sword);
		List<String> text = DescriptionSupport.text(lines);
		String percent = Math.round(SpellImbue.PROC_BASE_CHANCE * 100.0) + "%";
		LOG.info("[SPELLTEST] test=theImbueTooltipIsARequiresAndDescBlockThenOneLinePerBook RESULT lines={}", text);
		helper.assertTrue(DescriptionSupport.hasBlock(lines) && text.stream().anyMatch(line -> line.startsWith("Requires:")), "the tooltip has no Requires and Desc block: " + text);
		helper.assertTrue(DescriptionSupport.blockProblems("imbued sword", lines).isEmpty(), "the block is out of format: " + text);
		helper.assertTrue(DescriptionSupport.tooLong(text).isEmpty(), "lines over " + DescriptionSupport.LIMIT + " characters: " + text);
		helper.assertTrue(String.join(" ", text).contains(percent), "the tooltip does not state the " + percent + " chance: " + text);
		helper.assertTrue(text.stream().filter(line -> line.startsWith("Imbued: ")).count() == 3, "the tooltip does not name each book once: " + text);
		// A Compendium casts on use, never on a hit, so it shows the books and not the hit rule.
		ItemStack compendium = new ItemStack(Spellbooks.COMPENDIUM);
		compendium.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(SPARK_SHOT));
		List<String> stored = DescriptionSupport.text(DescriptionSupport.lines(helper, null, compendium));
		helper.assertTrue(stored.stream().anyMatch(line -> line.startsWith("Imbued: ")) && !String.join(" ", stored).contains("landed hit"),
			"the Compendium tooltip shows the hit rule: " + stored);
		helper.succeed();
	}

	/** The rate must sit within 5 points of the chance: three or more standard deviations at these sizes. */
	private static void assertNear(final GameTestHelper helper, final Identifier book, final int procs, final double chance) {
		double rate = (double) procs / HITS;
		helper.assertTrue(Math.abs(rate - chance) <= NEAR, book.getPath() + " cast on " + procs + " of " + HITS + " hits, not near " + chance);
	}

	private static Run thousandHits(final GameTestHelper helper, final List<Identifier> books, final double luk) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(books));
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		Mob victim = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 4).withNoFreeWill().spawn();
		victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		Map<Identifier, Integer> procs = new HashMap<>();
		int both = 0;
		RandomSource dice = RandomSource.create(SEED);
		try {
			SpellImbue.setProcRoll(dice::nextDouble);
			SpellImbue.setLukSource((holder, key) -> "luk".equals(key) ? luk : 0.0);
			for (int hit = 0; hit < HITS; hit++) {
				// A held-back cooldown, an old effect or a low health would hide a roll that passed.
				SpellImbue.clearCooldowns(player.getUUID());
				victim.removeAllEffects();
				victim.clearFire();
				victim.setHealth(victim.getMaxHealth());
				victim.damageCooldownTime = 0;
				int before = casts(player).size();
				victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
				List<Identifier> cast = new ArrayList<>(casts(player).subList(before, casts(player).size()));
				cast.forEach(spell -> procs.merge(spell, 1, Integer::sum));
				if (books.size() > 1 && cast.contains(books.get(0)) && cast.contains(books.get(1))) {
					both++;
				}
			}
		} finally {
			SpellImbue.setProcRoll(ALWAYS);
			SpellImbue.setLukSource(null);
		}
		victim.discard();
		return new Run(procs, both);
	}

	private static List<Identifier> casts(final ServerPlayer player) {
		return CASTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>());
	}

	private static void arrowHit(final GameTestHelper helper, final ServerPlayer caster, final Mob victim, final ItemStack bow) {
		Arrow arrow = new Arrow(helper.getLevel(), caster, new ItemStack(Items.ARROW), bow);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), ArcherSkills.SHOT_PROBE_DAMAGE);
	}

	/** A caster with an empty pack, placed where the archer cases of this mod place theirs. */
	private static ServerPlayer archer(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.removeAllEffects();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		return caster;
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
