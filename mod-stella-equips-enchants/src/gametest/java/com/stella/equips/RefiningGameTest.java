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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refining, after Ragnarok Online Renewal: a quote on the first use at a Refining Anvil or a village smith, a
 * paid roll on the second, +0.75 damage a level for weapons and armour for armour, a fifth of the durability
 * chipped on a failure or the piece broken when a failure would take its last, and special effects at +10,
 * +12 and +15. The level is shown like an enchantment and never takes one's place.
 */
public class RefiningGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.refine");
	/** Each player's forced outcome; a player not listed rolls the shipped odds. */
	private static final Map<UUID, Boolean> FORCED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<String>> EVENTS = new ConcurrentHashMap<>();

	static {
		RefineStation.setRoller((player, random, chance) -> {
			Boolean forced = FORCED.get(player.getUUID());
			return forced != null ? forced : RefineStation.fairRoll(random, chance);
		});
		RefineStation.setRecorder(new RefineStation.Recorder() {
			@Override
			public void quoted(final ServerPlayer player, final int nextLevel, final int chance, final int cost, final boolean warning) {
				events(player).add("quoted next=" + nextLevel + " chance=" + chance + " cost=" + cost + " warning=" + warning);
			}

			@Override
			public void attempted(final ServerPlayer player, final int nextLevel, final boolean success, final boolean broke) {
				events(player).add("attempted next=" + nextLevel + " success=" + success + " broke=" + broke);
			}

			@Override
			public void refused(final ServerPlayer player, final String reason) {
				events(player).add("refused " + reason);
			}
		});
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
	public void theFirstUseAtTheAnvilOnlyQuotes(final GameTestHelper helper) {
		ServerPlayer player = refiner(helper, new ItemStack(Items.IRON_SWORD), 5);
		BlockPos anvil = anvil(helper);
		useAnvil(helper, player, anvil);
		List<String> events = events(player);
		LOG.info("[BEHAVIOR] test=theFirstUseAtTheAnvilOnlyQuotes RESULT events={} level={} ingots={}", events, Refine.level(player.getMainHandItem()), ingots(player));
		helper.assertTrue(events.equals(List.of("quoted next=1 chance=100 cost=1 warning=false")), "the first use did " + events);
		helper.assertTrue(Refine.level(player.getMainHandItem()) == 0 && ingots(player) == 5, "the quote took material or refined");
		helper.succeed();
	}

	@GameTest
	public void aSecondUseRefinesAndTheAnvilNeverWears(final GameTestHelper helper) {
		ServerPlayer player = refiner(helper, new ItemStack(Items.IRON_SWORD), 15);
		FORCED.put(player.getUUID(), true);
		BlockPos anvil = anvil(helper);
		double before = damage(player.getMainHandItem());
		for (int step = 0; step < 6; step++) {
			useAnvil(helper, player, anvil);
			useAnvil(helper, player, anvil);
		}
		ItemStack sword = player.getMainHandItem();
		int spent = 15 - ingots(player);
		int expectedSpent = 0;
		for (int level = 0; level < 6; level++) {
			expectedSpent += Refine.cost(level);
		}
		boolean anvilStands = helper.getLevel().getBlockState(anvil).is(RefineBlocks.REFINING_ANVIL);
		LOG.info("[BEHAVIOR] test=aSecondUseRefinesAndTheAnvilNeverWears RESULT level={} spent={} damageGain={} anvilStands={}", Refine.level(sword), spent, damage(sword) - before, anvilStands);
		helper.assertTrue(Refine.level(sword) == 6, "six paid steps left the sword at +" + Refine.level(sword));
		helper.assertTrue(spent == expectedSpent, "six steps took " + spent + " ingots, not " + expectedSpent);
		helper.assertTrue(Math.abs(damage(sword) - before - 6 * Refine.DAMAGE_PER_LEVEL) < 1e-6, "+6 added " + (damage(sword) - before) + " damage");
		helper.assertTrue(anvilStands, "the Refining Anvil wore away");
		helper.succeed();
	}

	@GameTest
	public void aFailedRefineChipsAFifthOfTheDurability(final GameTestHelper helper) {
		ServerPlayer player = refiner(helper, new ItemStack(Items.DIAMOND_SWORD), 5);
		FORCED.put(player.getUUID(), false);
		BlockPos anvil = anvil(helper);
		useAnvil(helper, player, anvil);
		useAnvil(helper, player, anvil);
		ItemStack sword = player.getMainHandItem();
		int expected = (int) Math.ceil(sword.getMaxDamage() * Refine.FAILURE_CHIP);
		LOG.info("[BEHAVIOR] test=aFailedRefineChipsAFifthOfTheDurability RESULT damage={} expected={} level={} events={}", sword.getDamageValue(), expected, Refine.level(sword), events(player));
		helper.assertTrue(sword.is(Items.DIAMOND_SWORD) && sword.getDamageValue() == expected && Refine.level(sword) == 0, "a failure left " + sword + " at damage " + sword.getDamageValue());
		helper.succeed();
	}

	@GameTest
	public void aWornPieceIsWarnedAndBreaksOnFailure(final GameTestHelper helper) {
		ItemStack worn = new ItemStack(Items.IRON_SWORD);
		worn.setDamageValue(worn.getMaxDamage() - Refine.chip(worn));
		ServerPlayer player = refiner(helper, worn, 5);
		FORCED.put(player.getUUID(), false);
		BlockPos anvil = anvil(helper);
		useAnvil(helper, player, anvil);
		useAnvil(helper, player, anvil);
		List<String> events = events(player);
		LOG.info("[BEHAVIOR] test=aWornPieceIsWarnedAndBreaksOnFailure RESULT events={} held={}", events, player.getMainHandItem());
		helper.assertTrue(events.equals(List.of("quoted next=1 chance=100 cost=1 warning=true", "attempted next=1 success=false broke=true")), "a worn piece went " + events);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the failed piece survived as " + player.getMainHandItem());
		helper.succeed();
	}

	@GameTest
	public void aRefineWithoutMaterialIsRefused(final GameTestHelper helper) {
		ServerPlayer player = refiner(helper, new ItemStack(Items.IRON_SWORD), 0);
		FORCED.put(player.getUUID(), true);
		BlockPos anvil = anvil(helper);
		useAnvil(helper, player, anvil);
		useAnvil(helper, player, anvil);
		List<String> events = events(player);
		LOG.info("[BEHAVIOR] test=aRefineWithoutMaterialIsRefused RESULT events={}", events);
		helper.assertTrue(events.equals(List.of("quoted next=1 chance=100 cost=1 warning=false", "refused short_of_material")) && Refine.level(player.getMainHandItem()) == 0, "with no ingots the refine went " + events);
		helper.succeed();
	}

	@GameTest
	public void nothingRefinesPastFifteen(final GameTestHelper helper) {
		ItemStack capped = new ItemStack(Items.IRON_SWORD);
		Refine.setLevel(capped, Refine.MAX_LEVEL);
		ServerPlayer player = refiner(helper, capped, 20);
		FORCED.put(player.getUUID(), true);
		BlockPos anvil = anvil(helper);
		useAnvil(helper, player, anvil);
		useAnvil(helper, player, anvil);
		List<String> events = events(player);
		LOG.info("[BEHAVIOR] test=nothingRefinesPastFifteen RESULT events={} level={}", events, Refine.level(player.getMainHandItem()));
		helper.assertTrue(events.equals(List.of("refused capped", "refused capped")) && Refine.level(player.getMainHandItem()) == Refine.MAX_LEVEL, "a +15 piece went " + events);
		helper.succeed();
	}

	@GameTest
	public void aVillageSmithRefinesWithTheIngotInTheOffHand(final GameTestHelper helper) {
		ServerPlayer player = refiner(helper, new ItemStack(Items.IRON_SWORD), 0);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 3));
		FORCED.put(player.getUUID(), true);
		Villager smith = smith(helper, VillagerProfession.WEAPONSMITH);
		InteractionResult quote = useSmith(helper, player, smith);
		InteractionResult refine = useSmith(helper, player, smith);
		LOG.info("[BEHAVIOR] test=aVillageSmithRefinesWithTheIngotInTheOffHand RESULT quote={} refine={} level={} offhand={}", quote, refine, Refine.level(player.getMainHandItem()), player.getOffhandItem().getCount());
		helper.assertTrue(quote.consumesAction() && refine.consumesAction(), "the smith did not take the refine: " + quote + ", " + refine);
		helper.assertTrue(Refine.level(player.getMainHandItem()) == 1 && player.getOffhandItem().getCount() == 2, "the smith left the sword at +" + Refine.level(player.getMainHandItem()));
		helper.succeed();
	}

	@GameTest
	public void aSmithWithoutMaterialOrAFarmerOpensNoRefine(final GameTestHelper helper) {
		ServerPlayer bare = refiner(helper, new ItemStack(Items.IRON_SWORD), 5);
		ServerPlayer atFarmer = refiner(helper, new ItemStack(Items.IRON_SWORD), 0);
		atFarmer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 3));
		InteractionResult noMaterial = useSmith(helper, bare, smith(helper, VillagerProfession.WEAPONSMITH));
		InteractionResult farmer = useSmith(helper, atFarmer, smith(helper, VillagerProfession.FARMER));
		LOG.info("[BEHAVIOR] test=aSmithWithoutMaterialOrAFarmerOpensNoRefine RESULT noMaterial={} farmer={}", noMaterial, farmer);
		helper.assertTrue(noMaterial == InteractionResult.PASS && farmer == InteractionResult.PASS && events(bare).isEmpty() && events(atFarmer).isEmpty(), "a refine opened where trades belong: " + noMaterial + ", " + farmer);
		helper.succeed();
	}

	@GameTest
	public void armourGainsArmourAndVigorAtTen(final GameTestHelper helper) {
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		double before = plate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0, EquipmentSlot.CHEST);
		Refine.setLevel(plate, 4);
		double atFour = plate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0, EquipmentSlot.CHEST);
		double healthAtFour = plate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.MAX_HEALTH, 20.0, EquipmentSlot.CHEST);
		Refine.setLevel(plate, Refine.SEARING_LEVEL);
		double healthAtTen = plate.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.MAX_HEALTH, 20.0, EquipmentSlot.CHEST);
		LOG.info("[BEHAVIOR] test=armourGainsArmourAndVigorAtTen RESULT armorGain={} healthAtFour={} healthAtTen={}", atFour - before, healthAtFour, healthAtTen);
		helper.assertTrue(Math.abs(atFour - before - 4 * Refine.ARMOR_PER_LEVEL) < 1e-6, "+4 armour added " + (atFour - before));
		helper.assertTrue(healthAtFour == 20.0 && healthAtTen == 20.0 + Refine.VIGOR_HEALTH, "vigor came at the wrong level: " + healthAtFour + ", " + healthAtTen);
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
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		Refine.setLevel(plate, Refine.RENDING_LEVEL);
		wearer.setItemSlot(EquipmentSlot.CHEST, plate);
		Mob attacker = helper.spawnMob(EntityTypes.PIG, 5, 2, 3).withNoFreeWill().spawn();
		float before = attacker.getHealth();
		wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 2.0F);
		float lost = before - attacker.getHealth();
		LOG.info("[BEHAVIOR] test=twelveArmourPricksAMeleeAttacker RESULT attackerLost={}", lost);
		helper.assertTrue(lost == RefineEffects.THORN_DAMAGE, "the attacker lost " + lost + ", not " + RefineEffects.THORN_DAMAGE);
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
			helper.assertTrue(gap >= 4 * RefineEffects.BLESSING_HEAL - 1e-4, "four +15 pieces healed only " + gap + " more than none");
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
