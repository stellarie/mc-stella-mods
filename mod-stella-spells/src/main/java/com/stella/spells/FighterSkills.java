package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The fighter books: melee skills that swing whatever the caster carries. Every hit goes through
 * {@link #hit}, which reads the school's own stat through {@link RpgBridge#skillDamage}, so a fighter book
 * never reads Intelligence and a mage book never reads Strength.
 */
public final class FighterSkills {
	/** Cleave's arc: this many degrees either side of the caster's look. */
	static final double CLEAVE_HALF_ANGLE = 60.0;
	/** How many Bleeding levels an awakened Cleave can reach above the plain one, counting from amplifier 0. */
	static final int CLEAVE_BLEED_LEVELS = 2;
	/** The three-quarter point of a cone, in radians, before the arc's own half angle. */
	private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
	/** How far above the landing a Leap Slam or an Earthsplitter throws what it hits. */
	static final double LAUNCH_UP = 0.6;
	static final double EARTHSPLITTER_UP = 0.5;
	/** The half-width of the broken ground Earthsplitter hits along, in blocks. */
	static final double EARTHSPLITTER_WIDTH = 1.5;
	/** Charge's own reach, and the reach of the ground it stops on. */
	static final double CHARGE_REACH = 8.0;
	static final double CHARGE_WIDTH = 1.5;
	/** How long a Whirlwind waits between its three pulses, in ticks. */
	static final int WHIRLWIND_INTERVAL_TICKS = 20;
	static final int WHIRLWIND_PULSES = 3;
	static final double WHIRLWIND_PULL = 0.5;
	/** War Cry's and Iron Skin's reach and window. */
	static final double WAR_CRY_RADIUS = 8.0;
	static final int WAR_CRY_BUFF_TICKS = 200;
	/** Counter Stance's window, in ticks. */
	static final int COUNTER_WINDOW_TICKS = 30;
	/** Iron Skin's reflect: this share of a melee hit goes back to the attacker. */
	static final float IRON_SKIN_REFLECT = 0.2F;
	/** The entity tag that marks a holder whose Iron Skin or Counter Stance is awakened. */
	public static final String IRON_SKIN_AWAKENED_TAG = Spellbooks.MOD_ID + ".iron_skin_awakened";
	public static final String COUNTER_AWAKENED_TAG = Spellbooks.MOD_ID + ".counter_awakened";
	/** Execute's threshold: under this share of maximum health the hit trebles. */
	static final float EXECUTE_THRESHOLD = 0.3F;
	/** What a target at or under the threshold takes, and what a bleeding one takes on top. */
	static final float EXECUTE_LOW_FACTOR = 3.0F;
	static final float EXECUTE_BLEED_FACTOR = 2.0F;

	/** The tick each Counter Stance window ends on, by caster. */
	private static final Map<UUID, Integer> COUNTER_UNTIL = new ConcurrentHashMap<>();
	/** The tick each Iron Skin lasts until, by holder; the tag says whether that skin is awakened. */
	private static final Map<UUID, Integer> IRON_SKIN_UNTIL = new ConcurrentHashMap<>();
	/** Whirlwind pulses still owed, by caster, oldest first. */
	private static final Map<UUID, List<Pulse>> WHIRLWINDS = new ConcurrentHashMap<>();
	/** Whose melee answer is already reflecting, so a reflect cannot start another. */
	private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private FighterSkills() {
	}

	static void register() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(FighterSkills::allowDamage);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(FighterSkills::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	/** A new server's tick counter starts again at zero, so no window outlives the server it was set on. */
	static void clear() {
		COUNTER_UNTIL.clear();
		IRON_SKIN_UNTIL.clear();
		WHIRLWINDS.clear();
	}

	static int counterWindows() {
		return COUNTER_UNTIL.size();
	}

	// A player hit on a frozen or brittle mob shatters it; a fighter's own status is read from its effect.
	private static boolean melee(final net.minecraft.world.damagesource.DamageSource source) {
		return source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
			|| source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO)
			|| source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
			|| source.is(net.minecraft.world.damagesource.DamageTypes.SPEAR)
			|| source.is(net.minecraft.world.damagesource.DamageTypes.MACE_SMASH);
	}

	/**
	 * Counter Stance and the awakened Iron Skin read a blow before it lands. A stance answers one melee hit
	 * inside its window, or every one of them when it is awakened, and closes the window when its answer lands;
	 * an awakened skin answers every melee hit inside its own, with the share it sends back.
	 */
	private static boolean allowDamage(
		final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source, final float amount
	) {
		if (amount <= 0.0F || REFLECTING.get() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
			return true;
		}
		if (!melee(source)) {
			return true;
		}
		int now = victim.level().getServer().getTickCount();
		if (victim instanceof ServerPlayer player && countered(player, now)) {
			if (!player.entityTags().contains(COUNTER_AWAKENED_TAG)) {
				COUNTER_UNTIL.remove(player.getUUID());
			}
			answerBack(player, attacker);
			return false;
		}
		if (ironSkinAwakened(victim, now)) {
			reflect(victim, attacker, amount);
		}
		return true;
	}

	private static boolean countered(final ServerPlayer player, final int now) {
		Integer until = COUNTER_UNTIL.get(player.getUUID());
		return until != null && now < until;
	}

	private static boolean ironSkinAwakened(final LivingEntity holder, final int now) {
		Integer until = IRON_SKIN_UNTIL.get(holder.getUUID());
		return until != null && now < until && holder.entityTags().contains(IRON_SKIN_AWAKENED_TAG);
	}

	private static void answerBack(final ServerPlayer player, final LivingEntity attacker) {
		hitFor(player, attacker, SpellNumbers.COUNTER_STANCE.damage(), 1.0);
		SpellStatusApi.stagger(attacker, SpellNumbers.COUNTER_STANCE.extra().ticks(), player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	// The reflect never counts as a melee hit, so two Iron Skins cannot trade blows forever.
	private static void reflect(final LivingEntity holder, final LivingEntity attacker, final float amount) {
		float back = amount * IRON_SKIN_REFLECT;
		REFLECTING.set(Boolean.TRUE);
		try {
			attacker.hurtServer((ServerLevel) holder.level(), RpgBridge.skillSource(holder), back);
		} finally {
			REFLECTING.set(Boolean.FALSE);
		}
	}

	// A window that has run out is dropped on its own tick, so no map outlives its buff.
	private static void tick(final net.minecraft.server.MinecraftServer server) {
		int now = server.getTickCount();
		COUNTER_UNTIL.entrySet().removeIf(entry -> now >= entry.getValue());
		IRON_SKIN_UNTIL.entrySet().removeIf(entry -> now >= entry.getValue());
		pulseWhirlwinds(server, now);
	}

	private record Pulse(UUID caster, int at) {
	}

	private static void pulseWhirlwinds(final net.minecraft.server.MinecraftServer server, final int now) {
		for (Map.Entry<UUID, List<Pulse>> entry : List.copyOf(WHIRLWINDS.entrySet())) {
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
			List<Pulse> left = new ArrayList<>();
			for (Pulse pulse : entry.getValue()) {
				if (caster == null || !caster.isAlive()) {
					continue;
				}
				if (now < pulse.at()) {
					left.add(pulse);
					continue;
				}
				whirl(caster);
			}
			if (left.isEmpty()) {
				WHIRLWINDS.remove(entry.getKey());
			} else {
				WHIRLWINDS.put(entry.getKey(), left);
			}
		}
	}

	/** How many Whirlwind pulses this caster still owes; the test seam for the three-pulse sequence. */
	static int whirlwindPulses(final ServerPlayer caster) {
		List<Pulse> pulses = WHIRLWINDS.get(caster.getUUID());
		return pulses == null ? 0 : pulses.size();
	}

	// ---- the ten books ----

	/** Cleave: every foe in the arc in front of the caster, bleeding. An awakened cleave deepens the bleed. */
	static boolean applyCleave(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellNumbers numbers = SpellNumbers.CLEAVE;
		List<Mob> struck = foesInCone(caster, numbers.radius(modifiers), CLEAVE_HALF_ANGLE);
		for (Mob foe : struck) {
			hit(caster, foe, numbers.damage(), modifiers);
			bleed(caster, foe, numbers.durationTicks(modifiers), modifiers.awakened() ? CLEAVE_BLEED_LEVELS : 0);
		}
		return !struck.isEmpty();
	}

	private static void bleed(final ServerPlayer caster, final Mob foe, final int ticks, final int maxAmplifier) {
		if (maxAmplifier <= 0) {
			foe.removeEffect(SpellStatus.BLEEDING);
		}
		SpellStatusApi.bleed(foe, ticks, Math.max(0, maxAmplifier), caster);
	}

	/** Leap Slam: the caster lands beside the target and the landing throws everything around it. */
	static boolean applyLeapSlam(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		Vec3 landing = spotBeside(caster, target);
		if (landing == null) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.LEAP_SLAM;
		move(caster, landing, target.getYRot());
		boolean struck = false;
		for (Mob foe : foesAround(caster, numbers.radius(modifiers))) {
			struck = true;
			hit(caster, foe, numbers.damage(), modifiers);
			pushUp(caster, foe, LAUNCH_UP);
			if (modifiers.awakened()) {
				foe.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					MobEffects.SLOWNESS, numbers.extraTicks(modifiers), SpellNumbers.LEAP_SLAM.amplifier()
				), caster);
			}
		}
		return struck || true;
	}

	/** Whirlwind: three pulses twenty ticks apart, the later ones answering on the server tick. */
	static boolean applyWhirlwind(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		whirl(caster);
		List<Pulse> pulses = new ArrayList<>();
		int now = caster.level().getServer().getTickCount();
		for (int pulse = 1; pulse < WHIRLWIND_PULSES; pulse++) {
			pulses.add(new Pulse(caster.getUUID(), now + pulse * WHIRLWIND_INTERVAL_TICKS));
		}
		WHIRLWINDS.put(caster.getUUID(), pulses);
		return true;
	}

	// One Whirlwind pulse: everything around the caster takes the hit, and an awakened one drags them in.
	private static void whirl(final ServerPlayer caster) {
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(
			caster.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND),
			caster.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
			caster.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
		);
		modifiers = RpgBridge.grow(caster, "whirlwind", modifiers, School.FIGHTER);
		SpellNumbers numbers = SpellNumbers.WHIRLWIND;
		for (Mob foe : foesAround(caster, numbers.radius(modifiers))) {
			hit(caster, foe, numbers.damage(), modifiers);
			if (modifiers.awakened()) {
				pullToward(caster, foe, WHIRLWIND_PULL);
			}
		}
	}

	/** Shield Bash: a shield in either hand, a stagger, and an awakened wake behind the target. */
	static boolean applyShieldBash(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null || !carriesShield(caster)) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.SHIELD_BASH;
		hit(caster, target, numbers.damage(), modifiers);
		SpellStatusApi.stagger(target, numbers.durationTicks(modifiers), caster);
		if (modifiers.awakened()) {
			Vec3 behind = target.position().subtract(caster.position());
			double wake = numbers.radius(modifiers);
			for (Mob foe : foesAround(caster, numbers.radius(modifiers) * 2.0)) {
				if (foe == target || !behind(target, foe, behind, wake)) {
					continue;
				}
				SpellStatusApi.stagger(foe, numbers.durationTicks(modifiers), caster);
			}
		}
		return true;
	}

	static boolean carriesShield(final ServerPlayer caster) {
		return caster.getMainHandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
			|| caster.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
			|| caster.getMainHandItem().is(Items.SHIELD)
			|| caster.getOffhandItem().is(Items.SHIELD);
	}

	/** Charge: the caster runs to the last safe spot and takes everything on the way with it. */
	static boolean applyCharge(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		Vec3 look = caster.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
		if (horizontal.lengthSqr() < 1.0E-6) {
			return false;
		}
		horizontal = horizontal.normalize();
		Vec3 from = caster.position();
		Vec3 landing = lastSafeSpot(caster, horizontal, CHARGE_REACH);
		if (landing == null) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.CHARGE;
		move(caster, landing, caster.getYRot());
		boolean struck = false;
		Mob first = null;
		for (Mob foe : foesNearLine(caster, from, landing, CHARGE_WIDTH)) {
			if (first == null) {
				first = foe;
			}
			struck = true;
			hit(caster, foe, numbers.damage(), modifiers);
			shoveSideways(caster, foe, horizontal);
		}
		if (modifiers.awakened() && first != null) {
			SpellStatusApi.stagger(first, numbers.durationTicks(modifiers), caster);
		}
		return struck || true;
	}

	/** Execute: a heavier blow the weaker the target is, and a heavier one again while it bleeds. */
	static boolean applyExecute(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null) {
			return false;
		}
		SpellNumbers numbers = SpellNumbers.EXECUTE;
		float factor = 1.0F;
		boolean low = target.getHealth() <= target.getMaxHealth() * EXECUTE_THRESHOLD;
		boolean bleeding = target.hasEffect(SpellStatus.BLEEDING);
		if (low) {
			factor *= EXECUTE_LOW_FACTOR;
		}
		if (bleeding) {
			factor *= EXECUTE_BLEED_FACTOR;
		}
		hitFor(caster, target, numbers.damage() * factor, modifiers);
		if (modifiers.awakened() && !target.isAlive()) {
			SpellCasting.skipCooldown();
		}
		return true;
	}

	/** War Cry: the caster's foes turn on it, and its side is heartened. */
	static boolean applyWarCry(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		for (Mob foe : foesAround(caster, WAR_CRY_RADIUS)) {
			foe.setTarget(caster);
		}
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, WAR_CRY_BUFF_TICKS, SpellNumbers.WAR_CRY.amplifier()), caster);
		if (modifiers.awakened()) {
			for (LivingEntity ally : alliesAround(caster, WAR_CRY_RADIUS)) {
				ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WAR_CRY_BUFF_TICKS, SpellNumbers.WAR_CRY.amplifier()), caster);
			}
		}
		caster.level().playSound(null, caster.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.9F, 0.8F);
		return true;
	}

	/** Counter Stance: the first melee blow inside the window is turned aside and answered. */
	static boolean applyCounterStance(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		int now = caster.level().getServer().getTickCount();
		COUNTER_UNTIL.put(caster.getUUID(), now + SpellNumbers.COUNTER_STANCE.duration().ticks());
		if (modifiers.awakened()) {
			caster.addTag(COUNTER_AWAKENED_TAG);
		} else {
			caster.removeTag(COUNTER_AWAKENED_TAG);
		}
		caster.level().playSound(null, caster.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6F, 1.4F);
		return true;
	}

	/** Iron Skin: Resistance for half an hour, and an awakened skin answers the blows it takes. */
	static boolean applyIronSkin(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellNumbers numbers = SpellNumbers.IRON_SKIN;
		int ticks = numbers.duration().ticks();
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, numbers.amplifier()), caster);
		int now = caster.level().getServer().getTickCount();
		IRON_SKIN_UNTIL.put(caster.getUUID(), now + ticks);
		if (modifiers.awakened()) {
			caster.addTag(IRON_SKIN_AWAKENED_TAG);
		} else {
			caster.removeTag(IRON_SKIN_AWAKENED_TAG);
		}
		return true;
	}

	/** Earthsplitter: a line of broken ground in front of the caster, or three lines when it is awakened. */
	static boolean applyEarthsplitter(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SpellNumbers numbers = SpellNumbers.EARTHSPLITTER;
		List<Double> angles = modifiers.awakened() ? List.of(-20.0, 0.0, 20.0) : List.of(0.0);
		List<Mob> struck = new ArrayList<>();
		for (double degrees : angles) {
			Vec3 direction = rotate(caster.getLookAngle(), degrees);
			Vec3 end = caster.position().add(direction.x * numbers.radius(modifiers), 0.0, direction.z * numbers.radius(modifiers));
			for (Mob foe : foesNearLine(caster, caster.position(), end, EARTHSPLITTER_WIDTH)) {
				if (!struck.contains(foe)) {
					struck.add(foe);
				}
			}
		}
		for (Mob foe : struck) {
			hit(caster, foe, numbers.damage(), modifiers);
			pushUp(caster, foe, EARTHSPLITTER_UP);
		}
		return !struck.isEmpty();
	}

	// ---- shared helpers ----

	/** A hit: the target's own hurt cooldown is set aside so the skill's damage is not swallowed. */
	static void hit(final ServerPlayer caster, final Mob target, final float flat, final SpellModifiers.CastModifiers modifiers) {
		hitFor(caster, target, flat, modifiers);
	}

	private static void hitFor(final ServerPlayer caster, final LivingEntity target, final float flat, final SpellModifiers.CastModifiers modifiers) {
		hitFor(caster, target, flat, modifiers.powerMultiplier());
	}

	private static void hitFor(final ServerPlayer caster, final LivingEntity target, final float flat, final double power) {
		if (target.damageCooldownTime > 0) {
			target.damageCooldownTime = 0;
		}
		target.hurtServer((ServerLevel) caster.level(), RpgBridge.skillSource(caster), RpgBridge.skillDamage(caster, School.FIGHTER, flat, power));
	}

	/** Every foe inside a cone of this half angle, within this reach, in front of the caster. */
	static List<Mob> foesInCone(final ServerPlayer caster, final double range, final double degrees) {
		Vec3 look = new Vec3(caster.getLookAngle().x, 0.0, caster.getLookAngle().z);
		if (look.lengthSqr() < 1.0E-6) {
			return List.of();
		}
		look = look.normalize();
		double cosine = Math.cos(degrees * DEGREES_TO_RADIANS);
		List<Mob> struck = new ArrayList<>();
		for (Mob foe : foesAround(caster, range)) {
			Vec3 toward = new Vec3(foe.getX() - caster.getX(), 0.0, foe.getZ() - caster.getZ());
			if (toward.lengthSqr() < 1.0E-6 || toward.normalize().dot(look) >= cosine) {
				struck.add(foe);
			}
		}
		return struck;
	}

	/** Every foe within this reach of the caster. */
	static List<Mob> foesAround(final ServerPlayer caster, final double range) {
		return caster.level().getEntitiesOfClass(
			Mob.class,
			caster.getBoundingBox().inflate(range),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
				&& candidate.position().distanceToSqr(caster.position()) <= range * range
		);
	}

	/** Every foe within this width of the segment the caster travelled. */
	static List<Mob> foesNearLine(final ServerPlayer caster, final Vec3 from, final Vec3 to, final double width) {
		List<Mob> struck = new ArrayList<>();
		for (Mob foe : foesAround(caster, from.distanceTo(to) + width)) {
			if (distanceToSegment(foe.position(), from, to) <= width) {
				struck.add(foe);
			}
		}
		return struck;
	}

	private static double distanceToSegment(final Vec3 point, final Vec3 from, final Vec3 to) {
		Vec3 span = to.subtract(from);
		double lengthSquared = span.lengthSqr();
		if (lengthSquared < 1.0E-9) {
			return point.distanceTo(from);
		}
		double along = Math.max(0.0, Math.min(1.0, point.subtract(from).dot(span) / lengthSquared));
		return point.distanceTo(from.add(span.scale(along)));
	}

	/** The farthest standable spot along a direction before a solid block, or null when the first step is blocked. */
	static @Nullable Vec3 lastSafeSpot(final ServerPlayer caster, final Vec3 direction, final double blocks) {
		Vec3 from = caster.position();
		for (double step = blocks; step >= 1.0; step -= 1.0) {
			Vec3 candidate = new Vec3(from.x + direction.x * step, from.y, from.z + direction.z * step);
			if (flightPathClear(caster, from, candidate) && standsAt(caster, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean flightPathClear(final ServerPlayer caster, final Vec3 from, final Vec3 to) {
		ServerLevel level = (ServerLevel) caster.level();
		BlockHitResult hit = level.clip(new ClipContext(
			from.add(0.0, caster.getBbHeight() / 2.0, 0.0), to.add(0.0, caster.getBbHeight() / 2.0, 0.0),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster
		));
		return hit.getType() != HitResult.Type.BLOCK;
	}

	private static boolean standsAt(final ServerPlayer caster, final Vec3 spot) {
		ServerLevel level = (ServerLevel) caster.level();
		AABB moved = caster.getBoundingBox().move(spot.subtract(caster.position()));
		AABB underFoot = new AABB(moved.minX, spot.y - 0.05, moved.minZ, moved.maxX, spot.y, moved.maxZ);
		VoxelShape floor = level.getBlockState(BlockPos.containing(spot.x, spot.y - 0.05, spot.z))
			.getCollisionShape(level, BlockPos.containing(spot.x, spot.y - 0.05, spot.z));
		return !floor.isEmpty() && level.noCollision(caster, moved);
	}

	/** A free spot beside the target, facing the way the target faces, or null when there is none. */
	static @Nullable Vec3 spotBeside(final ServerPlayer caster, final Mob target) {
		Vec3 from = target.position();
		double reach = target.getBbWidth() / 2.0 + caster.getBbWidth() / 2.0 + 0.5;
		for (double degrees = 0.0; degrees < 360.0; degrees += 45.0) {
			Vec3 direction = rotate(new Vec3(1.0, 0.0, 0.0), degrees);
			Vec3 candidate = new Vec3(from.x + direction.x * reach, target.getY(), from.z + direction.z * reach);
			if (standsAt(caster, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static Vec3 rotate(final Vec3 direction, final double degrees) {
		double radians = degrees * DEGREES_TO_RADIANS;
		double cosine = Math.cos(radians);
		double sine = Math.sin(radians);
		return new Vec3(direction.x * cosine - direction.z * sine, direction.y, direction.x * sine + direction.z * cosine);
	}

	private static void move(final ServerPlayer caster, final Vec3 spot, final float yaw) {
		caster.teleportTo(spot.x, spot.y, spot.z);
		caster.setYRot(yaw);
		caster.setYHeadRot(yaw);
		caster.resetFallDistance();
	}

	private static void pushUp(final ServerPlayer caster, final Mob foe, final double up) {
		Vec3 away = foe.position().subtract(caster.position());
		Vec3 horizontal = new Vec3(away.x, 0.0, away.z);
		if (horizontal.lengthSqr() > 1.0E-6) {
			horizontal = horizontal.normalize();
		}
		foe.setDeltaMovement(horizontal.x * 0.2, up, horizontal.z * 0.2);
	}

	private static void shoveSideways(final ServerPlayer caster, final Mob foe, final Vec3 path) {
		Vec3 side = new Vec3(-path.z, 0.0, path.x);
		foe.setDeltaMovement(side.x * 0.6, 0.2, side.z * 0.6);
	}

	private static void pullToward(final ServerPlayer caster, final Mob foe, final double blocks) {
		Vec3 toward = caster.position().subtract(foe.position());
		Vec3 horizontal = new Vec3(toward.x, 0.0, toward.z);
		if (horizontal.lengthSqr() < 1.0E-6) {
			return;
		}
		horizontal = horizontal.normalize();
		Vec3 next = foe.position().add(horizontal.scale(Math.min(blocks, foe.position().distanceTo(caster.position()))));
		foe.teleportTo(next.x, next.y, next.z);
	}

	/** True when the foe stands on the far side of the target from the caster, within this wake. */
	static boolean behind(final Mob target, final Mob foe, final Vec3 away, final double wake) {
		Vec3 forward = new Vec3(away.x, 0.0, away.z);
		if (forward.lengthSqr() < 1.0E-6) {
			return false;
		}
		Vec3 offset = new Vec3(foe.getX() - target.getX(), 0.0, foe.getZ() - target.getZ());
		return offset.lengthSqr() <= wake * wake && offset.normalize().dot(forward.normalize()) > 0.0;
	}

	/** The caster's side within this reach: other players, and anything the mods mark a friend. */
	static List<LivingEntity> alliesAround(final ServerPlayer caster, final double range) {
		List<LivingEntity> allies = new ArrayList<>(caster.level().getEntitiesOfClass(
			LivingEntity.class,
			caster.getBoundingBox().inflate(range),
			candidate -> candidate != caster && candidate.isAlive()
				&& (candidate instanceof Player || Friends.isFriend(candidate))
				&& candidate.position().distanceToSqr(caster.position()) <= range * range
		));
		allies.sort(Comparator.comparingDouble(ally -> ally.position().distanceToSqr(caster.position())));
		return allies;
	}

	/** The nearest foe a caster looks at within this reach, or null. */
	static @Nullable Mob rayFoe(final ServerPlayer caster, final Vec3 direction, final double range) {
		ServerLevel level = (ServerLevel) caster.level();
		Vec3 start = caster.getEyePosition();
		Vec3 end = start.add(direction.scale(range));
		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		double reach = blockHit.getType() == HitResult.Type.MISS ? range : start.distanceTo(blockHit.getLocation());
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			caster, start, end, caster.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0),
			candidate -> candidate instanceof Mob mob && mob.isAlive() && Friends.isFoe(mob), reach * reach
		);
		return entityHit != null && entityHit.getEntity() instanceof Mob mob ? mob : null;
	}

	/** Every foe on a ray, nearest first. */
	static List<Mob> rayFoes(final ServerPlayer caster, final Vec3 direction, final double range) {
		Vec3 start = caster.getEyePosition();
		Vec3 end = start.add(direction.scale(range));
		List<Mob> crossed = new ArrayList<>();
		for (Mob foe : caster.level().getEntitiesOfClass(
			Mob.class,
			caster.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate)
		)) {
			if (foe.getBoundingBox().inflate(foe.getPickRadius()).clip(start, end).isPresent()) {
				crossed.add(foe);
			}
		}
		crossed.sort(Comparator.comparingDouble(foe -> foe.position().distanceToSqr(caster.position())));
		return crossed;
	}

	/** The item a fighter swings, for a tooltip and for the report; empty when nothing is equipped. */
	static ItemStack weaponOf(final ServerPlayer caster) {
		return RpgBridge.skillWeapon(caster);
	}
}
