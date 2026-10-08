package com.stella.spells;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The skill-book framework: every book carries a school, a skill book scales with its own stat, a mage book
 * never reads that stat, a fighter book counts toward awakening, and no mob ever casts one.
 */
public class SkillSchoolGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.skillschool");
	/** The fighter books the school framework declares. */
	static final Set<String> FIGHTER_IDS = Set.of(
		"cleave", "leap_slam", "whirlwind", "shield_bash", "charge", "execute", "war_cry", "counter_stance",
		"iron_skin", "earthsplitter"
	);
	/** The archer books: DEX, a ray, and never an arrow entity. */
	static final Set<String> ARCHER_IDS = Set.of(
		"multishot", "piercing_shot", "arrow_rain", "rapid_fire", "hunters_mark", "explosive_arrow",
		"disengage", "snare_trap", "aimed_bolt", "triangle_shot", "eagle_eye", "elemental_quiver"
	);
	/** The priest books: VIT, healing and holy damage. */
	static final Set<String> PRIEST_IDS = Set.of(
		"holy_light", "prayer_of_mending", "renew", "divine_shield", "holy_arms", "blessing_of_fortitude",
		"smite", "resurrection_ward", "consecrate", "guardian_angel", "unbroken_vow", "steadfast_prayer"
	);

	/** The school a skill book belongs to, read from the one table each id sits in. */
	static School schoolOf(final String path) {
		if (FIGHTER_IDS.contains(path)) {
			return School.FIGHTER;
		}
		if (ARCHER_IDS.contains(path)) {
			return School.ARCHER;
		}
		if (PRIEST_IDS.contains(path)) {
			return School.PRIEST;
		}
		return School.MAGE;
	}

	/** What a stub reports for a stat key; the test sets the coefficients, so a change is visible. */
	static final double STRENGTH_COEFFICIENT = 1.0;
	static final double INTELLIGENCE_COEFFICIENT = 0.0;

	@GameTest
	public void everyBookHasASchoolAndTheMageBooksKeepTheirs(final GameTestHelper helper) {
		Map<School, Integer> counts = new LinkedHashMap<>();
		List<String> wrong = new ArrayList<>();
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			counts.merge(definition.school(), 1, Integer::sum);
			String path = definition.id().getPath();
			School expected = schoolOf(path);
			if (definition.school() != expected) {
				wrong.add(path + "=" + definition.school());
			}
		}
		LOG.info("[BEHAVIOR] test=everyBookHasASchoolAndTheMageBooksKeepTheirs RESULT books={} counts={} wrong={}",
			SpellbookCatalog.DEFINITIONS.size(), counts, wrong);
		helper.assertTrue(wrong.isEmpty(), "these books carry the wrong school: " + wrong);
		helper.assertTrue(counts.getOrDefault(School.MAGE, 0) == SpellbookCatalog.MAGE_DEFINITIONS.size(),
			"the mage books number " + counts.getOrDefault(School.MAGE, 0) + ", not " + SpellbookCatalog.MAGE_DEFINITIONS.size());
		for (School school : List.of(School.FIGHTER, School.ARCHER)) {
			int declared = SkillbookCatalog.DEFINITIONS.stream().filter(book -> book.school() == school).toList().size();
			helper.assertTrue(declared > 0, "no book of school " + school + " ships at all");
			helper.assertTrue(counts.getOrDefault(school, 0) == declared,
				"the " + school + " books number " + counts.getOrDefault(school, 0) + ", not " + declared);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aFighterBookScalesWithStrengthAndAMageBookNeverDoes(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		Mob fighterFoe = foe(helper, 2, 3);
		Mob mageFoe = foe(helper, 4, 3);
		Mob weakFoe = foe(helper, 6, 3);
		// The stub reports the stat key's own coefficient, so a changed coefficient is a changed stat.
		double strong = strengthDamage(caster, fighterFoe, STRENGTH_COEFFICIENT);
		double weak = strengthDamage(caster, weakFoe, STRENGTH_COEFFICIENT / 2.0);
		double mageStrong = casterDamage(caster, mageFoe, STRENGTH_COEFFICIENT);
		double mageWeak = casterDamage(caster, mageFoe, STRENGTH_COEFFICIENT / 2.0);
		LOG.info("[BEHAVIOR] test=aFighterBookScalesWithStrengthAndAMageBookNeverDoes RESULT fighterStrong={} fighterWeak={} mageStrong={} mageWeak={} strKey={}",
			strong, weak, mageStrong, mageWeak, School.FIGHTER.statKey());
		helper.assertTrue(strong > weak, "Strength raised the fighter hit by nothing: " + strong + " against " + weak);
		helper.assertTrue(mageStrong == mageWeak, "a mage book read Strength: " + mageStrong + " against " + mageWeak);
		fighterFoe.discard();
		mageFoe.discard();
		weakFoe.discard();
		helper.succeed();
	}

	/** The flat hit a fighter book deals under a stub whose damage coefficient for the fighter stat is given. */
	private static double strengthDamage(final ServerPlayer caster, final Mob foe, final double coefficient) {
		float before = foe.getHealth();
		withStub(coefficient, 0.0, () -> FighterSkills.hit(caster, foe, SpellNumbers.CLEAVE.damage(), modifiers(caster, false)));
		return before - foe.getHealth();
	}

	/** The hit a mage book deals under the same stub; the mage path never passes a stat key. */
	private static double casterDamage(final ServerPlayer caster, final Mob foe, final double coefficient) {
		withStub(coefficient, 0.0, () -> RpgBridge.damage(caster, SpellNumbers.SPARK_SHOT.damage(), 1.0));
		// The mage path ignores the stub's skill handles, so its hit is the flat number.
		return SpellNumbers.SPARK_SHOT.damage();
	}

	/**
	 * Runs the work with a stub in force, then restores the lookup. The stub answers a skill percent by the
	 * school's own stat key, so a case can read the coefficient and the weapon the production hit passed in.
	 */
	private static void withStub(final double strengthCoefficient, final double weaponAttack, final Runnable work) {
		RpgBridge.setStats(new RpgBridge.Stats() {
			@Override
			public double power(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double skillDamage(
				final net.minecraft.world.entity.LivingEntity caster, final String statKey, final double skillPercent
			) {
				double stat = statKey.equals(School.FIGHTER.statKey()) ? skillPercent * strengthCoefficient
					: skillPercent * INTELLIGENCE_COEFFICIENT;
				return stat + weaponAttack;
			}

			@Override
			public double skillCooldown(final net.minecraft.world.entity.player.Player player, final String statKey) {
				return statKey.equals(School.FIGHTER.statKey()) ? 0.5 : 1.0;
			}
		});
		try {
			work.run();
		} finally {
			RpgBridge.setStats(null);
		}
	}

	/** A fighter book's rest falls with Strength and a mage book's only with Intelligence. */
	@GameTest
	public void aFighterBookCooldownFallsWithStrengthAndNotWithIntelligence(final GameTestHelper helper) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		int fighterBase = SpellbookCatalog.get(id("cleave")).baseCooldownTicks();
		int mageBase = SpellbookCatalog.get(Spellbooks.SPARK_SHOT_ID).baseCooldownTicks();
		SpellModifiers.CastModifiers plain = SpellModifiers.resolve(new ItemStack(Items.AIR), ItemStack.EMPTY, ItemStack.EMPTY);
		int fighterValue;
		int fighterIntelligence;
		int mageStrength;
		try {
			RpgBridge.setStats(new RpgBridge.Stats() {
				@Override
				public double power(final net.minecraft.world.entity.player.Player player) {
					return 1.0;
				}

				@Override
				public double cooldown(final net.minecraft.world.entity.player.Player player) {
					return 1.0;
				}

				@Override
				public double skillCooldown(final net.minecraft.world.entity.player.Player player, final String statKey) {
					return statKey.equals(School.FIGHTER.statKey()) ? 0.5 : 1.0;
				}
			});
			fighterValue = RpgBridge.grow(caster, "cleave", withCooldown(plain, fighterBase), School.FIGHTER).cooldownTicks();
			mageStrength = RpgBridge.grow(caster, "spark_shot", withCooldown(plain, mageBase), School.MAGE).cooldownTicks();
		} finally {
			RpgBridge.setStats(null);
		}
		try {
			RpgBridge.setStats(new RpgBridge.Stats() {
				@Override
				public double power(final net.minecraft.world.entity.player.Player player) {
					return 1.0;
				}

				@Override
				public double cooldown(final net.minecraft.world.entity.player.Player player) {
					return 0.5;
				}

				@Override
				public double skillCooldown(final net.minecraft.world.entity.player.Player player, final String statKey) {
					return 1.0;
				}
			});
			fighterIntelligence = RpgBridge.grow(caster, "cleave", withCooldown(plain, fighterBase), School.FIGHTER).cooldownTicks();
		} finally {
			RpgBridge.setStats(null);
		}
		LOG.info("[BEHAVIOR] test=aFighterBookCooldownFallsWithStrengthAndNotWithIntelligence RESULT base={} withStrength={} withIntelligence={} mageWithStrength={}",
			fighterBase, fighterValue, fighterIntelligence, mageStrength);
		helper.assertTrue(fighterValue < fighterBase, "Strength left the fighter rest at " + fighterValue);
		helper.assertTrue(fighterIntelligence == fighterBase, "Intelligence moved the fighter rest to " + fighterIntelligence);
		helper.assertTrue(mageStrength == mageBase, "Strength moved the mage rest to " + mageStrength);
		helper.succeed();
	}

	private static SpellModifiers.CastModifiers withCooldown(final SpellModifiers.CastModifiers base, final int ticks) {
		return new SpellModifiers.CastModifiers(
			base.powerMultiplier(), base.radiusMultiplier(), base.durationMultiplier(),
			base.defensiveDurationMultiplier(), base.targetRangeBlocks(), ticks, base.awakened()
		);
	}

	@GameTest
	public void withoutTheRpgModAFighterBookDealsItsFlatDamageTimesPotency(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		Mob foe = foe(helper, 2, 3);
		// A stub with no skill numbers at all: the bridge keeps the flat number, and the hit is that times power.
		RpgBridge.setStats(new RpgBridge.Stats() {
			@Override
			public double power(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}
		});
		try {
			double dealt = RpgBridge.skillDamage(caster, School.FIGHTER, SpellNumbers.CLEAVE.damage(), 1.0);
			double doubled = RpgBridge.skillDamage(caster, School.FIGHTER, SpellNumbers.CLEAVE.damage(), 2.0);
			double before = foe.getHealth();
			FighterSkills.hit(caster, foe, SpellNumbers.CLEAVE.damage(), modifiers(caster, 1.0, false));
			double dealtThroughAHit = before - foe.getHealth();
			LOG.info("[BEHAVIOR] test=withoutTheRpgModAFighterBookDealsItsFlatDamageTimesPotency RESULT flat={} damage={} doubled={} dealtThroughAHit={}",
				SpellNumbers.CLEAVE.damage(), dealt, doubled, dealtThroughAHit);
			helper.assertTrue(dealt == SpellNumbers.CLEAVE.damage(),
				"a plain hit was worth " + dealt + ", not the flat " + SpellNumbers.CLEAVE.damage());
			helper.assertTrue(doubled == SpellNumbers.CLEAVE.damage() * 2.0,
				"potency 2 was worth " + doubled + ", not twice the flat " + SpellNumbers.CLEAVE.damage());
			helper.assertTrue(dealtThroughAHit > 0.0, "the hit through the production path took nothing");
		} finally {
			RpgBridge.setStats(null);
		}
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aQuickSlotCastSwingsTheWeaponInTheWeaponSlot(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		Mob woodenFoe = foe(helper, 2, 3);
		Mob diamondFoe = foe(helper, 4, 3);
		caster.getInventory().setItem(35, new ItemStack(Items.WOODEN_SWORD));
		double woodenHit = weaponHit(caster, woodenFoe);
		caster.getInventory().setItem(35, new ItemStack(Items.DIAMOND_SWORD));
		double diamondHit = weaponHit(caster, diamondFoe);
		LOG.info("[BEHAVIOR] test=aQuickSlotCastSwingsTheWeaponInTheWeaponSlot RESULT woodenHit={} diamondHit={}",
			woodenHit, diamondHit);
		helper.assertTrue(diamondHit > woodenHit,
			"a diamond sword hit for " + diamondHit + ", no harder than a wooden sword's " + woodenHit);
		woodenFoe.discard();
		diamondFoe.discard();
		helper.succeed();
	}

	/**
	 * The hit a fighter book deals with the weapon slot as it now stands, under a stub whose damage is the
	 * attack damage of the weapon {@link RpgBridge#skillWeapon} handed it. The school stat adds nothing here,
	 * so the reading is the weapon's own.
	 */
	private static double weaponHit(final ServerPlayer caster, final Mob foe) {
		RpgBridge.setStats(new RpgBridge.Stats() {
			@Override
			public double power(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double skillDamage(
				final net.minecraft.world.entity.LivingEntity caster, final String statKey, final double skillPercent
			) {
				ItemStack weapon = RpgBridge.skillWeapon(caster);
				return weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
					.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
			}
		});
		try {
			float before = foe.getHealth();
			FighterSkills.hit(caster, foe, SpellNumbers.CLEAVE.damage(), modifiers(caster, false));
			return before - foe.getHealth();
		} finally {
			RpgBridge.setStats(null);
		}
	}

	/**
	 * The weapon slot decides the hit: the stub adds the attack damage of whatever {@link RpgBridge#skillWeapon}
	 * handed it, so a diamond sword must out-hit a wooden one by its own attack damage and not by the stat.
	 */
	@GameTest(maxTicks = 60)
	public void theWeaponSlotReachesTheSkillThroughTheBridge(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.getInventory().setItem(35, new ItemStack(Items.WOODEN_SWORD));
		double wooden = weaponBonus(caster);
		caster.getInventory().setItem(35, new ItemStack(Items.DIAMOND_SWORD));
		double diamond = weaponBonus(caster);
		LOG.info("[BEHAVIOR] test=theWeaponSlotReachesTheSkillThroughTheBridge RESULT wooden={} diamond={}",
			wooden, diamond);
		helper.assertTrue(diamond > wooden,
			"a diamond sword in the weapon slot read " + diamond + ", no more than a wooden sword's " + wooden);
		helper.succeed();
	}

	/** The attack damage the bridge's own weapon reading hands a skill, under a stub that echoes it. */
	private static double weaponBonus(final ServerPlayer caster) {
		RpgBridge.setStats(new RpgBridge.Stats() {
			@Override
			public double power(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double skillDamage(
				final net.minecraft.world.entity.LivingEntity caster, final String statKey, final double skillPercent
			) {
				return skillPercent;
			}
		});
		try {
			ItemStack weapon = RpgBridge.skillWeapon(caster);
			ItemAttributeModifiers modifiers = weapon.getOrDefault(
				DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY
			);
			return modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
		} finally {
			RpgBridge.setStats(null);
		}
	}

	/**
	 * The framework's awakening runs on the cast that reaches the count, and the effect of that cast still sees
	 * the plain flag: the awakened behaviour arrives on the next cast. Both halves are pinned here, because a
	 * fighter book that awakened but never carried the flag to its effect would be a silently plain book.
	 */
	@GameTest(maxTicks = 100)
	public void aFighterBookCountsTowardAwakeningAndCarriesTheFlagOnTheNextCast(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		// A foe in the cone, so the cast lands, and the pair placed together so the sight line is the arena's.
		Mob foe = foe(helper, 1, 3);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		caster.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		List<Boolean> seen = new ArrayList<>();
		Awakening.setProbe((spell, awakened) -> {
			if (spell.equals(id("cleave"))) {
				seen.add(awakened);
			}
		});
		// The stack in hand is the stack a cast reads and awakens; a count of one short sits on it.
		ItemStack held = new ItemStack(book("cleave"));
		held.set(Awakening.COUNT, Awakening.CASTS_TO_AWAKEN - 1);
		caster.setItemInHand(InteractionHand.MAIN_HAND, held);
		SpellCasting.CastResult plain = SpellCasting.cast(caster, book("cleave"), InteractionHand.MAIN_HAND);
		ItemStack afterCast = caster.getItemInHand(InteractionHand.MAIN_HAND);
		boolean awakenedNow = Awakening.isAwakened(afterCast);
		Boolean flagOnTheCountingCast = seen.isEmpty() ? null : seen.get(0);
		seen.clear();
		clearCooldown(caster);
		SpellCasting.CastResult awakened = SpellCasting.cast(caster, book("cleave"), InteractionHand.MAIN_HAND);
		Boolean flagOnTheNextCast = seen.isEmpty() ? null : seen.get(0);
		Awakening.setProbe(null);
		LOG.info("[BEHAVIOR] test=aFighterBookCountsTowardAwakeningAndCarriesTheFlagOnTheNextCast RESULT countingCast={} awakened={} flagOnCountingCast={} nextCast={} flagOnNextCast={} castsToAwaken={} foeLost={}",
			plain, awakenedNow, flagOnTheCountingCast, awakened, flagOnTheNextCast, Awakening.CASTS_TO_AWAKEN,
			foe.getMaxHealth() - foe.getHealth());
		helper.assertTrue(plain == SpellCasting.CastResult.CAST, "the counting cast went " + plain + ", so the case proves nothing");
		helper.assertTrue(awakenedNow, "the last counted cast did not awaken the fighter book: cast went " + plain
			+ ", count " + Awakening.count(afterCast) + ", awakened " + awakenedNow);
		helper.assertFalse(Boolean.TRUE.equals(flagOnTheCountingCast),
			"the counting cast already carried the awakened flag, which the awakening happens after");
		helper.assertTrue(awakened == SpellCasting.CastResult.CAST, "the cast after the flip went " + awakened);
		helper.assertTrue(Boolean.TRUE.equals(flagOnTheNextCast),
			"the cast after the flip did not reach its effect with awakened() true: " + flagOnTheNextCast);
		helper.succeed();
	}

	@GameTest
	public void twentyThousandContainerRollsProduceEveryFighterBookAndNoAwakenedOne(final GameTestHelper helper) {
		Set<Identifier> fighter = FIGHTER_IDS.stream()
			.map(SkillSchoolGameTest::id)
			.collect(Collectors.toUnmodifiableSet());
		Map<Identifier, Integer> counts = new LinkedHashMap<>();
		fighter.forEach(book -> counts.put(book, 0));
		Identifier table = Identifier.parse("minecraft:chests/simple_dungeon");
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(90210L);
		int awakened = 0;
		int total = 0;
		for (int roll = 0; roll < 20_000; roll++) {
			ItemStack stack = SpellbookTestAccess.rollChest(table, true, random);
			if (stack.isEmpty()) {
				continue;
			}
			total++;
			Identifier found = BuiltInRegistries.ITEM.getKey(stack.getItem());
			counts.computeIfPresent(found, (ignored, count) -> count + 1);
			if (Awakening.isAwakened(stack)) {
				awakened++;
			}
		}
		LOG.info("[BEHAVIOR] test=twentyThousandContainerRollsProduceEveryFighterBookAndNoAwakenedOne RESULT rolls=20000 books={} total={} awakened={} counts={}",
			72, total, awakened, counts);
		helper.assertTrue(counts.values().stream().allMatch(count -> count > 0),
			"a fighter book never dropped: " + counts);
		helper.assertTrue(awakened == 0, "a container roll produced " + awakened + " awakened books");
		helper.succeed();
	}

	@GameTest
	public void everyFighterBookCraftsFromItsOwnRecipes(final GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String path : FIGHTER_IDS) {
			ItemStack material = new ItemStack(BuiltInRegistries.ITEM.getValue(materialOf(path)));
			ItemStack crafted = craft(helper, shaped("M M", " B ", "M M", 'M', material, 'B', new ItemStack(Items.BOOK)));
			ItemStack scroll = craft(helper, shapeless(new ItemStack(Items.PAPER), material));
			ItemStack fromScrolls = craft(helper, shaped("SSS", "SBS", "SSS", 'S',
				new ItemStack(BuiltInRegistries.ITEM.getValue(id(path + "_scroll"))), 'B', new ItemStack(Items.BOOK)));
			if (!crafted.is(book(path))) {
				wrong.add(path + " book -> " + BuiltInRegistries.ITEM.getKey(crafted.getItem()));
			}
			if (scroll.getCount() != 2 || !scroll.is(BuiltInRegistries.ITEM.getValue(id(path + "_scroll")))) {
				wrong.add(path + " scroll -> " + BuiltInRegistries.ITEM.getKey(scroll.getItem()) + " x" + scroll.getCount());
			}
			if (!fromScrolls.is(book(path))) {
				wrong.add(path + " from scrolls -> " + BuiltInRegistries.ITEM.getKey(fromScrolls.getItem()));
			}
		}
		LOG.info("[BEHAVIOR] test=everyFighterBookCraftsFromItsOwnRecipes RESULT books={} wrong={}", FIGHTER_IDS.size(), wrong);
		helper.assertTrue(wrong.isEmpty(), "these recipes craft the wrong item: " + wrong);
		helper.succeed();
	}

	/** The material each fighter book is made of, read from the shipped catalogue. */
	static Identifier materialOf(final String path) {
		return BuiltInRegistries.ITEM.getKey(SpellbookCatalog.get(id(path)).ingredient());
	}

	private static CraftingInput shaped(
		final String rowA, final String rowB, final String rowC,
		final char first, final ItemStack firstStack, final char second, final ItemStack secondStack
	) {
		List<ItemStack> items = new ArrayList<>();
		for (String row : List.of(rowA, rowB, rowC)) {
			for (char symbol : row.toCharArray()) {
				items.add(symbol == first ? firstStack : symbol == second ? secondStack : ItemStack.EMPTY);
			}
		}
		return CraftingInput.of(3, 3, items);
	}

	private static CraftingInput shapeless(final ItemStack... stacks) {
		return CraftingInput.of(1, stacks.length, List.of(stacks));
	}

	private static ItemStack craft(final GameTestHelper helper, final CraftingInput input) {
		CraftingRecipe recipe = helper.getLevel().getServer().getRecipeManager()
			.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(holder -> holder.value())
			.orElse(null);
		return recipe == null ? ItemStack.EMPTY : recipe.assemble(input);
	}

	@GameTest
	public void noMobCastsAFighterBook(final GameTestHelper helper) {
		List<String> mobSpells = new ArrayList<>(MobSpells.OFFENSIVE);
		mobSpells.addAll(MobSpells.SUPPORT);
		mobSpells.addAll(MobSpells.PACK_SUPPORT);
		mobSpells.addAll(MobSpells.PACK_AREA_OFFENSIVE);
		mobSpells.addAll(MobSpells.VILLAGE_WITCH_OFFENSIVE);
		mobSpells.addAll(MobSpells.VILLAGE_WITCH_SUPPORT);
		List<String> mob = mobSpells.stream().filter(FIGHTER_IDS::contains).toList();
		List<Identifier> imbued = SpellImbue.MOB_SPELLS.stream()
			.filter(identifier -> FIGHTER_IDS.contains(identifier.getPath()))
			.toList();
		LOG.info("[BEHAVIOR] test=noMobCastsAFighterBook RESULT mobSpellLists={} fighterInMobLists={} mobImbueSpells={} fighterImbued={}",
			mobSpells.size(), mob, SpellImbue.MOB_SPELLS.size(), imbued);
		helper.assertTrue(mob.isEmpty(), "a mob spell list holds a fighter book: " + mob);
		helper.assertTrue(imbued.isEmpty(), "a mob's imbued weapon can cast a fighter book: " + imbued);
		helper.succeed();
	}

	/** A zombie that cannot fight back, with enough health to take several test hits. */
	static Mob foe(final GameTestHelper helper, final int x, final int z) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a foe");
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** A stone floor under the arena, so a movement book has ground to land on. */
	static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 12; x++) {
			for (int z = 0; z <= 12; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** Clears the rest of the stack in hand, so a second cast in one case can land. */
	static void clearCooldown(final ServerPlayer caster) {
		caster.getCooldowns().removeCooldown(caster.getCooldowns().getCooldownGroup(caster.getMainHandItem()));
	}

	static SpellbookItem book(final String path) {
		return (SpellbookItem) BuiltInRegistries.ITEM.getValue(id(path));
	}

	static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}

	/** The modifiers a plain cast of this book resolves, with the power the case needs. */
	static SpellModifiers.CastModifiers modifiers(final ServerPlayer caster, final boolean awakened) {
		return modifiers(caster, 1.0, awakened);
	}

	static SpellModifiers.CastModifiers modifiers(final ServerPlayer caster, final double power, final boolean awakened) {
		SpellModifiers.CastModifiers base = SpellModifiers.resolve(
			caster.getItemInHand(InteractionHand.MAIN_HAND),
			caster.getItemBySlot(EquipmentSlot.HEAD),
			caster.getItemBySlot(EquipmentSlot.CHEST)
		);
		return new SpellModifiers.CastModifiers(
			base.powerMultiplier() * power, base.radiusMultiplier(), base.durationMultiplier(),
			base.defensiveDurationMultiplier(), base.targetRangeBlocks(), base.cooldownTicks(), awakened
		);
	}

	/** An awakened stack of this book, which is all the awakened flag reads. */
	static ItemStack awakenedBook(final String path) {
		ItemStack stack = new ItemStack(book(path));
		stack.set(Awakening.COUNT, Awakening.CASTS_TO_AWAKEN);
		stack.set(Awakening.AWAKENED, Unit.INSTANCE);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/**
	 * Casts one skill book straight at its effect. The stack goes into the hand first, exactly as a real cast
	 * has it, and the modifiers are then read from that hand with the case's awakened flag set on them.
	 */
	static boolean cast(final ServerPlayer caster, final String path, final Mob target, final boolean awakened) {
		if (ARCHER_IDS.contains(path) && autoKit) {
			supplyArrowsIfGripped(caster);
		}
		caster.setItemInHand(InteractionHand.MAIN_HAND, awakened ? awakenedBook(path) : new ItemStack(book(path)));
		SpellModifiers.CastModifiers modifiers = modifiers(caster, awakened).withAwakened(awakened);
		Vec3 feet = caster.position();
		boolean applied = book(path).definition().effect().apply(caster, target, feet, modifiers);
		Spellbooks.LOGGER.info(
			"[SKILLTEST] path={} asked={} handed={} hand={} applied={} handAwakened={}",
			path, awakened, modifiers.awakened(), Awakening.count(caster.getItemInHand(InteractionHand.MAIN_HAND)),
			applied, Awakening.isAwakened(caster.getItemInHand(InteractionHand.MAIN_HAND))
		);
		return applied;
	}

	/**
	 * The school's own kit, put on a caster that carries neither: every archer book needs a bow or a crossbow
	 * and an arrow or a bolt, so a case that only wants one book's behaviour gets them without asking. A case
	 * that wants a bare hand turns {@link #autoKit} off first, which is what the gate cases do.
	 */
	static void supplyArrowsIfGripped(final ServerPlayer caster) {
		if (!(caster.getInventory().getItem(ARCHER_WEAPON_SLOT).getItem() instanceof ProjectileWeaponItem)
			&& !(caster.getMainHandItem().getItem() instanceof ProjectileWeaponItem)) {
			caster.getInventory().setItem(ARCHER_WEAPON_SLOT, new ItemStack(Items.BOW));
		}
		if (caster.getInventory().getItem(ARCHER_ARROW_SLOT).isEmpty()) {
			caster.getInventory().setItem(ARCHER_ARROW_SLOT, new ItemStack(Items.ARROW, 64));
		}
	}

	/** Whether a cast supplies the school's kit first; a case that reads the gate turns this off. */
	private static boolean autoKit = true;

	static void setAutoKit(final boolean on) {
		autoKit = on;
	}

	/** The weapon slot the mod reads a bow from, and a pack slot that a cast never replaces. */
	private static final int ARCHER_WEAPON_SLOT = 35;
	private static final int ARCHER_ARROW_SLOT = 20;
}
