package com.stella.equips;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.TargetedConditionalEffect;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.RunFunction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class EnchantDataGameTest {
	private static final String MOD_ID = StellaEquips.MOD_ID;
	private static final String FIXTURE_PATH = "test_marker";
	private static final int CATALOGUE_SIZE = 32;
	private static final int ARROW_SHOWER_ARROWS = 7;
	private static final int PLAIN_SHOT_DURABILITY = 1;
	private static final int MAXED_TABLE_COST = 30;
	private static final int MIN_COOLDOWN_TICKS = 20;
	private static final int FLOOR_Y = 2;
	private static final int SPAWN_Y = 3;
	private static final float WOUNDED_HEALTH = 4.0F;
	private static final int PARTIAL_DRAW = 71990;
	private static final double TRAVELLED_RESISTANCE = 0.3;
	private static final List<Item> POOL_ITEMS = List.of(
		Items.BOW,
		Items.CROSSBOW,
		Items.DIAMOND_SWORD,
		Items.DIAMOND_AXE,
		Items.DIAMOND_SPEAR,
		Items.TRIDENT,
		Items.MACE,
		Items.DIAMOND_PICKAXE,
		Items.DIAMOND_SHOVEL,
		Items.DIAMOND_HOE,
		Items.FISHING_ROD,
		Items.SHIELD,
		Items.ELYTRA,
		Items.DIAMOND_HELMET,
		Items.DIAMOND_CHESTPLATE,
		Items.DIAMOND_LEGGINGS,
		Items.DIAMOND_BOOTS
	);
	private static final Pattern COOLDOWN = Pattern.compile("#\\s*cooldown_ticks:\\s*(\\d+)");
	private static final List<Pattern> CAPS = List.of(
		Pattern.compile("#\\s*max_damage:\\s*(\\d+)"),
		Pattern.compile("#\\s*max_blocks:\\s*(\\d+)"),
		Pattern.compile("#\\s*max_entities:\\s*(\\d+)"),
		Pattern.compile("#\\s*max_effects:\\s*(\\d+)"),
		Pattern.compile("#\\s*max_particles:\\s*(\\d+)")
	);
	private static final List<String> BANNED_COMMANDS = List.of(
		"give", "loot", "clear", "item replace entity @a", "effect give @a",
		"tp @a", "tp @s", "tp @e", "tp @p", "tp @r", "summon minecraft:item"
	);

	@GameTest
	public void everyCatalogueEnchantmentLoads(final GameTestHelper helper) {
		Registry<Enchantment> registry = enchantments(helper);
		int catalogue = catalogue(registry).size();
		long inNamespace = registry.listElements()
			.filter(holder -> MOD_ID.equals(holder.key().identifier().getNamespace()))
			.count();
		long fixtures = inNamespace - catalogue;
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=catalogueLoaded RESULT catalogueCount={} namespaceCount={} fixtures={}",
			catalogue, inNamespace, fixtures
		);
		helper.assertTrue(
			catalogue == CATALOGUE_SIZE,
			"catalogue count: expected " + CATALOGUE_SIZE + " but was " + catalogue + " in namespace " + MOD_ID
		);
		helper.succeed();
	}

	@GameTest
	public void everyCatalogueEnchantmentAssemblesForOneLevel(final GameTestHelper helper) {
		List<Holder.Reference<Enchantment>> catalogue = catalogue(enchantments(helper));
		List<String> offenders = new ArrayList<>();
		int highest = 0;
		for (Holder.Reference<Enchantment> holder : catalogue) {
			int anvilCost = holder.value().getAnvilCost();
			highest = Math.max(highest, anvilCost);
			if (anvilCost > 1) {
				offenders.add(holder.key().identifier() + "=" + anvilCost);
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=anvilCostCeiling RESULT checked={} highestAnvilCost={} offenders={}",
			catalogue.size(), highest, offenders.size()
		);
		helper.assertTrue(offenders.isEmpty(), "anvil_cost above 1: " + offenders);
		helper.succeed();
	}

	@GameTest
	public void everySupportedItemsTagResolvesToItems(final GameTestHelper helper) {
		List<Holder.Reference<Enchantment>> catalogue = catalogue(enchantments(helper));
		List<String> offenders = new ArrayList<>();
		int smallest = Integer.MAX_VALUE;
		int largest = 0;
		for (Holder.Reference<Enchantment> holder : catalogue) {
			HolderSet<Item> supported = holder.value().getSupportedItems();
			int size = supported.size();
			if (size == 0) {
				offenders.add(holder.key().identifier().toString());
			}
			smallest = Math.min(smallest, size);
			largest = Math.max(largest, size);
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=supportedItemsResolve RESULT checked={} smallestSet={} largestSet={} emptySets={}",
			catalogue.size(), smallest == Integer.MAX_VALUE ? 0 : smallest, largest, offenders.size()
		);
		helper.assertTrue(offenders.isEmpty(), "supported_items resolved to nothing: " + offenders);
		helper.succeed();
	}

	@GameTest
	public void everyCatalogueEnchantmentIsInARollPool(final GameTestHelper helper) {
		Registry<Enchantment> registry = enchantments(helper);
		List<Holder.Reference<Enchantment>> catalogue = catalogue(registry);
		List<String> missing = new ArrayList<>();
		int pooled = 0;
		for (Holder.Reference<Enchantment> holder : catalogue) {
			String via = null;
			for (Item item : POOL_ITEMS) {
				if (poolFor(registry, new ItemStack(item)).contains(holder)) {
					via = itemName(item);
					break;
				}
			}
			if (via == null) {
				missing.add(holder.key().identifier().toString());
			} else {
				pooled++;
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=rollPoolMembership RESULT checked={} pooled={} missing={} shieldEnchantable={} elytraEnchantable={}",
			catalogue.size(), pooled, missing.size(), enchantableValue(Items.SHIELD), enchantableValue(Items.ELYTRA)
		);
		helper.assertTrue(missing.isEmpty(), "not in any EnchantRoller pool: " + missing);
		helper.succeed();
	}

	@GameTest
	public void everyFunctionBackedEnchantmentObeysTheFunctionRules(final GameTestHelper helper) {
		List<String> offenders = new ArrayList<>();
		int functions = 0;
		for (Holder.Reference<Enchantment> holder : catalogue(enchantments(helper))) {
			for (Identifier function : runFunctions(holder.value())) {
				functions++;
				String path = "data/" + function.getNamespace() + "/function/" + function.getPath() + ".mcfunction";
				Optional<CommandFunction<CommandSourceStack>> loaded = helper.getLevel().getServer().getFunctions().get(function);
				if (loaded.isEmpty()) {
					offenders.add(function + " is not in the function manager");
					continue;
				}
				String body = readResource(path);
				if (body == null) {
					offenders.add(path + " is unreadable");
					continue;
				}
				Matcher cooldown = COOLDOWN.matcher(body);
				if (!cooldown.find()) {
					offenders.add(function + " declares no cooldown_ticks");
					continue;
				}
				int ticks = Integer.parseInt(cooldown.group(1));
				if (ticks < MIN_COOLDOWN_TICKS) {
					offenders.add(function + " cooldown_ticks " + ticks + " is below " + MIN_COOLDOWN_TICKS);
				}
				if (!body.contains("scoreboard players operation")) {
					offenders.add(function + " holds no scoreboard cooldown");
				}
				if (!body.contains("matches " + ticks + "..")) {
					offenders.add(function + " does not guard its body on " + ticks + " ticks");
				}
				if (capValue(body) < 0) {
					offenders.add(function + " declares no per-activation cap");
				}
				for (String banned : BANNED_COMMANDS) {
					if (Pattern.compile("(?m)(^|run )" + Pattern.quote(banned) + " ").matcher(body).find()) {
						offenders.add(function + " uses the banned command " + banned);
					}
				}
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=functionRules RESULT functions={} violations={}",
			functions, offenders.size()
		);
		helper.assertTrue(functions > 0, "no run_function effect is present in the catalogue");
		helper.assertTrue(offenders.isEmpty(), "function rule violations: " + offenders);
		helper.succeed();
	}

	@GameTest
	public void catalogueWeightSharesAtCost30(final GameTestHelper helper) {
		Registry<Enchantment> registry = enchantments(helper);
		List<Holder.Reference<Enchantment>> catalogue = catalogue(registry);
		StringBuilder pools = new StringBuilder();
		List<String> uncovered = new ArrayList<>();
		List<String> badSums = new ArrayList<>();
		double smallest = 1.0;
		double largest = 0.0;
		for (Item item : POOL_ITEMS) {
			List<Holder.Reference<Enchantment>> candidates = candidatesFor(catalogue, item);
			if (candidates.isEmpty()) {
				continue;
			}
			int totalWeight = candidates.stream().mapToInt(holder -> holder.value().getWeight()).sum();
			double sum = 0.0;
			for (Holder.Reference<Enchantment> candidate : candidates) {
				double share = (double) candidate.value().getWeight() / totalWeight;
				sum += share;
				smallest = Math.min(smallest, share);
				largest = Math.max(largest, share);
			}
			if (Math.abs(sum - 1.0) > 0.0005) {
				badSums.add(itemName(item) + "=" + sum);
			}
			pools.append(itemName(item)).append(':').append(candidates.size()).append('/').append(totalWeight).append(' ');
		}
		for (Holder.Reference<Enchantment> holder : catalogue) {
			boolean reachable = false;
			for (Item item : POOL_ITEMS) {
				if (candidatesFor(catalogue, item).contains(holder)) {
					reachable = true;
					break;
				}
			}
			if (!reachable) {
				uncovered.add(holder.key().identifier().toString());
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=weightShares RESULT cost={} pools={} covered={} smallestShare={} largestShare={}",
			MAXED_TABLE_COST, pools.toString().trim(), catalogue.size() - uncovered.size(), smallest, largest
		);
		helper.assertTrue(badSums.isEmpty(), "candidate weight shares do not sum to 1: " + badSums);
		helper.assertTrue(uncovered.isEmpty(), "never a table candidate at cost " + MAXED_TABLE_COST + ": " + uncovered);
		helper.succeed();
	}

	@GameTest
	public void everyCatalogueEnchantmentIsOfferedAtTheEnchantingTable(final GameTestHelper helper) {
		Registry<Enchantment> registry = enchantments(helper);
		List<Holder.Reference<Enchantment>> catalogue = catalogue(registry);
		Optional<HolderSet.Named<Enchantment>> pool = registry.get(EnchantmentTags.IN_ENCHANTING_TABLE);
		List<String> missing = new ArrayList<>();
		List<String> unreachable = new ArrayList<>();
		int inTag = 0;
		int reachable = 0;
		for (Holder.Reference<Enchantment> holder : catalogue) {
			if (pool.isPresent() && pool.get().contains(holder)) {
				inTag++;
			} else {
				missing.add(holder.key().identifier().toString());
			}
			boolean candidate = false;
			for (Item item : POOL_ITEMS) {
				if (isTableCandidate(holder.value(), new ItemStack(item))) {
					candidate = true;
					break;
				}
			}
			if (candidate) {
				reachable++;
			} else {
				unreachable.add(holder.key().identifier().toString());
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=enchantingTableTag RESULT catalogueCount={} inTag={} missing={} reachableAtCost{}={}",
			catalogue.size(), inTag, missing.size(), MAXED_TABLE_COST, reachable
		);
		helper.assertTrue(
			missing.isEmpty(),
			"absent from #minecraft:in_enchanting_table: " + missing
		);
		helper.assertTrue(
			unreachable.isEmpty(),
			"never an offer at cost " + MAXED_TABLE_COST + ": " + unreachable
		);
		helper.succeed();
	}

	@GameTest
	public void everyCatalogueEnchantmentHasALangName(final GameTestHelper helper) {
		String lang = readResource("assets/" + MOD_ID + "/lang/en_us.json");
		List<String> missing = new ArrayList<>();
		for (Holder.Reference<Enchantment> holder : catalogue(enchantments(helper))) {
			Identifier id = holder.key().identifier();
			String key = "enchantment." + id.getNamespace() + "." + id.getPath().replace('/', '.');
			if (lang == null || !lang.contains("\"" + key + "\"")) {
				missing.add(key);
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=langNames RESULT catalogueCount={} langFileRead={} missing={}",
			catalogue(enchantments(helper)).size(), lang != null, missing.size()
		);
		helper.assertTrue(missing.isEmpty(), "no lang key for: " + missing);
		helper.succeed();
	}

	@GameTest
	public void greenThumbTillsTheGroundAroundTheBrokenBlock(final GameTestHelper helper) {
		buildFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer tiller = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(new Vec3(2.5, SPAWN_Y + 1, 2.5));
		tiller.setPos(origin.x, origin.y, origin.z);
		ItemStack hoe = enchanted(Items.DIAMOND_HOE, enchantment(helper, "tool/green_thumb"), 1);
		tiller.setItemInHand(InteractionHand.MAIN_HAND, hoe);
		for (int x = 1; x < 4; x++) {
			for (int z = 1; z < 4; z++) {
				helper.setBlock(x, SPAWN_Y, z, Blocks.GRASS_BLOCK);
			}
		}
		BlockPos hit = BlockPos.containing(helper.absoluteVec(new Vec3(2.5, SPAWN_Y + 0.5, 2.5)));
		tiller.gameMode.handleBlockBreakAction(
			hit, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 0
		);
		int farmland = 0;
		for (int x = 1; x < 4; x++) {
			for (int z = 1; z < 4; z++) {
				BlockPos pos = BlockPos.containing(helper.absoluteVec(new Vec3(x + 0.5, SPAWN_Y + 0.5, z + 0.5)));
				if (level.getBlockState(pos).is(Blocks.FARMLAND)) {
					farmland++;
				}
			}
		}
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=greenThumbTill RESULT tilledNeighbours={} of=8", farmland
		);
		helper.assertTrue(farmland > 0, "the broken block tilled no neighbour");
		helper.succeed();
	}

	@GameTest
	public void marksmanArrowsHitHarderThanPlainArrows(final GameTestHelper helper) {
		buildFloor(helper);
		ServerLevel level = helper.getLevel();
		Player shooter = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(new Vec3(0.5, SPAWN_Y, 0.5));
		shooter.setPos(origin.x, origin.y, origin.z);
		shooter.setXRot(0.0F);
		ItemStack plain = new ItemStack(Items.BOW);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, plain);
		shooter.getInventory().add(new ItemStack(Items.ARROW, 64));
		Mob first = helper.spawnMob(EntityTypes.RAVAGER, 0, SPAWN_Y, 4).withNoFreeWill().spawn();
		Mob second = helper.spawnMob(EntityTypes.RAVAGER, 3, SPAWN_Y, 4).withNoFreeWill().spawn();
		shooter.setYRot(0.0F);
		Items.BOW.releaseUsing(plain, level, shooter, PARTIAL_DRAW);
		ItemStack marksman = enchanted(Items.BOW, enchantment(helper, "bow/marksman"), 1);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, marksman);
		shooter.setYRot(-36.87F);
		Items.BOW.releaseUsing(marksman, level, shooter, PARTIAL_DRAW);
		helper.runAfterDelay(8L, () -> {
			float plainDamage = first.getMaxHealth() - first.getHealth();
			float marksmanDamage = second.getMaxHealth() - second.getHealth();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=marksmanArrow RESULT plainDamage={} marksmanDamage={} gain={}",
				plainDamage, marksmanDamage, marksmanDamage - plainDamage
			);
			helper.assertTrue(plainDamage > 0.0F, "the plain arrow missed");
			helper.assertTrue(marksmanDamage > 0.0F, "the marksman arrow missed");
			helper.assertTrue(
				marksmanDamage - plainDamage >= 0.5F,
				"the marksman bow hit no harder than the plain bow"
			);
			helper.succeed();
		});
	}

	@GameTest
	public void repeaterShortensTheCrossbowCharge(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player shooter = helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack repeaterStack = enchanted(Items.CROSSBOW, enchantment(helper, "crossbow/repeater"), 2);
		int plain = CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), shooter);
		int repeater = CrossbowItem.getChargeDuration(repeaterStack, shooter);
		int plainWear = EnchantmentHelper.processDurabilityChange(level, new ItemStack(Items.CROSSBOW), 1);
		int repeaterWear = EnchantmentHelper.processDurabilityChange(level, repeaterStack, 1);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=repeaterCharge RESULT plainTicks={} repeaterTicks={} saved={} plainWear={} repeaterWear={}",
			plain, repeater, plain - repeater, plainWear, repeaterWear
		);
		helper.assertTrue(
			repeater < plain,
			"the repeater crossbow charges in " + repeater + " ticks, no faster than " + plain
		);
		helper.assertTrue(
			repeaterWear == plainWear + 1,
			"one wear event costs the repeater " + repeaterWear + " against the plain " + plainWear
		);
		helper.succeed();
	}

	@GameTest
	public void bulwarkAndAegisAddResistanceAboveThePowerTerm(final GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		AttributeInstance instance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		instance.setBaseValue(TRAVELLED_RESISTANCE);
		ItemStack chestplate = enchanted(Items.DIAMOND_CHESTPLATE, enchantment(helper, "armor/bulwark"), 2);
		ItemStack shield = enchanted(Items.SHIELD, enchantment(helper, "shield/aegis"), 2);
		EnchantmentHelper.forEachModifier(chestplate, EquipmentSlot.CHEST, (attribute, modifier) -> {
			if (attribute.value() == Attributes.KNOCKBACK_RESISTANCE.value()) {
				instance.addTransientModifier(modifier);
			}
		});
		double withBulwark = instance.getValue();
		EnchantmentHelper.forEachModifier(shield, EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.value() == Attributes.KNOCKBACK_RESISTANCE.value()) {
				instance.addTransientModifier(modifier);
			}
		});
		double withBoth = instance.getValue();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=resistanceHeadroom RESULT powerTerm={} withBulwark={} withBoth={} headroom={}",
			TRAVELLED_RESISTANCE, withBulwark, withBoth, 1.0 - withBoth
		);
		helper.assertTrue(
			withBulwark > TRAVELLED_RESISTANCE,
			"Bulwark added no resistance on top of the power term at " + TRAVELLED_RESISTANCE
		);
		helper.assertTrue(withBoth > withBulwark, "Aegis added no resistance on top of Bulwark");
		helper.assertTrue(
			withBoth < 1.0,
			"the two enchantments saturate the attribute ceiling at " + withBoth
		);
		helper.succeed();
	}

	@GameTest
	public void arrowShowerCostsMoreDurabilityThanAPlainShot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player shooter = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(new Vec3(4.5, 4.0, 4.5));
		shooter.setPos(origin.x, origin.y, origin.z);
		ItemStack plain = new ItemStack(Items.BOW);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, plain);
		shooter.getInventory().add(new ItemStack(Items.ARROW, 64));
		int plainArrowsBefore = arrowsOf(level, shooter).size();
		Items.BOW.releaseUsing(plain, level, shooter, 0);
		int plainArrows = arrowsOf(level, shooter).size() - plainArrowsBefore;
		int plainWear = shooter.getMainHandItem().getDamageValue();
		ItemStack shower = enchanted(Items.BOW, enchantment(helper, "bow/arrow_shower"), 1);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, shower);
		int showerArrowsBefore = arrowsOf(level, shooter).size();
		Items.BOW.releaseUsing(shower, level, shooter, 0);
		int showerArrows = arrowsOf(level, shooter).size() - showerArrowsBefore;
		int showerWear = shooter.getMainHandItem().getDamageValue();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=arrowShowerCost RESULT plainArrows={} plainWear={} showerArrows={} showerWear={}",
			plainArrows, plainWear, showerArrows, showerWear
		);
		helper.assertTrue(plainArrows == 1, "a plain bow fired " + plainArrows + " arrows: expected 1");
		helper.assertTrue(
			showerArrows == ARROW_SHOWER_ARROWS,
			"arrow shower fired " + showerArrows + " arrows: expected " + ARROW_SHOWER_ARROWS
		);
		helper.assertTrue(
			showerWear == 2 * showerArrows,
			"the arrow shower bow lost " + showerWear + " durability for " + showerArrows
				+ " arrows: expected twice the arrow count, so the item_damage cost is gone"
		);
		helper.succeed();
	}

	@GameTest
	public void bombArrowsBlastLeavesTheBlockIntactAndStillHurtsAMob(final GameTestHelper helper) {
		buildFloor(helper);
		ServerLevel level = helper.getLevel();
		Player shooter = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(new Vec3(0.5, SPAWN_Y, 0.5));
		shooter.setPos(origin.x, origin.y, origin.z);
		shooter.setYRot(0.0F);
		shooter.setXRot(0.0F);
		ItemStack bow = enchanted(Items.BOW, enchantment(helper, "bow/bomb_arrows"), 1);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, bow);
		shooter.getInventory().add(new ItemStack(Items.ARROW, 64));
		for (int x = 0; x < 3; x++) {
			helper.setBlock(x, SPAWN_Y, 3, Blocks.STONE);
			helper.setBlock(x, SPAWN_Y + 1, 3, Blocks.STONE);
		}
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 1.5F, SPAWN_Y, 2.0F).withNoFreeWill().spawn();
		float targetBefore = target.getHealth();
		Items.BOW.releaseUsing(bow, level, shooter, 0);
		BlockPos wall = BlockPos.containing(helper.absoluteVec(new Vec3(1.0, SPAWN_Y + 0.5, 3.5)));
		helper.runAfterDelay(10L, () -> {
			boolean intact = level.getBlockState(wall).is(Blocks.STONE);
			List<AbstractArrow> arrows = arrowsOf(level, shooter);
			double closest = arrows.isEmpty() ? -1.0 : arrows.get(0).distanceToSqr(
				helper.absoluteVec(new Vec3(1.0, SPAWN_Y + 0.5, 3.0))
			);
			float targetLost = targetBefore - target.getHealth();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=bombArrowsBlast RESULT blockInteraction=none wallIntact={} arrows={} closestImpactSqr={} targetLost={}",
				intact, arrows.size(), closest, targetLost
			);
			helper.assertTrue(intact, "the blast broke the block it hit at " + wall);
			helper.assertTrue(!arrows.isEmpty(), "the arrow never reached the wall");
			helper.assertTrue(
				closest >= 0.0 && closest < 4.0,
				"the arrow came to rest " + closest + " squared blocks from the impact"
			);
			helper.succeed();
		});
	}

	@GameTest
	public void hookedTridentSlowsTheTargetItHits(final GameTestHelper helper) {
		buildFloor(helper);
		ServerLevel level = helper.getLevel();
		Player thrower = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(new Vec3(0.5, SPAWN_Y, 0.5));
		thrower.setPos(origin.x, origin.y, origin.z);
		ItemStack tridentStack = enchanted(Items.TRIDENT, enchantment(helper, "weapon/hooked"), 2);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 0, SPAWN_Y, 4).withNoFreeWill().spawn();
		ThrownTrident trident = new ThrownTrident(level, thrower, tridentStack);
		Vec3 start = helper.absoluteVec(new Vec3(0.5, SPAWN_Y + 1.0, 0.5));
		trident.setPos(start.x, start.y, start.z);
		trident.shoot(0.0, 0.0, 1.0, 2.0F, 0.0F);
		level.addFreshEntity(trident);
		helper.runAfterDelay(8L, () -> {
			MobEffectInstance slowness = target.getEffect(MobEffects.SLOWNESS);
			float lost = target.getMaxHealth() - target.getHealth();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=hookedTrident RESULT targetLost={} slownessAmplifier={} slownessDuration={}",
				lost, slowness == null ? -1 : slowness.getAmplifier(), slowness == null ? 0 : slowness.getDuration()
			);
			helper.assertTrue(lost > 0.0F, "the thrown trident did not hit the target");
			helper.assertTrue(slowness != null, "the hooked trident left the target unslowed");
			helper.succeed();
		});
	}

	private static List<Holder.Reference<Enchantment>> candidatesFor(
		final List<Holder.Reference<Enchantment>> catalogue, final Item item
	) {
		ItemStack stack = new ItemStack(item);
		return catalogue.stream().filter(holder -> isTableCandidate(holder.value(), stack)).toList();
	}

	private static boolean isTableCandidate(final Enchantment enchantment, final ItemStack stack) {
		if (!enchantment.canEnchant(stack)) {
			return false;
		}
		for (int level = enchantment.getMinLevel(); level <= enchantment.getMaxLevel(); level++) {
			if (MAXED_TABLE_COST >= enchantment.getMinCost(level) && MAXED_TABLE_COST <= enchantment.getMaxCost(level)) {
				return true;
			}
		}
		return false;
	}

	private static List<Holder<Enchantment>> poolFor(final Registry<Enchantment> registry, final ItemStack stack) {
		return registry.listElements()
			.filter(holder -> holder.value().canEnchant(stack))
			.map(holder -> (Holder<Enchantment>) holder)
			.toList();
	}

	private static List<Identifier> runFunctions(final Enchantment enchantment) {
		List<Identifier> functions = new ArrayList<>();
		for (TargetedConditionalEffect<EnchantmentEntityEffect> entry
			: enchantment.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
			collectRunFunctions(entry.effect(), functions);
		}
		for (ConditionalEffect<EnchantmentEntityEffect> entry
			: enchantment.getEffects(EnchantmentEffectComponents.POST_PIERCING_ATTACK)) {
			collectRunFunctions(entry.effect(), functions);
		}
		for (ConditionalEffect<EnchantmentEntityEffect> entry
			: enchantment.getEffects(EnchantmentEffectComponents.HIT_BLOCK)) {
			collectRunFunctions(entry.effect(), functions);
		}
		for (ConditionalEffect<EnchantmentEntityEffect> entry : enchantment.getEffects(EnchantmentEffectComponents.TICK)) {
			collectRunFunctions(entry.effect(), functions);
		}
		for (ConditionalEffect<EnchantmentEntityEffect> entry
			: enchantment.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED)) {
			collectRunFunctions(entry.effect(), functions);
		}
		return functions;
	}

	private static void collectRunFunctions(final EnchantmentEntityEffect effect, final List<Identifier> functions) {
		if (effect instanceof RunFunction runFunction) {
			functions.add(runFunction.function());
		} else if (effect instanceof AllOf.EntityEffects allOf) {
			for (EnchantmentEntityEffect inner : allOf.effects()) {
				collectRunFunctions(inner, functions);
			}
		}
	}

	private static int capValue(final String body) {
		for (Pattern cap : CAPS) {
			Matcher matcher = cap.matcher(body);
			if (matcher.find()) {
				return Integer.parseInt(matcher.group(1));
			}
		}
		return -1;
	}

	private static String readResource(final String path) {
		try (InputStream stream = EnchantDataGameTest.class.getResourceAsStream("/" + path)) {
			if (stream == null) {
				return null;
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException error) {
			return null;
		}
	}

	private static void buildFloor(final GameTestHelper helper) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(x, FLOOR_Y, z, Blocks.STONE);
			}
		}
	}

	private static ItemStack enchanted(final Item item, final Enchantment enchantment, final int level) {
		ItemStack stack = new ItemStack(item);
		Holder<Enchantment> holder = Holder.direct(enchantment);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(holder, level);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return stack;
	}

	private static Enchantment enchantment(final GameTestHelper helper, final String path) {
		Registry<Enchantment> registry = enchantments(helper);
		Enchantment enchantment = registry.getValue(Identifier.fromNamespaceAndPath(MOD_ID, path));
		if (enchantment == null) {
			throw new IllegalStateException("the catalogue enchantment " + MOD_ID + ":" + path + " is missing");
		}
		return enchantment;
	}

	private static void runFunction(
		final MinecraftServer server, final ServerLevel level, final Entity entity, final String function
	) {
		runCommand(server, level, entity, "function " + function);
	}

	private static void runCommand(
		final MinecraftServer server, final ServerLevel level, final Entity entity, final String command
	) {
		CommandSourceStack source = server.createCommandSourceStack().withLevel(level);
		if (entity != null) {
			source = source.withEntity(entity).withPosition(entity.position());
		}
		server.getCommands().performPrefixedCommand(source, command);
	}

	private static List<Holder.Reference<Enchantment>> catalogue(final Registry<Enchantment> registry) {
		return registry.listElements()
			.filter(holder -> MOD_ID.equals(holder.key().identifier().getNamespace()))
			.filter(holder -> !FIXTURE_PATH.equals(holder.key().identifier().getPath()))
			.sorted(Comparator.comparing(holder -> holder.key().identifier().toString()))
			.toList();
	}

	private static Registry<Enchantment> enchantments(final GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static String itemName(final Item item) {
		return item.builtInRegistryHolder().key().identifier().getPath();
	}

	private static int enchantableValue(final Item item) {
		Enchantable enchantable = new ItemStack(item).get(DataComponents.ENCHANTABLE);
		return enchantable == null ? 0 : enchantable.value();
	}

	private static List<AbstractArrow> arrowsOf(final ServerLevel level, final Entity owner) {
		return level.getEntitiesOfClass(AbstractArrow.class, owner.getBoundingBox().inflate(64.0))
			.stream()
			.filter(arrow -> arrow.getOwner() == owner)
			.toList();
	}
}
