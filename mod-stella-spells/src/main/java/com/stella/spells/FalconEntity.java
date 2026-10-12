package com.stella.spells;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
 * The Blitz Beat falcon. It is a parrot with no mind of its own: the server places it every tick, in a slow circle
 * above the head of its owner (a player, or a mob that holds the buff), or on the way to the foe a strike hit and
 * back. It flies and never sits. It cannot be hurt, pushed, hit or saved, and it leaves the moment the buff ends
 * or the owner is gone. The client draws it larger and brown.
 */
public final class FalconEntity extends Parrot {
	/** How many ticks one swoop takes, out and back. */
	public static final int SWOOP_TICKS = 8;
	/** The gap between the owner's head and the falcon, the circle it flies around the owner, and how far it bobs. */
	public static final double HOVER_CLEARANCE = 0.8;
	public static final double HOVER_RADIUS = 0.9;
	public static final double HOVER_BOB = 0.15;
	/** How many ticks one lap of the circle takes, and one bob. */
	private static final int ORBIT_TICKS = 50;
	private static final int BOB_TICKS = 26;
	/** A mob's falcon flies wider than a player's when the mob is wide. */
	private static final double MOB_ORBIT_SHARE = 0.75;

	private @Nullable UUID ownerId;
	private boolean ownerIsPlayer;
	private @Nullable UUID swoopTarget;
	private int swoopLeft;

	public FalconEntity(final EntityType<? extends Parrot> type, final Level level) {
		super(type, level);
		this.setNoAi(true);
		this.setNoGravity(true);
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
	}

	/** Where the falcon flies when it is at home above this owner, this many ticks into its life. */
	static Vec3 hover(final LivingEntity owner, final int age) {
		double angle = age * 2.0 * Math.PI / ORBIT_TICKS;
		double radius = owner instanceof Player ? HOVER_RADIUS : Math.max(HOVER_RADIUS, owner.getBbWidth() * MOB_ORBIT_SHARE);
		double bob = HOVER_BOB * Math.sin(age * 2.0 * Math.PI / BOB_TICKS);
		return owner.position().add(Math.cos(angle) * radius, owner.getBbHeight() + HOVER_CLEARANCE + bob, Math.sin(angle) * radius);
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
		Vec3 home = hover(owner, this.tickCount);
		Vec3 at = home;
		Vec3 facing = hover(owner, this.tickCount + 1).subtract(home);
		if (this.swoopLeft > 0) {
			Entity foe = this.swoopTarget == null ? null : level.getEntity(this.swoopTarget);
			double progress = 1.0 - (double) this.swoopLeft / SWOOP_TICKS;
			double reach = progress < 0.5 ? progress * 2.0 : (1.0 - progress) * 2.0;
			if (foe != null && foe.isAlive()) {
				Vec3 target = foe.getBoundingBox().getCenter();
				at = home.lerp(target, reach);
				facing = target.subtract(home);
			}
			this.swoopLeft--;
			if (this.swoopLeft == 0) {
				this.swoopTarget = null;
			}
		}
		this.setPos(at.x, at.y, at.z);
		float yaw = (float) (Mth.atan2(facing.z, facing.x) * (180.0 / Math.PI)) - 90.0F;
		this.setYRot(yaw);
		this.yRotO = yaw;
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
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
