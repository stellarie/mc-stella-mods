package com.stella.tower;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Each hundred floors: ten without a gimmick, then ten of each theme's traps, then ten of them all. */
public class TowerTrapGameTest {
	/** A low floor for what a trap deals, so the numbers stay small and exact. */
	private static final int LOW = 5;
	/** A deeper floor, where a trap's growth with the floor shows. */
	private static final int DEEP = 250;

	private static int floorOf(final TrapTheme theme, final boolean hall) {
		return TowerTestSupport.freshFloorWhere(f -> TrapTheme.of(f) == theme && (TowerMaze.hall(f) > 0) == hall, 110);
	}

	/** The traps built into a floor's ground, by offset. */
	private static Map<BlockPos, TrapTheme> traps(final ServerLevel tower, final int floor) {
		Map<BlockPos, TrapTheme> found = new java.util.HashMap<>();
		BlockPos o = TowerLayout.origin(floor);
		for (int x = 0; x < TowerLayout.SIZE; x++) {
			for (int z = 0; z < TowerLayout.SIZE; z++) {
				Block block = tower.getBlockState(o.offset(x, -1, z)).getBlock();
				if (block instanceof TowerTrapBlock trap) {
					found.put(new BlockPos(x, 0, z), trap.theme());
				}
			}
		}
		return found;
	}

	@GameTest
	public void themesTurnEveryTenFloorsAndStartOverEachHundred(final GameTestHelper helper) {
		int[] floors = {1, 10, 11, 20, 21, 35, 45, 55, 65, 75, 85, 91, 100, 101, 111, 1_000, 1_011};
		TrapTheme[] want = {TrapTheme.NONE, TrapTheme.NONE, TrapTheme.DESERT, TrapTheme.DESERT, TrapTheme.INFERNO, TrapTheme.VOID,
			TrapTheme.FROST, TrapTheme.BOG, TrapTheme.STORM, TrapTheme.SHADOW, TrapTheme.SPIKES, TrapTheme.CHAOS, TrapTheme.CHAOS,
			TrapTheme.NONE, TrapTheme.DESERT, TrapTheme.CHAOS, TrapTheme.DESERT};
		List<String> wrong = new java.util.ArrayList<>();
		for (int i = 0; i < floors.length; i++) {
			if (TrapTheme.of(floors[i]) != want[i]) {
				wrong.add(floors[i] + "=" + TrapTheme.of(floors[i]) + "/" + want[i]);
			}
		}
		TowerTestSupport.result("trapThemes", "wrong=" + wrong);
		helper.assertTrue(wrong.isEmpty(), "floors with the wrong theme: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void aThemedFloorLaysItsTrapsAlongTheMaze(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floorOf(TrapTheme.DESERT, false);
		TowerFloors.ensure(tower, floor);
		Map<BlockPos, TrapTheme> traps = traps(tower, floor);
		long strangers = traps.values().stream().filter(theme -> theme != TrapTheme.DESERT).count();
		TowerPlan plan = TowerPlan.at(tower, floor);
		// Counted in maze cells, as the rule is.
		int arrivalCellX = plan.arrivalCell()[0];
		int arrivalCellZ = plan.arrivalCell()[1];
		long nearArrival = traps.keySet().stream().filter(pos -> Math.max(Math.abs((pos.getX() - 1) / TowerMaze.PITCH - arrivalCellX),
			Math.abs((pos.getZ() - 1) / TowerMaze.PITCH - arrivalCellZ)) <= TowerTraps.ARRIVAL_CLEAR_CELLS).count();
		int[] portalCell = plan.portalCell();
		long inNook = traps.keySet().stream().filter(pos -> (pos.getX() - 1) / TowerMaze.PITCH == portalCell[0]
			&& Math.abs((pos.getZ() - 1) / TowerMaze.PITCH - portalCell[1]) <= 1).count();
		boolean[][] walls = plan.walls();
		long underWalls = traps.keySet().stream().filter(pos -> walls[pos.getX()][pos.getZ()]).count();
		TowerTestSupport.result("trapsLaid", "traps=" + traps.size() + " strangers=" + strangers + " nearArrival=" + nearArrival
			+ " inNook=" + inNook + " underWalls=" + underWalls);
		helper.assertTrue(traps.size() == TowerTraps.TRAPS_PER_FLOOR, "a desert floor holds " + traps.size() + " traps, not " + TowerTraps.TRAPS_PER_FLOOR);
		helper.assertTrue(strangers == 0, strangers + " traps of a desert floor are not desert traps");
		helper.assertTrue(nearArrival == 0 && inNook == 0 && underWalls == 0,
			"traps by the arrival=" + nearArrival + " in the portal's nook=" + inNook + " under walls=" + underWalls);
		helper.succeed();
	}

	/** Over a thousand floors' layouts, no trap lies by the arrival, in a hall, in the portal's nook, or under a wall. */
	@GameTest
	public void noFloorLaysATrapWhereItMustNot(final GameTestHelper helper) {
		long seed = 4242L;
		int laid = 0;
		List<String> wrong = new java.util.ArrayList<>();
		for (int floor = 1; floor <= 1_000; floor++) {
			TowerPlan plan = TowerPlan.of(seed, floor);
			boolean[][] walls = plan.walls();
			int arrivalCellX = plan.arrivalCell()[0];
			int arrivalCellZ = plan.arrivalCell()[1];
			int[] portalCell = plan.portalCell();
			int side = TowerMaze.hall(floor);
			int low = TowerLayout.SIZE / 2 - side / 2;
			for (TowerTraps.Trap trap : TowerTraps.layout(seed, floor)) {
				laid++;
				int cx = (trap.x() - 1) / TowerMaze.PITCH;
				int cz = (trap.z() - 1) / TowerMaze.PITCH;
				boolean byArrival = Math.max(Math.abs(cx - arrivalCellX), Math.abs(cz - arrivalCellZ)) <= TowerTraps.ARRIVAL_CLEAR_CELLS;
				boolean inHall = side > 0 && trap.x() >= low && trap.x() < low + side && trap.z() >= low && trap.z() < low + side;
				boolean inNook = side == 0 && cx == portalCell[0] && Math.abs(cz - portalCell[1]) <= 1;
				if (byArrival || inHall || inNook || walls[trap.x()][trap.z()]) {
					wrong.add(floor + ":" + trap);
				}
			}
		}
		TowerTestSupport.result("trapPlaces", "laid=" + laid + " wrong=" + wrong.size() + (wrong.isEmpty() ? "" : " first=" + wrong.get(0)));
		helper.assertTrue(laid > 0, "no floor laid a trap, so the case proves nothing");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " traps lie where they must not, first " + (wrong.isEmpty() ? "" : wrong.get(0)));
		helper.succeed();
	}

	@GameTest
	public void aHallIsKeptClearOfTraps(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floorOf(TrapTheme.FROST, true);
		TowerFloors.ensure(tower, floor);
		Map<BlockPos, TrapTheme> traps = traps(tower, floor);
		int side = TowerMaze.hall(floor);
		int low = TowerLayout.SIZE / 2 - side / 2;
		long inHall = traps.keySet().stream().filter(pos -> pos.getX() >= low && pos.getX() < low + side && pos.getZ() >= low && pos.getZ() < low + side).count();
		TowerTestSupport.result("hallClear", "traps=" + traps.size() + " inHall=" + inHall + " side=" + side);
		helper.assertTrue(!traps.isEmpty(), "the floor holds no traps, so the case proves nothing");
		helper.assertTrue(inHall == 0, inHall + " traps lie in the " + side + " block hall");
		helper.succeed();
	}

	@GameTest
	public void theFirstTenFloorsOfEachHundredHaveNoTraps(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floorOf(TrapTheme.NONE, false);
		TowerFloors.ensure(tower, floor);
		int traps = traps(tower, floor).size();
		TowerTestSupport.result("noGimmick", "floor=" + floor + " traps=" + traps);
		helper.assertTrue(traps == 0, "floor " + floor + " without a gimmick holds " + traps + " traps");
		helper.succeed();
	}

	@GameTest
	public void theLastTenFloorsOfEachHundredMixEveryKind(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = floorOf(TrapTheme.CHAOS, false);
		TowerFloors.ensure(tower, floor);
		Set<TrapTheme> kinds = new HashSet<>(traps(tower, floor).values());
		TowerTestSupport.result("chaosMix", "kinds=" + kinds);
		helper.assertTrue(kinds.size() > 1 && !kinds.contains(TrapTheme.NONE) && !kinds.contains(TrapTheme.CHAOS), "a mixed floor's traps are " + kinds);
		helper.succeed();
	}

	/** A player standing on a built floor, and the ground under them. */
	private static ServerPlayer standing(final GameTestHelper helper, final ServerLevel tower, final int floor) {
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		TowerTestSupport.vulnerable(player);
		return player;
	}

	private static BlockPos under(final ServerPlayer player) {
		return player.blockPosition().below();
	}

	private static TowerTraps.Sprung spring(final GameTestHelper helper, final ServerLevel tower, final ServerPlayer player, final TrapTheme theme) {
		TowerTraps.Sprung sprung = TowerTraps.spring(tower, under(player), player, theme, LOW);
		helper.assertTrue(sprung != null, "the " + theme + " trap did not spring");
		return sprung;
	}

	private static int plainFloor(final ServerLevel tower) {
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		return floor;
	}

	@GameTest
	public void aDesertTrapSinksThePlayerAndDropsArrows(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		float before = player.getHealth();
		TowerTraps.Sprung sprung = spring(helper, tower, player, TrapTheme.DESERT);
		long arrows = sprung.spawned().stream().filter(entity -> entity instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.DISALLOWED).count();
		boolean slow = player.hasEffect(MobEffects.SLOWNESS);
		float lost = before - player.getHealth();
		// The whole volley counts: three arrows at once would meet a hurt player's grace after the first.
		double want = TowerCurve.damage(TowerTraps.ARROW_BASE * TowerTraps.DESERT_ARROWS, LOW);
		TowerTestSupport.result("desertTrap", "slow=" + slow + " arrows=" + arrows + "/" + TowerTraps.DESERT_ARROWS + " lost=" + lost + " want=" + want);
		sprung.spawned().forEach(Entity::discard);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(slow && arrows == TowerTraps.DESERT_ARROWS, "the desert trap: slow=" + slow + " arrows=" + arrows);
		helper.assertTrue(Math.abs(lost - want) < 0.01, "the desert volley dealt " + lost + ", not the floor's " + want);
		helper.succeed();
	}

	@GameTest
	public void anInfernoTrapSetsThePlayerAlight(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		float before = player.getHealth();
		spring(helper, tower, player, TrapTheme.INFERNO);
		boolean burning = player.isOnFire();
		float lost = before - player.getHealth();
		TowerTestSupport.result("infernoTrap", "burning=" + burning + " lost=" + lost);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(burning && lost > 0.0F, "the inferno trap: burning=" + burning + " lost=" + lost);
		helper.succeed();
	}

	@GameTest
	public void aVoidTrapThrowsThePlayerElsewhereInTheMaze(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = plainFloor(tower);
		ServerPlayer player = standing(helper, tower, floor);
		Vec3 from = player.position();
		spring(helper, tower, player, TrapTheme.VOID);
		double moved = player.position().distanceTo(from);
		BlockPos feet = player.blockPosition();
		boolean free = tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty()
			&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty();
		int landed = TowerLayout.floorAt(feet);
		TowerTestSupport.result("voidTrap", "moved=" + (int) moved + " floor=" + landed + "/" + floor + " free=" + free);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(moved >= TowerTraps.VOID_MIN_DISTANCE && landed == floor && free,
			"the void trap moved the player " + (int) moved + " to floor " + landed + " free=" + free);
		helper.succeed();
	}

	@GameTest
	public void aFrostTrapFreezesThePlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		float before = player.getHealth();
		spring(helper, tower, player, TrapTheme.FROST);
		boolean frozen = player.isFullyFrozen();
		boolean slow = player.hasEffect(MobEffects.SLOWNESS);
		float lost = before - player.getHealth();
		double want = TowerCurve.damage(TowerTraps.FROST_BASE, LOW);
		TowerTestSupport.result("frostTrap", "frozen=" + frozen + " slow=" + slow + " ticksFrozen=" + player.getTicksFrozen() + " lost=" + lost + " want=" + want);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(frozen && slow, "the frost trap: frozen=" + frozen + " slow=" + slow);
		helper.assertTrue(Math.abs(lost - want) < 0.01, "the frost trap bit for " + lost + ", not the floor's " + want);
		helper.succeed();
	}

	@GameTest
	public void aBogTrapPoisonsThePlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		TowerTraps.spring(tower, under(player), player, TrapTheme.BOG, DEEP);
		net.minecraft.world.effect.MobEffectInstance poison = player.getEffect(MobEffects.POISON);
		int want = Math.min(TowerTraps.BOG_MAX_AMPLIFIER, TowerBuffs.level(DEEP));
		TowerTestSupport.result("bogTrap", "poison=" + poison + " want=" + want);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(poison != null && poison.getAmplifier() == want, "the bog trap of floor " + DEEP + " poisoned at " + poison + ", not level " + want);
		helper.succeed();
	}

	@GameTest
	public void aStormTrapStrikesThePlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		float before = player.getHealth();
		TowerTraps.Sprung sprung = spring(helper, tower, player, TrapTheme.STORM);
		long bolts = sprung.spawned().stream().filter(entity -> entity instanceof LightningBolt).count();
		float lost = before - player.getHealth();
		TowerTestSupport.result("stormTrap", "bolts=" + bolts + " lost=" + lost);
		sprung.spawned().forEach(Entity::discard);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(bolts == 1 && lost > 0.0F, "the storm trap: bolts=" + bolts + " lost=" + lost);
		helper.succeed();
	}

	@GameTest
	public void aShadowTrapBlindsThePlayerAndCallsAnAmbush(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = plainFloor(tower);
		ServerPlayer player = standing(helper, tower, floor);
		TowerTraps.Sprung sprung = spring(helper, tower, player, TrapTheme.SHADOW);
		boolean dark = player.hasEffect(MobEffects.DARKNESS);
		long ambush = sprung.spawned().stream().filter(entity -> entity instanceof Mob mob && TowerMobs.stateOf(mob) != null
			&& TowerMobs.stateOf(mob).floor() == LOW && TowerMobs.stateOf(mob).rank() == TowerRank.MOB).count();
		TowerTestSupport.result("shadowTrap", "dark=" + dark + " ambush=" + ambush + "/" + TowerTraps.AMBUSH);
		sprung.spawned().forEach(Entity::discard);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(dark && ambush == TowerTraps.AMBUSH, "the shadow trap: dark=" + dark + " ambush=" + ambush);
		helper.succeed();
	}

	/** Spikes deal what a tower mob of the floor would add to the same base: they grow with the floor. */
	@GameTest
	public void aSpikeTrapWoundsAsTheFloorDoes(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		float before = player.getHealth();
		spring(helper, tower, player, TrapTheme.SPIKES);
		float lost = before - player.getHealth();
		double want = TowerCurve.damage(TowerTraps.SPIKE_BASE, LOW);
		TowerTestSupport.result("spikeTrap", "lost=" + lost + " want=" + want);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(lost - want) < 0.01, "the spike trap dealt " + lost + ", not the floor's " + want);
		helper.succeed();
	}

	/** Armor turns spikes as it turns a mob's blow: no trap wound goes past it. */
	@GameTest
	public void armorTurnsSpikes(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		// A netherite chestplate's armor; gear reaches the attribute only on a tick a test player never runs.
		player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(8.0);
		float before = player.getHealth();
		spring(helper, tower, player, TrapTheme.SPIKES);
		float lost = before - player.getHealth();
		double bare = TowerCurve.damage(TowerTraps.SPIKE_BASE, LOW);
		TowerTestSupport.result("spikeArmor", "lost=" + lost + " bare=" + bare + " armor=" + player.getArmorValue());
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(player.getArmorValue() > 0, "the chestplate gave no armor, so the case proves nothing");
		helper.assertTrue(lost > 0.0F && lost < bare - 0.01, "an armored player lost " + lost + " to spikes, as much as bare " + bare);
		helper.succeed();
	}

	/** The void never throws a player onto the portal, which would carry them up, or onto another trap. */
	@GameTest
	public void theVoidLandsAPlayerOnNeitherPortalNorTrap(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = plainFloor(tower);
		BlockPos o = TowerLayout.origin(floor);
		BlockPos plain = BlockPos.containing(TowerLayout.arrival(tower, floor));
		BlockPos onTrap = plain.offset(1, 0, 0);
		tower.setBlockAndUpdate(onTrap.below(), TowerBlocks.TRAPS.get(TrapTheme.BOG).defaultBlockState());
		BlockPos inPortal = TowerLayout.portalInside(tower, floor).get(0);
		tower.setBlockAndUpdate(inPortal, TowerBlocks.TOWER_PORTAL.defaultBlockState());
		boolean plainSafe = TowerTraps.landsSafely(tower, plain);
		boolean trapSafe = TowerTraps.landsSafely(tower, onTrap);
		boolean portalSafe = TowerTraps.landsSafely(tower, inPortal);
		TowerTestSupport.result("voidLanding", "plain=" + plainSafe + " trap=" + trapSafe + " portal=" + portalSafe);
		helper.assertTrue(plainSafe, "the void refuses plain ground, so the case proves nothing");
		helper.assertFalse(trapSafe || portalSafe, "the void may land a player on a trap=" + trapSafe + " or the portal=" + portalSafe);
		helper.succeed();
	}

	/** A player riding a mount springs a trap the mount steps on. */
	@GameTest
	public void aRiderSpringsATrapUnderTheirMount(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		BlockPos ground = under(player);
		tower.setBlockAndUpdate(ground, TowerBlocks.TRAPS.get(TrapTheme.BOG).defaultBlockState());
		net.minecraft.world.entity.animal.pig.Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(tower, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		pig.snapTo(player.position());
		tower.addFreshEntity(pig);
		boolean riding = player.startRiding(pig, true, true);
		Vec3 from = pig.position();
		pig.move(MoverType.SELF, new Vec3(0.05, -0.1, 0.0));
		pig.applyEffectsFromBlocks(from, pig.position());
		boolean poisoned = player.hasEffect(MobEffects.POISON);
		TowerTestSupport.result("trapRider", "riding=" + riding + " pigOnGround=" + pig.onGround() + " poisoned=" + poisoned);
		player.stopRiding();
		pig.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(riding && pig.onGround(), "the player never rode the pig onto the ground, so the case proves nothing");
		helper.assertTrue(poisoned, "a rider's mount stepped on a bog trap and nothing sprang");
		helper.succeed();
	}

	/** A mob on a trap springs nothing, and leaves it armed for the next player. */
	@GameTest
	public void aMobWalksOverATrapUntouched(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		BlockPos ground = under(player);
		tower.setBlockAndUpdate(ground, TowerBlocks.TRAPS.get(TrapTheme.BOG).defaultBlockState());
		net.minecraft.world.entity.monster.zombie.Zombie zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(tower, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		zombie.snapTo(player.position());
		tower.addFreshEntity(zombie);
		Vec3 from = zombie.position();
		zombie.move(MoverType.SELF, new Vec3(0.05, -0.1, 0.0));
		zombie.applyEffectsFromBlocks(from, zombie.position());
		boolean zombieOnGround = zombie.onGround();
		boolean zombiePoisoned = zombie.hasEffect(MobEffects.POISON);
		boolean stillArmed = TowerTraps.spring(tower, ground, player, TrapTheme.BOG, LOW) != null;
		TowerTestSupport.result("trapMob", "onGround=" + zombieOnGround + " zombiePoisoned=" + zombiePoisoned + " stillArmed=" + stillArmed);
		zombie.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(zombieOnGround, "the zombie never stepped on the trap, so the case proves nothing");
		helper.assertTrue(!zombiePoisoned && stillArmed, "a mob sprang the trap: poisoned=" + zombiePoisoned + " armed after=" + stillArmed);
		helper.succeed();
	}

	/** A sprung trap rests before it springs again, so standing on one is not a death sentence. */
	@GameTest(maxTicks = 200)
	public void aSprungTrapRestsBeforeSpringingAgain(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		boolean first = TowerTraps.spring(tower, under(player), player, TrapTheme.BOG, LOW) != null;
		boolean again = TowerTraps.spring(tower, under(player), player, TrapTheme.BOG, LOW) != null;
		helper.runAfterDelay(TowerTraps.REARM_TICKS + 2L, () -> {
			boolean rested = TowerTraps.spring(tower, under(player), player, TrapTheme.BOG, LOW) != null;
			TowerTestSupport.result("trapRests", "first=" + first + " again=" + again + " rested=" + rested);
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(first && !again && rested, "a trap sprang first=" + first + " at once again=" + again + " after resting=" + rested);
			helper.succeed();
		});
	}

	/** Walking onto a trap springs it: a player's step, not a call, sets it off. */
	@GameTest
	public void walkingOntoATrapSpringsIt(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		BlockPos ground = under(player);
		tower.setBlockAndUpdate(ground, TowerBlocks.TRAPS.get(TrapTheme.BOG).defaultBlockState());
		// As the server moves a player whose client walked: the move, then what the blocks passed over do.
		Vec3 from = player.position();
		player.move(MoverType.SELF, new Vec3(0.05, -0.1, 0.0));
		player.applyEffectsFromBlocks(from, player.position());
		boolean poisoned = player.hasEffect(MobEffects.POISON);
		TowerTestSupport.result("trapStep", "onGround=" + player.onGround() + " poisoned=" + poisoned);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(poisoned, "stepping onto a bog trap did nothing");
		helper.succeed();
	}

	/** A player in creative walks over traps untouched. */
	@GameTest
	public void aCreativePlayerWalksOverTraps(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = standing(helper, tower, plainFloor(tower));
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		BlockPos ground = under(player);
		tower.setBlockAndUpdate(ground, TowerBlocks.TRAPS.get(TrapTheme.BOG).defaultBlockState());
		Vec3 from = player.position();
		player.move(MoverType.SELF, new Vec3(0.05, -0.1, 0.0));
		player.applyEffectsFromBlocks(from, player.position());
		boolean poisoned = player.hasEffect(MobEffects.POISON);
		boolean onGround = player.onGround();
		TowerTestSupport.result("trapCreative", "onGround=" + onGround + " poisoned=" + poisoned);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(onGround, "the player never stepped on the trap, so the case proves nothing");
		helper.assertFalse(poisoned, "a trap sprang under a creative player");
		helper.succeed();
	}
}
