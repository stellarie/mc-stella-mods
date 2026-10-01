package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineSpecials;
import com.stella.equips.refine.RefineSpecials.Special;
import com.stella.equips.refine.RefineStation;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shields in the Refine tab. A shield refines with iron ingots at the armor odds, adds armor while held in either
 * hand, and has no armor specials. From +10 it parries: a blocked melee hit sends a share of the blocked damage
 * back to its attacker. Every blocked hit costs the shield one durability point.
 *
 * <p>The blocking player is a joined survival player, because the mock player from the helper is creative and
 * takes no durability loss. A joined test player never ticks on its own, so the test calls {@code doTick} to run
 * out the shield's block delay. Mob attacks on a player need a difficulty above peaceful, so those cases run
 * inside one tick with the difficulty raised and put back.
 */
public class ShieldRefineGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.shield");
	private static final float HIT = 8.0F;
	/** Under the old threshold of 3. */
	private static final float SMALL_HIT = 2.0F;
	/** Costs the old rule more than 1. */
	private static final float LARGE_HIT = 30.0F;
	private static final double TOLERANCE = 1.0e-2;
	private static final int FRONT_Z = 4;
	private static final int BEHIND_Z = 0;

	// ---- refining ----

	@GameTest
	public void refines(final GameTestHelper helper) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
		helper.assertTrue(Refine.canRefine(shield), "a shield cannot be refined");
		helper.assertTrue(Refine.grade(shield) == Refine.Grade.ARMOR && Refine.grade(shield) == Refine.grade(chestplate),
			"a shield's odds are " + Refine.grade(shield) + ", not the armor grade");
		helper.assertTrue(Refine.material(shield) == Items.IRON_INGOT && Refine.isMaterial(shield, new ItemStack(Items.IRON_INGOT)),
			"a shield's material is " + Refine.material(shield) + ", not iron ingots");
		helper.assertTrue(!Refine.isMaterial(shield, new ItemStack(Items.OAK_PLANKS)) && !Refine.isMaterial(shield, new ItemStack(Items.DIAMOND)),
			"planks or diamonds count as shield material");

		ServerPlayer player = refiner(helper, 10, 5);
		int base = shield.getMaxDamage();
		shield.setDamageValue(100);
		AnvilMenu menu = openAnvil(player, shield);
		RefineStation.Result first = RefineStation.refine(player, menu.containerId);
		int afterFirst = player.getInventory().countItem(Items.IRON_INGOT);
		ItemStack refined = menu.getSlot(0).getItem();
		List<String> lore = refined.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(line -> line.getString()).toList();
		boolean glint = Boolean.TRUE.equals(refined.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		int levelAfterFirst = Refine.level(refined);
		int repaired = refined.getDamageValue();
		int cap = refined.getMaxDamage();

		// The +3 step costs 2 ingots and cannot fail.
		Refine.setLevel(refined, 3);
		RefineStation.Result second = RefineStation.refine(player, menu.containerId);
		int afterSecond = player.getInventory().countItem(Items.IRON_INGOT);

		ServerPlayer poor = refiner(helper, 0, 5);
		ItemStack other = new ItemStack(Items.SHIELD);
		AnvilMenu poorMenu = openAnvil(poor, other);
		RefineStation.Result refused = RefineStation.refine(poor, poorMenu.containerId);
		LOG.info("[BEHAVIOR] test=shield.refines RESULT first={} level={} ingots={} lore={} glint={} damage={} cap={} second={} level2={} ingots2={} refused={} planks={}",
			first, levelAfterFirst, afterFirst, lore, glint, repaired, cap, second, Refine.level(refined), afterSecond, refused, poor.getInventory().countItem(Items.OAK_PLANKS));
		helper.assertTrue(first == RefineStation.Result.SUCCESS && levelAfterFirst == 1, "the first step gave " + first + " +" + levelAfterFirst);
		helper.assertTrue(afterFirst == 10 - Refine.cost(0), "the first step took " + (10 - afterFirst) + " ingots");
		helper.assertTrue(!lore.isEmpty() && lore.get(0).equals("Refine +1") && glint, "the refined shield does not show its level: " + lore + " glint=" + glint);
		helper.assertTrue(repaired == 0 && cap == base + Refine.DURABILITY_PER_LEVEL, "a success left damage " + repaired + " of " + cap + " from base " + base);
		helper.assertTrue(second == RefineStation.Result.SUCCESS && Refine.level(refined) == 4 && afterSecond == afterFirst - Refine.cost(3) && Refine.cost(3) == 2,
			"the +3 step gave " + second + " +" + Refine.level(refined) + " and took " + (afterFirst - afterSecond) + " ingots");
		helper.assertTrue(refused == RefineStation.Result.SHORT_OF_MATERIAL && Refine.level(other) == 0 && poor.getInventory().countItem(Items.OAK_PLANKS) == 5,
			"planks paid for a refine: " + refused);
		helper.succeed();
	}

	@GameTest
	public void existingGearKeepsItsClass(final GameTestHelper helper) {
		ItemStack sword = blocking(new ItemStack(Items.IRON_SWORD));
		ItemStack helmet = blocking(new ItemStack(Items.DIAMOND_HELMET));
		ItemStack bow = blocking(new ItemStack(Items.BOW));
		Refine.setLevel(sword, Refine.ASCENDED_LEVEL);
		Refine.setLevel(helmet, Refine.ASCENDED_LEVEL);
		Refine.setLevel(bow, Refine.ASCENDED_LEVEL);
		LOG.info("[BEHAVIOR] test=shield.existingGearKeepsItsClass RESULT sword={} helmet={} bow={}",
			RefineSpecials.of(sword, Refine.ASCENDED_LEVEL), RefineSpecials.of(helmet, Refine.ASCENDED_LEVEL), RefineSpecials.of(bow, Refine.ASCENDED_LEVEL));
		helper.assertTrue(RefineSpecials.of(sword, Refine.ASCENDED_LEVEL).equals(List.of(Special.KEEN, Special.SEARING, Special.RENDING, Special.ASCENDED)),
			"a blocking iron sword at +15 has " + RefineSpecials.of(sword, Refine.ASCENDED_LEVEL));
		helper.assertTrue(RefineSpecials.of(helmet, Refine.ASCENDED_LEVEL).equals(List.of(Special.CLARITY, Special.BULWARK, Special.THORNED, Special.BLESSED)),
			"a blocking diamond helmet at +15 has " + RefineSpecials.of(helmet, Refine.ASCENDED_LEVEL));
		helper.assertTrue(RefineSpecials.of(bow, Refine.ASCENDED_LEVEL).equals(List.of(Special.KEEN, Special.PIERCING, Special.RENDING, Special.VOLLEY)),
			"a blocking bow at +15 has " + RefineSpecials.of(bow, Refine.ASCENDED_LEVEL));
		helper.assertTrue(Refine.material(helmet) == Items.DIAMOND && Refine.grade(sword) == Refine.Grade.WEAPON_2 && Refine.grade(helmet) == Refine.Grade.ARMOR,
			"a blocking sword or helmet changed its material or grade");
		helper.succeed();
	}

	/** The component decides, not the item: any damageable item that blocks attacks refines as a shield. */
	@GameTest
	public void aModdedShieldRefinesToo(final GameTestHelper helper) {
		ItemStack standIn = blocking(new ItemStack(Items.PAPER));
		standIn.set(DataComponents.MAX_STACK_SIZE, 1);
		standIn.set(DataComponents.MAX_DAMAGE, 500);
		standIn.set(DataComponents.DAMAGE, 0);
		boolean traits = Refine.canRefine(standIn) && Refine.grade(standIn) == Refine.Grade.ARMOR && Refine.material(standIn) == Items.IRON_INGOT;
		Refine.setLevel(standIn, Refine.MILESTONE_FIRST);
		ItemAttributeModifiers modifiers = standIn.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double armor = modifiers.compute(Attributes.ARMOR, 0.0, EquipmentSlot.OFFHAND);
		List<String> titles = RefineSpecials.of(standIn, Refine.MILESTONE_FIRST).stream().map(Special::title).toList();
		LOG.info("[BEHAVIOR] test=shield.aModdedShieldRefinesToo RESULT traits={} armor={} specials={} cap={}", traits, armor, titles, standIn.getMaxDamage());
		helper.assertTrue(traits, "a blocking stand-in is not refined as armor-grade gear with iron");
		helper.assertTrue(Math.abs(armor - Refine.MILESTONE_FIRST * Refine.ARMOR_PER_LEVEL) < 1e-6 && titles.equals(List.of("Parry")),
			"a +10 stand-in has armor " + armor + " and specials " + titles);
		helper.assertTrue(standIn.getMaxDamage() == 500 + Refine.MILESTONE_FIRST * Refine.DURABILITY_PER_LEVEL, "a +10 stand-in caps at " + standIn.getMaxDamage());
		helper.succeed();
	}

	/** A new level replaces the shield's armor, so a failure that lowers the level lowers the armor and the cap. */
	@GameTest
	public void levelChangesReplaceTheArmor(final GameTestHelper helper) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		int base = shield.getMaxDamage();
		Refine.setLevel(shield, 5);
		Refine.setLevel(shield, 3);
		ItemAttributeModifiers lowered = shield.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		long entries = lowered.modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR)).count();
		double armor = lowered.compute(Attributes.ARMOR, 0.0, EquipmentSlot.MAINHAND);
		int cap = shield.getMaxDamage();
		Refine.setLevel(shield, 0);
		boolean cleared = shield.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty();
		LOG.info("[BEHAVIOR] test=shield.levelChangesReplaceTheArmor RESULT entries={} armor={} cap={} cleared={} capAtZero={} glintAtZero={}",
			entries, armor, cap, cleared, shield.getMaxDamage(), shield.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		helper.assertTrue(entries == 1 && Math.abs(armor - 3 * Refine.ARMOR_PER_LEVEL) < 1e-6, "+5 then +3 left " + entries + " armor modifiers adding " + armor);
		helper.assertTrue(cap == base + 3 * Refine.DURABILITY_PER_LEVEL, "+3 caps at " + cap + " from base " + base);
		helper.assertTrue(cleared && shield.getMaxDamage() == base && shield.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null, "+0 left a bonus, a cap or a glint");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void armorWhileHeld(final GameTestHelper helper) {
		int level = 6;
		ItemStack shield = refined(level);
		ItemAttributeModifiers modifiers = shield.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double expected = level * Refine.ARMOR_PER_LEVEL;
		double inMain = modifiers.compute(Attributes.ARMOR, 0.0, EquipmentSlot.MAINHAND);
		double inOff = modifiers.compute(Attributes.ARMOR, 0.0, EquipmentSlot.OFFHAND);
		double onHead = modifiers.compute(Attributes.ARMOR, 0.0, EquipmentSlot.HEAD);
		boolean handGroup = modifiers.modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ARMOR) && entry.slot() == EquipmentSlotGroup.HAND);
		int cap = shield.getMaxDamage();
		int base = new ItemStack(Items.SHIELD).getMaxDamage();

		Mob control = pig(helper, 1);
		Mob mainHand = pig(helper, 2);
		Mob offHand = pig(helper, 3);
		Mob onTheHead = pig(helper, 4);
		control.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		mainHand.setItemSlot(EquipmentSlot.MAINHAND, refined(level));
		offHand.setItemSlot(EquipmentSlot.OFFHAND, refined(level));
		onTheHead.setItemSlot(EquipmentSlot.HEAD, refined(level));
		helper.runAfterDelay(5, () -> {
			double plain = control.getAttributeValue(Attributes.ARMOR);
			double gainMain = mainHand.getAttributeValue(Attributes.ARMOR) - plain;
			double gainOff = offHand.getAttributeValue(Attributes.ARMOR) - plain;
			double gainHead = onTheHead.getAttributeValue(Attributes.ARMOR) - plain;
			LOG.info("[BEHAVIOR] test=shield.armorWhileHeld RESULT level={} item(main={} off={} head={}) mob(main={} off={} head={}) cap={} base={}",
				level, inMain, inOff, onHead, gainMain, gainOff, gainHead, cap, base);
			helper.assertTrue(handGroup && Math.abs(inMain - expected) < 1e-6 && Math.abs(inOff - expected) < 1e-6 && onHead == 0.0,
				"the item carries armor " + inMain + " main, " + inOff + " off, " + onHead + " head, hand group " + handGroup);
			helper.assertTrue(Math.abs(gainMain - expected) < 1e-6, "a +" + level + " shield in the main hand added " + gainMain + " armor, not " + expected);
			helper.assertTrue(Math.abs(gainOff - expected) < 1e-6, "a +" + level + " shield in the off hand added " + gainOff + " armor, not " + expected);
			helper.assertTrue(Math.abs(gainHead) < 1e-6, "a shield on the head added " + gainHead + " armor");
			helper.assertTrue(cap == base + level * Refine.DURABILITY_PER_LEVEL, "a +" + level + " shield caps at " + cap + " from base " + base);
			helper.succeed();
		});
	}

	@GameTest
	public void shieldsTakeNoArmorSpecials(final GameTestHelper helper) {
		StringBuilder seen = new StringBuilder();
		boolean only = true;
		for (int level = 0; level <= Refine.MAX_LEVEL; level++) {
			List<String> titles = RefineSpecials.of(refined(level), level).stream().map(Special::title).toList();
			seen.append(level).append("=").append(titles).append(' ');
			only &= titles.equals(level >= Refine.MILESTONE_FIRST ? List.of("Parry") : List.of());
		}
		LOG.info("[BEHAVIOR] test=shield.shieldsTakeNoArmorSpecials RESULT {}", seen);
		helper.assertTrue(only, "a shield's specials are " + seen);
		helper.succeed();
	}

	@GameTest
	public void parryShowsInTheTooltip(final GameTestHelper helper) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		Refine.setLevel(shield, 9);
		List<String> nine = lore(shield);
		Refine.setLevel(shield, 10);
		List<String> ten = lore(shield);
		Refine.setLevel(shield, 12);
		List<String> twelve = lore(shield);
		Refine.setLevel(shield, 15);
		List<String> fifteen = lore(shield);
		LOG.info("[BEHAVIOR] test=shield.parryShowsInTheTooltip RESULT nine={} ten={} twelve={} fifteen={}", nine, ten, twelve, fifteen);
		helper.assertTrue(nine.equals(List.of("Refine +9")), "a +9 shield shows " + nine);
		helper.assertTrue(parryLines(ten) == 1 && parryLine(ten).contains("25%"), "a +10 shield shows " + ten);
		helper.assertTrue(parryLines(twelve) == 1 && parryLine(twelve).contains("50%"), "a +12 shield shows " + twelve);
		helper.assertTrue(parryLines(fifteen) == 1 && parryLine(fifteen).contains("75%"), "a +15 shield shows " + fifteen);
		helper.succeed();
	}

	// ---- parry ----

	/** The shares are oniichan's table. Each one applies to the damage the shield blocked. */
	@GameTest
	public void parry(final GameTestHelper helper) {
		int[] levels = {9, 10, 11, 12, 14, 15};
		double[] shares = {0.0, 0.25, 0.25, 0.50, 0.50, 0.75};
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob attacker = attacker(helper, 2, FRONT_Z);
			StringBuilder seen = new StringBuilder();
			double[] losses = new double[levels.length];
			float[] defenderLoss = new float[levels.length];
			onNormal(helper, () -> {
				for (int i = 0; i < levels.length; i++) {
					raise(defender, refined(levels[i]));
					helper.assertTrue(defender.isBlocking(), "the +" + levels[i] + " shield is not blocking, so the case proves nothing");
					float health = defender.getHealth();
					losses[i] = loss(defender, attacker, helper.getLevel().damageSources().mobAttack(attacker), HIT);
					defenderLoss[i] = health - defender.getHealth();
					seen.append("+").append(levels[i]).append(":").append(losses[i]).append(' ');
				}
			});
			LOG.info("[BEHAVIOR] test=shield.parry RESULT hit={} losses={}", HIT, seen);
			for (int i = 0; i < levels.length; i++) {
				helper.assertTrue(defenderLoss[i] == 0.0F, "the +" + levels[i] + " shield did not block the hit, the wearer lost " + defenderLoss[i]);
				helper.assertTrue(Math.abs(losses[i] - HIT * shares[i]) < TOLERANCE,
					"a +" + levels[i] + " shield sent back " + losses[i] + " of a blocked " + HIT + ", not " + HIT * shares[i]);
			}
			helper.succeed();
		} finally {
			leave(helper, defender);
		}
	}

	@GameTest
	public void parryIgnoresArrows(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob shooter = attacker(helper, 2, FRONT_Z);
			ItemStack shield = refined(Refine.MAX_LEVEL);
			raise(defender, shield);
			helper.assertTrue(defender.isBlocking(), "the shield is not blocking, so the case proves nothing");
			Arrow arrow = new Arrow(helper.getLevel(), shooter, new ItemStack(Items.ARROW), null);
			int worn = defender.getOffhandItem().getDamageValue();
			float health = defender.getHealth();
			float loss = loss(defender, shooter, helper.getLevel().damageSources().arrow(arrow, shooter), HIT);
			int cost = defender.getOffhandItem().getDamageValue() - worn;
			LOG.info("[BEHAVIOR] test=shield.parryIgnoresArrows RESULT shooterLoss={} wearerLoss={} shieldCost={}", loss, health - defender.getHealth(), cost);
			helper.assertTrue(cost == 1 && defender.getHealth() == health, "the arrow was not blocked: shield cost " + cost + ", wearer lost " + (health - defender.getHealth()));
			helper.assertTrue(Math.abs(loss) < TOLERANCE, "a blocked arrow sent " + loss + " back to its shooter");
			helper.succeed();
		} finally {
			leave(helper, defender);
		}
	}

	@GameTest
	public void parryNeedsARaisedShield(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob front = attacker(helper, 2, FRONT_Z);
			Mob behind = attacker(helper, 2, BEHIND_Z);
			defender.setItemInHand(InteractionHand.OFF_HAND, refined(Refine.MAX_LEVEL));
			float[] lowered = new float[1];
			float[] fromBehind = new float[1];
			onNormal(helper, () -> {
				lowered[0] = loss(defender, front, helper.getLevel().damageSources().mobAttack(front), HIT);
				raise(defender, refined(Refine.MAX_LEVEL));
				helper.assertTrue(defender.isBlocking(), "the shield is not blocking, so the case proves nothing");
				fromBehind[0] = loss(defender, behind, helper.getLevel().damageSources().mobAttack(behind), HIT);
			});
			LOG.info("[BEHAVIOR] test=shield.parryNeedsARaisedShield RESULT lowered={} fromBehind={}", lowered[0], fromBehind[0]);
			helper.assertTrue(Math.abs(lowered[0]) < TOLERANCE, "a lowered shield sent " + lowered[0] + " back");
			helper.assertTrue(Math.abs(fromBehind[0]) < TOLERANCE, "a hit from behind a raised shield sent " + fromBehind[0] + " back");
			helper.succeed();
		} finally {
			leave(helper, defender);
		}
	}

	/** A shield that stops only part of a hit sends back a share of that part, not of the whole hit. */
	@GameTest
	public void parryFollowsTheBlockedShare(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob attacker = attacker(helper, 2, FRONT_Z);
			ItemStack shield = refined(Refine.MAX_LEVEL);
			BlocksAttacks vanilla = shield.get(DataComponents.BLOCKS_ATTACKS);
			float stopped = 0.6F;
			shield.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(vanilla.blockDelaySeconds(), vanilla.disableCooldownScale(),
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, stopped)), vanilla.itemDamage(), vanilla.bypassedBy(),
				vanilla.blockSound(), vanilla.disableSound()));
			raise(defender, shield);
			helper.assertTrue(defender.isBlocking(), "the shield is not blocking, so the case proves nothing");
			float[] loss = new float[1];
			float health = defender.getHealth();
			onNormal(helper, () -> loss[0] = loss(defender, attacker, helper.getLevel().damageSources().mobAttack(attacker), HIT));
			float expected = HIT * stopped * 0.75F;
			LOG.info("[BEHAVIOR] test=shield.parryFollowsTheBlockedShare RESULT stopped={} loss={} expected={} wearerLoss={}", stopped, loss[0], expected, health - defender.getHealth());
			helper.assertTrue(defender.getHealth() < health, "the shield stopped the whole hit, so the case proves nothing");
			helper.assertTrue(Math.abs(loss[0] - expected) < TOLERANCE, "a shield that stopped " + stopped + " of the hit sent back " + loss[0] + ", not " + expected);
			helper.succeed();
		} finally {
			leave(helper, defender);
		}
	}

	@GameTest
	public void parryReachesAPlayer(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		ServerPlayer striker = survivor(helper, 2.5, 4.5, 180.0F);
		try {
			raise(defender, refined(Refine.MAX_LEVEL));
			helper.assertTrue(defender.isBlocking(), "the shield is not blocking, so the case proves nothing");
			float health = striker.getHealth();
			defender.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(striker), HIT);
			float loss = health - striker.getHealth();
			LOG.info("[BEHAVIOR] test=shield.parryReachesAPlayer RESULT loss={} wearerHealth={}", loss, defender.getHealth());
			helper.assertTrue(Math.abs(loss - HIT * 0.75F) < TOLERANCE, "a +15 parry sent " + loss + " to a player attacker, not " + HIT * 0.75F);
			helper.succeed();
		} finally {
			leave(helper, defender);
			leave(helper, striker);
		}
	}

	/** Two raised +15 shields face each other. The parry is thorns damage, and thorns damage is never parried. */
	@GameTest
	public void parryNeverLoops(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		ServerPlayer striker = survivor(helper, 2.5, 4.5, 180.0F);
		try {
			raise(defender, refined(Refine.MAX_LEVEL));
			raise(striker, refined(Refine.MAX_LEVEL));
			helper.assertTrue(defender.isBlocking() && striker.isBlocking(), "a shield is not blocking, so the case proves nothing");
			defender.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(striker), HIT);
			int defenderShield = defender.getOffhandItem().getDamageValue();
			int strikerShield = striker.getOffhandItem().getDamageValue();
			LOG.info("[BEHAVIOR] test=shield.parryNeverLoops RESULT defenderShield={} strikerShield={} defenderHealth={} strikerHealth={}",
				defenderShield, strikerShield, defender.getHealth(), striker.getHealth());
			helper.assertTrue(strikerShield == 1, "the thorns hit cost the striker's shield " + strikerShield + ", so the case proves nothing");
			helper.assertTrue(defenderShield == 1, "the parry was parried back: the defender's shield took " + defenderShield + " blocks");
			helper.assertTrue(defender.getHealth() == defender.getMaxHealth() && striker.getHealth() == striker.getMaxHealth(), "a blocked exchange hurt a player");
			helper.succeed();
		} finally {
			leave(helper, defender);
			leave(helper, striker);
		}
	}

	/** The shield that breaks on a blow has still blocked it, so the blow is parried. */
	@GameTest
	public void parryOnTheBreakingBlow(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob attacker = attacker(helper, 2, FRONT_Z);
			ItemStack shield = refined(Refine.MAX_LEVEL);
			shield.setDamageValue(shield.getMaxDamage() - 1);
			raise(defender, shield);
			helper.assertTrue(defender.isBlocking(), "the shield is not blocking, so the case proves nothing");
			float[] loss = new float[1];
			onNormal(helper, () -> loss[0] = loss(defender, attacker, helper.getLevel().damageSources().mobAttack(attacker), HIT));
			boolean broke = defender.getOffhandItem().isEmpty();
			LOG.info("[BEHAVIOR] test=shield.parryOnTheBreakingBlow RESULT broke={} loss={}", broke, loss[0]);
			helper.assertTrue(broke, "the shield did not break, so the case proves nothing");
			helper.assertTrue(Math.abs(loss[0] - HIT * 0.75F) < TOLERANCE, "the breaking blow sent " + loss[0] + " back, not " + HIT * 0.75F);
			helper.succeed();
		} finally {
			leave(helper, defender);
		}
	}

	// ---- durability ----

	@GameTest
	public void durability(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob attacker = attacker(helper, 2, FRONT_Z);
			int[] plain = new int[2];
			int[] refined = new int[2];
			onNormal(helper, () -> {
				raise(defender, new ItemStack(Items.SHIELD));
				helper.assertTrue(defender.isBlocking(), "the shield is not blocking, so the case proves nothing");
				plain[0] = cost(helper, defender, attacker, SMALL_HIT);
				plain[1] = cost(helper, defender, attacker, LARGE_HIT);
				raise(defender, refined(Refine.SEARING_LEVEL));
				helper.assertTrue(defender.isBlocking(), "the refined shield is not blocking, so the case proves nothing");
				refined[0] = cost(helper, defender, attacker, SMALL_HIT);
				refined[1] = cost(helper, defender, attacker, LARGE_HIT);
			});
			LOG.info("[BEHAVIOR] test=shield.durability RESULT plain(small={} large={}) refined(small={} large={})", plain[0], plain[1], refined[0], refined[1]);
			helper.assertTrue(plain[0] == 1, "a small hit cost a plain shield " + plain[0]);
			helper.assertTrue(plain[1] == 1, "a large hit cost a plain shield " + plain[1]);
			helper.assertTrue(refined[0] == 1, "a small hit cost a refined shield " + refined[0]);
			helper.assertTrue(refined[1] == 1, "a large hit cost a refined shield " + refined[1]);
			helper.succeed();
		} finally {
			leave(helper, defender);
		}
	}

	/** The game charges every hit on a raised shield. A hit that the shield does not block must still cost nothing. */
	@GameTest
	public void durabilityIgnoresUnblockedHits(final GameTestHelper helper) {
		ServerPlayer defender = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob front = attacker(helper, 2, FRONT_Z);
			Mob behind = attacker(helper, 2, BEHIND_Z);
			int[] cost = new int[2];
			float[] wearerLoss = new float[1];
			onNormal(helper, () -> {
				raise(defender, new ItemStack(Items.SHIELD));
				helper.assertTrue(defender.isBlocking(), "the shield is not blocking, so the case proves nothing");
				float health = defender.getHealth();
				cost[0] = cost(helper, defender, behind, HIT);
				wearerLoss[0] = health - defender.getHealth();
				defender.stopUsingItem();
				cost[1] = cost(helper, defender, front, HIT);
			});
			LOG.info("[BEHAVIOR] test=shield.durabilityIgnoresUnblockedHits RESULT fromBehind={} wearerLoss={} lowered={}", cost[0], wearerLoss[0], cost[1]);
			helper.assertTrue(wearerLoss[0] > 0.0F, "the hit from behind was blocked, so the case proves nothing");
			helper.assertTrue(cost[0] == 0, "a hit from behind a raised shield cost it " + cost[0]);
			helper.assertTrue(cost[1] == 0, "a hit on a lowered shield cost it " + cost[1]);
			helper.succeed();
		} finally {
			leave(helper, defender);
		}
	}

	// ---- fixtures ----

	private static ItemStack refined(final int level) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		Refine.setLevel(shield, level);
		return shield;
	}

	/** Adds a shield's blocking component. */
	private static ItemStack blocking(final ItemStack stack) {
		stack.set(DataComponents.BLOCKS_ATTACKS, new ItemStack(Items.SHIELD).get(DataComponents.BLOCKS_ATTACKS));
		return stack;
	}

	private static List<String> lore(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(line -> line.getString()).toList();
	}

	private static long parryLines(final List<String> lore) {
		return lore.stream().filter(line -> line.startsWith("Parry:")).count();
	}

	private static String parryLine(final List<String> lore) {
		return lore.stream().filter(line -> line.startsWith("Parry:")).findFirst().orElse("");
	}

	private static ServerPlayer refiner(final GameTestHelper helper, final int ingots, final int planks) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (ingots > 0) {
			player.getInventory().add(new ItemStack(Items.IRON_INGOT, ingots));
		}
		if (planks > 0) {
			player.getInventory().add(new ItemStack(Items.OAK_PLANKS, planks));
		}
		return player;
	}

	private static AnvilMenu openAnvil(final ServerPlayer player, final ItemStack piece) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		player.containerMenu = menu;
		menu.getSlot(0).set(piece);
		return menu;
	}

	private static Mob pig(final GameTestHelper helper, final int x) {
		Mob pig = helper.spawnMob(EntityTypes.PIG, x, 2, 3).withNoFreeWill().spawn();
		pig.setPersistenceRequired();
		return pig;
	}

	private static Mob attacker(final GameTestHelper helper, final int x, final int z) {
		Mob spider = helper.spawnMob(EntityTypes.SPIDER, x, 2, z).withNoFreeWill().spawn();
		spider.setPersistenceRequired();
		spider.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		spider.setHealth(1000.0F);
		return spider;
	}

	/** A joined survival player that can be hurt. */
	private static ServerPlayer survivor(final GameTestHelper helper, final double x, final double z, final float yaw) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "shield-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		// Without this the player stays immune.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		player.teleportTo(at.x, at.y, at.z);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		return player;
	}

	private static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** Holds the shield up past its block delay. */
	private static void raise(final ServerPlayer player, final ItemStack shield) {
		player.stopUsingItem();
		player.setItemInHand(InteractionHand.OFF_HAND, shield);
		player.startUsingItem(InteractionHand.OFF_HAND);
		int delay = player.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS).blockDelayTicks();
		for (int tick = 0; tick <= delay; tick++) {
			player.doTick();
		}
	}

	/** Runs the body on normal difficulty. */
	private static void onNormal(final GameTestHelper helper, final Runnable body) {
		Difficulty before = helper.getLevel().getDifficulty();
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			body.run();
		} finally {
			helper.getLevel().getServer().setDifficulty(before, true);
		}
	}

	/** The attacker's health loss to one hit. */
	private static float loss(final ServerPlayer defender, final Mob attacker, final DamageSource source, final float hit) {
		attacker.damageCooldownTime = 0;
		defender.damageCooldownTime = 0;
		float before = attacker.getHealth();
		defender.hurtServer((ServerLevel) defender.level(), source, hit);
		return before - attacker.getHealth();
	}

	/** The off-hand shield's durability loss. */
	private static int cost(final GameTestHelper helper, final ServerPlayer defender, final Mob attacker, final float hit) {
		defender.damageCooldownTime = 0;
		int before = defender.getOffhandItem().getDamageValue();
		defender.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), hit);
		return defender.getOffhandItem().getDamageValue() - before;
	}
}
