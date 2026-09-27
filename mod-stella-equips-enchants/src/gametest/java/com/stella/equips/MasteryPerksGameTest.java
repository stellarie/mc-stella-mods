package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.mastery.MasteryPerks;
import com.stella.equips.mastery.WeaponMastery;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Mastery IV unlocks one perk per weapon type. Each perk fires on the wielder's melee hit only at the
 * perk tier: a tier III weapon one kill short of it fires nothing.
 */
public class MasteryPerksGameTest {
	private static final float HIT = 10.0F;
	private static final float WOUNDED = 10.0F;
	private static final double EPSILON = 1.0e-4;
	private static final Map<UUID, List<MasteryPerks.Perk>> FIRED = new ConcurrentHashMap<>();

	static {
		MasteryPerks.setRecorder((player, perk, victim) -> FIRED.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add(perk));
	}

	@GameTest
	public void everyWeaponTypeCarriesItsOwnPerk(final GameTestHelper helper) {
		List<MasteryPerks.Perk> perks = List.of(
			MasteryPerks.perkFor(new ItemStack(Items.IRON_SWORD)), MasteryPerks.perkFor(new ItemStack(Items.IRON_AXE)),
			MasteryPerks.perkFor(new ItemStack(Items.IRON_SPEAR)), MasteryPerks.perkFor(new ItemStack(Items.TRIDENT)),
			MasteryPerks.perkFor(new ItemStack(Items.MACE))
		);
		helper.assertTrue(perks.stream().allMatch(perk -> perk != null) && perks.stream().distinct().count() == perks.size(), "the weapon types do not each carry their own perk: " + perks);
		helper.assertTrue(MasteryPerks.perkFor(new ItemStack(Items.IRON_PICKAXE)) == null, "a pickaxe carries a perk");
		result("perTypes", "perks=" + perks);
		helper.succeed();
	}

	@GameTest
	public void aMasterSwordStealsLife(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, master(Items.IRON_SWORD));
		player.setHealth(WOUNDED);
		hit(helper, player, zombie(helper, 3, 3));
		helper.assertTrue(Math.abs(player.getHealth() - WOUNDED - MasteryPerks.LIFESTEAL_HEAL) < EPSILON, "health went from " + WOUNDED + " to " + player.getHealth());
		result("lifesteal", "health=" + player.getHealth());
		helper.succeed();
	}

	@GameTest
	public void aTierThreeSwordStealsNothing(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		WeaponMastery.setKills(sword, WeaponMastery.THRESHOLDS[MasteryPerks.PERK_TIER - 1] - 1);
		ServerPlayer player = wielder(helper, sword);
		player.setHealth(WOUNDED);
		hit(helper, player, zombie(helper, 3, 3));
		helper.assertTrue(player.getHealth() == WOUNDED && !FIRED.containsKey(player.getUUID()), "a tier three sword healed to " + player.getHealth());
		result("noPerkBelowTier", "health=" + player.getHealth());
		helper.succeed();
	}

	@GameTest
	public void aMasterAxeCleavesTheHostilesBesideItsTarget(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, master(Items.IRON_AXE));
		Mob target = zombie(helper, 3, 3);
		Mob beside = zombie(helper, 4, 3);
		Cow cow = helper.spawnMob(EntityTypes.COW, 3, 1, 4).withNoFreeWill().spawn();
		float besideBefore = beside.getHealth();
		float cowBefore = cow.getHealth();
		hit(helper, player, target);
		float cleaved = besideBefore - beside.getHealth();
		helper.assertTrue(Math.abs(cleaved - HIT * MasteryPerks.CLEAVE_SHARE) < EPSILON, "the zombie beside took " + cleaved);
		helper.assertTrue(cow.getHealth() == cowBefore, "the cleave hit a cow");
		result("cleave", "cleaved=" + cleaved);
		helper.succeed();
	}

	@GameTest
	public void aMasterSpearGivesMomentum(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, master(Items.IRON_SPEAR));
		hit(helper, player, zombie(helper, 3, 3));
		helper.assertTrue(player.hasEffect(MobEffects.SPEED), "the spear gave no momentum");
		result("momentum", "speed=true");
		helper.succeed();
	}

	@GameTest
	public void aMasterTridentCallsTheTide(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, master(Items.TRIDENT));
		hit(helper, player, zombie(helper, 3, 3));
		helper.assertTrue(player.hasEffect(MobEffects.CONDUIT_POWER), "the trident called no tide");
		result("tidecaller", "conduit=true");
		helper.succeed();
	}

	@GameTest
	public void aMasterMaceSendsAnAftershock(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, master(Items.MACE));
		Mob target = zombie(helper, 3, 3);
		Mob near = zombie(helper, 5, 3);
		float before = near.getHealth();
		hit(helper, player, target);
		float shaken = before - near.getHealth();
		helper.assertTrue(Math.abs(shaken - MasteryPerks.AFTERSHOCK_DAMAGE) < EPSILON, "the aftershock dealt " + shaken);
		result("aftershock", "shaken=" + shaken);
		helper.succeed();
	}

	@GameTest
	public void theTierFourTooltipNamesTheWeaponsPerk(final GameTestHelper helper) {
		String sword = lastLine(master(Items.IRON_SWORD));
		String axe = lastLine(master(Items.IRON_AXE));
		helper.assertTrue(sword.contains(MasteryPerks.Perk.LIFESTEAL.title()) && axe.contains(MasteryPerks.Perk.CLEAVE.title()), "tier four lines: " + sword + " / " + axe);
		result("tooltipPerk", "sword=[" + sword + "] axe=[" + axe + "]");
		helper.succeed();
	}

	private static String lastLine(final ItemStack stack) {
		List<net.minecraft.network.chat.Component> lines = stack.getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines();
		return lines.isEmpty() ? "" : lines.getLast().getString();
	}

	private static ItemStack master(final net.minecraft.world.item.Item item) {
		ItemStack stack = new ItemStack(item);
		WeaponMastery.setKills(stack, WeaponMastery.THRESHOLDS[MasteryPerks.PERK_TIER - 1]);
		return stack;
	}

	private static void hit(final GameTestHelper helper, final ServerPlayer player, final Mob victim) {
		victim.damageCooldownTime = 0;
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
	}

	// A durable zombie without its natural armour, so a hit lands at exactly the damage the perk deals.
	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(200.0F);
		return zombie;
	}

	private static ServerPlayer wielder(final GameTestHelper helper, final ItemStack weapon) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "perk-player"), false);
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
		StellaEquips.LOGGER.info("[BEHAVIOR] test=masteryPerks.{} RESULT {}", test, detail);
	}
}
