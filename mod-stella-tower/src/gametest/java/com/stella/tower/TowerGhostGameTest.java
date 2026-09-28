package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The tower's ghosts: on the players' side, generous to them, and a curse to whoever kills one. */
public class TowerGhostGameTest {
	/** A low floor for what a ghost's spell deals, so a zombie outlives it. */
	private static final int LOW = 5;
	/** The rune case's own ghost floor, so its pinned chance touches no other case's ghost. */
	private static final int RUNE_FLOOR = 6;

	private static ServerPlayer climber(final GameTestHelper helper, final ServerLevel tower, final int floor) {
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		return player;
	}

	private static TowerGhost ghostBeside(final GameTestHelper helper, final ServerLevel tower, final int floor, final ServerPlayer player) {
		// Of a low floor, so a light blow shows on it; above the player, clear of the maze's walls.
		TowerGhost ghost = TowerGhosts.summon(tower, LOW, player.position().add(0.0, 2.5, 0.0));
		helper.assertTrue(ghost != null, "no ghost could be raised");
		return ghost;
	}

	/**
	 * An ordinary floor: no boss-floor buff, so a player's effects are the ghost's alone, and no guardians.
	 * Its own mobs are held off too: at the test floors' height one blow kills a ghost, and a ghost slain
	 * mid-case mends and strengthens no one. The pin is on this run's own floor, which no other case uses.
	 */
	private static int floor(final ServerLevel tower) {
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerMobs.pinFloorCap(floor, 0);
		TowerFloors.ensure(tower, floor);
		return floor;
	}

	@GameTest
	public void aGhostIsNamedAGhost(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		String name = ghost.getDisplayName().getString();
		TowerTestSupport.result("ghostName", "name=" + name);
		ghost.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(name.startsWith(TowerGhosts.PREFIX + " "), "a ghost is named " + name);
		helper.succeed();
	}

	/** Struck by a player, a ghost still never turns on one. */
	@GameTest(maxTicks = 100)
	public void aGhostNeverTurnsOnAPlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		ghost.hurtServer(tower, player.damageSources().playerAttack(player), 1.0F);
		boolean struck = ghost.getHealth() < ghost.getMaxHealth();
		// As any goal of its own would: the ghost is told to take the player on.
		ghost.setTarget(player);
		float health = player.getHealth();
		helper.runAfterDelay(80L, () -> {
			boolean turned = ghost.getTarget() == player;
			float lost = health - player.getHealth();
			TowerTestSupport.result("ghostLoyal", "turned=" + turned + " lost=" + lost + " alive=" + ghost.isAlive());
			ghost.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(struck, "the player never struck the ghost, so the case proves nothing");
			helper.assertFalse(turned || lost > 0.0F, "the ghost turned on the player: target=" + turned + " lost=" + lost);
			helper.succeed();
		});
	}

	/** A ghost casts at what hunts the players: a tower zombie near it takes its spells. */
	@GameTest(maxTicks = 200)
	public void aGhostCastsAtAnEnemy(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		player.setGameMode(GameType.CREATIVE);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, LOW, TowerElement.DARK);
		// Within the arrival's own maze cell, in the ghost's sight.
		zombie.snapTo(player.position().add(1.0, 0.0, 0.0));
		tower.addFreshEntity(zombie);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth() && zombie.getLastHurtByMob() == ghost, "the ghost never cast at the zombie");
			if (!done[0]) {
				done[0] = true;
				TowerTestSupport.result("ghostCasts", "tick=" + helper.getTick() + " zombie=" + zombie.getHealth() + "/" + zombie.getMaxHealth());
				zombie.discard();
				ghost.discard();
				TowerTestSupport.leave(helper, player);
			}
		});
	}

	@GameTest(maxTicks = 200)
	public void aGhostMendsAHurtPlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		player.setHealth(10.0F);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(player.getHealth() > 10.0F, "the ghost never mended the hurt player");
			if (!done[0]) {
				done[0] = true;
				TowerTestSupport.result("ghostMends", "tick=" + helper.getTick() + " health=" + player.getHealth());
				ghost.discard();
				TowerTestSupport.leave(helper, player);
			}
		});
	}

	@GameTest(maxTicks = 300)
	public void aGhostStrengthensAPlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			// The ghost's own gift, not a floor buff: a floor buff is ambient.
			List<String> held = TowerGhosts.BUFFS.stream().filter(effect -> player.hasEffect(effect) && !player.getEffect(effect).isAmbient())
				.map(effect -> effect.getRegisteredName()).toList();
			helper.assertTrue(!held.isEmpty(), "the ghost never strengthened the player");
			if (!done[0]) {
				done[0] = true;
				TowerTestSupport.result("ghostBuffs", "tick=" + helper.getTick() + " held=" + held);
				ghost.discard();
				TowerTestSupport.leave(helper, player);
			}
		});
	}

	@GameTest
	public void killingAGhostBlindsTheKiller(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		ghost.hurtServer(tower, player.damageSources().playerAttack(player), 1.0E30F);
		MobEffectInstance blind = player.getEffect(MobEffects.BLINDNESS);
		TowerTestSupport.result("ghostCurse", "dead=" + !ghost.isAlive() + " blind=" + blind);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!ghost.isAlive(), "the ghost did not die, so the case proves nothing");
		helper.assertTrue(blind != null && blind.getDuration() >= TowerGhosts.CURSE_TICKS - 1, "the ghost's killer is blind for " + blind);
		helper.succeed();
	}

	/** A slain ghost may leave a ghost rune; the chance is pinned to certain for the case. */
	@GameTest
	public void aSlainGhostMayLeaveARune(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		TowerGhost ghost = TowerGhosts.summon(tower, RUNE_FLOOR, player.position().add(0.0, 2.5, 0.0));
		helper.assertTrue(ghost != null, "no ghost could be raised");
		Vec3 at = ghost.position();
		List<ItemEntity> runes;
		try {
			TowerGhosts.pinRuneChance(RUNE_FLOOR, 1.0);
			ghost.hurtServer(tower, player.damageSources().playerAttack(player), 1.0E30F);
			runes = tower.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0), item -> item.getItem().is(TowerItems.GHOST_RUNE));
		} finally {
			TowerGhosts.pinRuneChance(RUNE_FLOOR, null);
		}
		TowerTestSupport.result("ghostRune", "dead=" + !ghost.isAlive() + " runes=" + runes.size() + " chance=" + TowerGhosts.RUNE_CHANCE);
		runes.forEach(ItemEntity::discard);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!ghost.isAlive(), "the ghost did not die, so the case proves nothing");
		helper.assertTrue(runes.size() == 1, "a slain ghost left " + runes.size() + " runes at a certain chance");
		helper.succeed();
	}

	/** An occupied floor comes to keep its share of ghosts, and they stand outside its cap. */
	@GameTest(maxTicks = 300)
	public void ghostsHauntAnOccupiedFloorOutsideItsCap(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerGhosts.pinRiseChance(floor, 1.0);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.setGameMode(GameType.CREATIVE);
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], TowerGhosts.on(tower, floor).size()));
		helper.runAfterDelay(280L, () -> {
			TowerGhosts.pinRiseChance(floor, null);
			List<TowerGhost> ghosts = TowerGhosts.on(tower, floor);
			int capped = TowerMobs.count(tower, floor);
			// The floor's own mobs, counted apart from the spawner's count: tower mobs that are no ghost.
			int own = tower.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, TowerMobs.room(floor), mob -> mob.isAlive()
				&& TowerMobs.stateOf(mob) != null && TowerMobs.stateOf(mob).floor() == floor && TowerMobs.stateOf(mob).rank() == TowerRank.MOB
				&& !(mob instanceof TowerGhost)).size();
			int stateless = (int) ghosts.stream().filter(ghost -> TowerMobs.stateOf(ghost) == null).count();
			TowerTestSupport.result("ghostHaunt", "ghosts=" + ghosts.size() + " most=" + most[0] + " capped=" + capped + " own=" + own + " stateless=" + stateless);
			ghosts.forEach(TowerGhost::discard);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(most[0] == TowerGhosts.GHOSTS_PER_FLOOR, "the floor kept " + most[0] + " ghosts at most, not " + TowerGhosts.GHOSTS_PER_FLOOR);
			helper.assertTrue(capped == own && stateless == 0, "the cap counts " + capped + " of " + own + " floor mobs, ghosts without a floor=" + stateless);
			helper.succeed();
		});
	}

	/** A boss floor's buffs are for the players' foes: a ghost carries none, so no thorns strike the player back. */
	@GameTest
	public void aGhostCarriesNoBossFloorBuff(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = climber(helper, tower, floor);
		TowerGhost ghost = TowerGhosts.summon(tower, floor, player.position().add(0.0, 2.5, 0.0));
		helper.assertTrue(ghost != null, "no ghost could be raised");
		List<TowerBuffs.MobBuff> buffs;
		try {
			TowerBuffs.pinMobBuffs(floor, List.of(TowerBuffs.MobBuff.THORNS, TowerBuffs.MobBuff.EXTRA_LIFE));
			buffs = TowerBuffs.mobBuffsOf(ghost);
		} finally {
			TowerBuffs.pinMobBuffs(floor, null);
		}
		TowerTestSupport.result("ghostBuffsNone", "buffs=" + buffs);
		ghost.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(buffs.isEmpty(), "a ghost on a boss floor carries " + buffs);
		helper.succeed();
	}

	/** A shot fired past a ghost does not hit it, and one that kills it anyway lays no curse: only a blow struck in hand does. */
	@GameTest
	public void onlyAHandStruckKillCursesTheKiller(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		boolean shootable = ghost.canBeHitByProjectile();
		net.minecraft.world.entity.projectile.arrow.Arrow arrow = EntityTypes.ARROW.create(tower, EntitySpawnReason.COMMAND);
		arrow.setOwner(player);
		arrow.snapTo(player.position());
		ghost.hurtServer(tower, tower.damageSources().arrow(arrow, player), 1.0E30F);
		boolean blind = player.hasEffect(MobEffects.BLINDNESS);
		TowerTestSupport.result("ghostArrow", "shootable=" + shootable + " dead=" + !ghost.isAlive() + " blind=" + blind);
		arrow.discard();
		ghost.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(!ghost.isAlive(), "the arrow did not kill the ghost, so the case proves nothing");
		helper.assertFalse(shootable || blind, "a ghost can be shot=" + shootable + ", and a shot kill blinded the player=" + blind);
		helper.succeed();
	}

	/** A ghost's kill is the players': the nearest player is credited, so the floor's pay is not lost. */
	@GameTest(maxTicks = 200)
	public void aGhostsBoltCreditsTheNearestPlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floor(tower);
		ServerPlayer player = climber(helper, tower, floor);
		player.setGameMode(GameType.CREATIVE);
		TowerGhost ghost = ghostBeside(helper, tower, floor, player);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, LOW, TowerElement.DARK);
		zombie.snapTo(player.position().add(1.0, 0.0, 0.0));
		tower.addFreshEntity(zombie);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getLastHurtByMob() == ghost, "the ghost never cast at the zombie, so the case proves nothing");
			helper.assertTrue(zombie.getLastHurtByPlayer() == player, "the ghost's bolt credited " + zombie.getLastHurtByPlayer() + ", not the nearest player");
			if (!done[0]) {
				done[0] = true;
				TowerTestSupport.result("ghostCredit", "tick=" + helper.getTick());
				zombie.discard();
				ghost.discard();
				TowerTestSupport.leave(helper, player);
			}
		});
	}

	/**
	 * A monster beyond its bolt's reach does not draw a ghost away from the players it keeps. In a boss
	 * floor's hall, where the ghost sees it plainly.
	 */
	@GameTest(maxTicks = 220)
	public void aFarMonsterDoesNotDrawAGhostAway(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		// The far zombie is the only foe: the floor's guardians rise as pigs, and its own mobs are held off.
		TowerElites.pinGuardianKind(floor, EntityTypes.PIG);
		TowerMobs.pinFloorCap(floor, 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.setGameMode(GameType.CREATIVE);
		Vec3 middle = Vec3.atBottomCenterOf(TowerLayout.origin(floor).offset(TowerLayout.SIZE / 2 - 3, 0, TowerLayout.SIZE / 2 + 3));
		player.teleportTo(tower, middle.x, middle.y, middle.z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerGhost ghost = TowerGhosts.summon(tower, LOW, middle.add(0.0, 2.5, 0.0));
		helper.assertTrue(ghost != null, "no ghost could be raised");
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		zombie.setNoAi(true);
		// Within the hall and the ghost's sense of the room, and beyond its bolt.
		zombie.snapTo(middle.add(13.0, 0.0, -13.0));
		tower.addFreshEntity(zombie);
		helper.runAfterDelay(200L, () -> {
			boolean drawn = ghost.getTarget() == zombie;
			double fromPlayer = ghost.distanceTo(player);
			double zombieFrom = zombie.distanceTo(ghost);
			TowerTestSupport.result("ghostFarMonster", "drawn=" + drawn + " fromPlayer=" + (int) fromPlayer + " zombieFrom=" + (int) zombieFrom);
			TowerElites.pinGuardianKind(floor, null);
			TowerMobs.pinFloorCap(floor, null);
			zombie.discard();
			ghost.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertFalse(drawn, "the ghost took a monster " + (int) zombieFrom + " blocks off, beyond its bolt, for its quarry");
			helper.assertTrue(fromPlayer <= TowerGhosts.TEND_RANGE, "the ghost drifted " + (int) fromPlayer + " blocks from the player");
			helper.succeed();
		});
	}

	/** How a ghost looks is the client's to draw: a blaze's form, grey and see-through. */
	@GameTest(required = false)
	public void aGhostLooksLikeAGreyTranslucentBlazeRequiresManualTest(final GameTestHelper helper) {
		helper.fail("a ghost's look is drawn by the client: check in play that it is a grey, see-through blaze");
	}
}
