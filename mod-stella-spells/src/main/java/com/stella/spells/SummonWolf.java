package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Summon Wolf, a spellbook of its own that no scroll copies. It tames one wolf to the caster. The wolf guards
 * the caster, follows it, carries an aura and can be ridden (see {@link SummonedWolf}). Its health grows with the
 * caster overall RPG stats, and a base number stands when the RPG mod is absent. Casting again while it lives
 * calls it to the caster side and heals it a little, at no cooldown.
 */
public final class SummonWolf {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_wolf");
	/** Thirty seconds of rest between summons. */
	public static final int COOLDOWN_TICKS = 600;
	/** A tamed wolf health, and what each point of the caster six stats adds to it. */
	public static final double BASE_HEALTH = 40.0;
	public static final double HEALTH_PER_STAT = 0.5;
	/** The share of its own maximum health a living wolf recovers when its master casts the spell again. */
	public static final double RECALL_HEAL_FRACTION = 0.25;
	private static final int REFRESH_INTERVAL_TICKS = 20;
	/**
	 * The one wolf a master holds, saved with the master. A wolf left in an unloaded chunk cannot be called back, so
	 * a new cast raises another and moves the bond; the old one is retired when it loads again.
	 */
	public static final AttachmentType<UUID> BOND = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_wolf_bond"),
		builder -> builder.persistent(UUIDUtil.CODEC).copyOnDeath()
	);
	/** Each master wolf, by master id; the wolf own owner and the bond are the truth, this only finds it. */
	private static final Map<UUID, UUID> SUMMONED = new ConcurrentHashMap<>();

	private SummonWolf() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SUMMONED.clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof SummonedWolf wolf) || !(level instanceof ServerLevel)) {
				return;
			}
			if (stale(wolf)) {
				wolf.discard();
				return;
			}
			if (wolf.getOwnerReference() != null) {
				SUMMONED.put(wolf.getOwnerReference().getUUID(), wolf.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof SummonedWolf wolf && wolf.getOwnerReference() != null) {
				SUMMONED.remove(wolf.getOwnerReference().getUUID(), wolf.getUUID());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_INTERVAL_TICKS != 0) {
				return;
			}
			for (UUID masterId : List.copyOf(SUMMONED.keySet())) {
				SummonedWolf wolf = find(server, masterId);
				ServerPlayer master = server.getPlayerList().getPlayer(masterId);
				if (wolf == null || master == null) {
					continue;
				}
				if (wolf.level() != master.level()) {
					recall(wolf, master);
				} else {
					refreshHealth(wolf, master, false);
				}
			}
		});
	}

	/** The spell: a new wolf, or the old one called to the caster side and healed on the way. */
	static boolean apply(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		SummonedWolf existing = find(caster.level().getServer(), caster.getUUID());
		if (existing != null) {
			boolean recalled = recall(existing, caster);
			if (recalled) {
				existing.heal((float) (existing.getMaxHealth() * RECALL_HEAL_FRACTION));
			}
			return recalled;
		}
		SummonedWolf wolf = SpellEntities.SUMMONED_WOLF.create(caster.level(), EntitySpawnReason.MOB_SUMMONED);
		if (wolf == null) {
			return false;
		}
		wolf.addTag(SummonSkeleton.SUMMON_TAG);
		wolf.addTag(SummonSkeleton.OWNER_TAG_PREFIX + caster.getUUID());
		wolf.addTag(MobSpells.NO_BEHAVIOR_TAG);
		wolf.setPersistenceRequired();
		wolf.tame(caster);
		wolf.setCustomName(Component.literal(caster.getScoreboardName() + "'s Wolf"));
		refreshHealth(wolf, caster, true);
		Vec3 at = beside(caster);
		wolf.snapTo(at.x, at.y, at.z, caster.getYRot(), 0.0F);
		// The bond moves first: the load event fires inside the add, and must see this wolf as the one held.
		UUID previous = caster.getAttached(BOND);
		caster.setAttached(BOND, wolf.getUUID());
		if (!caster.level().addFreshEntity(wolf)) {
			caster.setAttached(BOND, previous);
			return false;
		}
		SUMMONED.put(caster.getUUID(), wolf.getUUID());
		CastHits.touch(caster);
		caster.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.8, at.z, 24, 0.4, 0.5, 0.4, 0.05);
		caster.level().playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, caster.getSoundSource(), 0.8F, 1.4F);
		Spellbooks.LOGGER.info("[SPELL] event=SUMMON_WOLF caster={} wolf={} health={}", caster.getUUID(), wolf.getUUID(), wolf.getMaxHealth());
		return true;
	}

	/** The living wolf of this master, wherever it is loaded; null for none. */
	public static @Nullable SummonedWolf find(final MinecraftServer server, final UUID master) {
		UUID id = SUMMONED.get(master);
		if (id == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof SummonedWolf wolf && wolf.isAlive()) {
				return wolf;
			}
		}
		return null;
	}

	/** The wolf maximum health for this master: the base alone without the RPG mod, else the base plus the stats. */
	public static double maxHealthFor(final ServerPlayer master) {
		double total = RpgBridge.statTotal(master);
		return Double.isFinite(total) && total > 0.0 ? BASE_HEALTH + HEALTH_PER_STAT * total : BASE_HEALTH;
	}

	// A wolf whose master, when online, is bonded to another; a master with no bond yet adopts this one.
	private static boolean stale(final SummonedWolf wolf) {
		if (!(wolf.getOwner() instanceof ServerPlayer master)) {
			return false;
		}
		UUID bond = master.getAttached(BOND);
		if (bond == null) {
			master.setAttached(BOND, wolf.getUUID());
			return false;
		}
		return !bond.equals(wolf.getUUID());
	}

	private static void refreshHealth(final SummonedWolf wolf, final ServerPlayer master, final boolean full) {
		AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		double base = maxHealthFor(master);
		if (!full && health.getBaseValue() == base) {
			return;
		}
		float oldMaximum = wolf.getMaxHealth();
		float oldHealth = wolf.getHealth();
		health.setBaseValue(base);
		float fraction = oldMaximum <= 0.0F ? 1.0F : oldHealth / oldMaximum;
		wolf.setHealth(full ? wolf.getMaxHealth() : Math.max(1.0F, Math.min(wolf.getMaxHealth(), fraction * wolf.getMaxHealth())));
	}

	private static boolean recall(final SummonedWolf wolf, final ServerPlayer caster) {
		Vec3 at = beside(caster);
		Entity moved = wolf.level() == caster.level()
			? wolf
			: wolf.teleport(new TeleportTransition(caster.level(), at, Vec3.ZERO, caster.getYRot(), 0.0F, TeleportTransition.DO_NOTHING));
		if (moved == null) {
			return false;
		}
		moved.teleportTo(at.x, at.y, at.z);
		if (moved instanceof SummonedWolf servant) {
			servant.setTarget(null);
			servant.getNavigation().stop();
			SUMMONED.put(caster.getUUID(), servant.getUUID());
			caster.setAttached(BOND, servant.getUUID());
			refreshHealth(servant, caster, false);
		}
		caster.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.8, at.z, 16, 0.4, 0.5, 0.4, 0.05);
		return true;
	}

	private static Vec3 beside(final ServerPlayer caster) {
		Vec3 look = caster.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
		Vec3 at = caster.position().add(flat.scale(1.5));
		return CastingSpots.standing(caster.level(), at) ? at : caster.position();
	}
}
