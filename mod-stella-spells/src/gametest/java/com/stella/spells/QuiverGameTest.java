package com.stella.spells;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The quiver: its menu and what it keeps, the ammunition order of a bow and a crossbow, and the arrows an
 * archer skill spends from it. Every count reads both stores, the quiver and the pack.
 */
public class QuiverGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.quiver");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final int WEAPON_SLOT = 35;
	private static final int PACK_SLOT = 12;
	private static final InteractionHand HAND = InteractionHand.MAIN_HAND;

	@GameTest(maxTicks = 40)
	public void aQuiverMenuAcceptsArrowsAndFireworksAndRefusesOtherItems(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack quiver = quiverWith();
		player.setItemInHand(InteractionHand.OFF_HAND, quiver);
		QuiverMenu menu = QuiverMenu.open(1, player.getInventory(), quiver, InteractionHand.OFF_HAND);
		List<ItemStack> accepted = List.of(
			new ItemStack(Items.ARROW, 5), new ItemStack(Items.SPECTRAL_ARROW, 5), new ItemStack(Items.TIPPED_ARROW, 5),
			new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.FIRE), 5), new ItemStack(Items.FIREWORK_ROCKET, 5));
		List<ItemStack> refused = List.of(
			new ItemStack(Items.STONE, 5), new ItemStack(Items.BOW), new ItemStack(Items.STRING, 5),
			new ItemStack(Items.DIAMOND, 5), new ItemStack(Quiver.ITEM));
		List<String> report = new ArrayList<>();
		for (int index = 0; index < accepted.size(); index++) {
			menu.setCarried(accepted.get(index).copy());
			menu.clicked(index, 0, ContainerInput.PICKUP, player);
			boolean in = menu.getSlot(index).getItem().is(accepted.get(index).getItem()) && menu.getCarried().isEmpty();
			report.add(accepted.get(index).getItem() + "=" + in);
			helper.assertTrue(in, "the quiver refused " + accepted.get(index).getItem());
		}
		for (ItemStack stack : refused) {
			menu.setCarried(stack.copy());
			menu.clicked(accepted.size(), 0, ContainerInput.PICKUP, player);
			boolean out = menu.getSlot(accepted.size()).getItem().isEmpty();
			report.add(stack.getItem() + "=" + (out ? "refused" : "TAKEN"));
			helper.assertTrue(out, "the quiver took " + stack.getItem());
			menu.setCarried(ItemStack.EMPTY);
		}
		LOG.info("[BEHAVIOR] test=aQuiverMenuAcceptsArrowsAndFireworksAndRefusesOtherItems RESULT report={}", report);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aQuiverKeepsItsContentsAcrossClosingTheMenuAndASaveAndReload(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack quiver = quiverWith();
		player.setItemInHand(InteractionHand.OFF_HAND, quiver);
		QuiverMenu menu = QuiverMenu.open(1, player.getInventory(), quiver, InteractionHand.OFF_HAND);
		menu.setCarried(new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.IRON), 17));
		menu.clicked(2, 0, ContainerInput.PICKUP, player);
		menu.setCarried(new ItemStack(Items.FIREWORK_ROCKET, 9));
		menu.clicked(7, 0, ContainerInput.PICKUP, player);
		menu.removed(player);
		int ironAfterClose = Quiver.count(quiver, ArrowItems.BY_KIND.get(ArrowKind.IRON));
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
		Tag saved = ItemStack.CODEC.encodeStart(ops, quiver).getOrThrow();
		DataResult<ItemStack> parsed = ItemStack.CODEC.parse(ops, saved);
		ItemStack reloaded = parsed.getOrThrow();
		NonNullList<ItemStack> contents = Quiver.contents(reloaded);
		LOG.info("[BEHAVIOR] test=aQuiverKeepsItsContentsAcrossClosingTheMenuAndASaveAndReload RESULT afterClose={} slot2={} slot7={} reloadedIsQuiver={}",
			ironAfterClose, contents.get(2), contents.get(7), reloaded.getItem() instanceof QuiverItem);
		helper.assertTrue(ironAfterClose == 17, "closing the menu kept " + ironAfterClose + " iron arrows");
		helper.assertTrue(reloaded.getItem() instanceof QuiverItem, "the reloaded stack is not a quiver");
		helper.assertTrue(contents.get(2).is(ArrowItems.BY_KIND.get(ArrowKind.IRON)) && contents.get(2).getCount() == 17,
			"slot 2 reloaded as " + contents.get(2));
		helper.assertTrue(contents.get(7).is(Items.FIREWORK_ROCKET) && contents.get(7).getCount() == 9, "slot 7 reloaded as " + contents.get(7));
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void aBowShotWithAQuiverInTheOffHandSpendsTheQuiverBeforeThePack(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		Item iron = ArrowItems.BY_KIND.get(ArrowKind.IRON);
		player.setItemInHand(HAND, new ItemStack(Items.BOW));
		player.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(iron, 3)));
		player.getInventory().setItem(PACK_SLOT, new ItemStack(Items.ARROW, 5));
		List<String> report = new ArrayList<>();
		int[] quiverLeft = new int[4];
		int[] packLeft = new int[4];
		for (int shot = 0; shot < 4; shot++) {
			int before = firedBy(helper, player).size();
			loose(helper, player);
			quiverLeft[shot] = Quiver.count(Quiver.held(player), iron);
			packLeft[shot] = player.getInventory().countItem(Items.ARROW);
			report.add("shot" + (shot + 1) + ":quiver=" + quiverLeft[shot] + "/pack=" + packLeft[shot]);
			helper.assertTrue(firedBy(helper, player).size() == before + 1, "shot " + (shot + 1) + " loosed " + (firedBy(helper, player).size() - before) + " arrows");
		}
		LOG.info("[BEHAVIOR] test=aBowShotWithAQuiverInTheOffHandSpendsTheQuiverBeforeThePack RESULT report={}", report);
		helper.assertTrue(quiverLeft[0] == 2 && quiverLeft[1] == 1 && quiverLeft[2] == 0, "the quiver ran down as " + List.of(quiverLeft[0], quiverLeft[1], quiverLeft[2]));
		helper.assertTrue(packLeft[0] == 5 && packLeft[1] == 5 && packLeft[2] == 5, "the pack was touched while the quiver held arrows");
		helper.assertTrue(packLeft[3] == 4, "the fourth shot left " + packLeft[3] + " pack arrows, not 4");
		firedBy(helper, player).forEach(AbstractArrow::discard);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void aCreativeShotLeavesTheQuiverWhole(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.setGameMode(GameType.CREATIVE);
		player.setItemInHand(HAND, new ItemStack(Items.BOW));
		player.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(Items.ARROW, 3)));
		loose(helper, player);
		int left = Quiver.count(Quiver.held(player), Items.ARROW);
		LOG.info("[BEHAVIOR] test=aCreativeShotLeavesTheQuiverWhole RESULT left={} fired={}", left, firedBy(helper, player).size());
		helper.assertTrue(firedBy(helper, player).size() == 1, "the creative bow loosed " + firedBy(helper, player).size() + " arrows");
		helper.assertTrue(left == 3, "a creative shot left " + left + " quiver arrows, not 3");
		firedBy(helper, player).forEach(AbstractArrow::discard);
		player.setGameMode(GameType.SURVIVAL);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void theQuiverShotLoosesTheQuiverArrowKind(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		Item fire = ArrowItems.BY_KIND.get(ArrowKind.FIRE);
		player.setItemInHand(HAND, new ItemStack(Items.BOW));
		player.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(fire, 2)));
		player.getInventory().setItem(PACK_SLOT, new ItemStack(Items.ARROW, 5));
		loose(helper, player);
		List<AbstractArrow> fired = firedBy(helper, player);
		boolean kind = fired.size() == 1 && fired.get(0).getPickupItemStackOrigin().is(fire);
		LOG.info("[BEHAVIOR] test=theQuiverShotLoosesTheQuiverArrowKind RESULT fired={} fireKind={} quiver={}", fired.size(), kind, Quiver.count(Quiver.held(player), fire));
		helper.assertTrue(kind, "the arrow in flight is not a fire arrow");
		fired.forEach(AbstractArrow::discard);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aCrossbowLoadsFireworksFromTheQuiverAndABowDoesNot(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(Items.FIREWORK_ROCKET, 3)));
		ItemStack bowDraw = player.getProjectile(new ItemStack(Items.BOW));
		ItemStack crossbowDraw = player.getProjectile(new ItemStack(Items.CROSSBOW));
		LOG.info("[BEHAVIOR] test=aCrossbowLoadsFireworksFromTheQuiverAndABowDoesNot RESULT bow={} crossbow={}", bowDraw, crossbowDraw);
		helper.assertTrue(bowDraw.isEmpty(), "a bow drew " + bowDraw + " from a quiver of fireworks");
		helper.assertTrue(crossbowDraw.is(Items.FIREWORK_ROCKET), "a crossbow drew " + crossbowDraw);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aCrossbowShotSpendsTheQuiverAndStartsFromTheQuiverFirst(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.setItemInHand(HAND, new ItemStack(Items.CROSSBOW));
		player.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(Items.ARROW, 2)));
		player.getInventory().setItem(PACK_SLOT, new ItemStack(Items.ARROW, 5));
		ItemStack crossbow = player.getItemInHand(HAND);
		crossbow.use(helper.getLevel(), player, HAND);
		int full = crossbow.getUseDuration(player) - net.minecraft.world.item.CrossbowItem.getChargeDuration(crossbow, player);
		crossbow.getItem().onUseTick(helper.getLevel(), player, crossbow, full);
		crossbow.getItem().releaseUsing(crossbow, helper.getLevel(), player, full);
		LOG.info("[BEHAVIOR] test=aCrossbowShotSpendsTheQuiverAndStartsFromTheQuiverFirst RESULT quiver={} pack={}",
			Quiver.count(Quiver.held(player), Items.ARROW), player.getInventory().countItem(Items.ARROW));
		helper.assertTrue(Quiver.count(Quiver.held(player), Items.ARROW) == 1, "the loaded crossbow left " + Quiver.count(Quiver.held(player), Items.ARROW) + " quiver arrows");
		helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 5, "the loading touched the pack");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theQuiverAnswersForAPlayerThatIsNotAServerPlayer(final GameTestHelper helper) {
		Player remote = remotePlayer(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		remote.setItemInHand(HAND, bow);
		ItemStack without = remote.getProjectile(bow);
		remote.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(Items.ARROW, 4)));
		ItemStack with = remote.getProjectile(bow);
		InteractionResult result = bow.use(helper.getLevel(), remote, HAND);
		LOG.info("[BEHAVIOR] test=theQuiverAnswersForAPlayerThatIsNotAServerPlayer RESULT serverPlayer={} without={} with={} result={}",
			remote instanceof ServerPlayer, without.isEmpty(), with, result);
		helper.assertFalse(remote instanceof ServerPlayer, "the harness built a server player: the case proves nothing");
		helper.assertTrue(without.isEmpty(), "a remote player with no quiver was handed " + without);
		helper.assertFalse(with.isEmpty(), "the quiver answered nothing for a player that is not a server player");
		helper.assertTrue(result instanceof InteractionResult.Success && remote.isUsingItem(), "the draw did not start: " + result);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aQuiverInTheMainHandOrThePackFeedsNothing(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		player.getInventory().setItem(PACK_SLOT, quiverWith(new ItemStack(Items.ARROW, 4)));
		ItemStack packDraw = player.getProjectile(new ItemStack(Items.BOW));
		player.setItemInHand(HAND, quiverWith(new ItemStack(Items.ARROW, 4)));
		ItemStack mainDraw = player.getProjectile(new ItemStack(Items.BOW));
		LOG.info("[BEHAVIOR] test=aQuiverInTheMainHandOrThePackFeedsNothing RESULT pack={} mainHand={}", packDraw, mainDraw);
		helper.assertTrue(packDraw.isEmpty(), "a quiver in the pack fed a bow: " + packDraw);
		helper.assertTrue(mainDraw.isEmpty(), "a quiver in the main hand fed a bow: " + mainDraw);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void ammunitionComesFromTheQuiverThenThePackThenTheSpectralQuiver(final GameTestHelper helper) {
		ServerPlayer player = survivor(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		player.addEffect(new MobEffectInstance(SpellStatus.SPECTRAL_QUIVER, ArcherSkills.SPECTRAL_QUIVER_TICKS, 0));
		player.getInventory().setItem(PACK_SLOT, new ItemStack(Items.SPECTRAL_ARROW, 5));
		player.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(Items.TIPPED_ARROW, 2)));
		ItemStack first = player.getProjectile(bow);
		player.setItemInHand(InteractionHand.OFF_HAND, quiverWith());
		ItemStack second = player.getProjectile(bow);
		player.getInventory().setItem(PACK_SLOT, ItemStack.EMPTY);
		ItemStack third = player.getProjectile(bow);
		LOG.info("[BEHAVIOR] test=ammunitionComesFromTheQuiverThenThePackThenTheSpectralQuiver RESULT first={} second={} third={}", first, second, third);
		helper.assertTrue(first.is(Items.TIPPED_ARROW), "the quiver did not come first: " + first);
		helper.assertTrue(second.is(Items.SPECTRAL_ARROW), "the pack did not come second: " + second);
		helper.assertTrue(third.is(Items.ARROW) && !third.isEmpty(), "the spectral quiver did not come last: " + third);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void doubleStrafeWithTwoFireArrowsInTheQuiverSpendsTwoAndIgnitesTheTarget(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.FIRE), 2));
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		boolean applied = cast(caster, "rapid_fire", foe);
		double lost = 1000.0 - foe.getHealth();
		int left = Quiver.count(Quiver.held(caster), ArrowItems.BY_KIND.get(ArrowKind.FIRE));
		LOG.info("[BEHAVIOR] test=doubleStrafeWithTwoFireArrowsInTheQuiverSpendsTwoAndIgnitesTheTarget RESULT applied={} left={} fireTicks={} lost={} base={}",
			applied, left, foe.getRemainingFireTicks(), lost, SpellNumbers.RAPID_FIRE.damage() * ArcherSkills.DOUBLE_STRAFE_HITS);
		helper.assertTrue(applied, "double strafe never cast");
		helper.assertTrue(left == 0, "double strafe left " + left + " of 2 fire arrows");
		helper.assertTrue(foe.getRemainingFireTicks() > 0, "the target is not burning");
		helper.assertTrue(Math.abs(lost - SpellNumbers.RAPID_FIRE.damage() * ArcherSkills.DOUBLE_STRAFE_HITS) < 0.05, "a fire arrow changed the damage: " + lost);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void doubleStrafeWithAnEmptyQuiverDealsBaseDamageAndIgnitesNothing(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		boolean applied = cast(caster, "rapid_fire", foe);
		double lost = 1000.0 - foe.getHealth();
		caster.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		Mob second = FighterBooksGameTest.foe(helper, CASTER_X + 3.0, CASTER_Z + 4.0);
		boolean appliedBare = cast(caster, "rapid_fire", second);
		double lostBare = 1000.0 - second.getHealth();
		LOG.info("[BEHAVIOR] test=doubleStrafeWithAnEmptyQuiverDealsBaseDamageAndIgnitesNothing RESULT applied={} lost={} fire={} bareApplied={} bareLost={} bareFire={}",
			applied, lost, foe.getRemainingFireTicks(), appliedBare, lostBare, second.getRemainingFireTicks());
		double base = SpellNumbers.RAPID_FIRE.damage() * ArcherSkills.DOUBLE_STRAFE_HITS;
		helper.assertTrue(applied && appliedBare, "double strafe refused an empty quiver or a bare hand");
		helper.assertTrue(Math.abs(lost - base) < 0.05 && Math.abs(lostBare - base) < 0.05, "the damage was " + lost + " and " + lostBare + ", not " + base);
		helper.assertTrue(foe.getRemainingFireTicks() <= 0 && second.getRemainingFireTicks() <= 0, "an empty quiver ignited a target");
		foe.discard();
		second.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void doubleStrafeCarriesTheDamageOfTheArrowsItSpends(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.IRON), 2));
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		cast(caster, "rapid_fire", foe);
		double lost = 1000.0 - foe.getHealth();
		double base = SpellNumbers.RAPID_FIRE.damage() * ArcherSkills.DOUBLE_STRAFE_HITS;
		LOG.info("[BEHAVIOR] test=doubleStrafeCarriesTheDamageOfTheArrowsItSpends RESULT lost={} base={} ratio={}", lost, base, lost / base);
		helper.assertTrue(Math.abs(lost / base - 1.40) < ArcherSkills.SHOT_RATIO_TOLERANCE, "two iron arrows gave " + lost / base + " of base, not 1.40");
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void doubleStrafeWithOneIronArrowSpendsOneAndBoostsOneHit(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.IRON), 1));
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		cast(caster, "rapid_fire", foe);
		double lost = 1000.0 - foe.getHealth();
		double hit = SpellNumbers.RAPID_FIRE.damage();
		double expected = hit * 1.40 + hit;
		LOG.info("[BEHAVIOR] test=doubleStrafeWithOneIronArrowSpendsOneAndBoostsOneHit RESULT lost={} expected={} left={}",
			lost, expected, Quiver.count(Quiver.held(caster), ArrowItems.BY_KIND.get(ArrowKind.IRON)));
		helper.assertTrue(Quiver.count(Quiver.held(caster), ArrowItems.BY_KIND.get(ArrowKind.IRON)) == 0, "the lone arrow was not spent");
		helper.assertTrue(Math.abs(lost - expected) < 0.05, "one iron arrow and one bare hit dealt " + lost + ", not " + expected);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void eachArcherSkillSpendsItsTableOfArrowsFromTheQuiver(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(Items.ARROW, 20));
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.5);
		List<String> report = new ArrayList<>();
		String[][] table = {
			{"rapid_fire", "2"}, {"piercing_shot", "1"}, {"arrow_repel", "1"}, {"multishot", "1"},
			{"hunters_mark", "0"}, {"ankle_snare", "0"}, {"explosive_arrow", "0"}, {"aimed_bolt", "0"}, {"triangle_shot", "0"},
			{"eagle_eye", "0"}, {"snare_trap", "0"}, {"disengage", "0"}, {"elemental_quiver", "0"}
		};
		for (String[] row : table) {
			caster.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(Items.ARROW, 20)));
			boolean applied = cast(caster, row[0], foe);
			int spent = 20 - Quiver.count(Quiver.held(caster), Items.ARROW);
			report.add(row[0] + "=" + spent + (applied ? "" : "(refused)"));
			helper.assertTrue(applied, row[0] + " never cast");
			helper.assertTrue(spent == Integer.parseInt(row[1]), row[0] + " spent " + spent + " arrows, not " + row[1]);
			ArcherSkills.clearFor(caster);
			caster.removeAllEffects();
			foe.setHealth(1000.0F);
		}
		LOG.info("[BEHAVIOR] test=eachArcherSkillSpendsItsTableOfArrowsFromTheQuiver RESULT report={}", report);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void sharpShootingWithOneFireArrowIgnitesEveryFoeOnTheLine(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.FIRE), 1));
		Mob first = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.5);
		Mob second = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.5);
		boolean applied = cast(caster, "piercing_shot", null);
		LOG.info("[BEHAVIOR] test=sharpShootingWithOneFireArrowIgnitesEveryFoeOnTheLine RESULT applied={} first={} second={} left={}",
			applied, first.getRemainingFireTicks(), second.getRemainingFireTicks(), Quiver.count(Quiver.held(caster), ArrowItems.BY_KIND.get(ArrowKind.FIRE)));
		helper.assertTrue(applied, "sharp shooting never cast");
		helper.assertTrue(first.getRemainingFireTicks() > 0 && second.getRemainingFireTicks() > 0, "the line was not set on fire");
		helper.assertTrue(Quiver.count(Quiver.held(caster), ArrowItems.BY_KIND.get(ArrowKind.FIRE)) == 0, "sharp shooting spent more than one arrow");
		first.discard();
		second.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void anImbuedCastSpendsNoQuiverArrow(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(ArrowItems.BY_KIND.get(ArrowKind.FIRE), 4));
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		CastContext.setImbuedVictim(foe);
		boolean applied;
		try {
			applied = cast(caster, "rapid_fire", foe);
		} finally {
			CastContext.setImbuedVictim(null);
		}
		int left = Quiver.count(Quiver.held(caster), ArrowItems.BY_KIND.get(ArrowKind.FIRE));
		LOG.info("[BEHAVIOR] test=anImbuedCastSpendsNoQuiverArrow RESULT applied={} left={} fireTicks={}", applied, left, foe.getRemainingFireTicks());
		helper.assertTrue(applied, "the imbued cast never ran");
		helper.assertTrue(left == 4, "an imbued cast spent " + (4 - left) + " arrows");
		helper.assertTrue(foe.getRemainingFireTicks() <= 0, "an imbued cast took a quiver arrow effect");
		foe.discard();
		helper.succeed();
	}

	/** A survival player with an empty pack. */
	private static ServerPlayer survivor(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		player.getInventory().clearContent();
		player.removeAllEffects();
		return player;
	}

	/** A caster with a bow in the weapon slot, no arrow in the pack, and a quiver in the off hand. */
	private static ServerPlayer archer(final GameTestHelper helper, final ItemStack... ammunition) {
		ServerPlayer caster = survivor(helper);
		SkillSchoolGameTest.setAutoKit(false);
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		caster.setItemInHand(InteractionHand.OFF_HAND, quiverWith(ammunition));
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		return caster;
	}

	private static ItemStack quiverWith(final ItemStack... ammunition) {
		ItemStack quiver = new ItemStack(Quiver.ITEM);
		NonNullList<ItemStack> items = NonNullList.withSize(QuiverMenu.SIZE, ItemStack.EMPTY);
		for (int slot = 0; slot < ammunition.length; slot++) {
			items.set(slot, ammunition[slot]);
		}
		Quiver.store(quiver, items);
		return quiver;
	}

	private static boolean cast(final ServerPlayer caster, final String path, final Mob target) {
		SpellModifiers.CastModifiers modifiers = SkillSchoolGameTest.modifiers(caster, false);
		return SkillSchoolGameTest.book(path).definition().effect().apply(caster, target, caster.position(), modifiers);
	}

	/** Draws the bow past full and releases it. */
	private static void loose(final GameTestHelper helper, final ServerPlayer player) {
		ItemStack bow = player.getItemInHand(HAND);
		player.stopUsingItem();
		InteractionResult started = bow.use(helper.getLevel(), player, HAND);
		helper.assertTrue(started instanceof InteractionResult.Success && player.isUsingItem(), "the bow refused to start the draw: " + started);
		bow.releaseUsing(helper.getLevel(), player, bow.getUseDuration(player) - 20);
		player.stopUsingItem();
	}

	private static List<AbstractArrow> firedBy(final GameTestHelper helper, final ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(16.0), arrow -> arrow.getOwner() == player);
	}

	private static Player remotePlayer(final GameTestHelper helper) {
		return new Player(helper.getLevel(), new GameProfile(UUID.randomUUID(), "remote")) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
	}
}
