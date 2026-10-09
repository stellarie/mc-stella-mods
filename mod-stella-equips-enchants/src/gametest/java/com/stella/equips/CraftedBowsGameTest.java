package com.stella.equips;

import com.stella.equips.bows.CraftedBowDef;
import com.stella.equips.bows.CraftedBows;
import com.stella.equips.bows.Signature;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineSpecials;
import com.stella.equips.refine.RefineStation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/**
 * The nine crafted bows and crossbows: recipes, catalog stats, draw and charge time, arrow damage, signature
 * effects and the refine milestone table. The catalog rows below are the plan's table, written out by hand, so a
 * mistyped constant in the mod cannot move its own test.
 */
public class CraftedBowsGameTest {
	private record Row(String id, boolean crossbow, int durability, double time, double damage, Signature signature) {
	}

	private static final List<Row> CATALOG = List.of(
		new Row("hunter_bow", false, 448, 0.95, 1.00, Signature.KEEP),
		new Row("composite_bow", false, 576, 0.85, 1.10, Signature.DRAW),
		new Row("gakkung", false, 400, 0.65, 0.90, Signature.DRAW),
		new Row("great_bow", false, 1200, 1.25, 1.40, Signature.DAMAGE),
		new Row("rudra_bow", false, 2400, 1.15, 1.55, Signature.HEAL),
		new Row("arbalest", true, 700, 1.00, 1.30, Signature.DAMAGE),
		new Row("repeater", true, 560, 0.55, 0.85, Signature.CHARGE),
		new Row("ballista", true, 1300, 1.50, 1.70, Signature.PIERCE),
		new Row("siege_arbalest", true, 2600, 1.35, 1.85, Signature.PIERCE)
	);

	private static final int[] MILESTONES = {7, 10, 12, 15};
	private static final Map<Signature, double[]> TABLE = Map.of(
		Signature.DRAW, new double[] {0.05, 0.10, 0.15, 0.20},
		Signature.CHARGE, new double[] {0.05, 0.10, 0.15, 0.20},
		Signature.DAMAGE, new double[] {0.05, 0.10, 0.15, 0.25},
		Signature.KEEP, new double[] {5, 10, 15, 20},
		Signature.HEAL, new double[] {1, 2, 3, 5},
		Signature.PIERCE, new double[] {0, 1, 1, 2}
	);
	private static final int[] LEVELS = {0, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
	private static final double RATIO_TOLERANCE = 0.05;
	private static final int SAMPLES = 32;
	private static final float CROSSBOW_ARROW_POWER = 3.15F;

	/** Shared roller: a holder's verdict decides, and every call is recorded under the holder's id. */
	private static final Map<UUID, Boolean> KEEP_VERDICTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Double>> KEEP_CALLS = new ConcurrentHashMap<>();

	static {
		CraftedBows.setRoller((holder, random, chancePercent) -> {
			KEEP_CALLS.computeIfAbsent(holder.getUUID(), key -> new CopyOnWriteArrayList<>()).add(chancePercent);
			Boolean verdict = KEEP_VERDICTS.get(holder.getUUID());
			return verdict != null ? verdict : random.nextDouble() * 100.0 < chancePercent;
		});
	}

	private static Row row(final String id) {
		return CATALOG.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
	}

	private static Item item(final String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, id));
	}

	private static ItemStack stack(final String id, final int refine) {
		ItemStack stack = new ItemStack(item(id));
		if (refine > 0) {
			Refine.setLevel(stack, refine);
		}
		return stack;
	}

	private static int tier(final int level) {
		int tier = -1;
		for (int index = 0; index < MILESTONES.length; index++) {
			if (level >= MILESTONES[index]) {
				tier = index;
			}
		}
		return tier;
	}

	private static double expectedBonus(final Signature signature, final int level) {
		int tier = tier(level);
		return tier < 0 ? 0.0 : TABLE.get(signature)[tier];
	}

	private static ServerPlayer archer(final GameTestHelper helper) {
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(4.5, 2.0, 4.5));
		player.setXRot(-90.0F);
		// Slot 9 is outside the hotbar, so putting a weapon in the hand never replaces the arrows.
		player.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
		return player;
	}

	private static int arrowItems(final ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.ARROW)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static List<AbstractArrow> arrows(final ServerPlayer player) {
		return UniqueTestKit.arrowsOf(player.level(), player, player.position());
	}

	/**
	 * Draws a bow for the given ticks and lets go, or charges a crossbow for them. This game version loads a
	 * crossbow in its use tick, once the charge is whole, not at release.
	 */
	private static void draw(final ServerPlayer player, final ItemStack bow, final int ticks) {
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		int remaining = bow.getItem().getUseDuration(bow, player) - ticks;
		if (bow.getItem() instanceof CrossbowItem) {
			bow.getItem().onUseTick(player.level(), player, bow, remaining);
		} else {
			bow.getItem().releaseUsing(bow, player.level(), player, remaining);
		}
	}

	private static void load(final ItemStack crossbow) {
		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
	}

	/** A full-power shot from any of the items, and the one arrow it looses. */
	private static AbstractArrow fullShot(final GameTestHelper helper, final ItemStack weapon) {
		ServerPlayer player = archer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		if (weapon.getItem() instanceof CrossbowItem crossbow) {
			load(weapon);
			crossbow.performShooting(player.level(), player, InteractionHand.MAIN_HAND, weapon, CROSSBOW_ARROW_POWER, 1.0F, null);
		} else {
			weapon.getItem().releaseUsing(weapon, player.level(), player, 0);
		}
		List<AbstractArrow> shot = arrows(player);
		helper.assertTrue(shot.size() == 1, BuiltInRegistries.ITEM.getKey(weapon.getItem()) + " loosed " + shot.size() + " arrows");
		return shot.get(0);
	}

	private static double potential(final AbstractArrow arrow) {
		return RefineEffects.baseDamage(arrow) * arrow.getDeltaMovement().length();
	}

	/** The mean over many shots: a bow aims with a random spread that moves one shot's speed by about 1%. */
	private static double meanPotential(final GameTestHelper helper, final ItemStack weapon, final int level, final boolean flatOut) {
		double total = 0.0;
		for (int shot = 0; shot < SAMPLES; shot++) {
			AbstractArrow arrow = fullShot(helper, weapon.copy());
			total += (RefineEffects.baseDamage(arrow) - (flatOut ? level * Refine.DAMAGE_PER_LEVEL : 0.0)) * arrow.getDeltaMovement().length();
			arrow.discard();
		}
		return total / SAMPLES;
	}

	private static double meanSpeed(final GameTestHelper helper, final ItemStack weapon) {
		double total = 0.0;
		for (int shot = 0; shot < SAMPLES; shot++) {
			AbstractArrow arrow = fullShot(helper, weapon.copy());
			total += arrow.getDeltaMovement().length();
			arrow.discard();
		}
		return total / SAMPLES;
	}

	private static Mob victim(final GameTestHelper helper, final float x) {
		return helper.spawnMob(EntityTypes.ZOMBIE, x, 2.0F, 1.5F).withNoFreeWill().spawn();
	}

	@GameTest
	public void everyItemIsCraftedFromItsRecipe(final GameTestHelper helper) {
		Map<String, String[]> grids = Map.of(
			"hunter_bow", new String[] {"", "rabbit_hide", "", "leather", "bow", "leather", "", "string", ""},
			"composite_bow", new String[] {"", "iron_ingot", "", "string", "bow", "string", "", "iron_ingot", ""},
			"gakkung", new String[] {"", "gold_ingot", "", "bone", "bow", "bone", "", "string", ""},
			"great_bow", new String[] {"", "diamond", "", "string", "bow", "string", "", "diamond", ""},
			"arbalest", new String[] {"", "iron_ingot", "", "tripwire_hook", "crossbow", "tripwire_hook", "", "iron_ingot", ""},
			"repeater", new String[] {"", "copper_ingot", "", "redstone", "crossbow", "redstone", "", "copper_ingot", ""},
			"ballista", new String[] {"", "diamond", "", "piston", "crossbow", "piston", "", "diamond", ""}
		);
		ServerLevel level = helper.getLevel();
		var recipes = level.getServer().getRecipeManager();
		List<String> made = new ArrayList<>();
		for (Map.Entry<String, String[]> entry : grids.entrySet()) {
			List<ItemStack> grid = new ArrayList<>();
			for (String name : entry.getValue()) {
				grid.add(name.isEmpty() ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name))));
			}
			var found = recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), level);
			boolean makes = found.isPresent() && found.get().value() instanceof CraftingRecipe crafting
				&& crafting.assemble(CraftingInput.of(3, 3, grid)).is(item(entry.getKey()));
			helper.assertTrue(makes, entry.getKey() + " is not crafted from its grid " + List.of(entry.getValue()));
			// A stick in the centre is not a bow, so the same grid makes nothing.
			List<ItemStack> wrong = new ArrayList<>(grid);
			wrong.set(4, new ItemStack(Items.STICK));
			var refused = recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, wrong), level);
			helper.assertTrue(refused.isEmpty() || !((CraftingRecipe) refused.get().value()).assemble(CraftingInput.of(3, 3, wrong)).is(item(entry.getKey())),
				entry.getKey() + " is also crafted without its base");
			made.add(entry.getKey());
		}
		Map<String, String[]> smithing = Map.of(
			"rudra_bow", new String[] {"great_bow"},
			"siege_arbalest", new String[] {"ballista"}
		);
		for (Map.Entry<String, String[]> entry : smithing.entrySet()) {
			ItemStack template = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
			ItemStack base = new ItemStack(item(entry.getValue()[0]));
			ItemStack ingot = new ItemStack(Items.NETHERITE_INGOT);
			var found = recipes.getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(template, base, ingot), level);
			boolean makes = found.isPresent() && found.get().value() instanceof SmithingTransformRecipe smith
				&& smith.assemble(new SmithingRecipeInput(template, base, ingot)).is(item(entry.getKey()));
			helper.assertTrue(makes, entry.getKey() + " is not smithed from " + entry.getValue()[0]);
			ItemStack vanillaBase = new ItemStack(entry.getKey().equals("rudra_bow") ? Items.BOW : Items.CROSSBOW);
			var vanilla = recipes.getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(template, vanillaBase, ingot), level);
			helper.assertTrue(vanilla.isEmpty() || !((SmithingTransformRecipe) vanilla.get().value())
				.assemble(new SmithingRecipeInput(template, vanillaBase, ingot)).is(item(entry.getKey())),
				entry.getKey() + " is smithed from a vanilla weapon");
			made.add(entry.getKey());
		}
		UniqueTestKit.result("craftedRecipes", "made=" + made);
		helper.assertTrue(made.size() == CATALOG.size(), "recipes checked: " + made);
		helper.succeed();
	}

	@GameTest
	public void everyItemHasItsCatalogStats(final GameTestHelper helper) {
		helper.assertTrue(CraftedBows.all().size() == CATALOG.size(), "the registry holds " + CraftedBows.all().size() + " items");
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			CraftedBowDef def = CraftedBows.byId(row.id());
			helper.assertTrue(def != null, "no crafted item " + row.id());
			ItemStack stack = stack(row.id(), 0);
			seen.add(row.id() + "=" + stack.getMaxDamage());
			helper.assertTrue(stack.getMaxDamage() == row.durability(), row.id() + " durability " + stack.getMaxDamage());
			helper.assertTrue(def.durability() == row.durability(), row.id() + " def durability " + def.durability());
			helper.assertTrue(def.time() == row.time() && def.damage() == row.damage(), row.id() + " def numbers " + def.time() + " " + def.damage());
			helper.assertTrue(def.signature() == row.signature(), row.id() + " signature " + def.signature());
			helper.assertTrue(CraftedBows.def(stack) == def, row.id() + " is not found from its stack");
			helper.assertTrue(stack.getItem() instanceof CrossbowItem == row.crossbow(), row.id() + " has the wrong kind");
			helper.assertTrue(stack.getOrDefault(DataComponents.ENCHANTABLE, null) != null, row.id() + " cannot be enchanted");
			helper.assertTrue(stack.get(DataComponents.REPAIRABLE) != null, row.id() + " has no repair material");
		}
		UniqueTestKit.result("craftedStats", "durability=" + seen);
		helper.succeed();
	}

	@GameTest
	public void everyItemIsAProjectileWeapon(final GameTestHelper helper) {
		for (Row row : CATALOG) {
			Item item = item(row.id());
			helper.assertTrue(item instanceof ProjectileWeaponItem, row.id() + " is not a ProjectileWeaponItem");
			helper.assertTrue(row.crossbow() ? item instanceof CrossbowItem : item instanceof BowItem, row.id() + " has the wrong class");
			helper.assertTrue(Refine.isRanged(new ItemStack(item)), row.id() + " is not ranged for refine");
		}
		helper.succeed();
	}

	@GameTest
	public void aFullDrawTakesTheCatalogTicks(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			if (row.crossbow()) {
				continue;
			}
			ItemStack bow = stack(row.id(), 0);
			int ticks = CraftedBows.drawTicks(bow);
			helper.assertTrue(ticks == (int) Math.round(20 * row.time()), row.id() + " draws in " + ticks + " ticks, catalog " + Math.round(20 * row.time()));
			ServerPlayer early = archer(helper);
			draw(early, stack(row.id(), 0), ticks - 1);
			ServerPlayer exact = archer(helper);
			draw(exact, stack(row.id(), 0), ticks);
			ServerPlayer vanilla = archer(helper);
			draw(vanilla, new ItemStack(Items.BOW), ticks);
			boolean earlyFull = !arrows(early).isEmpty() && arrows(early).get(0).isCritArrow();
			boolean exactFull = !arrows(exact).isEmpty() && arrows(exact).get(0).isCritArrow();
			boolean vanillaFull = !arrows(vanilla).isEmpty() && arrows(vanilla).get(0).isCritArrow();
			seen.add(row.id() + ":" + ticks + " early=" + earlyFull + " exact=" + exactFull + " vanilla=" + vanillaFull);
			helper.assertTrue(!arrows(early).isEmpty() && !earlyFull, row.id() + " fired full power one tick early");
			helper.assertTrue(exactFull, row.id() + " is not at full power after its " + ticks + " draw ticks");
			helper.assertTrue(vanillaFull == (ticks >= 20), "the vanilla bow at " + ticks + " ticks is full: " + vanillaFull);
		}
		UniqueTestKit.result("craftedDraw", String.join(" | ", seen));
		helper.succeed();
	}

	@GameTest
	public void aFullChargeTakesTheCatalogTicks(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			if (!row.crossbow()) {
				continue;
			}
			ItemStack template = stack(row.id(), 0);
			int ticks = CraftedBows.drawTicks(template);
			helper.assertTrue(ticks == (int) Math.round(25 * row.time()), row.id() + " charges in " + ticks + " ticks, catalog " + Math.round(25 * row.time()));
			ServerPlayer early = archer(helper);
			ItemStack earlyBow = stack(row.id(), 0);
			draw(early, earlyBow, ticks - 1);
			ServerPlayer exact = archer(helper);
			ItemStack exactBow = stack(row.id(), 0);
			draw(exact, exactBow, ticks);
			ServerPlayer vanilla = archer(helper);
			ItemStack vanillaBow = new ItemStack(Items.CROSSBOW);
			draw(vanilla, vanillaBow, ticks);
			seen.add(row.id() + ":" + ticks + " early=" + CrossbowItem.isCharged(earlyBow) + " exact=" + CrossbowItem.isCharged(exactBow)
				+ " vanilla=" + CrossbowItem.isCharged(vanillaBow));
			helper.assertTrue(!CrossbowItem.isCharged(earlyBow), row.id() + " was loaded one tick early");
			helper.assertTrue(CrossbowItem.isCharged(exactBow), row.id() + " is not loaded after its " + ticks + " charge ticks");
			helper.assertTrue(CrossbowItem.isCharged(vanillaBow) == (ticks >= 25), "the vanilla crossbow at " + ticks + " ticks is loaded: " + CrossbowItem.isCharged(vanillaBow));
		}
		UniqueTestKit.result("craftedCharge", String.join(" | ", seen));
		helper.succeed();
	}

	@GameTest
	public void arrowsDealTheCatalogDamageRatio(final GameTestHelper helper) {
		double bowBaseline = meanPotential(helper, new ItemStack(Items.BOW), 0, false);
		double crossbowBaseline = meanPotential(helper, new ItemStack(Items.CROSSBOW), 0, false);
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			double ratio = meanPotential(helper, stack(row.id(), 0), 0, false) / (row.crossbow() ? crossbowBaseline : bowBaseline);
			seen.add(row.id() + "=" + Math.round(ratio * 1000) / 1000.0);
			helper.assertTrue(Math.abs(ratio - row.damage()) <= row.damage() * RATIO_TOLERANCE,
				row.id() + " arrow deals " + ratio + " of a vanilla arrow, catalog " + row.damage());
		}
		UniqueTestKit.result("craftedDamage", "ratios=" + seen);
		helper.succeed();
	}

	@GameTest
	public void greatBowArrowsFlyFasterWithoutHittingHarder(final GameTestHelper helper) {
		double vanilla = meanSpeed(helper, new ItemStack(Items.BOW));
		double great = meanSpeed(helper, stack("great_bow", 0));
		double rudra = meanSpeed(helper, stack("rudra_bow", 0));
		UniqueTestKit.result("craftedSpeed", "vanilla=" + vanilla + " great=" + great + " rudra=" + rudra);
		helper.assertTrue(Math.abs(great / vanilla - CraftedBows.byId("great_bow").speed()) < 0.02, "great bow speed ratio " + great / vanilla);
		helper.assertTrue(Math.abs(CraftedBows.byId("great_bow").speed() - 1.3) < 1e-9, "the catalog arrow speed is 1.3");
		helper.assertTrue(Math.abs(rudra / vanilla - 1.0) < 0.02, "rudra bow speed ratio " + rudra / vanilla);
		helper.succeed();
	}

	@GameTest
	public void signatureValueFollowsTheMilestoneTable(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			double unrefined = CraftedBows.signature(stack(row.id(), 0));
			helper.assertTrue(unrefined == 0.0, row.id() + " unrefined bonus " + unrefined);
			for (int level : LEVELS) {
				double bonus = CraftedBows.signature(stack(row.id(), level));
				double expected = expectedBonus(row.signature(), level);
				helper.assertTrue(Math.abs(bonus - expected) < 1e-9, row.id() + " at +" + level + " has bonus " + bonus + ", expected " + expected);
				if (level == 6) {
					helper.assertTrue(bonus == unrefined, row.id() + " at +6 differs from unrefined: " + bonus);
				}
			}
			seen.add(row.id() + "=" + CraftedBows.signature(stack(row.id(), 7)) + "/" + CraftedBows.signature(stack(row.id(), 10)) + "/"
				+ CraftedBows.signature(stack(row.id(), 12)) + "/" + CraftedBows.signature(stack(row.id(), 15)));
		}
		UniqueTestKit.result("craftedMilestones", "+7/+10/+12/+15 " + String.join(" ", seen));
		helper.succeed();
	}

	@GameTest
	public void drawAndChargeTimeShrinkWithTheMilestones(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			if (row.signature() != Signature.DRAW && row.signature() != Signature.CHARGE) {
				continue;
			}
			double base = row.crossbow() ? 25 : 20;
			int previous = Integer.MAX_VALUE;
			for (int level : new int[] {0, 6, 7, 10, 12, 15}) {
				int ticks = CraftedBows.drawTicks(stack(row.id(), level));
				int expected = (int) Math.round(base * row.time() * (1.0 - expectedBonus(row.signature(), level)));
				helper.assertTrue(ticks == expected, row.id() + " at +" + level + " takes " + ticks + " ticks, expected " + expected);
				helper.assertTrue(ticks <= previous, row.id() + " got slower at +" + level);
				previous = ticks;
				seen.add(row.id() + "+" + level + "=" + ticks);
			}
		}
		UniqueTestKit.result("craftedTime", String.join(" ", seen));
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void damageMilestonesRaiseTheArrow(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (String id : List.of("great_bow", "arbalest")) {
			// The generic refine adds its flat damage a level on top, so it is taken out before the signature is compared.
			double flat = meanPotential(helper, stack(id, 0), 0, true);
			for (int level : new int[] {6, 7, 10, 12, 15}) {
				double ratio = meanPotential(helper, stack(id, level), level, true) / flat;
				double expected = 1.0 + expectedBonus(Signature.DAMAGE, level);
				seen.add(id + "+" + level + "=" + Math.round(ratio * 1000) / 1000.0);
				helper.assertTrue(Math.abs(ratio - expected) < 0.01, id + " at +" + level + " arrow scale " + ratio + ", expected " + expected);
			}
		}
		UniqueTestKit.result("craftedDamageMilestones", String.join(" ", seen));
		helper.succeed();
	}

	@GameTest
	public void aPassingKeepRollSpendsNoArrow(final GameTestHelper helper) {
		ServerPlayer player = archer(helper);
		KEEP_VERDICTS.put(player.getUUID(), true);
		int before = arrowItems(player);
		draw(player, stack("hunter_bow", 0), 72000);
		List<AbstractArrow> shot = arrows(player);
		UniqueTestKit.result("hunterKeep", "before=" + before + " after=" + arrowItems(player) + " arrows=" + shot.size());
		helper.assertTrue(shot.size() == 1, "the shot loosed " + shot.size() + " arrows");
		helper.assertTrue(arrowItems(player) == before, "a kept shot still spent " + (before - arrowItems(player)) + " arrows");
		helper.assertTrue(shot.get(0).pickup != AbstractArrow.Pickup.ALLOWED, "the kept arrow can also be picked up");
		helper.succeed();
	}

	@GameTest
	public void aFailingKeepRollSpendsOneArrow(final GameTestHelper helper) {
		ServerPlayer player = archer(helper);
		KEEP_VERDICTS.put(player.getUUID(), false);
		int before = arrowItems(player);
		draw(player, stack("hunter_bow", 0), 72000);
		List<AbstractArrow> shot = arrows(player);
		helper.assertTrue(shot.size() == 1, "the shot loosed " + shot.size() + " arrows");
		helper.assertTrue(before - arrowItems(player) == 1, "the shot spent " + (before - arrowItems(player)) + " arrows");
		helper.assertTrue(shot.get(0).pickup == AbstractArrow.Pickup.ALLOWED, "a spent arrow cannot be picked up");
		helper.succeed();
	}

	@GameTest
	public void keepChanceFollowsTheMilestones(final GameTestHelper helper) {
		List<Double> seen = new ArrayList<>();
		for (int level : LEVELS) {
			ServerPlayer player = archer(helper);
			KEEP_VERDICTS.put(player.getUUID(), false);
			draw(player, stack("hunter_bow", level), 72000);
			List<Double> calls = KEEP_CALLS.getOrDefault(player.getUUID(), List.of());
			helper.assertTrue(calls.size() == 1, "+" + level + " rolled " + calls.size() + " times");
			double expected = 20.0 + expectedBonus(Signature.KEEP, level);
			seen.add(calls.get(0));
			helper.assertTrue(Math.abs(calls.get(0) - expected) < 1e-9, "+" + level + " rolled at " + calls.get(0) + ", expected " + expected);
		}
		UniqueTestKit.result("hunterKeepChance", "chances=" + seen);
		helper.succeed();
	}

	@GameTest
	public void otherBowsNeverRollTheKeepChance(final GameTestHelper helper) {
		ServerPlayer player = archer(helper);
		int before = arrowItems(player);
		draw(player, stack("composite_bow", 15), 72000);
		helper.assertTrue(!KEEP_CALLS.containsKey(player.getUUID()), "a composite bow rolled the keep chance");
		helper.assertTrue(before - arrowItems(player) == 1, "the shot spent " + (before - arrowItems(player)) + " arrows");
		helper.succeed();
	}

	@GameTest
	public void infinityStillWorksOnACraftedBow(final GameTestHelper helper) {
		ServerPlayer player = archer(helper);
		KEEP_VERDICTS.put(player.getUUID(), false);
		ItemStack bow = stack("hunter_bow", 0);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(UniqueTestKit.enchantment(helper.getLevel(), Enchantments.INFINITY), 1);
		EnchantmentHelper.setEnchantments(bow, mutable.toImmutable());
		helper.assertTrue(UniqueTestKit.enchantment(helper.getLevel(), Enchantments.INFINITY).value().isSupportedItem(bow), "Infinity does not support a crafted bow");
		int before = arrowItems(player);
		draw(player, bow, 72000);
		helper.assertTrue(arrows(player).size() == 1, "the shot loosed " + arrows(player).size() + " arrows");
		helper.assertTrue(arrowItems(player) == before, "Infinity still spent " + (before - arrowItems(player)) + " arrows");
		helper.succeed();
	}

	@GameTest
	public void multishotStillWorksOnACraftedCrossbow(final GameTestHelper helper) {
		ServerPlayer player = archer(helper);
		ItemStack crossbow = stack("arbalest", 0);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(UniqueTestKit.enchantment(helper.getLevel(), Enchantments.MULTISHOT), 1);
		EnchantmentHelper.setEnchantments(crossbow, mutable.toImmutable());
		helper.assertTrue(UniqueTestKit.enchantment(helper.getLevel(), Enchantments.MULTISHOT).value().isSupportedItem(crossbow), "Multishot does not support a crafted crossbow");
		draw(player, crossbow, CraftedBows.drawTicks(crossbow));
		ChargedProjectiles loaded = crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
		helper.assertTrue(CrossbowItem.isCharged(crossbow) && loaded.size() == 3, "Multishot loaded " + loaded.size() + " bolts");
		((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, CROSSBOW_ARROW_POWER, 1.0F, null);
		helper.assertTrue(arrows(player).size() == 3, "Multishot loosed " + arrows(player).size() + " bolts");
		helper.succeed();
	}

	@GameTest
	public void aCraftedCrossbowLoadsAndFires(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			if (!row.crossbow()) {
				continue;
			}
			ServerPlayer player = archer(helper);
			ItemStack crossbow = stack(row.id(), 0);
			int before = arrowItems(player);
			draw(player, crossbow, CraftedBows.drawTicks(crossbow));
			helper.assertTrue(CrossbowItem.isCharged(crossbow), row.id() + " did not load");
			helper.assertTrue(before - arrowItems(player) == 1, row.id() + " spent " + (before - arrowItems(player)) + " arrows to load");
			((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, CROSSBOW_ARROW_POWER, 1.0F, null);
			helper.assertTrue(arrows(player).size() == 1, row.id() + " loosed " + arrows(player).size() + " bolts");
			helper.assertTrue(!CrossbowItem.isCharged(crossbow), row.id() + " stayed loaded after the shot");
			helper.assertTrue(crossbow.getDamageValue() == 1, row.id() + " took " + crossbow.getDamageValue() + " damage from one shot");
			seen.add(row.id());
		}
		UniqueTestKit.result("craftedCrossbowFlow", "loaded and fired " + seen);
		helper.succeed();
	}

	@GameTest
	public void pierceCountMatchesTheCatalog(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		int[] base = {2, 3};
		String[] ids = {"ballista", "siege_arbalest"};
		for (int index = 0; index < ids.length; index++) {
			for (int level : LEVELS) {
				ItemStack crossbow = stack(ids[index], level);
				AbstractArrow arrow = fullShot(helper, crossbow);
				int generic = RefineSpecials.has(crossbow, RefineSpecials.Special.PIERCING) ? 1 : 0;
				int expected = base[index] + (int) expectedBonus(Signature.PIERCE, level) + generic;
				seen.add(ids[index] + "+" + level + "=" + arrow.getPierceLevel());
				helper.assertTrue(arrow.getPierceLevel() == expected, ids[index] + " at +" + level + " bolt pierces " + arrow.getPierceLevel() + ", expected " + expected);
				helper.assertTrue(CraftedBows.pierce(crossbow) == base[index] + (int) expectedBonus(Signature.PIERCE, level), ids[index] + " pierce accessor at +" + level);
			}
		}
		helper.assertTrue(fullShot(helper, stack("arbalest", 0)).getPierceLevel() == 0, "an arbalest bolt pierces");
		UniqueTestKit.result("craftedPierce", String.join(" ", seen));
		helper.succeed();
	}

	@GameTest
	public void aPiercingEnchantmentStacksWithThePierceTrait(final GameTestHelper helper) {
		ItemStack crossbow = stack("ballista", 0);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(UniqueTestKit.enchantment(helper.getLevel(), Enchantments.PIERCING), 2);
		EnchantmentHelper.setEnchantments(crossbow, mutable.toImmutable());
		AbstractArrow arrow = fullShot(helper, crossbow);
		UniqueTestKit.result("craftedPierceEnchant", "pierce=" + arrow.getPierceLevel());
		helper.assertTrue(arrow.getPierceLevel() == 2 + 2, "Piercing II on a ballista gives " + arrow.getPierceLevel());
		helper.succeed();
	}

	private static float heal(final GameTestHelper helper, final ServerPlayer wielder, final AbstractArrow arrow, final float x, final float amount) {
		Mob target = victim(helper, x);
		float before = wielder.getHealth();
		float targetBefore = target.getHealth();
		DamageSource source = helper.getLevel().damageSources().arrow(arrow, wielder);
		target.hurtServer(helper.getLevel(), source, amount);
		float dealt = targetBefore - target.getHealth();
		helper.assertTrue(dealt > 0.0F, "the arrow hurt nobody");
		return (wielder.getHealth() - before) / dealt;
	}

	@GameTest(maxTicks = 40)
	public void aHitHealsTheWielderForAShareOfTheDamage(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (int level : LEVELS) {
			ServerPlayer wielder = UniqueTestKit.connected(helper, new Vec3(4.5, 2.0, 4.5));
			wielder.setXRot(-90.0F);
			wielder.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
			wielder.setHealth(5.0F);
			ItemStack bow = stack("rudra_bow", level);
			draw(wielder, bow, 72000);
			AbstractArrow arrow = arrows(wielder).get(0);
			float share = heal(helper, wielder, arrow, 1.5F, 10.0F);
			double expected = (5.0 + expectedBonus(Signature.HEAL, level)) / 100.0;
			seen.add("+" + level + "=" + Math.round(share * 1000) / 1000.0);
			helper.assertTrue(Math.abs(share - expected) < 0.002, "rudra bow at +" + level + " healed " + share + " of the damage, expected " + expected);
		}
		UniqueTestKit.result("rudraHeal", "shares " + String.join(" ", seen));
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void otherBowsDoNotHeal(final GameTestHelper helper) {
		ServerPlayer wielder = UniqueTestKit.connected(helper, new Vec3(4.5, 2.0, 4.5));
		wielder.setXRot(-90.0F);
		wielder.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
		wielder.setHealth(5.0F);
		draw(wielder, stack("great_bow", 15), 72000);
		AbstractArrow arrow = arrows(wielder).get(0);
		float share = heal(helper, wielder, arrow, 1.5F, 10.0F);
		helper.assertTrue(share == 0.0F, "a great bow healed " + share + " of the damage");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aVolleyArrowDoesNotHealAgain(final GameTestHelper helper) {
		ServerPlayer wielder = UniqueTestKit.connected(helper, new Vec3(4.5, 2.0, 4.5));
		wielder.setXRot(-90.0F);
		wielder.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
		wielder.setHealth(5.0F);
		draw(wielder, stack("rudra_bow", Refine.ASCENDED_LEVEL), 72000);
		helper.runAfterDelay(6L, () -> {
			List<AbstractArrow> all = arrows(wielder);
			List<AbstractArrow> volley = all.stream().filter(arrow -> arrow.entityTags().contains(RefineEffects.VOLLEY_TAG)).toList();
			List<AbstractArrow> primary = all.stream().filter(arrow -> !arrow.entityTags().contains(RefineEffects.VOLLEY_TAG)).toList();
			helper.assertTrue(volley.size() == 1 && primary.size() == 1, "arrows: " + primary.size() + " primary, " + volley.size() + " volley");
			float primaryShare = heal(helper, wielder, primary.get(0), 1.5F, 10.0F);
			float volleyShare = heal(helper, wielder, volley.get(0), 3.5F, 10.0F);
			UniqueTestKit.result("rudraVolley", "primary=" + primaryShare + " volley=" + volleyShare);
			helper.assertTrue(primaryShare > 0.0F, "the first arrow healed nothing");
			helper.assertTrue(volleyShare == 0.0F, "the volley arrow healed " + volleyShare + " of its damage");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void siegeBoltsKnockTheTargetBack(final GameTestHelper helper) {
		double[] pushed = new double[2];
		String[] ids = {"ballista", "siege_arbalest"};
		for (int index = 0; index < ids.length; index++) {
			ServerPlayer player = archer(helper);
			AbstractArrow arrow = fullShot(helper, stack(ids[index], 0));
			Mob target = victim(helper, 1.5F + index * 3.0F);
			arrow.setPos(target.getX() - 1.0, target.getY() + 1.0, target.getZ());
			arrow.setDeltaMovement(3.0, 0.0, 0.0);
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, player), 4.0F);
			pushed[index] = target.getDeltaMovement().x;
		}
		UniqueTestKit.result("siegeKnockback", "ballista=" + pushed[0] + " siege=" + pushed[1]);
		helper.assertTrue(pushed[1] - pushed[0] > 0.2, "the siege bolt pushed " + pushed[1] + ", the ballista bolt " + pushed[0]);
		helper.succeed();
	}

	@GameTest
	public void theRefineStationAcceptsEveryItem(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			ItemStack piece = stack(row.id(), 0);
			helper.assertTrue(Refine.canRefine(piece), row.id() + " cannot be refined");
			List<RefineSpecials.Special> specials = RefineSpecials.of(stack(row.id(), Refine.MAX_LEVEL), Refine.MAX_LEVEL);
			helper.assertTrue(specials.equals(List.of(RefineSpecials.Special.KEEN, RefineSpecials.Special.PIERCING,
				RefineSpecials.Special.RENDING, RefineSpecials.Special.VOLLEY)), row.id() + " has the specials " + specials);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().add(new ItemStack(Refine.material(piece), 4));
			AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
			player.containerMenu = menu;
			menu.getSlot(0).set(piece);
			RefineStation.Result result = RefineStation.refine(player, menu.containerId);
			ItemStack refined = menu.getSlot(0).getItem();
			seen.add(row.id() + ":" + result + "+" + Refine.level(refined) + " via " + BuiltInRegistries.ITEM.getKey(Refine.material(piece)).getPath());
			helper.assertTrue(result == RefineStation.Result.SUCCESS && Refine.level(refined) == 1, row.id() + " refine left " + result + " +" + Refine.level(refined));
			helper.assertTrue(refined.getMaxDamage() == row.durability() + Refine.DURABILITY_PER_LEVEL, row.id() + " cap after refine " + refined.getMaxDamage());
		}
		UniqueTestKit.result("craftedRefine", String.join(" ", seen));
		helper.succeed();
	}

	@GameTest
	public void theTooltipNamesTheSignatureAtItsLevel(final GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		for (Row row : CATALOG) {
			String plain = CraftedBows.effectLine(stack(row.id(), 0));
			helper.assertTrue(!plain.isBlank(), row.id() + " has no effect line");
			String previous = plain;
			int previousLevel = 0;
			for (int level : MILESTONES) {
				String line = CraftedBows.effectLine(stack(row.id(), level));
				boolean grows = expectedBonus(row.signature(), level) != expectedBonus(row.signature(), previousLevel);
				helper.assertTrue(line.equals(previous) != grows, row.id() + " line at +" + level + (grows ? " did not change: " : " changed: ") + line);
				previous = line;
				previousLevel = level;
			}
			seen.add(row.id() + ": " + plain + " -> " + previous);
		}
		UniqueTestKit.result("craftedTooltip", String.join(" | ", seen));
		helper.succeed();
	}

	private static String resource(final String path) {
		try (java.io.InputStream in = CraftedBowsGameTest.class.getResourceAsStream(path)) {
			return in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			return "";
		}
	}

	@GameTest
	public void everyItemHasAModelATextureAndAName(final GameTestHelper helper) {
		for (Row row : CATALOG) {
			String base = "/assets/" + StellaEquips.MOD_ID + "/";
			String[] textures = row.crossbow()
				? new String[] {"", "_pulling_0", "_pulling_1", "_pulling_2", "_arrow", "_firework"}
				: new String[] {"", "_pulling_0", "_pulling_1", "_pulling_2"};
			helper.assertTrue(CraftedBowsGameTest.class.getResource(base + "items/" + row.id() + ".json") != null, row.id() + " has no item definition");
			for (String suffix : textures) {
				helper.assertTrue(CraftedBowsGameTest.class.getResource(base + "textures/item/" + row.id() + suffix + ".png") != null, row.id() + suffix + " has no texture");
				helper.assertTrue(CraftedBowsGameTest.class.getResource(base + "models/item/" + row.id() + suffix + ".json") != null, row.id() + suffix + " has no model");
			}
			String lang = resource(base + "lang/en_us.json");
			helper.assertTrue(lang.contains("\"item." + StellaEquips.MOD_ID + "." + row.id() + "\""), row.id() + " has no lang name");
		}
		helper.succeed();
	}
}
