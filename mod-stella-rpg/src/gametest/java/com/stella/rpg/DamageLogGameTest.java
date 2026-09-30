package com.stella.rpg;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** The opt-in damage counter: dealt and taken lines in chat, silent when off, and silent for poison ticks. */
public final class DamageLogGameTest {
	@GameTest
	public void dealtAndTakenLines(final GameTestHelper helper) {
		RpgTestSupport.Recorded recorded = RpgTestSupport.recordedPlayer(helper);
		ServerPlayer player = recorded.player();
		DamageLog.set(player, true);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		lines(recorded);
		float before = zombie.getHealth();
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 4.0F);
		int dealt = Math.round(before - zombie.getHealth());
		List<String> dealtLines = lines(recorded);
		float playerBefore = player.getHealth();
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 2.0F);
		int taken = Math.round(playerBefore - player.getHealth());
		List<String> takenLines = lines(recorded);
		RpgTestSupport.result("dealtAndTakenLines", "dealt=" + dealtLines + " taken=" + takenLines);
		helper.assertTrue(dealtLines.contains(DamageLog.dealt(dealt, "Zombie")), "no dealt line: " + dealtLines);
		helper.assertTrue(takenLines.contains(DamageLog.taken(taken, "Zombie")), "no taken line: " + takenLines);
		helper.assertTrue(dealt > 0 && taken > 0, "dealt=" + dealt + " taken=" + taken);
		helper.succeed();
	}

	@GameTest
	public void silentWhenOffAndForPoison(final GameTestHelper helper) {
		RpgTestSupport.Recorded recorded = RpgTestSupport.recordedPlayer(helper);
		ServerPlayer player = recorded.player();
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		lines(recorded);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 4.0F);
		List<String> off = lines(recorded);
		DamageLog.set(player, true);
		MobEffects.POISON.value().applyEffectTick(helper.getLevel(), player, 0);
		List<String> poison = lines(recorded);
		RpgTestSupport.result("silentWhenOffAndForPoison", "off=" + off + " poison=" + poison);
		helper.assertTrue(off.stream().noneMatch(line -> line.startsWith("You dealt")), "a counter line while off: " + off);
		helper.assertTrue(poison.stream().noneMatch(line -> line.contains("damage")), "a poison tick was logged: " + poison);
		helper.succeed();
	}

	private static List<String> lines(final RpgTestSupport.Recorded recorded) {
		List<String> lines = new ArrayList<>();
		Object message;
		while ((message = recorded.channel().readOutbound()) != null) {
			if (message instanceof ClientboundSystemChatPacket chat && !chat.overlay()) {
				lines.add(chat.content().getString());
			}
		}
		return lines;
	}
}
