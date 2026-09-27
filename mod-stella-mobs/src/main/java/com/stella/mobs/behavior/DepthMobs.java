package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Depth mobs: rare, solitary hostiles of the deep, below y=0. A natural spawn there turns Depth one time in a
 * hundred, and never within four chunks of another. A Depth mob has twice the health of its kind, can still
 * be an elite or a boss, and drops echo shards and a keepsake of its kind. Each kind has a trick:
 * <ul>
 * <li>a Depth zombie gets back up twice before it falls for good;</li>
 * <li>a Depth skeleton archer slips away to a safer spot to shoot from when its target closes in or it is struck;</li>
 * <li>a Depth creeper sets the ground it blasts on fire;</li>
 * <li>a Depth spider's bite brings Darkness and a strong poison.</li>
 * </ul>
 */
public final class DepthMobs {
	public static final String TAG = "mod-stella-mobs.depth";
	public static final String REVIVE_TAG = "mod-stella-mobs.depth_revive";
	public static final String REVIVE_SECOND_TAG = "mod-stella-mobs.depth_revive_2";
	public static final double CHANCE = 0.01;
	/** Four chunks: no Depth mob spawns this close to another. */
	public static final double SOLITARY_RADIUS = 64.0;
	/** A Depth mob spawns only below this height. */
	public static final int MAX_Y = 0;
	public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "depth_health");
	public static final double HEALTH_BONUS = 1.0;
	public static final int REVIVES = 2;
	public static final float REVIVE_SHARE = 0.5F;
	public static final double SAFE_DISTANCE_MIN = 10.0;
	public static final double SAFE_DISTANCE_MAX = 16.0;
	public static final double TOO_CLOSE = 5.0;
	public static final int BLINK_COOLDOWN_TICKS = 80;
	public static final int RETRY_TICKS = 10;
	public static final int FIRE_RADIUS = 3;
	public static final int BITE_DARKNESS_TICKS = 100;
	public static final int BITE_POISON_TICKS = 100;
	public static final int BITE_POISON_AMPLIFIER = 1;

	/** Test seam: pins the chance; play always reads the constant. */
	private static volatile double chance = CHANCE;

	private DepthMobs() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(DepthMobs::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(DepthMobs::afterDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(DepthMobs::afterDamage);
	}

	public static void pinChance(final double value) {
		chance = value;
	}

	public static boolean isDepth(final LivingEntity mob) {
		return mob.entityTags().contains(TAG);
	}

	/** The kinds that have a Depth form: every hostile of the deep with a trick, and the witch and enderman plain. */
	public static boolean hasDepthForm(final Mob mob) {
		return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Creeper || mob instanceof Spider
			|| mob instanceof net.minecraft.world.entity.monster.Witch || mob instanceof net.minecraft.world.entity.monster.Enderman;
	}

	/** The spawn roll: a natural spawn below y=0, of a kind with a Depth form, far from any other Depth mob. */
	public static boolean rollAtSpawn(final Mob mob, final ServerLevel level, final @Nullable EntitySpawnReason reason) {
		if (reason != EntitySpawnReason.NATURAL || !Level.OVERWORLD.equals(level.dimension()) || mob.getBlockY() >= MAX_Y
			|| !hasDepthForm(mob) || isDepth(mob) || !rolls(mob.getRandom()) || !alone(level, mob)) {
			return false;
		}
		makeDepth(mob);
		return true;
	}

	/** One spawn's roll, at the shipped chance. */
	public static boolean rolls(final RandomSource random) {
		return random.nextDouble() < chance;
	}

	/** True when no Depth mob lives within the solitary radius. */
	public static boolean alone(final ServerLevel level, final Mob mob) {
		return level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(SOLITARY_RADIUS),
			other -> other != mob && other.isAlive() && isDepth(other)).isEmpty();
	}

	/** Turns a mob Depth: the tag, the name, twice the health, its revives, and its trick. */
	public static void makeDepth(final Mob mob) {
		if (isDepth(mob)) {
			return;
		}
		mob.addTag(TAG);
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getModifier(HEALTH_ID) == null) {
			health.addPermanentModifier(new AttributeModifier(HEALTH_ID, HEALTH_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			mob.setHealth(mob.getMaxHealth());
		}
		mob.setCustomName(Component.literal("Depth ").append(mob.getType().getDescription()).withStyle(ChatFormatting.DARK_AQUA));
		mob.setPersistenceRequired();
		if (mob instanceof Zombie) {
			mob.addTag(REVIVE_TAG);
			mob.addTag(REVIVE_SECOND_TAG);
		}
		install(mob);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=depthMobs DEPTH type={} y={} health={}", mob.getType(), mob.getBlockY(), mob.getMaxHealth());
	}

	/** The goals a Depth mob needs after it loads: the archer's slip. Idempotent. */
	public static void install(final Mob mob) {
		if (isDepth(mob) && mob instanceof AbstractSkeleton skeleton && BossAbilities.copiesOf(skeleton, SlipGoal.class) == 0) {
			skeleton.getGoalSelector().addGoal(BehaviorInstaller.WITCH_BLOCK_START, new SlipGoal(skeleton));
		}
	}

	/** How many revives a Depth zombie has left. */
	public static int revivesLeft(final Mob mob) {
		return (mob.entityTags().contains(REVIVE_TAG) ? 1 : 0) + (mob.entityTags().contains(REVIVE_SECOND_TAG) ? 1 : 0);
	}

	private static boolean allowDeath(final LivingEntity entity, final DamageSource source, final float amount) {
		if (!(entity instanceof Zombie zombie) || !isDepth(zombie) || revivesLeft(zombie) == 0 || !(zombie.level() instanceof ServerLevel level)) {
			return true;
		}
		zombie.removeTag(zombie.entityTags().contains(REVIVE_SECOND_TAG) ? REVIVE_SECOND_TAG : REVIVE_TAG);
		zombie.setHealth(zombie.getMaxHealth() * REVIVE_SHARE);
		zombie.clearFire();
		level.sendParticles(ParticleTypes.SCULK_SOUL, zombie.getX(), zombie.getY(0.5), zombie.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, zombie.getSoundSource(), 1.0F, 0.6F);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=depthMobs REVIVE zombie={} left={}", zombie.getUUID(), revivesLeft(zombie));
		return false;
	}

	private static void afterDeath(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Mob mob) || !isDepth(mob) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		for (ItemStack drop : drops(mob, mob.getRandom())) {
			mob.spawnAtLocation(level, drop);
		}
	}

	/** What a Depth mob leaves: echo shards, and a keepsake of its kind. */
	public static List<ItemStack> drops(final Mob mob, final RandomSource random) {
		ItemStack shards = new ItemStack(Items.ECHO_SHARD, 1 + random.nextInt(2));
		ItemStack keepsake;
		if (mob instanceof Zombie) {
			keepsake = new ItemStack(Items.GOLDEN_APPLE);
		} else if (mob instanceof AbstractSkeleton) {
			keepsake = new ItemStack(Items.SPECTRAL_ARROW, 8);
		} else if (mob instanceof Creeper) {
			keepsake = new ItemStack(Items.FIRE_CHARGE, 3);
		} else if (mob instanceof Spider) {
			keepsake = new ItemStack(Items.COBWEB, 4);
		} else {
			keepsake = new ItemStack(Items.AMETHYST_SHARD, 4);
		}
		return List.of(shards, keepsake);
	}

	// A Depth spider's bite brings Darkness and a strong poison.
	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (blocked || !(source.getDirectEntity() instanceof Spider spider) || !isDepth(spider) || victim == spider) {
			return;
		}
		victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BITE_DARKNESS_TICKS, 0), spider);
		victim.addEffect(new MobEffectInstance(MobEffects.POISON, BITE_POISON_TICKS, BITE_POISON_AMPLIFIER), spider);
	}

	/** Called as a creeper's blast ends: a Depth creeper leaves the ground it blasted on fire. */
	public static void afterBlast(final Creeper creeper) {
		if (!isDepth(creeper) || !(creeper.level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		BlockPos center = creeper.blockPosition();
		int lit = 0;
		for (int dx = -FIRE_RADIUS; dx <= FIRE_RADIUS; dx++) {
			for (int dz = -FIRE_RADIUS; dz <= FIRE_RADIUS; dz++) {
				if (dx * dx + dz * dz > FIRE_RADIUS * FIRE_RADIUS) {
					continue;
				}
				for (int dy = 2; dy >= -2; dy--) {
					BlockPos spot = center.offset(dx, dy, dz);
					if (level.getBlockState(spot).isAir() && level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
						level.setBlockAndUpdate(spot, BaseFireBlock.getState(level, spot));
						lit++;
						break;
					}
				}
			}
		}
		StellaMobs.LOGGER.info("[BEHAVIOR] test=depthMobs BLAST creeper={} fires={}", creeper.getUUID(), lit);
	}

	/** A Depth archer slips to a safer spot, farther from its target, when the target closes in or it is struck. Flagless. */
	public static final class SlipGoal extends Goal {
		private final AbstractSkeleton skeleton;
		private int cooldown;

		public SlipGoal(final AbstractSkeleton skeleton) {
			this.skeleton = skeleton;
		}

		@Override
		public boolean canUse() {
			if (this.cooldown > 0) {
				this.cooldown--;
				return false;
			}
			LivingEntity target = this.skeleton.getTarget();
			return target != null && target.isAlive() && this.skeleton.isHolding(Items.BOW)
				&& (this.skeleton.distanceToSqr(target) < TOO_CLOSE * TOO_CLOSE || this.skeleton.hurtTime > 0);
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			LivingEntity target = this.skeleton.getTarget();
			if (target != null && Blink.around(this.skeleton, target, SAFE_DISTANCE_MIN, SAFE_DISTANCE_MAX)) {
				this.cooldown = BLINK_COOLDOWN_TICKS;
				StellaMobs.LOGGER.info("[BEHAVIOR] test=depthMobs SLIP skeleton={} distance={}", this.skeleton.getUUID(), this.skeleton.distanceTo(target));
			} else {
				// No safe spot this time: look again shortly.
				this.cooldown = RETRY_TICKS;
				StellaMobs.LOGGER.info("[BEHAVIOR] test=depthMobs SLIP_NO_SPOT skeleton={}", this.skeleton.getUUID());
			}
		}
	}
}
