package com.stella.spells;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Blitz Beat falcon. It is a parrot with no mind of its own: the server places it every tick, on the
 * owner shoulder (a player's, or a mob's that holds the buff), or on the way to the foe a strike hit and back. It cannot be hurt, pushed, hit or saved,
 * and it leaves the moment the buff ends or the owner is gone. The client draws it larger and brown.
 */
public final class FalconEntity extends Parrot {
	/** How many ticks one swoop takes, out and back. */
	public static final int SWOOP_TICKS = 8;
	private static final double PERCH_SIDE = 0.45;
	private static final double PERCH_HEIGHT = 1.45;
	private static final double MOB_PERCH_SHARE = 0.8;

	private @Nullable UUID ownerId;
	private boolean ownerIsPlayer;
	private @Nullable UUID swoopTarget;
	private int swoopLeft;

	public FalconEntity(final EntityType<? extends Parrot> type, final Level level) {
		super(type, level);
		this.setNoAi(true);
		this.setNoGravity(true);
		this.setInSittingPose(true);
	}

	void bind(final LivingEntity owner) {
		this.ownerId = owner.getUUID();
		this.ownerIsPlayer = owner instanceof Player;
	}

	public @Nullable UUID falconOwner() {
		return this.ownerId;
	}

	/** True while the falcon is on its way to a foe or back from it. */
	public boolean swooping() {
		return this.swoopLeft > 0;
	}

	/** Sends the falcon at this foe: out in the first half of the swoop, back in the second. */
	void swoopAt(final Entity foe) {
		this.swoopTarget = foe.getUUID();
		this.swoopLeft = SWOOP_TICKS;
		this.setInSittingPose(false);
	}

	/** Where the falcon sits when it is perched on this owner: a player at shoulder height, a mob by its size. */
	static Vec3 perch(final LivingEntity owner) {
		double yaw = Math.toRadians(owner.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		double side = owner instanceof Player ? PERCH_SIDE : Math.max(PERCH_SIDE, owner.getBbWidth() * 0.75);
		double height = owner instanceof Player ? PERCH_HEIGHT : owner.getBbHeight() * MOB_PERCH_SHARE;
		return owner.position().add(right.scale(side)).add(0.0, height, 0.0);
	}

	// A player owner is found on the player list, so a player who left frees the falcon at once.
	private @Nullable LivingEntity owner(final ServerLevel level) {
		if (this.ownerId == null) {
			return null;
		}
		if (this.ownerIsPlayer) {
			return level.getServer().getPlayerList().getPlayer(this.ownerId);
		}
		return level.getEntity(this.ownerId) instanceof LivingEntity found ? found : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity owner = this.owner(level);
		if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != level || !owner.hasEffect(SpellStatus.BLITZ_BEAT)) {
			this.discard();
			return;
		}
		Vec3 perch = perch(owner);
		Vec3 at = perch;
		if (this.swoopLeft > 0) {
			Entity foe = this.swoopTarget == null ? null : level.getEntity(this.swoopTarget);
			double progress = 1.0 - (double) this.swoopLeft / SWOOP_TICKS;
			double reach = progress < 0.5 ? progress * 2.0 : (1.0 - progress) * 2.0;
			if (foe != null && foe.isAlive()) {
				at = perch.lerp(foe.getBoundingBox().getCenter(), reach);
			}
			this.swoopLeft--;
			if (this.swoopLeft == 0) {
				this.swoopTarget = null;
				this.setInSittingPose(true);
			}
		}
		this.setPos(at.x, at.y, at.z);
		this.setYRot(owner.getYRot());
		this.yRotO = this.getYRot();
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(final Entity other) {
		return false;
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}
}
