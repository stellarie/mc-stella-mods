package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.guardian.BoneShield;
import com.stella.equips.guardian.SkeletonCrown;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineStation;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gear of the Tower Guardians. The shield test blocks real hits from a mob and then lands a real blow.
 * The crown test lands real arrow hits through the arrow's own hit code, at each refine tier.
 */
public class GuardianGearGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.guardian");
	private static final float HIT = 8.0F;
	private static final float BLOW = 10.0F;
	private static final double TOLERANCE = 1.0e-2;
	private static final int BLOCKS_PAST_THE_CAP = BoneShield.MAX_STACKS + 3;

	@GameTest
	public void boneShieldBlocksStackToTwelveAndTheNextBlowSpendsThem(final GameTestHelper helper) {
		ServerPlayer holder = survivor(helper, 2.5, 2.5, 0.0F);
		try {
			Mob attacker = foe(helper, 3.5, 5.5);
			Mob victim = foe(helper, 2.0, 4.0);
			Mob behind = foe(helper, 2.5, 0.5);
			float[] blow = new float[2];
			int[] stacks = new int[3];
			Vec3[] pushed = new Vec3[2];
			float[] wearerLoss = new float[1];
			onNormal(helper, () -> {
				raise(holder, new ItemStack(BoneShield.item()));
				helper.assertTrue(holder.isBlocking(), "the Bone Shield is not blocking, so the case proves nothing");
				float health = holder.getHealth();
				for (int i = 0; i < BLOCKS_PAST_THE_CAP; i++) {
					holder.damageCooldownTime = 0;
					holder.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
					if (i == 0) {
						stacks[0] = BoneShield.stacks(holder);
					}
				}
				wearerLoss[0] = health - holder.getHealth();
				stacks[1] = BoneShield.stacks(holder);
				blow[0] = playerBlow(helper, holder, victim);
				pushed[0] = attacker.getDeltaMovement();
				pushed[1] = behind.getDeltaMovement();
				stacks[2] = BoneShield.stacks(holder);
				blow[1] = playerBlow(helper, holder, victim);
			});
			LOG.info("[BEHAVIOR] test=guardian.boneShield RESULT firstBlock={} afterBlocks={} wearerLoss={} blow={} afterBlow={} next={} frontPush={} behindPush={}",
				stacks[0], stacks[1], wearerLoss[0], blow[0], stacks[2], blow[1], pushed[0], pushed[1]);
			helper.assertTrue(wearerLoss[0] == 0.0F, "the shield did not block every hit, the wearer lost " + wearerLoss[0]);
			helper.assertTrue(stacks[0] == 1 && stacks[1] == BoneShield.MAX_STACKS,
				"a block gave " + stacks[0] + " stacks and " + BLOCKS_PAST_THE_CAP + " blocks gave " + stacks[1] + ", not " + BoneShield.MAX_STACKS);
			float expected = BLOW * (float) (1.0 + BoneShield.BONUS_PER_STACK * BoneShield.MAX_STACKS);
			helper.assertTrue(Math.abs(blow[0] - expected) < TOLERANCE, "the blow with " + BoneShield.MAX_STACKS + " stacks dealt " + blow[0] + ", not " + expected);
			helper.assertTrue(stacks[2] == 0 && Math.abs(blow[1] - BLOW) < TOLERANCE,
				"the blow after it dealt " + blow[1] + " with " + stacks[2] + " stacks left, not a plain " + BLOW);
			helper.assertTrue(pushed[0].z > 0.5 && Math.abs(pushed[1].z) < 0.05 && Math.abs(pushed[1].x) < 0.05,
				"the mob in front moved " + pushed[0] + " and the mob behind moved " + pushed[1]);
			helper.succeed();
		} finally {
			leave(helper, holder);
		}
	}

	@GameTest
	public void boneShieldHoldsTwoThousandAndRefinesWithNetheriteOnly(final GameTestHelper helper) {
		ItemStack shield = new ItemStack(BoneShield.item());
		helper.assertTrue(shield.getMaxDamage() == 2000, "the Bone Shield holds " + shield.getMaxDamage() + ", not 2000");
		helper.assertTrue(BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-equips-enchants:bone_shield")) == BoneShield.item(),
			"the id mod-stella-equips-enchants:bone_shield is not the Bone Shield");
		helper.assertTrue(Refine.material(new ItemStack(Items.SHIELD)) == Items.IRON_INGOT, "a plain shield no longer refines with iron");

		ServerPlayer iron = refiner(helper, new ItemStack(Items.IRON_INGOT, 10));
		ItemStack withIron = new ItemStack(BoneShield.item());
		RefineStation.Result refused = RefineStation.refine(iron, openAnvil(iron, withIron).containerId);

		ServerPlayer smith = refiner(helper, new ItemStack(Items.NETHERITE_INGOT, 10));
		ItemStack refined = new ItemStack(BoneShield.item());
		RefineStation.Result first = RefineStation.refine(smith, openAnvil(smith, refined).containerId);
		int left = smith.getInventory().countItem(Items.NETHERITE_INGOT);
		LOG.info("[BEHAVIOR] test=guardian.boneShieldRefine RESULT durability={} iron={} netherite={} level={} ingotsLeft={}",
			shield.getMaxDamage(), refused, first, Refine.level(refined), left);
		helper.assertTrue(refused == RefineStation.Result.SHORT_OF_MATERIAL && Refine.level(withIron) == 0, "iron ingots refined the Bone Shield: " + refused);
		helper.assertTrue(first == RefineStation.Result.SUCCESS && Refine.level(refined) == 1 && left == 10 - Refine.cost(0),
			"netherite gave " + first + " +" + Refine.level(refined) + " and left " + left + " ingots");
		helper.succeed();
	}

	@GameTest
	public void skeletonCrownAddsRangedDamageAtEachRefineTier(final GameTestHelper helper) {
		ServerPlayer archer = UniqueTestKit.survivor(helper, new Vec3(2.5, 2.0, 1.5));
		Mob target = foe(helper, 2.5, 4.5);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		target.setHealth(1000.0F);
		int[] levels = {0, 7, 10, 12, 15};
		double[] shares = {0.25, 0.35, 0.45, 0.55, 0.65};
		float plain = arrowLoss(helper, archer, target);
		StringBuilder seen = new StringBuilder();
		for (int i = 0; i < levels.length; i++) {
			ItemStack crown = new ItemStack(SkeletonCrown.item());
			Refine.setLevel(crown, levels[i]);
			archer.setItemSlot(EquipmentSlot.HEAD, crown);
			float loss = arrowLoss(helper, archer, target);
			seen.append("+").append(levels[i]).append(":").append(loss).append(' ');
			double expected = plain * (1.0 + shares[i]);
			helper.assertTrue(Math.abs(loss - expected) < TOLERANCE, "an arrow with the +" + levels[i] + " crown dealt " + loss + ", not " + expected + " from a plain " + plain);
		}
		LOG.info("[BEHAVIOR] test=guardian.skeletonCrown RESULT plain={} withCrown={}", plain, seen);
		helper.assertTrue(plain > 1.0F, "the plain arrow dealt " + plain + ", so the ratios prove nothing");
		helper.succeed();
	}

	@GameTest
	public void skeletonCrownIsANetheriteHelmetWithItsOwnWornModelAndRefinesWithNetherite(final GameTestHelper helper) {
		ItemStack crown = new ItemStack(SkeletonCrown.item());
		ItemStack plain = new ItemStack(Items.NETHERITE_HELMET);
		Equippable equippable = crown.get(DataComponents.EQUIPPABLE);
		helper.assertTrue(BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-equips-enchants:skeleton_crown")) == SkeletonCrown.item(),
			"the id mod-stella-equips-enchants:skeleton_crown is not the Skeleton Crown");
		helper.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.HEAD, "the Skeleton Crown is not worn on the head");
		helper.assertTrue(crown.getMaxDamage() == plain.getMaxDamage() && armorOf(crown) == armorOf(plain) && armorOf(crown) > 0.0,
			"the crown has durability " + crown.getMaxDamage() + " and armor " + armorOf(crown) + ", a netherite helmet has " + plain.getMaxDamage() + " and " + armorOf(plain));
		boolean model = equippable != null && equippable.assetId().isPresent()
			&& equippable.assetId().get().identifier().equals(Identifier.parse("mod-stella-equips-enchants:skeleton_crown"))
			&& SkeletonCrown.class.getResource("/assets/mod-stella-equips-enchants/equipment/skeleton_crown.json") != null
			&& SkeletonCrown.class.getResource("/assets/mod-stella-equips-enchants/textures/entity/equipment/humanoid/skeleton_crown.png") != null;
		helper.assertTrue(model, "the crown has no worn model asset and texture of its own");

		ServerPlayer smith = refiner(helper, new ItemStack(Items.NETHERITE_INGOT, 10));
		RefineStation.Result first = RefineStation.refine(smith, openAnvil(smith, crown).containerId);
		helper.assertTrue(first == RefineStation.Result.SUCCESS && Refine.level(crown) == 1, "netherite gave " + first + " +" + Refine.level(crown));
		helper.succeed();
	}

	// ---- fixtures ----

	private static double armorOf(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
	}

	private static Mob foe(final GameTestHelper helper, final double x, final double z) {
		Mob spider = helper.spawnWithNoFreeWill(EntityTypes.SPIDER, new Vec3(x, 2.0, z));
		spider.setPersistenceRequired();
		spider.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		spider.setHealth(1000.0F);
		return spider;
	}

	/** The victim's health loss to one blow of the holder. */
	private static float playerBlow(final GameTestHelper helper, final ServerPlayer holder, final Mob victim) {
		victim.damageCooldownTime = 0;
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(holder), BLOW);
		return before - victim.getHealth();
	}

	/** The target's health loss to one arrow of the archer, through the arrow's own hit code. */
	private static float arrowLoss(final GameTestHelper helper, final ServerPlayer archer, final Mob target) {
		Arrow arrow = new Arrow(helper.getLevel(), archer, new ItemStack(Items.ARROW), null);
		arrow.setDeltaMovement(0.0, 0.0, 3.0);
		arrow.setBaseDamage(2.0);
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		try {
			Method hit = AbstractArrow.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
			hit.setAccessible(true);
			hit.invoke(arrow, new EntityHitResult(target));
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
		return before - target.getHealth();
	}

	private static ServerPlayer refiner(final GameTestHelper helper, final ItemStack material) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.getInventory().add(material);
		return player;
	}

	private static AnvilMenu openAnvil(final ServerPlayer player, final ItemStack piece) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		player.containerMenu = menu;
		menu.getSlot(0).set(piece);
		return menu;
	}

	/** A joined survival player that can be hurt. */
	private static ServerPlayer survivor(final GameTestHelper helper, final double x, final double z, final float yaw) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "guardian-player"), false);
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
}
