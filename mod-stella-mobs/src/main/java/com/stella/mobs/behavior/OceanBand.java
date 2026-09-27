package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * O01, the dolphin-riding skeleton band: three to five skeletons in armour, each on its own dolphin, each
 * with a bow or an axe and always a helmet. Every 2x2 area of ocean chunks rolls once, as a player comes
 * near, and holds a band two times in a hundred. One band in twenty is led by an elite, or now and then a
 * boss, who always swings an axe and rides a very fast dolphin. The helmet is forced because the feature
 * rests on it: any head item stops the daylight burn. The water breathing rides a flagless goal, because a
 * riding skeleton breathes air and would drown on its first dive.
 */
public final class OceanBand {
	public static final int MIN_RIDERS = 3;
	public static final int MAX_RIDERS = 5;
	/** A band in two of every hundred 2x2 areas of ocean. */
	public static final double REGION_CHANCE = 0.02;
	/** One band in twenty has exactly one leader, an elite or a boss. */
	public static final double LEADER_CHANCE = 0.05;
	/** The share of leaders that are bosses rather than elites. */
	public static final double BOSS_LEADER_SHARE = 0.25;
	/** The leader's dolphin: this much faster than its kind. */
	public static final double FAST_DOLPHIN_BONUS = 1.5;
	public static final Identifier FAST_DOLPHIN_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "band_leader_dolphin");
	public static final int BREATHING_TICKS = 400;
	public static final int BREATHING_CHECK_TICKS = 100;
	public static final int BREATHING_PRIORITY = 5;
	public static final float CAPTAIN_DROP_CHANCE = Elite.KIT_DROP_CHANCE + Elite.GEAR_DROP_BONUS;
	public static final float MEMBER_DROP_CHANCE = EscortSquad.DROP_CHANCE + Elite.GEAR_DROP_BONUS;
	public static final double BAND_RADIUS = 48.0;
	private static final long REGION_SALT = 0x0CEA_2B41_7D00L;
	private static final int SCATTER = 4;
	private static final double SPAWN_OFFSET = 6.0;
	private static final AttachmentType<List<Pending>> QUEUE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "ocean_band_queue"), ArrayList::new
	);
	private static final AttachmentType<Band> BANDS = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "ocean_band_members"),
		() -> new Band(0L, List.of(), List.of())
	);

	/** Who leads a band. */
	public enum Leader { NONE, ELITE, BOSS }

	/** The chance is a field so a test can pin it; play always reads the constant. */
	private static double chance = REGION_CHANCE;

	private OceanBand() {
	}

	/** The dolphin that triggers a band, held for one tick so the drain runs outside its own add; a test may pin its size and leader. */
	public record Pending(Dolphin mount, int size, @Nullable Leader leader) {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the ocean band attachments {} and {}", QUEUE.identifier(), BANDS.identifier());
	}

	/** Whether a 2x2 area holds a band, from its own random. */
	public static boolean rollsBand(final RandomSource random) {
		return random.nextDouble() < chance;
	}

	/** Who leads a band, from its own random. */
	public static Leader rollLeader(final RandomSource random) {
		if (random.nextDouble() >= LEADER_CHANCE) {
			return Leader.NONE;
		}
		return random.nextDouble() < BOSS_LEADER_SHARE ? Leader.BOSS : Leader.ELITE;
	}

	/**
	 * Called once for each chunk the encounter scan marks. The chunk at the even corner of each 2x2 area speaks
	 * for its area: over ocean, and on its roll, a dolphin rises at the surface and the band gathers round it.
	 */
	public static @Nullable Dolphin rollRegion(final ServerLevel level, final LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		if ((pos.x() & 1) != 0 || (pos.z() & 1) != 0) {
			return null;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ REGION_SALT ^ (pos.pack() * 0x9E3779B97F4A7C15L));
		if (!rollsBand(random)) {
			return null;
		}
		int x = pos.getMiddleBlockX();
		int z = pos.getMiddleBlockZ();
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		BlockPos water = new BlockPos(x, surface - 1, z);
		if (!level.getBiome(water).is(BiomeTags.IS_OCEAN) || !level.getFluidState(water).is(FluidTags.WATER)) {
			return null;
		}
		Dolphin dolphin = EntityTypes.DOLPHIN.create(level, EntitySpawnReason.EVENT);
		if (dolphin == null) {
			return null;
		}
		dolphin.snapTo(x + 0.5, water.getY(), z + 0.5, random.nextFloat() * 360.0F, 0.0F);
		dolphin.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		if (level.addFreshEntity(dolphin)) {
			queue(level, dolphin, 0, null);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=oceanBand REGION chunk={} at={}", pos, water);
			return dolphin;
		}
		return null;
	}

	/** Queues one dolphin, once. A dolphin that already carries a band never starts a second one. */
	private static void queue(final ServerLevel level, final Dolphin dolphin, final int size, final @Nullable Leader leader) {
		List<Pending> pending = ((AttachmentTarget) level).getAttachedOrCreate(QUEUE);

		for (Pending item : pending) {
			if (item.mount() == dolphin) {
				return;
			}
		}

		pending.add(new Pending(dolphin, size, leader));
	}

	/** Builds every band queued on the previous tick, once the level's own entity pass has finished. */
	public static void drain(final ServerLevel level) {
		List<Pending> pending = ((AttachmentTarget) level).getAttached(QUEUE);
		if (pending == null || pending.isEmpty()) {
			return;
		}

		List<Pending> batch = List.copyOf(pending);
		pending.clear();

		for (Pending item : batch) {
			if (item.mount().isAlive() && !item.mount().isRemoved()) {
				build(level, item.mount(), item.size(), item.leader());
			}
		}
	}

	/** The test seam: pins the region chance. */
	public static void pinChance(final double value) {
		chance = value;
	}



	/** Queues a band on a dolphin the caller owns. Used by the band test, which builds its own arena. */
	public static void queueForTest(final Dolphin dolphin, final int size, final @Nullable Leader leader) {
		if (dolphin.level() instanceof ServerLevel level) {
			queue(level, dolphin, size, leader);
		}
	}

	/** The breathing goal the band adds to every member. Exposed so a test can mount the same goal. */
	public static Goal breathingGoal(final Mob member) {
		return new RidingBreathingGoal(member);
	}

	/** One band's identity, so a query can never count a band still standing in the arena. */
	public record Band(long id, List<Dolphin> mounts, List<Skeleton> members) {
	}

	/** The band the drain built around one anchor, or null when the anchor started none. */
	public static @Nullable Band bandOf(final Dolphin origin) {
		return ((AttachmentTarget) origin).getAttached(BANDS);
	}

	/** The band's mounts, read from the recorded band so no second band can be counted. */
	public static int mountsOf(final Dolphin origin) {
		Band band = bandOf(origin);
		return band == null ? 0 : band.mounts().size();
	}

	/** The band's seated members, read from the recorded band. */
	public static List<Skeleton> membersOf(final Dolphin origin) {
		Band band = bandOf(origin);
		return band == null ? List.of() : band.members();
	}

	/** One line per seated member, for a test that has to see the band rather than a count. */
	public static String describeBand(final Dolphin origin) {
		StringBuilder text = new StringBuilder();

		for (Skeleton member : membersOf(origin)) {
			text.append(member.getUUID())
				.append('/')
				.append(member.getMainHandItem().isEmpty() ? "empty" : member.getMainHandItem().getItem())
				.append('/')
				.append(member.getVehicle() == null ? "none" : member.getVehicle().getUUID())
				.append(' ');
		}

		return text.toString().trim();
	}

	private static void build(final ServerLevel level, final Dolphin first, final int pinnedSize, final @Nullable Leader pinnedLeader) {
		RandomSource random = first.getRandom();
		int size = pinnedSize > 0 ? pinnedSize : MIN_RIDERS + random.nextInt(MAX_RIDERS - MIN_RIDERS + 1);
		Leader leader = pinnedLeader != null ? pinnedLeader : rollLeader(random);
		GearTier leaderTier = Elite.pickTier(random);
		GearTier memberTier = Elite.escortTier(leaderTier);
		List<Dolphin> mounts = new ArrayList<>();
		mounts.add(first);

		for (int i = 1; i < size; i++) {
			Dolphin mount = mount(level, first);
			if (mount != null) {
				mounts.add(mount);
			}
		}

		int bows = 0;
		int axes = 0;
		List<Skeleton> members = new ArrayList<>();
		List<Dolphin> seated = new ArrayList<>(mounts);

		for (int i = 0; i < mounts.size(); i++) {
			boolean leads = leader != Leader.NONE && i == mounts.size() - 1;
			boolean bow = !leads && random.nextBoolean();
			Skeleton member = skeleton(level, first, leads ? leader : Leader.NONE, bow, leads ? leaderTier : memberTier);
			if (member == null || !member.startRiding(mounts.get(i), true, false)) {
				continue;
			}
			members.add(member);
			if (leads) {
				AttributeInstance speed = mounts.get(i).getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null && speed.getModifier(FAST_DOLPHIN_ID) == null) {
					speed.addPermanentModifier(new AttributeModifier(FAST_DOLPHIN_ID, FAST_DOLPHIN_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				}
			} else if (bow) {
				bows++;
			} else {
				axes++;
			}
		}

		((AttachmentTarget) first).setAttached(BANDS, new Band(level.getGameTime(), seated, members));

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=oceanBand BAND mounts={} bows={} axes={} leader={} memberTier={}",
			seated.size(), bows, axes, leader, memberTier
		);
	}

	@SuppressWarnings("unchecked")
	private static Dolphin mount(final ServerLevel level, final Dolphin origin) {
		RandomSource random = origin.getRandom();
		EntityType<? extends Mob> type = (EntityType<? extends Mob>) origin.getType();
		Mob created = type.create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof Dolphin dolphin)) {
			return null;
		}

		dolphin.snapTo(
			origin.getX() + (random.nextDouble() * 2.0 - 1.0) * SPAWN_OFFSET,
			origin.getY(),
			origin.getZ() + (random.nextDouble() * 2.0 - 1.0) * SPAWN_OFFSET,
			random.nextFloat() * 360.0F,
			0.0F
		);
		dolphin.finalizeSpawn(
			level, level.getCurrentDifficultyAt(dolphin.blockPosition()), EntitySpawnReason.EVENT, null
		);
		EliteState.setSummoned(dolphin, true);
		level.addFreshEntity(dolphin);
		return dolphin;
	}

	private static Skeleton skeleton(
		final ServerLevel level,
		final Dolphin origin,
		final Leader leader,
		final boolean bow,
		final GearTier tier
	) {
		RandomSource random = origin.getRandom();
		Mob created = EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof Skeleton skeleton)) {
			return null;
		}

		skeleton.snapTo(
			origin.getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			origin.getY(),
			origin.getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			random.nextFloat() * 360.0F,
			0.0F
		);
		skeleton.finalizeSpawn(
			level, level.getCurrentDifficultyAt(skeleton.blockPosition()), EntitySpawnReason.EVENT, null
		);
		EliteState.setSummoned(skeleton, true);
		ReactiveState.get(skeleton).setGearApplied(true);
		GearRoller.rollSet(
			skeleton, tier, leader == Leader.NONE ? MEMBER_DROP_CHANCE : CAPTAIN_DROP_CHANCE, random,
			level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
		);
		if (leader == Leader.BOSS) {
			Bosses.blessAsBoss(skeleton, level, 1.0);
		} else if (leader == Leader.ELITE) {
			Elite.mark(skeleton, tier, Elite.HEALTH_MULTIPLIER);
		}
		// Forced after every roll and kit: the leader swings an axe, a member holds what it drew, and every
		// head wears a helmet, since a bare head ignites on the first dawn check.
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(bow ? Items.BOW : Items.IRON_AXE));
		if (skeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GearTier.IRON.armorFor(EquipmentSlot.HEAD)));
		}
		skeleton.getGoalSelector().addGoal(BREATHING_PRIORITY, new RidingBreathingGoal(skeleton));
		level.addFreshEntity(skeleton);
		return skeleton;
	}

	/** Water breathing while the member has a vehicle, because the dive is long and a rider breathes air. */
	private static final class RidingBreathingGoal extends Goal {
		private final Mob member;
		private long nextCheckTick;

		private RidingBreathingGoal(final Mob member) {
			this.member = member;
		}

		@Override
		public boolean canUse() {
			return this.member.isPassenger() && this.member.tickCount >= this.nextCheckTick;
		}

		@Override
		public boolean canContinueToUse() {
			return this.member.isPassenger();
		}

		@Override
		public void start() {
			this.nextCheckTick = this.member.tickCount + BREATHING_CHECK_TICKS;
			// The effect outlasts the check, so the member is never briefly out of air.
			this.member.addEffect(
				new MobEffectInstance(MobEffects.WATER_BREATHING, BREATHING_TICKS, 0, false, false), this.member
			);
		}
	}
}
