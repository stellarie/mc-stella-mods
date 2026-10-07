package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The stray, by what it holds and what it is.
 * <ul>
 * <li>An elite or boss archer is a solitary sniper: it comes with no escort, it shoots once every ten seconds
 * away to a new spot around a player who looks straight at it while it hunts them, and a blow sends it away
 * too, blinding the attacker for a second, at most once every two minutes.</li>
 * <li>A melee stray is fast and freezes the players it strikes. An elite or boss one, far from its target,
 * blinds the target for a second and appears beside it, at most once every thirty seconds.</li>
 * </ul>
 */
public final class StrayKit {
	public static final int SNIPER_INTERVAL_TICKS = 200;
	public static final int SNIPER_DRAW_TICKS = 20;
	public static final double SNIPER_RANGE = 32.0;
	public static final float SNIPER_POWER = 3.2F;
	/** Arrow damage scales with its speed, so this base lands at about ten at the sniper's power. */
	public static final double ELITE_SNIPER_DAMAGE = 3.0;
	public static final double BOSS_SNIPER_DAMAGE = 4.0;
	/** A straight arrow never falls, so one that hits nothing is removed after this long. */
	public static final int SNIPER_ARROW_LIFE_TICKS = 100;
	public static final int LOOK_BLINK_COOLDOWN_TICKS = 60;
	public static final double LOOK_BLINK_MIN = 10.0;
	public static final double LOOK_BLINK_MAX = 16.0;
	public static final int ESCAPE_COOLDOWN_TICKS = 2400;
	/** How far a stray slips after its own arrow leaves the bow, and the far end of that band. */
	public static final double SHOT_BLINK_MIN = 8.0;
	public static final double SHOT_BLINK_MAX = 16.0;
	public static final double ESCAPE_MIN = 12.0;
	public static final double ESCAPE_MAX = 20.0;
	public static final int BLINDNESS_TICKS = 20;
	/** A melee stray's blow, frozen solid for this much more. */
	public static final int MELEE_FREEZE_TICKS = 140;
	public static final double MELEE_SPEED_BONUS = 0.35;
	public static final double MELEE_FOLLOW_RANGE = 32.0;
	public static final double AMBUSH_DISTANCE = 10.0;
	public static final int AMBUSH_COOLDOWN_TICKS = 600;
	public static final String SNIPER_TAG = "mod-stella-mobs.stray_sniper";
	public static final String BOSS_SNIPER_TAG = "mod-stella-mobs.stray_sniper_boss";
	public static final Identifier FURY_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "stray_fury");
	public static final Identifier FURY_RANGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "stray_fury_range");

	public interface Recorder {
		default void blink(final Mob stray, final String reason) {
		}

		default void sniped(final Mob stray, final AbstractArrow arrow) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	private static final Map<Mob, Integer> LAST_ESCAPE = new WeakHashMap<>();
	private static final List<AbstractArrow> STRAIGHT = new ArrayList<>();

	private StrayKit() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StrayKit::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(StrayKit::expireArrows);
		// A sniper arrow that comes back with its chunk is tracked again, so it still expires.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.entityTags().contains(SNIPER_TAG)) {
				track(arrow);
			}
		});
	}

	private static void track(final AbstractArrow arrow) {
		synchronized (STRAIGHT) {
			if (!STRAIGHT.contains(arrow)) {
				STRAIGHT.add(arrow);
			}
		}
	}

	public static boolean isArcher(final Mob mob) {
		return mob instanceof Stray && mob.isHolding(Items.BOW);
	}

	public static boolean isElite(final Mob mob) {
		return Elite.isElite(mob) || Bosses.isBoss(mob);
	}

	/** An elite or boss stray archer comes alone. */
	public static boolean isSolitary(final Mob mob) {
		return isArcher(mob) && isElite(mob);
	}

	/** From the installer: the sniper's bow for an elite archer, the fury for a melee stray. */
	public static void install(final Mob mob) {
		if (!(mob instanceof Stray stray)) {
			return;
		}
		if (isArcher(stray)) {
			if (isElite(stray) && BossAbilities.copiesOf(stray, SniperGoal.class) == 0) {
				stray.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedBowAttackGoal);
				stray.getGoalSelector().addGoal(SkeletonVolleyGoal.ATTACK_PRIORITY, new SniperGoal(stray));
			}
			return;
		}
		modifier(stray, Attributes.MOVEMENT_SPEED, FURY_ID, MELEE_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		AttributeInstance range = stray.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null && range.getModifier(FURY_RANGE_ID) == null && range.getBaseValue() < MELEE_FOLLOW_RANGE) {
			range.addPermanentModifier(new AttributeModifier(FURY_RANGE_ID, MELEE_FOLLOW_RANGE - range.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
		}
		if (BossAbilities.copiesOf(stray, AmbushGoal.class) == 0) {
			stray.getGoalSelector().addGoal(BehaviorInstaller.WITCH_BLOCK_START, new AmbushGoal(stray));
		}
	}

	private static void modifier(final Mob mob, final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
		final Identifier id, final double amount, final AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && instance.getModifier(id) == null) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** True when a player looks straight at this mob, as an enderman reads a stare. */
	public static boolean isLookedAtBy(final Player player, final Entity mob) {
		Vec3 view = player.getViewVector(1.0F).normalize();
		Vec3 to = mob.getEyePosition().subtract(player.getEyePosition());
		double distance = to.length();
		if (distance < 1.0E-4) {
			return false;
		}
		return view.dot(to.normalize()) > 1.0 - 0.025 / distance && player.hasLineOfSight(mob);
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		// A melee stray's blow freezes a player solid.
		if (!blocked && victim instanceof Player && source.getDirectEntity() instanceof Stray stray && !isArcher(stray)) {
			victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), victim.getTicksRequiredToFreeze()) + MELEE_FREEZE_TICKS);
		}
		// A blow on an elite archer sends it away, once every two minutes.
		if (victim instanceof Stray stray && isSolitary(stray) && source.getEntity() instanceof LivingEntity attacker && attacker != stray
			&& stray.isAlive() && stray.level() instanceof ServerLevel level) {
			int now = level.getServer().getTickCount();
			synchronized (LAST_ESCAPE) {
				Integer last = LAST_ESCAPE.get(stray);
				if (last != null && now >= last && now - last < ESCAPE_COOLDOWN_TICKS) {
					return;
				}
				LAST_ESCAPE.put(stray, now);
			}
			attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0), stray);
			recorder.blink(stray, Blink.around(stray, attacker, ESCAPE_MIN, ESCAPE_MAX) ? "escape" : "escape-stuck");
		}
	}

	/** A shot leaves the stray somewhere else: it slips out to the band around its target at once. */
	public static void afterShot(final Mob stray, final Entity target) {
		recorder.blink(stray, Blink.around(stray, target, SHOT_BLINK_MIN, SHOT_BLINK_MAX) ? "shot" : "shot-stuck");
	}

	private static void expireArrows(final MinecraftServer server) {
		synchronized (STRAIGHT) {
			Iterator<AbstractArrow> it = STRAIGHT.iterator();
			while (it.hasNext()) {
				AbstractArrow arrow = it.next();
				if (arrow.isRemoved()) {
					it.remove();
				} else if (arrow.tickCount > SNIPER_ARROW_LIFE_TICKS) {
					arrow.discard();
					it.remove();
				}
			}
		}
	}

	/** The elite archer's bow: one heavy straight shot every ten seconds, and a blink from a player's stare. */
	public static class SniperGoal extends Goal {
		private final Stray stray;
		private int sinceShot = SNIPER_INTERVAL_TICKS - SNIPER_DRAW_TICKS;
		private int blinkCooldown;

		public SniperGoal(final Stray stray) {
			this.stray = stray;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.stray.getTarget();
			return target != null && target.isAlive() && this.stray.isHolding(Items.BOW);
		}

		@Override
		public void stop() {
			this.stray.stopUsingItem();
			this.stray.setAggressive(false);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.stray.getTarget();
			if (target == null) {
				return;
			}
			this.stray.setAggressive(true);
			this.sinceShot++;
			this.blinkCooldown = Math.max(0, this.blinkCooldown - 1);
			if (this.blinkCooldown == 0 && target instanceof Player player && isLookedAtBy(player, this.stray)) {
				this.blinkCooldown = LOOK_BLINK_COOLDOWN_TICKS;
				if (Blink.around(this.stray, player, LOOK_BLINK_MIN, LOOK_BLINK_MAX)) {
					recorder.blink(this.stray, "stare");
				}
				return;
			}
			this.stray.getLookControl().setLookAt(target, 30.0F, 30.0F);
			boolean inReach = this.stray.distanceToSqr(target) <= SNIPER_RANGE * SNIPER_RANGE && this.stray.getSensing().hasLineOfSight(target);
			if (!inReach) {
				this.stray.stopUsingItem();
				this.stray.getNavigation().moveTo(target, 1.0);
				return;
			}
			this.stray.getNavigation().stop();
			if (this.sinceShot >= SNIPER_INTERVAL_TICKS - SNIPER_DRAW_TICKS && !this.stray.isUsingItem()) {
				this.stray.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.stray, Items.BOW));
			}
			if (this.sinceShot >= SNIPER_INTERVAL_TICKS) {
				this.sinceShot = 0;
				this.stray.stopUsingItem();
				this.shoot(target);
			}
		}

		private void shoot(final LivingEntity target) {
			if (!(this.stray.level() instanceof ServerLevel level)) {
				return;
			}
			ItemStack bow = this.stray.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.stray, Items.BOW));
			ItemStack one = this.stray.getProjectile(bow).copyWithCount(1);
			Arrow arrow = new Arrow(level, this.stray, one, bow);
			boolean boss = Bosses.isBoss(this.stray);
			arrow.setNoGravity(true);
			arrow.setBaseDamage(boss ? BOSS_SNIPER_DAMAGE : ELITE_SNIPER_DAMAGE);
			arrow.addTag(SNIPER_TAG);
			if (boss) {
				arrow.addTag(BOSS_SNIPER_TAG);
			}
			Vec3 aim = target.getEyePosition().subtract(0.0, 0.3, 0.0).add(ShotLead.offset(this.stray, target, SNIPER_POWER)).subtract(arrow.position());
			Projectile.spawnProjectileUsingShoot(arrow, level, one, aim.x, aim.y, aim.z, SNIPER_POWER, 0.0F);
			track(arrow);
			this.stray.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.6F);
			recorder.sniped(this.stray, arrow);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=straySniper SHOT stray={} boss={} distance={}", this.stray.getUUID(), boss, this.stray.distanceTo(target));
		}
	}

	/** An elite melee stray, far from its target, blinds it and appears beside it. Flagless. */
	public static class AmbushGoal extends Goal {
		private final Stray stray;
		private int cooldown;

		public AmbushGoal(final Stray stray) {
			this.stray = stray;
		}

		@Override
		public boolean canUse() {
			if (this.cooldown > 0) {
				this.cooldown--;
				return false;
			}
			LivingEntity target = this.stray.getTarget();
			return isElite(this.stray) && !isArcher(this.stray) && target != null && target.isAlive()
				&& this.stray.distanceToSqr(target) > AMBUSH_DISTANCE * AMBUSH_DISTANCE;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			LivingEntity target = this.stray.getTarget();
			if (target == null) {
				return;
			}
			this.cooldown = AMBUSH_COOLDOWN_TICKS;
			target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0), this.stray);
			if (Blink.around(this.stray, target, 1.5, 3.0)) {
				recorder.blink(this.stray, "ambush");
			}
		}
	}
}
