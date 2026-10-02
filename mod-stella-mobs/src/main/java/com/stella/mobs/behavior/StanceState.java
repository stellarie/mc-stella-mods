package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * The stance of one mob and the flags behind it. The state is not saved: a mob that loads again reads its player again
 * within a few ticks. It holds no timer of the level clock, because every time here counts the mob's own ticks. The type
 * registers when this class first loads, which is safe for an attachment that nothing saves or sends.
 */
public final class StanceState {
	public static final AttachmentType<StanceState> TYPE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "stance_state"), StanceState::new
	);

	private Stance stance = Stance.NORMAL;
	private int sinceTick;
	private boolean mobLow;
	private boolean playerLow;
	private int nextAssessTick;
	private int kiteUntilTick;
	private @Nullable UUID targetId;

	static StanceState get(final Mob mob) {
		return ((AttachmentTarget) mob).getAttachedOrCreate(TYPE);
	}

	/** The state, or null for a mob with none. */
	static @Nullable StanceState peek(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(TYPE);
	}

	Stance stance() {
		return this.stance;
	}

	int sinceTick() {
		return this.sinceTick;
	}

	boolean mobLow() {
		return this.mobLow;
	}

	boolean playerLow() {
		return this.playerLow;
	}

	int nextAssessTick() {
		return this.nextAssessTick;
	}

	void setNextAssessTick(final int nextAssessTick) {
		this.nextAssessTick = nextAssessTick;
	}

	/** RETREAT resolves to KITE until this tick. */
	int kiteUntilTick() {
		return this.kiteUntilTick;
	}

	@Nullable UUID targetId() {
		return this.targetId;
	}

	void setTargetId(final @Nullable UUID targetId) {
		this.targetId = targetId;
	}

	void setFlags(final boolean mobLow, final boolean playerLow) {
		this.mobLow = mobLow;
		this.playerLow = playerLow;
	}

	/** Moves to a stance. Holding it logs no line. */
	void change(final Mob mob, final Stance to, final String reason) {
		if (this.stance == to) {
			return;
		}

		Stance from = this.stance;
		this.stance = to;
		this.sinceTick = mob.tickCount;
		StellaMobs.LOGGER.debug(
			"[BEHAVIOR] test=stance CHANGE mob={} type={} from={} to={} tick={} {}",
			mob.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), from, to, mob.tickCount, reason
		);
	}

	/** The mob lost or changed its player. */
	void reset(final Mob mob, final String reason) {
		this.mobLow = false;
		this.playerLow = false;
		this.kiteUntilTick = 0;
		this.change(mob, Stance.NORMAL, reason);
	}

	/** A retreat run ended: kite for a while. */
	void endRetreat(final Mob mob) {
		this.kiteUntilTick = mob.tickCount + StanceGoal.KITE_TICKS;
		this.change(mob, Stance.KITE, "retreat ended");
	}

	void holdKite(final Mob mob, final int untilTick) {
		this.kiteUntilTick = Math.max(this.kiteUntilTick, untilTick);
		if (this.stance == Stance.RETREAT) {
			this.change(mob, Stance.KITE, "kite requested");
		}
	}
}
