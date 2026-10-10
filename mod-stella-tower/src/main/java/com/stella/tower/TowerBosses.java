package com.stella.tower;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * The boss of every {@value #EVERY}th floor: the Blaze Sovereign of the nether mod, reached through a share, or the
 * Ember Tyrant when the share is absent or fails.
 * <p>
 * The tower does not depend on the nether mod. The nether mod publishes {@value #SHARE_KEY}, a
 * {@code BiFunction<ServerLevel, CompoundTag, Mob>}; the tower asks it with a tag holding the Sovereign's final mob
 * {@code level}, and gets an unplaced Sovereign or null. It positions and adds the mob and adopts it
 * ({@link TowerMobs#adopt}) without touching its health, attributes or specials. A factory that returns null or throws
 * is set aside with one line in the log, and the Ember Tyrant rises in its place: a blaze three times its size whose
 * every hit sets its target alight, kept near the ground.
 */
public final class TowerBosses {
	public static final int EVERY = 50;
	public static final String SHARE_KEY = "mod-stella-nether:sovereign-factory";
	public static final String LEVEL_KEY = "level";
	public static final String TYRANT_TAG = "mod-stella-tower.ember_tyrant";
	public static final double TYRANT_SCALE = 3.0;
	public static final int TYRANT_BURN_SECONDS = 5;
	/** A tyrant higher than this over the ground is drawn down. */
	public static final double TYRANT_RISE = 2.0;
	public static final double TYRANT_PULL = 0.5;
	private static final Set<UUID> TYRANTS = ConcurrentHashMap.newKeySet();
	private static boolean told;

	private TowerBosses() {
	}

	/** What rose for a floor: the mob, not yet placed, and whether the nether mod made it. */
	public record Raised(Mob mob, boolean sovereign) {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerBosses::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damage, blocked) -> {
			Entity attacker = source.getEntity();
			if (!blocked && damage > 0.0F && attacker != null && attacker.entityTags().contains(TYRANT_TAG) && attacker != victim) {
				victim.igniteForSeconds(TYRANT_BURN_SECONDS);
			}
		});
	}

	/** Whether the floor's boss is the Sovereign (or the Ember Tyrant in its place). */
	public static boolean sovereignFloor(final int floor) {
		return floor > 0 && floor % EVERY == 0;
	}

	/** The mob of a sovereign floor, unplaced and not yet a tower mob; null when not even the tyrant could be made. */
	public static @Nullable Raised create(final ServerLevel tower, final int floor) {
		Mob sovereign = fromShare(tower, floor);
		if (sovereign != null) {
			return new Raised(sovereign, true);
		}
		Mob tyrant = EntityTypes.BLAZE.create(tower, EntitySpawnReason.EVENT);
		return tyrant == null ? null : new Raised(tyrant, false);
	}

	private static @Nullable Mob fromShare(final ServerLevel tower, final int floor) {
		Object share = FabricLoader.getInstance().getObjectShare().get(SHARE_KEY);
		if (!(share instanceof BiFunction<?, ?, ?> factory)) {
			return null;
		}
		try {
			CompoundTag tag = new CompoundTag();
			tag.putInt(LEVEL_KEY, TowerMobs.levelOf(floor));
			@SuppressWarnings("unchecked")
			Object made = ((BiFunction<ServerLevel, CompoundTag, Object>) factory).apply(tower, tag);
			if (made instanceof Mob mob) {
				return mob;
			}
			tell("the Sovereign factory gave no mob for floor " + floor);
		} catch (RuntimeException | LinkageError failure) {
			tell("the Sovereign factory failed for floor " + floor + ": " + failure);
		}
		return null;
	}

	private static void tell(final String line) {
		if (!told) {
			told = true;
			StellaTower.LOGGER.info("[TOWER] {}; the Ember Tyrant rises in its place", line);
		}
	}

	/** Makes a raised boss a tower boss of its floor: the Sovereign as it is, the tyrant at the tower's own strength. */
	public static void settle(final Raised raised, final int floor, final TowerElement element) {
		Mob mob = raised.mob();
		if (raised.sovereign()) {
			TowerMobs.adopt(mob, floor, element, TowerRank.BOSS);
			StellaTower.LOGGER.info("[TOWER] floor {} boss is the Blaze Sovereign", floor);
			return;
		}
		mob.getAttribute(Attributes.SCALE).setBaseValue(TYRANT_SCALE);
		TowerMobs.makeTowerMob(mob, floor, element, TowerRank.BOSS);
		mob.setCustomName(Component.translatable("entity.mod-stella-tower.ember_tyrant").withStyle(TowerRank.BOSS.color()));
		mob.addTag(TYRANT_TAG);
		TYRANTS.add(mob.getUUID());
		StellaTower.LOGGER.info("[TOWER] floor {} boss is the Ember Tyrant", floor);
	}

	private static void tick(final MinecraftServer server) {
		if (TYRANTS.isEmpty() || server.getTickCount() % 2 != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null) {
			return;
		}
		for (UUID id : TYRANTS) {
			if (tower.getEntity(id) instanceof Mob tyrant && tyrant.isAlive()) {
				keepNearGround(tyrant);
			} else {
				TYRANTS.remove(id);
			}
		}
	}

	/** Draws a tyrant that floats more than {@value #TYRANT_RISE} blocks over the ground back down; true if it was. */
	static boolean keepNearGround(final LivingEntity tyrant) {
		int gap = 0;
		net.minecraft.core.BlockPos at = tyrant.blockPosition();
		while (gap <= TYRANT_RISE + 2 && tyrant.level().getBlockState(at.below(gap + 1)).getCollisionShape(tyrant.level(), at.below(gap + 1)).isEmpty()) {
			gap++;
		}
		if (gap > TYRANT_RISE) {
			net.minecraft.world.phys.Vec3 now = tyrant.getDeltaMovement();
			tyrant.setDeltaMovement(now.x, -TYRANT_PULL, now.z);
			return true;
		}
		return false;
	}
}
