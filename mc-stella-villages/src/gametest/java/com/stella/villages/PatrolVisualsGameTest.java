package com.stella.villages;

import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import com.stella.villages.patrol.PatrolVisuals;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.phys.Vec3;

/**
 * V5: a patrol member is told apart from a hostile illager with vanilla assets only, so a client needs
 * nothing extra. Pillagers and Vindicators wear the village banner; every member,
 * gives off the village sparkle. An ordinary illager keeps its vanilla look.
 */
public class PatrolVisualsGameTest {
	/** Sparkles each member gave off, and the most players one reached. */
	private static final Map<UUID, AtomicInteger> SPARKLES = new ConcurrentHashMap<>();
	private static final Map<UUID, AtomicInteger> VIEWERS = new ConcurrentHashMap<>();

	static {
		PatrolVisuals.setRecorder(new PatrolVisuals.Recorder() {
			@Override
			public void sparkled(final Mob member, final int viewers) {
				SPARKLES.computeIfAbsent(member.getUUID(), key -> new AtomicInteger()).incrementAndGet();
				VIEWERS.computeIfAbsent(member.getUUID(), key -> new AtomicInteger()).accumulateAndGet(viewers, Math::max);
			}
		});
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void patrolFootSoldiersWearTheVillageBanner(final GameTestHelper helper) {
		String test = "patrolFootSoldiersWearTheVillageBanner";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		List<Mob> soldiers = PatrolTestSupport.patrols(helper).stream()
			.filter(mob -> PatrolState.role(mob) == PatrolState.Role.VINDICATOR || PatrolState.role(mob) == PatrolState.Role.PILLAGER)
			.toList();
		helper.assertTrue(!soldiers.isEmpty(), "the roster has no foot soldiers, so the case proves nothing");
		for (Mob soldier : soldiers) {
			helper.assertTrue(PatrolVisuals.isVillageBanner(soldier.getItemBySlot(EquipmentSlot.HEAD)), PatrolTestSupport.typeId(soldier) + " wears " + soldier.getItemBySlot(EquipmentSlot.HEAD));
			helper.assertTrue(soldier.getDropChances().byEquipment(EquipmentSlot.HEAD) == 0.0F, "a patrol banner can drop");
		}
		PatrolTestSupport.result(test, "soldiers=" + soldiers.size() + " banners=" + soldiers.size());
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void everyPatrolMemberGivesOffTheVillageSparkle(final GameTestHelper helper) {
		String test = "everyPatrolMemberGivesOffTheVillageSparkle";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		watcher.setPos(at.x, at.y, at.z);
		helper.succeedWhen(() -> {
			List<Mob> members = PatrolTestSupport.patrols(helper);
			helper.assertTrue(members.size() == PatrolService.PATROL_MEMBER_COUNT, "the roster is incomplete, so the case proves nothing");
			for (Mob member : members) {
				AtomicInteger sparkles = SPARKLES.get(member.getUUID());
				helper.assertTrue(sparkles != null && sparkles.get() > 0, PatrolTestSupport.typeId(member) + " gave off no sparkle yet");
			}
			boolean seen = members.stream().anyMatch(member -> VIEWERS.getOrDefault(member.getUUID(), new AtomicInteger()).get() > 0);
			helper.assertTrue(seen, "no sparkle reached a player");
			PatrolTestSupport.result(test, "members=" + members.size() + " seen=" + seen);
		});
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void anOrdinaryIllagerKeepsItsVanillaLook(final GameTestHelper helper) {
		Pillager pillager = helper.spawnMob(EntityTypes.PILLAGER, 2, 2, 2).spawn();
		helper.runAfterDelay(PatrolVisuals.SPARKLE_INTERVAL_TICKS * 3L, () -> {
			helper.assertFalse(PatrolState.isMarked(pillager), "the control pillager is a patrol member, so the case proves nothing");
			helper.assertFalse(PatrolVisuals.isVillageBanner(pillager.getItemBySlot(EquipmentSlot.HEAD)), "an ordinary pillager wears the village banner");
			helper.assertTrue(SPARKLES.get(pillager.getUUID()) == null, "an ordinary pillager gave off the village sparkle");
			PatrolTestSupport.result("anOrdinaryIllagerKeepsItsVanillaLook", "banner=false sparkles=0");
			helper.succeed();
		});
	}
}
