package com.stella.tower.colosseum;

import com.stella.tower.StellaTower;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a guardian does, tick by tick, in place of vanilla goals: pick a target, walk its horse to it, fight, and go home.
 * <p>
 * It holds its own target. A guardian takes the nearest player inside the arena, and never one outside it. Whoever hurts
 * it, wherever they stand, is its target for {@value #RETALIATE_TICKS} ticks. With no target it walks back to its post.
 * Between blows it picks a special move ({@link MoveKind}); every move shows its start before it lands: sound, particles,
 * a raised bow.
 */
public final class GuardianBrain {
	public static final int RETALIATE_TICKS = 200;
	public static final double AURA_RADIUS = 12.0;
	private static final int EMBER_TICKS = 3;
	public static final int AURA_TICKS = 20;
	public static final int AURA_DURATION = 60;
	/** The nearest player a guardian takes notice of. */
	public static final double NOTICE = 40.0;
	/** Between the end of one move and the start of the next, in ticks. */
	public static final int GAP_TICKS = 30;
	public static final int FIRST_MOVE_TICKS = 40;
	private static final int REPATH_TICKS = 5;
	private static final int RETARGET_TICKS = 10;
	private static final double BAR_RADIUS = 80.0;
	private static final int MELEE_SWING_TICKS = 20;
	private static final int SHIELD_RAISE_TICKS = 4;
	private static final int SHOT_TICKS = 40;
	private static final double SHOOT_RANGE = 30.0;
	private static final double ARCHER_NEAR = 9.0;
	private static final double ARCHER_FAR = 16.0;
	private static final double ARCHER_HOME = 13.0;
	private static final float SHOT_SPEED = 2.0F;
	private static final float SHOT_SPREAD = 2.0F;
	private static final double ROUND_UP = 0.7;

	private static final Map<UUID, GuardianBrain> BRAINS = new ConcurrentHashMap<>();

	private final Mob rider;
	private final GuardianKind kind;
	private final ServerBossEvent bar;
	private final Map<MoveKind, Integer> readyAt = new EnumMap<>(MoveKind.class);
	private final List<UUID> loosed = new ArrayList<>();
	private final List<UUID> lobs = new ArrayList<>();
	private final List<Lobbed> lobbed = new ArrayList<>();
	private int detonations;
	private int ticks;
	private int nextMoveAt = FIRST_MOVE_TICKS;
	private int nextBlowAt;
	private int raiseAt;
	private @Nullable LivingEntity focus;
	private @Nullable LivingEntity attacker;
	private int attackedAt = Integer.MIN_VALUE / 2;
	private GuardianMoves.@Nullable Move move;
	private @Nullable MoveKind moveKind;
	private int moveTicks;
	private double orbit = 1.0;
	private int orbitUntil;

	/** A lobbed arrow in flight: where it was last seen, and for how long. */
	private static final class Lobbed {
		final Arrow arrow;
		Vec3 last;
		int age;

		Lobbed(final Arrow arrow) {
			this.arrow = arrow;
			this.last = arrow.position();
		}
	}

	private GuardianBrain(final Mob rider, final GuardianKind kind) {
		this.rider = rider;
		this.kind = kind;
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(kind.nameKey()), kind.barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
	}

	// ---------------------------------------------------------------- registry

	static void attach(final Mob rider) {
		GuardianKind kind = GuardianKind.of(rider);
		if (kind != null && rider.level() instanceof ServerLevel) {
			BRAINS.putIfAbsent(rider.getUUID(), new GuardianBrain(rider, kind));
		}
	}

	static void detach(final Mob rider) {
		GuardianBrain brain = BRAINS.remove(rider.getUUID());
		if (brain != null) {
			brain.bar.removeAllPlayers();
			brain.bar.setVisible(false);
		}
	}

	/** The brain of a guardian, found from the rider or from its horse; null for anything else. */
	public static @Nullable GuardianBrain of(final Entity entity) {
		GuardianBrain brain = BRAINS.get(entity.getUUID());
		if (brain != null) {
			return brain;
		}
		Entity passenger = entity.getFirstPassenger();
		return passenger == null ? null : BRAINS.get(passenger.getUUID());
	}

	public static Collection<GuardianBrain> all() {
		return List.copyOf(BRAINS.values());
	}

	static void tickAll(final MinecraftServer server) {
		for (GuardianBrain brain : List.copyOf(BRAINS.values())) {
			if (brain.rider.isRemoved() || !(brain.rider.level() instanceof ServerLevel level)) {
				detach(brain.rider);
				continue;
			}
			if (!brain.rider.isAlive()) {
				continue;
			}
			try {
				brain.tick(level);
			} catch (RuntimeException failure) {
				StellaTower.LOGGER.warn("[COLOSSEUM] a guardian's brain failed: {}", failure.toString());
			}
		}
	}

	// ---------------------------------------------------------------- state for the moves and the tests

	public Mob rider() {
		return this.rider;
	}

	public GuardianKind kind() {
		return this.kind;
	}

	/** The horse it rides, if it is alive and mounted. */
	public @Nullable Mob mount() {
		return this.rider.getVehicle() instanceof Mob horse && horse.isAlive() && horse.entityTags().contains(Guardians.TAG_HORSE) ? horse : null;
	}

	/** The body that walks: the horse if it has one, else the rider. */
	public Mob mover() {
		Mob horse = mount();
		return horse != null ? horse : this.rider;
	}

	public @Nullable LivingEntity focus() {
		return this.focus;
	}

	/** The boss bar the players near it see. */
	public ServerBossEvent bar() {
		return this.bar;
	}

	/** The move it is making, if any. */
	public @Nullable MoveKind current() {
		return this.moveKind;
	}

	/** Every arrow it has loosed, by id. */
	public List<UUID> loosed() {
		return Collections.unmodifiableList(this.loosed);
	}

	/** The lobbed arrows among them, by id. */
	public List<UUID> lobs() {
		return Collections.unmodifiableList(this.lobs);
	}

	/** How many lobbed arrows have landed and burst. */
	public int detonations() {
		return this.detonations;
	}

	public Guardians.Home home() {
		return this.rider.getAttached(Guardians.HOME);
	}

	ServerLevel level() {
		return (ServerLevel) this.rider.level();
	}

	void hurtBy(final LivingEntity who) {
		this.attacker = who;
		this.attackedAt = this.ticks;
	}

	// ---------------------------------------------------------------- the tick

	private void tick(final ServerLevel level) {
		this.ticks++;
		upkeep();
		if (this.ticks % EMBER_TICKS == 0) {
			embers(level);
		}
		if (this.ticks % AURA_TICKS == 0) {
			aura(level);
		}
		if (this.ticks % REPATH_TICKS == 0) {
			updateBar(level);
		}
		trackLobs(level);
		guard();
		if (this.focus == null || this.ticks % RETARGET_TICKS == 0 || !attackable(this.focus)) {
			retarget(level);
		}
		this.rider.setTarget(this.focus);
		LivingEntity target = this.focus;
		if (this.move != null) {
			if (target == null || !attackable(target)) {
				endMove();
			} else if (this.move.tick(this, target, this.moveTicks++)) {
				endMove();
			}
			return;
		}
		if (target == null) {
			this.rider.setAggressive(false);
			goHome();
			return;
		}
		this.rider.setAggressive(true);
		this.rider.getLookControl().setLookAt(target, 30.0F, 30.0F);
		steer(target);
		if (this.ticks >= this.nextMoveAt) {
			chooseMove(target);
		}
		if (this.move == null) {
			blow(level, target);
		}
	}

	/** The melee guardian keeps its off-hand shield up while it hunts, and lowers it only for a swing. */
	private void guard() {
		if (this.kind != GuardianKind.MELEE || !this.rider.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return;
		}
		if (this.focus == null) {
			if (this.rider.isUsingItem()) {
				this.rider.stopUsingItem();
			}
			return;
		}
		if (!this.rider.isUsingItem() && this.ticks >= this.raiseAt) {
			this.rider.startUsingItem(InteractionHand.OFF_HAND);
		}
	}

	/** The marks that keep a guardian what it is: no vanilla goal and no real fire. */
	private void upkeep() {
		Mob horse = mount();
		if (this.ticks % 20 == 0) {
			if (!this.rider.getGoalSelector().getAvailableGoals().isEmpty()) {
				Guardians.strip(this.rider);
			}
			if (horse != null && horse.entityTags().contains(Guardians.TAG_HORSE) && !horse.getGoalSelector().getAvailableGoals().isEmpty()) {
				Guardians.strip(horse);
			}
		}
		if (this.rider.getRemainingFireTicks() > 0) {
			this.rider.clearFire();
		}
		if (horse != null && horse.getRemainingFireTicks() > 0) {
			horse.clearFire();
		}
	}

	// ---------------------------------------------------------------- aura and bar

	/** A boss's wreath: flame and soul fire rise off rider and horse, so it reads as a boss, not as a mob on fire. */
	private void embers(final ServerLevel level) {
		Mob horse = mount();
		for (Mob part : horse == null ? new Mob[] {this.rider} : new Mob[] {this.rider, horse}) {
			double w = part.getBbWidth() * 0.45;
			double h = part.getBbHeight();
			level.sendParticles(ParticleTypes.FLAME, part.getX(), part.getY() + h * 0.5, part.getZ(), 2, w, h * 0.35, w, 0.01);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, part.getX(), part.getY() + h * 0.5, part.getZ(), 2, w, h * 0.35, w, 0.01);
		}
	}

	private void aura(final ServerLevel level) {
		Vec3 at = this.rider.position();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(at) <= AURA_RADIUS * AURA_RADIUS) {
				player.addEffect(new MobEffectInstance(this.kind.aura(), AURA_DURATION, 0, true, true));
			}
		}
		level.sendParticles(this.kind == GuardianKind.MELEE ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL,
			at.x, at.y + 0.4, at.z, 16, AURA_RADIUS / 3.0, 0.2, AURA_RADIUS / 3.0, 0.01);
	}

	/** The bar shows to the players inside the guardian's arena, and to nobody who has left it. */
	private void updateBar(final ServerLevel level) {
		float max = Math.max(1.0F, this.rider.getMaxHealth());
		this.bar.setProgress(Math.max(0.0F, Math.min(1.0F, this.rider.getHealth() / max)));
		for (ServerPlayer player : List.copyOf(this.bar.getPlayers())) {
			if (player.level() != level || !barSees(player)) {
				this.bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.players()) {
			if (barSees(player) && !this.bar.getPlayers().contains(player)) {
				this.bar.addPlayer(player);
			}
		}
	}

	/** Whether a player is where the bar is shown: inside the arena, or near the guardian when it keeps no arena. */
	private boolean barSees(final ServerPlayer player) {
		return home() == null ? player.distanceToSqr(this.rider.position()) <= BAR_RADIUS * BAR_RADIUS : insideArena(player.position());
	}

	// ---------------------------------------------------------------- targets

	/** Whether the guardian may fight this one: alive, not a guardian, and not a spectator or a creative player. */
	static boolean attackable(final @Nullable LivingEntity who) {
		if (who == null || !who.isAlive() || who.isRemoved() || Guardians.isPart(who) || who instanceof ArmorStand) {
			return false;
		}
		return !(who instanceof Player player) || !(player.isSpectator() || player.isCreative());
	}

	private boolean inside(final Entity who) {
		return insideArena(who.position());
	}

	/** Whether a spot lies inside the arena the guardian keeps. */
	boolean insideArena(final Vec3 at) {
		Guardians.Home home = home();
		if (home == null) {
			return true;
		}
		double dx = at.x - (home.center().getX() + 0.5);
		double dz = at.z - (home.center().getZ() + 0.5);
		return dx * dx + dz * dz <= (double) home.radius() * home.radius() && Math.abs(at.y - home.center().getY()) <= home.radius();
	}

	private @Nullable LivingEntity recentAttacker() {
		LivingEntity who = this.attacker;
		if (who != null && this.ticks - this.attackedAt <= RETALIATE_TICKS && attackable(who) && who.level() == this.rider.level()) {
			return who;
		}
		return null;
	}

	/**
	 * Whoever hurt it lately is the target, wherever they stand. Else the held target stays while it is alive and in the
	 * arena. Else the nearest player in the arena, within notice. A target outside the arena is dropped.
	 */
	private void retarget(final ServerLevel level) {
		LivingEntity revenge = recentAttacker();
		if (revenge != null) {
			this.focus = revenge;
			return;
		}
		if (this.focus != null && attackable(this.focus) && inside(this.focus)) {
			return;
		}
		LivingEntity nearest = null;
		double best = NOTICE * NOTICE;
		for (ServerPlayer player : level.players()) {
			double away = player.distanceToSqr(this.rider);
			if (attackable(player) && inside(player) && away <= best) {
				best = away;
				nearest = player;
			}
		}
		this.focus = nearest;
	}

	// ---------------------------------------------------------------- walking

	private void goHome() {
		Guardians.Home home = home();
		Mob body = mover();
		if (home == null || this.ticks % 20 != 0) {
			return;
		}
		Vec3 post = Vec3.atBottomCenterOf(home.post());
		if (body.position().distanceToSqr(post) > 9.0) {
			body.getNavigation().moveTo(post.x, post.y, post.z, 1.0);
		}
	}

	void stopMoving() {
		mover().getNavigation().stop();
	}

	private void steer(final LivingEntity target) {
		if (this.ticks % REPATH_TICKS != 0) {
			return;
		}
		Mob body = mover();
		if (this.kind == GuardianKind.MELEE) {
			if (inReach(target)) {
				body.getNavigation().stop();
				body.lookAt(target, 30.0F, 30.0F);
			} else {
				body.getNavigation().moveTo(target, 1.1);
			}
			return;
		}
		Vec3 away = this.rider.position().subtract(target.position());
		double flat = Math.hypot(away.x, away.z);
		if (flat < ARCHER_NEAR) {
			Vec3 back = flat < 1.0E-3 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(away.x / flat, 0.0, away.z / flat);
			Vec3 goal = target.position().add(back.scale(ARCHER_HOME));
			body.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.2);
		} else if (flat > ARCHER_FAR) {
			body.getNavigation().moveTo(target, 1.0);
		} else {
			if (this.ticks >= this.orbitUntil) {
				this.orbit = -this.orbit;
				this.orbitUntil = this.ticks + 60;
			}
			double angle = Math.atan2(away.z, away.x) + Math.toRadians(35.0) * this.orbit;
			Vec3 goal = target.position().add(Math.cos(angle) * flat, 0.0, Math.sin(angle) * flat);
			body.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
			body.lookAt(target, 30.0F, 30.0F);
		}
	}

	/** Whether the target is close enough to be struck: inside the horse's and the rider's bodies, widened. */
	boolean inReach(final LivingEntity target) {
		Mob horse = mount();
		AABB body = horse == null ? this.rider.getBoundingBox() : horse.getBoundingBox().minmax(this.rider.getBoundingBox());
		return body.inflate(1.8, 0.8, 1.8).intersects(target.getBoundingBox());
	}

	void face(final LivingEntity target) {
		Mob body = mover();
		body.lookAt(target, 360.0F, 360.0F);
		this.rider.getLookControl().setLookAt(target, 360.0F, 360.0F);
	}

	// ---------------------------------------------------------------- blows

	private void blow(final ServerLevel level, final LivingEntity target) {
		if (this.ticks < this.nextBlowAt) {
			return;
		}
		if (this.kind == GuardianKind.MELEE) {
			if (inReach(target)) {
				this.nextBlowAt = this.ticks + MELEE_SWING_TICKS;
				strike(target);
			}
			return;
		}
		double away = Math.sqrt(this.rider.distanceToSqr(target));
		if (away <= SHOOT_RANGE && away >= 3.0 && this.rider.hasLineOfSight(target)) {
			this.nextBlowAt = this.ticks + SHOT_TICKS;
			shoot(target.getBoundingBox().getCenter(), SHOT_SPEED, SHOT_SPREAD, false);
		}
	}

	/** One swing: the guardian's own melee hit, as any mob deals it. */
	void strike(final LivingEntity target) {
		this.rider.stopUsingItem();
		this.raiseAt = this.ticks + SHIELD_RAISE_TICKS;
		this.rider.swingForAttack(InteractionHand.MAIN_HAND);
		this.rider.doHurtTarget(level(), target);
	}

	/** Damage by the guardian's attack, times a factor: for the moves that are not a swing. */
	boolean hurt(final LivingEntity target, final double factor) {
		float amount = (float) (this.rider.getAttributeValue(Attributes.ATTACK_DAMAGE) * factor);
		return target.hurtServer(level(), level().damageSources().mobAttack(this.rider), amount);
	}

	/** Whether a move may hurt this one. */
	static boolean hittable(final LivingEntity who) {
		return attackable(who);
	}

	/**
	 * Looses an arrow from the rider at a spot, aimed along the arrow's own arc. Returns it, or null if it was not
	 * spawned.
	 */
	@Nullable Arrow shoot(final Vec3 at, final float speed, final float spread, final boolean crit) {
		ServerLevel level = level();
		ItemStack ammo = new ItemStack(Items.ARROW);
		Arrow arrow = new Arrow(level, this.rider, ammo, this.rider.getMainHandItem());
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.setCritArrow(crit);
		Vec3 from = arrow.position();
		double flat = Math.hypot(at.x - from.x, at.z - from.z);
		double elevation = Ballistics.elevation(speed, flat, at.y - from.y, false);
		Vec3 direction = aim(at.x - from.x, at.z - from.z, flat, elevation);
		Projectile.spawnProjectileUsingShoot(arrow, level, ammo, direction.x, direction.y, direction.z, speed, spread);
		if (arrow.isRemoved()) {
			return null;
		}
		this.loosed.add(arrow.getUUID());
		level.playSound(null, this.rider.getX(), this.rider.getY(), this.rider.getZ(), SoundEvents.SKELETON_SHOOT, this.rider.getSoundSource(), 1.2F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		return arrow;
	}

	private static Vec3 aim(final double dx, final double dz, final double flat, final double elevation) {
		if (flat < 1.0E-6) {
			return new Vec3(0.0, Math.signum(elevation), 0.0);
		}
		return new Vec3(dx / flat * Math.cos(elevation), Math.sin(elevation), dz / flat * Math.cos(elevation));
	}

	/** Lobs a burning arrow to land on a spot; it bursts where it lands. */
	void lob(final Vec3 landing) {
		ServerLevel level = level();
		ItemStack ammo = new ItemStack(Items.ARROW);
		Arrow arrow = new Arrow(level, this.rider, ammo, this.rider.getMainHandItem());
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		Vec3 from = arrow.position();
		double flat = Math.hypot(landing.x - from.x, landing.z - from.z);
		double speed = GuardianMoves.LOB_SPEED;
		double elevation = Ballistics.elevation(speed, flat, landing.y - from.y, true);
		Vec3 direction = aim(landing.x - from.x, landing.z - from.z, flat, elevation);
		Projectile.spawnProjectileUsingShoot(arrow, level, ammo, direction.x, direction.y, direction.z, (float) speed, 0.0F);
		arrow.igniteForSeconds(60.0F);
		if (!arrow.isRemoved()) {
			this.loosed.add(arrow.getUUID());
			this.lobs.add(arrow.getUUID());
			this.lobbed.add(new Lobbed(arrow));
			level.playSound(null, this.rider.getX(), this.rider.getY(), this.rider.getZ(), SoundEvents.BLAZE_SHOOT, this.rider.getSoundSource(), 1.2F, 0.8F);
		}
	}

	private void trackLobs(final ServerLevel level) {
		if (this.lobbed.isEmpty()) {
			return;
		}
		for (Lobbed each : List.copyOf(this.lobbed)) {
			Arrow arrow = each.arrow;
			boolean landed = arrow.isRemoved() || (each.age >= 3 && arrow.position().distanceToSqr(each.last) < 1.0E-6) || each.age > 240;
			if (landed) {
				this.lobbed.remove(each);
				GuardianMoves.burst(this, level, each.last);
				this.detonations++;
				arrow.discard();
				continue;
			}
			level.sendParticles(ParticleTypes.FLAME, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			each.last = arrow.position();
			each.age++;
		}
	}

	/** Moves a body, horse and rider together, to a standing spot. */
	void teleport(final Vec3 spot, final float yaw) {
		ServerLevel level = level();
		Mob horse = mount();
		if (horse != null) {
			this.rider.stopRiding();
			horse.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, true);
			this.rider.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, true);
			this.rider.startRiding(horse, true, false);
			horse.getNavigation().stop();
		} else {
			this.rider.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, true);
			this.rider.getNavigation().stop();
		}
	}

	// ---------------------------------------------------------------- moves

	private void chooseMove(final LivingEntity target) {
		double away = Math.sqrt(this.rider.distanceToSqr(target));
		List<MoveKind> ready = new ArrayList<>();
		for (MoveKind candidate : MoveKind.of(this.kind)) {
			if (this.ticks >= this.readyAt.getOrDefault(candidate, 0) && candidate.inRange(away)
				&& (candidate != MoveKind.VOLLEY && candidate != MoveKind.VANISH || this.rider.hasLineOfSight(target))) {
				ready.add(candidate);
			}
		}
		if (!ready.isEmpty() && this.rider.getRandom().nextDouble() < ROUND_UP) {
			begin(ready.get(this.rider.getRandom().nextInt(ready.size())), target);
		}
	}

	/** Starts a move now, whatever its cooldown or range; false if the guardian is busy with another. */
	public boolean begin(final MoveKind wanted, final LivingEntity target) {
		if (this.move != null || wanted.owner() != this.kind) {
			return false;
		}
		this.focus = target;
		this.move = GuardianMoves.create(wanted);
		this.moveKind = wanted;
		this.moveTicks = 0;
		this.readyAt.put(wanted, this.ticks + wanted.cooldown());
		StellaTower.LOGGER.debug("[COLOSSEUM] {} begins {}", this.kind, wanted);
		return true;
	}

	private void endMove() {
		if (this.move != null) {
			this.move.end(this);
		}
		StellaTower.LOGGER.debug("[COLOSSEUM] {} ends {}", this.kind, this.moveKind);
		this.move = null;
		this.moveKind = null;
		this.nextMoveAt = this.ticks + GAP_TICKS;
	}

	/** Everything a move reaches within a box that a guardian may hurt. */
	List<LivingEntity> victims(final AABB box) {
		return level().getEntitiesOfClass(LivingEntity.class, box, GuardianBrain::hittable);
	}
}
