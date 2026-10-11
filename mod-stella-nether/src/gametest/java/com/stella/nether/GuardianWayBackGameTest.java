package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A player who leaves a raid, by its portal, a log-in or a death, appears in front of the guardian they used. */
public class GuardianWayBackGameTest {
	/** What is wrong with where a player stands relative to a guardian, or nothing: near, in front, on ground with room, facing it. */
	static String notInFront(final ServerLevel nether, final BlazeGuardian guardian, final ServerPlayer player) {
		StringBuilder wrong = new StringBuilder();
		if (!player.level().dimension().equals(Level.NETHER)) {
			return " not in the nether, in " + player.level().dimension().identifier();
		}
		Vec3 offset = player.position().subtract(guardian.position());
		Vec3 flat = new Vec3(offset.x, 0.0, offset.z);
		double distance = flat.length();
		if (distance < 1.0 || distance > 3.2) {
			wrong.append(" distance ").append(String.format("%.2f", distance));
		}
		double ahead = flat.lengthSqr() < 1.0E-9 ? -1.0 : Vec3.directionFromRotation(0.0F, guardian.getYRot()).dot(flat.normalize());
		if (ahead <= 0.5) {
			wrong.append(" ahead ").append(String.format("%.2f", ahead));
		}
		BlockPos under = player.blockPosition().below();
		if (!nether.getBlockState(under).isFaceSturdy(nether, under, Direction.UP) || Math.abs(player.getY() - player.blockPosition().getY()) > 1.0E-6) {
			wrong.append(" not on the ground at ").append(player.position());
		}
		if (!nether.noCollision(player.getBoundingBox())) {
			wrong.append(" stuck in a block");
		}
		Vec3 toGuardian = guardian.position().subtract(player.position());
		double looking = Vec3.directionFromRotation(0.0F, player.getYRot()).dot(new Vec3(toGuardian.x, 0.0, toGuardian.z).normalize());
		if (looking < 0.9) {
			wrong.append(" looking ").append(String.format("%.2f", looking));
		}
		return wrong.toString();
	}

	/** A second guardian across the nether from the standing one, facing the other way. */
	static BlazeGuardian other(final GameTestHelper helper) {
		return NetherTestSupport.guardianAt(helper, 3064, 3000, 90.0F);
	}

	@GameTest(environment = "mod-stella-nether-test:back_portal", maxTicks = 100)
	public void leavingByTheRaidPortalPutsThePlayerInFrontOfTheGuardianTheyUsed(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerLevel nether = NetherTestSupport.nether(helper);
		BlazeGuardian first = NetherTestSupport.hub(helper);
		BlazeGuardian second = other(helper);
		ServerPlayer player = NetherTestSupport.atGuardian(helper, second);
		NetherTestSupport.enterAt(helper, player, second, RaidMode.HARD);
		boolean inside = player.level().dimension().equals(RaidTravel.RAID);
		NetherTestSupport.leaveByPortal(helper, player, RaidMode.HARD);
		String wrong = notInFront(nether, second, player);
		double fromFirst = player.position().distanceTo(first.position());
		NetherTestSupport.result("backByPortal", "inside=" + inside + " wrong=[" + wrong + " ] fromUsed=" + String.format("%.2f", player.position().distanceTo(second.position()))
			+ " fromOther=" + String.format("%.1f", fromFirst));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(inside, "the player never entered the raid");
		helper.assertTrue(wrong.isEmpty(), "the player left the raid and stands:" + wrong);
		helper.assertTrue(fromFirst > 30.0, "the player came out " + fromFirst + " blocks from the other guardian");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:back_login", maxTicks = 100)
	public void loggingBackInInsideTheRaidPutsThePlayerInFrontOfTheirGuardian(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerLevel nether = NetherTestSupport.nether(helper);
		BlazeGuardian first = NetherTestSupport.hub(helper);
		BlazeGuardian second = other(helper);
		ServerPlayer player = NetherTestSupport.atGuardian(helper, first);
		NetherTestSupport.enterAt(helper, player, first, RaidMode.NORMAL);
		RaidInstance.onJoin(player);
		String wrong = notInFront(nether, first, player);
		NetherTestSupport.result("backByLogin", "wrong=[" + wrong + " ] fromOther=" + String.format("%.1f", player.position().distanceTo(second.position())));
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(wrong.isEmpty(), "the player who logged back in stands:" + wrong);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:back_death", maxTicks = 100)
	public void dyingInTheRaidWakesThePlayerInFrontOfTheirGuardian(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		ServerLevel nether = NetherTestSupport.nether(helper);
		BlazeGuardian guardian = NetherTestSupport.hub(helper);
		ServerPlayer dying = NetherTestSupport.atGuardian(helper, guardian);
		NetherTestSupport.enterAt(helper, dying, guardian, RaidMode.NORMAL);
		ServerGamePacketListenerImpl connection = dying.connection;
		NetherTestSupport.vulnerable(dying);
		dying.kill((ServerLevel) dying.level());
		boolean dead = dying.isDeadOrDying();
		// The respawn button, as the client sends it; the connection then points at the new player.
		connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
		ServerPlayer woken = connection.player;
		String wrong = woken == dying ? " never respawned" : notInFront(nether, guardian, woken);
		NetherTestSupport.result("backByDeath", "dead=" + dead + " wrong=[" + wrong + " ] level=" + woken.level().dimension().identifier() + " at=" + woken.position()
			+ " guardian=" + guardian.position());
		NetherTestSupport.leave(helper, woken);
		helper.assertTrue(dead, "the player in the raid did not die");
		helper.assertTrue(wrong.isEmpty(), "the player who died in the raid wakes and stands:" + wrong);
		helper.succeed();
	}
}
