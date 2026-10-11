package com.stella.mobs.projectile;

import com.stella.mobs.behavior.GhastFireballGoal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A ghast's shot: a slow fireball that bursts in a blast that hurts and throws bodies but breaks no block.
 * Where it lands it hurts a player in reach, lights one to three fires at random spots when mobs may grief,
 * and leaves a cloud of Wither and Slowness that lingers five seconds.
 */
public class GhastWitherShot extends LargeFireball {
	/** How far below a hit the cloud looks for ground. */
	private static final double SETTLE_DEPTH = 4.0;
	/** How far above and below the landing a fire looks for the ground in its column. */
	private static final int FIRE_REACH_UP = 2;
	private static final int FIRE_REACH_DOWN = 4;
	/** Tries for each fire, so a landing on rough ground still lights what it can. */
	private static final int FIRE_TRIES = 8;
	private final int witherAmplifier;

	public GhastWitherShot(final Level level, final LivingEntity owner, final Vec3 direction, final int witherAmplifier) {
		super(level, owner, direction, 0);
		this.witherAmplifier = witherAmplifier;
		// Vanilla starts a fireball slow and speeds it up. This one starts at its speed and holds it.
		this.setDeltaMovement(direction.normalize().scale(GhastFireballGoal.SHOT_SPEED));
		this.accelerationPower = this.holdingPush();
	}

	@Override
	public void tick() {
		super.tick();
		// A shot that has hit nothing by now burns out, so a miss never leaves its cloud far away.
		if (!this.level().isClientSide() && this.tickCount > GhastFireballGoal.SHOT_LIFE_TICKS) {
			this.discard();
		}
	}

	// A shot saved in flight would reload as a vanilla fireball that explodes, so none is saved.
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	// A player's blow turns the shot back, and vanilla then lets it speed up. It keeps its own speed instead.
	@Override
	protected void onDeflection(final boolean byAttack) {
		super.onDeflection(byAttack);
		this.accelerationPower = this.holdingPush();
	}

	/** The push that cancels the loss to inertia each tick, so the speed stays where it is. */
	private double holdingPush() {
		return GhastFireballGoal.SHOT_SPEED * (1.0 / this.getInertia() - 1.0);
	}

	// The whole hit, replaced: a blast that spares blocks, then the landing.
	@Override
	protected void onHit(final HitResult hitResult) {
		if (this.level() instanceof ServerLevel level) {
			Vec3 blast = hitResult.getLocation();
			Entity source = this.getOwner() != null ? this.getOwner() : this;
			level.explode(source, null, StellaSmallFireball.BODIES_ONLY, blast.x, blast.y, blast.z, GhastFireballGoal.EXPLOSION_POWER, false, Level.ExplosionInteraction.NONE);
			Vec3 at = settle(level, hitResult);
			List<Player> hurt = hurtPlayers(level, at);
			AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
			MobEffectInstance wither = new MobEffectInstance(MobEffects.WITHER, GhastFireballGoal.WITHER_TICKS, this.witherAmplifier);
			if (this.getOwner() instanceof LivingEntity owner) {
				cloud.setOwner(owner);
			}
			cloud.setRadius(GhastFireballGoal.CLOUD_RADIUS);
			cloud.setDuration(GhastFireballGoal.CLOUD_DURATION_TICKS);
			cloud.setWaitTime(0);
			cloud.setRadiusPerTick(0.0F);
			cloud.addEffect(wither);
			cloud.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GhastFireballGoal.SLOWNESS_TICKS, GhastFireballGoal.SLOWNESS_AMPLIFIER));
			level.addFreshEntity(cloud);
			List<BlockPos> fires = scorch(level, at, level.getRandom());
			if (this.getOwner() instanceof Ghast ghast) {
				GhastFireballGoal.cloudLanded(ghast, cloud, wither);
				GhastFireballGoal.shotLanded(ghast, at, fires, hurt);
			}
		}
		this.discard();
	}

	// A cloud only reaches the bodies it lies across, so it settles at a struck body's feet or on the ground below the hit.
	private Vec3 settle(final ServerLevel level, final HitResult hitResult) {
		if (hitResult instanceof EntityHitResult entityHit) {
			return entityHit.getEntity().position();
		}

		Vec3 at = hitResult.getLocation();
		BlockHitResult ground = level.clip(new ClipContext(at, at.subtract(0.0, SETTLE_DEPTH, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return ground.getType() == HitResult.Type.BLOCK ? ground.getLocation() : at;
	}

	// The landing hurts a player whose body reaches the cloud's radius. The ghast is the source, so its level scaling applies.
	// A shot a player has turned back hurts no one: the player who struck it stands near its landing.
	private List<Player> hurtPlayers(final ServerLevel level, final Vec3 at) {
		double reach = GhastFireballGoal.CLOUD_RADIUS;
		List<Player> hurt = new ArrayList<>();
		if (this.getOwner() instanceof Player) {
			return hurt;
		}

		for (Player player : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(reach), candidate -> candidate.isAlive() && !candidate.isSpectator())) {
			if (player.getBoundingBox().distanceToSqr(at) <= reach * reach
				&& player.hurtServer(level, level.damageSources().fireball(this, this.getOwner()), GhastFireballGoal.LANDING_DAMAGE)) {
				hurt.add(player);
			}
		}

		return hurt;
	}

	/**
	 * Lights {@link GhastFireballGoal#FIRE_MIN} to {@link GhastFireballGoal#FIRE_MAX} fires at random spots within the
	 * cloud's radius round {@code center}. A spot is air above ground that holds a fire. Nothing burns unless
	 * {@code mobGriefing} is on. Returns the spots lit.
	 */
	public static List<BlockPos> scorch(final ServerLevel level, final Vec3 center, final RandomSource random) {
		List<BlockPos> lit = new ArrayList<>();
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return lit;
		}

		int wanted = GhastFireballGoal.FIRE_MIN + random.nextInt(GhastFireballGoal.FIRE_MAX - GhastFireballGoal.FIRE_MIN + 1);
		double radius = GhastFireballGoal.CLOUD_RADIUS;
		for (int attempt = 0; attempt < wanted * FIRE_TRIES && lit.size() < wanted; attempt++) {
			double angle = random.nextDouble() * 2.0 * Math.PI;
			double reach = radius * Math.sqrt(random.nextDouble());
			double x = center.x + Math.cos(angle) * reach;
			double z = center.z + Math.sin(angle) * reach;
			BlockPos spot = groundSpot(level, BlockPos.containing(x, center.y, z));
			if (spot == null || lit.contains(spot) || Math.hypot(spot.getX() + 0.5 - center.x, spot.getZ() + 0.5 - center.z) > radius) {
				continue;
			}

			BlockState fire = BaseFireBlock.getState(level, spot);
			if (fire.canSurvive(level, spot)) {
				level.setBlockAndUpdate(spot, fire);
				lit.add(spot);
			}
		}

		return lit;
	}

	// The first air block, from the top of the column down, that stands on a block's upper face.
	private static BlockPos groundSpot(final ServerLevel level, final BlockPos column) {
		for (int dy = FIRE_REACH_UP; dy >= -FIRE_REACH_DOWN; dy--) {
			BlockPos pos = column.above(dy);
			BlockPos below = pos.below();
			if (level.getBlockState(pos).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				return pos;
			}
		}

		return null;
	}
}
