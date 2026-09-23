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
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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
	private static final int CATALOGUE_SIZE = 44;
	private static final int ARROW_SHOWER_ARROWS = 7;
	private static final int PLAIN_SHOT_DURABILITY = 1;
	private static final int MAXED_TABLE_COST = 30;
	private static final int MIN_COOLDOWN_TICKS = 20;
	private static final int FLOOR_Y = 2;
	private static final int SPAWN_Y = 3;
	private static final float WOUNDED_HEALTH = 4.0F;
	private static final int CLOCK_START = 10000;
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
		"give", "loot", "clear", "item replace entity @a", "effect give @a", "tp @a", "summon minecraft:item"
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
	public void catalogueRollRatesFollowCostAndWeight(final GameTestHelper helper) {
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
			"[BEHAVIOR] test=rollRates RESULT power={} pools={} covered={} smallestShare={} largestShare={}",
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
	public void arrowShowerFiresTheWholeVolley(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player shooter = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(new Vec3(4.5, 4.0, 4.5));
		shooter.setPos(origin.x, origin.y, origin.z);
		ItemStack bow = enchanted(Items.BOW, enchantment(helper, "bow/arrow_shower"), 1);
		shooter.setItemInHand(InteractionHand.MAIN_HAND, bow);
		shooter.getInventory().add(new ItemStack(Items.ARROW, 64));
		Items.BOW.releaseUsing(bow, level, shooter, 0);
		List<AbstractArrow> arrows = arrowsOf(level, shooter);
		int durabilityAdded = shooter.getMainHandItem().getDamageValue();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=arrowShowerVolley RESULT arrowsFired={} durabilityAdded={} plainShotDurability={}",
			arrows.size(), durabilityAdded, PLAIN_SHOT_DURABILITY
		);
		helper.assertTrue(
			arrows.size() == ARROW_SHOWER_ARROWS,
			"arrow shower fired " + arrows.size() + " arrows: expected " + ARROW_SHOWER_ARROWS
		);
		helper.assertTrue(
			durabilityAdded > PLAIN_SHOT_DURABILITY,
			"the bow lost " + durabilityAdded + " durability, which is not more than a plain shot"
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
	public void cleaveBlastReachesANearbyMobAndSparesTheWielder(final GameTestHelper helper) {
		buildFloor(helper);
		Mob wielder = helper.spawnMob(EntityTypes.ZOMBIE, 0, SPAWN_Y, 3).withNoFreeWill().spawn();
		Mob victim = helper.spawnMob(EntityTypes.ZOMBIE, 3, SPAWN_Y, 3).withNoFreeWill().spawn();
		Mob bystander = helper.spawnMob(EntityTypes.ZOMBIE, 3, SPAWN_Y, 4).withNoFreeWill().spawn();
		Enchantment cleave = enchantment(helper, "weapon/cleave");
		wielder.setItemInHand(InteractionHand.MAIN_HAND, enchanted(Items.DIAMOND_AXE, cleave, 1));
		float wielderBefore = wielder.getHealth();
		float bystanderBefore = bystander.getHealth();
		boolean hit = wielder.doHurtTarget(helper.getLevel(), victim);
		float wielderLost = wielderBefore - wielder.getHealth();
		float bystanderLost = bystanderBefore - bystander.getHealth();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=cleaveBlast RESULT hit={} wielderLost={} bystanderLost={} victimLost={}",
			hit, wielderLost, bystanderLost, victim.getMaxHealth() - victim.getHealth()
		);
		helper.assertTrue(hit, "the axe swing did not land on the victim");
		helper.assertTrue(bystanderLost > 0.0F, "the cleave blast did not reach the bystander one block away");
		helper.assertTrue(wielderLost == 0.0F, "the cleave blast damaged its own wielder by " + wielderLost);
		helper.succeed();
	}

	@GameTest
	public void rangersMarkPaysOnlyAtFullHealthAndObeysItsCooldown(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		runCommand(server, level, null, "scoreboard players set #clock ench_clock " + CLOCK_START);
		buildFloor(helper);
		Mob healthy = helper.spawnMob(EntityTypes.ZOMBIE, 1, SPAWN_Y, 1).withNoFreeWill().spawn();
		Mob wounded = helper.spawnMob(EntityTypes.ZOMBIE, 3, SPAWN_Y, 1).withNoFreeWill().spawn();
		wounded.setHealth(WOUNDED_HEALTH);
		String function = MOD_ID + ":rangers_mark";
		runFunction(server, level, healthy, function);
		float paidAtFullHealth = healthy.getMaxHealth() - healthy.getHealth();
		healthy.setHealth(healthy.getMaxHealth());
		runFunction(server, level, healthy, function);
		float paidInsideCooldown = healthy.getMaxHealth() - healthy.getHealth();
		runFunction(server, level, wounded, function);
		float paidToAWoundedVictim = WOUNDED_HEALTH - wounded.getHealth();
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=rangersMarkGate RESULT paidAtFullHealth={} paidInsideCooldown={} paidToAWoundedVictim={}",
			paidAtFullHealth, paidInsideCooldown, paidToAWoundedVictim
		);
		helper.assertTrue(paidAtFullHealth > 0.0F, "the full health victim took no bonus damage");
		helper.assertTrue(paidInsideCooldown == 0.0F, "the cooldown paid again inside its window");
		helper.assertTrue(paidToAWoundedVictim == 0.0F, "a wounded victim was paid the full health bonus");
		helper.succeed();
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
