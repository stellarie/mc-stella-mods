package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The tower's ghost illagers and ghost witches: a squad of three to five that rises on an occupied floor, stands on the
 * players' side, hunts the tower's mobs and is hunted by them, moves at three quarters of its kind's pace, and leaves nothing
 * behind. A ghost evoker's vexes are ghosts as well.
 */
public class TowerGhostSquadGameTest {
	/** A low floor for what a ghost deals and takes, so a zombie outlives the first blows. */
	private static final int LOW = 5;
	private static final List<EntityType<? extends Mob>> KINDS = List.of(GhostSquads.VINDICATOR, GhostSquads.PILLAGER, GhostSquads.EVOKER, GhostSquads.WITCH);

	/** A biome floor with no boss and no elite, whose sweeps would not touch a squad or a mob in the arena. */
	private static int freshFloor() {
		return TowerTestSupport.freshBiomeFloorWhere(f -> f % TowerElites.BOSS_EVERY != 0 && f % TowerElites.ELITE_EVERY != 0);
	}

	/** A floor with its mob cap, its ghost packs and its squads held off, and a stone platform in its room. */
	private record Arena(ServerLevel tower, int floor, Vec3 middle) {
		static Arena build(final GameTestHelper helper) {
			ServerLevel tower = TowerTestSupport.tower(helper);
			int floor = freshFloor();
			QuietFloor.hold(floor);
			TowerFloors.ensure(tower, floor);
			return new Arena(tower, floor, TowerTestSupport.arena(tower, floor, 11));
		}

		/** Takes down every mob of the floor and lets the pins go. */
		void close() {
			this.tower.getEntitiesOfClass(Mob.class, TowerMobs.room(this.floor), Mob::isAlive).forEach(Mob::discard);
			QuietFloor.release(this.floor);
		}

		Zombie zombie(final Vec3 at, final boolean thinking) {
			Zombie zombie = EntityTypes.ZOMBIE.create(this.tower, EntitySpawnReason.COMMAND);
			TowerMobs.makeTowerMob(zombie, LOW, TowerElement.DARK);
			zombie.snapTo(at);
			zombie.setNoAi(!thinking);
			this.tower.addFreshEntity(zombie);
			return zombie;
		}
	}

	/**
	 * Catches a squad that rises alone or in the wrong number, a ghost that is no tower mob of its floor, a squad that falls
	 * apart, or a ghost that counts against the floor's cap. The real spawning round raises it, with a player on the floor.
	 */
	@GameTest(maxTicks = 700)
	public void anOccupiedFloorRaisesASquadOfThreeToFiveGhostsThatKeepTogether(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshFloor();
		QuietFloor.hold(floor);
		GhostSquads.pinRiseChance(floor, 1.0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		List<Mob> squad = new ArrayList<>();
		int[] formedAt = {-1};
		int[] most = {0};
		double[] spread = {0.0};
		helper.onEachTick(() -> {
			List<Mob> ghosts = GhostSquads.on(tower, floor);
			most[0] = Math.max(most[0], ghosts.size());
			if (squad.isEmpty()) {
				if (!ghosts.isEmpty()) {
					squad.addAll(ghosts);
					formedAt[0] = (int) helper.getTick();
				}
				return;
			}
			Mob leader = GhostSquads.leaderOf(squad.get(0));
			for (Mob member : squad) {
				spread[0] = Math.max(spread[0], member.distanceTo(leader));
			}
		});
		helper.runAfterDelay(600L, () -> {
			QuietFloor.release(floor);
			Set<String> ids = new java.util.HashSet<>();
			squad.forEach(member -> ids.add(String.valueOf(GhostSquads.squadOf(member))));
			boolean allGhosts = squad.stream().allMatch(member -> member instanceof Ghostly && TowerMobs.stateOf(member) != null && member.isAlive());
			boolean kinds = squad.stream().allMatch(member -> KINDS.contains(member.getType()));
			int capCount = TowerMobs.count(tower, floor);
			TowerTestSupport.result("ghostSquad", "formedAt=" + formedAt[0] + " size=" + squad.size() + " squads=" + ids + " most=" + most[0]
				+ " spread=" + spread[0] + " kinds=" + squad.stream().map(member -> member.getType().toShortString()).toList());
			tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor), Ghostly.class::isInstance).forEach(Mob::discard);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(!squad.isEmpty(), "no squad rose on the occupied floor");
			helper.assertTrue(squad.size() >= GhostSquads.MIN_SIZE && squad.size() <= GhostSquads.MAX_SIZE, "a squad of " + squad.size() + " rose");
			helper.assertTrue(ids.size() == 1 && !ids.contains("null"), "the ghosts belong to squads " + ids);
			helper.assertTrue(most[0] <= GhostSquads.MAX_SIZE, "the floor held " + most[0] + " ghosts at once");
			helper.assertTrue(allGhosts && kinds, "a member of the squad is no living ghost tower mob of an illager kind or a witch");
			helper.assertTrue(capCount == 0, "the ghosts counted " + capCount + " against the floor's cap");
			helper.assertTrue(spread[0] <= GhostSquads.CLOSE * 4.0, "a ghost strayed " + spread[0] + " blocks from its leader");
			helper.succeed();
		});
	}

	/**
	 * Catches a ghost that takes the player for its quarry, hurts them with its blow, bolt, fangs or splash, or ignores the mob it
	 * should hunt. The player stands in the line of fire, beside a zombie that all four kinds hunt, and has struck each of them.
	 */
	@GameTest(maxTicks = 500)
	public void ghostsHuntATowerMobBesideAPlayerWhoStruckThemAndNeverTurnOnThem(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Arena arena = Arena.build(helper);
		ServerPlayer player = TowerTestSupport.onFloor(helper, arena.tower(), arena.floor());
		TowerTestSupport.vulnerable(player);
		player.teleportTo(arena.tower(), arena.middle().x, arena.middle().y, arena.middle().z, Set.of(), 0.0F, 0.0F, true);
		float health = player.getHealth();
		Zombie zombie = arena.zombie(arena.middle().add(5.0, 0.0, 0.0), false);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000.0);
		zombie.setHealth(100_000.0F);
		List<Mob> ghosts = new ArrayList<>();
		for (int i = 0; i < KINDS.size(); i++) {
			Mob ghost = GhostSquads.summon(arena.tower(), LOW, KINDS.get(i), arena.middle().add(-4.0, 0.0, -1.5 + i));
			helper.assertTrue(ghost != null, "no ghost of kind " + KINDS.get(i) + " could be raised");
			ghosts.add(ghost);
			ghost.hurtServer(arena.tower(), player.damageSources().playerAttack(player), 1.0F);
			ghost.setTarget(player);
		}
		int[] targeted = {0};
		float[] lowest = {health};
		helper.onEachTick(() -> {
			lowest[0] = Math.min(lowest[0], player.getHealth());
			player.setInvulnerableTime(0);
			for (Mob ghost : ghosts) {
				targeted[0] += ghost.getTarget() == player ? 1 : 0;
			}
		});
		helper.runAfterDelay(400L, () -> {
			boolean hurt = zombie.getHealth() < zombie.getMaxHealth();
			TowerTestSupport.result("ghostsBesideAPlayer", "targetedPlayer=" + targeted[0] + " lowest=" + lowest[0] + "/" + health + " zombieHurt=" + hurt);
			arena.close();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(hurt, "the ghosts never hurt the tower zombie, so the case proves nothing");
			helper.assertTrue(targeted[0] == 0, "a ghost took the player for its quarry " + targeted[0] + " times");
			helper.assertTrue(lowest[0] >= health, "the player fell to " + lowest[0] + " of " + health + " beside the ghosts");
			helper.succeed();
		});
	}

	/**
	 * Catches a tower mob that leaves a ghost alone. A zombie of the tower must take a ghost that cannot fight back for its
	 * target; the control, a zombie of the world with no tower state, must not.
	 */
	@GameTest(maxTicks = 300)
	public void towerMobsTakeAGhostForTheirQuarry(final GameTestHelper helper) {
		Arena arena = Arena.build(helper);
		Mob ghost = GhostSquads.summon(arena.tower(), LOW, GhostSquads.VINDICATOR, arena.middle());
		helper.assertTrue(ghost != null, "no ghost could be raised");
		ghost.setNoAi(true);
		Zombie towerZombie = arena.zombie(arena.middle().add(6.0, 0.0, 0.0), true);
		Zombie stranger = EntityTypes.ZOMBIE.create(arena.tower(), EntitySpawnReason.COMMAND);
		TowerTestSupport.unrolled(stranger).setPersistenceRequired();
		stranger.snapTo(arena.middle().add(-6.0, 0.0, 0.0));
		arena.tower().addFreshEntity(stranger);
		boolean[] took = {false};
		boolean[] strangerTook = {false};
		helper.onEachTick(() -> {
			took[0] |= towerZombie.getTarget() == ghost;
			strangerTook[0] |= stranger.getTarget() == ghost;
		});
		helper.runAfterDelay(200L, () -> {
			TowerTestSupport.result("towerMobsTakeGhosts", "towerZombieTargetedGhost=" + took[0] + " strangerTargetedGhost=" + strangerTook[0]);
			arena.close();
			helper.assertTrue(took[0], "the tower zombie never took the ghost for its target");
			helper.assertFalse(strangerTook[0], "a zombie with no tower state took the ghost, so the tower's hook proves nothing");
			helper.succeed();
		});
	}

	private void ghostHurtsATowerMob(final GameTestHelper helper, final EntityType<? extends Mob> kind, final EntityType<? extends Mob> victimType) {
		Arena arena = Arena.build(helper);
		Mob victim = victimType.create(arena.tower(), EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(victim, LOW, TowerElement.DARK);
		victim.snapTo(arena.middle().add(6.0, 0.0, 0.0));
		victim.setNoAi(true);
		arena.tower().addFreshEntity(victim);
		Mob ghost = GhostSquads.summon(arena.tower(), LOW, kind, arena.middle());
		helper.assertTrue(ghost != null, "no ghost could be raised");
		// A ghost wanders off a little between its casts: a ghost out of the victim's sight is stood back where the case put it.
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0 && !ghost.hasLineOfSight(victim)) {
				ghost.snapTo(arena.middle());
			}
		});
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(!victim.isAlive() || victim.getHealth() < victim.getMaxHealth(), kind.toShortString() + " never hurt the " + victimType.toShortString());
			if (!done[0]) {
				done[0] = true;
				TowerTestSupport.result("ghostHunts", kind.toShortString() + " tick=" + helper.getTick() + " " + victimType.toShortString()
					+ "=" + victim.getHealth() + "/" + victim.getMaxHealth() + " lastHurtBy=" + victim.getLastHurtByMob());
				arena.close();
			}
		});
	}

	@GameTest(maxTicks = 400)
	public void aGhostVindicatorHurtsATowerMob(final GameTestHelper helper) {
		ghostHurtsATowerMob(helper, GhostSquads.VINDICATOR, EntityTypes.ZOMBIE);
	}

	@GameTest(maxTicks = 400)
	public void aGhostPillagerHurtsATowerMob(final GameTestHelper helper) {
		ghostHurtsATowerMob(helper, GhostSquads.PILLAGER, EntityTypes.ZOMBIE);
	}

	@GameTest(maxTicks = 500)
	public void aGhostEvokerHurtsATowerMob(final GameTestHelper helper) {
		ghostHurtsATowerMob(helper, GhostSquads.EVOKER, EntityTypes.ZOMBIE);
	}

	/** An undead quarry is hurt by a potion of healing, which a ghost witch throws at it. */
	@GameTest(maxTicks = 400)
	public void aGhostWitchHurtsAnUndeadTowerMob(final GameTestHelper helper) {
		ghostHurtsATowerMob(helper, GhostSquads.WITCH, EntityTypes.ZOMBIE);
	}

	/** A living quarry is hurt by a potion of harming. */
	@GameTest(maxTicks = 400)
	public void aGhostWitchHurtsALivingTowerMob(final GameTestHelper helper) {
		ghostHurtsATowerMob(helper, GhostSquads.WITCH, EntityTypes.SPIDER);
	}

	/**
	 * Catches a ghost evoker that summons the vexes of the raids instead of ghost vexes. Its summon is cast for real, against
	 * a zombie in sight; every vex it raises must be a ghost of the tower, and none may be a vexes of the world.
	 */
	@GameTest(maxTicks = 500)
	public void aGhostEvokerSummonsGhostVexes(final GameTestHelper helper) {
		Arena arena = Arena.build(helper);
		Zombie victim = arena.zombie(arena.middle().add(6.0, 0.0, 0.0), false);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000.0);
		victim.setHealth(100_000.0F);
		Mob evoker = GhostSquads.summon(arena.tower(), LOW, GhostSquads.EVOKER, arena.middle());
		helper.assertTrue(evoker != null, "no ghost evoker could be raised");
		AABB box = new AABB(arena.middle().subtract(30.0, 10.0, 30.0), arena.middle().add(30.0, 20.0, 30.0));
		List<Vex> summoned = new ArrayList<>();
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0 && !evoker.hasLineOfSight(victim)) {
				evoker.snapTo(arena.middle());
			}
			for (Vex vex : arena.tower().getEntitiesOfClass(Vex.class, box, v -> v.getOwner() == evoker)) {
				if (!summoned.contains(vex)) {
					summoned.add(vex);
				}
			}
		});
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(summoned.size() >= GhostEvoker.VEXES_PER_CAST, "the ghost evoker summoned " + summoned.size() + " vexes");
			if (!done[0]) {
				done[0] = true;
				boolean allGhosts = summoned.stream().allMatch(vex -> vex.getType() == GhostSquads.VEX && vex instanceof Ghostly && TowerMobs.stateOf(vex) != null);
				TowerTestSupport.result("ghostVexes", "tick=" + helper.getTick() + " summoned=" + summoned.size() + " allGhosts=" + allGhosts);
				arena.close();
				helper.assertTrue(allGhosts, "a vex the ghost evoker summoned is no ghost vex of the tower");
			}
		});
	}

	/**
	 * Catches a ghost that is as fast as its kind: each kind's walking speed, and the vex's flight, which is set by its move
	 * control and not by an attribute, are measured against the kind's own.
	 */
	@GameTest(maxTicks = 300)
	public void aGhostMovesAtThreeQuartersOfItsKindsPace(final GameTestHelper helper) {
		// A vex of the world is gone in a peaceful level, and the ghost vex is not: the control needs a fighting difficulty.
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Arena arena = Arena.build(helper);
		List<String> off = new ArrayList<>();
		check(arena, off, GhostSquads.VINDICATOR, EntityTypes.VINDICATOR);
		check(arena, off, GhostSquads.PILLAGER, EntityTypes.PILLAGER);
		check(arena, off, GhostSquads.EVOKER, EntityTypes.EVOKER);
		check(arena, off, GhostSquads.WITCH, EntityTypes.WITCH);
		Vex ghost = GhostSquads.summon(arena.tower(), LOW, GhostSquads.VEX, arena.middle().add(0.0, 3.0, -9.0));
		Vex plain = EntityTypes.VEX.create(arena.tower(), EntitySpawnReason.COMMAND);
		TowerTestSupport.unrolled(plain).addTag(com.stella.mobs.StellaMobs.NO_BEHAVIOR_TAG);
		// Another test player is far off, and a mob far from every player is despawned unless it must persist.
		plain.setPersistenceRequired();
		plain.snapTo(arena.middle().add(0.0, 3.0, 9.0));
		arena.tower().addFreshEntity(plain);
		// The count starts when both are ticking: a vex in a chunk that is still loading does not move at all.
		Vec3[] from = new Vec3[2];
		double[] moved = {-1.0, -1.0};
		int[] began = {-1};
		helper.onEachTick(() -> {
			if (began[0] < 0) {
				if (ghost.tickCount < 1 || plain.tickCount < 1) {
					return;
				}
				began[0] = (int) helper.getTick();
				from[0] = ghost.position();
				from[1] = plain.position();
			}
			// Both are sent the same far spot every tick, so neither wanders and neither arrives.
			ghost.getMoveControl().setWantedPosition(from[0].x + 60.0, from[0].y, from[0].z, 1.0);
			plain.getMoveControl().setWantedPosition(from[1].x + 60.0, from[1].y, from[1].z, 1.0);
			if (helper.getTick() == began[0] + 40L) {
				moved[0] = ghost.position().distanceTo(from[0]);
				moved[1] = plain.position().distanceTo(from[1]);
			}
		});
		helper.runAfterDelay(250L, () -> {
			double ghostMoved = moved[0];
			double plainMoved = moved[1];
			TowerTestSupport.result("ghostPace", "off=" + off + " vexGhost=" + ghostMoved + " vexPlain=" + plainMoved + " ratio=" + ghostMoved / plainMoved + " began=" + began[0]);
			ghost.discard();
			plain.discard();
			arena.close();
			helper.assertTrue(off.isEmpty(), "a ghost does not walk at 0.75 of its kind's speed: " + off);
			helper.assertTrue(plainMoved > 1.0, "the control vex barely moved: " + plainMoved);
			helper.assertTrue(Math.abs(ghostMoved / plainMoved - 0.75) < 0.04, "the ghost vex flew " + ghostMoved / plainMoved + " of a vex's pace");
			helper.succeed();
		});
	}

	private static void check(final Arena arena, final List<String> off, final EntityType<? extends Mob> ghost, final EntityType<? extends Mob> kind) {
		Mob made = ghost.create(arena.tower(), EntitySpawnReason.COMMAND);
		double want = DefaultAttributes.getSupplier(kind).getBaseValue(Attributes.MOVEMENT_SPEED) * 0.75;
		double got = made.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		if (Math.abs(want - got) > 1.0E-9) {
			off.add(ghost.toShortString() + " " + got + " against " + want);
		}
		made.discard();
	}

	/**
	 * Catches a ghost that leaves loot, gear or experience when a player slays it. A witch of the mobs mod drops its potions on
	 * death, so the witch is the sharp case. The control, a zombie of the tower slain the same way, must leave experience.
	 */
	@GameTest(maxTicks = 100)
	public void aSlainGhostDropsNothing(final GameTestHelper helper) {
		Arena arena = Arena.build(helper);
		ServerPlayer player = TowerTestSupport.onFloor(helper, arena.tower(), arena.floor());
		TowerTestSupport.vulnerable(player);
		List<Mob> ghosts = new ArrayList<>();
		List<EntityType<? extends Mob>> types = new ArrayList<>(KINDS);
		types.add(GhostSquads.VEX);
		for (int i = 0; i < types.size(); i++) {
			Mob ghost = GhostSquads.summon(arena.tower(), LOW, types.get(i), arena.middle().add(-8.0 + 4.0 * i, 0.0, -6.0));
			helper.assertTrue(ghost != null, "no ghost of kind " + types.get(i) + " could be raised");
			ghosts.add(ghost);
		}
		Zombie control = arena.zombie(arena.middle().add(0.0, 0.0, 6.0), false);
		AABB box = new AABB(arena.middle().subtract(14.0, 2.0, 14.0), arena.middle().add(14.0, 6.0, 14.0));
		// A chest under the platform spills its loot when the platform is laid: only items that come after the kills count.
		Set<java.util.UUID> before = new java.util.HashSet<>();
		arena.tower().getEntitiesOfClass(ItemEntity.class, box).forEach(item -> before.add(item.getUUID()));
		for (Mob mob : ghosts) {
			mob.setLastHurtByPlayer(player, 100);
			mob.hurtServer(arena.tower(), player.damageSources().playerAttack(player), 1_000_000.0F);
		}
		control.setLastHurtByPlayer(player, 100);
		control.hurtServer(arena.tower(), player.damageSources().playerAttack(player), 1_000_000.0F);
		helper.runAfterDelay(10L, () -> {
			boolean allDead = ghosts.stream().noneMatch(Mob::isAlive);
			// The control zombie stands apart, so what it drops is told from a ghost's by where it lies.
			long items = arena.tower().getEntitiesOfClass(ItemEntity.class, box, item -> !before.contains(item.getUUID()) && item.position().distanceTo(control.position()) >= 3.0).size();
			List<ExperienceOrb> orbs = arena.tower().getEntitiesOfClass(ExperienceOrb.class, box);
			long nearControl = orbs.stream().filter(orb -> orb.position().distanceTo(control.position()) < 3.0).count();
			long nearGhosts = orbs.size() - nearControl;
			TowerTestSupport.result("ghostDrops", "allDead=" + allDead + " items=" + items + " ghostOrbs=" + nearGhosts + " controlOrbs=" + nearControl);
			arena.close();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(allDead, "a ghost survived the blow");
			helper.assertTrue(nearControl > 0, "the control zombie dropped no experience, so the case proves nothing");
			helper.assertTrue(items == 0 && nearGhosts == 0, "the ghosts left " + items + " items and " + nearGhosts + " orbs: " + arena.tower().getEntitiesOfClass(ItemEntity.class, box, item -> !before.contains(item.getUUID()) && item.position().distanceTo(control.position()) >= 3.0).stream().map(item -> item.getItem() + "@" + item.position()).toList());
			helper.succeed();
		});
	}

	/** How a ghost looks is the client's to draw: the vanilla kind, seen through, wreathed in soul fire. */
	@GameTest(required = false)
	public void ghostIllagersAndTheMerchantLookSeeThroughRequiresManualTest(final GameTestHelper helper) {
		helper.fail("the ghosts' look is drawn by the client: check in play that the ghost illagers, witches, vexes and the merchant are see-through and wreathed in flame and soul fire");
	}
}
