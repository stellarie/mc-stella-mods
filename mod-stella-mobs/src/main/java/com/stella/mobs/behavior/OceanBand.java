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
 * O01, the dolphin-riding skeleton band: one captain and six members, three with a bow and three with a
 * sword, each on its own dolphin, spawned sporadically over an ocean. The helmet is forced because the
 * whole feature rests on it: any non-empty head item stops the daylight burn, and a rolled kit leaves a
 * member bare headed at a thirty percent per slot chance. The water breathing rides a flagless goal,
 * because a riding skeleton breathes air and would drown on its first dive.
 */
public final class OceanBand {
	public static final int MEMBERS = 6;
	public static final int RANGED_MEMBERS = 3;
	public static final int BAND_SIZE = MEMBERS + 1;
	/** Sporadic: one attempt in sixty natural dolphin spawns, and one band per level per five minutes. */
	public static final double SPAWN_CHANCE = 1.0 / 60.0;
	public static final long COOLDOWN_TICKS = 6000L;
	public static final int BREATHING_TICKS = 400;
	public static final int BREATHING_CHECK_TICKS = 100;
	public static final int BREATHING_PRIORITY = 5;
	public static final float CAPTAIN_DROP_CHANCE = Elite.KIT_DROP_CHANCE + Elite.GEAR_DROP_BONUS;
	public static final float MEMBER_DROP_CHANCE = EscortSquad.DROP_CHANCE + Elite.GEAR_DROP_BONUS;
	public static final double BAND_RADIUS = 48.0;
	private static final int SCATTER = 4;
	private static final double SPAWN_OFFSET = 6.0;
	private static final AttachmentType<List<Pending>> QUEUE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "ocean_band_queue"), ArrayList::new
	);
	private static final AttachmentType<Long> NEXT_ATTEMPT = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "ocean_band_next_attempt"), () -> 0L
	);
	private static final AttachmentType<Band> BANDS = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "ocean_band_members"),
		() -> new Band(0L, List.of(), List.of())
	);
	/** The chance is a field so a test can pin it; play always reads the constant. */
	private static double chance = SPAWN_CHANCE;

	private OceanBand() {
	}

	/** The dolphin that triggers a band, held for one tick so the drain runs outside its own add. */
	public record Pending(Dolphin mount) {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the ocean band attachments {} and {}", QUEUE.identifier(), NEXT_ATTEMPT.identifier());
	}

	/** Called for every fresh natural dolphin. The chance, the cooldown and the biome all have to agree. */
	public static void trySpawn(final ServerLevel level, final Dolphin dolphin) {
		RandomSource random = dolphin.getRandom();
		if (random.nextDouble() >= chance) {
			return;
		}

		long now = level.getGameTime();
		if (now < nextAttempt(level)) {
			return;
		}

		if (!level.getBiome(dolphin.blockPosition()).is(BiomeTags.IS_OCEAN)) {
			return;
		}

		((AttachmentTarget) level).setAttached(NEXT_ATTEMPT, now + COOLDOWN_TICKS);
		queue(level, dolphin);
	}

	/** Queues one dolphin, once. A dolphin that already carries a band never starts a second one. */
	private static void queue(final ServerLevel level, final Dolphin dolphin) {
		List<Pending> pending = ((AttachmentTarget) level).getAttachedOrCreate(QUEUE);

		for (Pending item : pending) {
			if (item.mount() == dolphin) {
				return;
			}
		}

		pending.add(new Pending(dolphin));
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
				build(level, item.mount());
			}
		}
	}

	/** The test seam: pins the chance, and clears the cooldown so one attempt builds a band. */
	public static void pinChance(final double value) {
		chance = value;
	}

	static void clearCooldown(final ServerLevel level) {
		((AttachmentTarget) level).setAttached(NEXT_ATTEMPT, 0L);
	}

	/** Queues a band on a dolphin the caller owns. Used by the band test, which builds its own arena. */
	public static void queueForTest(final Dolphin dolphin) {
		if (dolphin.level() instanceof ServerLevel level) {
			queue(level, dolphin);
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

	private static void build(final ServerLevel level, final Dolphin first) {
		GearTier captainTier = Elite.pickTier(level.getRandom());
		GearTier memberTier = Elite.escortTier(captainTier);
		List<Dolphin> mounts = new ArrayList<>();
		mounts.add(first);

		for (int i = 1; i < BAND_SIZE; i++) {
			Dolphin mount = mount(level, first);
			if (mount != null) {
				mounts.add(mount);
			}
		}

		int ranged = 0;
		int melee = 0;
		List<Skeleton> members = new ArrayList<>();
		// The first mount is the anchor the queue came from, so it is always one of the seven.
		List<Dolphin> seated = new ArrayList<>(mounts);

		for (int i = 0; i < mounts.size(); i++) {
			boolean captain = i == mounts.size() - 1;
			boolean isRanged = !captain && ranged < RANGED_MEMBERS;
			Skeleton member = skeleton(level, first, captain, isRanged, captainTier, memberTier);
			if (member == null || !member.startRiding(mounts.get(i), true, false)) {
				continue;
			}

			members.add(member);

			if (!captain) {
				if (isRanged) {
					ranged++;
				} else {
					melee++;
				}
			}
		}

		((AttachmentTarget) first).setAttached(BANDS, new Band(level.getGameTime(), seated, members));

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=oceanBand BAND mounts={} ranged={} melee={} captainTier={} memberTier={} captainDrop={} memberDrop={}",
			seated.size(), ranged, melee, captainTier, memberTier, CAPTAIN_DROP_CHANCE, MEMBER_DROP_CHANCE
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
		final boolean captain,
		final boolean ranged,
		final GearTier captainTier,
		final GearTier memberTier
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
		GearTier tier = captain ? captainTier : memberTier;
		float drop = captain ? CAPTAIN_DROP_CHANCE : MEMBER_DROP_CHANCE;

		if (captain) {
			Elite.mark(skeleton, tier, Elite.HEALTH_MULTIPLIER);
		}

		GearRoller.rollSet(
			skeleton, tier, drop, random, level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
		);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ranged ? Items.BOW : Items.IRON_SWORD));
		// Forced after the roll: a bare head ignites on the first dawn check.
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GearTier.IRON.armorFor(EquipmentSlot.HEAD)));
		skeleton.getGoalSelector().addGoal(BREATHING_PRIORITY, new RidingBreathingGoal(skeleton));
		level.addFreshEntity(skeleton);
		return skeleton;
	}

	private static long nextAttempt(final ServerLevel level) {
		Long value = ((AttachmentTarget) level).getAttached(NEXT_ATTEMPT);
		return value == null ? 0L : value;
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
