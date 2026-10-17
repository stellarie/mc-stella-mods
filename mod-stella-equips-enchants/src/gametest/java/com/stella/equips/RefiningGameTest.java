package com.stella.equips;

import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineBlocks;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineStation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refining, after Ragnarok Online Renewal, in the Refine tab of an anvil: a paid roll on the piece in the first
 * slot, +0.75 damage a level for weapons and armour for armour, a failure that lowers the level by one and never
 * costs durability, and special effects at +10, +12 and +15. The level is shown like an enchantment.
 */
public class RefiningGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.refine");
	/** Each player's forced outcome; a player not listed rolls the shipped odds. */
	private static final Map<UUID, Boolean> FORCED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<String>> EVENTS = new ConcurrentHashMap<>();

	static {
		RefineRolls.install();
		RefineStation.setRoller((player, random, chance) -> {
			Boolean forced = FORCED.get(player.getUUID());
			return forced != null ? forced : RefineStation.fairRoll(random, chance);
		});
		RefineStation.setRecorder(new RefineStation.Recorder() {
			@Override
			public void attempted(final ServerPlayer player, final int nextLevel, final boolean success) {
				events(player).add("attempted next=" + nextLevel + " success=" + success);
			}

			@Override
			public void refused(final ServerPlayer player, final String reason) {
				events(player).add("refused " + reason);
			}
		});
	}

	/** Forces a player's refine rolls; the shared roller of this class installs when the class loads. */
	static void force(final ServerPlayer player, final boolean outcome) {
		FORCED.put(player.getUUID(), outcome);
	}

	private static List<String> events(final ServerPlayer player) {
		return EVENTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>());
	}

	@GameTest
	public void gradesFollowTheRenewalTables(final GameTestHelper helper) {
		boolean grades = Refine.grade(new ItemStack(Items.WOODEN_SWORD)) == Refine.Grade.WEAPON_1
			&& Refine.grade(new ItemStack(Items.IRON_SWORD)) == Refine.Grade.WEAPON_2
			&& Refine.grade(new ItemStack(Items.DIAMOND_SWORD)) == Refine.Grade.WEAPON_3
			&& Refine.grade(new ItemStack(Items.NETHERITE_SWORD)) == Refine.Grade.WEAPON_4
			&& Refine.grade(new ItemStack(Items.IRON_CHESTPLATE)) == Refine.Grade.ARMOR;
		helper.assertTrue(grades, "a piece landed in the wrong grade");
		// The Renewal odds: safe to +7 for a grade 1 weapon, +4 for armour, and 15% for the last step.
		helper.assertTrue(Refine.Grade.WEAPON_1.chanceTo(7) == 100 && Refine.Grade.WEAPON_1.chanceTo(8) == 60
			&& Refine.Grade.ARMOR.chanceTo(4) == 100 && Refine.Grade.ARMOR.chanceTo(5) == 60
			&& Refine.Grade.ARMOR.chanceTo(Refine.MAX_LEVEL) == 15 && Refine.Grade.WEAPON_4.chanceTo(10) == 9, "the odds table drifted from Renewal's");
		LOG.info("[BEHAVIOR] test=gradesFollowTheRenewalTables RESULT grades={} w1to8={} armorTo15={}", grades, Refine.Grade.WEAPON_1.chanceTo(8), Refine.Grade.ARMOR.chanceTo(Refine.MAX_LEVEL));
		helper.succeed();
	}

	@GameTest
	public void theShippedRollMeetsItsOdds(final GameTestHelper helper) {
		int chance = Refine.Grade.ARMOR.chanceTo(6);
		int trials = 20_000;
		RandomSource random = RandomSource.create(1234L);
		int won = 0;
		for (int i = 0; i < trials; i++) {
			if (RefineStation.fairRoll(random, chance)) {
				won++;
			}
		}
		double p = chance / 100.0;
		double sigma = Math.sqrt(trials * p * (1 - p));
		double rate = won / (double) trials;
		LOG.info("[BEHAVIOR] test=theShippedRollMeetsItsOdds RESULT chance={} rate={} trials={}", chance, rate, trials);
		helper.assertTrue(Math.abs(won - trials * p) <= 4 * sigma, "a " + chance + "% step succeeded at " + rate);
		helper.succeed();
	}

	@GameTest
	public void successRaisesLevel(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(sword, 3);
		ServerPlayer player = refiner(helper, sword, 15);
		FORCED.put(player.getUUID(), true);
		AnvilMenu menu = openAnvil(helper, player, sword);
		RefineStation.Result result = RefineStation.refine(player, menu.containerId);
		ItemStack refined = menu.getSlot(0).getItem();
		LOG.info("[BEHAVIOR] test=successRaisesLevel RESULT result={} level={} ingots={}", result, Refine.level(refined), ingots(player));
		helper.assertTrue(result == RefineStation.Result.SUCCESS && Refine.level(refined) == 4, "a success left " + result + " +" + Refine.level(refined));
		helper.assertTrue(ingots(player) == 15 - Refine.cost(3), "the step took " + (15 - ingots(player)) + " ingots");
		helper.succeed();
	}

	/** The cap is the piece's own cap plus 50 per level, a drop lowers it, and the damage stays below it. */
	@GameTest
	public void capFollowsLevel(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		int base = sword.getMaxDamage();
		Refine.setLevel(sword, 10);
		int atTen = sword.getMaxDamage();
		sword.setDamageValue(atTen - 10);
		Refine.setLevel(sword, 2);
		int atTwo = sword.getMaxDamage();
		int damage = sword.getDamageValue();
		ItemStack custom = new ItemStack(Items.WOODEN_SWORD);
		custom.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, 400);
		Refine.setLevel(custom, 1);
		ItemStack book = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(book, 0);
		LOG.info("[BEHAVIOR] test=capFollowsLevel RESULT base={} atTen={} atTwo={} damage={} custom={} zero={}",
			base, atTen, atTwo, damage, custom.getMaxDamage(), book.getMaxDamage());
		helper.assertTrue(atTen == base + 500 && atTwo == base + 100, "the cap was " + atTen + " at +10 and " + atTwo + " at +2 from " + base);
		helper.assertTrue(damage == atTwo - 1, "a lowered cap left damage " + damage + " of " + atTwo);
		helper.assertTrue(custom.getMaxDamage() == 450 && book.getMaxDamage() == base, "a custom cap became " + custom.getMaxDamage());
		helper.succeed();
	}

	/** A success repairs fully and raises the cap; a failure keeps the damage. */
	@GameTest
	public void successRepairs(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		int base = sword.getMaxDamage();
		Refine.setLevel(sword, 3);
		sword.setDamageValue(200);
		ServerPlayer player = refiner(helper, sword, 15);
		FORCED.put(player.getUUID(), true);
		AnvilMenu menu = openAnvil(helper, player, sword);
		RefineStation.refine(player, menu.containerId);
		ItemStack refined = menu.getSlot(0).getItem();
		int repairedDamage = refined.getDamageValue();
		int cap = refined.getMaxDamage();
		refined.setDamageValue(150);
		FORCED.put(player.getUUID(), false);
		RefineStation.refine(player, menu.containerId);
		int failedDamage = menu.getSlot(0).getItem().getDamageValue();
		LOG.info("[BEHAVIOR] test=successRepairs RESULT damage={} cap={} failedDamage={}", repairedDamage, cap, failedDamage);
		helper.assertTrue(repairedDamage == 0 && cap == base + 200, "a success left damage " + repairedDamage + " and cap " + cap);
		helper.assertTrue(failedDamage == 150, "a failure changed the damage to " + failedDamage);
		helper.succeed();
	}

	@GameTest
	public void failureDropsLevel(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		Refine.setLevel(sword, 8);
		sword.setDamageValue(100);
		ServerPlayer player = refiner(helper, sword, 10);
		FORCED.put(player.getUUID(), false);
		AnvilMenu menu = openAnvil(helper, player, sword);
		RefineStation.Result result = RefineStation.refine(player, menu.containerId);
		ItemStack after = menu.getSlot(0).getItem();
		LOG.info("[BEHAVIOR] test=failureDropsLevel RESULT result={} level={} damage={} count={}", result, Refine.level(after), after.getDamageValue(), after.getCount());
		helper.assertTrue(result == RefineStation.Result.FAILURE && Refine.level(after) == 8, "a failure below +10 left +" + Refine.level(after));
		helper.assertTrue(after.is(Items.DIAMOND_SWORD) && after.getDamageValue() == 100 && after.getCount() == 1, "a failure cost durability or the piece");
		helper.succeed();
	}

	/** Regression guard for the milestone floors: a failure never loses +10 or +12 once reached. */
	@GameTest
	public void milestoneFloors(final GameTestHelper helper) {
		int[] from = {0, 9, 10, 11, 12, 13, 14};
		int[] expected = {0, 9, 10, 10, 12, 12, 13};
		StringBuilder seen = new StringBuilder();
		boolean valid = true;
		for (int i = 0; i < from.length; i++) {
			int after = Refine.levelAfterFailure(from[i]);
			seen.append(from[i]).append("->").append(after).append(' ');
			valid &= after == expected[i];
		}
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(sword, 13);
		ServerPlayer player = refiner(helper, sword, 20);
		FORCED.put(player.getUUID(), false);
		AnvilMenu menu = openAnvil(helper, player, sword);
		RefineStation.refine(player, menu.containerId);
		int live = Refine.level(menu.getSlot(0).getItem());
		LOG.info("[BEHAVIOR] test=milestoneFloors RESULT {} live13={}", seen, live);
		helper.assertTrue(valid, "failure levels were " + seen);
		helper.assertTrue(live == 12, "a failed +13 attempt left +" + live);
		helper.succeed();
	}

	@GameTest
	public void levelNeverNegative(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ServerPlayer player = refiner(helper, sword, 5);
		FORCED.put(player.getUUID(), false);
		AnvilMenu menu = openAnvil(helper, player, sword);
		RefineStation.refine(player, menu.containerId);
		int level = Refine.level(menu.getSlot(0).getItem());
		LOG.info("[BEHAVIOR] test=levelNeverNegative RESULT level={}", level);
		helper.assertTrue(level == 0, "a failure at +0 left " + level);
		helper.succeed();
	}

	@GameTest
	public void costTaken(final GameTestHelper helper) {
		ItemStack plate = new ItemStack(Items.DIAMOND_CHESTPLATE);
		Refine.setLevel(plate, 6);
		ServerPlayer player = refiner(helper, plate, 10);
		FORCED.put(player.getUUID(), true);
		AnvilMenu menu = openAnvil(helper, player, plate);
		RefineStation.refine(player, menu.containerId);
		int left = player.getInventory().countItem(Items.DIAMOND);
		LOG.info("[BEHAVIOR] test=costTaken RESULT left={} cost={}", left, Refine.cost(6));
		helper.assertTrue(left == 10 - Refine.cost(6) && Refine.cost(6) == 3, "a +6 step took " + (10 - left) + " diamonds");
		helper.succeed();
	}

	@GameTest
	public void shortOfMaterial(final GameTestHelper helper) {
		ItemStack plate = new ItemStack(Items.DIAMOND_CHESTPLATE);
		Refine.setLevel(plate, 6);
		ServerPlayer player = refiner(helper, plate, 1);
		FORCED.put(player.getUUID(), true);
		AnvilMenu menu = openAnvil(helper, player, plate);
		RefineStation.Result result = RefineStation.refine(player, menu.containerId);
		LOG.info("[BEHAVIOR] test=shortOfMaterial RESULT result={}", result);
		helper.assertTrue(result == RefineStation.Result.SHORT_OF_MATERIAL && Refine.level(menu.getSlot(0).getItem()) == 6
			&& player.getInventory().countItem(Items.DIAMOND) == 1, "a short refine went " + result);
		helper.succeed();
	}

	@GameTest
	public void wrongMenuIgnored(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ServerPlayer player = refiner(helper, sword, 5);
		FORCED.put(player.getUUID(), true);
		AnvilMenu menu = openAnvil(helper, player, sword);
		RefineStation.Result wrongId = RefineStation.refine(player, menu.containerId + 1);
		player.containerMenu = player.inventoryMenu;
		RefineStation.Result wrongMenu = RefineStation.refine(player, player.inventoryMenu.containerId);
		LOG.info("[BEHAVIOR] test=wrongMenuIgnored RESULT wrongId={} wrongMenu={}", wrongId, wrongMenu);
		helper.assertTrue(wrongId == RefineStation.Result.WRONG_MENU && wrongMenu == RefineStation.Result.WRONG_MENU
			&& Refine.level(sword) == 0 && ingots(player) == 5, "a forged request refined: " + wrongId + ", " + wrongMenu);
		helper.succeed();
	}

	@GameTest
	public void anvilNeverDamaged(final GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.getLevel().setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ServerPlayer player = refiner(helper, sword, 64 * 4);
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
		player.containerMenu = menu;
		menu.getSlot(0).set(sword);
		for (int step = 0; step < 50; step++) {
			FORCED.put(player.getUUID(), step % 3 != 2);
			RefineStation.refine(player, menu.containerId);
		}
		BlockState state = helper.getLevel().getBlockState(pos);
		LOG.info("[BEHAVIOR] test=anvilNeverDamaged RESULT state={} level={}", state, Refine.level(menu.getSlot(0).getItem()));
		helper.assertTrue(state.equals(Blocks.ANVIL.defaultBlockState()), "50 refines changed the anvil to " + state);
		helper.succeed();
	}

	@GameTest
	public void cappedRefused(final GameTestHelper helper) {
		ItemStack capped = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(capped, Refine.MAX_LEVEL);
		ServerPlayer player = refiner(helper, capped, 20);
		FORCED.put(player.getUUID(), true);
		AnvilMenu menu = openAnvil(helper, player, capped);
		RefineStation.Result result = RefineStation.refine(player, menu.containerId);
		LOG.info("[BEHAVIOR] test=cappedRefused RESULT result={}", result);
		helper.assertTrue(result == RefineStation.Result.CAPPED && Refine.level(capped) == Refine.MAX_LEVEL && ingots(player) == 20,
			"a +15 piece went " + result);
		helper.succeed();
	}

	@GameTest
	public void refiningAnvilOpensMenu(final GameTestHelper helper) {
		ServerPlayer player = refiner(helper, new ItemStack(Items.IRON_SWORD), 5);
		BlockPos anvil = anvil(helper);
		useAnvil(helper, player, anvil);
		LOG.info("[BEHAVIOR] test=refiningAnvilOpensMenu RESULT menu={}", player.containerMenu);
		helper.assertTrue(player.containerMenu instanceof AnvilMenu, "the Refining Anvil opened " + player.containerMenu);
		helper.assertTrue(helper.getLevel().getBlockState(anvil).is(RefineBlocks.REFINING_ANVIL), "the Refining Anvil wore away");
		helper.succeed();
	}

	@GameTest
	public void smithRouteRemoved(final GameTestHelper helper) {
		ServerPlayer player = refiner(helper, new ItemStack(Items.IRON_SWORD), 0);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 3));
		FORCED.put(player.getUUID(), true);
		InteractionResult result = useSmith(helper, player, smith(helper, VillagerProfession.WEAPONSMITH));
		LOG.info("[BEHAVIOR] test=smithRouteRemoved RESULT result={} level={}", result, Refine.level(player.getMainHandItem()));
		helper.assertTrue(result == InteractionResult.PASS && Refine.level(player.getMainHandItem()) == 0
			&& player.getOffhandItem().getCount() == 3, "a smith still refined: " + result);
		helper.succeed();
	}

	@GameTest
	public void armourGainsArmourAndVigorAtTen(final GameTestHelper helper) {
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		double before = helmet.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0, EquipmentSlot.HEAD);
		Refine.setLevel(helmet, 4);
		double atFour = helmet.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0, EquipmentSlot.HEAD);
		double healthAtFour = helmet.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.MAX_HEALTH, 20.0, EquipmentSlot.HEAD);
		Refine.setLevel(helmet, Refine.SEARING_LEVEL);
		double healthAtTen = helmet.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.MAX_HEALTH, 20.0, EquipmentSlot.HEAD);
		LOG.info("[BEHAVIOR] test=armourGainsArmourAndVigorAtTen RESULT armorGain={} healthAtFour={} healthAtTen={}", atFour - before, healthAtFour, healthAtTen);
		helper.assertTrue(Math.abs(atFour - before - 4 * Refine.ARMOR_PER_LEVEL) < 1e-6, "+4 armour added " + (atFour - before));
		helper.assertTrue(healthAtFour == 20.0 && Math.abs(healthAtTen - 20.0 * (1.0 + Refine.VIGOR_MULTIPLIER)) < 1e-6, "vigor came at the wrong level: " + healthAtFour + ", " + healthAtTen);
		helper.succeed();
	}

	// The armour attribute stops at 30, so the iron set is read off its modifiers and a lighter leather set off the value.
	@GameTest(maxTicks = 60)
	public void fourRefinedPiecesAddTheirArmourTogether(final GameTestHelper helper) {
		List<List<net.minecraft.world.item.Item>> sets = List.of(
			List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
			List.of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS));
		List<EquipmentSlot> slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		Mob[] plain = new Mob[2];
		Mob[] refined = new Mob[2];
		for (int set = 0; set < 2; set++) {
			plain[set] = helper.spawnMob(EntityTypes.PIG, 1 + set, 2, 3).withNoFreeWill().spawn();
			refined[set] = helper.spawnMob(EntityTypes.PIG, 4 + set, 2, 3).withNoFreeWill().spawn();
			for (int i = 0; i < slots.size(); i++) {
				plain[set].setItemSlot(slots.get(i), new ItemStack(sets.get(set).get(i)));
				ItemStack piece = new ItemStack(sets.get(set).get(i));
				Refine.setLevel(piece, Refine.SEARING_LEVEL);
				refined[set].setItemSlot(slots.get(i), piece);
			}
		}
		helper.runAfterDelay(5, () -> {
			double expected = 4 * Refine.SEARING_LEVEL * Refine.ARMOR_PER_LEVEL;
			double ironModifiers = slots.stream()
				.mapToDouble(slot -> refined[0].getAttribute(Attributes.ARMOR).getModifier(Refine.armorId(slot)) == null ? 0.0
					: refined[0].getAttribute(Attributes.ARMOR).getModifier(Refine.armorId(slot)).amount())
				.sum();
			double leatherGain = refined[1].getAttributeValue(Attributes.ARMOR) - plain[1].getAttributeValue(Attributes.ARMOR);
			LOG.info("[BEHAVIOR] test=fourRefinedPiecesAddTheirArmourTogether RESULT ironModifiers={} leatherGain={} expected={}", ironModifiers, leatherGain, expected);
			helper.assertTrue(Math.abs(ironModifiers - expected) < 1e-6, "four +10 iron pieces carry " + ironModifiers + " refine armour, not " + expected);
			helper.assertTrue(Math.abs(leatherGain - expected) < 1e-6, "four +10 leather pieces added " + leatherGain + " armour, not " + expected);
			helper.succeed();
		});
	}

	@GameTest
	public void aRebuildDropsTheSharedLegacyArmourId(final GameTestHelper helper) {
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		ItemAttributeModifiers.Builder legacy = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : plate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			legacy.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
		}
		legacy.add(Attributes.ARMOR, new net.minecraft.world.entity.ai.attributes.AttributeModifier(Refine.ARMOR_ID, 5.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
			net.minecraft.world.entity.EquipmentSlotGroup.CHEST);
		plate.set(DataComponents.ATTRIBUTE_MODIFIERS, legacy.build());
		Refine.setLevel(plate, 6);
		List<net.minecraft.resources.Identifier> ids = plate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.filter(entry -> entry.attribute().equals(Attributes.ARMOR)).map(entry -> entry.modifier().id()).toList();
		double armour = plate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0, EquipmentSlot.CHEST);
		LOG.info("[BEHAVIOR] test=aRebuildDropsTheSharedLegacyArmourId RESULT ids={} armour={}", ids, armour);
		helper.assertTrue(!ids.contains(Refine.ARMOR_ID) && ids.contains(Refine.armorId(EquipmentSlot.CHEST)), "the rebuilt armour modifiers are " + ids);
		helper.assertTrue(Math.abs(armour - (6.0 + 6 * Refine.ARMOR_PER_LEVEL)) < 1e-6, "the rebuilt chestplate has " + armour + " armour, so the legacy bonus stayed or the new one is missing");
		helper.succeed();
	}

	@GameTest
	public void theRefineShowsLikeAnEnchantmentWithoutBeingOne(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
		ItemEnchantments enchantsBefore = sword.get(DataComponents.ENCHANTMENTS);
		Refine.setLevel(sword, 3);
		List<String> lore = sword.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(line -> line.getString()).toList();
		boolean glint = Boolean.TRUE.equals(sword.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		boolean enchantsKept = enchantsBefore.equals(sword.get(DataComponents.ENCHANTMENTS));
		LOG.info("[BEHAVIOR] test=theRefineShowsLikeAnEnchantmentWithoutBeingOne RESULT lore={} glint={} enchantsKept={}", lore, glint, enchantsKept);
		helper.assertTrue(!lore.isEmpty() && lore.get(0).equals("Refine +3") && glint, "the refine does not show: " + lore);
		helper.assertTrue(enchantsKept && sword.get(DataComponents.ENCHANTMENTS).size() == 1, "the refine touched the enchantments");
		helper.succeed();
	}

	@GameTest
	public void aTwelveWeaponBurnsAndWithers(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(sword, Refine.RENDING_LEVEL);
		ServerPlayer player = refiner(helper, sword, 0);
		Mob spider = foe(helper, 5, 2, 5);
		spider.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 2.0F);
		boolean burning = spider.getRemainingFireTicks() > 0;
		boolean withered = spider.hasEffect(MobEffects.WITHER);
		LOG.info("[BEHAVIOR] test=aTwelveWeaponBurnsAndWithers RESULT burning={} withered={}", burning, withered);
		helper.assertTrue(burning && withered, "a +12 blow left burning=" + burning + " withered=" + withered);
		helper.succeed();
	}

	@GameTest
	public void aNineWeaponHasNoEffect(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(sword, Refine.SEARING_LEVEL - 1);
		ServerPlayer player = refiner(helper, sword, 0);
		Mob spider = foe(helper, 5, 2, 5);
		spider.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 2.0F);
		LOG.info("[BEHAVIOR] test=aNineWeaponHasNoEffect RESULT burning={}", spider.getRemainingFireTicks() > 0);
		helper.assertTrue(spider.getRemainingFireTicks() <= 0, "a +9 blow set the foe alight");
		helper.succeed();
	}

	@GameTest
	public void aFifteenWeaponSendsAShockwave(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(sword, Refine.ASCENDED_LEVEL);
		ServerPlayer player = refiner(helper, sword, 0);
		Mob struck = foe(helper, 4, 2, 4);
		Mob near = foe(helper, 6, 2, 4);
		Mob far = foe(helper, 4 + (int) RefineEffects.SHOCKWAVE_RADIUS + 3, 2, 4);
		Mob pig = helper.spawnMob(EntityTypes.PIG, 4, 2, 6).withNoFreeWill().spawn();
		float nearBefore = near.getHealth();
		float farBefore = far.getHealth();
		float pigBefore = pig.getHealth();
		struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 5.0F);
		float nearLost = nearBefore - near.getHealth();
		LOG.info("[BEHAVIOR] test=aFifteenWeaponSendsAShockwave RESULT nearLost={} farLost={} pigLost={}", nearLost, farBefore - far.getHealth(), pigBefore - pig.getHealth());
		helper.assertTrue(Math.abs(nearLost - 5.0F * RefineEffects.SHOCKWAVE_SHARE) < 1e-4, "the hostile beside the blow lost " + nearLost);
		helper.assertTrue(far.getHealth() == farBefore && pig.getHealth() == pigBefore, "the shockwave reached past its radius or struck a pig");
		helper.succeed();
	}

	@GameTest
	public void twelveArmourPricksAMeleeAttacker(final GameTestHelper helper) {
		Mob wearer = helper.spawnMob(EntityTypes.ZOMBIE, 3, 2, 3).withNoFreeWill().spawn();
		wearer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		wearer.setHealth(200.0F);
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		Refine.setLevel(plate, Refine.RENDING_LEVEL);
		wearer.setItemSlot(EquipmentSlot.CHEST, plate);
		Mob attacker = helper.spawnMob(EntityTypes.PIG, 5, 2, 3).withNoFreeWill().spawn();
		attacker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		attacker.setHealth(200.0F);
		float attackerBefore = attacker.getHealth();
		float wearerBefore = wearer.getHealth();
		wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 20.0F);
		float blow = 20.0F;
		float lost = attackerBefore - attacker.getHealth();
		LOG.info("[BEHAVIOR] test=twelveArmourPricksAMeleeAttacker RESULT wearerLost={} attackerLost={}", wearerBefore - wearer.getHealth(), lost);
		helper.assertTrue(wearer.getHealth() < wearerBefore && Math.abs(lost - RefineEffects.THORN_SHARE * blow) < 1e-3, "the attacker lost " + lost + " after a blow of " + blow);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void twelveArmourPricksAPlayersAttackerOncePerLandedBlow(final GameTestHelper helper) {
		ServerPlayer wearer = refiner(helper, ItemStack.EMPTY, 0);
		wearer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		Refine.setLevel(plate, Refine.RENDING_LEVEL);
		wearer.setItemSlot(EquipmentSlot.CHEST, plate);
		Mob attacker = helper.spawnMob(EntityTypes.PIG, 4, 2, 1).withNoFreeWill().spawn();
		// A player who has just joined cannot be hurt for three seconds.
		helper.runAfterDelay(80, () -> {
			float before = attacker.getHealth();
			float wearerBefore = wearer.getHealth();
			wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 2.0F);
			float afterLanded = attacker.getHealth();
			float blow = 2.0F;
			float taken = wearerBefore - wearer.getHealth();
			// A second blow of the same weight inside the wearer's invulnerability does not land. The attacker's
			// own invulnerability is cleared, so a second prick would show.
			attacker.damageCooldownTime = 0;
			wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 2.0F);
			float afterIgnored = attacker.getHealth();
			LOG.info("[BEHAVIOR] test=twelveArmourPricksAPlayersAttackerOncePerLandedBlow RESULT landed={} ignored={} wearerHealth={}",
				before - afterLanded, afterLanded - afterIgnored, wearer.getHealth());
			helper.assertTrue(taken > 0.0F && Math.abs(before - afterLanded - RefineEffects.THORN_SHARE * blow) < 1e-3, "a landed blow of " + blow + " pricked the attacker for " + (before - afterLanded));
			helper.assertTrue(afterIgnored == afterLanded, "a blow that never landed still pricked the attacker");
			helper.succeed();
		});
	}

	@GameTest
	public void theShockwaveSparesAVillagePatrol(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(sword, Refine.ASCENDED_LEVEL);
		ServerPlayer player = refiner(helper, sword, 0);
		Mob struck = foe(helper, 4, 2, 4);
		Mob patrol = helper.spawnMob(EntityTypes.PILLAGER, 6, 2, 4).withNoFreeWill().spawn();
		patrol.addTag("mod-stella-mobs.friendly_patrol");
		Mob enemy = foe(helper, 4, 2, 6);
		float patrolBefore = patrol.getHealth();
		float enemyBefore = enemy.getHealth();
		struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 5.0F);
		LOG.info("[BEHAVIOR] test=theShockwaveSparesAVillagePatrol RESULT patrolLost={} enemyLost={}", patrolBefore - patrol.getHealth(), enemyBefore - enemy.getHealth());
		helper.assertTrue(enemy.getHealth() < enemyBefore, "the shockwave struck no enemy, so the case proves nothing");
		helper.assertTrue(patrol.getHealth() == patrolBefore, "the shockwave struck a village patrol");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void fifteenArmourBlessesItsWearer(final GameTestHelper helper) {
		ServerPlayer blessed = refiner(helper, ItemStack.EMPTY, 0);
		ServerPlayer plain = refiner(helper, ItemStack.EMPTY, 0);
		helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayers().contains(blessed), "the mock player is not listed, so the case proves nothing");
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			ItemStack piece = new ItemStack(switch (slot) {
				case HEAD -> Items.IRON_HELMET;
				case CHEST -> Items.IRON_CHESTPLATE;
				case LEGS -> Items.IRON_LEGGINGS;
				default -> Items.IRON_BOOTS;
			});
			Refine.setLevel(piece, Refine.ASCENDED_LEVEL);
			blessed.setItemSlot(slot, piece);
		}
		for (ServerPlayer player : List.of(blessed, plain)) {
			player.getFoodData().setFoodLevel(17);
			player.setHealth(10.0F);
		}
		helper.runAfterDelay(RefineEffects.BLESSING_INTERVAL_TICKS + 5L, () -> {
			float gap = blessed.getHealth() - plain.getHealth();
			LOG.info("[BEHAVIOR] test=fifteenArmourBlessesItsWearer RESULT blessed={} plain={} gap={}", blessed.getHealth(), plain.getHealth(), gap);
			helper.assertTrue(gap >= 4 * RefineEffects.BLESSING_SHARE * blessed.getMaxHealth() - 1e-4, "four +15 pieces healed only " + gap + " more than none");
			helper.succeed();
		});
	}

	@GameTest
	public void aRefinedBowLoosesStrongerArrows(final GameTestHelper helper) {
		ItemStack bow = new ItemStack(Items.BOW);
		Refine.setLevel(bow, 8);
		ServerPlayer player = refiner(helper, bow, 0);
		ServerLevel level = helper.getLevel();
		Arrow refined = new Arrow(level, player, new ItemStack(Items.ARROW), bow);
		Arrow plain = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		level.addFreshEntity(refined);
		level.addFreshEntity(plain);
		double gain = RefineEffects.baseDamage(refined) - RefineEffects.baseDamage(plain);
		refined.discard();
		plain.discard();
		LOG.info("[BEHAVIOR] test=aRefinedBowLoosesStrongerArrows RESULT gain={}", gain);
		helper.assertTrue(Math.abs(gain - 8 * Refine.DAMAGE_PER_LEVEL) < 1e-6, "a +8 bow's arrow gained " + gain);
		helper.succeed();
	}

	@GameTest
	public void nineAnvilsCraftARefiningAnvil(final GameTestHelper helper) {
		var recipe = helper.getLevel().getServer().getRecipeManager()
			.byKey(ResourceKey.create(Registries.RECIPE, RefineBlocks.REFINING_ANVIL_ID));
		List<ItemStack> grid = java.util.Collections.nCopies(9, new ItemStack(Items.ANVIL));
		boolean makes = recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting
			&& crafting.assemble(CraftingInput.of(3, 3, grid)).is(RefineBlocks.REFINING_ANVIL_ITEM);
		LOG.info("[BEHAVIOR] test=nineAnvilsCraftARefiningAnvil RESULT recipe={} makes={}", recipe.isPresent(), makes);
		helper.assertTrue(makes, "nine anvils do not craft a Refining Anvil");
		helper.succeed();
	}

	private static ServerPlayer refiner(final GameTestHelper helper, final ItemStack held, final int ingots) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		if (ingots > 0) {
			// The piece's own material: iron for an iron sword, diamonds for a diamond one.
			player.getInventory().add(new ItemStack(Refine.material(held), ingots));
		}
		return player;
	}

	private static BlockPos anvil(final GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.getLevel().setBlockAndUpdate(pos, RefineBlocks.REFINING_ANVIL.defaultBlockState());
		return pos;
	}

	private static AnvilMenu openAnvil(final GameTestHelper helper, final ServerPlayer player, final ItemStack piece) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		player.containerMenu = menu;
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		menu.getSlot(0).set(piece);
		return menu;
	}

	private static void useAnvil(final GameTestHelper helper, final ServerPlayer player, final BlockPos pos) {
		BlockState state = helper.getLevel().getBlockState(pos);
		state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	private static InteractionResult useSmith(final GameTestHelper helper, final ServerPlayer player, final Villager smith) {
		return UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, smith, new EntityHitResult(smith));
	}

	private static Villager smith(final GameTestHelper helper, final ResourceKey<VillagerProfession> profession) {
		Villager villager = helper.spawnMob(EntityTypes.VILLAGER, 3, 2, 3).withNoFreeWill().spawn();
		villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), profession).withLevel(2));
		return villager;
	}

	private static Mob foe(final GameTestHelper helper, final int x, final int y, final int z) {
		Mob spider = helper.spawnMob(EntityTypes.SPIDER, x, y, z).withNoFreeWill().spawn();
		spider.setPersistenceRequired();
		return spider;
	}

	private static int ingots(final ServerPlayer player) {
		return player.getInventory().countItem(Items.IRON_INGOT);
	}

	private static double damage(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
	}
}
