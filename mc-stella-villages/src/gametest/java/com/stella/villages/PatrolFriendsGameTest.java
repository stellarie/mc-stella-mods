package com.stella.villages;

import com.stella.villages.patrol.PatrolLabels;
import com.stella.villages.patrol.PatrolState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * A village patrol is one side: a stray hit between members does no harm and never turns one on
 * another, and every member wears its role over its head, green while calm and red while provoked.
 */
public class PatrolFriendsGameTest {
	private static final float HIT = 4.0F;

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void patrolMembersTakeNoDamageFromEachOther(final GameTestHelper helper) {
		String test = "patrolMembersTakeNoDamageFromEachOther";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob shooter = member(helper, PatrolState.Role.PILLAGER);
		Mob victim = member(helper, PatrolState.Role.HEAD);
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(shooter), HIT);
		float afterFriend = victim.getHealth();
		Zombie stranger = helper.spawnMob(EntityTypes.ZOMBIE, 1, 2, 1).withNoFreeWill().spawn();
		victim.damageCooldownTime = 0;
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(stranger), HIT);
		helper.assertTrue(victim.getHealth() < afterFriend, "a stranger's hit did no harm either, so the case proves nothing");
		helper.assertTrue(afterFriend == before, "a fellow member's hit took " + (before - afterFriend) + " health");
		PatrolTestSupport.result(test, "friendDamage=" + (before - afterFriend) + " strangerDamage=" + (afterFriend - victim.getHealth()));
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void patrolMembersNeverTargetEachOther(final GameTestHelper helper) {
		String test = "patrolMembersNeverTargetEachOther";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob guard = member(helper, PatrolState.Role.HEAD);
		Mob fellow = member(helper, PatrolState.Role.PILLAGER);
		guard.setTarget(fellow);
		net.minecraft.world.entity.LivingEntity afterFellow = guard.getTarget();
		Zombie stranger = helper.spawnMob(EntityTypes.ZOMBIE, 1, 2, 1).withNoFreeWill().spawn();
		guard.setTarget(stranger);
		helper.assertTrue(guard.getTarget() == stranger, "a member cannot target a stranger either, so the case proves nothing");
		helper.assertTrue(afterFellow != fellow, "a member targeted a fellow member");
		PatrolTestSupport.result(test, "fellowTargeted=" + (afterFellow == fellow) + " strangerTargeted=true");
		helper.succeed();
	}

	/** A guard never targets a blue slime, and the mage's harmful splash skips it. */
	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void blueSlimeIsNotAFoe(final GameTestHelper helper) {
		String test = "blueSlimeIsNotAFoe";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		Mob guard = member(helper, PatrolState.Role.HEAD);
		com.stella.mobs.entity.BlueSlime slime = helper.spawn(com.stella.mobs.entity.StellaEntities.BLUE_SLIME, 1, 2, 1);
		guard.setTarget(slime);
		boolean targeted = guard.getTarget() == slime;
		boolean splashed = com.stella.villages.patrol.VillageWitchSplash.hostile(slime);
		Zombie stranger = helper.spawnMob(EntityTypes.ZOMBIE, 1, 2, 1).withNoFreeWill().spawn();
		boolean controlSplash = com.stella.villages.patrol.VillageWitchSplash.hostile(stranger);
		PatrolTestSupport.result(test, "targeted=" + targeted + " splashed=" + splashed + " controlSplash=" + controlSplash);
		helper.assertTrue(controlSplash, "the splash skips a zombie too, so the case proves nothing");
		helper.assertTrue(!targeted && !splashed, "a guard targeted the blue slime or the splash took it as a foe");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void everyPatrolMemberWearsAGreenRoleLabel(final GameTestHelper helper) {
		String test = "everyPatrolMemberWearsAGreenRoleLabel";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		helper.succeedWhen(() -> {
			List<Mob> members = PatrolTestSupport.patrols(helper);
			helper.assertTrue(members.stream().anyMatch(mob -> PatrolState.role(mob) == PatrolState.Role.HEAD), "the roster has no head, so the case proves nothing");
			for (Mob member : members) {
				String wanted = PatrolLabels.label(PatrolState.role(member), false).getString();
				helper.assertTrue(labelled(member, wanted, PatrolLabels.CALM), PatrolTestSupport.typeId(member) + " wears " + member.getCustomName() + ", not a green " + wanted);
			}
			PatrolTestSupport.result(test, "members=" + members.size() + " labelled=" + members.size());
		});
	}

	@GameTest(maxTicks = 400, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aProvokedPatrolTurnsRedUntilTheThreatEnds(final GameTestHelper helper) {
		String test = "aProvokedPatrolTurnsRedUntilTheThreatEnds";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(PatrolTestSupport.anchor(helper)).add(0.0, 1.0, 0.0);
		attacker.setPos(at.x, at.y, at.z);
		if (!PatrolTestSupport.startThreat(helper, test, attacker.getUUID())) {
			return;
		}

		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(!PatrolTestSupport.patrols(helper).isEmpty(), "the roster is empty, so the case proves nothing");
				for (Mob member : PatrolTestSupport.patrols(helper)) {
					helper.assertTrue(labelColor(member, PatrolLabels.PROVOKED), PatrolTestSupport.typeId(member) + " is not red while provoked: " + member.getCustomName());
				}
			})
			.thenExecute(() -> attacker.setHealth(0.0F))
			.thenWaitUntil(() -> {
				helper.assertTrue(!PatrolTestSupport.patrols(helper).isEmpty(), "the roster is empty, so the case proves nothing");
				for (Mob member : PatrolTestSupport.patrols(helper)) {
					helper.assertTrue(labelColor(member, PatrolLabels.CALM), PatrolTestSupport.typeId(member) + " stayed red after the threat ended: " + member.getCustomName());
				}
			})
			.thenExecute(() -> PatrolTestSupport.result(test, "provoked=red calm=green members=" + PatrolTestSupport.patrols(helper).size()))
			.thenSucceed();
	}

	private static Mob member(final GameTestHelper helper, final PatrolState.Role role) {
		Mob found = PatrolTestSupport.patrols(helper).stream().filter(mob -> PatrolState.role(mob) == role).findFirst().orElse(null);
		helper.assertTrue(found != null, "the roster has no " + role);
		return found;
	}

	private static boolean labelled(final Mob mob, final String text, final ChatFormatting color) {
		return mob.isCustomNameVisible() && mob.getCustomName() != null && mob.getCustomName().getString().equals(text) && labelColor(mob, color);
	}

	private static boolean labelColor(final Mob mob, final ChatFormatting color) {
		return mob.getCustomName() != null && TextColor.fromLegacyFormat(color).equals(mob.getCustomName().getStyle().getColor());
	}
}
