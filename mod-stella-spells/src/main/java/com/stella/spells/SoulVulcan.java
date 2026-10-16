package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Soul Vulcan: for a minute, a staff bolt that hits also fires five more bolts. They fly out wide, then curve
 * onto the target and the foes around it. Each lands as a staff hit and may trigger the staff's imbued spells,
 * under the charge of the swing that fired them.
 */
final class SoulVulcan {
	static final int EXTRA_BOLTS = 5;
	/** How far from the target a foe still draws a bolt. */
	static final double SPLASH_RADIUS = 6.0;
	static final double SPEED = 1.4;
	/** The share of the new heading that comes from the target each tick. */
	static final double HOMING = 0.35;
	/** Angle between the look line and a bolt's first heading. */
	static final double LAUNCH_ANGLE = Math.toRadians(40.0);
	static final int MAX_AGE = 40;
	private static final double HIT_MARGIN = 0.5;
	private static final double RETARGET_RADIUS = 8.0;
	private static final Identifier BOLT_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "staff_bolt");

	private static final Map<UUID, Integer> ENDS_AT = new ConcurrentHashMap<>();
	private static final List<Bolt> BOLTS = new CopyOnWriteArrayList<>();

	private static final class Bolt {
		final ServerLevel level;
		final ServerPlayer owner;
		final ItemStack staff;
		final float amount;
		final boolean full;
		final boolean certain;
		LivingEntity target;
		Vec3 pos;
		Vec3 velocity;
		int age;

		Bolt(
			final ServerLevel level, final ServerPlayer owner, final ItemStack staff, final LivingEntity target, final float amount,
			final boolean full, final boolean certain, final Vec3 pos, final Vec3 velocity
		) {
			this.level = level;
			this.owner = owner;
			this.staff = staff;
			this.target = target;
			this.amount = amount;
			this.full = full;
			this.certain = certain;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	private SoulVulcan() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SoulVulcan::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ENDS_AT.clear();
			BOLTS.clear();
		});
	}

	static boolean apply(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (!StaffPower.isStaff(caster.getMainHandItem())) {
			caster.sendSystemMessage(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".needs_staff"), true);
			return false;
		}
		int now = caster.level().getServer().getTickCount();
		ENDS_AT.put(caster.getUUID(), now + SpellNumbers.SOUL_VULCAN.durationTicks(modifiers));
		return true;
	}

	/** Test seam: bolts still in flight. */
	static int pending() {
		return BOLTS.size();
	}

	static boolean active(final ServerPlayer player) {
		Integer endsAt = ENDS_AT.get(player.getUUID());
		return endsAt != null && player.level().getServer().getTickCount() < endsAt;
	}

	/** A staff bolt hit {@code target}: launch the extra bolts when the spell runs. */
	static void follow(
		final ServerPlayer player, final ItemStack staff, final LivingEntity target, final float amount,
		final boolean full, final boolean certain
	) {
		if (!active(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		List<LivingEntity> targets = new ArrayList<>();
		targets.add(target);
		AABB near = target.getBoundingBox().inflate(SPLASH_RADIUS);
		level.getEntitiesOfClass(LivingEntity.class, near, e -> e != target && e.isAlive() && Friends.isFoe(e)
				&& !StaffBolt.friendly(player, e) && e.distanceToSqr(target) <= SPLASH_RADIUS * SPLASH_RADIUS)
			.stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(target)))
			.limit(EXTRA_BOLTS - 1).forEach(targets::add);

		Vec3 look = player.getLookAngle();
		Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 up = side.cross(look).normalize();
		Vec3 from = player.getEyePosition().add(look.scale(0.5));
		for (int i = 0; i < EXTRA_BOLTS; i++) {
			double turn = Math.PI * 2.0 * i / EXTRA_BOLTS;
			Vec3 heading = look.scale(Math.cos(LAUNCH_ANGLE))
				.add(side.scale(Math.cos(turn) * Math.sin(LAUNCH_ANGLE)))
				.add(up.scale(Math.sin(turn) * Math.sin(LAUNCH_ANGLE)));
			BOLTS.add(new Bolt(level, player, staff, targets.get(i % targets.size()), amount, full, certain, from, heading.scale(SPEED)));
		}
	}

	private static void tick(final MinecraftServer server) {
		for (Bolt bolt : BOLTS) {
			if (bolt.level.getServer() != server || !bolt.owner.isAlive() || bolt.owner.isRemoved() || ++bolt.age > MAX_AGE) {
				BOLTS.remove(bolt);
				continue;
			}
			if (!bolt.target.isAlive()) {
				bolt.target = nearestFoe(bolt);
				if (bolt.target == null) {
					BOLTS.remove(bolt);
					continue;
				}
			}
			Vec3 aim = bolt.target.getBoundingBox().getCenter().subtract(bolt.pos);
			if (aim.lengthSqr() > 1.0e-6) {
				Vec3 steered = bolt.velocity.scale(1.0 - HOMING).add(aim.normalize().scale(SPEED * HOMING));
				bolt.velocity = steered.normalize().scale(SPEED);
			}
			Vec3 next = bolt.pos.add(bolt.velocity);
			AABB box = bolt.target.getBoundingBox().inflate(HIT_MARGIN);
			boolean hit = box.contains(next) || box.clip(bolt.pos, next).isPresent();
			SpellVisuals.particle(bolt.level, bolt.owner, BOLT_ID, ParticleTypes.SOUL_FIRE_FLAME, next, 1, 0.03, 0.0);
			bolt.pos = next;
			if (hit) {
				BOLTS.remove(bolt);
				land(bolt);
			}
		}
	}

	private static LivingEntity nearestFoe(final Bolt bolt) {
		AABB near = new AABB(bolt.pos, bolt.pos).inflate(RETARGET_RADIUS);
		return bolt.level.getEntitiesOfClass(LivingEntity.class, near,
				e -> e.isAlive() && Friends.isFoe(e) && !StaffBolt.friendly(bolt.owner, e)).stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(bolt.pos))).orElse(null);
	}

	private static void land(final Bolt bolt) {
		LivingEntity victim = bolt.target;
		victim.damageCooldownTime = 0;
		if (victim.hurtServer(bolt.level, SpellCasting.spellSource(bolt.owner), bolt.amount)) {
			StaffBolt.applyWeaponEnchants(bolt.level, bolt.owner, bolt.staff, victim);
		}
		SpellImbue.onStaffHit(bolt.owner, bolt.staff, victim, bolt.full, bolt.certain);
	}
}
