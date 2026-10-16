package com.stella.end;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Everything the controller remembers about one dragon. Each list is a record of what the dragon actually did. */
public final class DragonState {
	public enum Mode {
		PASSIVE,
		AWAKE,
		ROOST
	}

	public enum Attack {
		SWOOP,
		BARRAGE,
		DIVE
	}

	public record AttackRecord(Attack attack, long at) {
	}

	/** One dragon fireball of a barrage, and the clock when it left. */
	public record Shot(UUID id, long at) {
	}

	/** One swoop pass: who it struck, the damage it handed the game for each, and the slowness it gave. */
	public record Swoop(long at, List<UUID> struck, List<Float> blows, int slownessAmplifier) {
	}

	/** One dive landing: who it threw up, and the fire charges it threw round it. */
	public record Dive(long at, Vec3 impact, List<UUID> lifted, List<UUID> charges) {
	}

	/** One of the four wyvern places. A place holds a pair, or waits {@code respawnAt} to hold a new one. */
	public static final class Slot {
		@Nullable UUID wyvern;
		@Nullable UUID rider;
		long respawnAt = EndState.NEVER;

		public @Nullable UUID wyvern() {
			return wyvern;
		}

		public @Nullable UUID rider() {
			return rider;
		}

		public long respawnAt() {
			return respawnAt;
		}
	}

	final UUID id;
	Mode mode = Mode.PASSIVE;
	long modeSince;
	boolean roosted;
	long roostStartedAt = EndState.NEVER;
	long nextSummonAt;
	long nextAttackAt;
	long lastRoofSeen;
	/** The attack under way, and what it needs to go on. */
	@Nullable Attack current;
	@Nullable Attack forced;
	long attackStartedAt;
	final List<Vec3> path = new ArrayList<>();
	int pathIndex;
	@Nullable List<UUID> struck;
	int shotsLeft;
	long nextShotAt;
	@Nullable Vec3 impact;
	long landedAt = EndState.NEVER;
	Vec3 velocity = Vec3.ZERO;
	final List<AttackRecord> attacks = new ArrayList<>();
	final List<Shot> shots = new ArrayList<>();
	final List<Swoop> swoops = new ArrayList<>();
	final List<Dive> dives = new ArrayList<>();
	final List<UUID> summons = new ArrayList<>();
	final Slot[] slots = new Slot[DragonRules.WYVERNS];
	Set<Integer> buffs = new LinkedHashSet<>();
	boolean dead;

	DragonState(final UUID id) {
		this.id = id;
		for (int i = 0; i < slots.length; i++) {
			slots[i] = new Slot();
		}
	}

	public UUID id() {
		return id;
	}

	public Mode mode() {
		return mode;
	}

	public boolean roosted() {
		return roosted;
	}

	public long roostStartedAt() {
		return roostStartedAt;
	}

	public List<AttackRecord> attacks() {
		return List.copyOf(attacks);
	}

	public List<Shot> shots() {
		return List.copyOf(shots);
	}

	public List<Swoop> swoops() {
		return List.copyOf(swoops);
	}

	public List<Dive> dives() {
		return List.copyOf(dives);
	}

	public List<UUID> summons() {
		return List.copyOf(summons);
	}

	public Slot slot(final int index) {
		return slots[index];
	}

	public Set<Integer> buffs() {
		return Set.copyOf(buffs);
	}

	/** The share of the roll space an attack takes. After a roost the swoop and the dive count double. */
	public int weight(final Attack attack) {
		return roosted && attack != Attack.BARRAGE ? 2 : 1;
	}

	/** The attack a roll picks, out of the total weight. */
	public Attack pick(final int roll) {
		int swoop = weight(Attack.SWOOP);
		int barrage = weight(Attack.BARRAGE);
		if (roll < swoop) {
			return Attack.SWOOP;
		}
		return roll < swoop + barrage ? Attack.BARRAGE : Attack.DIVE;
	}

	public int totalWeight() {
		return weight(Attack.SWOOP) + weight(Attack.BARRAGE) + weight(Attack.DIVE);
	}
}
