package com.stella.spells;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refining a spell or skill book: two identical books in an anvil, what each level adds to the book's main
 * stat, what the milestones add, and the tooltip that says so. Every case drives the real anvil or the real cast.
 */
public class BookRefineGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.bookrefine");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final double RATIO_SLACK = 0.01;
	private static final float HIT = 10.0F;
	private static final int TOOLTIP_LIMIT = 24;
	private static final ResourceKey<Enchantment> QUICKENING =
		ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("mod-stella-spells:quickening"));

	static ItemStack refined(final String path, final int level) {
		return BookRefine.withLevel(new ItemStack(SkillSchoolGameTest.book(path)), level);
	}

	static SpellCasting.CastResult cast(final ServerPlayer caster, final ItemStack book) {
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		SkillSchoolGameTest.clearCooldown(caster);
		return SpellCasting.cast(caster, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);
	}

	private static void reset(final Mob foe) {
		foe.setHealth(1000.0F);
		foe.clearFire();
		foe.removeAllEffects();
		foe.damageCooldownTime = 0;
	}

	private static AnvilMenu anvil(final GameTestHelper helper, final ServerPlayer player, final ItemStack left, final ItemStack right) {
		AnvilMenu anvil = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		anvil.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
		anvil.createResult();
		return anvil;
	}

	@GameTest
	public void twoIdenticalBooksRefineInTheAnvilAndTheSecondIsUsedUp(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.setExperienceLevels(20);
		boolean emeraldAnvil = FabricLoader.getInstance().isModLoaded("mod-stella-equips-enchants");
		if (emeraldAnvil) {
			player.getInventory().add(new ItemStack(Items.EMERALD, 64));
		}
		ItemStack left = refined("spark_shot", 0);
		AnvilMenu anvil = anvil(helper, player, left, refined("spark_shot", 0));
		var resultSlot = anvil.getSlot(AnvilMenu.RESULT_SLOT);
		ItemStack result = resultSlot.getItem();
		LOG.info("[BEHAVIOR] test=twoIdenticalBooksRefineInTheAnvilAndTheSecondIsUsedUp RESULT result={} level={} cost={}",
			result, BookRefine.level(result), anvil.getCost());
		helper.assertTrue(result.getItem() == left.getItem() && BookRefine.level(result) == 1, "the anvil made " + result + " at +" + BookRefine.level(result));
		helper.assertTrue(anvil.getCost() == 1, "the first refine costs " + anvil.getCost());
		helper.assertTrue(BookRefine.level(left) == 0, "the left book was changed in place");
		helper.assertTrue(resultSlot.mayPickup(player), "the refined book cannot be taken");
		ItemStack taken = resultSlot.getItem().copy();
		resultSlot.onTake(player, taken);
		helper.assertTrue(anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "the second book was not used up");
		helper.assertTrue(anvil.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty(), "the first book stayed in the anvil");
		if (!emeraldAnvil) {
			helper.assertTrue(player.experienceLevel == 19, "the refine left the player at level " + player.experienceLevel);
		}
		helper.succeed();
	}

	@GameTest
	public void eachStepCostsTheNewLevelAndLevelFifteenIsTheCap(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		ItemStack book = refined("frostbind", 0);
		List<Integer> costs = new ArrayList<>();
		for (int step = 1; step <= BookRefine.MAX_LEVEL; step++) {
			AnvilMenu anvil = anvil(helper, player, book, refined("frostbind", 0));
			ItemStack result = anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
			helper.assertTrue(BookRefine.level(result) == step, "step " + step + " made +" + BookRefine.level(result));
			costs.add(anvil.getCost());
			book = result;
		}
		AnvilMenu capped = anvil(helper, player, book, refined("frostbind", 0));
		LOG.info("[BEHAVIOR] test=eachStepCostsTheNewLevelAndLevelFifteenIsTheCap RESULT costs={} cappedResult={}", costs,
			capped.getSlot(AnvilMenu.RESULT_SLOT).getItem());
		for (int step = 1; step <= BookRefine.MAX_LEVEL; step++) {
			helper.assertTrue(costs.get(step - 1) == step, "the step to +" + step + " costs " + costs.get(step - 1));
		}
		helper.assertTrue(capped.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "a +15 book refined again");
		helper.succeed();
	}

	@GameTest
	public void aDifferentBookGivesNoOutputAndTheRefineKeepsEnchantsAndAwakening(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		AnvilMenu other = anvil(helper, player, refined("spark_shot", 3), refined("frostbind", 0));
		helper.assertTrue(other.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(),
			"two different books made " + other.getSlot(AnvilMenu.RESULT_SLOT).getItem());
		ItemStack scrollSide = anvil(helper, player, refined("spark_shot", 3), new ItemStack(Items.PAPER)).getSlot(AnvilMenu.RESULT_SLOT).getItem();
		helper.assertTrue(scrollSide.isEmpty() || BookRefine.level(scrollSide) == 0, "paper refined a book");

		ItemStack awakened = SkillSchoolGameTest.awakenedBook("spark_shot");
		Holder<Enchantment> quickening = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(QUICKENING);
		awakened.enchant(quickening, 2);
		AnvilMenu same = anvil(helper, player, awakened, refined("spark_shot", 0));
		ItemStack result = same.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		LOG.info("[BEHAVIOR] test=aDifferentBookGivesNoOutputAndTheRefineKeepsEnchantsAndAwakening RESULT level={} awakened={} enchants={}",
			BookRefine.level(result), Awakening.isAwakened(result), EnchantmentHelper.getEnchantmentsForCrafting(result));
		helper.assertTrue(BookRefine.level(result) == 1, "the awakened book refined to +" + BookRefine.level(result));
		helper.assertTrue(Awakening.isAwakened(result), "the refine dropped the awakening");
		helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(result).equals(EnchantmentHelper.getEnchantmentsForCrafting(awakened)),
			"the refine changed the enchantments: " + EnchantmentHelper.getEnchantmentsForCrafting(result));
		helper.assertFalse(SpellImbue.canHold(refined("spark_shot", 0)), "a book can hold a spell, so the anvil would imbue it");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aDamageBookDealsTwoPercentMoreForEachLevel(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 3.0);
		reset(foe);
		cast(caster, refined("spark_shot", 0));
		float plain = 1000.0F - foe.getHealth();
		reset(foe);
		cast(caster, refined("spark_shot", 5));
		float five = 1000.0F - foe.getHealth();
		LOG.info("[BEHAVIOR] test=aDamageBookDealsTwoPercentMoreForEachLevel RESULT plain={} five={} ratio={}", plain, five, five / plain);
		helper.assertTrue(plain > 0.0F, "the plain book dealt nothing");
		helper.assertTrue(Math.abs(five / plain - 1.10) < RATIO_SLACK, "a +5 damage book dealt " + five + " against " + plain);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aHealBookHealsTwoPercentMoreForEachLevel(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.setHealth(100.0F);
		cast(caster, refined("mending_word", 0));
		float plain = caster.getHealth() - 100.0F;
		caster.setHealth(100.0F);
		cast(caster, refined("mending_word", 5));
		float five = caster.getHealth() - 100.0F;
		LOG.info("[BEHAVIOR] test=aHealBookHealsTwoPercentMoreForEachLevel RESULT plain={} five={} ratio={}", plain, five, five / plain);
		helper.assertTrue(plain > 0.0F, "the plain book healed nothing");
		helper.assertTrue(Math.abs(five / plain - 1.10) < RATIO_SLACK, "a +5 heal book healed " + five + " against " + plain);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aResistanceBookCutsDamageTwoPercentPerLevelWhileItsBuffLasts(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		cast(caster, refined("stoneguard", 0));
		float plain = lost(helper, caster);
		caster.removeAllEffects();
		cast(caster, refined("stoneguard", 5));
		float five = lost(helper, caster);
		MobEffectInstance guard = caster.getEffect(SpellStatus.REFINED_GUARD);
		caster.removeEffect(SpellStatus.REFINED_GUARD);
		float unguarded = lost(helper, caster);
		LOG.info("[BEHAVIOR] test=aResistanceBookCutsDamageTwoPercentPerLevelWhileItsBuffLasts RESULT plain={} five={} unguarded={} guard={}",
			plain, five, unguarded, guard);
		helper.assertTrue(plain > 0.0F, "the +0 Stoneguard let no damage through");
		helper.assertTrue(guard != null && guard.getAmplifier() == 4, "the +5 guard reads " + guard);
		helper.assertTrue(Math.abs(five / plain - 0.90) < RATIO_SLACK, "a +5 Stoneguard took " + five + " against " + plain);
		helper.assertTrue(Math.abs(unguarded / plain - 1.0) < RATIO_SLACK, "with the guard gone the hit was " + unguarded + " against " + plain);
		helper.succeed();
	}

	private static float lost(final GameTestHelper helper, final ServerPlayer player) {
		float before = player.getHealth();
		player.damageCooldownTime = 0;
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), HIT);
		return before - player.getHealth();
	}

	@GameTest(maxTicks = 40)
	public void aUtilityBookGivesTwoPercentMatkPerLevelForTenSecondsAfterACast(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		cast(caster, refined("owls_gaze", 0));
		helper.assertFalse(caster.hasEffect(SpellStatus.REFINED_FOCUS), "a +0 book gave the MATK buff");
		caster.removeAllEffects();
		cast(caster, refined("owls_gaze", 5));
		MobEffectInstance focus = caster.getEffect(SpellStatus.REFINED_FOCUS);
		Object share = FabricLoader.getInstance().getObjectShare().get(BookRefine.MATK_SHARE);
		double bonus = share instanceof ToDoubleFunction<?> function ? ((ToDoubleFunction<Object>) function).applyAsDouble(caster) : -1.0;
		LOG.info("[BEHAVIOR] test=aUtilityBookGivesTwoPercentMatkPerLevelForTenSecondsAfterACast RESULT focus={} bonus={} share={}",
			focus, bonus, share != null);
		helper.assertTrue(focus != null && focus.getDuration() == BookRefine.FOCUS_TICKS, "the buff lasts " + (focus == null ? -1 : focus.getDuration()) + " ticks");
		helper.assertTrue(Math.abs(bonus - 0.10) < 1.0e-9, "the shared MATK bonus is " + bonus);
		caster.removeEffect(SpellStatus.REFINED_FOCUS);
		helper.assertTrue(((ToDoubleFunction<Object>) share).applyAsDouble(caster) == 0.0, "the bonus outlived the buff");
		helper.succeed();
	}

	/**
	 * Tagged, not required: the real RPG mod is a separate jar. Run with -PrpgJar=path to read its MATK percent
	 * through its own class. Without the jar this case fails visibly, and the bridge test in the RPG mod covers the
	 * reading with a stand-in.
	 */
	@GameTest(maxTicks = 40, required = false)
	public void aRefinedUtilityCastRaisesTheRealRpgMatkPercentRequiresTheRpgJar(final GameTestHelper helper) throws ReflectiveOperationException {
		Class<?> rpg = null;
		try {
			rpg = Class.forName("com.stella.rpg.RpgSpells");
		} catch (ClassNotFoundException absent) {
			helper.assertTrue(false, "the RPG mod is absent: run with -PrpgJar=<path to the rpg jar>");
		}
		java.lang.reflect.Method matkPercent = rpg.getMethod("matkPercent", net.minecraft.world.entity.LivingEntity.class);
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		double before = (double) matkPercent.invoke(null, caster);
		cast(caster, refined("owls_gaze", 5));
		double during = (double) matkPercent.invoke(null, caster);
		caster.removeEffect(SpellStatus.REFINED_FOCUS);
		double after = (double) matkPercent.invoke(null, caster);
		LOG.info("[BEHAVIOR] test=aRefinedUtilityCastRaisesTheRealRpgMatkPercent RESULT before={} during={} after={}", before, during, after);
		helper.assertTrue(Math.abs(during - before - 0.10) < 1.0e-9, "the real MATK percent went from " + before + " to " + during);
		helper.assertTrue(Math.abs(after - before) < 1.0e-9, "the MATK percent stayed raised after the buff: " + after);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void levelSevenShortensTheCooldownByTenPercent(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		int six = cooldownOf(caster, "mending_word", 6);
		int seven = cooldownOf(caster, "mending_word", 7);
		LOG.info("[BEHAVIOR] test=levelSevenShortensTheCooldownByTenPercent RESULT six={} seven={}", six, seven);
		helper.assertTrue(six == 140, "the +6 cooldown is " + six);
		helper.assertTrue(seven == Math.round(six * 0.90), "the +7 cooldown is " + seven + " against " + six);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void levelTwelveCutsTheCostByFifteenPercent(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		int eleven = cooldownOf(caster, "mending_word", 11);
		int twelve = cooldownOf(caster, "mending_word", 12);
		LOG.info("[BEHAVIOR] test=levelTwelveCutsTheCostByFifteenPercent RESULT eleven={} twelve={}", eleven, twelve);
		helper.assertTrue(twelve == Math.round(eleven * 0.85), "a book with no resource takes the cut as cooldown: " + twelve + " against " + eleven);
		int spentEleven = showerArrowsSpent(helper, 11);
		int spentTwelve = showerArrowsSpent(helper, 12);
		LOG.info("[BEHAVIOR] test=levelTwelveCutsTheCostByFifteenPercent RESULT arrowsEleven={} arrowsTwelve={}", spentEleven, spentTwelve);
		helper.assertTrue(spentEleven == ArcherSkills.ARROW_SHOWER_COST, "Arrow Shower at +11 spent " + spentEleven);
		helper.assertTrue(spentTwelve == Math.round(ArcherSkills.ARROW_SHOWER_COST * 0.85), "Arrow Shower at +12 spent " + spentTwelve);
		helper.succeed();
	}

	private static int cooldownOf(final ServerPlayer caster, final String path, final int level) {
		return SpellModifiers.resolve(refined(path, level), ItemStack.EMPTY, ItemStack.EMPTY).cooldownTicks();
	}

	private static int showerArrowsSpent(final GameTestHelper helper, final int level) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().setItem(35, new ItemStack(Items.BOW));
		caster.getInventory().setItem(20, new ItemStack(Items.ARROW, ArcherSkills.ARROW_SHOWER_COST));
		ItemStack book = refined("arrow_rain", level);
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(book, ItemStack.EMPTY, ItemStack.EMPTY);
		net.minecraft.world.phys.Vec3 ground = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1.0, 4.5));
		boolean applied = ArcherSkills.applyArrowRain(caster, null, ground, modifiers);
		int left = caster.getInventory().getItem(20).getCount();
		ArcherSkills.clearFor(caster);
		helper.assertTrue(applied, "Arrow Shower at +" + level + " never started");
		return ArcherSkills.ARROW_SHOWER_COST - left;
	}

	@GameTest(maxTicks = 40)
	public void levelTenAddsAnEffectForEachKind(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob front = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 3.0);
		// A mage damage book sets what it hits on fire.
		reset(front);
		cast(caster, refined("wardens_roar", 9));
		boolean burnsAtNine = front.getRemainingFireTicks() > 0;
		reset(front);
		cast(caster, refined("wardens_roar", 10));
		boolean burnsAtTen = front.getRemainingFireTicks() > 0;
		// A fighter damage book slows it.
		reset(front);
		cast(caster, refined("cleave", 9));
		boolean slowAtNine = front.hasEffect(MobEffects.SLOWNESS);
		reset(front);
		cast(caster, refined("cleave", 10));
		boolean slowAtTen = front.hasEffect(MobEffects.SLOWNESS);
		// A heal book clears one harmful effect from the one it heals.
		caster.removeAllEffects();
		caster.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
		caster.setHealth(500.0F);
		cast(caster, refined("mending_word", 9));
		boolean poisonAtNine = caster.hasEffect(MobEffects.POISON);
		caster.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
		caster.setHealth(500.0F);
		cast(caster, refined("mending_word", 10));
		boolean poisonAtTen = caster.hasEffect(MobEffects.POISON);
		// A resistance or utility book lasts a quarter longer, the MATK buff included.
		caster.removeAllEffects();
		cast(caster, refined("stoneguard", 9));
		int guardNine = caster.getEffect(MobEffects.RESISTANCE).getDuration();
		caster.removeAllEffects();
		cast(caster, refined("stoneguard", 10));
		int guardTen = caster.getEffect(MobEffects.RESISTANCE).getDuration();
		caster.removeAllEffects();
		cast(caster, refined("owls_gaze", 9));
		int focusNine = caster.getEffect(SpellStatus.REFINED_FOCUS).getDuration();
		caster.removeAllEffects();
		cast(caster, refined("owls_gaze", 10));
		int focusTen = caster.getEffect(SpellStatus.REFINED_FOCUS).getDuration();
		LOG.info("[BEHAVIOR] test=levelTenAddsAnEffectForEachKind RESULT burn={}/{} slow={}/{} poison={}/{} guard={}/{} focus={}/{}",
			burnsAtNine, burnsAtTen, slowAtNine, slowAtTen, poisonAtNine, poisonAtTen, guardNine, guardTen, focusNine, focusTen);
		helper.assertTrue(!burnsAtNine && burnsAtTen, "mage damage books: burn at +9 " + burnsAtNine + ", at +10 " + burnsAtTen);
		helper.assertTrue(!slowAtNine && slowAtTen, "fighter damage books: slow at +9 " + slowAtNine + ", at +10 " + slowAtTen);
		helper.assertTrue(poisonAtNine && !poisonAtTen, "heal books: poison kept at +9 " + poisonAtNine + ", at +10 " + poisonAtTen);
		helper.assertTrue(Math.abs(guardTen / (double) guardNine - 1.25) < RATIO_SLACK, "Stoneguard lasts " + guardTen + " against " + guardNine);
		helper.assertTrue(Math.abs(focusTen / (double) focusNine - 1.25) < RATIO_SLACK, "the MATK buff lasts " + focusTen + " against " + focusNine);
		front.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theBooksThatActLaterScaleTooAimedBoltCounterStanceAndGuardianAngel(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().setItem(35, new ItemStack(Items.BOW));
		caster.getInventory().setItem(20, new ItemStack(Items.ARROW, 64));
		Mob first = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 6.0);
		Mob second = FighterBooksGameTest.foe(helper, CASTER_X + 2.0, CASTER_Z + 6.0);
		cast(caster, refined("aimed_bolt", 0));
		double plainBolt = arrowHit(helper, caster, first);
		cast(caster, refined("aimed_bolt", 5));
		double fiveBolt = arrowHit(helper, caster, second);

		Mob attacker = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.0);
		cast(caster, refined("counter_stance", 0));
		float plainAnswer = answered(helper, caster, attacker);
		cast(caster, refined("counter_stance", 5));
		float fiveAnswer = answered(helper, caster, attacker);

		net.minecraft.world.entity.npc.villager.Villager ally = net.minecraft.world.entity.EntityTypes.VILLAGER.create(helper.getLevel(),
			net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		helper.assertTrue(ally != null, "the level refused a villager");
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(CASTER_X, 1.0, CASTER_Z + 3.0));
		ally.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		ally.setNoAi(true);
		ally.setNoGravity(true);
		helper.getLevel().addFreshEntity(ally);
		float blow = 10.0F;
		cast(caster, refined("guardian_angel", 0));
		float plainShare = blow - PriestSkills.guardianShare(ally, helper.getLevel(), helper.getLevel().damageSources().generic(), blow);
		cast(caster, refined("guardian_angel", 5));
		float fiveShare = blow - PriestSkills.guardianShare(ally, helper.getLevel(), helper.getLevel().damageSources().generic(), blow);
		LOG.info("[BEHAVIOR] test=theBooksThatActLaterScaleToo RESULT bolt={}/{} answer={}/{} share={}/{}", plainBolt, fiveBolt, plainAnswer, fiveAnswer, plainShare, fiveShare);
		helper.assertTrue(plainBolt > 0.0 && Math.abs(fiveBolt / plainBolt - 1.10) < RATIO_SLACK, "a +5 Aimed Bolt hit for " + fiveBolt + " against " + plainBolt);
		helper.assertTrue(plainAnswer > 0.0F && Math.abs(fiveAnswer / plainAnswer - 1.10) < RATIO_SLACK, "a +5 Counter Stance answered for " + fiveAnswer + " against " + plainAnswer);
		helper.assertTrue(plainShare > 0.0F && Math.abs(fiveShare / plainShare - 1.10) < RATIO_SLACK, "a +5 Guardian Angel took " + fiveShare + " against " + plainShare);
		helper.succeed();
	}

	private static double arrowHit(final GameTestHelper helper, final ServerPlayer caster, final Mob victim) {
		net.minecraft.world.entity.projectile.arrow.Arrow arrow = net.minecraft.world.entity.EntityTypes.ARROW.create(helper.getLevel(),
			net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		helper.assertTrue(arrow != null, "the level refused an arrow");
		arrow.setOwner(caster);
		arrow.setPos(caster.getX(), caster.getY() + 1.0, caster.getZ());
		helper.getLevel().addFreshEntity(arrow);
		float before = victim.getHealth();
		victim.damageCooldownTime = 0;
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), ArcherSkills.SHOT_PROBE_DAMAGE);
		return before - victim.getHealth();
	}

	private static float answered(final GameTestHelper helper, final ServerPlayer caster, final Mob attacker) {
		float before = attacker.getHealth();
		attacker.damageCooldownTime = 0;
		caster.damageCooldownTime = 0;
		caster.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 4.0F);
		return before - attacker.getHealth();
	}

	@GameTest(maxTicks = 40)
	public void aDamageBookThatStruckNothingYetLeavesItsFireOnTheNextFoeItHurts(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 6.0);
		net.minecraft.world.phys.Vec3 aim = helper.absoluteVec(new net.minecraft.world.phys.Vec3(CASTER_X, 1.0, CASTER_Z + 6.0));
		reset(foe);
		RefineCast cast = new RefineCast(caster, SkillSchoolGameTest.book("dragonfall").definition(), null, aim, null, List.of(), caster.position(),
			SpellModifiers.resolve(refined("dragonfall", 10), ItemStack.EMPTY, ItemStack.EMPTY));
		BookRefine.Pending pending = BookRefine.begin(caster, cast.definition(), refined("dragonfall", 10));
		BookRefine.finish(pending, cast);
		helper.assertFalse(foe.isOnFire(), "the rider set the foe alight before anything hurt it");
		foe.damageCooldownTime = 0;
		foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(caster, caster), 1.0F);
		boolean burning = foe.getRemainingFireTicks() > 0;
		foe.damageCooldownTime = 0;
		foe.clearFire();
		foe.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(caster, caster), 1.0F);
		LOG.info("[BEHAVIOR] test=aDamageBookThatStruckNothingYetLeavesItsFireOnTheNextFoeItHurts RESULT firstBurns={} secondBurns={}", burning, foe.getRemainingFireTicks() > 0);
		helper.assertTrue(burning, "the first foe the caster hurt did not catch fire");
		helper.assertFalse(foe.getRemainingFireTicks() > 0, "the rider stayed after one foe");
		helper.succeed();
	}

	@GameTest
	public void theTooltipShowsTheLevelTheBonusAndEachUnlockedMilestone(final GameTestHelper helper) {
		List<String> plain = text(SpellTooltip.refineLines(refined("spark_shot", 0)));
		List<String> five = text(SpellTooltip.refineLines(refined("spark_shot", 5)));
		List<String> seven = text(SpellTooltip.refineLines(refined("spark_shot", 7)));
		List<String> fifteen = text(SpellTooltip.refineLines(refined("spark_shot", 15)));
		List<String> heal = text(SpellTooltip.refineLines(refined("mending_word", 5)));
		List<String> guard = text(SpellTooltip.refineLines(refined("stoneguard", 5)));
		List<String> matk = text(SpellTooltip.refineLines(refined("owls_gaze", 5)));
		LOG.info("[BEHAVIOR] test=theTooltipShowsTheLevelTheBonusAndEachUnlockedMilestone RESULT five={} seven={} fifteen={} heal={} guard={} matk={}",
			five, seven, fifteen, heal, guard, matk);
		helper.assertTrue(plain.isEmpty(), "an unrefined book shows refine lines: " + plain);
		helper.assertTrue(String.join(" ", five).contains("Refine +5") && String.join(" ", five).contains("+10% damage"), "the +5 damage lines read " + five);
		helper.assertTrue(!String.join(" ", five).contains("+7"), "the +5 lines show the +7 milestone: " + five);
		helper.assertTrue(String.join(" ", seven).contains("+7"), "the +7 lines hide the +7 milestone: " + seven);
		helper.assertTrue(!String.join(" ", seven).contains("+10:"), "the +7 lines show the +10 milestone: " + seven);
		helper.assertTrue(String.join(" ", fifteen).contains("Chain Spark"), "the +15 lines hide the signature: " + fifteen);
		helper.assertTrue(String.join(" ", fifteen).contains("+7") && String.join(" ", fifteen).contains("+10") && String.join(" ", fifteen).contains("+12"),
			"the +15 lines hide an earlier milestone: " + fifteen);
		helper.assertTrue(String.join(" ", heal).contains("+10% healing"), "the heal lines read " + heal);
		helper.assertTrue(String.join(" ", guard).contains("10%") && String.join(" ", guard).contains("damage taken"), "the resistance lines read " + guard);
		helper.assertTrue(String.join(" ", matk).contains("+10% MATK"), "the utility lines read " + matk);
		for (List<String> lines : List.of(five, seven, fifteen, heal, guard, matk)) {
			for (String line : lines) {
				helper.assertTrue(line.length() <= TOOLTIP_LIMIT, "a refine line is " + line.length() + " characters: " + line);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void anOldEquipsRefineLineIsHiddenAndTheBookReadsItsLevelUnderTheNewRule(final GameTestHelper helper) {
		ServerPlayer viewer = FighterBooksGameTest.caster(helper);
		ItemStack old = new ItemStack(SkillSchoolGameTest.book("spark_shot"));
		net.minecraft.world.item.component.CustomData.update(DataComponents.CUSTOM_DATA, old, tag -> tag.putInt(BookRefine.LEVEL_KEY, 3));
		List<Component> lines = new ArrayList<>(old.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		lines.add(0, Component.literal("Refine +3"));
		old.set(DataComponents.LORE, new ItemLore(lines));
		List<Component> tooltip = old.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), viewer, TooltipFlag.NORMAL);
		SpellTooltip.apply(viewer, old, tooltip);
		List<String> shown = text(tooltip);
		LOG.info("[BEHAVIOR] test=anOldEquipsRefineLineIsHiddenAndTheBookReadsItsLevelUnderTheNewRule RESULT level={} oldLine={}",
			BookRefine.level(old), shown.stream().filter(line -> line.startsWith("Refine +")).toList());
		helper.assertTrue(BookRefine.level(old) == 3, "an old +3 book reads level " + BookRefine.level(old));
		helper.assertTrue(shown.stream().noneMatch(line -> line.startsWith("Refine +")), "the old Refine line stayed: " + shown);
		helper.succeed();
	}

	@GameTest
	public void theMainStatIsTheFirstOfDamageHealResistanceAndOtherwiseMatk(final GameTestHelper helper) {
		Set<String> resistance = new HashSet<>();
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			if (BookRefine.statOf(definition) == BookRefine.Stat.RESISTANCE) {
				resistance.add(definition.id().getPath());
			}
		}
		LOG.info("[BEHAVIOR] test=theMainStatIsTheFirstOfDamageHealResistanceAndOtherwiseMatk RESULT resistance={}", resistance);
		helper.assertTrue(resistance.equals(Set.of("stoneguard", "aegis_of_the_deep", "war_cry", "iron_skin")),
			"the resistance books are " + resistance);
		helper.assertTrue(stat("spark_shot") == BookRefine.Stat.DAMAGE, "spark_shot is " + stat("spark_shot"));
		helper.assertTrue(stat("consecrate") == BookRefine.Stat.DAMAGE, "consecrate has damage and heal, damage first: " + stat("consecrate"));
		helper.assertTrue(stat("sanctuary") == BookRefine.Stat.DAMAGE, "sanctuary is " + stat("sanctuary"));
		helper.assertTrue(stat("mending_word") == BookRefine.Stat.HEAL, "mending_word is " + stat("mending_word"));
		helper.assertTrue(stat("guardian_angel") == BookRefine.Stat.HEAL, "guardian_angel is " + stat("guardian_angel"));
		helper.assertTrue(stat("owls_gaze") == BookRefine.Stat.MATK, "owls_gaze is " + stat("owls_gaze"));
		helper.assertTrue(stat("arrow_rain") == BookRefine.Stat.MATK, "arrow_rain has no number of its own: " + stat("arrow_rain"));
		helper.assertTrue(stat("multishot") == BookRefine.Stat.MATK, "Grappling Hook deals no damage: " + stat("multishot"));
		helper.succeed();
	}

	@GameTest
	public void theShotBooksAndSanctuaryNumbersMatchTheConstantsTheyDescribe(final GameTestHelper helper) {
		double flatPerMultiplier = 100.0 / RpgBridge.SKILL_PERCENT_PER_FLAT;
		helper.assertTrue(Math.abs(SpellNumbers.AIMED_BOLT.damage() - ArcherSkills.AIMED_BOLT_DAMAGE * flatPerMultiplier) < 1.0e-6,
			"Aimed Bolt numbers say " + SpellNumbers.AIMED_BOLT.damage() + " for a shot of " + ArcherSkills.AIMED_BOLT_DAMAGE + " times");
		helper.assertTrue(Math.abs(SpellNumbers.EXPLOSIVE_ARROW.damage() - ArcherSkills.EXPLOSIVE_ARROW_DAMAGE * flatPerMultiplier) < 1.0e-6,
			"Explosive Arrow numbers say " + SpellNumbers.EXPLOSIVE_ARROW.damage() + " for a shot of " + ArcherSkills.EXPLOSIVE_ARROW_DAMAGE + " times");
		helper.assertTrue(SpellNumbers.SANCTUARY.heal() == MoreSpells.SANCTUARY_SELF_HEAL && SpellNumbers.SANCTUARY.allyHeal() == MoreSpells.SANCTUARY_ALLY_HEAL
			&& SpellNumbers.SANCTUARY.heal() > 0.0F, "the Sanctuary heal numbers drifted from the mob constants");
		helper.succeed();
	}

	@GameTest
	public void everyBookHasASignatureWithADistinctShortDescription(final GameTestHelper helper) {
		Set<String> names = new HashSet<>();
		Set<String> texts = new HashSet<>();
		List<String> problems = new ArrayList<>();
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			String path = definition.id().getPath();
			if (!BookRefine.hasSignature(definition)) {
				problems.add(path + " has no signature");
				continue;
			}
			String name = Component.translatable("spellbook.mod-stella-spells.sig." + path).getString();
			String body = Component.translatable("spellbook.mod-stella-spells.sig." + path + ".text").getString();
			if (name.startsWith("spellbook.") || body.startsWith("spellbook.")) {
				problems.add(path + " has no text");
			}
			if (!names.add(name) || !texts.add(body)) {
				problems.add(path + " repeats another book's signature");
			}
			for (String line : text(SpellTooltip.refineLines(refined(path, 15)))) {
				if (line.length() > TOOLTIP_LIMIT) {
					problems.add(path + " line over " + TOOLTIP_LIMIT + ": " + line);
				}
			}
		}
		LOG.info("[BEHAVIOR] test=everyBookHasASignatureWithADistinctShortDescription RESULT books={} signatures={} problems={}",
			SpellbookCatalog.DEFINITIONS.size(), names.size(), problems);
		helper.assertTrue(SpellbookCatalog.DEFINITIONS.size() == 81, "the catalog holds " + SpellbookCatalog.DEFINITIONS.size() + " books");
		helper.assertTrue(problems.isEmpty(), "signature problems: " + problems);
		helper.succeed();
	}

	private static BookRefine.Stat stat(final String path) {
		return BookRefine.statOf(SkillSchoolGameTest.book(path).definition());
	}

	static List<String> text(final List<Component> lines) {
		List<String> out = new ArrayList<>();
		for (Component line : lines) {
			out.add(line.getString());
		}
		return out;
	}
}
