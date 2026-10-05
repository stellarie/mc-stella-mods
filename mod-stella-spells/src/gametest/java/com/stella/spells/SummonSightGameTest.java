package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A servant hunts what its master sees. A wall the servant cannot see through does not hide a foe from
 * the pair, and a mob wearing a friend mark is never the quarry.
 */
public class SummonSightGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.summonsight");

	@GameTest(maxTicks = 100)
	public void aServantTakesAFoeItsMasterSeesThroughAWall(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aServantTakesAFoeItsMasterSeesThroughAWall");
		helper.setTime(18_000L);
		floor(helper);
		// The servant is walled off from the foe; the master stands in the same column as the foe, so its
		// own ray never crosses the wall.
		ServerPlayer master = master(helper, 5.5, 0.5);
		Skeleton servant = servant(helper, master, 3, 3);
		wall(helper, 4, 3);
		Mob foe = foe(helper, 5, 5);
		boolean servantSees = servant.hasLineOfSight(foe);
		boolean masterSees = master.hasLineOfSight(foe);
		helper.assertTrue(masterSees, "the master cannot see the foe either, so the case proves nothing");
		helper.assertTrue(!servantSees, "the servant sees through the wall, so the case proves nothing");
		Mob found = SummonSkeleton.targetFor(servant, SummonSkeleton.Mode.SEARCH);
		boolean shared = SummonSkeleton.sighted(servant, master, foe);
		LOG.info("[BEHAVIOR] test=aServantTakesAFoeItsMasterSeesThroughAWall RESULT servantSees={} masterSees={} shared={} found={}",
			servantSees, masterSees, shared, found == foe ? "foe" : String.valueOf(found));
		helper.assertTrue(shared, "the servant does not share its master's sight");
		helper.assertTrue(found == foe, "the servant did not take the foe its master can see: " + found);
		servant.discard();
		foe.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aServantNeverTakesAFriendAsQuarry(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aServantNeverTakesAFriendAsQuarry");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		Mob friend = foe(helper, 3, 1);
		for (String tag : Friends.FRIEND_TAGS) {
			friend.addTag(tag);
		}
		helper.assertTrue(Friends.isFriend(friend),
			"the control mob does not count as a friend, so the case proves nothing: " + friend.entityTags());
		helper.assertTrue(master.hasLineOfSight(friend), "the master cannot see the friend, so the case proves nothing");
		helper.assertTrue(friend.distanceTo(master) <= SummonSkeleton.GUARD_RADIUS,
			"the friend stands outside the guard radius, so the case proves nothing");
		Mob found = SummonSkeleton.targetFor(servant, SummonSkeleton.Mode.SEARCH);
		LOG.info("[BEHAVIOR] test=aServantNeverTakesAFriendAsQuarry RESULT friendDistance={} friendSeen={} friendCounts={} found={}",
			friend.distanceTo(master), master.hasLineOfSight(friend), Friends.isFoe(friend),
			found == null ? "none" : found == friend ? "friend" : found.getUUID().toString());
		helper.assertTrue(found != friend, "the servant took a mob wearing a friend mark as its quarry");
		servant.discard();
		friend.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aServantTakesAFreshFoeAtOnce(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aServantTakesAFreshFoeAtOnce");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		helper.assertTrue(SummonSkeleton.targetFor(servant, SummonSkeleton.Mode.SEARCH) == null,
			"the servant started with a quarry, so the case proves nothing");
		Mob foe = foe(helper, 5, 3);
		int arrivedAt = foe.tickCount;
		Mob found = SummonSkeleton.targetFor(servant, SummonSkeleton.Mode.SEARCH);
		LOG.info("[BEHAVIOR] test=aServantTakesAFreshFoeAtOnce RESULT arrivedAt={} found={} within={}",
			arrivedAt, found == foe ? "foe" : String.valueOf(found), found == foe);
		helper.assertTrue(found == foe, "the servant did not take a fresh foe in range at once: " + found);
		servant.discard();
		foe.discard();
		leave(helper, master);
		helper.succeed();
	}

	/** A three-high wall on the servant's own line to the foe. */
	private static void wall(final GameTestHelper helper, final int x, final int z) {
		for (int dz = 0; dz <= 2; dz++) {
			for (int dy = 1; dy <= 3; dy++) {
				helper.setBlock(x, dy, z + dz, Blocks.STONE);
			}
		}
	}

	private static Mob foe(final GameTestHelper helper, final int x, final int z) {
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a foe");
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static Skeleton servant(final GameTestHelper helper, final ServerPlayer master, final int x, final int z) {
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "the summon raised no servant, so the case proves nothing");
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		servant.teleportTo(at.x, at.y, at.z);
		servant.setNoAi(true);
		return servant;
	}

	private static SpellbookItem book() {
		return (SpellbookItem) BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
	}

	private static ServerPlayer master(final GameTestHelper helper, final double x, final double z) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "summoner"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	private static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
