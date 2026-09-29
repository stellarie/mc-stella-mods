package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The rally callout: join the fight at a flank slot beside the player. Receivers alternate sides, a melee mob stands
 * a fixed distance out and a ranged one stands mid-band, and an idle receiver is given the player as its target.
 */
public final class Rally {
	public static final Identifier TYPE = StellaLearn.id("rally");
	/** Blocks from the player to a melee mob's flank slot. */
	public static final double MELEE_SLOT_DISTANCE = 6.0;
	public static final AttachmentType<Vec3> SLOT = AttachmentRegistry.create(StellaLearn.id("rally_slot"));

	private Rally() {
	}

	public static void register() {
		CalloutTypes.register(TYPE, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final ServerPlayer player, final Callout callout) {
				return true;
			}

			@Override
			public void perform(final Mob mob, final ServerPlayer player, final Callout callout, final int index) {
				Vec3 slot = slotFor(mob, player, index);
				((AttachmentTarget) mob).setAttached(SLOT, slot);
				LearnLog.info("learnRally", "SLOT", LearnLog.who(mob) + " " + LearnLog.who(player) + " index=" + index + " side=" + (index % 2 == 0 ? "left" : "right")
					+ " slot=" + text(slot) + " distance=" + LearnLog.num(slot.distanceTo(player.position())) + " target=" + (mob.getTarget() == null ? "none" : "player"));
				if (mob.getTarget() == null) {
					mob.setTarget(player);
				}
			}
		});
	}

	/** A point as `x,y,z` to two places, for a log line. */
	static String text(final Vec3 point) {
		return LearnLog.num(point.x) + "," + LearnLog.num(point.y) + "," + LearnLog.num(point.z);
	}

	public static @Nullable Vec3 slotOf(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(SLOT);
	}

	/** Where the receiver `index` stands: to one side of a player who faces the fight, the sides alternating. */
	public static Vec3 slotFor(final Mob mob, final ServerPlayer player, final int index) {
		Vec3 look = player.getLookAngle();
		Vec3 facing = new Vec3(look.x, 0.0, look.z);
		facing = facing.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : facing.normalize();
		Vec3 side = new Vec3(-facing.z, 0.0, facing.x).scale(index % 2 == 0 ? 1.0 : -1.0);
		return player.position().add(side.scale(distanceFor(mob)));
	}

	private static double distanceFor(final Mob mob) {
		ReactiveState state = ReactiveState.get(mob);
		return state.getRole() == CombatRole.MELEE ? MELEE_SLOT_DISTANCE : (state.getNearDistance() + state.getFarDistance()) / 2.0;
	}
}
