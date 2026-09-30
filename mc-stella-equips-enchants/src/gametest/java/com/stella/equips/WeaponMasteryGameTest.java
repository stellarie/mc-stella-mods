package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.mastery.WeaponMastery;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Surprise, equips: a melee weapon counts the hostiles it kills, and each mastery tier adds attack damage
 * the wielder actually carries. Peaceful kills and tools never count.
 */
public class WeaponMasteryGameTest {
	/** A short pause between reading the wielder before and after the tier. */
	private static final long EQUIPMENT_SETTLE_TICKS = 2L;
	private static final double EPSILON = 1.0e-6;
	private static final Map<UUID, List<String>> EVENTS = new ConcurrentHashMap<>();

	static {
		WeaponMastery.setRecorder(new WeaponMastery.Recorder() {
			@Override
			public void counted(final ServerPlayer player, final ItemStack weapon, final int kills) {
				EVENTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add("kill:" + kills);
			}

			@Override
			public void tierReached(final ServerPlayer player, final ItemStack weapon, final int tier) {
				EVENTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add("tier:" + tier);
			}
		});
	}

	@GameTest
	public void aHostileKilledInMeleeCountsForTheWeapon(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, new ItemStack(Items.IRON_SWORD));
		kill(helper, player, helper.spawn(EntityTypes.ZOMBIE, 3, 1, 3));
		ItemStack sword = player.getMainHandItem();
		helper.assertTrue(WeaponMastery.kills(sword) == 1 && EVENTS.getOrDefault(player.getUUID(), List.of()).contains("kill:1"), "the sword counts " + WeaponMastery.kills(sword) + " kills");
		result("hostileCounts", "kills=" + WeaponMastery.kills(sword));
		helper.succeed();
	}

	@GameTest
	public void aPeacefulKillNeverCounts(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, new ItemStack(Items.IRON_SWORD));
		kill(helper, player, helper.spawn(EntityTypes.COW, 3, 1, 3));
		helper.assertTrue(WeaponMastery.kills(player.getMainHandItem()) == 0 && !EVENTS.containsKey(player.getUUID()), "a cow counted toward mastery");
		result("peacefulNever", "kills=0");
		helper.succeed();
	}

	@GameTest
	public void aToolIsNotAWeapon(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, new ItemStack(Items.IRON_PICKAXE));
		kill(helper, player, helper.spawn(EntityTypes.ZOMBIE, 3, 1, 3));
		helper.assertTrue(WeaponMastery.kills(player.getMainHandItem()) == 0 && !EVENTS.containsKey(player.getUUID()), "a pickaxe counted toward mastery");
		result("toolNever", "kills=0");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void reachingATierAddsItsAttackDamageToTheWielder(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		WeaponMastery.setKills(sword, WeaponMastery.THRESHOLDS[0] - 1);
		ServerPlayer player = wielder(helper, sword);
		double[] before = {Double.NaN};
		helper.runAfterDelay(EQUIPMENT_SETTLE_TICKS, () -> {
			// A player on an embedded connection is never ticked by the server, so its equipment is read here.
			player.doTick();
			before[0] = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
			kill(helper, player, helper.spawn(EntityTypes.ZOMBIE, 3, 1, 3));
		});
		helper.runAfterDelay(EQUIPMENT_SETTLE_TICKS * 3, () -> {
			player.doTick();
			double after = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
			helper.assertTrue(EVENTS.getOrDefault(player.getUUID(), List.of()).contains("tier:1"), "the weapon never reached tier one: " + EVENTS.get(player.getUUID()));
			helper.assertTrue(Math.abs(after - before[0] - WeaponMastery.DAMAGE_PER_TIER) < EPSILON, "attack damage went from " + before[0] + " to " + after);
			result("tierDamage", "before=" + before[0] + " after=" + after);
			helper.succeed();
		});
	}

	@GameTest
	public void theTooltipLineFollowsTheTier(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		WeaponMastery.setKills(sword, WeaponMastery.THRESHOLDS[0] - 1);
		String below = lastLine(sword);
		WeaponMastery.setKills(sword, WeaponMastery.THRESHOLDS[0]);
		String reached = lastLine(sword);
		WeaponMastery.setKills(sword, WeaponMastery.THRESHOLDS[1]);
		String next = lastLine(sword);
		helper.assertTrue(!below.isBlank() && !below.equals(reached) && !reached.equals(next), "the tooltip line did not follow the tier: " + below + " / " + reached + " / " + next);
		helper.assertTrue(sword.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size() == 1, "setting the count twice stacked the mastery line");
		result("tooltip", "lines=[" + below + "] [" + reached + "] [" + next + "]");
		helper.succeed();
	}

	private static String lastLine(final ItemStack stack) {
		List<Component> lines = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
		return lines.isEmpty() ? "" : lines.getLast().getString();
	}

	private static void kill(final GameTestHelper helper, final ServerPlayer player, final LivingEntity victim) {
		victim.setInvulnerableTime(0);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		helper.assertFalse(victim.isAlive(), "the victim survived, so the case proves nothing");
	}

	// A survival player placed in the level, holding the weapon under test.
	private static ServerPlayer wielder(final GameTestHelper helper, final ItemStack weapon) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mastery-player"), false);
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

	private static void result(final String test, final String detail) {
		StellaEquips.LOGGER.info("[BEHAVIOR] test=weaponMastery.{} RESULT {}", test, detail);
	}
}
