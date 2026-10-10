package com.stella.spells;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Blitz Beat falcon. It is a parrot with no mind of its own: the server places it every tick, on the
 * owner shoulder, or on the way to the foe a strike hit and back. It cannot be hurt, pushed, hit or saved,
 * and it leaves the moment the buff ends or the owner is gone. The client draws it larger and brown.
 */
public final class FalconEntity extends Parrot {
	/** How many ticks one swoop takes, out and back. */
	public static final int SWOOP_TICKS = 8;
	private static final double PERCH_SIDE = 0.45;
	private static final double PERCH_HEIGHT = 1.45;

	private @Nullable UUID ownerId;
	private @Nullable UUID swoopTarget;
	private int swoopLeft;

	public FalconEntity(final EntityType<? extends Parrot> type, final Level level) {
		super(type, level);
		this.setNoAi(true);
		this.setNoGravity(true);
		this.setInSittingPose(true);
	}

	void bind(final ServerPlayer owner) {
		this.ownerId = owner.getUUID();
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

	/** Where the falcon sits when it is perched on this player. */
	static Vec3 perch(final Player owner) {
		double yaw = Math.toRadians(owner.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		return owner.position().add(right.scale(PERCH_SIDE)).add(0.0, PERCH_HEIGHT, 0.0);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = this.ownerId == null ? null : level.getServer().getPlayerList().getPlayer(this.ownerId);
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
