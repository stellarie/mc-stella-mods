package com.stella.equips;

import com.stella.equips.merchant.MerchantDef;
import com.stella.equips.merchant.MerchantKind;
import com.stella.equips.merchant.MerchantWeapons;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import com.stella.equips.refine.RefineStation;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The six merchant weapons. The expected rows and numbers below are the plan's table, written out by hand, so a mistyped
 * constant in the mod cannot move its own test. Refined stacks also carry the generic refine effects, so each case
 * that reads a refined number says what it adds.
 */
public class MerchantWeaponsGameTest {
	private static final String NAMESPACE = StellaEquips.MOD_ID;
	private static final float HIT = 10.0F;
	private static final float BIG_HEALTH = 500.0F;
	private static final float EPSILON = 1.0E-3F;
	/** The refine levels to probe, and how many milestones each has reached. */
	private static final int[] LEVELS = {0, 6, 7, 9, 10, 11, 12, 14, 15};
	private static final int[] REACHED = {0, 0, 1, 1, 2, 2, 3, 3, 4};

	private record Row(String id, String name, MerchantKind kind, Map<String, Integer> stats, Map<String, Integer> affixes) {
	}

	private static final List<Row> TABLE = List.of(
		new Row("soulreaver", "Soulreaver", MerchantKind.SWORD, Map.of("str", 10, "agi", 10, "luk", 10),
			Map.of("damage_flat", 40, "damage_pct", 120, "armor_pen", 120)),
		new Row("gravecleaver", "Gravecleaver", MerchantKind.AXE, Map.of("str", 10, "vit", 10, "dex", 10),
			Map.of("damage_flat", 40, "damage_pct", 120)),
		new Row("wraithstring", "Wraithstring", MerchantKind.BOW, Map.of("dex", 10, "agi", 10, "luk", 10),
			Map.of("damage_pct", 120, "armor_pen", 120)),
		new Row("hexbolt", "Hexbolt", MerchantKind.CROSSBOW, Map.of("dex", 10, "str", 10, "luk", 10),
			Map.of("damage_pct", 120, "armor_pen", 120)),
		new Row("wraithpike", "Wraithpike", MerchantKind.SPEAR, Map.of("str", 10, "agi", 10, "vit", 10),
			Map.of("damage_flat", 40, "armor_pen", 120)),
		new Row("archmage_staff", "Archmage's Staff", MerchantKind.STAFF, Map.of("int", 10, "dex", 10, "luk", 10),
			Map.of("matk_flat", 40, "matk_pct", 120, "magic_pen", 120))
	);

	private record Burst(Vec3 at, List<LivingEntity> hurt) {
	}

	/** The slash roll is forced per holder and every call is recorded; a holder that no test forced never wins. */
	private static final Map<UUID, Boolean> FORCED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Double>> CALLS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Burst>> BURSTS = new ConcurrentHashMap<>();

	static {
		RefineRolls.install();
		MerchantWeapons.setRoller((holder, random, chance) -> {
			CALLS.computeIfAbsent(holder.getUUID(), key -> new CopyOnWriteArrayList<>()).add(chance);
			return FORCED.getOrDefault(holder.getUUID(), false);
		});
		MerchantWeapons.setBurstListener((bolt, at, hurt) -> {
			if (bolt.getOwner() != null) {
				BURSTS.computeIfAbsent(bolt.getOwner().getUUID(), key -> new CopyOnWriteArrayList<>()).add(new Burst(at, hurt));
			}
		});
	}

	private static Row row(final String id) {
		return TABLE.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
	}

	private static Item item(final String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(NAMESPACE, id));
	}

	private static ItemStack stack(final String id, final int refine) {
		ItemStack stack = new ItemStack(item(id));
		if (refine > 0) {
			Refine.setLevel(stack, refine);
		}
		return stack;
	}

	private static ServerPlayer holder(final GameTestHelper helper, final double x) {
		return UniqueTestKit.survivor(helper, new Vec3(x, 1.0, 1.5));
	}

	private static Zombie foe(final GameTestHelper helper, final double x, final double z) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1.0, z));
		zombie.setNoGravity(true);
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BIG_HEALTH);
		zombie.setHealth(BIG_HEALTH);
		return zombie;
	}

	private static float lost(final LivingEntity entity) {
		return BIG_HEALTH - entity.getHealth();
	}

	private static void strike(final GameTestHelper helper, final ServerPlayer holder, final LivingEntity victim, final float amount) {
		ServerLevel level = helper.getLevel();
		victim.hurtServer(level, level.damageSources().playerAttack(holder), amount);
	}

	private static TagKey<Item> tag(final String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NAMESPACE, path));
	}

	// ---- the catalog ----

	@GameTest
	public void sixItemsExistWithNetheriteStats(final GameTestHelper helper) {
		int netheriteDurability = new ItemStack(Items.NETHERITE_SWORD).getMaxDamage();
		helper.assertTrue(netheriteDurability == 2031, "the netherite durability is " + netheriteDurability);
		helper.assertTrue(MerchantWeapons.all().size() == TABLE.size(), "the registry holds " + MerchantWeapons.all().size() + " weapons");
		List<String> seen = new ArrayList<>();
		for (Row row : TABLE) {
			ItemStack stack = stack(row.id(), 0);
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath(NAMESPACE, row.id())), row.id() + " is not registered");
			MerchantDef def = MerchantWeapons.def(stack);
			helper.assertTrue(def != null && def.kind() == row.kind() && def.name().equals(row.name()), row.id() + " def " + def);
			helper.assertTrue(stack.getMaxDamage() == netheriteDurability, row.id() + " durability " + stack.getMaxDamage());
			helper.assertTrue(Refine.material(stack) == Items.NETHERITE_INGOT, row.id() + " is not mended with netherite");
			helper.assertTrue(stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON) == Rarity.EPIC, row.id() + " rarity");
			helper.assertTrue(stack.getHoverName().getString().equals(row.name()), row.id() + " is named " + stack.getHoverName().getString());
			helper.assertTrue(stack.get(DataComponents.ENCHANTABLE) != null, row.id() + " cannot be enchanted");
			seen.add(row.id() + "=" + stack.getMaxDamage());
		}
		helper.assertTrue(new ItemStack(item("soulreaver")).is(ItemTags.SWORDS), "soulreaver is not a sword");
		helper.assertTrue(new ItemStack(item("gravecleaver")).is(ItemTags.AXES), "gravecleaver is not an axe");
		helper.assertTrue(new ItemStack(item("wraithpike")).is(ItemTags.SPEARS), "wraithpike is not a spear");
		helper.assertTrue(item("wraithstring") instanceof BowItem && item("hexbolt") instanceof CrossbowItem, "the bow classes");
		helper.assertTrue(new ItemStack(item("wraithstring")).is(tag("netherite_ranged")) && new ItemStack(item("hexbolt")).is(tag("netherite_ranged")),
			"the bow and the crossbow are not in netherite_ranged");
		helper.assertTrue(new ItemStack(item("archmage_staff")).is(tag("mage_staffs")), "the staff is not in mage_staffs");
		helper.assertTrue(Refine.isWeapon(new ItemStack(item("archmage_staff"))), "the staff does not count as a weapon");
		UniqueTestKit.result("merchantCatalog", "durability=" + seen);
		helper.succeed();
	}

	@GameTest
	public void everyItemHasADistinctIcon(final GameTestHelper helper) throws IOException {
		Set<String> faces = new HashSet<>();
		List<String> files = new ArrayList<>();
		for (Row row : TABLE) {
			files.add("textures/item/" + row.id() + ".png");
			files.add("items/" + row.id() + ".json");
			if (row.kind() == MerchantKind.BOW) {
				for (int frame = 0; frame < 3; frame++) {
					files.add("textures/item/" + row.id() + "_pulling_" + frame + ".png");
				}
			}
			if (row.kind() == MerchantKind.CROSSBOW) {
				for (String frame : List.of("_pulling_0", "_pulling_1", "_pulling_2", "_arrow", "_firework")) {
					files.add("textures/item/" + row.id() + frame + ".png");
				}
			}
			files.add("models/item/" + row.id() + ".json");
		}
		for (String file : files) {
			try (InputStream stream = MerchantWeaponsGameTest.class.getResourceAsStream("/assets/" + NAMESPACE + "/" + file)) {
				helper.assertTrue(stream != null, "the asset " + file + " is missing from the jar");
			}
		}
		for (Row row : TABLE) {
			try (InputStream stream = MerchantWeaponsGameTest.class.getResourceAsStream("/assets/" + NAMESPACE + "/textures/item/" + row.id() + ".png")) {
				BufferedImage icon = ImageIO.read(stream);
				helper.assertTrue(icon.getWidth() == 16 && icon.getHeight() == 16, row.id() + " icon is " + icon.getWidth() + "x" + icon.getHeight());
				int opaque = 0;
				StringBuilder face = new StringBuilder();
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						int argb = icon.getRGB(x, y);
						if ((argb >>> 24) > 0) {
							opaque++;
						}
						face.append(Integer.toHexString(argb)).append(',');
					}
				}
				helper.assertTrue(opaque >= 25, row.id() + " icon is almost empty: " + opaque + " pixels");
				helper.assertTrue(faces.add(face.toString()), row.id() + " shares its icon with another weapon");
			}
		}
		// The Wraithpike is a bare shaft: its model is the spear model with a spear texture and no head model.
		helper.assertTrue(MerchantWeaponsGameTest.class.getResource("/assets/" + NAMESPACE + "/models/item/wraithpike_in_hand.json") != null,
			"the wraithpike in-hand model is missing");
		UniqueTestKit.result("merchantIcons", "files=" + files.size() + " distinctIcons=" + faces.size());
		helper.succeed();
	}

	// ---- the fixed rolls ----

	@GameTest
	public void rollsAreFixedFromCreation(final GameTestHelper helper) {
		for (Row row : TABLE) {
			ItemStack first = stack(row.id(), 0);
			ItemStack second = stack(row.id(), 0);
			Map<String, Integer> stats = first.get(TestItems.STAT_ROLLS);
			Map<String, Integer> affixes = first.get(TestItems.AFFIX_ROLLS);
			helper.assertTrue(stats != null && stats.equals(row.stats()), row.id() + " stat rolls " + stats);
			helper.assertTrue(affixes != null && affixes.equals(row.affixes()), row.id() + " affix rolls " + affixes);
			helper.assertTrue(stats.equals(second.get(TestItems.STAT_ROLLS)) && affixes.equals(second.get(TestItems.AFFIX_ROLLS)), row.id() + " rolls differ between two stacks");
			ItemStack refined = stack(row.id(), 15);
			helper.assertTrue(stats.equals(refined.copy().get(TestItems.STAT_ROLLS)) && affixes.equals(refined.copy().get(TestItems.AFFIX_ROLLS)),
				row.id() + " lost its rolls through a refine");
		}
		UniqueTestKit.result("merchantRolls", "stats=" + stack("soulreaver", 0).get(TestItems.STAT_ROLLS) + " staff=" + stack("archmage_staff", 0).get(TestItems.AFFIX_ROLLS));
		helper.succeed();
	}

	@GameTest
	public void withoutTheRpgComponentsTheItemsStayPlain(final GameTestHelper helper) {
		for (MerchantDef def : MerchantWeapons.all()) {
			DataComponentMap.Builder builder = DataComponentMap.builder();
			MerchantWeapons.fixRolls(builder, def, path -> null);
			DataComponentMap built = builder.build();
			helper.assertTrue(built.isEmpty(), def.id() + " set components with no rpg mod: " + built);
		}
		helper.succeed();
	}

	// ---- Soulreaver ----

	@GameTest
	public void soulreaverSlashesForHalfTheHit(final GameTestHelper helper) {
		ServerPlayer lucky = holder(helper, 1.5);
		ServerPlayer plain = holder(helper, 2.5);
		lucky.setItemInHand(InteractionHand.MAIN_HAND, stack("soulreaver", 0));
		plain.setItemInHand(InteractionHand.MAIN_HAND, stack("soulreaver", 0));
		FORCED.put(lucky.getUUID(), true);
		FORCED.put(plain.getUUID(), false);
		Zombie slashed = foe(helper, 1.5, 5.5);
		Zombie struck = foe(helper, 5.5, 5.5);
		strike(helper, lucky, slashed, HIT);
		strike(helper, plain, struck, HIT);
		UniqueTestKit.result("soulreaverSlash", "slashed=" + lost(slashed) + " struck=" + lost(struck) + " chances=" + CALLS.get(lucky.getUUID()));
		helper.assertTrue(Math.abs(lost(slashed) - HIT * 1.5F) < EPSILON, "a proc took " + lost(slashed) + ", not 15");
		helper.assertTrue(Math.abs(lost(struck) - HIT) < EPSILON, "a miss took " + lost(struck) + ", not 10");
		helper.assertTrue(CALLS.get(lucky.getUUID()).equals(List.of(15.0)), "the slash chance was " + CALLS.get(lucky.getUUID()));
		helper.succeed();
	}

	@GameTest
	public void soulreaverMilestonesRaiseDamageAndLifesteal(final GameTestHelper helper) {
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < LEVELS.length; i++) {
			ServerPlayer wielder = holder(helper, 1.5);
			wielder.setItemInHand(InteractionHand.MAIN_HAND, stack("soulreaver", LEVELS[i]));
			FORCED.put(wielder.getUUID(), false);
			wielder.setHealth(1.0F);
			Zombie victim = foe(helper, 3.5, 5.5);
			strike(helper, wielder, victim, HIT);
			double expectedHit = HIT * (1.0 + 0.05 * REACHED[i]);
			double expectedHeal = expectedHit * 0.02 * REACHED[i];
			log.append('+').append(LEVELS[i]).append("=hit ").append(lost(victim)).append(" heal ").append(wielder.getHealth() - 1.0F).append(' ');
			helper.assertTrue(Math.abs(lost(victim) - expectedHit) < EPSILON, "+" + LEVELS[i] + " hit " + lost(victim) + ", expected " + expectedHit);
			helper.assertTrue(Math.abs(wielder.getHealth() - 1.0F - expectedHeal) < EPSILON, "+" + LEVELS[i] + " healed " + (wielder.getHealth() - 1.0F) + ", expected " + expectedHeal);
			victim.discard();
		}
		UniqueTestKit.result("soulreaverMilestones", log.toString().trim());
		helper.succeed();
	}

	// ---- Gravecleaver ----

	@GameTest
	public void gravecleaverCleavesWithinThreeBlocks(final GameTestHelper helper) {
		double[] share = {0.5, 0.6, 0.7, 0.8};
		int[] levels = {0, 7, 10, 12};
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < levels.length; i++) {
			ServerPlayer wielder = holder(helper, 1.5);
			wielder.setItemInHand(InteractionHand.MAIN_HAND, stack("gravecleaver", levels[i]));
			Zombie target = foe(helper, 2.5, 2.5);
			Zombie near = foe(helper, 2.5, 5.0);
			Zombie far = foe(helper, 2.5, 7.5);
			strike(helper, wielder, target, HIT);
			log.append('+').append(levels[i]).append("=near ").append(lost(near)).append(" far ").append(lost(far)).append(' ');
			helper.assertTrue(Math.abs(lost(target) - HIT) < EPSILON, "+" + levels[i] + " the target took " + lost(target));
			helper.assertTrue(Math.abs(lost(near) - HIT * share[i]) < EPSILON, "+" + levels[i] + " the foe at 2.5 blocks took " + lost(near) + ", expected " + HIT * share[i]);
			helper.assertTrue(lost(far) == 0.0F, "+" + levels[i] + " the foe at 5 blocks took " + lost(far));
			target.discard();
			near.discard();
			far.discard();
		}
		UniqueTestKit.result("gravecleaverCleave", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void gravecleaverAtPlusFifteenCleavesNinetyPercent(final GameTestHelper helper) {
		ServerPlayer wielder = holder(helper, 1.5);
		wielder.setItemInHand(InteractionHand.MAIN_HAND, stack("gravecleaver", 15));
		Zombie target = foe(helper, 2.5, 2.5);
		Zombie near = foe(helper, 2.5, 5.0);
		Zombie far = foe(helper, 2.5, 7.5);
		strike(helper, wielder, target, HIT);
		// The +15 refine adds a shockwave of its own to foes near the target, so the foe at 2.5 blocks takes at least the cleave.
		UniqueTestKit.result("gravecleaverPlus15", "near=" + lost(near) + " far=" + lost(far));
		helper.assertTrue(lost(near) >= HIT * 0.9F - EPSILON, "the foe at 2.5 blocks took " + lost(near) + ", under 90% of the hit");
		helper.assertTrue(lost(far) == 0.0F, "the foe at 5 blocks took " + lost(far));
		helper.succeed();
	}

	@GameTest
	public void gravecleaverCleavesOnTheKillingBlow(final GameTestHelper helper) {
		ServerPlayer wielder = holder(helper, 1.5);
		wielder.setItemInHand(InteractionHand.MAIN_HAND, stack("gravecleaver", 0));
		Zombie target = foe(helper, 2.5, 2.5);
		target.setHealth(4.0F);
		Zombie near = foe(helper, 2.5, 5.0);
		strike(helper, wielder, target, HIT);
		UniqueTestKit.result("gravecleaverKill", "targetAlive=" + target.isAlive() + " near=" + lost(near));
		helper.assertTrue(!target.isAlive(), "the target survived a killing blow");
		helper.assertTrue(Math.abs(lost(near) - HIT * 0.5F) < EPSILON, "the foe near a killed target took " + lost(near));
		helper.succeed();
	}

	// ---- Wraithstring ----

	private static ServerPlayer archer(final GameTestHelper helper) {
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(4.5, 1.0, 0.5));
		player.setXRot(0.0F);
		player.setYRot(0.0F);
		player.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
		return player;
	}

	private static List<AbstractArrow> arrows(final ServerPlayer player) {
		return UniqueTestKit.arrowsOf(player.level(), player, player.position());
	}

	private static int arrowItems(final ServerPlayer player) {
		return player.getInventory().countItem(Items.ARROW);
	}

	/** Draws a bow for the given ticks and lets go. */
	private static void draw(final ServerPlayer player, final ItemStack bow, final int ticks) {
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		bow.getItem().releaseUsing(bow, player.level(), player, bow.getItem().getUseDuration(bow, player) - ticks);
	}

	@GameTest
	public void wraithstringDrawsTwentyFivePercentFaster(final GameTestHelper helper) {
		int full = MerchantWeapons.drawTicks();
		helper.assertTrue(full == 15, "a full draw takes " + full + " ticks, expected 15");
		ServerPlayer early = archer(helper);
		draw(early, stack("wraithstring", 0), full - 1);
		ServerPlayer exact = archer(helper);
		draw(exact, stack("wraithstring", 0), full);
		ServerPlayer vanilla = archer(helper);
		draw(vanilla, new ItemStack(Items.BOW), full);
		boolean earlyFull = !arrows(early).isEmpty() && arrows(early).get(0).isCritArrow();
		boolean exactFull = !arrows(exact).isEmpty() && arrows(exact).get(0).isCritArrow();
		boolean vanillaFull = !arrows(vanilla).isEmpty() && arrows(vanilla).get(0).isCritArrow();
		UniqueTestKit.result("wraithstringDraw", "ticks=" + full + " early=" + earlyFull + " exact=" + exactFull + " vanilla=" + vanillaFull);
		helper.assertTrue(!arrows(early).isEmpty() && !earlyFull, "the bow loosed full power one tick early");
		helper.assertTrue(exactFull, "the bow is not at full power after 15 ticks");
		helper.assertTrue(!arrows(vanilla).isEmpty() && !vanillaFull, "a vanilla bow is at full power after 15 ticks");
		helper.succeed();
	}

	private static AbstractArrow fullBowShot(final GameTestHelper helper, final ItemStack bow) {
		ServerPlayer player = archer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		bow.getItem().releaseUsing(bow, player.level(), player, 0);
		List<AbstractArrow> shot = arrows(player);
		helper.assertTrue(shot.size() == 1, "the bow loosed " + shot.size() + " arrows");
		return shot.get(0);
	}

	@GameTest
	public void wraithstringArrowsCarryTheMilestones(final GameTestHelper helper) {
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < LEVELS.length; i++) {
			AbstractArrow arrow = fullBowShot(helper, stack("wraithstring", LEVELS[i]));
			// A vanilla arrow has base damage 2. A refined bow adds 0.75 a level, and its Piercing adds one foe from +10.
			double expectedDamage = 2.0 * (1.2 + 0.05 * REACHED[i]) + 0.75 * LEVELS[i];
			int expectedPierce = 2 + (REACHED[i] == 4 ? 1 : 0) + (LEVELS[i] >= 10 ? 1 : 0);
			double damage = RefineEffects.baseDamage(arrow);
			log.append('+').append(LEVELS[i]).append("=dmg ").append(damage).append(" pierce ").append(arrow.getPierceLevel()).append(' ');
			helper.assertTrue(Math.abs(damage - expectedDamage) < 1.0E-6, "+" + LEVELS[i] + " arrow damage " + damage + ", expected " + expectedDamage);
			helper.assertTrue(arrow.getPierceLevel() == expectedPierce, "+" + LEVELS[i] + " pierce " + arrow.getPierceLevel() + ", expected " + expectedPierce);
			arrow.discard();
		}
		UniqueTestKit.result("wraithstringMilestones", log.toString().trim());
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void wraithstringArrowPassesTwoFoesAndHitsAThird(final GameTestHelper helper) {
		ServerPlayer player = archer(helper);
		Zombie first = foe(helper, 4.5, 3.5);
		Zombie second = foe(helper, 4.5, 5.5);
		Zombie third = foe(helper, 4.5, 7.5);
		Zombie fourth = foe(helper, 4.5, 9.5);
		ItemStack bow = stack("wraithstring", 0);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		bow.getItem().releaseUsing(bow, player.level(), player, 0);
		helper.runAfterDelay(20, () -> {
			UniqueTestKit.result("wraithstringFlight", "lost=" + lost(first) + "/" + lost(second) + "/" + lost(third) + "/" + lost(fourth));
			helper.assertTrue(lost(first) > 0.0F && lost(second) > 0.0F && lost(third) > 0.0F, "the arrow missed one of three foes in line");
			helper.assertTrue(lost(fourth) == 0.0F, "the arrow went on to a fourth foe");
			// A vanilla arrow would stop at the first, so the same line proves the pierce, and the damage is the +20 percent.
			helper.assertTrue(lost(third) >= 2.0 * 1.2 * 3.0 - 1.0, "the third foe took only " + lost(third));
			helper.succeed();
		});
	}

	// ---- Hexbolt ----

	private static ItemStack chargedHexbolt(final ServerPlayer player, final int refine) {
		ItemStack crossbow = stack("hexbolt", refine);
		player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
		crossbow.getItem().onUseTick(player.level(), player, crossbow, crossbow.getItem().getUseDuration(crossbow, player) - 40);
		return crossbow;
	}

	@GameTest
	public void hexboltLoosesThreeBoltsForOneArrow(final GameTestHelper helper) {
		ServerPlayer player = archer(helper);
		int before = arrowItems(player);
		ItemStack crossbow = chargedHexbolt(player, 0);
		int loaded = crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).size();
		int spent = before - arrowItems(player);
		helper.assertTrue(CrossbowItem.isCharged(crossbow), "the crossbow did not charge");
		((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
		List<AbstractArrow> bolts = arrows(player);
		long pickable = bolts.stream().filter(bolt -> bolt.pickup == AbstractArrow.Pickup.ALLOWED).count();
		List<Double> headings = new ArrayList<>();
		for (AbstractArrow bolt : bolts) {
			Vec3 motion = bolt.getDeltaMovement();
			headings.add(Math.toDegrees(Math.atan2(motion.x, motion.z)));
		}
		headings.sort(Double::compare);
		UniqueTestKit.result("hexboltBolts", "loaded=" + loaded + " arrowsSpent=" + spent + " bolts=" + bolts.size() + " pickable=" + pickable + " headings=" + headings);
		helper.assertTrue(loaded == 3, "the crossbow held " + loaded + " bolts");
		helper.assertTrue(spent == 1, "the charge cost " + spent + " arrows");
		helper.assertTrue(bolts.size() == 3, "the shot loosed " + bolts.size() + " bolts");
		helper.assertTrue(pickable == 1, "the shot left " + pickable + " pickable arrows");
		helper.assertTrue(Math.abs(Math.abs(headings.get(1) - headings.get(0)) - 10.0) < 3.0 && Math.abs(Math.abs(headings.get(2) - headings.get(1)) - 10.0) < 3.0,
			"the bolts did not leave in a fan of 10 degrees: " + headings);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void hexboltBurstsOnABlockHurtingBodiesAndBreakingNone(final GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(x, y, 9), Blocks.STONE);
			}
		}
		BlockPos torch = new BlockPos(3, 2, 8);
		helper.setBlock(torch, Blocks.TORCH);
		ServerPlayer player = archer(helper);
		Zombie near = foe(helper, 5.6, 8.4);
		Zombie far = foe(helper, 0.5, 6.0);
		ItemStack crossbow = chargedHexbolt(player, 0);
		((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
		helper.runAfterDelay(25, () -> {
			List<Burst> bursts = BURSTS.getOrDefault(player.getUUID(), List.of());
			boolean torchStands = helper.getBlockState(torch).is(Blocks.TORCH);
			boolean wallStands = helper.getBlockState(new BlockPos(4, 2, 9)).is(Blocks.STONE);
			UniqueTestKit.result("hexboltBurst", "bursts=" + bursts.size() + " near=" + lost(near) + " far=" + lost(far) + " torch=" + torchStands + " wall=" + wallStands);
			helper.assertTrue(bursts.size() == 3, "the shot burst " + bursts.size() + " times, not once per bolt");
			helper.assertTrue(lost(near) > 0.0F, "the foe beside the impact took nothing");
			helper.assertTrue(lost(far) == 0.0F, "the foe away from every impact took " + lost(far));
			helper.assertTrue(torchStands && wallStands, "a burst broke a block");
			helper.succeed();
		});
	}

	@GameTest
	public void hexboltBurstRadiusGrowsAtPlusTwelveAndFifteen(final GameTestHelper helper) {
		double[] radius = {1.5, 1.5, 1.5, 2.0, 2.5};
		int[] levels = {0, 7, 10, 12, 15};
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < levels.length; i++) {
			helper.assertTrue(Math.abs(MerchantWeapons.burstRadius(stack("hexbolt", levels[i])) - radius[i]) < 1.0E-9,
				"+" + levels[i] + " radius " + MerchantWeapons.burstRadius(stack("hexbolt", levels[i])));
			ServerPlayer player = archer(helper);
			Arrow bolt = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), stack("hexbolt", levels[i]));
			bolt.addTag(MerchantWeapons.HEXBOLT_TAG);
			bolt.setDeltaMovement(0.0, 0.0, 3.15);
			helper.getLevel().addFreshEntity(bolt);
			Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 5.0));
			Zombie at18 = foe(helper, 4.5, 5.0 + 1.8 + 0.3);
			Zombie at23 = foe(helper, 4.5, 5.0 - 2.3 - 0.3);
			Zombie at30 = foe(helper, 4.5 + 3.0 + 0.3, 5.0);
			MerchantWeapons.impact(bolt, at, null);
			log.append('+').append(levels[i]).append("=").append(lost(at18) > 0).append('/').append(lost(at23) > 0).append('/').append(lost(at30) > 0).append(' ');
			helper.assertTrue((lost(at18) > 0.0F) == (radius[i] >= 1.8), "+" + levels[i] + " the foe 1.8 blocks off: hurt " + (lost(at18) > 0.0F));
			helper.assertTrue((lost(at23) > 0.0F) == (radius[i] >= 2.3), "+" + levels[i] + " the foe 2.3 blocks off: hurt " + (lost(at23) > 0.0F));
			helper.assertTrue(lost(at30) == 0.0F, "+" + levels[i] + " the foe 3 blocks off took damage");
			bolt.discard();
			at18.discard();
			at23.discard();
			at30.discard();
		}
		UniqueTestKit.result("hexboltRadius", log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void hexboltBoltsHitHarderAtEachMilestone(final GameTestHelper helper) {
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < LEVELS.length; i++) {
			ServerPlayer player = archer(helper);
			ItemStack crossbow = chargedHexbolt(player, LEVELS[i]);
			((CrossbowItem) crossbow.getItem()).performShooting(player.level(), player, InteractionHand.MAIN_HAND, crossbow, 3.15F, 1.0F, null);
			List<AbstractArrow> bolts = arrows(player).stream().filter(bolt -> bolt.entityTags().contains(MerchantWeapons.HEXBOLT_TAG)).toList();
			helper.assertTrue(bolts.size() == 3, "+" + LEVELS[i] + " loosed " + bolts.size() + " tagged bolts");
			// A vanilla bolt has base damage 2. A refined crossbow adds 0.75 a level to each bolt.
			double expected = 2.0 * (1.0 + 0.05 * REACHED[i]) + 0.75 * LEVELS[i];
			for (AbstractArrow bolt : bolts) {
				double damage = RefineEffects.baseDamage(bolt);
				helper.assertTrue(Math.abs(damage - expected) < 1.0E-6, "+" + LEVELS[i] + " bolt damage " + damage + ", expected " + expected);
			}
			log.append('+').append(LEVELS[i]).append('=').append(expected).append(' ');
			bolts.forEach(AbstractArrow::discard);
		}
		UniqueTestKit.result("hexboltDamage", log.toString().trim());
		helper.succeed();
	}

	// ---- Wraithpike ----

	@GameTest
	public void wraithpikeReachesOneBlockFurtherAndGrowsAQuarterPerMilestone(final GameTestHelper helper) {
		AttackRange vanilla = new ItemStack(Items.NETHERITE_SPEAR).get(DataComponents.ATTACK_RANGE);
		helper.assertTrue(vanilla != null, "the vanilla spear has no attack range");
		ServerPlayer player = holder(helper, 1.5);
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < LEVELS.length; i++) {
			ItemStack pike = stack("wraithpike", LEVELS[i]);
			AttackRange range = pike.get(DataComponents.ATTACK_RANGE);
			float extra = 1.0F + 0.25F * REACHED[i];
			helper.assertTrue(range != null, "+" + LEVELS[i] + " the pike has no attack range");
			log.append('+').append(LEVELS[i]).append('=').append(range.maxReach()).append(' ');
			helper.assertTrue(Math.abs(range.maxReach() - (vanilla.maxReach() + extra)) < 1.0E-6, "+" + LEVELS[i] + " reach " + range.maxReach() + ", vanilla " + vanilla.maxReach());
			helper.assertTrue(Math.abs(range.maxCreativeReach() - (vanilla.maxCreativeReach() + extra)) < 1.0E-6, "+" + LEVELS[i] + " creative reach " + range.maxCreativeReach());
			helper.assertTrue(range.minReach() == vanilla.minReach() && range.hitboxMargin() == vanilla.hitboxMargin() && range.mobFactor() == vanilla.mobFactor(),
				"+" + LEVELS[i] + " changed more than the reach");
			// The same range, asked of a real hit: a point 5.0 blocks out is out of a vanilla spear's reach and inside the pike's.
			Vec3 point = player.getEyePosition().add(player.getLookAngle().scale(5.0));
			helper.assertTrue(!vanilla.isInRange(player, point), "a point at 5 blocks is inside the vanilla spear's reach");
			helper.assertTrue(range.isInRange(player, point), "+" + LEVELS[i] + " a point at 5 blocks is outside the pike's reach");
		}
		AttackRange top = stack("wraithpike", 15).get(DataComponents.ATTACK_RANGE);
		Vec3 far = player.getEyePosition().add(player.getLookAngle().scale(6.2));
		helper.assertTrue(top.isInRange(player, far) && !stack("wraithpike", 0).get(DataComponents.ATTACK_RANGE).isInRange(player, far),
			"a point at 6.2 blocks is not the +15 pike's alone");
		UniqueTestKit.result("wraithpikeReach", "vanilla=" + vanilla.maxReach() + " " + log.toString().trim());
		helper.succeed();
	}

	@GameTest
	public void wraithpikeChargeHitGivesWitherTwoForSixtyTicks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer charger = UniqueTestKit.connected(helper, new Vec3(1.5, 1.0, 1.5));
		charger.setItemInHand(InteractionHand.MAIN_HAND, stack("wraithpike", 0));
		charger.startUsingItem(InteractionHand.MAIN_HAND);
		ServerPlayer jabber = UniqueTestKit.connected(helper, new Vec3(2.5, 1.0, 1.5));
		jabber.setItemInHand(InteractionHand.MAIN_HAND, stack("wraithpike", 0));
		Zombie charged = foe(helper, 1.5, 4.5);
		Zombie jabbed = foe(helper, 2.5, 4.5);
		helper.assertTrue(charger.isUsingItem() && !jabber.isUsingItem(), "the charge did not start");
		charged.hurtServer(level, level.damageSources().source(DamageTypes.SPEAR, charger), HIT);
		jabbed.hurtServer(level, level.damageSources().source(DamageTypes.SPEAR, jabber), HIT);
		MobEffectInstance wither = charged.getEffect(MobEffects.WITHER);
		UniqueTestKit.result("wraithpikeWither", "charged=" + (wither == null ? "none" : wither.getAmplifier() + "/" + wither.getDuration()) + " jab=" + jabbed.getEffect(MobEffects.WITHER));
		helper.assertTrue(wither != null && wither.getAmplifier() == 1 && wither.getDuration() == 60, "the charge hit gave " + wither);
		helper.assertTrue(jabbed.getEffect(MobEffects.WITHER) == null, "a jab gave Wither");
		helper.succeed();
	}

	@GameTest
	public void wraithpikeMilestonesAddFivePercentDamage(final GameTestHelper helper) {
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < LEVELS.length; i++) {
			ServerLevel level = helper.getLevel();
			ServerPlayer wielder = holder(helper, 1.5);
			wielder.setItemInHand(InteractionHand.MAIN_HAND, stack("wraithpike", LEVELS[i]));
			Zombie victim = foe(helper, 3.5, 5.5);
			victim.hurtServer(level, level.damageSources().source(DamageTypes.SPEAR, wielder), HIT);
			double expected = HIT * (1.0 + 0.05 * REACHED[i]);
			log.append('+').append(LEVELS[i]).append('=').append(lost(victim)).append(' ');
			helper.assertTrue(Math.abs(lost(victim) - expected) < EPSILON, "+" + LEVELS[i] + " hit " + lost(victim) + ", expected " + expected);
			victim.discard();
		}
		UniqueTestKit.result("wraithpikeDamage", log.toString().trim());
		helper.succeed();
	}

	// ---- refine and tooltips ----

	@GameTest
	public void everyWeaponRefinesWithNetheriteToFifteen(final GameTestHelper helper) {
		for (Row row : TABLE) {
			ItemStack piece = stack(row.id(), 0);
			helper.assertTrue(Refine.canRefine(piece), row.id() + " cannot be refined");
			helper.assertTrue(Refine.grade(piece) == Refine.Grade.WEAPON_4, row.id() + " grade " + Refine.grade(piece));
			helper.assertTrue(Refine.isMaterial(piece, new ItemStack(Items.NETHERITE_INGOT)), row.id() + " does not take netherite");
			helper.assertTrue(!Refine.isMaterial(piece, new ItemStack(Items.IRON_INGOT)), row.id() + " takes iron");
			// The first four steps of a netherite weapon are certain, so the real station refines them with no rigged roll.
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().add(new ItemStack(Items.NETHERITE_INGOT, 10));
			AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
			player.containerMenu = menu;
			menu.getSlot(0).set(piece);
			for (int step = 1; step <= 4; step++) {
				RefineStation.Result result = RefineStation.refine(player, menu.containerId);
				helper.assertTrue(result == RefineStation.Result.SUCCESS && Refine.level(menu.getSlot(0).getItem()) == step,
					row.id() + " step " + step + " gave " + result + " +" + Refine.level(menu.getSlot(0).getItem()));
			}
			helper.assertTrue(player.getInventory().countItem(Items.NETHERITE_INGOT) == 10 - 5, row.id() + " the four steps cost " + (10 - player.getInventory().countItem(Items.NETHERITE_INGOT)));
			ItemStack top = stack(row.id(), Refine.MAX_LEVEL);
			helper.assertTrue(Refine.level(top) == 15 && Refine.canRefine(top), row.id() + " does not reach +15");
			int expectedCap = piece.getMaxDamage() - 50 * 4 + 50 * 15;
			helper.assertTrue(top.getMaxDamage() == expectedCap, row.id() + " durability cap at +15 is " + top.getMaxDamage());
		}
		helper.succeed();
	}

	@GameTest
	public void everyTooltipStatesTheEffectAndTheMilestones(final GameTestHelper helper) {
		Map<String, List<String>> expected = Map.of(
			"soulreaver", List.of("15% chance", "+50% of the hit", "+5% damage", "+2% lifesteal"),
			"gravecleaver", List.of("within 3 blocks", "50% of the hit", "+10% cleave"),
			"wraithstring", List.of("25% faster", "+20% damage", "pass through 2", "+1 pierce at +15"),
			"hexbolt", List.of("3 bolts", "bursts on impact", "breaking no block", "+0.5 burst radius at +12 and +15"),
			"wraithpike", List.of("1 block further", "Wither II", "+5% damage", "+0.25 reach"),
			"archmage_staff", List.of("+25% damage", "20% shorter", "+10% spell damage", "-5% cooldown"));
		for (Row row : TABLE) {
			ItemStack stack = stack(row.id(), 0);
			List<Component> lines = new ArrayList<>();
			stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
			StringBuilder text = new StringBuilder();
			lines.forEach(line -> text.append(line.getString()).append(' '));
			for (String phrase : expected.get(row.id())) {
				helper.assertTrue(text.toString().contains(phrase), row.id() + " tooltip lacks '" + phrase + "': " + text);
			}
			helper.assertTrue(text.toString().contains("At +7, +10, +12 and +15"), row.id() + " tooltip names no milestones");
			ItemStack top = stack(row.id(), 15);
			List<Component> topLines = new ArrayList<>();
			top.getItem().appendHoverText(top, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, topLines::add, TooltipFlag.NORMAL);
			helper.assertTrue(topLines.stream().anyMatch(line -> line.getString().startsWith("Now: ")), row.id() + " tooltip shows no current bonus at +15");
		}
		helper.succeed();
	}

	/**
	 * The Arcane Sword and Arcane Bow are netherite-tier, fire-proof, carry 100 MATK plus 5 per refine level for the RPG mod, and
	 * are in its one-damage tag. The bow looses an arrow from an empty inventory, uses nothing up, and leaves an arrow that no
	 * one can pick up.
	 */
	@GameTest
	public void theArcaneSwordAndBowNeedNoAmmoAndCarryFlatMatk(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer archer = holder(helper, 2.5);
		archer.getInventory().clearContent();
		ItemStack bow = stack("arcane_bow", 0);
		ItemStack sword = stack("arcane_shortsword", 0);
		TagKey<Item> oneDamage = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("mod-stella-rpg", "one_damage_weapons"));
		for (ItemStack weapon : List.of(bow, sword)) {
			var tag = weapon.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty("stella_staff");
			helper.assertTrue(tag.getIntOr("matk", 0) == 100 && tag.getIntOr("matk_step", 0) == 5, weapon + " profile " + tag);
			helper.assertTrue(weapon.is(oneDamage), weapon + " is not in the one-damage tag");
			helper.assertTrue(weapon.has(DataComponents.DAMAGE_RESISTANT), weapon + " burns");
			helper.assertTrue(weapon.getMaxDamage() == net.minecraft.world.item.ToolMaterial.NETHERITE.durability(), weapon + " durability " + weapon.getMaxDamage());
		}
		int before = level.getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(16.0)).size();
		boolean released = bow.getItem().releaseUsing(bow, level, archer, bow.getItem().getUseDuration(bow, archer) - 20);
		List<AbstractArrow> arrows = level.getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(16.0));
		boolean inventoryEmpty = archer.getInventory().isEmpty();
		UniqueTestKit.result("arcaneBow", "released=" + released + " arrows=" + (arrows.size() - before) + " pickup=" + (arrows.isEmpty() ? null : arrows.get(0).pickup));
		helper.assertTrue(released && arrows.size() - before == 1, "the bow loosed " + (arrows.size() - before) + " arrows with an empty inventory");
		helper.assertTrue(arrows.get(0).pickup == AbstractArrow.Pickup.DISALLOWED, "the arrow can be picked up");
		helper.assertTrue(inventoryEmpty, "the shot left something in the inventory");
		helper.succeed();
	}
}
