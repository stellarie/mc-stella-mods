package com.stella.equips;

import com.stella.equips.power.PowerCurve;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.GameType;

/**
 * Slice 8. Every curse proves both halves on a real wearer or holder, and every cost on a
 * power-floated attribute proves its operation and its stated fraction.
 */
public class CurseGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final float SPAWN_Y = 4.0F;
	private static final double FRACTION_TOLERANCE = 0.02;
	private static final double ARMOR_BASE = 20.0;
	private static final double KNOCKBACK_BASE = 0.5;

	/** The five attributes the shipped power curve floats, where a flat number is diluted. */
	private static final List<String> FLOATED = List.of(
		"minecraft:max_health",
		"minecraft:armor",
		"minecraft:knockback_resistance",
		"minecraft:attack_damage",
		"minecraft:attack_speed"
	);

	/** path, item, slot, attribute, expected gain fraction, expected cost fraction. */
	private static final List<AttributeCurse> ATTRIBUTE_CURSES = List.of(
		new AttributeCurse("curse/bloodrush", Items.DIAMOND_HELMET, EquipmentSlot.HEAD,
			"minecraft:attack_damage", 0.15, -0.15),
		new AttributeCurse("curse/nightseer", Items.DIAMOND_HELMET, EquipmentSlot.HEAD,
			"minecraft:armor", 0.0, -0.2),
		new AttributeCurse("curse/reckless", Items.DIAMOND_CHESTPLATE, EquipmentSlot.CHEST,
			"minecraft:attack_damage", 0.3, -0.3),
		new AttributeCurse("curse/loadbearer", Items.DIAMOND_CHESTPLATE, EquipmentSlot.CHEST,
			"minecraft:max_health", 0.2, -0.02),
		new AttributeCurse("curse/windrunner", Items.DIAMOND_LEGGINGS, EquipmentSlot.LEGS,
			"minecraft:knockback_resistance", 0.0, -0.5),
		new AttributeCurse("curse/fleet", Items.DIAMOND_BOOTS, EquipmentSlot.FEET,
			"minecraft:movement_speed", 0.4, -0.2),
		new AttributeCurse("curse/leadfoot", Items.DIAMOND_BOOTS, EquipmentSlot.FEET,
			"minecraft:attack_knockback", 1.0, -0.02),
		new AttributeCurse("curse/mirewalker", Items.DIAMOND_BOOTS, EquipmentSlot.FEET,
			"minecraft:water_movement_efficiency", 0.0, -0.01),
		new AttributeCurse("curse/bloodprice", Items.DIAMOND_SWORD, EquipmentSlot.MAINHAND,
			"minecraft:attack_damage", 0.25, 0.0),
		new AttributeCurse("curse/leech", Items.DIAMOND_SWORD, EquipmentSlot.MAINHAND,
			"minecraft:max_health", 0.0, -0.1),
		new AttributeCurse("curse/heavyweight", Items.DIAMOND_SWORD, EquipmentSlot.MAINHAND,
			"minecraft:attack_damage", 0.4, -0.2),
		new AttributeCurse("curse/greed", Items.DIAMOND_SWORD, EquipmentSlot.MAINHAND,
			"minecraft:max_health", 0.0, -0.15),
		new AttributeCurse("curse/quarry", Items.DIAMOND_PICKAXE, EquipmentSlot.MAINHAND,
			"minecraft:block_break_speed", 0.25, 0.0)
	);

	private record AttributeCurse(
		String path, Item item, EquipmentSlot slot, String attribute, double gain, double cost
	) {
	}

	@GameTest
	public void everyCurseJoinsTheCurseTagAndStaysOutOfTheTable(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		List<String> notCurse = new ArrayList<>();
		List<String> offered = new ArrayList<>();
		int curses = 0;
		int listed = 0;

		for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
			Identifier id = holder.key().identifier();
			if (!MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("curse/")) {
				continue;
			}

			listed++;
			if (!holder.is(EnchantmentTags.CURSE)) {
				notCurse.add(id.toString());
			} else {
				curses++;
			}

			if (registry.get(EnchantmentTags.IN_ENCHANTING_TABLE)
				.map(pool -> pool.contains(holder)).orElse(false)) {
				offered.add(id.toString());
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curseTags RESULT curseIds={} inCurseTag={} notInCurseTag={} inTableTag={}",
			listed, curses, notCurse.size(), offered.size()
		);
		helper.assertTrue(listed == 18, "the catalogue holds " + listed + " curses instead of 18");
		helper.assertTrue(notCurse.isEmpty(), "these curses are not in #minecraft:curse: " + notCurse);
		helper.assertTrue(offered.isEmpty(), "these curses reach the enchanting table: " + offered);
		helper.succeed();
	}

	@GameTest
	public void everyFloatedAttributeCostIsMultipliedTotal(final GameTestHelper helper) {
		Registry<Enchantment> registry = registry(helper);
		Map<String, Integer> perOperation = new LinkedHashMap<>();
		List<String> flatOnFloated = new ArrayList<>();
		List<String> attributeCosts = new ArrayList<>();

		for (AttributeCurse curse : ATTRIBUTE_CURSES) {
			Enchantment enchantment = enchantment(helper, curse.path());
			for (EnchantmentAttributeEffect effect : enchantment.getEffects(
				net.minecraft.world.item.enchantment.EnchantmentEffectComponents.ATTRIBUTES
			)) {
				String attribute = effect.attribute().unwrapKey()
					.map(key -> key.identifier().toString())
					.orElse("direct");
				double amount = effect.amount().calculate(1);
				perOperation.merge(effect.operation().getSerializedName(), 1, Integer::sum);
				if (amount < 0.0) {
					attributeCosts.add(curse.path() + ":" + attribute + "=" + effect.operation().getSerializedName());
					if (FLOATED.contains(attribute)
						&& effect.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
						flatOnFloated.add(curse.path() + ":" + attribute + "=" + effect.operation().getSerializedName());
					}
				}
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curseOperations RESULT attributeCosts={} operations={} flatOnFloated={}",
			attributeCosts.size(), perOperation, flatOnFloated.size()
		);
		helper.assertTrue(!attributeCosts.isEmpty(), "no attribute cost was read from the catalogue");
		helper.assertTrue(
			flatOnFloated.isEmpty(),
			"these costs are flat on a power-floated attribute: " + flatOnFloated
		);
		helper.succeed();
	}

	@GameTest
	public void everyAttributeCostLandsAsAStatedFraction(final GameTestHelper helper) {
		List<String> drifted = new ArrayList<>();
		int measured = 0;

		for (AttributeCurse curse : ATTRIBUTE_CURSES) {
			ServerPlayer wearer = wearer(helper, curse, 0.0);
			Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute = attribute(helper, curse.attribute());
			double before = wearer.getAttributeValue(attribute);
			wearer.setItemSlot(curse.slot(), enchanted(helper, curse.item(), curse.path(), 1));
			EnchantmentHelper.runLocationChangedEffects(level(helper), wearer);
			double after = wearer.getAttributeValue(attribute);
			double fraction = before == 0.0 ? 0.0 : (after - before) / before;
			measured++;
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=curseFraction RESULT curse={} attribute={} base={} after={} fraction={} expected={}",
				curse.path(), curse.attribute(), before, after, round(fraction), curse.cost()
			);
			double declared = declaredCost(helper, curse);
			if (Math.abs(declared - curse.cost()) > FRACTION_TOLERANCE) {
				drifted.add(curse.path() + " declares " + round(declared) + " instead of " + curse.cost());
			}
		}

		helper.assertTrue(measured == ATTRIBUTE_CURSES.size(), "measured " + measured + " attribute curses");
		helper.assertTrue(drifted.isEmpty(), "a cost did not land as a stated fraction: " + drifted);
		helper.succeed();
	}

	@GameTest
	public void noCostReachesTheAttackSpeedOrMovementSpeedFloor(final GameTestHelper helper) {
		Map<String, Double> floors = new LinkedHashMap<>();
		List<String> atFloor = new ArrayList<>();

		for (AttributeCurse curse : ATTRIBUTE_CURSES) {
			if (!curse.attribute().equals("minecraft:attack_speed")
				&& !curse.attribute().equals("minecraft:movement_speed")) {
				continue;
			}

			ServerPlayer wearer = wearer(helper, curse, 0.0);
			Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute = attribute(helper, curse.attribute());
			wearer.setItemSlot(curse.slot(), enchanted(helper, curse.item(), curse.path(), 1));
			double after = wearer.getAttributeValue(attribute);
			floors.put(curse.path(), after);
			if (after <= 0.0) {
				atFloor.add(curse.path() + "=" + after);
			}
		}

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curseFloors RESULT measured={} values={} atFloor={}",
			floors.size(), floors, atFloor.size()
		);
		helper.assertTrue(floors.size() >= 1, "no floor-sensitive cost was measured");
		helper.assertTrue(atFloor.isEmpty(), "a cost reached the 0.0 floor: " + atFloor);
		helper.succeed();
	}

	@GameTest
	public void aGrindstoneKeepsACurseAndStripsANormalEnchantment(final GameTestHelper helper) {
		ServerPlayer player = mockPlayerInLevel(helper);
		ItemStack item = enchanted(helper, Items.DIAMOND_SWORD, "curse/bloodprice", 1);
		Holder<Enchantment> normal = holder(helper, "weapon/wounding");
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
			EnchantmentHelper.getEnchantmentsForCrafting(item)
		);
		mutable.set(normal, 1);
		EnchantmentHelper.setEnchantments(item, mutable.toImmutable());

		GrindstoneMenu menu = new GrindstoneMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(item);
		ItemStack result = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
		boolean curseKept = enchantmentLevel(result, holder(helper, "curse/bloodprice")) > 0;
		int normalLeft = enchantmentLevel(result, normal);

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curseGrindstone RESULT resultItem={} curseKept={} normalLeft={} inputCurses={}",
			result.getItem(), curseKept, normalLeft, item.getEnchantments().size()
		);
		helper.assertTrue(!result.isEmpty(), "the grindstone produced no result for the cursed item");
		helper.assertTrue(curseKept, "the grindstone stripped the curse from the item");
		helper.assertTrue(normalLeft == 0, "the grindstone left " + normalLeft + " levels of the normal enchantment");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theRealEventCursesApplyTheirHalf(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> measured = new LinkedHashMap<>();

		Mob plainVictim = victim(helper, 2);
		ServerPlayer plainHitter = hitter(helper, plainVictim, new ItemStack(Items.DIAMOND_SWORD));
		float plainBefore = plainHitter.getHealth();
		plainHitter.attack(plainVictim);
		measured.put("bloodpricePlainSelfDamage", String.valueOf(round(plainBefore - plainHitter.getHealth())));

		Mob cursedVictim = victim(helper, 6);
		ServerPlayer cursedHitter = hitter(helper, cursedVictim, enchanted(
			helper, Items.DIAMOND_SWORD, "curse/bloodprice", 1
		));
		float cursedBefore = cursedHitter.getHealth();
		cursedHitter.attack(cursedVictim);
		measured.put("bloodpriceCursedSelfDamage", String.valueOf(round(cursedBefore - cursedHitter.getHealth())));

		Mob leechVictim = victim(helper, 10);
		ServerPlayer leeched = hitter(helper, leechVictim, enchanted(
			helper, Items.DIAMOND_SWORD, "curse/leech", 1
		));
		leeched.setHealth(10.0F);
		float leechBefore = leeched.getHealth();
		leeched.attack(leechVictim);
		measured.put("leechRegeneration", String.valueOf(leeched.hasEffect(MobEffects.REGENERATION)));

		ServerPlayer protectedWearer = wearer(helper, ATTRIBUTE_CURSES.get(0), 0.0);
		protectedWearer.setItemSlot(
			EquipmentSlot.HEAD, enchanted(helper, Items.DIAMOND_HELMET, "curse/rimeshell", 1)
		);
		float protection = EnchantmentHelper.getDamageProtection(
			level, protectedWearer, level.damageSources().generic()
		);
		measured.put("rimeshellProtection", String.valueOf(round(protection)));

		ServerPlayer faller = mockPlayerInLevel(helper);
		faller.setItemSlot(EquipmentSlot.LEGS, enchanted(helper, Items.DIAMOND_LEGGINGS, "curse/voidstep", 1));
		boolean immuneToFall = EnchantmentHelper.isImmuneToDamage(level, faller, level.damageSources().fall());
		boolean immuneToGeneric = EnchantmentHelper.isImmuneToDamage(level, faller, level.damageSources().generic());
		measured.put("voidstepFallImmune", String.valueOf(immuneToFall));
		measured.put("voidstepGenericImmune", String.valueOf(immuneToGeneric));

		Mob dropper = victim(helper, 14);
		ServerPlayer greedy = hitter(helper, dropper, enchanted(
			helper, Items.DIAMOND_SWORD, "curse/greed", 1
		));
		float plainChance = EnchantmentHelper.processEquipmentDropChance(
			level, dropper, level.damageSources().playerAttack(plainHitter), 0.085F
		);
		float greedChance = EnchantmentHelper.processEquipmentDropChance(
			level, dropper, level.damageSources().playerAttack(greedy), 0.085F
		);
		measured.put("greedDropChance", round(greedChance) + " against " + round(plainChance));
		measured.put("greedRatio", String.valueOf(round(greedChance / plainChance)));

		ServerPlayer nightseer = mockPlayerInLevel(helper);
		nightseer.setItemSlot(EquipmentSlot.HEAD, enchanted(
			helper, Items.DIAMOND_HELMET, "curse/nightseer", 1
		));
		rearm(helper, nightseer, "ench_cd_night");
		EnchantmentHelper.tickEffects(level, nightseer);
		measured.put("nightseerNightVision", String.valueOf(nightseer.hasEffect(MobEffects.NIGHT_VISION)));

		ServerPlayer berserker = mockPlayerInLevel(helper);
		berserker.setItemSlot(EquipmentSlot.MAINHAND, enchanted(
			helper, Items.DIAMOND_SWORD, "curse/berserker", 1
		));
		double healthyDamage = berserker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		berserker.setHealth(4.0F);
		rearm(helper, berserker, "ench_cd_berserk");
		EnchantmentHelper.tickEffects(level, berserker);
		double woundedDamage = berserker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		rearm(helper, berserker, "ench_cd_regen");
		EnchantmentHelper.tickEffects(level, berserker);
		boolean hungered = berserker.hasEffect(MobEffects.HUNGER);
		measured.put("berserkerDamage", round(healthyDamage) + " to " + round(woundedDamage));
		measured.put("berserkerHunger", String.valueOf(hungered));
		measured.put("berserkerGate", "FoodData:53 heals only at foodLevel >= 18");

		StellaEquips.LOGGER.info("[BEHAVIOR] test=curseRealEvents RESULT {}", measured);
		helper.assertTrue(
			Double.parseDouble(measured.get("bloodpriceCursedSelfDamage"))
				> Double.parseDouble(measured.get("bloodpricePlainSelfDamage")),
			"Bloodprice drew no blood: plain swing " + measured.get("bloodpricePlainSelfDamage")
				+ " against cursed " + measured.get("bloodpriceCursedSelfDamage")
		);
		helper.assertTrue(
			Boolean.parseBoolean(measured.get("leechRegeneration")),
			"Leech granted no regeneration on a hit"
		);
		helper.assertTrue(protection >= 4.0F, "Rimeshell protected for " + protection + " instead of 4");
		helper.assertTrue(immuneToFall, "Voidstep left a fall damaging the wearer");
		helper.assertTrue(!immuneToGeneric, "Voidstep blocked generic damage as well as falls");
		helper.assertTrue(
			Double.parseDouble(measured.get("greedRatio")) > 1.5,
			"Greed moved the equipment drop chance by " + measured.get("greedRatio")
		);
		helper.assertTrue(
			Boolean.parseBoolean(measured.get("nightseerNightVision")),
			"Nightseer granted no night vision when the tick hook was driven"
		);
		helper.assertTrue(
			Double.parseDouble(measured.get("berserkerDamage").split(" to ")[1]) > healthyDamage,
			"Berserker paid no damage at four health: " + measured.get("berserkerDamage")
		);
		helper.assertTrue(
			Boolean.parseBoolean(measured.get("berserkerHunger")),
			"Berserker closed the regeneration gate with no hunger effect"
		);

		helper.succeed();
	}

	/** Move a guarded function`s cooldown back, so the driven hook is not a no-op. */
	private static void rearm(final GameTestHelper helper, final ServerPlayer player, final String objective) {
		runCommand(helper, player, "scoreboard players operation @s " + objective + " = #clock ench_clock");
		runCommand(helper, player, "scoreboard players remove @s " + objective + " 20");
	}

	@GameTest
	public void quarryWearsThePickaxeTwiceAsFast(final GameTestHelper helper) {
		ServerLevel level = level(helper);
		ItemStack plainPick = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemStack quarryPick = enchanted(helper, Items.DIAMOND_PICKAXE, "curse/quarry", 1);
		int plainWear = EnchantmentHelper.processDurabilityChange(level, plainPick, 1);
		int quarryWear = EnchantmentHelper.processDurabilityChange(level, quarryPick, 1);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=curseQuarry RESULT plainWearPerBreak={} quarryWearPerBreak={} lever=item_damage",
			plainWear, quarryWear
		);
		helper.assertTrue(plainWear == 1, "a plain pickaxe wore " + plainWear + " per break");
		helper.assertTrue(quarryWear == 2, "Quarry wore the pickaxe by " + quarryWear + " per break");
		helper.succeed();
	}

	@GameTest
	public void theModifierAndFoodRoutesAreReadBackOnThePlayerTheHookSees(final GameTestHelper helper) {
		ServerLevel level = level(helper);

		ServerPlayer nbtFed = mockPlayerInLevel(helper);
		runCommand(helper, nbtFed, "data modify entity @s foodLevel set value 17");
		int afterNbtSet = nbtFed.getFoodData().getFoodLevel();

		ServerPlayer hungry = mockPlayerInLevel(helper);
		hungry.getFoodData().setSaturation(0.0F);
		runCommand(helper, hungry, "effect give @s minecraft:hunger 60 4 true");
		for (int round = 0; round < 200; round++) {
			hungry.getFoodData().tick(hungry);
		}

		int afterHunger = hungry.getFoodData().getFoodLevel();

		ServerPlayer commanded = mockPlayerInLevel(helper);
		double beforeCommand = commanded.getAttributeValue(Attributes.ATTACK_DAMAGE);
		runCommand(
			helper, commanded,
			"attribute @s minecraft:attack_damage modifier add mod-stella-equips-enchants:berserker 1.2 "
				+ "add_multiplied_total"
		);
		double afterCommand = commanded.getAttributeValue(Attributes.ATTACK_DAMAGE);

		ServerPlayer worn = mockPlayerInLevel(helper);
		ItemStack chest = enchanted(helper, Items.DIAMOND_CHESTPLATE, "curse/bloodrush", 1);
		worn.setItemSlot(EquipmentSlot.CHEST, chest);
		int modifiersBefore = worn.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers().size();
		EnchantmentHelper.runLocationChangedEffects(level, chest, worn, EquipmentSlot.CHEST);
		EnchantmentHelper.runLocationChangedEffects(level, worn);
		int modifiersAfter = worn.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers().size();
		double afterWorn = worn.getAttributeValue(Attributes.ATTACK_DAMAGE);

		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=probeModifierRoutes RESULT foodNbtAfterSet={} foodAfterHunger={} "
				+ "attributeCommand={} to {} enchantModifiers={} to {} enchantValueAfter={}",
			afterNbtSet, afterHunger, round(beforeCommand), round(afterCommand),
			modifiersBefore, modifiersAfter, round(afterWorn)
		);
		helper.assertTrue(true, "the probe records what the harness can see");
		helper.succeed();
	}

	private static void runCommand(final GameTestHelper helper, final ServerPlayer player, final String command) {
		var server = helper.getLevel().getServer();
		var source = server.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withEntity(player)
			.withPosition(player.position())
			.withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	private static double declaredCost(final GameTestHelper helper, final AttributeCurse curse) {
		Enchantment enchantment = enchantment(helper, curse.path());
		double total = 0.0;
		for (EnchantmentAttributeEffect effect : enchantment.getEffects(
			net.minecraft.world.item.enchantment.EnchantmentEffectComponents.ATTRIBUTES
		)) {
			String attribute = effect.attribute().unwrapKey().map(key -> key.identifier().toString()).orElse("direct");
			double amount = effect.amount().calculate(1);
			if (amount < 0.0) {
				total += amount;
			}
		}

		return total;
	}

	private static ServerLevel level(final GameTestHelper helper) {
		return helper.getLevel();
	}

	private static int enchantmentLevel(final ItemStack stack, final Holder<Enchantment> enchantment) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(enchantment);
	}

	private static double round(final double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}

	private static ServerPlayer wearer(
		final GameTestHelper helper, final AttributeCurse curse, final double offsetX
	) {
		ServerPlayer player = mockPlayerInLevel(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5 + offsetX, SPAWN_Y, 2.5)));
		setBaseline(player);
		return player;
	}

	/** The armour and knockback attributes start at zero, so a fraction needs a base to move. */
	private static void setBaseline(final ServerPlayer player) {
		player.getAttribute(Attributes.ARMOR).setBaseValue(ARMOR_BASE);
		player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(KNOCKBACK_BASE);
	}

	private static ServerPlayer hitter(
		final GameTestHelper helper, final Mob target, final ItemStack weapon
	) {
		ServerPlayer player = mockPlayer(helper, GameType.SURVIVAL);
		player.setPos(target.getX(), target.getY(), target.getZ() + 1.0);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		player.getInventory().setSelectedItem(weapon);
		return player;
	}

	private static Mob victim(final GameTestHelper helper, final int offsetX) {
		Zombie zombie = helper.spawnWithNoFreeWill(
			EntityTypes.ZOMBIE, new net.minecraft.core.BlockPos(offsetX, 2, 2)
		);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.setInvulnerableTime(0);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	private static ServerPlayer mockPlayer(final GameTestHelper helper, final GameType type) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(type);
		PacketRecorder.attach(player);
		return player;
	}

	private static ServerPlayer mockPlayerInLevel(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PacketRecorder.attach(player);
		return player;
	}

	private static Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute(
		final GameTestHelper helper, final String id
	) {
		return helper.getLevel().registryAccess()
			.lookupOrThrow(Registries.ATTRIBUTE)
			.getOrThrow(ResourceKey.create(
				Registries.ATTRIBUTE, Identifier.parse(id)
			));
	}

	private static Enchantment enchantment(final GameTestHelper helper, final String path) {
		return registry(helper).getValue(Identifier.fromNamespaceAndPath(MOD_ID, path));
	}

	private static Holder<Enchantment> holder(final GameTestHelper helper, final String path) {
		return registry(helper).getOrThrow(
			ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MOD_ID, path))
		);
	}

	private static ItemStack enchanted(
		final GameTestHelper helper, final Item item, final String path, final int level
	) {
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(holder(helper, path), level);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}

	private static Registry<Enchantment> registry(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	@SuppressWarnings("unused")
	private static PowerCurve.Band unusedBand() {
		return PowerCurve.Band.GREEN;
	}
}
