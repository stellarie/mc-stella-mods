package com.stella.rpg;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** A death recap lists the recent hits that led to a player's death, and leaves out hits older than ten seconds. */
public final class DeathRecapGameTest {
	@GameTest
	public void recapListsHits(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty before = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		List<String> captured = new ArrayList<>();
		DeathRecap.setRecorder((who, lines) -> {
			if (who == player) {
				captured.addAll(lines);
			}
		});
		try {
			DeathRecap.record(player, new DeathRecap.Hit(level.getGameTime() - DeathRecap.WINDOW_TICKS - 50, "Ancient", "old", 99));
			for (int i = 0; i < 2; i++) {
				player.damageCooldownTime = 0;
				player.hurtServer(level, level.damageSources().mobAttack(zombie), 2.0F);
			}
			player.damageCooldownTime = 0;
			player.hurtServer(level, level.damageSources().mobAttack(zombie), 10000.0F);
		} finally {
			DeathRecap.setRecorder(null);
			level.getServer().setDifficulty(before, true);
		}
		RpgTestSupport.result("deathRecap", String.join(" | ", captured));
		helper.assertTrue(!player.isAlive(), "the player survived, so no recap was due");
		helper.assertTrue(captured.size() == 5, "the recap has " + captured.size() + " lines: " + captured);
		helper.assertTrue(captured.getFirst().startsWith("Death recap"), "the header is " + captured.getFirst());
		helper.assertTrue(captured.subList(1, 4).stream().allMatch(line -> line.contains("Zombie") && line.contains("mob")),
			"a hit line lacks the attacker or the type: " + captured);
		helper.assertTrue(captured.stream().noneMatch(line -> line.contains("Ancient")), "an old hit stayed in the recap");
		helper.assertTrue(captured.getLast().contains("from 3 hits"), "the total line is " + captured.getLast());
		helper.succeed();
	}

	/** A lethal burn joins the recap as one line that sums its ticks; before, damage-over-time ticks never entered it. */
	@GameTest
	public void lethalBurnIsListed(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty before = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		List<String> captured = new ArrayList<>();
		DeathRecap.setRecorder((who, lines) -> {
			if (who == player) {
				captured.addAll(lines);
			}
		});
		try {
			player.damageCooldownTime = 0;
			player.hurtServer(level, level.damageSources().mobAttack(zombie), 2.0F);
			for (int i = 0; i < 3; i++) {
				player.damageCooldownTime = 0;
				player.hurtServer(level, level.damageSources().onFire(), 1.0F);
			}
			player.damageCooldownTime = 0;
			player.hurtServer(level, level.damageSources().onFire(), 10000.0F);
		} finally {
			DeathRecap.setRecorder(null);
			level.getServer().setDifficulty(before, true);
		}
		RpgTestSupport.result("deathRecapBurn", String.join(" | ", captured));
		helper.assertTrue(!player.isAlive(), "the player survived, so no recap was due");
		long fireLines = captured.stream().filter(line -> line.contains("onFire")).count();
		helper.assertTrue(fireLines == 1, "the burn shows on " + fireLines + " lines: " + captured);
		helper.assertTrue(captured.getLast().contains("from 2 hits"), "the total line is " + captured.getLast());
		helper.succeed();
	}

	/** The recap shows only the newest {@link DeathRecap#MAX_LINES} hits, oldest first, and totals every hit. */
	@GameTest
	public void capKeepsNewestHitsInOrder(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		long now = helper.getLevel().getGameTime();
		int hits = DeathRecap.MAX_LINES + 2;
		for (int i = 0; i < hits; i++) {
			DeathRecap.record(player, new DeathRecap.Hit(now - hits + 1 + i, "A" + i, "mob", 1));
		}
		List<String> lines = DeathRecap.lines(player, now);
		RpgTestSupport.result("deathRecapCap", String.join(" | ", lines));
		helper.assertTrue(lines.size() == DeathRecap.MAX_LINES + 2, "the recap has " + lines.size() + " lines: " + lines);
		helper.assertTrue(lines.get(1).contains("A2 ") && lines.get(DeathRecap.MAX_LINES).contains("A" + (hits - 1) + " "),
			"the hit lines are not the newest, oldest first: " + lines);
		helper.assertTrue(lines.getLast().contains("from " + hits + " hits"), "the total line is " + lines.getLast());
		helper.succeed();
	}
}
