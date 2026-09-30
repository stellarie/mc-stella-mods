package com.stella.rpg;

import com.stella.rpg.net.AllocateBatchPayload;
import com.stella.rpg.net.AllocatePayload;
import com.stella.rpg.net.RespecPayload;
import com.stella.rpg.net.RpgNet;
import com.stella.rpg.net.StatsSyncPayload;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RpgNetGameTest {
	@GameTest
	public void allocatePayload(final GameTestHelper helper) {
		ServerPlayer viaPayload = RpgTestSupport.player(helper);
		ServerPlayer direct = RpgTestSupport.player(helper);
		viaPayload.experienceLevel = 10;
		direct.experienceLevel = 10;
		StatsSyncPayload reply = RpgNet.allocate(viaPayload, new AllocatePayload(Stat.STR.ordinal(), 3));
		StatPoints.Result expected = StatPoints.allocate(direct, Stat.STR, 3);
		helper.assertTrue(expected == StatPoints.Result.OK, "direct result=" + expected);
		helper.assertTrue(reply.resultCode() == expected, "reply result=" + reply.resultCode());
		helper.assertTrue(StatPoints.sheet(viaPayload).equals(StatPoints.sheet(direct)), "sheet=" + StatPoints.sheet(viaPayload));
		helper.assertTrue(reply.base()[Stat.STR.ordinal()] == 3, "reply base=" + reply);
		helper.assertTrue(reply.unspent() == StatPoints.unspent(10, StatPoints.sheet(direct)), "reply unspent=" + reply.unspent());
		StatsSyncPayload refused = RpgNet.allocate(viaPayload, new AllocatePayload(Stat.AGI.ordinal(), 100));
		StatPoints.Result directRefused = StatPoints.allocate(direct, Stat.AGI, 100);
		helper.assertTrue(refused.resultCode() == directRefused, "refused result=" + refused.resultCode());
		helper.assertTrue(StatPoints.sheet(viaPayload).equals(StatPoints.sheet(direct)), "sheet after refusal=" + StatPoints.sheet(viaPayload));
		RpgTestSupport.result("allocatePayload", "reply=" + reply);
		helper.succeed();
	}

	@GameTest
	public void badPayloadRefused(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		StatSheet before = StatPoints.sheet(player);
		StatsSyncPayload badStat = RpgNet.allocate(player, new AllocatePayload(9, 1));
		helper.assertTrue(badStat.resultCode() == StatPoints.Result.INVALID_STAT, "stat 9 result=" + badStat.resultCode());
		StatsSyncPayload negativeStat = RpgNet.allocate(player, new AllocatePayload(-1, 1));
		helper.assertTrue(negativeStat.resultCode() == StatPoints.Result.INVALID_STAT, "stat -1 result=" + negativeStat.resultCode());
		StatsSyncPayload zero = RpgNet.allocate(player, new AllocatePayload(Stat.STR.ordinal(), 0));
		helper.assertTrue(zero.resultCode() == StatPoints.Result.INVALID_AMOUNT, "amount 0 result=" + zero.resultCode());
		StatsSyncPayload huge = RpgNet.allocate(player, new AllocatePayload(Stat.STR.ordinal(), Integer.MAX_VALUE));
		helper.assertTrue(huge.resultCode() == StatPoints.Result.NOT_ENOUGH_POINTS, "huge result=" + huge.resultCode());
		helper.assertTrue(StatPoints.sheet(player).equals(before), "sheet changed: " + StatPoints.sheet(player));
		RpgTestSupport.result("badPayloadRefused", "stat9=" + badStat.resultCode() + " amount0=" + zero.resultCode());
		helper.succeed();
	}

	@GameTest
	public void batchAllocate(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		StatPoints.setSheet(player, new StatSheet(0, 12, 0, 0, 0, 0));
		helper.assertTrue(StatPoints.unspent(10, StatPoints.sheet(player)) == 10, "start unspent");
		StatsSyncPayload reply = RpgNet.allocateBatch(player, new AllocateBatchPayload(new int[] {3, 0, 2, 0, 0, 1}));
		helper.assertTrue(reply.resultCode() == StatPoints.Result.OK, "result=" + reply.resultCode());
		StatSheet sheet = StatPoints.sheet(player);
		helper.assertTrue(sheet.equals(new StatSheet(3, 12, 2, 0, 0, 1)), "sheet=" + sheet);
		helper.assertTrue(reply.unspent() == 4, "reply unspent=" + reply.unspent());
		RpgTestSupport.result("batchAllocate", "reply=" + reply);
		helper.succeed();
	}

	@GameTest
	public void batchIsAtomic(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 2000;
		StatSheet before = StatPoints.sheet(player);
		StatsSyncPayload over = RpgNet.allocateBatch(player, new AllocateBatchPayload(new int[] {1, 1, 1, 1, 1, 5000}));
		helper.assertTrue(over.resultCode() == StatPoints.Result.NOT_ENOUGH_POINTS, "over result=" + over.resultCode());
		helper.assertTrue(StatPoints.sheet(player).equals(before), "sheet after overspend=" + StatPoints.sheet(player));
		StatsSyncPayload ceiling = RpgNet.allocateBatch(player, new AllocateBatchPayload(new int[] {2, 0, 0, 0, 0, 1025}));
		helper.assertTrue(ceiling.resultCode() == StatPoints.Result.ABOVE_CEILING, "ceiling result=" + ceiling.resultCode());
		helper.assertTrue(StatPoints.sheet(player).equals(before), "sheet after ceiling=" + StatPoints.sheet(player));
		helper.assertTrue(ceiling.unspent() == StatPoints.total(2000), "unspent=" + ceiling.unspent());
		RpgTestSupport.result("batchIsAtomic", "over=" + over.resultCode() + " ceiling=" + ceiling.resultCode());
		helper.succeed();
	}

	@GameTest
	public void batchRejectsBadInput(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		StatSheet before = StatPoints.sheet(player);
		int[][] bad = {
			{1, 1, -1, 0, 0, 0},
			{5, 5, -5, 0, 0, 5},
			{1, 1, 1, 1, 1},
			{1, 1, 1, 1, 1, 1, 1},
			{},
			{0, 0, 0, 0, 0, 0}
		};
		for (int[] amounts : bad) {
			StatsSyncPayload reply = RpgNet.allocateBatch(player, new AllocateBatchPayload(amounts));
			helper.assertTrue(reply.resultCode() == StatPoints.Result.INVALID_AMOUNT, "result=" + reply.resultCode() + " len=" + amounts.length);
			helper.assertTrue(StatPoints.sheet(player).equals(before), "sheet changed: " + StatPoints.sheet(player));
		}
		RpgTestSupport.result("batchRejectsBadInput", "cases=" + bad.length);
		helper.succeed();
	}

	@GameTest
	public void batchCodecRoundTrip(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
		AllocateBatchPayload.CODEC.encode(buf, new AllocateBatchPayload(new int[] {3, 0, 2, 0, 0, 1}));
		AllocateBatchPayload decoded = AllocateBatchPayload.CODEC.decode(buf);
		helper.assertTrue(Arrays.equals(decoded.amounts(), new int[] {3, 0, 2, 0, 0, 1}), "decoded mismatch");
		helper.assertTrue(buf.readableBytes() == 0, "unread bytes=" + buf.readableBytes());
		helper.succeed();
	}

	@GameTest
	public void respecPayload(final GameTestHelper helper) {
		ServerPlayer rich = allocated(helper, 320);
		StatsSyncPayload reply = RpgNet.respec(rich);
		helper.assertTrue(reply.resultCode() == StatPoints.Result.OK, "rich result=" + reply.resultCode());
		helper.assertTrue(StatPoints.sheet(rich).equals(StatSheet.EMPTY), "sheet=" + StatPoints.sheet(rich));
		helper.assertTrue(StatPoints.countEmeralds(rich) == 0, "emeralds=" + StatPoints.countEmeralds(rich));
		helper.assertTrue(reply.unspent() == StatPoints.total(10), "reply unspent=" + reply.unspent());
		ServerPlayer poor = allocated(helper, 319);
		StatSheet before = StatPoints.sheet(poor);
		StatsSyncPayload refused = RpgNet.respec(poor);
		helper.assertTrue(refused.resultCode() == StatPoints.Result.NOT_ENOUGH_EMERALDS, "poor result=" + refused.resultCode());
		helper.assertTrue(StatPoints.sheet(poor).equals(before), "poor sheet=" + StatPoints.sheet(poor));
		helper.assertTrue(StatPoints.countEmeralds(poor) == 319, "poor emeralds=" + StatPoints.countEmeralds(poor));
		RpgTestSupport.result("respecPayload", "rich=" + reply.resultCode() + " poor=" + refused.resultCode());
		helper.succeed();
	}

	@GameTest
	public void syncContent(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 30;
		RpgTestSupport.setStat(player, Stat.STR, 25);
		RpgTestSupport.setStat(player, Stat.LUK, 7);
		StatBonuses.Provider bonus = (entity, stat) -> entity == player && stat == Stat.STR ? 5 : 0;
		StatBonuses.register(bonus);
		try {
			StatsSyncPayload payload = StatsSyncPayload.of(player);
			helper.assertTrue(payload.base().length == 6 && payload.bonus().length == 6, "lengths=" + payload);
			helper.assertTrue(payload.base()[Stat.STR.ordinal()] == 25, "str base=" + payload);
			helper.assertTrue(payload.base()[Stat.LUK.ordinal()] == 7, "luk base=" + payload);
			helper.assertTrue(payload.bonus()[Stat.STR.ordinal()] == 5, "str bonus=" + payload);
			helper.assertTrue(payload.bonus()[Stat.AGI.ordinal()] == 0, "agi bonus=" + payload);
			helper.assertTrue(payload.unspent() == StatPoints.total(30) - 32, "unspent=" + payload.unspent());
			helper.assertTrue(payload.level() == 30, "level=" + payload.level());
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
			StatsSyncPayload.CODEC.encode(buf, payload);
			StatsSyncPayload decoded = StatsSyncPayload.CODEC.decode(buf);
			helper.assertTrue(decoded.equals(payload), "decoded=" + decoded + " original=" + payload);
			helper.assertTrue(buf.readableBytes() == 0, "unread bytes=" + buf.readableBytes());
			RpgTestSupport.result("syncContent", payload.toString());
		} finally {
			StatBonuses.unregister(bonus);
		}
		helper.succeed();
	}

	@GameTest
	public void noSendToVanillaClient(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		helper.assertTrue(!ServerPlayNetworking.canSend(player, StatsSyncPayload.TYPE), "test player can receive the payload");
		helper.assertTrue(!RpgNet.sync(player), "sync sent to a player without the channel");
		StatsSyncPayload reply = RpgNet.allocate(player, new AllocatePayload(Stat.STR.ordinal(), 1));
		helper.assertTrue(reply.resultCode() == StatPoints.Result.OK, "reply result=" + reply.resultCode());
		helper.assertTrue(StatPoints.sheet(player).get(Stat.STR) == 1, "sheet=" + StatPoints.sheet(player));
		RpgNet.syncIfChanged(player);
		RpgTestSupport.result("noSendToVanillaClient", "sync=false");
		helper.succeed();
	}

	private static ServerPlayer allocated(final GameTestHelper helper, final int emeralds) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.experienceLevel = 10;
		helper.assertTrue(StatPoints.allocate(player, Stat.STR, 6) == StatPoints.Result.OK, "allocation refused");
		int remaining = emeralds;
		int slot = 0;
		while (remaining > 0) {
			int count = Math.min(64, remaining);
			player.getInventory().setItem(slot++, new ItemStack(Items.EMERALD, count));
			remaining -= count;
		}
		return player;
	}
}
