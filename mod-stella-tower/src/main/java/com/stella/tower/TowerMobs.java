package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's own mobs: who they are, how strong their floor makes them, and how many a floor holds.
 * <p>
 * A floor with a player on it is kept stocked, a few at a time, up to {@link #FLOOR_CAP}. A tower mob is a
 * mob of {@link #levelOf its floor's level}: the mobs mod's curves set its health and what it deals. Its
 * health is raised on its attribute as far as the game allows; the rest of its size is kept as a divisor
 * on every hit it takes. The tower adds only the rank's damage factor to what the mobs mod deals.
 */
public final class TowerMobs {
	public static final String TAG = "mod-stella-tower.mob";
	/** Mobs from the mobs mod roll no elites of their own in the tower; the tower decides that. */
	public static final String NO_SPAWN_ROLL_TAG = StellaMobs.NO_SPAWN_ROLL_TAG;
	/** The first floor is a level 20 mob, and each floor adds one level. */
	public static final int FIRST_FLOOR_LEVEL = 20;
	public static final int FLOOR_CAP = 72;
	public static final int SPAWN_INTERVAL_TICKS = 20;
	public static final int SPAWN_BATCH = 8;
	public static final int SPAWN_TRIES = 24;
	public static final double MIN_PLAYER_DISTANCE = 12.0;
	public static final int OVERWORLD_ONLY_UNTIL = 20;
	public static final Identifier HEALTH_ID = StellaTower.id("floor_health");
	public static final Identifier ARMOR_ID = StellaTower.id("floor_armor");
	public static final Identifier SPEED_ID = StellaTower.id("floor_speed");

	/** The overworld's hostiles, witches aside: all the first floors hold. */
	public static final List<EntityType<? extends Mob>> OVERWORLD = List.of(
		EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.CREEPER,
		EntityTypes.HUSK, EntityTypes.STRAY, EntityTypes.CAVE_SPIDER, EntityTypes.PILLAGER, EntityTypes.VINDICATOR
	);
	/**
	 * Joins from the twenty-first floor. Ghasts are too big for an eight-block room, and shulkers teleport
	 * by a road no room can close.
	 */
	public static final List<EntityType<? extends Mob>> LATER = List.of(
		EntityTypes.WITCH, EntityTypes.BLAZE, EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE,
		EntityTypes.MAGMA_CUBE, EntityTypes.HOGLIN, EntityTypes.ENDERMAN, EntityTypes.ENDERMITE
	);

	/** What a tower mob carries: its floor, its element, and the health its attribute could not hold. */
	public record State(int floor, TowerElement element, double healthDivisor, TowerRank rank) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("floor").forGetter(State::floor),
			TowerElement.CODEC.fieldOf("element").forGetter(State::element),
			Codec.DOUBLE.fieldOf("health_divisor").forGetter(State::healthDivisor),
			TowerRank.CODEC.optionalFieldOf("rank", TowerRank.MOB).forGetter(State::rank)
		).apply(instance, State::new));
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(
		StellaTower.id("tower_mob"), builder -> builder.persistent(State.CODEC).copyOnDeath()
	);

	private TowerMobs() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerMobs::tick);
		// A tower mob that becomes another, a split magma cube or a converted mob, stays a tower mob of its floor.
		// Fabric's own listener copies the old mob's attachments across in the default phase; this one runs after it,
		// so the rank set here is the one that stays.
		net.minecraft.resources.Identifier late = StellaTower.id("after_attachment_copy");
		ServerLivingEntityEvents.MOB_CONVERSION.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, late);
		ServerLivingEntityEvents.MOB_CONVERSION.register(late, (previous, converted, params) -> {
			State state = stateOf(previous);
			if (state != null) {
				// A split guardian's pieces are ordinary mobs: one boss is one boss, and one key.
				TowerRank rank = params.type() == net.minecraft.world.entity.ConversionType.SPLIT_ON_DEATH ? TowerRank.MOB : state.rank();
				makeTowerMob(converted, state.floor(), state.element(), rank);
			}
		});
	}

	public static @Nullable State stateOf(final LivingEntity entity) {
		return entity.getAttached(STATE);
	}

	/** The mob level of a floor. */
	public static int levelOf(final int floor) {
		return floor + FIRST_FLOOR_LEVEL - 1;
	}

	/** The kinds a floor may hold. */
	public static List<EntityType<? extends Mob>> pool(final int floor) {
		if (floor <= OVERWORLD_ONLY_UNTIL) {
			return OVERWORLD;
		}
		List<EntityType<? extends Mob>> all = new ArrayList<>(OVERWORLD);
		all.addAll(LATER);
		return all;
	}

	/** The most health an attribute may hold here: the live cap, divided by the rpg mod's health scale. */
	public static double healthCap() {
		double cap = ((net.minecraft.world.entity.ai.attributes.RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
		Object scale = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare().get("mod-stella-rpg:health-scale");
		double k = scale instanceof Double value && value > 0.0 ? value : 1.0;
		return cap / k;
	}

	/** Makes a mob a tower mob of this floor, in this element, at full health. */
	public static void makeTowerMob(final Mob mob, final int floor, final TowerElement element) {
		makeTowerMob(mob, floor, element, TowerRank.MOB);
	}

	/** Makes a mob a tower mob of this floor and rank, in this element, at full health. */
	public static void makeTowerMob(final Mob mob, final int floor, final TowerElement element, final TowerRank rank) {
		makeTowerMob(mob, floor, element, rank, levelOf(floor));
	}

	/** Makes a mob a tower mob of this floor and rank at a level of its own, as a lost room's mobs are. */
	public static void makeTowerMob(final Mob mob, final int floor, final TowerElement element, final TowerRank rank, final int level) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		double want = health.getBaseValue() * SpawnScaling.healthMultiplier(level) * rank.healthFactor();
		double held = Math.min(want, healthCap());
		// The floor alone sets a tower mob's health: a zombie leader's bonus or any other is set aside.
		for (AttributeModifier other : List.copyOf(health.getModifiers())) {
			// The rpg mod's health scale is not a bonus.
			if (!other.id().getNamespace().equals("mod-stella-rpg")) {
				health.removeModifier(other.id());
			}
		}
		health.addPermanentModifier(new AttributeModifier(HEALTH_ID, held - health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
		AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(ARMOR_ID);
			double room = Math.max(0.0, TowerCurve.ARMOR_CAP - armor.getValue());
			armor.addPermanentModifier(new AttributeModifier(ARMOR_ID, Math.min(room, TowerCurve.armorBonus(floor)), AttributeModifier.Operation.ADD_VALUE));
		}
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
			speed.addPermanentModifier(new AttributeModifier(SPEED_ID, TowerCurve.speedBonus(floor), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setAttached(STATE, new State(floor, element, want / held, rank));
		mob.addTag(TAG);
		mob.addTag(NO_SPAWN_ROLL_TAG);
		// Its hits take the mobs mod's level path; the health is the tower's own.
		mob.addTag(StellaMobs.SELF_SCALED_TAG);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(level, 0));
		mob.setPersistenceRequired();
		net.minecraft.network.chat.MutableComponent name = Component.empty();
		if (rank != TowerRank.MOB) {
			name.append(Component.literal(rank.label() + " ").withStyle(rank.color()));
		}
		name.append(Component.literal(element.label() + " ").withStyle(element.color())).append(mob.getType().getDescription());
		mob.setCustomName(name);
	}

	/** Sends away anything riding the mob or carrying it, so a jockey never enters the tower half made. */
	public static void settle(final Mob mob) {
		for (Entity rider : List.copyOf(mob.getPassengers())) {
			rider.stopRiding();
			rider.discard();
		}
		Entity vehicle = mob.getVehicle();
		if (vehicle != null) {
			mob.stopRiding();
			vehicle.discard();
		}
	}

	/** Raises one tower mob on the floor, somewhere clear and away from every player; null if none fit. */
	public static @Nullable Mob spawnOne(final ServerLevel tower, final int floor) {
		RandomSource random = tower.getRandom();
		List<EntityType<? extends Mob>> pool = pool(floor);
		EntityType<? extends Mob> type = pool.get(random.nextInt(pool.size()));
		Mob mob = type.create(tower, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		BlockPos origin = TowerLayout.origin(floor);
		// Finalized first: a magma cube's size, and so the room it needs, is only drawn here.
		BlockPos middle = origin.offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2);
		mob.snapTo(Vec3.atBottomCenterOf(middle));
		mob.finalizeSpawn(tower, tower.getCurrentDifficultyAt(middle), EntitySpawnReason.EVENT, null);
		settle(mob);
		if (!placeInRoom(tower, floor, mob, random)) {
			mob.discard();
			return null;
		}
		makeTowerMob(mob, floor, TowerElement.values()[random.nextInt(TowerElement.values().length)]);
		return tower.addFreshEntity(mob) ? mob : null;
	}

	/** Finds the mob a spot on the floor, clear and away from every player; false if none fit in the tries. */
	public static boolean placeInRoom(final ServerLevel tower, final int floor, final Mob mob, final RandomSource random) {
		BlockPos origin = TowerLayout.origin(floor);
		List<ServerPlayer> players = playersOn(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		Vec3 arrival = plan.arrivalPos();
		for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
			int x = origin.getX() + 1 + random.nextInt(TowerLayout.SIZE - 2);
			int z = origin.getZ() + 1 + random.nextInt(TowerLayout.SIZE - 2);
			BlockPos feet = new BlockPos(x, TowerLayout.BASE_Y, z);
			Vec3 spot = spotFor(mob, feet);
			if (!tower.isLoaded(feet) || spot.distanceTo(arrival) < MIN_PLAYER_DISTANCE || tooClose(players, spot)
				|| inSafeZone(plan, mob, spot)) {
				continue;
			}
			mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
			if (standsFree(tower, mob, feet)) {
				return true;
			}
		}
		return false;
	}

	/** Whether any part of a mob standing here would be inside one of the floor's safe zones. */
	static boolean inSafeZone(final TowerPlan plan, final Mob mob, final Vec3 spot) {
		BlockPos origin = plan.origin();
		double half = mob.getBbWidth() / 2.0;
		int lowX = (int) Math.floor(spot.x - half) - origin.getX();
		int highX = (int) Math.floor(spot.x + half) - origin.getX();
		int lowZ = (int) Math.floor(spot.z - half) - origin.getZ();
		int highZ = (int) Math.floor(spot.z + half) - origin.getZ();
		for (int x = lowX; x <= highX; x++) {
			for (int z = lowZ; z <= highZ; z++) {
				if (plan.inSafeZone(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Where on a block a mob stands: its middle, or for a mob broader than a block its corner, which is the
	 * middle of a two by two path; at a block's middle such a mob would need three blocks clear.
	 */
	public static Vec3 spotFor(final Mob mob, final BlockPos feet) {
		return mob.getBbWidth() > 1.0F ? new Vec3(feet.getX(), feet.getY(), feet.getZ()) : Vec3.atBottomCenterOf(feet);
	}

	/** On solid ground, with the mob's whole body clear of every block. */
	static boolean standsFree(final ServerLevel tower, final Mob mob, final BlockPos feet) {
		return tower.getBlockState(feet.below()).isFaceSturdy(tower, feet.below(), net.minecraft.core.Direction.UP)
			&& tower.noCollision(mob, mob.getBoundingBox());
	}

	private static boolean tooClose(final List<ServerPlayer> players, final Vec3 spot) {
		for (ServerPlayer player : players) {
			if (player.position().distanceTo(spot) < MIN_PLAYER_DISTANCE) {
				return true;
			}
		}
		return false;
	}

	static List<ServerPlayer> playersOn(final ServerLevel tower, final int floor) {
		List<ServerPlayer> on = new ArrayList<>();
		for (ServerPlayer player : tower.players()) {
			if (!player.isSpectator() && TowerLayout.roomAt(player.blockPosition()) == floor) {
				on.add(player);
			}
		}
		return on;
	}

	static AABB room(final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		return new AABB(o.getX(), o.getY() - 1, o.getZ(), o.getX() + TowerLayout.SIZE, o.getY() + TowerLayout.HEIGHT, o.getZ() + TowerLayout.SIZE);
	}

	/** Tower mobs standing on the floor now; its own record, not a kind, says which floor a mob is of. */
	public static int count(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(Mob.class, room(floor), mob -> {
			State state = stateOf(mob);
			// A ghost stands on the players' side, outside the floor's cap.
			return mob.isAlive() && state != null && state.floor() == floor && state.rank() == TowerRank.MOB && !(mob instanceof TowerGhost);
		}).size();
	}

	/** The test seam: a floor's pinned mob cap; null restores the shipped one. */
	private static final Map<Integer, Integer> PINNED_CAP = new java.util.concurrent.ConcurrentHashMap<>();

	public static void pinFloorCap(final int floor, final @Nullable Integer cap) {
		if (cap == null) {
			PINNED_CAP.remove(floor);
		} else {
			PINNED_CAP.put(floor, cap);
		}
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % SPAWN_INTERVAL_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		Map<Integer, Boolean> occupied = new HashMap<>();
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor > 0 && !player.isSpectator() && TowerFloors.isBuilt(tower, floor)) {
				occupied.put(floor, true);
			}
		}
		for (int floor : occupied.keySet()) {
			// The whole room is held loaded while it is occupied, so no mob in it goes uncounted.
			BlockPos middle = TowerLayout.origin(floor).offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2);
			tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(middle), TowerFloors.CHUNK_RADIUS);
			TowerGhosts.haunt(tower, floor);
			TowerShamans.haunt(tower, floor);
			int room = PINNED_CAP.getOrDefault(floor, FLOOR_CAP) - count(tower, floor);
			for (int i = 0; i < Math.min(room, SPAWN_BATCH); i++) {
				spawnOne(tower, floor);
			}
		}
	}

	/** A hit's damage once the tower has had its say: a tower mob's rank hits harder, and a big one takes less. */
	public static float scaleDamage(final LivingEntity victim, final DamageSource source, final float amount) {
		// A weapon rune multiplies the blow as it is struck; armor runes turn a tower mob's blow as it lands.
		// Stored heat is added after the tier, so a weapon rune does not multiply it.
		float scaled = RuneEffects.soaked(victim, source, RuneGuards.kindle(victim, source, TowerRunes.weapon(victim, source, amount)));
		if (source.getEntity() instanceof LivingEntity attacker && attacker != victim && !TowerBuffs.reflecting()) {
			State from = stateOf(attacker);
			if (from != null) {
				// The level part comes from the mobs mod; adding it here would scale the hit twice.
				scaled = (float) (scaled * from.rank().damageFactor());
				scaled = TowerRunes.armor(victim, from.element(), scaled);
			}
		}
		State to = stateOf(victim);
		if (to != null && to.healthDivisor() > 1.0) {
			scaled = (float) (scaled / to.healthDivisor());
		}
		if (to != null) {
			scaled = (float) (scaled / TowerBuffs.healthFactor(victim));
		}
		return RuneGuards.guard(victim, source, scaled);
	}
}
