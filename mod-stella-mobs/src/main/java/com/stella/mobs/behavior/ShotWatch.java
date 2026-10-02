package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Who shot, and whether the player saw the shooter. Each mob that hunts a player keeps one record. The record holds the
 * seen mark of the player and the last shot of the mob.
 *
 * <p>{@link AmbushGoal.AmbushWatchGoal} feeds the seen mark. The player has seen the mob after {@link #SEEN_TICKS} ticks
 * of unbroken sight. The mark clears after {@link #FORGET_TICKS} ticks without a sighting.
 *
 * <p>An entity load listener feeds the shots. It sees every projectile whose owner is a mob with a player target, so
 * arrows, tridents, potions, and fireballs all count. It stores the owner's tick count and the seen mark at that moment.
 *
 * <p>The record is not saved. Every time in it counts the mob's own ticks, because the level clock barely moves in a
 * test. The type registers when this class first loads, which is safe for an attachment that nothing saves or sends.
 */
public final class ShotWatch {
	public static final int SEEN_TICKS = 10;
	public static final int FORGET_TICKS = 100;

	private static final int NEVER = Integer.MIN_VALUE / 2;
	private static final AttachmentType<Record> RECORD = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "shot_watch"), Record::new
	);

	/** The shots and the seen state of one mob. */
	static final class Record {
		private @Nullable UUID playerId;
		private int seenSince = NEVER;
		private int lastSeenAt = NEVER;
		private boolean seen;
		private int shots;
		private int handledShots;
		private int lastShotTick = NEVER;
		private boolean lastShotSeen;
		private AmbushGoal.Phase phase = AmbushGoal.Phase.STALK;
		private @Nullable Vec3 pendingSpot;
		private @Nullable UUID pendingPlayer;
		private int pendingUntil;
		private int nextSearchTick;

		/**
		 * One poll of the seen state. A sighting that holds for {@link #SEEN_TICKS} sets the mark. A mark that has gone
		 * {@link #FORGET_TICKS} without a sighting clears. A new player starts the state over.
		 */
		void update(final int now, final @Nullable UUID target, final boolean sighted) {
			if (target != null && !target.equals(this.playerId)) {
				this.playerId = target;
				this.seen = false;
				this.seenSince = NEVER;
				this.lastSeenAt = NEVER;
			}

			if (sighted) {
				if (this.seenSince == NEVER) {
					this.seenSince = now;
				}

				this.lastSeenAt = now;
				if (now - this.seenSince >= SEEN_TICKS) {
					this.seen = true;
				}
			} else {
				this.seenSince = NEVER;
				if (this.seen && now - this.lastSeenAt >= FORGET_TICKS) {
					this.seen = false;
				}
			}
		}

		void shot(final int now) {
			this.shots++;
			this.lastShotTick = now;
			this.lastShotSeen = this.seen;
		}

		boolean seen() {
			return this.seen;
		}

		int shots() {
			return this.shots;
		}

		boolean lastShotSeen() {
			return this.lastShotSeen;
		}

		int lastShotTick() {
			return this.lastShotTick;
		}

		boolean hasUnhandledShot() {
			return this.shots != this.handledShots;
		}

		void handleShots() {
			this.handledShots = this.shots;
		}

		AmbushGoal.Phase phase() {
			return this.phase;
		}

		void setPhase(final AmbushGoal.Phase phase) {
			this.phase = phase;
		}

		/** A cover spot that the unit has found and the ambush goal has not yet taken. */
		void pend(final Vec3 spot, final UUID player, final int until) {
			this.pendingSpot = spot;
			this.pendingPlayer = player;
			this.pendingUntil = until;
		}

		/** The pending spot while it holds for this player and has not expired. */
		@Nullable Vec3 pending(final int now, final UUID player) {
			return this.pendingSpot != null && now <= this.pendingUntil && player.equals(this.pendingPlayer) ? this.pendingSpot : null;
		}

		void clearPending() {
			this.pendingSpot = null;
			this.pendingPlayer = null;
		}

		/** True from this tick on, so a unit searches for cover about once per `ticks`. */
		boolean searchDue(final int now) {
			return now >= this.nextSearchTick;
		}

		void searched(final int now, final int ticks) {
			this.nextSearchTick = now + ticks;
		}
	}

	private ShotWatch() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(ShotWatch::onLoad);
	}

	private static void onLoad(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Projectile projectile) || !(projectile.getOwner() instanceof Mob owner)) {
			return;
		}

		// A projectile in flight that returns with its chunk is an old shot.
		if (entity instanceof EntityLoadData loadData && loadData.isLoadedFromDisk()) {
			return;
		}

		// The installer's check does not cover a listener.
		if (!(owner.getTarget() instanceof Player) || owner.entityTags().contains(StellaMobs.NO_BEHAVIOR_TAG)) {
			return;
		}

		Record record = record(owner);
		record.shot(owner.tickCount);
		StellaMobs.LOGGER.debug(
			"[BEHAVIOR] test=ambush SHOT mob={} tick={} seen={} shots={}", owner.getUUID(), owner.tickCount, record.seen(), record.shots()
		);
	}

	static Record record(final Mob mob) {
		return ((AttachmentTarget) mob).getAttachedOrCreate(RECORD);
	}

	/** The record, or null for a mob that never hunted a player. */
	static @Nullable Record peek(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(RECORD);
	}

	/** True while the player counts the mob as seen. */
	public static boolean seenMark(final Mob mob) {
		Record record = peek(mob);
		return record != null && record.seen();
	}

	/** How many shots the mob has loosed at a player. */
	public static int shotCount(final Mob mob) {
		Record record = peek(mob);
		return record == null ? 0 : record.shots();
	}

	/** True when the player counted the mob as seen at its last shot. */
	public static boolean lastShotSeen(final Mob mob) {
		Record record = peek(mob);
		return record != null && record.lastShotSeen();
	}
}
