package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Compendium: crafted from a book, filled at the anvil with any number of spell and skill books, and cast
 * whole on use. Bows and crossbows take every book a sword takes, and their arrows cast what they hold.
 */
public class CompendiumGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.compendium");
	private static final Identifier COMPENDIUM = id("compendium");
	private static final Identifier QUICKSILVER = id("quicksilver");
	private static final Identifier IRON_SKIN = id("iron_skin");
	private static final Identifier FROSTBIND = id("frostbind");
	private static final Identifier SPARK_SHOT = id("spark_shot");
	private static final int COMPENDIUM_COOLDOWN_TICKS = 600;

	private static int lastCost;

	@GameTest
	public void aBookLapisAndAmethystCraftOneCompendium(final GameTestHelper helper) {
		ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);
		ItemStack amethyst = new ItemStack(Items.AMETHYST_SHARD);
		CraftingInput input = CraftingInput.of(3, 3, List.of(
			lapis, amethyst, lapis,
			amethyst, new ItemStack(Items.BOOK), amethyst,
			lapis, amethyst, lapis));
		ItemStack result = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		LOG.info("[COMPENDIUMTEST] test=aBookLapisAndAmethystCraftOneCompendium RESULT result={}", result);
		helper.assertTrue(result.getItem() == compendium() && result.getCount() == 1, "the recipe made " + result);
		helper.succeed();
	}

	@GameTest
	public void theAnvilFillsACompendiumBookByBookAndTakesNoRepeat(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		ItemStack empty = new ItemStack(compendium());
		ItemStack one = anvil(helper, player, empty, new ItemStack(item(QUICKSILVER)));
		int firstCost = lastCost;
		ItemStack two = anvil(helper, player, one, new ItemStack(item(IRON_SKIN)));
		int secondCost = lastCost;
		ItemStack three = anvil(helper, player, two, new ItemStack(item(FROSTBIND)));
		ItemStack repeat = anvil(helper, player, three, new ItemStack(item(QUICKSILVER)));
		LOG.info("[COMPENDIUMTEST] test=theAnvilFillsACompendiumBookByBookAndTakesNoRepeat RESULT costs={},{} one={} two={} three={} repeat={}",
			firstCost, secondCost, SpellImbue.spellsOf(one), SpellImbue.spellsOf(two), SpellImbue.spellsOf(three), repeat);
		helper.assertTrue(one.getItem() == compendium() && SpellImbue.spellsOf(one).equals(List.of(QUICKSILVER)), "one book gave " + one);
		helper.assertTrue(SpellImbue.spellsOf(two).equals(List.of(QUICKSILVER, IRON_SKIN)), "a spell and a skill gave " + SpellImbue.spellsOf(two));
		helper.assertTrue(SpellImbue.spellsOf(three).equals(List.of(QUICKSILVER, IRON_SKIN, FROSTBIND)), "a third book gave " + SpellImbue.spellsOf(three));
		helper.assertTrue(repeat.isEmpty(), "the same book twice gave " + repeat);
		helper.assertTrue(firstCost == SpellImbue.IMBUE_XP_COST && secondCost == SpellImbue.IMBUE_XP_COST, "the fills cost " + firstCost + ", " + secondCost);
		helper.succeed();
	}

	@GameTest
	public void aCompendiumCastsEveryStoredSpellAtOnceThenRests(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		ItemStack held = filled(QUICKSILVER, IRON_SKIN, SPARK_SHOT);
		caster.setItemInHand(InteractionHand.MAIN_HAND, held);
		// A book's own cooldown does not stop the Compendium from casting that book.
		caster.getCooldowns().addCooldown(new ItemStack(item(QUICKSILVER)), 100);
		held.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		boolean speed = caster.hasEffect(MobEffects.SPEED);
		boolean resistance = caster.hasEffect(MobEffects.RESISTANCE);
		boolean resting = caster.getCooldowns().isOnCooldown(held);
		LOG.info("[COMPENDIUMTEST] test=aCompendiumCastsEveryStoredSpellAtOnceThenRests RESULT speed={} resistance={} resting={}", speed, resistance, resting);
		helper.assertTrue(speed && resistance, "the Compendium cast speed=" + speed + " resistance=" + resistance);
		helper.assertTrue(resting, "the Compendium took no cooldown");
		for (int i = 0; i < COMPENDIUM_COOLDOWN_TICKS - 1; i++) {
			caster.getCooldowns().tick();
		}
		helper.assertTrue(caster.getCooldowns().isOnCooldown(held), "the cooldown ended before " + COMPENDIUM_COOLDOWN_TICKS + " ticks");
		caster.getCooldowns().tick();
		helper.assertFalse(caster.getCooldowns().isOnCooldown(held), "the cooldown outlasted " + COMPENDIUM_COOLDOWN_TICKS + " ticks");
		helper.succeed();
	}

	@GameTest
	public void anEmptyCompendiumAndOneWithNothingToHitTakeNoCooldown(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		ItemStack empty = new ItemStack(compendium());
		caster.setItemInHand(InteractionHand.MAIN_HAND, empty);
		empty.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		boolean emptyResting = caster.getCooldowns().isOnCooldown(empty);
		// Spark Shot needs a foe on the caster's line; with none, nothing casts.
		ItemStack aimless = filled(SPARK_SHOT);
		caster.setItemInHand(InteractionHand.MAIN_HAND, aimless);
		aimless.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		boolean aimlessResting = caster.getCooldowns().isOnCooldown(aimless);
		LOG.info("[COMPENDIUMTEST] test=anEmptyCompendiumAndOneWithNothingToHitTakeNoCooldown RESULT emptyResting={} aimlessResting={}", emptyResting, aimlessResting);
		helper.assertFalse(emptyResting, "an empty Compendium took a cooldown");
		helper.assertFalse(aimlessResting, "a Compendium that cast nothing took a cooldown");
		helper.succeed();
	}

	@GameTest
	public void aCompendiumTakesNoPlaceOnAWeaponOrArmour(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		ItemStack full = filled(QUICKSILVER, IRON_SKIN);
		List<String> took = new ArrayList<>();
		for (ItemStack piece : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 16))) {
			ItemStack result = anvil(helper, player, piece, full.copy());
			if (!result.isEmpty()) {
				took.add(piece.getItem() + " -> " + result);
			}
		}
		LOG.info("[COMPENDIUMTEST] test=aCompendiumTakesNoPlaceOnAWeaponOrArmour RESULT took={}", took);
		helper.assertTrue(took.isEmpty(), "a Compendium in the book slot changed: " + took);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aBowAndACrossbowTakeEveryBookASwordTakes(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		int swordTook = 0;
		List<String> bowRefused = new ArrayList<>();
		List<String> crossbowRefused = new ArrayList<>();
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			ItemStack book = new ItemStack(item(definition.id()));
			if (anvil(helper, player, new ItemStack(Items.IRON_SWORD), book.copy()).isEmpty()) {
				continue;
			}
			swordTook++;
			if (anvil(helper, player, new ItemStack(Items.BOW), book.copy()).isEmpty()) {
				bowRefused.add(definition.id().getPath());
			}
			if (anvil(helper, player, new ItemStack(Items.CROSSBOW), book.copy()).isEmpty()) {
				crossbowRefused.add(definition.id().getPath());
			}
		}
		LOG.info("[COMPENDIUMTEST] test=aBowAndACrossbowTakeEveryBookASwordTakes RESULT swordTook={} bowRefused={} crossbowRefused={}", swordTook, bowRefused, crossbowRefused);
		helper.assertTrue(swordTook > 40, "a sword took only " + swordTook + " books, so the case proves little");
		helper.assertTrue(bowRefused.isEmpty(), "a bow refused: " + bowRefused);
		helper.assertTrue(crossbowRefused.isEmpty(), "a crossbow refused: " + crossbowRefused);
		helper.succeed();
	}

	@GameTest
	public void anArrowFromAnImbuedBowCastsTheImbueOnHit(final GameTestHelper helper) {
		arrowCasts(helper, Items.BOW, "bow");
	}

	@GameTest
	public void anArrowFromAnImbuedCrossbowCastsTheImbueOnHit(final GameTestHelper helper) {
		arrowCasts(helper, Items.CROSSBOW, "crossbow");
	}

	private static void arrowCasts(final GameTestHelper helper, final Item weapon, final String name) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		ItemStack imbued = anvil(helper, player, new ItemStack(weapon), new ItemStack(item(FROSTBIND)));
		helper.assertFalse(imbued.isEmpty(), "the anvil refused Frostbind on a " + name);
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 4).withNoFreeWill().spawn();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.setHealth(100.0F);
		Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), imbued);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, player), 4.0F);
		boolean slowed = zombie.hasEffect(MobEffects.SLOWNESS);
		LOG.info("[COMPENDIUMTEST] test=anArrowFrom{}CastsTheImbueOnHit RESULT slowed={}", name, slowed);
		zombie.discard();
		helper.assertTrue(slowed, "the arrow from the imbued " + name + " did not cast Frostbind");
		helper.succeed();
	}

	private static ItemStack filled(final Identifier... spells) {
		ItemStack stack = new ItemStack(compendium());
		stack.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(List.of(spells)));
		return stack;
	}

	private static ItemStack anvil(final GameTestHelper helper, final ServerPlayer player, final ItemStack left, final ItemStack right) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(),
			ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
		menu.createResult();
		lastCost = menu.getCost();
		return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
	}

	private static Item compendium() {
		Item item = BuiltInRegistries.ITEM.getValue(COMPENDIUM);
		if (item == Items.AIR) {
			throw new AssertionError("the Compendium is not registered");
		}
		return item;
	}

	private static Item item(final Identifier id) {
		return BuiltInRegistries.ITEM.getValue(id);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}
}
