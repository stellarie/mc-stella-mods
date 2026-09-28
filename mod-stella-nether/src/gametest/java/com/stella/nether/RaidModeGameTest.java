package com.stella.nether;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The first player into an empty raid is its host and sets its mode; everyone inside holds one boon, and in
 * hard mode each raid mob holds two. The raid is one shared instance, so each test runs alone in its batch.
 */
public class RaidModeGameTest {
	/** Crosses into the raid for real, so the player stands in the raid level. */
	static ServerPlayer enter(final GameTestHelper helper, final RaidTravel.Choice choice, final RaidMode mode) {
		ServerPlayer player = NetherTestSupport.join(helper, 1.5, 1.5);
		RaidTravel.choose(player, choice, mode);
		TeleportTransition transition = NetherTestSupport.portal(helper.getLevel(), player, helper.absolutePos(new BlockPos(1, 1, 1)));
		helper.assertTrue(transition != null, "the raid choice led nowhere");
		player.teleport(transition);
		return player;
	}

	static List<Holder<MobEffect>> infinite(final net.minecraft.world.entity.LivingEntity entity) {
		return entity.getActiveEffects().stream().filter(MobEffectInstance::isInfiniteDuration).map(MobEffectInstance::getEffect).toList();
	}

	@GameTest(environment = "mod-stella-nether-test:host", maxTicks = 100)
	public void theFirstPlayerInIsTheHostAndSetsTheMode(final GameTestHelper helper) {
		RaidInstance.reset(helper.getLevel().getServer(), "test");
		ServerPlayer host = enter(helper, RaidTravel.Choice.RAID, RaidMode.HARD);
		RaidState state = RaidInstance.state(helper.getLevel().getServer());
		NetherTestSupport.result("hostSetsMode", "host=" + host.getUUID().equals(state.host().orElse(null)) + " mode=" + state.mode() + " active=" + state.active());
		NetherTestSupport.leave(helper, host);
		helper.assertTrue(state.active() && host.getUUID().equals(state.host().orElse(null)) && state.mode() == RaidMode.HARD,
			"the raid after its first entrant: " + state);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:join", maxTicks = 100)
	public void aLaterPlayerJoinsWithoutChangingHostOrMode(final GameTestHelper helper) {
		RaidInstance.reset(helper.getLevel().getServer(), "test");
		ServerPlayer host = enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		ServerPlayer guest = enter(helper, RaidTravel.Choice.RAID, RaidMode.HARD);
		RaidState state = RaidInstance.state(helper.getLevel().getServer());
		NetherTestSupport.result("guestJoins", "hostKept=" + host.getUUID().equals(state.host().orElse(null)) + " mode=" + state.mode()
			+ " guestInRaid=" + guest.level().dimension().equals(RaidTravel.RAID));
		NetherTestSupport.leave(helper, guest);
		NetherTestSupport.leave(helper, host);
		helper.assertTrue(host.getUUID().equals(state.host().orElse(null)) && state.mode() == RaidMode.NORMAL,
			"a guest changed the raid to " + state);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:boon", maxTicks = 100)
	public void aPlayerInsideHoldsExactlyOneBoon(final GameTestHelper helper) {
		RaidInstance.reset(helper.getLevel().getServer(), "test");
		ServerPlayer player = enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		List<Holder<MobEffect>> boons = infinite(player);
		NetherTestSupport.result("oneBoon", "boons=" + boons);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(boons.size() == RaidInstance.PLAYER_BOONS_EACH && RaidInstance.PLAYER_BOONS.containsAll(boons),
			"a player inside holds " + boons);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:leave", maxTicks = 100)
	public void leavingTheRaidTakesTheBoonAway(final GameTestHelper helper) {
		RaidInstance.reset(helper.getLevel().getServer(), "test");
		ServerPlayer player = enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		int inside = infinite(player).size();
		TeleportTransition back = NetherTestSupport.portal((ServerLevel) player.level(), player, RaidInstance.exitPortal(helper.getLevel().getServer()));
		helper.assertTrue(back != null, "the raid portal led nowhere");
		player.teleport(back);
		List<Holder<MobEffect>> outside = infinite(player);
		NetherTestSupport.result("boonLeaves", "inside=" + inside + " outside=" + outside);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(inside == RaidInstance.PLAYER_BOONS_EACH && outside.isEmpty(), "boons inside=" + inside + " outside=" + outside);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:spread", maxTicks = 100)
	public void boonsAreDrawnAtRandomFromThePool(final GameTestHelper helper) {
		Set<Holder<MobEffect>> seen = new HashSet<>();
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7L);
		for (int i = 0; i < 64; i++) {
			seen.addAll(RaidInstance.draw(RaidInstance.PLAYER_BOONS, RaidInstance.PLAYER_BOONS_EACH, random));
		}
		NetherTestSupport.result("boonSpread", "distinct=" + seen.size() + " pool=" + RaidInstance.PLAYER_BOONS.size());
		helper.assertTrue(seen.size() >= Math.min(RaidInstance.PLAYER_BOONS.size(), 3) && RaidInstance.PLAYER_BOONS.size() >= 3,
			"64 draws from " + RaidInstance.PLAYER_BOONS.size() + " gave only " + seen);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:hard", maxTicks = 200)
	public void inHardModeEachRaidMobHoldsTwoBoons(final GameTestHelper helper) {
		RaidInstance.reset(helper.getLevel().getServer(), "test");
		ServerPlayer host = enter(helper, RaidTravel.Choice.RAID, RaidMode.HARD);
		ZombifiedPiglin mob = spawnInRaid((ServerLevel) host.level(), host.position().add(3.0, 0.0, 0.0));
		helper.succeedWhen(() -> {
			helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(mob.getUUID()), "the mob has not loaded yet");
			List<Holder<MobEffect>> boons = infinite(mob);
			NetherTestSupport.result("hardMobBoons", "boons=" + boons);
			helper.assertTrue(boons.size() == RaidInstance.MOB_BOONS_EACH && RaidInstance.MOB_BOONS.containsAll(boons)
				&& new HashSet<>(boons).size() == boons.size(), "a hard raid mob holds " + boons);
			mob.discard();
			NetherTestSupport.leave(helper, host);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:normalmobs", maxTicks = 200)
	public void inNormalModeRaidMobsHoldNoBoons(final GameTestHelper helper) {
		RaidInstance.reset(helper.getLevel().getServer(), "test");
		ServerPlayer host = enter(helper, RaidTravel.Choice.RAID, RaidMode.NORMAL);
		ZombifiedPiglin mob = spawnInRaid((ServerLevel) host.level(), host.position().add(3.0, 0.0, 0.0));
		helper.succeedWhen(() -> {
			helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(mob.getUUID()), "the mob has not loaded yet");
			List<Holder<MobEffect>> boons = infinite(mob);
			NetherTestSupport.result("normalMobBoons", "boons=" + boons);
			helper.assertTrue(boons.isEmpty(), "a normal raid mob holds " + boons);
			mob.discard();
			NetherTestSupport.leave(helper, host);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:undead", maxTicks = 100)
	public void anUndeadRaidMobIsNeverDrawnABoonItRefuses(final GameTestHelper helper) {
		RaidInstance.reset(helper.getLevel().getServer(), "test");
		ServerPlayer host = enter(helper, RaidTravel.Choice.RAID, RaidMode.HARD);
		ServerLevel raid = (ServerLevel) host.level();
		int short_ = 0;
		for (int i = 0; i < 32; i++) {
			ZombifiedPiglin mob = EntityTypes.ZOMBIFIED_PIGLIN.create(raid, EntitySpawnReason.MOB_SUMMONED);
			RaidInstance.boostMob(raid, mob);
			if (infinite(mob).size() != RaidInstance.MOB_BOONS_EACH) {
				short_++;
			}
		}
		NetherTestSupport.result("undeadBoons", "short=" + short_ + " of=32");
		NetherTestSupport.leave(helper, host);
		helper.assertTrue(short_ == 0, short_ + " of 32 undead raid mobs held fewer than " + RaidInstance.MOB_BOONS_EACH + " boons");
		helper.succeed();
	}

	static ZombifiedPiglin spawnInRaid(final ServerLevel raid, final Vec3 at) {
		ZombifiedPiglin mob = EntityTypes.ZOMBIFIED_PIGLIN.create(raid, EntitySpawnReason.MOB_SUMMONED);
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		raid.addFreshEntity(mob);
		return mob;
	}
}
