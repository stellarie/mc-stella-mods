package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.enchant.Auras;
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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every flat proc is a share now. The auras take a share of the wearer's max health, divided by the health
 * scale mod-stella-rpg publishes, and Aftershock and Thorned take a share of the hit: a bigger hit or a bigger
 * health pool gives a bigger proc, in the same proportion.
 */
public class PercentProcGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.percent-procs");
	private static final int PADDING = 40;
	private static final float HIT = 10.0F;
	private static final float BIG_HIT = 100.0F;
	private static final double TOLERANCE = 1.0e-2;
	private static final double WEARER_HEALTH = 300.0;
	/** Under the game's 1024 ceiling for max health. */
	private static final double SPIDER_HEALTH = 1000.0;

	static {
		RefineRolls.install();
	}

	@GameTest(padding = PADDING)
	public void procTable(final GameTestHelper helper) {
		ServerPlayer blazeWearer = auraWearer(helper, "armor/blaze_armor", 1, WEARER_HEALTH);
		float blaze = pulseLoss(helper, blazeWearer);
		ServerPlayer mistWearer = auraWearer(helper, "armor/mist_armor", 3, WEARER_HEALTH);
		float mist = pulseLoss(helper, mistWearer);
		float aftershock = aftershock(helper, HIT);
		float[] oneThorn = thorned(helper, 1, BIG_HIT);
		float[] fourThorns = thorned(helper, 4, BIG_HIT);
		LOG.info("[BEHAVIOR] test=procTable RESULT blaze={} mist={} aftershock={} thorned1={} thorned4={}", blaze, mist, aftershock, oneThorn[1], fourThorns[1]);
		helper.assertTrue(Math.abs(blaze - Auras.BLAZE_SHARE * blazeWearer.getMaxHealth()) < TOLERANCE, "a blaze pulse took " + blaze + " from " + blazeWearer.getMaxHealth() + " health");
		helper.assertTrue(Math.abs(mist - Auras.MIST_SHARE_PER_LEVEL * 3 * mistWearer.getMaxHealth()) < TOLERANCE, "a rank 3 mist pulse took " + mist);
		helper.assertTrue(Math.abs(aftershock - HIT * MasteryPerks.AFTERSHOCK_SHARE) < TOLERANCE, "the aftershock dealt " + aftershock + " for a hit of " + HIT);
		helper.assertTrue(oneThorn[0] > 0.0F && Math.abs(oneThorn[1] - RefineEffects.THORN_SHARE * oneThorn[0]) < TOLERANCE,
			"one +12 piece pricked for " + oneThorn[1] + " after a blow of " + oneThorn[0]);
		helper.assertTrue(Math.abs(fourThorns[1] - 4 * RefineEffects.THORN_SHARE * fourThorns[0]) < TOLERANCE,
			"four +12 pieces pricked for " + fourThorns[1] + " after a blow of " + fourThorns[0]);
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void procsGrowWithTheirSource(final GameTestHelper helper) {
		float smallAftershock = aftershock(helper, HIT);
		float bigAftershock = aftershock(helper, BIG_HIT);
		float[] smallThorn = thorned(helper, 2, HIT);
		float[] bigThorn = thorned(helper, 2, BIG_HIT);
		float smallBlaze = pulseLoss(helper, auraWearer(helper, "armor/blaze_armor", 1, 100.0));
		float bigBlaze = pulseLoss(helper, auraWearer(helper, "armor/blaze_armor", 1, 1000.0));
		LOG.info("[BEHAVIOR] test=procsGrowWithTheirSource RESULT aftershock={}/{} thorn={}/{} blaze={}/{}", smallAftershock, bigAftershock, smallThorn[1], bigThorn[1], smallBlaze, bigBlaze);
		helper.assertTrue(smallAftershock > 0.0F && Math.abs(bigAftershock - 10 * smallAftershock) < TOLERANCE * 10, "a tenfold hit gave an aftershock of " + bigAftershock + " from " + smallAftershock);
		helper.assertTrue(smallThorn[1] > 0.0F && Math.abs(bigThorn[1] - 10 * smallThorn[1]) < TOLERANCE * 10, "a tenfold blow gave a prick of " + bigThorn[1] + " from " + smallThorn[1]);
		helper.assertTrue(smallBlaze > 0.0F && Math.abs(bigBlaze - 10 * smallBlaze) < TOLERANCE * 10, "tenfold health gave a blaze pulse of " + bigBlaze + " from " + smallBlaze);
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void healthScaleDividesTheAuras(final GameTestHelper helper) {
		double scale = 15.0;
		Object previous = FabricLoader.getInstance().getObjectShare().put(HealthScale.OBJECT_SHARE_KEY, scale);
		try {
			ServerPlayer blazeWearer = auraWearer(helper, "armor/blaze_armor", 1, WEARER_HEALTH);
			float blaze = pulseLoss(helper, blazeWearer);
			ServerPlayer mistWearer = auraWearer(helper, "armor/mist_armor", 2, WEARER_HEALTH);
			float mist = pulseLoss(helper, mistWearer);
			LOG.info("[BEHAVIOR] test=healthScaleDividesTheAuras RESULT scale={} blaze={} mist={} maxHealth={}", scale, blaze, mist, blazeWearer.getMaxHealth());
			helper.assertTrue(Math.abs(blaze - Auras.BLAZE_SHARE * blazeWearer.getMaxHealth() / scale) < TOLERANCE, "a blaze pulse at K=15 took " + blaze);
			helper.assertTrue(Math.abs(mist - Auras.MIST_SHARE_PER_LEVEL * 2 * mistWearer.getMaxHealth() / scale) < TOLERANCE, "a rank 2 mist pulse at K=15 took " + mist);
		} finally {
			restore(previous);
		}
		helper.succeed();
	}

	@GameTest
	public void healthScaleIsOneUnlessItIsAPositiveDouble(final GameTestHelper helper) {
		Object previous = FabricLoader.getInstance().getObjectShare().get(HealthScale.OBJECT_SHARE_KEY);
		try {
			FabricLoader.getInstance().getObjectShare().remove(HealthScale.OBJECT_SHARE_KEY);
			double absent = HealthScale.factor();
			double[] observed = new double[4];
			Object[] published = {"fifteen", -3.0, 0.0, Double.NaN};
			for (int i = 0; i < published.length; i++) {
				FabricLoader.getInstance().getObjectShare().put(HealthScale.OBJECT_SHARE_KEY, published[i]);
				observed[i] = HealthScale.factor();
			}
			LOG.info("[BEHAVIOR] test=healthScaleIsOneUnlessItIsAPositiveDouble RESULT absent={} junk={}", absent, List.of(observed[0], observed[1], observed[2], observed[3]));
			helper.assertTrue(absent == 1.0, "an absent share gave " + absent);
			for (double factor : observed) {
				helper.assertTrue(factor == 1.0, "a share that is no positive number gave " + factor);
			}
		} finally {
			restore(previous);
		}
		helper.succeed();
	}

	private static void restore(final Object previous) {
		if (previous == null) {
			FabricLoader.getInstance().getObjectShare().remove(HealthScale.OBJECT_SHARE_KEY);
		} else {
			FabricLoader.getInstance().getObjectShare().put(HealthScale.OBJECT_SHARE_KEY, previous);
		}
	}

	/** One pulse of the wearer's aura at the foe beside it: the health the foe lost. */
	private static float pulseLoss(final GameTestHelper helper, final ServerPlayer wearer) {
		Mob foe = sturdy(helper, 3, 2, 3);
		float before = foe.getHealth();
		Auras.pulse(wearer);
		return before - foe.getHealth();
	}

	private static ServerPlayer auraWearer(final GameTestHelper helper, final String path, final int rank, final double maxHealth) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		player.setHealth((float) maxHealth);
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.IRON_CHESTPLATE, path, rank));
		return player;
	}

	/** A Mastery IV mace's hit on one hostile: the health a second hostile beside it lost. */
	private static float aftershock(final GameTestHelper helper, final float hit) {
		ItemStack mace = new ItemStack(Items.MACE);
		WeaponMastery.setKills(mace, WeaponMastery.THRESHOLDS[MasteryPerks.PERK_TIER - 1]);
		ServerPlayer player = wielder(helper, mace);
		Mob target = sturdy(helper, 3, 2, 3);
		Mob near = sturdy(helper, 5, 2, 3);
		float before = near.getHealth();
		target.damageCooldownTime = 0;
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), hit);
		return before - near.getHealth();
	}

	/** A blow on a wearer of +12 armour: the blow, then the health its attacker lost. */
	private static float[] thorned(final GameTestHelper helper, final int pieces, final float blow) {
		Mob wearer = sturdy(helper, 3, 2, 3);
		List<Item> armour = List.of(Items.IRON_CHESTPLATE, Items.IRON_HELMET, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		List<EquipmentSlot> slots = List.of(EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		for (int i = 0; i < pieces; i++) {
			ItemStack piece = new ItemStack(armour.get(i));
			Refine.setLevel(piece, Refine.RENDING_LEVEL);
			wearer.setItemSlot(slots.get(i), piece);
		}
		Mob attacker = sturdy(helper, 5, 2, 3);
		float attackerBefore = attacker.getHealth();
		wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), blow);
		return new float[] {blow, attackerBefore - attacker.getHealth()};
	}

	private static Mob sturdy(final GameTestHelper helper, final int x, final int y, final int z) {
		Mob spider = helper.spawnMob(EntityTypes.SPIDER, x, y, z).withNoFreeWill().spawn();
		spider.setPersistenceRequired();
		spider.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SPIDER_HEALTH);
		spider.setHealth((float) SPIDER_HEALTH);
		return spider;
	}

	private static ServerPlayer wielder(final GameTestHelper helper, final ItemStack weapon) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "percent-player"), false);
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

	private static ItemStack enchanted(final GameTestHelper helper, final Item item, final String path, final int level) {
		Holder<Enchantment> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, path)));
		ItemStack stack = new ItemStack(item);
		stack.enchant(holder, level);
		return stack;
	}
}
