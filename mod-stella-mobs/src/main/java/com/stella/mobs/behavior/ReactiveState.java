package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

public final class ReactiveState {
	public static final AttachmentType<ReactiveState> TYPE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "reactive_state"), ReactiveState::new
	);
	private static final double PLAYER_RADIUS = 32.0;
	private static final int PLAYER_CHECK_INTERVAL_TICKS = 20;

	private boolean installed;
	private boolean gearApplied;
	private long shieldReadyTick;
	private long retreatReadyTick;
	private long nextArrowScanTick;
	private long nextAllyScanTick;
	private long nextPlayerCheckTick;
	private boolean playerNearby;
	private int lastDefendedHurtTimestamp;

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the behavior state attachment {}", TYPE.identifier());
	}

	public static ReactiveState get(final Mob mob) {
		return ((AttachmentTarget) mob).getAttachedOrCreate(TYPE);
	}

	public boolean playerInRange(final Mob mob) {
		long now = mob.level().getGameTime();
		if (now >= this.nextPlayerCheckTick) {
			this.nextPlayerCheckTick = now + PLAYER_CHECK_INTERVAL_TICKS;
			this.playerNearby = mob.level().hasNearbyAlivePlayer(mob.getX(), mob.getY(), mob.getZ(), PLAYER_RADIUS);
		}

		return this.playerNearby;
	}

	public boolean isInstalled() {
		return this.installed;
	}

	public void setInstalled(final boolean installed) {
		this.installed = installed;
	}

	public boolean isGearApplied() {
		return this.gearApplied;
	}

	public void setGearApplied(final boolean gearApplied) {
		this.gearApplied = gearApplied;
	}

	public long getShieldReadyTick() {
		return this.shieldReadyTick;
	}

	public void setShieldReadyTick(final long shieldReadyTick) {
		this.shieldReadyTick = shieldReadyTick;
	}

	public long getRetreatReadyTick() {
		return this.retreatReadyTick;
	}

	public void setRetreatReadyTick(final long retreatReadyTick) {
		this.retreatReadyTick = retreatReadyTick;
	}

	public long getNextArrowScanTick() {
		return this.nextArrowScanTick;
	}

	public void setNextArrowScanTick(final long nextArrowScanTick) {
		this.nextArrowScanTick = nextArrowScanTick;
	}

	public long getNextAllyScanTick() {
		return this.nextAllyScanTick;
	}

	public void setNextAllyScanTick(final long nextAllyScanTick) {
		this.nextAllyScanTick = nextAllyScanTick;
	}

	public int getLastDefendedHurtTimestamp() {
		return this.lastDefendedHurtTimestamp;
	}

	public void setLastDefendedHurtTimestamp(final int lastDefendedHurtTimestamp) {
		this.lastDefendedHurtTimestamp = lastDefendedHurtTimestamp;
	}
}
