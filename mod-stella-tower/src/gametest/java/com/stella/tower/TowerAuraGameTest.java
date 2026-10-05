package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** What each element does for a tower mob: the ice mist, dark armor, holy mending and the on-hit marks. */
public class TowerAuraGameTest {
	/** Far from the safe zones, which sit by the floor's arrival and its hall. */
	private static final double AT_X = 40.0;
	private static final double AT_Z = 10.0;
	/** A tower mob's own floor is a low one, so its blows and health stay small whatever floor it stands on. */
	private static final int OWN_FLOOR = 5;

	private static Vec3 spot(final int floor, final double dx, final double dz) {
		BlockPos origin = TowerLayout.origin(floor);
		return new Vec3(origin.getX() + AT_X + dx, TowerLayout.BASE_Y + 1.0, origin.getZ() + AT_Z + dz);
	}

	private static int freshBuilt(final ServerLevel tower) {
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		return floor;
	}

	private static <T extends Mob> T place(final ServerLevel tower, final EntityType<T> type, final Vec3 at) {
		T mob = type.create(tower, EntitySpawnReason.COMMAND);
		mob.snapTo(at);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		tower.addFreshEntity(mob);
		return mob;
	}

	private static Zombie towerMob(final ServerLevel tower, final TowerElement element, final Vec3 at) {
		Zombie zombie = place(tower, EntityTypes.ZOMBIE, at);
		TowerMobs.makeTowerMob(zombie, OWN_FLOOR, element);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	private static ServerPlayer playerAt(final GameTestHelper helper, final ServerLevel tower, final Vec3 at) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.teleportTo(tower, at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	private static void pulse(final ServerLevel tower, final int floor) {
		TowerAuras.pulse(tower, floor, TowerAuras.now(tower));
	}

	private static void hit(final ServerLevel tower, final LivingEntity victim, final LivingEntity attacker, final float amount) {
		victim.damageCooldownTime = 0;
		victim.hurtServer(tower, attacker.damageSources().mobAttack(attacker), amount);
	}

	private static String slow(final LivingEntity entity) {
		MobEffectInstance effect = entity.getEffect(MobEffects.SLOWNESS);
		return effect == null ? "none" : "amp" + effect.getAmplifier() + "/" + effect.getDuration();
	}

	private static boolean slowed(final LivingEntity entity) {
		MobEffectInstance effect = entity.getEffect(MobEffects.SLOWNESS);
		return effect != null && effect.getAmplifier() == TowerAuras.ICE_SLOW_AMPLIFIER && effect.getDuration() <= TowerAuras.ICE_SLOW_TICKS;
	}

	// ---- ice ----

	@GameTest
	public void icePulseSlowsAPlayerWithinRadius(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		ServerPlayer near = playerAt(helper, tower, spot(floor, TowerAuras.ICE_RADIUS - 1.0, 0));
		pulse(tower, floor);
		TowerTestSupport.result("iceSlowsPlayerInRadius", "near=" + slow(near) + " radius=" + TowerAuras.ICE_RADIUS);
		boolean ok = slowed(near);
		String seen = slow(near);
		ice.discard();
		TowerTestSupport.leave(helper, near);
		helper.assertTrue(ok, "a player inside the mist was not slowed: " + seen);
		helper.succeed();
	}

	@GameTest
	public void icePulseDoesNotSlowAPlayerOutsideTheRadius(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		ServerPlayer far = playerAt(helper, tower, spot(floor, TowerAuras.ICE_RADIUS - 0.5, TowerAuras.ICE_RADIUS - 0.5));
		pulse(tower, floor);
		TowerTestSupport.result("iceSparesPlayerOutside", "far=" + slow(far));
		boolean spared = !far.hasEffect(MobEffects.SLOWNESS);
		String seen = slow(far) + " at " + far.position().distanceTo(ice.position());
		ice.discard();
		TowerTestSupport.leave(helper, far);
		helper.assertTrue(spared, "a player outside the mist was slowed: " + seen);
		helper.succeed();
	}

	@GameTest
	public void icePulseSlowsATamedWolf(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		ServerPlayer owner = playerAt(helper, tower, spot(floor, 30, 30));
		Wolf wolf = place(tower, EntityTypes.WOLF, spot(floor, 2, 0));
		wolf.tame(owner);
		pulse(tower, floor);
		TowerTestSupport.result("iceSlowsTamedWolf", "wolf=" + slow(wolf));
		boolean ok = slowed(wolf);
		String seen = slow(wolf);
		ice.discard();
		wolf.discard();
		TowerTestSupport.leave(helper, owner);
		helper.assertTrue(ok, "a tamed wolf inside the mist was not slowed: " + seen);
		helper.succeed();
	}

	@GameTest
	public void icePulseSlowsAMobTheSpellsModSummoned(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		Zombie summoned = place(tower, EntityTypes.ZOMBIE, spot(floor, 2, 0));
		summoned.addTag(TowerAuras.SUMMONED_TAG);
		pulse(tower, floor);
		TowerTestSupport.result("iceSlowsSummoned", "summoned=" + slow(summoned));
		boolean ok = slowed(summoned);
		String seen = slow(summoned);
		ice.discard();
		summoned.discard();
		helper.assertTrue(ok, "a summoned mob inside the mist was not slowed: " + seen);
		helper.succeed();
	}

	@GameTest
	public void icePulseSlowsAPlayerMadeIronGolem(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		IronGolem golem = place(tower, EntityTypes.IRON_GOLEM, spot(floor, 2, 0));
		golem.setPlayerCreated(true);
		IronGolem village = place(tower, EntityTypes.IRON_GOLEM, spot(floor, -2, 0));
		pulse(tower, floor);
		TowerTestSupport.result("iceSlowsGolem", "built=" + slow(golem) + " village=" + slow(village));
		boolean ok = slowed(golem) && !village.hasEffect(MobEffects.SLOWNESS);
		String seen = "player-made golem " + slow(golem) + ", village golem " + slow(village);
		ice.discard();
		golem.discard();
		village.discard();
		helper.assertTrue(ok, seen);
		helper.succeed();
	}

	@GameTest
	public void icePulseSlowsAGhost(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		TowerGhost ghost = place(tower, TowerGhosts.GHOST, spot(floor, 2, 0));
		pulse(tower, floor);
		boolean ok = slowed(ghost);
		String seen = slow(ghost);
		TowerTestSupport.result("iceSlowsGhost", "ghost=" + seen);
		ice.discard();
		ghost.discard();
		helper.assertTrue(ok, "a ghost inside the mist was not slowed: " + seen);
		helper.succeed();
	}

	@GameTest
	public void icePulseSparesTowerMobs(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		Zombie kin = towerMob(tower, TowerElement.WIND, spot(floor, 2, 0));
		Zombie other = towerMob(tower, TowerElement.ICE, spot(floor, -2, 0));
		pulse(tower, floor);
		TowerTestSupport.result("iceSparesTowerMobs", "kin=" + slow(kin) + " otherIce=" + slow(other) + " self=" + slow(ice));
		boolean spared = !kin.hasEffect(MobEffects.SLOWNESS) && !other.hasEffect(MobEffects.SLOWNESS) && !ice.hasEffect(MobEffects.SLOWNESS);
		String seen = "kin " + slow(kin) + " other " + slow(other) + " self " + slow(ice);
		ice.discard();
		kin.discard();
		other.discard();
		helper.assertTrue(spared, "tower mobs were slowed by the mist: " + seen);
		helper.succeed();
	}

	@GameTest
	public void icePulseSparesAPlayerInASafeZone(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		BlockPos origin = TowerLayout.origin(floor);
		TowerMaze.Zone zone = TowerPlan.at(tower, floor).safeZones().get(0);
		Vec3 inside = new Vec3(origin.getX() + zone.x() + 2.5, TowerLayout.BASE_Y + 1.0, origin.getZ() + zone.z() + 2.5);
		Zombie ice = towerMob(tower, TowerElement.ICE, inside.add(2.0, 0, 0));
		ServerPlayer player = playerAt(helper, tower, inside);
		pulse(tower, floor);
		TowerTestSupport.result("iceSparesSafeZone", "player=" + slow(player) + " zone=" + zone);
		boolean spared = !player.hasEffect(MobEffects.SLOWNESS);
		String seen = slow(player);
		ice.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(spared, "a player in a safe zone was slowed: " + seen);
		helper.succeed();
	}

	/**
	 * The whole road: the server's own tick pulses an occupied floor, and the mist reaches the player.
	 * <p>
	 * The window spans thirty pulses, not ten. A full suite run once missed the mist over ten of them and
	 * the same suite passed on the next run, so the wait was too short for the slower of two runs.
	 */
	@GameTest(maxTicks = 300)
	public void iceMistReachesAPlayerOnTheServersOwnPulse(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie ice = towerMob(tower, TowerElement.ICE, spot(floor, 0, 0));
		ServerPlayer near = playerAt(helper, tower, spot(floor, 2, 0));
		TowerTestSupport.holdRoom(tower, spot(floor, 0, 0));
		long[] seen = {-1};
		boolean[] cleaned = {false};
		Runnable clean = () -> {
			if (!cleaned[0]) {
				cleaned[0] = true;
				ice.discard();
				TowerTestSupport.leave(helper, near);
			}
		};
		// A failing wait is silent until the test times out, so the failure path is cleaned just before that.
		helper.runAfterDelay(295L, clean);
		helper.succeedWhen(() -> {
			if (seen[0] < 0 && slowed(near)) {
				seen[0] = helper.getTick();
				TowerTestSupport.result("iceOwnPulse", "slowedAtTick=" + seen[0] + " effect=" + slow(near));
			}
			helper.assertTrue(seen[0] >= 0, "the mist never reached the player: towerPlayers=" + tower.players().size()
				+ " inTower=" + (near.level() == tower) + " at=" + near.blockPosition() + " floor=" + floor + " iceAlive=" + ice.isAlive());
			clean.run();
		});
	}

	// ---- dark ----

	/** Stacks lost this long after the last hit: one at the end of the hold, and one more at each step after. */
	private static int stepsLost(final int idle) {
		int lost = 0;
		while (TowerAuras.DARK_HOLD_TICKS + lost * TowerAuras.DARK_DECAY_TICKS <= idle) {
			lost++;
		}
		return lost;
	}

	private static double armor(final LivingEntity mob) {
		return mob.getAttributeValue(Attributes.ARMOR);
	}

	@GameTest
	public void darkGainsOneArmorPerHit(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie dark = towerMob(tower, TowerElement.DARK, spot(floor, 0, 0));
		Zombie foe = towerMob(tower, TowerElement.WIND, spot(floor, 1, 0));
		double base = armor(dark);
		int hits = 3;
		for (int i = 0; i < hits; i++) {
			hit(tower, dark, foe, 1.0F);
		}
		double gained = armor(dark) - base;
		TowerTestSupport.result("darkPerHit", "hits=" + hits + " gained=" + gained + " perStack=" + TowerAuras.DARK_ARMOR_PER_STACK);
		dark.discard();
		foe.discard();
		helper.assertTrue(gained == hits * TowerAuras.DARK_ARMOR_PER_STACK, "three hits gave " + gained + " armor");
		helper.succeed();
	}

	@GameTest
	public void darkArmorStopsAtItsCap(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie dark = towerMob(tower, TowerElement.DARK, spot(floor, 0, 0));
		Zombie foe = towerMob(tower, TowerElement.WIND, spot(floor, 1, 0));
		double base = armor(dark);
		for (int i = 0; i < TowerAuras.DARK_STACK_CAP + 5; i++) {
			hit(tower, dark, foe, 1.0F);
		}
		double gained = armor(dark) - base;
		TowerTestSupport.result("darkCap", "gained=" + gained + " cap=" + TowerAuras.DARK_STACK_CAP);
		dark.discard();
		foe.discard();
		helper.assertTrue(gained == TowerAuras.DARK_STACK_CAP * TowerAuras.DARK_ARMOR_PER_STACK, "armor grew by " + gained + " past the cap");
		helper.succeed();
	}

	@GameTest
	public void darkOnlyGainsArmorForDarkMobs(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie wind = towerMob(tower, TowerElement.WIND, spot(floor, 0, 0));
		Zombie foe = towerMob(tower, TowerElement.LIGHTNING, spot(floor, 1, 0));
		double base = armor(wind);
		for (int i = 0; i < 3; i++) {
			hit(tower, wind, foe, 1.0F);
		}
		double gained = armor(wind) - base;
		TowerTestSupport.result("darkOnlyDark", "windGained=" + gained);
		wind.discard();
		foe.discard();
		helper.assertTrue(gained == 0.0, "a wind mob hardened by " + gained);
		helper.succeed();
	}

	/** Held for the hold time after the last hit, then one stack a decay step; the clock is the caller's, since the level's is frozen. */
	@GameTest
	public void darkHoldsItsArmorThenDecaysOnePerStep(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie dark = towerMob(tower, TowerElement.DARK, spot(floor, 0, 0));
		Zombie foe = towerMob(tower, TowerElement.WIND, spot(floor, 1, 0));
		double base = armor(dark);
		for (int i = 0; i < TowerAuras.DARK_STACK_CAP; i++) {
			hit(tower, dark, foe, 1.0F);
		}
		// All in this one tick, so the server's own pulse cannot rewrite the armor between the samples.
		int last = TowerAuras.now(tower);
		int cap = TowerAuras.DARK_STACK_CAP;
		List<String> log = new ArrayList<>();
		int[] offsets = {
			TowerAuras.DARK_HOLD_TICKS - 1,
			TowerAuras.DARK_HOLD_TICKS,
			TowerAuras.DARK_HOLD_TICKS + TowerAuras.DARK_DECAY_TICKS - 1,
			TowerAuras.DARK_HOLD_TICKS + TowerAuras.DARK_DECAY_TICKS,
			TowerAuras.DARK_HOLD_TICKS + TowerAuras.DARK_DECAY_TICKS * (cap - 1),
			TowerAuras.DARK_HOLD_TICKS + TowerAuras.DARK_DECAY_TICKS * cap + 500
		};
		int[] want = new int[offsets.length];
		for (int i = 0; i < offsets.length; i++) {
			want[i] = Math.max(0, cap - stepsLost(offsets[i]));
		}
		boolean ok = true;
		for (int i = 0; i < offsets.length; i++) {
			TowerAuras.pulse(tower, floor, last + offsets[i]);
			double got = (armor(dark) - base) / TowerAuras.DARK_ARMOR_PER_STACK;
			log.add("+" + offsets[i] + "=" + got);
			ok &= got == want[i];
		}
		TowerTestSupport.result("darkDecay", "hold=" + TowerAuras.DARK_HOLD_TICKS + " step=" + TowerAuras.DARK_DECAY_TICKS + " samples=" + log);
		dark.discard();
		foe.discard();
		helper.assertTrue(ok, "dark armor by ticks after the last hit: " + log + ", wanted " + java.util.Arrays.toString(want));
		helper.succeed();
	}

	@GameTest
	public void darkHitAfterPartialDecayStacksOnTheRemainder(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie dark = towerMob(tower, TowerElement.DARK, spot(floor, 0, 0));
		Zombie foe = towerMob(tower, TowerElement.WIND, spot(floor, 1, 0));
		for (int i = 0; i < 4; i++) {
			hit(tower, dark, foe, 1.0F);
		}
		int last = TowerAuras.now(tower);
		// Two steps into the decay: two of the four stacks are gone.
		int later = last + TowerAuras.DARK_HOLD_TICKS + TowerAuras.DARK_DECAY_TICKS;
		int remaining = TowerAuras.darkStacks(dark, later);
		int expected = 4 - stepsLost(later - last);
		TowerTestSupport.result("darkRemainder", "remaining=" + remaining);
		dark.discard();
		foe.discard();
		helper.assertTrue(remaining == expected && expected == 2, "four stacks two decay steps on left " + remaining + ", wanted " + expected);
		helper.succeed();
	}

	// ---- holy ----

	private static Zombie hurt(final Zombie zombie) {
		zombie.setHealth(zombie.getMaxHealth() / 2.0F);
		return zombie;
	}

	@GameTest
	public void holyMendsItselfAndAHurtTowerMobNearby(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie holy = towerMob(tower, TowerElement.HOLY, spot(floor, 0, 0));
		Zombie kin = towerMob(tower, TowerElement.WIND, spot(floor, TowerAuras.HOLY_RADIUS - 1.0, 0));
		holy.getAttribute(Attributes.MAX_HEALTH).removeModifiers();
		kin.getAttribute(Attributes.MAX_HEALTH).removeModifiers();
		holy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		kin.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		hurt(holy);
		hurt(kin);
		float holyBefore = holy.getHealth();
		float kinBefore = kin.getHealth();
		TowerAuras.pulse(tower, floor, TowerAuras.HOLY_INTERVAL_TICKS * 3);
		float holyGain = holy.getHealth() - holyBefore;
		float kinGain = kin.getHealth() - kinBefore;
		float wantSelf = (float) (holy.getMaxHealth() * TowerAuras.HOLY_HEAL_SHARE);
		float wantKin = (float) (kin.getMaxHealth() * TowerAuras.HOLY_HEAL_SHARE);
		TowerTestSupport.result("holyMends", "self=" + holyGain + " kin=" + kinGain + " wantSelf=" + wantSelf + " wantKin=" + wantKin);
		holy.discard();
		kin.discard();
		helper.assertTrue(Math.abs(holyGain - wantSelf) < 0.01F && Math.abs(kinGain - wantKin) < 0.01F,
			"holy mended self " + holyGain + " and kin " + kinGain + ", wanted " + wantSelf + " and " + wantKin);
		helper.succeed();
	}

	@GameTest
	public void holyDoesNotMendATowerMobBeyondItsRadius(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie holy = towerMob(tower, TowerElement.HOLY, spot(floor, 0, 0));
		Zombie far = hurt(towerMob(tower, TowerElement.WIND, spot(floor, TowerAuras.HOLY_RADIUS - 0.5, TowerAuras.HOLY_RADIUS - 0.5)));
		float before = far.getHealth();
		TowerAuras.pulse(tower, floor, TowerAuras.HOLY_INTERVAL_TICKS * 3);
		float gain = far.getHealth() - before;
		TowerTestSupport.result("holyRadius", "farGain=" + gain);
		holy.discard();
		far.discard();
		helper.assertTrue(gain == 0.0F, "a tower mob beyond the radius was mended by " + gain);
		helper.succeed();
	}

	@GameTest
	public void holyMendsOnlyOnItsInterval(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie holy = hurt(towerMob(tower, TowerElement.HOLY, spot(floor, 0, 0)));
		float before = holy.getHealth();
		TowerAuras.pulse(tower, floor, TowerAuras.HOLY_INTERVAL_TICKS * 3 + TowerAuras.PULSE_TICKS);
		float gain = holy.getHealth() - before;
		TowerTestSupport.result("holyInterval", "offBeatGain=" + gain);
		holy.discard();
		helper.assertTrue(gain == 0.0F, "holy mended between its beats by " + gain);
		helper.succeed();
	}

	@GameTest
	public void holyDoesNotMendAPlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie holy = towerMob(tower, TowerElement.HOLY, spot(floor, 0, 0));
		ServerPlayer player = playerAt(helper, tower, spot(floor, 2, 0));
		TowerTestSupport.vulnerable(player);
		player.setHealth(10.0F);
		TowerAuras.pulse(tower, floor, TowerAuras.HOLY_INTERVAL_TICKS * 3);
		float health = player.getHealth();
		TowerTestSupport.result("holyPlayers", "health=" + health);
		holy.discard();
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(health == 10.0F, "holy mended a player to " + health);
		helper.succeed();
	}

	// ---- fire, water, wind, void: on hit ----

	private static Pig victim(final ServerLevel tower, final int floor, final double dx) {
		Pig pig = place(tower, EntityTypes.PIG, spot(floor, dx, 0));
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		pig.setHealth(200.0F);
		return pig;
	}

	/** A blow soaked whole by absorption hearts still lands: it costs no health, and it still burns. */
	@GameTest
	public void fireHitStillIgnitesATargetThatAbsorbsItWhole(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie fire = towerMob(tower, TowerElement.FIRE, spot(floor, 0, 0));
		Pig padded = victim(tower, floor, 1.5);
		padded.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(1000.0);
		padded.setAbsorptionAmount(1000.0F);
		float before = padded.getHealth();
		hit(tower, padded, fire, 1.0F);
		int want = Math.round(TowerAuras.FIRE_SECONDS * 20.0F);
		float lost = before - padded.getHealth();
		TowerTestSupport.result("fireHitAbsorbed", "healthLost=" + lost + " burnTicks=" + padded.getRemainingFireTicks() + " want=" + want);
		int burning = padded.getRemainingFireTicks();
		fire.discard();
		padded.discard();
		helper.assertTrue(lost == 0.0F, "the case proves nothing: the hit cost " + lost + " health");
		helper.assertTrue(burning == want, "an absorbed fire hit left " + burning + " ticks of burning, not " + want);
		helper.succeed();
	}

	@GameTest
	public void fireHitIgnitesItsTarget(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie fire = towerMob(tower, TowerElement.FIRE, spot(floor, 0, 0));
		Zombie plain = towerMob(tower, TowerElement.WIND, spot(floor, 0, 5));
		Pig burned = victim(tower, floor, 1.5);
		Pig spared = victim(tower, floor, 6.5);
		hit(tower, burned, fire, 1.0F);
		hit(tower, spared, plain, 1.0F);
		int want = Math.round(TowerAuras.FIRE_SECONDS * 20.0F);
		TowerTestSupport.result("fireHit", "burnTicks=" + burned.getRemainingFireTicks() + " want=" + want + " controlTicks=" + spared.getRemainingFireTicks());
		boolean ok = burned.getRemainingFireTicks() == want && spared.getRemainingFireTicks() <= 0;
		fire.discard();
		plain.discard();
		burned.discard();
		spared.discard();
		helper.assertTrue(ok, "fire hit left " + burned.getRemainingFireTicks() + " ticks of burning, a wind hit " + spared.getRemainingFireTicks());
		helper.succeed();
	}

	/** A control hit by a Lightning mob from the same spot: the difference is the element's own doing. */
	private static Vec3 velocityAfter(final ServerLevel tower, final int floor, final TowerElement element) {
		Zombie attacker = towerMob(tower, element, spot(floor, 0, 0));
		Pig target = victim(tower, floor, 1.5);
		hit(tower, target, attacker, 1.0F);
		Vec3 velocity = target.getDeltaMovement();
		attacker.discard();
		target.discard();
		return velocity;
	}

	@GameTest
	public void waterHitPushesItsTargetAwayHarder(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Vec3 wet = velocityAfter(tower, floor, TowerElement.WATER);
		Vec3 control = velocityAfter(tower, floor, TowerElement.LIGHTNING);
		double extra = wet.x - control.x;
		TowerTestSupport.result("waterHit", "extraX=" + extra + " want=" + TowerAuras.WATER_KNOCKBACK + " extraY=" + (wet.y - control.y));
		helper.assertTrue(Math.abs(extra - TowerAuras.WATER_KNOCKBACK) < 1.0E-6 && Math.abs(wet.y - control.y) < 1.0E-6,
			"a water hit added " + extra + " horizontal push, not " + TowerAuras.WATER_KNOCKBACK);
		helper.succeed();
	}

	@GameTest
	public void windHitLiftsItsTarget(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Vec3 gust = velocityAfter(tower, floor, TowerElement.WIND);
		Vec3 control = velocityAfter(tower, floor, TowerElement.LIGHTNING);
		double lift = gust.y - control.y;
		TowerTestSupport.result("windHit", "lift=" + lift + " want=" + TowerAuras.WIND_LIFT);
		helper.assertTrue(Math.abs(lift - TowerAuras.WIND_LIFT) < 1.0E-6, "a wind hit lifted " + lift + ", not " + TowerAuras.WIND_LIFT);
		helper.succeed();
	}

	@GameTest
	public void voidHitDarkensItsTarget(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie voidMob = towerMob(tower, TowerElement.VOID, spot(floor, 0, 0));
		Zombie plain = towerMob(tower, TowerElement.WIND, spot(floor, 0, 5));
		Pig darkened = victim(tower, floor, 1.5);
		Pig spared = victim(tower, floor, 6.5);
		hit(tower, darkened, voidMob, 1.0F);
		hit(tower, spared, plain, 1.0F);
		MobEffectInstance effect = darkened.getEffect(MobEffects.DARKNESS);
		int ticks = effect == null ? -1 : effect.getDuration();
		TowerTestSupport.result("voidHit", "darknessTicks=" + ticks + " want=" + TowerAuras.VOID_DARKNESS_TICKS + " control=" + spared.hasEffect(MobEffects.DARKNESS));
		boolean ok = ticks == TowerAuras.VOID_DARKNESS_TICKS && !spared.hasEffect(MobEffects.DARKNESS);
		voidMob.discard();
		plain.discard();
		darkened.discard();
		spared.discard();
		helper.assertTrue(ok, "a void hit left darkness for " + ticks + " ticks");
		helper.succeed();
	}

	// ---- lightning ----

	@GameTest
	public void lightningQuickensWhileItHasATarget(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie bolt = towerMob(tower, TowerElement.LIGHTNING, spot(floor, 0, 0));
		Zombie foe = towerMob(tower, TowerElement.WIND, spot(floor, 3, 0));
		double base = bolt.getAttributeValue(Attributes.MOVEMENT_SPEED);
		bolt.setTarget(foe);
		pulse(tower, floor);
		double hunting = bolt.getAttributeValue(Attributes.MOVEMENT_SPEED);
		bolt.setTarget(null);
		pulse(tower, floor);
		double after = bolt.getAttributeValue(Attributes.MOVEMENT_SPEED);
		TowerTestSupport.result("lightningSpeed", "base=" + base + " hunting=" + hunting + " after=" + after + " bonus=" + TowerAuras.LIGHTNING_SPEED_BONUS);
		bolt.discard();
		foe.discard();
		helper.assertTrue(Math.abs(hunting - base * (1.0 + TowerAuras.LIGHTNING_SPEED_BONUS)) < 1.0E-9, "hunting speed " + hunting + " from " + base);
		helper.assertTrue(Math.abs(after - base) < 1.0E-9, "speed stayed at " + after + " once the target was gone");
		helper.succeed();
	}

	@GameTest
	public void lightningAtRestKeepsItsOwnPace(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		Zombie bolt = towerMob(tower, TowerElement.LIGHTNING, spot(floor, 0, 0));
		double base = bolt.getAttributeValue(Attributes.MOVEMENT_SPEED);
		pulse(tower, floor);
		double idle = bolt.getAttributeValue(Attributes.MOVEMENT_SPEED);
		TowerTestSupport.result("lightningIdle", "base=" + base + " idle=" + idle);
		bolt.discard();
		helper.assertTrue(idle == base, "an idle lightning mob ran at " + idle + " from " + base);
		helper.succeed();
	}

	// ---- particles ----

	/** The particles sent at the named entities only: a neighbour's, sent at another entity, are not heard. */
	private static List<ParticleOptions> recordFrom(final java.util.Set<LivingEntity> subjects, final Runnable action) {
		List<ParticleOptions> seen = new ArrayList<>();
		TowerAuras.particleSink = (at, particle, count) -> {
			if (subjects.contains(at)) {
				seen.add(particle);
			}
		};
		try {
			action.run();
		} finally {
			TowerAuras.particleSink = null;
		}
		return seen;
	}

	/** Every element shows its own particles, as far as the server can tell: it hands them to the level to send. */
	@GameTest
	public void everyElementSendsItsOwnParticles(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = freshBuilt(tower);
		List<String> missing = new ArrayList<>();
		java.util.Map<TowerElement, List<ParticleOptions>> want = new java.util.EnumMap<>(TowerElement.class);
		want.put(TowerElement.ICE, List.of(ParticleTypes.SNOWFLAKE, ParticleTypes.CLOUD));
		want.put(TowerElement.DARK, List.of(ParticleTypes.SQUID_INK));
		want.put(TowerElement.HOLY, List.of(ParticleTypes.END_ROD, ParticleTypes.WAX_ON));
		want.put(TowerElement.FIRE, List.of(ParticleTypes.FLAME));
		want.put(TowerElement.WATER, List.of(ParticleTypes.DRIPPING_WATER));
		want.put(TowerElement.WIND, List.of(ParticleTypes.POOF));
		want.put(TowerElement.LIGHTNING, List.of(ParticleTypes.ELECTRIC_SPARK));
		want.put(TowerElement.VOID, List.of(ParticleTypes.PORTAL));
		for (TowerElement element : TowerElement.values()) {
			Zombie mob = towerMob(tower, element, spot(floor, 0, 0));
			Zombie other = towerMob(tower, TowerElement.HOLY == element ? TowerElement.FIRE : TowerElement.WIND, spot(floor, 1, 0));
			Pig target = victim(tower, floor, 1.5);
			Zombie hurtKin = hurt(other);
			hurt(mob);
			mob.setTarget(other);
			List<ParticleOptions> seen = recordFrom(java.util.Set.of(mob, target), () -> {
				// A pulse for the ambient elements, a hit for the on-hit ones and Dark.
				TowerAuras.pulse(tower, floor, TowerAuras.HOLY_INTERVAL_TICKS * 3);
				hit(tower, target, mob, 1.0F);
				hit(tower, mob, other, 1.0F);
			});
			mob.discard();
			other.discard();
			target.discard();
			for (ParticleOptions particle : want.get(element)) {
				if (!seen.contains(particle)) {
					missing.add(element + " sent no " + particle);
				}
			}
			hurtKin.discard();
		}
		TowerTestSupport.result("particles", "missing=" + missing);
		helper.assertTrue(missing.isEmpty(), "particles never sent: " + missing);
		helper.succeed();
	}

	/** The server sends particles, but only a client can show that they are seen; the harness has none. */
	@GameTest(required = false)
	public void elementParticlesAreVisibleInPlayRequiresManualTest(final GameTestHelper helper) {
		helper.fail("headless server cannot see particles: in play, near an ice mob check for snowflake mist, a dark mob for ink puffs, "
			+ "a holy mob for glowing motes, and fire, water, wind and void hits for flames, drips, puffs and portal swirls");
	}
}
