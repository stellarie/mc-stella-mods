package com.stella.learn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Three or more hostile monsters within reach of each other, and the slot each one holds against the player they hunt.
 * A scan builds a new one each time, so a mob that walks in is a member of the next one.
 */
public final class Formation {
	/** What a member does. FRONT fights where it stands, and the others walk to a position first. */
	public enum Role {
		FRONT,
		STRIKER,
		FLANK,
		BACK,
		GUARD,
		FALL_BACK
	}

	/**
	 * `pos` is where the member walks, or null when it fights where it stands. `engaged` is true for a striker whose shield
	 * bearer stands at the target, which is when it may strike.
	 */
	public record Slot(Role role, @Nullable Vec3 pos, boolean engaged) {
	}

	private final List<Monster> members;
	private final @Nullable ServerPlayer focus;
	private final Map<UUID, Slot> slots = new HashMap<>();

	Formation(final List<Monster> members, final @Nullable ServerPlayer focus) {
		this.members = members;
		this.focus = focus;
	}

	public List<Monster> members() {
		return this.members;
	}

	public int size() {
		return this.members.size();
	}

	public boolean contains(final Mob mob) {
		return this.members.contains(mob);
	}

	/** The player most members hunt, or null when none does. Slots exist only with a focus. */
	public @Nullable ServerPlayer focus() {
		return this.focus;
	}

	public @Nullable Slot slotOf(final Mob mob) {
		return this.slots.get(mob.getUUID());
	}

	void put(final Mob mob, final Slot slot) {
		this.slots.put(mob.getUUID(), slot);
	}
}
