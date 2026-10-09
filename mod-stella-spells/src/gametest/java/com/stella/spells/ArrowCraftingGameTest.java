package com.stella.spells;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arrow Crafting and the crafted arrow kinds. A hit is landed by calling the arrow's own {@code onHitEntity},
 * so the damage, the effect and the kind lookup all run the way they do in flight. Two cases fly a real arrow.
 */
public class ArrowCraftingGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.arrowcrafting");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final float ARROW_SPEED = 3.0F;
	/** The catalog's damage ratios against a plain arrow. */
	private static final Map<ArrowKind, Double> CATALOG_RATIO = Map.of(
		ArrowKind.FLINT, 1.20, ArrowKind.IRON, 1.40, ArrowKind.DIAMOND, 1.75, ArrowKind.KEEN, 1.15);
	private static final int KEEN_SAMPLES = 1000;
	private static final Method ON_HIT_ENTITY = hitMethod("onHitEntity", EntityHitResult.class);
	private static final Method ON_HIT_BLOCK = hitMethod("onHitBlock", BlockHitResult.class);

	private static Method hitMethod(final String name, final Class<?> argument) {
		try {
			Method method = AbstractArrow.class.getDeclaredMethod(name, argument);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(maxTicks = 40)
	public void arrowCraftingTurnsEachMaterialIntoEightArrowsOfItsKindAndSpendsOne(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		List<String> report = new ArrayList<>();
		for (ArrowItems.Craft craft : ArrowItems.CRAFTS) {
			caster.getInventory().clearContent();
			caster.getInventory().setItem(12, new ItemStack(craft.material(), 3));
			boolean applied = ArcherSkills.applyArrowCrafting(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
			int arrows = caster.getInventory().countItem(craft.arrow());
			int left = caster.getInventory().countItem(craft.material());
			int allArrows = countAnyArrow(caster);
			report.add(BuiltInRegistries.ITEM.getKey(craft.arrow()).getPath() + "=" + arrows + "/" + left);
			helper.assertTrue(applied, BuiltInRegistries.ITEM.getKey(craft.material()) + " never crafted");
			helper.assertTrue(arrows == ArcherSkills.ARROW_CRAFTING_ARROWS,
				BuiltInRegistries.ITEM.getKey(craft.material()) + " gave " + arrows + " arrows");
			helper.assertTrue(allArrows == arrows, "the craft gave arrows of another kind too: " + allArrows);
			helper.assertTrue(left == 2, BuiltInRegistries.ITEM.getKey(craft.material()) + " left " + left + ", so the cast spent " + (3 - left));
		}
		LOG.info("[BEHAVIOR] test=arrowCraftingTurnsEachMaterialIntoEightArrowsOfItsKindAndSpendsOne RESULT kinds={} report={}",
			ArrowItems.CRAFTS.size(), report);
		helper.assertTrue(ArrowItems.CRAFTS.size() == 10, "the catalog has " + ArrowItems.CRAFTS.size() + " materials, not 10");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void arrowCraftingNeedsNoBowAndCastsThroughTheBookWithACooldown(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.getInventory().setItem(12, new ItemStack(Items.FLINT, 2));
		ItemStack book = new ItemStack(SkillSchoolGameTest.book("arrow_crafting"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		SpellCasting.CastResult result = SpellCasting.cast(caster, SkillSchoolGameTest.book("arrow_crafting"), InteractionHand.MAIN_HAND);
		boolean cooling = caster.getCooldowns().isOnCooldown(caster.getMainHandItem());
		boolean anyBow = caster.getInventory().hasAnyMatching(stack -> stack.getItem() instanceof ProjectileWeaponItem);
		int arrows = caster.getInventory().countItem(ArrowItems.BY_KIND.get(ArrowKind.FLINT));
		LOG.info("[BEHAVIOR] test=arrowCraftingNeedsNoBowAndCastsThroughTheBookWithACooldown RESULT result={} cooling={} bow={} arrows={}",
			result, cooling, anyBow, arrows);
		helper.assertFalse(anyBow, "the case carried a bow, so it proves nothing about needing none");
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "arrow crafting read " + result + " with a material and no bow");
		helper.assertTrue(cooling, "a landed craft started no cooldown");
		helper.assertTrue(arrows == ArcherSkills.ARROW_CRAFTING_ARROWS, "the book gave " + arrows + " flint arrows");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void anAwakenedArrowCraftingGivesTwelve(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.getInventory().setItem(12, new ItemStack(Items.IRON_INGOT, 2));
		boolean applied = ArcherSkills.applyArrowCrafting(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, true));
		int arrows = caster.getInventory().countItem(ArrowItems.BY_KIND.get(ArrowKind.IRON));
		LOG.info("[BEHAVIOR] test=anAwakenedArrowCraftingGivesTwelve RESULT applied={} arrows={} plain={} awakened={}",
			applied, arrows, ArcherSkills.ARROW_CRAFTING_ARROWS, ArcherSkills.AWAKENED_ARROW_CRAFTING_ARROWS);
		helper.assertTrue(applied, "the awakened craft never applied");
		helper.assertTrue(arrows == ArcherSkills.AWAKENED_ARROW_CRAFTING_ARROWS, "the awakened craft gave " + arrows);
		helper.assertTrue(ArcherSkills.AWAKENED_ARROW_CRAFTING_ARROWS == 12 && ArcherSkills.ARROW_CRAFTING_ARROWS == 8,
			"the book is declared as " + ArcherSkills.ARROW_CRAFTING_ARROWS + " and " + ArcherSkills.AWAKENED_ARROW_CRAFTING_ARROWS);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void arrowCraftingTakesTheOffHandMaterialBeforeThePack(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.getInventory().setItem(12, new ItemStack(Items.BONE, 2));
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
		boolean applied = ArcherSkills.applyArrowCrafting(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		int iron = caster.getInventory().countItem(ArrowItems.BY_KIND.get(ArrowKind.IRON));
		LOG.info("[BEHAVIOR] test=arrowCraftingTakesTheOffHandMaterialBeforeThePack RESULT applied={} ironArrows={} offHand={} bone={} plain={}",
			applied, iron, caster.getOffhandItem().getCount(), caster.getInventory().countItem(Items.BONE), caster.getInventory().countItem(Items.ARROW));
		helper.assertTrue(applied && iron == ArcherSkills.ARROW_CRAFTING_ARROWS, "the off-hand iron made " + iron + " iron arrows");
		helper.assertTrue(caster.getOffhandItem().getCount() == 1, "the off-hand stack holds " + caster.getOffhandItem().getCount());
		helper.assertTrue(caster.getInventory().countItem(Items.BONE) == 2, "the pack bone was spent first");
		helper.assertTrue(caster.getInventory().countItem(Items.ARROW) == 0, "the craft made plain arrows from the pack");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void arrowCraftingTakesTheFirstPackMaterialInTableOrder(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		// Gunpowder sits in the earlier slot, flint is earlier in the table: the table wins.
		caster.getInventory().setItem(10, new ItemStack(Items.GUNPOWDER, 2));
		caster.getInventory().setItem(20, new ItemStack(Items.FLINT, 2));
		boolean applied = ArcherSkills.applyArrowCrafting(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		int flint = caster.getInventory().countItem(ArrowItems.BY_KIND.get(ArrowKind.FLINT));
		LOG.info("[BEHAVIOR] test=arrowCraftingTakesTheFirstPackMaterialInTableOrder RESULT applied={} flintArrows={} blastArrows={} gunpowder={} flintLeft={}",
			applied, flint, caster.getInventory().countItem(ArrowItems.BY_KIND.get(ArrowKind.BLAST)),
			caster.getInventory().countItem(Items.GUNPOWDER), caster.getInventory().countItem(Items.FLINT));
		helper.assertTrue(applied && flint == ArcherSkills.ARROW_CRAFTING_ARROWS, "the craft made " + flint + " flint arrows");
		helper.assertTrue(caster.getInventory().countItem(Items.GUNPOWDER) == 2, "the later material was spent");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void arrowCraftingWithNoMaterialRefusesAndSpendsNoCooldown(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.getInventory().setItem(12, new ItemStack(Items.STONE, 5));
		ItemStack book = new ItemStack(SkillSchoolGameTest.book("arrow_crafting"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book);
		SpellCasting.CastResult result = SpellCasting.cast(caster, SkillSchoolGameTest.book("arrow_crafting"), InteractionHand.MAIN_HAND);
		boolean cooling = caster.getCooldowns().isOnCooldown(caster.getMainHandItem());
		int arrows = countAnyArrow(caster);
		LOG.info("[BEHAVIOR] test=arrowCraftingWithNoMaterialRefusesAndSpendsNoCooldown RESULT result={} cooling={} arrows={}", result, cooling, arrows);
		helper.assertTrue(result == SpellCasting.CastResult.NO_TARGET, "a craft with no material read " + result);
		helper.assertFalse(cooling, "a refused craft started the cooldown");
		helper.assertTrue(arrows == 0, "a refused craft gave " + arrows + " arrows");
		helper.assertTrue(caster.getInventory().countItem(Items.STONE) == 5, "a refused craft spent a stone");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void arrowCraftingDropsTheOverflowAtTheCastersFeet(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			caster.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
		}
		caster.getInventory().setItem(12, new ItemStack(Items.BONE, 2));
		boolean applied = ArcherSkills.applyArrowCrafting(caster, null, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		int carried = caster.getInventory().countItem(Items.ARROW);
		int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, caster.getBoundingBox().inflate(3.0),
			entity -> entity.getItem().is(Items.ARROW)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
		LOG.info("[BEHAVIOR] test=arrowCraftingDropsTheOverflowAtTheCastersFeet RESULT applied={} carried={} dropped={}", applied, carried, dropped);
		helper.assertTrue(applied, "the craft refused a full pack");
		helper.assertTrue(carried + dropped == ArcherSkills.ARROW_CRAFTING_ARROWS,
			"the craft kept " + carried + " and dropped " + dropped + " of " + ArcherSkills.ARROW_CRAFTING_ARROWS);
		helper.assertTrue(dropped > 0, "nothing dropped, so the pack was not full");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void everyCraftedArrowIsInTheArrowsTagAndAnArcherWeaponTakesIt(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		List<String> report = new ArrayList<>();
		for (ArrowKind kind : ArrowKind.values()) {
			Item arrow = ArrowItems.BY_KIND.get(kind);
			caster.getInventory().clearContent();
			caster.getInventory().setItem(12, new ItemStack(arrow, 5));
			ItemStack bow = caster.getProjectile(new ItemStack(Items.BOW));
			ItemStack crossbow = caster.getProjectile(new ItemStack(Items.CROSSBOW));
			report.add(kind.path() + ":" + new ItemStack(arrow).is(ItemTags.ARROWS) + "/" + bow.is(arrow) + "/" + crossbow.is(arrow));
			helper.assertTrue(new ItemStack(arrow).is(ItemTags.ARROWS), kind.path() + " is not in #minecraft:arrows");
			helper.assertTrue(arrow instanceof ArrowItem, kind.path() + " is not an arrow item");
			helper.assertTrue(bow.is(arrow), "a bow did not take " + kind.path());
			helper.assertTrue(crossbow.is(arrow), "a crossbow did not take " + kind.path());
		}
		LOG.info("[BEHAVIOR] test=everyCraftedArrowIsInTheArrowsTagAndAnArcherWeaponTakesIt RESULT report={}", report);
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void eachDamageKindDealsItsCatalogRatioAgainstAPlainArrow(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		List<String> report = new ArrayList<>();
		double plain = meanDamage(helper, caster, foe, new ItemStack(Items.ARROW), false, 1);
		double plainCrit = meanDamage(helper, caster, foe, new ItemStack(Items.ARROW), true, KEEN_SAMPLES);
		for (Map.Entry<ArrowKind, Double> entry : CATALOG_RATIO.entrySet()) {
			boolean keen = entry.getKey() == ArrowKind.KEEN;
			ItemStack stack = new ItemStack(ArrowItems.BY_KIND.get(entry.getKey()));
			double dealt = meanDamage(helper, caster, foe, stack, keen, keen ? KEEN_SAMPLES : 1);
			double ratio = dealt / (keen ? plainCrit : plain);
			report.add(entry.getKey().path() + "=" + ratio);
			helper.assertTrue(Math.abs(ratio / entry.getValue() - 1.0) < ArcherSkills.SHOT_RATIO_TOLERANCE,
				entry.getKey().path() + " dealt " + ratio + " of a plain arrow, not near " + entry.getValue());
		}
		LOG.info("[BEHAVIOR] test=eachDamageKindDealsItsCatalogRatioAgainstAPlainArrow RESULT plain={} plainCrit={} ratios={}", plain, plainCrit, report);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aKeenArrowIsAlwaysACriticalArrowAndAPlainOneIsNot(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		AbstractArrow keen = arrowOf(helper, caster, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.KEEN)));
		AbstractArrow plain = arrowOf(helper, caster, new ItemStack(Items.ARROW));
		LOG.info("[BEHAVIOR] test=aKeenArrowIsAlwaysACriticalArrowAndAPlainOneIsNot RESULT keen={} plain={}", keen.isCritArrow(), plain.isCritArrow());
		helper.assertTrue(keen.isCritArrow(), "a keen arrow is not a critical arrow");
		helper.assertFalse(plain.isCritArrow(), "the control arrow is critical, so the case proves nothing");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aDiamondArrowCarriesOneExtraPierceAndOthersCarryNone(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		int diamond = arrowOf(helper, caster, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.DIAMOND))).getPierceLevel();
		int iron = arrowOf(helper, caster, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.IRON))).getPierceLevel();
		int plain = arrowOf(helper, caster, new ItemStack(Items.ARROW)).getPierceLevel();
		LOG.info("[BEHAVIOR] test=aDiamondArrowCarriesOneExtraPierceAndOthersCarryNone RESULT diamond={} iron={} plain={}", diamond, iron, plain);
		helper.assertTrue(diamond == 1, "a diamond arrow pierces " + diamond);
		helper.assertTrue(iron == 0 && plain == 0, "another arrow pierces: iron " + iron + ", plain " + plain);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aDiamondArrowInFlightHurtsTwoFoesInALine(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob first = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 3.0);
		Mob second = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 5.0);
		AbstractArrow arrow = arrowOf(helper, caster, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.DIAMOND)));
		arrow.setPos(caster.getX(), caster.getY() + 1.0, caster.getZ());
		arrow.setNoGravity(true);
		arrow.shoot(0.0, 0.0, 1.0, ARROW_SPEED, 0.0F);
		helper.getLevel().addFreshEntity(arrow);
		helper.runAfterDelay(30L, () -> {
			double firstLost = 1000.0 - first.getHealth();
			double secondLost = 1000.0 - second.getHealth();
			LOG.info("[BEHAVIOR] test=aDiamondArrowInFlightHurtsTwoFoesInALine RESULT firstLost={} secondLost={}", firstLost, secondLost);
			helper.assertTrue(firstLost > 0.0, "the diamond arrow never hurt the first foe");
			helper.assertTrue(secondLost > 0.0, "the diamond arrow never reached the second foe");
			first.discard();
			second.discard();
			arrow.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void aFireArrowSetsTheTargetOnFireForFourSecondsAndAPlainArrowDoesNot(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob burned = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob control = FighterBooksGameTest.foe(helper, CASTER_X + 3.0, CASTER_Z + 4.0);
		land(helper, caster, burned, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.FIRE)), false);
		land(helper, caster, control, new ItemStack(Items.ARROW), false);
		LOG.info("[BEHAVIOR] test=aFireArrowSetsTheTargetOnFireForFourSecondsAndAPlainArrowDoesNot RESULT fireTicks={} control={}",
			burned.getRemainingFireTicks(), control.getRemainingFireTicks());
		helper.assertTrue(burned.getRemainingFireTicks() >= 4 * 20, "the fire arrow left " + burned.getRemainingFireTicks() + " fire ticks");
		helper.assertTrue(control.getRemainingFireTicks() <= 0, "the plain arrow ignited its target");
		burned.discard();
		control.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aFrostArrowSlowsTheTargetTwiceOverForThreeSeconds(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		land(helper, caster, foe, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.FROST)), false);
		MobEffectInstance slow = foe.getEffect(MobEffects.SLOWNESS);
		LOG.info("[BEHAVIOR] test=aFrostArrowSlowsTheTargetTwiceOverForThreeSeconds RESULT slow={}", slow);
		helper.assertTrue(slow != null, "the frost arrow applied no Slowness");
		helper.assertTrue(slow.getAmplifier() == 1, "the Slowness amplifier is " + slow.getAmplifier() + ", not II");
		helper.assertTrue(slow.getDuration() >= 3 * 20 - 2 && slow.getDuration() <= 3 * 20, "the Slowness lasts " + slow.getDuration());
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aVenomArrowPoisonsTheTargetForFourSeconds(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		// A zombie is undead and shrugs off poison, so the target is a creeper.
		Mob foe = livingFoe(helper, CASTER_X, CASTER_Z + 4.0);
		land(helper, caster, foe, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.VENOM)), false);
		MobEffectInstance poison = foe.getEffect(MobEffects.POISON);
		LOG.info("[BEHAVIOR] test=aVenomArrowPoisonsTheTargetForFourSeconds RESULT poison={}", poison);
		helper.assertTrue(poison != null, "the venom arrow applied no Poison");
		helper.assertTrue(poison.getAmplifier() == 0, "the Poison amplifier is " + poison.getAmplifier() + ", not I");
		helper.assertTrue(poison.getDuration() >= 4 * 20 - 2 && poison.getDuration() <= 4 * 20, "the Poison lasts " + poison.getDuration());
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aSpectralArrowKeepsItsVanillaGlowingOnHit(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		land(helper, caster, foe, new ItemStack(Items.SPECTRAL_ARROW), false);
		LOG.info("[BEHAVIOR] test=aSpectralArrowKeepsItsVanillaGlowingOnHit RESULT glowing={}", foe.hasEffect(MobEffects.GLOWING));
		helper.assertTrue(foe.hasEffect(MobEffects.GLOWING), "the spectral arrow left no glowing");
		helper.assertTrue(new ItemStack(Items.SPECTRAL_ARROW).is(ItemTags.ARROWS), "the spectral arrow left #minecraft:arrows");
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aBlastArrowBurstsOnNearbyFoesAndBreaksNoBlock(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob target = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X + 1.5, CASTER_Z + 4.0);
		Mob far = FighterBooksGameTest.foe(helper, CASTER_X + 4.0, CASTER_Z + 4.0);
		BlockPos wall = helper.absolutePos(new BlockPos(1, 1, 6));
		helper.getLevel().setBlock(wall, Blocks.GLASS.defaultBlockState(), 3);
		land(helper, caster, target, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.BLAST)), false);
		double nearLost = 1000.0 - near.getHealth();
		double farLost = 1000.0 - far.getHealth();
		boolean intact = helper.getLevel().getBlockState(wall).is(Blocks.GLASS);
		LOG.info("[BEHAVIOR] test=aBlastArrowBurstsOnNearbyFoesAndBreaksNoBlock RESULT nearLost={} farLost={} glassIntact={} burst={} radius={}",
			nearLost, farLost, intact, ArrowHits.BLAST_DAMAGE, ArrowHits.BLAST_RADIUS);
		helper.assertTrue(nearLost > 0.0, "the burst never reached a foe 1.5 blocks away");
		helper.assertTrue(Math.abs(nearLost - ArrowHits.BLAST_DAMAGE) < 0.05, "the burst dealt " + nearLost + ", not " + ArrowHits.BLAST_DAMAGE);
		helper.assertTrue(farLost == 0.0, "the burst reached a foe 4 blocks away: " + farLost);
		helper.assertTrue(intact, "the burst broke a block");
		target.discard();
		near.discard();
		far.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aBlastArrowThatStrikesABlockBurstsThereAndBreaksNothing(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Mob near = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		BlockPos wall = helper.absolutePos(new BlockPos(1, 1, 6));
		helper.getLevel().setBlock(wall, Blocks.GLASS.defaultBlockState(), 3);
		AbstractArrow arrow = arrowOf(helper, caster, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.BLAST)));
		Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 5)));
		arrow.setPos(at.x, at.y, at.z);
		try {
			ON_HIT_BLOCK.invoke(arrow, new BlockHitResult(at, Direction.NORTH, wall, false));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		double lost = 1000.0 - near.getHealth();
		boolean intact = helper.getLevel().getBlockState(wall).is(Blocks.GLASS);
		LOG.info("[BEHAVIOR] test=aBlastArrowThatStrikesABlockBurstsThereAndBreaksNothing RESULT lost={} glassIntact={}", lost, intact);
		helper.assertTrue(lost > 0.0, "a blast arrow that struck a block hurt a foe 1 block off by nothing");
		helper.assertTrue(intact, "the burst broke the block it struck");
		near.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aFireArrowLoosedFromABowIgnitesTheFoeItReaches(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		caster.getInventory().setItem(12, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.FIRE), 3));
		ItemStack bow = new ItemStack(Items.BOW);
		caster.setItemInHand(InteractionHand.MAIN_HAND, bow);
		bow.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		bow.releaseUsing(helper.getLevel(), caster, bow.getUseDuration(caster) - 20);
		List<AbstractArrow> fired = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, caster.getBoundingBox().inflate(16.0),
			arrow -> arrow.getOwner() == caster);
		boolean origin = !fired.isEmpty() && fired.get(0).getPickupItemStackOrigin().is(ArrowItems.BY_KIND.get(ArrowKind.FIRE));
		helper.runAfterDelay(40L, () -> {
			LOG.info("[BEHAVIOR] test=aFireArrowLoosedFromABowIgnitesTheFoeItReaches RESULT fired={} origin={} fireTicks={} left={}",
				fired.size(), origin, foe.getRemainingFireTicks(), caster.getInventory().countItem(ArrowItems.BY_KIND.get(ArrowKind.FIRE)));
			helper.assertTrue(fired.size() == 1, "the bow loosed " + fired.size() + " arrows");
			helper.assertTrue(origin, "the loosed arrow is not a fire arrow");
			helper.assertTrue(foe.getRemainingFireTicks() > 0, "the foe at the end of the flight is not burning");
			foe.discard();
			fired.forEach(AbstractArrow::discard);
			helper.succeed();
		});
	}

	private static Mob livingFoe(final GameTestHelper helper, final double x, final double z) {
		Mob mob = EntityTypes.CREEPER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a foe");
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		mob.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static int countAnyArrow(final ServerPlayer caster) {
		int count = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.is(ItemTags.ARROWS)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** A fresh arrow entity of this stack's item, owned by the caster, as a bow would make it. */
	private static AbstractArrow arrowOf(final GameTestHelper helper, final ServerPlayer caster, final ItemStack stack) {
		return ((ArrowItem) stack.getItem()).createArrow(helper.getLevel(), stack, caster, new ItemStack(Items.BOW));
	}

	/** Lands the arrow on the foe through the arrow's own hit, at full speed. */
	private static void land(final GameTestHelper helper, final ServerPlayer caster, final Mob foe, final ItemStack stack, final boolean crit) {
		AbstractArrow arrow = arrowOf(helper, caster, stack);
		arrow.setCritArrow(crit || arrow.isCritArrow());
		arrow.setPos(foe.getX(), foe.getY(), foe.getZ());
		arrow.setDeltaMovement(0.0, 0.0, ARROW_SPEED);
		foe.damageCooldownTime = 0;
		try {
			ON_HIT_ENTITY.invoke(arrow, new EntityHitResult(foe));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The mean health a foe loses to this arrow over a number of hits. */
	private static double meanDamage(
		final GameTestHelper helper, final ServerPlayer caster, final Mob foe, final ItemStack stack, final boolean crit, final int samples
	) {
		double total = 0.0;
		for (int sample = 0; sample < samples; sample++) {
			foe.setHealth(1000.0F);
			float before = foe.getHealth();
			land(helper, caster, foe, stack, crit);
			total += before - foe.getHealth();
		}
		return total / samples;
	}
}
