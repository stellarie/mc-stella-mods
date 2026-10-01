package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.RoleSpec;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

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
	private int witchBuffIndex;
	private boolean witchInvisibilityCast;
	private boolean elite;
	private long voidDragReadyTick;
	private long harpoonReadyTick;
	private int retreatCooldownTicks;
	private boolean companionSpawned;
	private @Nullable UUID companionId;
	private String r02Decision = "NO_THREAT";
	private String r02SkipReason = "";
	private int r02ThreatScanTick = Integer.MIN_VALUE;
	private List<Creeper> r02Threats = List.of();
	private @Nullable UUID r02RouteThreat;
	private @Nullable Vec3 r02RouteTarget;
	private @Nullable String r02RouteFailure;
	private int r02RouteValidUntil;
	private @Nullable String r02NativeCommandBefore;
	private @Nullable String r02NativeCommandAfter;
	private @Nullable BlockPos r02ShulkerTarget;
	private @Nullable Vec3 r02DragonTarget;
	private @Nullable UUID r02ThreatId;

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the behavior state attachment {}", TYPE.identifier());
	}

	public static ReactiveState get(final Mob mob) {
		return ((AttachmentTarget) mob).getAttachedOrCreate(TYPE);
	}

	/**
	 * Cached for PLAYER_CHECK_INTERVAL_TICKS, keyed on the mob's own tick counter rather than the level
	 * clock: the counter advances in play and under the game test runner alike, while the runner's level
	 * clock barely moves, which used to freeze this answer on the first check.
	 */
	public boolean playerInRange(final Mob mob) {
		if (mob.tickCount >= this.nextPlayerCheckTick) {
			this.nextPlayerCheckTick = mob.tickCount + PLAYER_CHECK_INTERVAL_TICKS;
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

	/** W11: where the ally buff rotation starts, so the three buffs cycle in a fixed order. */
	public int getWitchBuffIndex() {
		return this.witchBuffIndex;
	}

	public void setWitchBuffIndex(final int witchBuffIndex) {
		this.witchBuffIndex = witchBuffIndex;
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



	/**
	 * The shared rearm cooldown for the two band goals, counted in the goals' own ticks rather than in
	 * game time, because a goal ticks once per game tick and the harness clock does not advance. Exactly
	 * one caller decrements it, RetreatGoal, which sits at rank 0 and is therefore evaluated first on
	 * every tick where a MOVE goal is considered. Both goals read the same budget, so neither can call a
	 * mob exhausted while the other calls it ready.
	 */
	public boolean isRetreatCoolingDown() {
		return this.retreatCooldownTicks > 0;
	}

	public void armRetreatCooldown(final int ticks) {
		this.retreatCooldownTicks = ticks;
	}

	public void tickRetreatCooldown() {
		if (this.retreatCooldownTicks > 0) {
			this.retreatCooldownTicks--;
		}
	}

		/** R25 style cooldown for the void drag: transient, because a reload may arm it again. */
	public long getVoidDragReadyTick() {
		return this.voidDragReadyTick;
	}

	public void setVoidDragReadyTick(final long voidDragReadyTick) {
		this.voidDragReadyTick = voidDragReadyTick;
	}

	/** P2: the harpoon's rearm, so one drowned cannot chain pulls with a fast throw. */
	public long getHarpoonReadyTick() {
		return this.harpoonReadyTick;
	}

	public void setHarpoonReadyTick(final long harpoonReadyTick) {
		this.harpoonReadyTick = harpoonReadyTick;
	}

	/** B05: one companion per boss, so a re-queue cannot double the squad. */
	public boolean isCompanionSpawned() {
		return this.companionSpawned;
	}

	public void setCompanionSpawned(final boolean companionSpawned) {
		this.companionSpawned = companionSpawned;
	}

	/** The companion this boss was given, recorded so the pair can be found after either one moves. */
	public @Nullable UUID companionId() {
		return this.companionId;
	}

	public void setCompanionId(final UUID companionId) {
		this.companionId = companionId;
	}

	public String getR02Decision() {
		return this.r02Decision;
	}

	public String getR02SkipReason() {
		return this.r02SkipReason;
	}

	public void setR02Result(final String decision, final String skipReason) {
		this.r02Decision = decision;
		this.r02SkipReason = skipReason;
	}

	public List<Creeper> cachedR02Threats(final int mobTick) {
		return this.r02ThreatScanTick == mobTick ? this.r02Threats : null;
	}

	public void cacheR02Threats(final int mobTick, final List<Creeper> threats) {
		this.r02ThreatScanTick = mobTick;
		this.r02Threats = List.copyOf(threats);
	}

	public @Nullable Vec3 cachedR02Route(final UUID threatId, final int mobTick) {
		return threatId.equals(this.r02RouteThreat) && mobTick < this.r02RouteValidUntil
			? this.r02RouteTarget
			: null;
	}

	public @Nullable String cachedR02RouteFailure(final UUID threatId, final int mobTick) {
		return threatId.equals(this.r02RouteThreat) && mobTick < this.r02RouteValidUntil
			? this.r02RouteFailure
			: null;
	}

	public boolean hasCachedR02RouteFailure(final UUID threatId, final int mobTick) {
		return threatId.equals(this.r02RouteThreat)
			&& this.r02RouteFailure != null
			&& mobTick < this.r02RouteValidUntil;
	}

	public void cacheR02Route(final UUID threatId, final Vec3 target, final int validUntil) {
		this.r02RouteThreat = threatId;
		this.r02RouteTarget = target;
		this.r02RouteFailure = null;
		this.r02RouteValidUntil = validUntil;
	}

	public void cacheR02RouteFailure(final UUID threatId, final String reason, final int validUntil) {
		this.r02RouteThreat = threatId;
		this.r02RouteTarget = null;
		this.r02RouteFailure = reason;
		this.r02RouteValidUntil = validUntil;
	}

	public void clearR02Route() {
		this.r02RouteThreat = null;
		this.r02RouteTarget = null;
		this.r02RouteFailure = null;
		this.r02RouteValidUntil = 0;
	}

	public String getR02NativeCommandBefore() {
		return this.r02NativeCommandBefore;
	}

	public String getR02NativeCommandAfter() {
		return this.r02NativeCommandAfter;
	}

	public void setR02NativeCommands(final @Nullable String before, final @Nullable String after) {
		this.r02NativeCommandBefore = before;
		this.r02NativeCommandAfter = after;
	}

	public @Nullable BlockPos getR02ShulkerTarget() {
		return this.r02ShulkerTarget;
	}

	public void setR02ShulkerTarget(final @Nullable BlockPos target) {
		this.r02ShulkerTarget = target;
	}

	public @Nullable Vec3 getR02DragonTarget() {
		return this.r02DragonTarget;
	}

	public void setR02DragonTarget(final @Nullable Vec3 target) {
		this.r02DragonTarget = target;
	}

	public @Nullable UUID getR02ThreatId() {
		return this.r02ThreatId;
	}

	public void setR02ThreatId(final @Nullable UUID threatId) {
		this.r02ThreatId = threatId;
	}
}
