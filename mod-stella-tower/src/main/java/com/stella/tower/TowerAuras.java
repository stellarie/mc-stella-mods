package com.stella.tower;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What an element does for the tower mob that carries it. Ice chills the players' side around it, Dark
 * hardens with every blow it takes, Holy mends its kin; Fire, Water, Wind and Void mark what they hit, and
 * Lightning quickens on the hunt. Every effect is modest, and every one is shown with particles.
 * <p>
 * The pulse runs every {@link #PULSE_TICKS} ticks over occupied floors only, never over the whole level.
 */
public final class TowerAuras {
	public static final int PULSE_TICKS = 10;

	public static final double ICE_RADIUS = 4.0;
	/** Slowness I (amplifier 0), refreshed each pulse, so it lapses soon after leaving the mist. */
	public static final int ICE_SLOW_AMPLIFIER = 0;
	public static final int ICE_SLOW_TICKS = 30;
	/** The spells mod's tag for what its casters summon; a string, so the two mods need not know each other. */
	public static final String SUMMONED_TAG = "mod-stella-spells.summoned";

	public static final int DARK_STACK_CAP = 10;
	public static final double DARK_ARMOR_PER_STACK = 1.0;
	/** A stack is kept this long after the last hit, then one is lost every {@link #DARK_DECAY_TICKS}. */
	public static final int DARK_HOLD_TICKS = 100;
	public static final int DARK_DECAY_TICKS = 20;
	public static final Identifier DARK_ARMOR_ID = StellaTower.id("dark_armor");

	public static final int HOLY_INTERVAL_TICKS = 40;
	public static final double HOLY_RADIUS = 6.0;
	public static final double HOLY_HEAL_SHARE = 0.02;

	public static final float FIRE_SECONDS = 3.0F;
	public static final double WATER_KNOCKBACK = 0.4;
	public static final double WIND_LIFT = 0.3;
	public static final double LIGHTNING_SPEED_BONUS = 0.10;
	public static final Identifier LIGHTNING_SPEED_ID = StellaTower.id("lightning_speed");
	public static final int VOID_DARKNESS_TICKS = 60;

	/** Seen by tests only: each particle a pulse or a hit sends, with the entity it is sent at and its count. Null in play. */
	@FunctionalInterface
	public interface ParticleSink {
		void sent(LivingEntity at, ParticleOptions particle, int count);
	}

	public static volatile @Nullable ParticleSink particleSink;

	/** A Dark mob's armor: the stacks it stood on at its last hit, and when that was. */
	private record Dark(int stacks, int lastHit) {
	}

	private static final AttachmentType<Dark> DARK = AttachmentRegistry.create(StellaTower.id("dark_stacks"));

	private TowerAuras() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerAuras::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// A hit that lands marks its target even when absorption hearts soak it whole; only a shield stops it.
			// Dark hardens by damage taken, so a hit that cost nothing adds no stack.
			if (taken > 0.0F) {
				struck(level, entity, now(level));
			}
			if (source.getEntity() instanceof LivingEntity attacker && attacker != entity && !TowerBuffs.reflecting()) {
				strike(level, attacker, entity, source);
			}
		});
	}

	/** The server's own tick count: the game's clock barely moves in the test level, and the server's never stops. */
	public static int now(final ServerLevel level) {
		return level.getServer().getTickCount();
	}

	public static @Nullable TowerElement elementOf(final LivingEntity entity) {
		TowerMobs.State state = TowerMobs.stateOf(entity);
		return state == null || entity instanceof Ghostly ? null : state.element();
	}

	// ---- the hit ----

	/** A hit on a Dark mob: one more stack, on top of what it still holds. */
	static void struck(final ServerLevel level, final LivingEntity victim, final int now) {
		if (elementOf(victim) != TowerElement.DARK) {
			return;
		}
		int stacks = Math.min(DARK_STACK_CAP, darkStacks(victim, now) + 1);
		victim.setAttached(DARK, new Dark(stacks, now));
		applyDark(victim, stacks);
		particles(level, ParticleTypes.SQUID_INK, victim, Math.min(stacks, 5));
	}

	/** What a blow from an elemental tower mob leaves on its target. */
	static void strike(final ServerLevel level, final LivingEntity attacker, final LivingEntity target, final DamageSource source) {
		TowerElement element = elementOf(attacker);
		if (element == null || !target.isAlive()) {
			return;
		}
		switch (element) {
			case FIRE -> {
				target.igniteForSeconds(FIRE_SECONDS);
				particles(level, ParticleTypes.FLAME, target, 6);
			}
			case WATER -> {
				Vec3 away = target.position().subtract(attacker.position());
				Vec3 flat = new Vec3(away.x, 0.0, away.z);
				if (flat.lengthSqr() > 1.0E-6) {
					Vec3 push = flat.normalize().scale(WATER_KNOCKBACK);
					target.push(push.x, 0.0, push.z);
					target.needsSync = true;
				}
				particles(level, ParticleTypes.DRIPPING_WATER, target, 6);
			}
			case WIND -> {
				target.push(0.0, WIND_LIFT, 0.0);
				target.needsSync = true;
				particles(level, ParticleTypes.POOF, target, 6);
			}
			case VOID -> {
				target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, VOID_DARKNESS_TICKS, 0, true, true));
				particles(level, ParticleTypes.PORTAL, target, 10);
			}
			default -> {
			}
		}
	}

	// ---- the pulse ----

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % PULSE_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		Set<Integer> occupied = new HashSet<>();
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor > 0 && !player.isSpectator()) {
				occupied.add(floor);
			}
		}
		int now = now(tower);
		for (int floor : occupied) {
			pulse(tower, floor, now);
		}
	}

	/** One pulse of one floor, at the given tick: every element's ambient work. */
	public static void pulse(final ServerLevel tower, final int floor, final int now) {
		List<Mob> mobs = tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor), mob -> mob.isAlive() && elementOf(mob) != null);
		for (Mob mob : mobs) {
			switch (elementOf(mob)) {
				case ICE -> chill(tower, floor, mob);
				case DARK -> refreshDark(tower, mob, now);
				case HOLY -> {
					if (now % HOLY_INTERVAL_TICKS == 0) {
						mend(tower, floor, mob);
					}
				}
				case LIGHTNING -> quicken(tower, mob);
				default -> {
				}
			}
		}
	}

	/** Whether the mob stands on the players' side, and so feels the cold. */
	public static boolean chilled(final LivingEntity entity, final int floor) {
		if (!entity.isAlive()) {
			return false;
		}
		if (entity instanceof Ghostly) {
			return true;
		}
		if (entity instanceof Player player) {
			return !player.isSpectator() && !TowerSafety.sheltered(player);
		}
		if (TowerMobs.stateOf(entity) != null) {
			return false;
		}
		return entity instanceof OwnableEntity owned && owned.getOwner() != null
			|| entity.entityTags().contains(SUMMONED_TAG)
			|| entity instanceof IronGolem golem && golem.isPlayerCreated();
	}

	static void chill(final ServerLevel tower, final int floor, final Mob source) {
		AABB box = source.getBoundingBox().inflate(ICE_RADIUS);
		List<LivingEntity> targets = tower.getEntitiesOfClass(LivingEntity.class, box,
			entity -> entity.distanceToSqr(source) <= ICE_RADIUS * ICE_RADIUS && chilled(entity, floor));
		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ICE_SLOW_TICKS, ICE_SLOW_AMPLIFIER, true, false));
		}
		particles(tower, ParticleTypes.SNOWFLAKE, source, 6);
		particles(tower, ParticleTypes.CLOUD, source, 2);
	}

	// ---- dark ----

	/** The stacks a Dark mob holds at the given tick: all of them for the hold, then one every decay step. */
	public static int darkStacks(final LivingEntity mob, final int now) {
		Dark dark = mob.getAttached(DARK);
		if (dark == null) {
			return 0;
		}
		int idle = Math.max(0, now - dark.lastHit());
		int lost = idle < DARK_HOLD_TICKS ? 0 : (idle - DARK_HOLD_TICKS) / DARK_DECAY_TICKS + 1;
		return Math.max(0, dark.stacks() - lost);
	}

	/** Brings a Dark mob's armor to what it should hold at the given tick. */
	static void refreshDark(final ServerLevel tower, final LivingEntity mob, final int now) {
		int stacks = darkStacks(mob, now);
		applyDark(mob, stacks);
		if (stacks > 0) {
			particles(tower, ParticleTypes.SQUID_INK, mob, Math.min(stacks, 5));
		}
	}

	private static void applyDark(final LivingEntity mob, final int stacks) {
		AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}
		AttributeModifier held = armor.getModifier(DARK_ARMOR_ID);
		double want = stacks * DARK_ARMOR_PER_STACK;
		if (held != null && held.amount() == want) {
			return;
		}
		armor.removeModifier(DARK_ARMOR_ID);
		if (stacks > 0) {
			armor.addTransientModifier(new AttributeModifier(DARK_ARMOR_ID, want, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ---- holy ----

	/** Mends the Holy mob and every hurt tower mob within reach. */
	static void mend(final ServerLevel tower, final int floor, final Mob healer) {
		AABB box = healer.getBoundingBox().inflate(HOLY_RADIUS);
		List<LivingEntity> hurt = new ArrayList<>(tower.getEntitiesOfClass(LivingEntity.class, box,
			entity -> entity.isAlive() && entity.getHealth() < entity.getMaxHealth() && entity.distanceToSqr(healer) <= HOLY_RADIUS * HOLY_RADIUS
				&& TowerMobs.stateOf(entity) != null && !(entity instanceof Ghostly)));
		for (LivingEntity target : hurt) {
			target.heal((float) (target.getMaxHealth() * HOLY_HEAL_SHARE));
			particles(tower, ParticleTypes.END_ROD, target, 3);
			particles(tower, ParticleTypes.WAX_ON, target, 2);
		}
	}

	// ---- lightning ----

	/** A Lightning mob runs a tenth faster while it has something to hunt, and at its own pace otherwise. */
	static void quicken(final ServerLevel tower, final Mob mob) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean hunting = mob.getTarget() != null;
		boolean held = speed.getModifier(LIGHTNING_SPEED_ID) != null;
		if (hunting && !held) {
			speed.addTransientModifier(new AttributeModifier(LIGHTNING_SPEED_ID, LIGHTNING_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!hunting && held) {
			speed.removeModifier(LIGHTNING_SPEED_ID);
		}
		if (hunting) {
			particles(tower, ParticleTypes.ELECTRIC_SPARK, mob, 3);
		}
	}

	// ---- particles ----

	private static void particles(final ServerLevel level, final ParticleOptions particle, final LivingEntity at, final int count) {
		level.sendParticles(particle, at.getX(), at.getY(0.5), at.getZ(), count, 0.3, 0.4, 0.3, 0.02);
		ParticleSink sink = particleSink;
		if (sink != null) {
			sink.sent(at, particle, count);
		}
	}
}
