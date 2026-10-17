package com.stella.end;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Everything the controller remembers about one dragon. Each list is a record of what the dragon actually did. */
public final class DragonState {
	public enum Mode {
		PASSIVE,
		AWAKE,
		ROOST,
		PERCH
	}

	public enum Attack {
		SWOOP,
		BARRAGE,
		DIVE,
		AIRDROP
	}

	/** One attack: when it began, and when it ended; never while it runs or when a mode change cut it short. */
	public static final class AttackRecord {
		final Attack attack;
		final long at;
		long endedAt = EndState.NEVER;

		AttackRecord(final Attack attack, final long at) {
			this.attack = attack;
			this.at = at;
		}

		public Attack attack() {
			return attack;
		}

		public long at() {
			return at;
		}

		public long endedAt() {
			return endedAt;
		}
	}

	/** One dragon fireball of a barrage, and the clock when it left. */
	public record Shot(UUID id, long at) {
	}

	/** One swoop pass: who it struck, the damage it handed the game for each, and the slowness it gave. */
	public record Swoop(long at, List<UUID> struck, List<Float> blows, int slownessAmplifier) {
	}

	/** One dive landing: the push it gave each creature near it, and the fire charges it threw round it. */
	public record Dive(long at, Vec3 impact, Map<UUID, Vec3> pushed, List<UUID> charges) {
	}

	/** One breath of a freed wyvern: its place, when, the cloud it made, and the player it aimed at. */
	public record Breath(int slot, long at, UUID cloud, UUID target) {
	}

	/** One swoop of a freed wyvern at a player: when it began, the player, and when it reached them or gave up. */
	public static final class WyvernSwoop {
		final int slot;
		final long startedAt;
		final UUID target;
		long hitAt = EndState.NEVER;
		long endedAt = EndState.NEVER;
		float dealt;

		WyvernSwoop(final int slot, final long startedAt, final UUID target) {
			this.slot = slot;
			this.startedAt = startedAt;
			this.target = target;
		}

		public int slot() {
			return slot;
		}

		public long startedAt() {
			return startedAt;
		}

		public UUID target() {
			return target;
		}

		/** The clock when the wyvern touched the player; never when the swoop ran out first. */
		public long hitAt() {
			return hitAt;
		}

		/** The health the bite took from the player; zero when the player was not hurt. */
		public float dealt() {
			return dealt;
		}

		public long endedAt() {
			return endedAt;
		}
	}

	/** One wind-up before a swoop or a dive: when it began, when the attack started, and the End particles it sent. */
	public static final class Windup {
		final Attack attack;
		final long startedAt;
		long launchedAt = EndState.NEVER;
		int bursts;
		int sent;

		Windup(final Attack attack, final long startedAt) {
			this.attack = attack;
			this.startedAt = startedAt;
		}

		public Attack attack() {
			return attack;
		}

		public long startedAt() {
			return startedAt;
		}

		public long launchedAt() {
			return launchedAt;
		}

		/** The particle bursts the dragon sent. */
		public int bursts() {
			return bursts;
		}

		/** The sum over those bursts of the players each one reached. */
		public int sent() {
			return sent;
		}
	}

	/** One airdrop: the archers and the wither skeletons it added. */
	public record Airdrop(long at, List<UUID> archers, List<UUID> withers) {
	}

	/** One perch: when it began, when the dragon touched down, when it ended, and the breath clouds it rained. */
	public static final class Perch {
		final long startedAt;
		long landedAt = EndState.NEVER;
		long endedAt = EndState.NEVER;
		final List<UUID> clouds = new ArrayList<>();

		Perch(final long startedAt) {
			this.startedAt = startedAt;
		}

		public long startedAt() {
			return startedAt;
		}

		public long landedAt() {
			return landedAt;
		}

		public long endedAt() {
			return endedAt;
		}

		public List<UUID> clouds() {
			return List.copyOf(clouds);
		}
	}

	/** One of the four wyvern places. A place holds a pair, or waits {@code respawnAt} to hold a new one. */
	public static final class Slot {
		@Nullable UUID wyvern;
		@Nullable UUID rider;
		long respawnAt = EndState.NEVER;
		/** When the rider died and left the wyvern free; never while the rider lives. */
		long freedAt = EndState.NEVER;
		long nextBreathAt = EndState.NEVER;
		long nextSwoopAt = EndState.NEVER;
		@Nullable WyvernSwoop swoop;

		public @Nullable UUID wyvern() {
			return wyvern;
		}

		public @Nullable UUID rider() {
			return rider;
		}

		public long respawnAt() {
			return respawnAt;
		}

		public long freedAt() {
			return freedAt;
		}

		public long nextBreathAt() {
			return nextBreathAt;
		}

		public long nextSwoopAt() {
			return nextSwoopAt;
		}
	}

	final UUID id;
	Mode mode = Mode.PASSIVE;
	long modeSince;
	/** The clock when the dragon last woke from passive; never before the first waking. */
	long awokeAt = EndState.NEVER;
	/** How many attacks the cycle has handed out. */
	int cycleIndex;
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
	/** The clock at which the airdrop let go of its mobs; never before. */
	long droppedAt = EndState.NEVER;
	Vec3 velocity = Vec3.ZERO;
	final List<AttackRecord> attacks = new ArrayList<>();
	final List<Shot> shots = new ArrayList<>();
	final List<Swoop> swoops = new ArrayList<>();
	final List<Dive> dives = new ArrayList<>();
	final List<Windup> windups = new ArrayList<>();
	final List<Airdrop> airdrops = new ArrayList<>();
	final List<Perch> perches = new ArrayList<>();
	final List<Breath> breaths = new ArrayList<>();
	final List<WyvernSwoop> wyvernSwoops = new ArrayList<>();
	/** The clock at which a wind-up ends and its attack starts; never when no wind-up runs. */
	long launchAt = EndState.NEVER;
	/** Where the dragon hangs once its wind-up has braked it; null until then. */
	@Nullable Vec3 hold;
	/** The clock at which the dragon next perches, and the delay that was drawn for it. */
	long perchAt;
	int perchDelay;
	long lastTick = EndState.NEVER;
	long nextCloudAt;
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

	public List<Windup> windups() {
		return List.copyOf(windups);
	}

	public List<Airdrop> airdrops() {
		return List.copyOf(airdrops);
	}

	public List<Perch> perches() {
		return List.copyOf(perches);
	}

	public long perchAt() {
		return perchAt;
	}

	public int perchDelay() {
		return perchDelay;
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

	private static final List<Attack> ORDER = List.of(Attack.SWOOP, Attack.BARRAGE, Attack.DIVE, Attack.AIRDROP);
	private static final List<Attack> ORDER_ROOSTED = List.of(Attack.SWOOP, Attack.BARRAGE, Attack.DIVE, Attack.AIRDROP, Attack.SWOOP, Attack.DIVE);

	/** The attacks in the order the dragon flies them. After a roost the swoop and the dive come twice in a round. */
	public List<Attack> cycle() {
		return roosted ? ORDER_ROOSTED : ORDER;
	}

	/** The attack the dragon flies next, and the cycle moves on. */
	Attack nextInCycle() {
		List<Attack> round = cycle();
		return round.get(cycleIndex++ % round.size());
	}

	public int cycleIndex() {
		return cycleIndex;
	}

	public long awokeAt() {
		return awokeAt;
	}

	/** Whether the dragon woke less than {@link DragonRules#WAKE_GRACE} ticks ago: it neither roosts nor perches yet. */
	boolean inGrace(final long now) {
		return awokeAt != EndState.NEVER && now - awokeAt < DragonRules.WAKE_GRACE;
	}

	public List<Breath> breaths() {
		return List.copyOf(breaths);
	}

	public List<WyvernSwoop> wyvernSwoops() {
		return List.copyOf(wyvernSwoops);
	}
}
