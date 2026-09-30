package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.enchant.HealthScale;
import com.stella.equips.mastery.MasteryPerks;
import com.stella.equips.mastery.WeaponMastery;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineEffects;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heals and health bonuses follow the health they serve. Lifesteal returns a share of the damage it dealt, Blessed
 * a share of max health in vanilla units every five seconds, and Vigor a share of base max health as an
 * ADD_MULTIPLIED_BASE modifier.
 */
public class PercentHealGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.percent-heals");
	private static final float HIT = 10.0F;
	private static final float BIG_HIT = 100.0F;
	private static final double TOLERANCE = 1.0e-3;
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	private static final List<Item> IRON = List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);

	static {
		RefineRolls.install();
	}

	@GameTest
	public void healTable(final GameTestHelper helper) {
		float smallSteal = lifesteal(helper, HIT);
		float bigSteal = lifesteal(helper, BIG_HIT);
		ServerPlayer blessed = armoured(helper, Refine.ASCENDED_LEVEL);
		blessed.setHealth(5.0F);
		RefineEffects.blessOnce(blessed);
		float blessing = blessed.getHealth() - 5.0F;
		ItemAttributeModifiers.Entry vigor = vigorEntry(refined(Items.IRON_HELMET, Refine.SEARING_LEVEL));
		LOG.info("[BEHAVIOR] test=healTable RESULT lifesteal={}/{} blessed={} maxHealth={} vigorOperation={} vigorAmount={}",
			smallSteal, bigSteal, blessing, blessed.getMaxHealth(), vigor == null ? null : vigor.modifier().operation(), vigor == null ? null : vigor.modifier().amount());
		helper.assertTrue(Math.abs(smallSteal - HIT * MasteryPerks.LIFESTEAL_SHARE) < TOLERANCE, "a hit of " + HIT + " healed " + smallSteal);
		helper.assertTrue(Math.abs(bigSteal - BIG_HIT * MasteryPerks.LIFESTEAL_SHARE) < TOLERANCE, "a hit of " + BIG_HIT + " healed " + bigSteal);
		helper.assertTrue(Math.abs(blessing - 4 * RefineEffects.BLESSING_SHARE * blessed.getMaxHealth()) < TOLERANCE,
			"four +15 pieces healed " + blessing + " of " + blessed.getMaxHealth() + " max health");
		helper.assertTrue(vigor != null && vigor.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
			&& Math.abs(vigor.modifier().amount() - Refine.VIGOR_MULTIPLIER) < TOLERANCE, "the +10 vigor modifier was " + vigor);
		helper.succeed();
	}

	@GameTest
	public void blessedMendsNothingBelowFifteenAndDividesByTheHealthScale(final GameTestHelper helper) {
		ServerPlayer nearly = armoured(helper, Refine.ASCENDED_LEVEL - 1);
		nearly.setHealth(5.0F);
		RefineEffects.blessOnce(nearly);
		double scale = 15.0;
		Object previous = FabricLoader.getInstance().getObjectShare().put(HealthScale.OBJECT_SHARE_KEY, scale);
		float scaled;
		ServerPlayer blessed = armoured(helper, Refine.ASCENDED_LEVEL);
		try {
			blessed.setHealth(5.0F);
			RefineEffects.blessOnce(blessed);
			scaled = blessed.getHealth() - 5.0F;
		} finally {
			if (previous == null) {
				FabricLoader.getInstance().getObjectShare().remove(HealthScale.OBJECT_SHARE_KEY);
			} else {
				FabricLoader.getInstance().getObjectShare().put(HealthScale.OBJECT_SHARE_KEY, previous);
			}
		}
		LOG.info("[BEHAVIOR] test=blessedMendsNothingBelowFifteenAndDividesByTheHealthScale RESULT nearly={} scaled={}", nearly.getHealth(), scaled);
		helper.assertTrue(nearly.getHealth() == 5.0F, "four +14 pieces healed to " + nearly.getHealth());
		helper.assertTrue(Math.abs(scaled - 4 * RefineEffects.BLESSING_SHARE * blessed.getMaxHealth() / scale) < TOLERANCE, "four +15 pieces at K=15 healed " + scaled);
		helper.succeed();
	}

	@GameTest
	public void vigorRaisesMaxHealthByAShareOfItsBaseAtTenNotBelow(final GameTestHelper helper) {
		ItemStack nine = refined(Items.IRON_HELMET, Refine.SEARING_LEVEL - 1);
		ItemStack ten = refined(Items.IRON_HELMET, Refine.SEARING_LEVEL);
		// A share of the base scales with it: 20 becomes 20.4, and 200 becomes 204.
		double small = maxHealthWith(ten, 20.0);
		double large = maxHealthWith(ten, 200.0);
		LOG.info("[BEHAVIOR] test=vigorRaisesMaxHealthByAShareOfItsBaseAtTenNotBelow RESULT nineEntry={} small={} large={}", vigorEntry(nine), small, large);
		helper.assertTrue(vigorEntry(nine) == null && maxHealthWith(nine, 20.0) == 20.0, "a +9 piece gave vigor");
		helper.assertTrue(Math.abs(small - 20.0 * (1 + Refine.VIGOR_MULTIPLIER)) < TOLERANCE, "20 max health became " + small);
		helper.assertTrue(Math.abs(large - 200.0 * (1 + Refine.VIGOR_MULTIPLIER)) < TOLERANCE, "200 max health became " + large);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void wornVigorRaisesTheWearersMaxHealth(final GameTestHelper helper) {
		Mob wearer = helper.spawnMob(EntityTypes.PIG, 3, 2, 3).withNoFreeWill().spawn();
		wearer.setPersistenceRequired();
		double before = wearer.getMaxHealth();
		for (int i = 0; i < ARMOR_SLOTS.size(); i++) {
			wearer.setItemSlot(ARMOR_SLOTS.get(i), refined(IRON.get(i), Refine.SEARING_LEVEL));
		}
		helper.runAfterDelay(5, () -> {
			// Four vigor pieces and the chestplate's fortitude, all shares of the same base.
			double expected = before * (1 + 4 * Refine.VIGOR_MULTIPLIER + Refine.FORTITUDE_MULTIPLIER);
			LOG.info("[BEHAVIOR] test=wornVigorRaisesTheWearersMaxHealth RESULT before={} after={} expected={} armor={}", before, wearer.getMaxHealth(), expected, wearer.getAttributeValue(Attributes.ARMOR));
			helper.assertTrue(Math.abs(wearer.getMaxHealth() - expected) < TOLERANCE, "four +10 iron pieces made " + before + " max health " + wearer.getMaxHealth());
			helper.succeed();
		});
	}

	/** A Mastery IV sword's hit for an amount on a hostile with no armour: the health its wielder gained. */
	private static float lifesteal(final GameTestHelper helper, final float hit) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		WeaponMastery.setKills(sword, WeaponMastery.THRESHOLDS[MasteryPerks.PERK_TIER - 1]);
		ServerPlayer player = wielder(helper, sword);
		player.setHealth(5.0F);
		Mob victim = helper.spawnMob(EntityTypes.SPIDER, 3, 2, 3).withNoFreeWill().spawn();
		victim.setPersistenceRequired();
		victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		victim.setHealth(1000.0F);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), hit);
		return player.getHealth() - 5.0F;
	}

	private static ServerPlayer armoured(final GameTestHelper helper, final int level) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (int i = 0; i < ARMOR_SLOTS.size(); i++) {
			player.setItemSlot(ARMOR_SLOTS.get(i), refined(IRON.get(i), level));
		}
		return player;
	}

	private static ItemStack refined(final Item item, final int level) {
		ItemStack stack = new ItemStack(item);
		Refine.setLevel(stack, level);
		return stack;
	}

	private static ItemAttributeModifiers.Entry vigorEntry(final ItemStack piece) {
		return piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.filter(entry -> entry.modifier().id().equals(Refine.vigorId(EquipmentSlot.HEAD))).findFirst().orElse(null);
	}

	private static double maxHealthWith(final ItemStack piece, final double base) {
		return piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.MAX_HEALTH, base, EquipmentSlot.HEAD);
	}

	private static ServerPlayer wielder(final GameTestHelper helper, final ItemStack weapon) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "heal-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.teleportTo(at.x, at.y, at.z);
		player.setNoGravity(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return player;
	}
}
