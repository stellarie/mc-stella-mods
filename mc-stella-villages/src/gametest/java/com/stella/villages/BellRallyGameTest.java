package com.stella.villages;

import com.stella.villages.patrol.BellRally;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Surprise, villages: ringing a village's bell rallies its whole patrol, once a minute, and a bell that
 * belongs to no village rallies nobody.
 */
public class BellRallyGameTest {
	private static final Map<BlockPos, List<List<UUID>>> RALLIES = new ConcurrentHashMap<>();
	private static final Map<BlockPos, List<String>> REFUSALS = new ConcurrentHashMap<>();

	static {
		BellRally.setRecorder(new BellRally.Recorder() {
			@Override
			public void rallied(final BlockPos bell, final UUID villageId, final List<UUID> members) {
				RALLIES.computeIfAbsent(bell, key -> new CopyOnWriteArrayList<>()).add(List.copyOf(members));
			}

			@Override
			public void refused(final BlockPos bell, final UUID villageId, final String reason) {
				REFUSALS.computeIfAbsent(bell, key -> new CopyOnWriteArrayList<>()).add(reason);
			}
		});
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void ringingTheVillageBellRalliesTheWholePatrol(final GameTestHelper helper) {
		String test = "ringingTheVillageBellRalliesTheWholePatrol";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		BlockPos bell = PatrolTestSupport.anchor(helper);
		List<Mob> patrol = PatrolTestSupport.patrols(helper);
		ring(helper, bell);
		List<List<UUID>> rallies = RALLIES.getOrDefault(bell, List.of());
		helper.assertTrue(rallies.size() == 1, "the bell made " + rallies.size() + " rallies");
		Set<UUID> rallied = Set.copyOf(rallies.get(0));
		helper.assertTrue(!patrol.isEmpty() && patrol.stream().allMatch(mob -> rallied.contains(mob.getUUID())), "the rally left out patrol members: " + patrol.size() + " vs " + rallied.size());
		for (Mob member : patrol) {
			MobEffectInstance speed = member.getEffect(MobEffects.SPEED);
			helper.assertTrue(speed != null && speed.getAmplifier() == BellRally.SPEED_AMPLIFIER && member.hasEffect(MobEffects.STRENGTH), PatrolTestSupport.typeId(member) + " was not rallied: " + speed);
		}
		PatrolTestSupport.result(test, "rallied=" + rallied.size() + " patrol=" + patrol.size());
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aSecondRingInsideTheCooldownRalliesNobody(final GameTestHelper helper) {
		String test = "aSecondRingInsideTheCooldownRalliesNobody";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		BlockPos bell = PatrolTestSupport.anchor(helper);
		ring(helper, bell);
		ring(helper, bell);
		helper.assertTrue(RALLIES.getOrDefault(bell, List.of()).size() == 1, "two rings made " + RALLIES.getOrDefault(bell, List.of()).size() + " rallies");
		helper.assertTrue(REFUSALS.getOrDefault(bell, List.of()).contains("cooldown"), "the second ring was not refused for the cooldown: " + REFUSALS.get(bell));
		PatrolTestSupport.result(test, "rallies=1 refusals=" + REFUSALS.get(bell));
		helper.succeed();
	}

	// Padded like the village tests, so a neighbour's village anchor is always past the rally range.
	@GameTest(maxTicks = 60, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aBellWithNoVillageRalliesNobody(final GameTestHelper helper) {
		helper.setBlock(2, 0, 2, Blocks.STONE);
		helper.setBlock(2, 1, 2, Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(2, 1, 2));
		ring(helper, bell);
		helper.assertTrue(!RALLIES.containsKey(bell), "a bell outside any village rallied a patrol");
		PatrolTestSupport.result("aBellWithNoVillageRalliesNobody", "rallies=0");
		helper.succeed();
	}

	private static void ring(final GameTestHelper helper, final BlockPos bell) {
		ServerPlayer ringer = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(bell.north());
		ringer.setPos(at.x, at.y, at.z);
		UseBlockCallback.EVENT.invoker().interact(
			ringer, helper.getLevel(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(bell), Direction.NORTH, bell, false)
		);
	}
}
