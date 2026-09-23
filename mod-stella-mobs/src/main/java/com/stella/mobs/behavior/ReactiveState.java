package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
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
	private long keepDistanceReadyTick;
	private long nextArrowScanTick;
	private long nextAllyScanTick;
	private long nextPlayerCheckTick;
	private boolean playerNearby;
	private int lastDefendedHurtTimestamp;
	private CombatRole role = CombatRole.MELEE;
	private double nearDistance;
	private double farDistance;
	private boolean mainHandRolled;
	private long witchActionUntilTick;
	private long witchNextOffensiveTick;
	private long witchNextAllyScanTick;
	private int witchThrowIndex;
	private boolean witchInvisibilityCast;
	private boolean elite;

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

	public long getKeepDistanceReadyTick() {
		return this.keepDistanceReadyTick;
	}

	public void setKeepDistanceReadyTick(final long keepDistanceReadyTick) {
		this.keepDistanceReadyTick = keepDistanceReadyTick;
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

	public CombatRole getRole() {
		return this.role;
	}

	public void setRoleSpec(final RoleSpec spec) {
		this.role = spec.role();
		this.nearDistance = spec.nearDistance();
		this.farDistance = spec.farDistance();
	}

	public double getNearDistance() {
		return this.nearDistance;
	}

	public double getFarDistance() {
		return this.farDistance;
	}

	public boolean isMainHandRolled() {
		return this.mainHandRolled;
	}

	public void setMainHandRolled(final boolean mainHandRolled) {
		this.mainHandRolled = mainHandRolled;
	}

	public long getWitchActionUntilTick() {
		return this.witchActionUntilTick;
	}

	public void setWitchActionUntilTick(final long witchActionUntilTick) {
		this.witchActionUntilTick = witchActionUntilTick;
	}

	public long getWitchNextOffensiveTick() {
		return this.witchNextOffensiveTick;
	}

	public void setWitchNextOffensiveTick(final long witchNextOffensiveTick) {
		this.witchNextOffensiveTick = witchNextOffensiveTick;
	}

	public long getWitchNextAllyScanTick() {
		return this.witchNextAllyScanTick;
	}

	public void setWitchNextAllyScanTick(final long witchNextAllyScanTick) {
		this.witchNextAllyScanTick = witchNextAllyScanTick;
	}

	public int getWitchThrowIndex() {
		return this.witchThrowIndex;
	}

	public void setWitchThrowIndex(final int witchThrowIndex) {
		this.witchThrowIndex = witchThrowIndex;
	}

	public boolean isWitchInvisibilityCast() {
		return this.witchInvisibilityCast;
	}

	public void setWitchInvisibilityCast(final boolean witchInvisibilityCast) {
		this.witchInvisibilityCast = witchInvisibilityCast;
	}

	public boolean isElite() {
		return this.elite;
	}

	public void setElite(final boolean elite) {
		this.elite = elite;
	}
}
