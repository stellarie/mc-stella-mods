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
import net.minecraft.world.level.ClipContext;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's own mobs: who they are, how strong their floor makes them, and how many a floor holds.
 * <p>
 * A floor with a player on it is kept stocked, a few at a time, up to {@link #floorCap its cap}: {@link #FLOOR_CAP} on a
 * base-size floor and more on a larger one, in step with its area. A tower mob is a
 * mob of {@link #levelOf its floor's level}: the mobs mod's curves set its health and what it deals. Its
 * health is raised on its attribute as far as the game allows; the rest of its size is kept as a divisor
 * on every hit it takes. The tower adds only the rank's damage factor to what the mobs mod deals.
 */
public final class TowerMobs {
	public static final String TAG = "mod-stella-tower.mob";
	/** Mobs from the mobs mod roll no elites of their own in the tower; the tower decides that. */
	public static final String NO_SPAWN_ROLL_TAG = StellaMobs.NO_SPAWN_ROLL_TAG;
	/** The first floor is a level 50 mob. */
	public static final int FIRST_FLOOR_LEVEL = 50;
	/** A lost room's level is its floor plus this, less one. The room keeps this rule apart from the floors'. */
	public static final int LOST_ROOM_FLOOR_OFFSET = 20;
	/** The mobs a base-size floor holds; a larger floor holds this many times its area against the base floor's. */
	public static final int FLOOR_CAP = 72;
	public static final int SPAWN_INTERVAL_TICKS = 20;
	/** The mobs a base-size floor gains in a round of spawning; a larger floor gains more, so it fills in the same time. */
	public static final int SPAWN_BATCH = 8;
	public static final int SPAWN_TRIES = 24;
	public static final double MIN_PLAYER_DISTANCE = 12.0;
	/** On a biome floor, which has no safe zone past floor 1, no mob is raised this near the arrival. */
	public static final double BIOME_ARRIVAL_CLEAR = 16.0;
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
	 * Joins from the twenty-first floor. Ghasts are too big for an eight-block room. A shulker's teleports are held
	 * to its floor's room by {@code TowerShulkerMixin}.
	 */
	public static final List<EntityType<? extends Mob>> LATER = List.of(
		EntityTypes.WITCH, EntityTypes.BLAZE, EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE,
		EntityTypes.MAGMA_CUBE, EntityTypes.HOGLIN, EntityTypes.ENDERMAN, EntityTypes.BREEZE, EntityTypes.SHULKER
	);
	/** A shulker teleports to rest no higher than this over the base: past it no player could reach it. */
	public static final int SHULKER_CEILING = 14;
	/**
	 * On a biome floor the first tries at a spot pass over one that a player on the floor sees from where they stand: a
	 * mob is raised out of sight, and the last tries take any spot so a floor with no hiding place is still stocked.
	 */
	public static final int HIDDEN_TRIES = 16;
	/** The height of a player's eyes over their feet, for the sight lines of the mob's placement. */
	private static final double EYE_HEIGHT = 1.62;

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

	/**
	 * The mob level of a floor: 50 on floor 1, one more per floor, ten more at each tenth floor, and fifty
	 * instead of ten at each fiftieth.
	 */
	public static int levelOf(final int floor) {
		int tens = (floor - 1) / 10;
		int fifties = (floor - 1) / 50;
		return FIRST_FLOOR_LEVEL + (floor - 1) + 10 * (tens - fifties) + 50 * fifties;
	}

	/**
	 * The kinds a floor of the tower raises: its biome's, a great hall's those of the biome below it. A floor drawn before
	 * the biome floors keeps the pool of its number.
	 */
	public static List<EntityType<? extends Mob>> poolOf(final ServerLevel tower, final int floor) {
		if (floor < 1) {
			return pool(floor);
		}
		TowerPlan plan = TowerPlan.atQuiet(tower, floor);
		if (!plan.isBiome()) {
			return pool(floor);
		}
		FloorBiome biome = plan.biome().biome();
		return BiomePools.of(biome == FloorBiome.GREAT_HALL ? FloorBiomes.of(plan.seed(), floor - 1) : biome);
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

	/**
	 * Makes a mob that is not the tower's own a tower mob of this floor, as it is: its floor, element and rank, and the
	 * marks that keep the mobs mod's rolls off it. Its health, attributes and hits are its maker's, and are not touched.
	 */
	public static void adopt(final Mob mob, final int floor, final TowerElement element, final TowerRank rank) {
		mob.setAttached(STATE, new State(floor, element, 1.0, rank));
		mob.addTag(TAG);
		mob.addTag(NO_SPAWN_ROLL_TAG);
		mob.setPersistenceRequired();
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
		TowerPlan plan = TowerPlan.at(tower, floor);
		RandomSource random = tower.getRandom();
		List<EntityType<? extends Mob>> pool = poolOf(tower, floor);
		EntityType<? extends Mob> type = pool.get(random.nextInt(pool.size()));
		Mob mob = type.create(tower, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		BlockPos origin = TowerLayout.origin(floor);
		// Finalized first: a magma cube's size, and so the room it needs, is only drawn here.
		BlockPos middle = origin.offset(plan.width() / 2, 0, plan.depth() / 2);
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

	/** Finds the mob a spot on any story of the floor, clear and away from every player; false if none fit in the tries. */
	public static boolean placeInRoom(final ServerLevel tower, final int floor, final Mob mob, final RandomSource random) {
		BlockPos origin = TowerLayout.origin(floor);
		List<ServerPlayer> players = playersOn(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		Vec3 arrival = plan.arrivalPos();
		List<TowerStory> stories = plan.stories();
		List<Vec3> eyes = new ArrayList<>();
		for (ServerPlayer player : players) {
			eyes.add(player.getEyePosition());
		}
		if (eyes.isEmpty()) {
			eyes.add(arrival.add(0.0, EYE_HEIGHT, 0.0));
		}
		for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
			int x = origin.getX() + 1 + random.nextInt(plan.width() - 2);
			int z = origin.getZ() + 1 + random.nextInt(plan.depth() - 2);
			int sy = plan.isBiome() ? plan.standY(x - origin.getX(), z - origin.getZ()) : stories.get(random.nextInt(stories.size())).y();
			if (sy < TowerWalk.LOW || plan.isBiome() && plan.biome().inDais(plan, x - origin.getX(), z - origin.getZ())) {
				continue;
			}
			BlockPos feet = new BlockPos(x, TowerLayout.BASE_Y + sy, z);
			Vec3 spot = spotFor(mob, feet);
			double clear = plan.isBiome() ? BIOME_ARRIVAL_CLEAR : MIN_PLAYER_DISTANCE;
			if (!tower.isLoaded(feet) || spot.distanceTo(arrival) < clear || tooClose(players, spot)
				|| inSafeZone(plan, mob, spot) || TowerMerchants.forbids(tower, spot)) {
				continue;
			}
			mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
			if (standsFree(tower, mob, feet)) {
				if (plan.isBiome() && attempt < HIDDEN_TRIES && seenFrom(tower, mob, eyes)) {
					continue;
				}
				return true;
			}
		}
		return false;
	}

	/** Whether any of the eyes has a clear line to the mob's eyes. */
	private static boolean seenFrom(final ServerLevel tower, final Mob mob, final List<Vec3> eyes) {
		Vec3 to = mob.getEyePosition();
		for (Vec3 eye : eyes) {
			if (tower.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a body in the tower may land at a spot by a teleport of its own: in the room of the floor it stands in, from
	 * the base up to {@code ceiling} blocks over it and under the floor's roof. Outside the tower there is no rule.
	 */
	public static boolean mayLandAt(final LivingEntity self, final BlockPos target, final int ceiling) {
		if (!(self.level() instanceof ServerLevel tower) || !tower.dimension().equals(TowerTravel.TOWER)) {
			return true;
		}
		int floor = TowerLayout.floorAt(self.blockPosition());
		if (floor <= 0) {
			return false;
		}
		BlockPos origin = TowerLayout.origin(floor);
		TowerPlan plan = TowerPlan.atQuiet(tower, floor);
		int top = TowerLayout.BASE_Y + Math.min(ceiling, plan.roofY());
		return target.getX() >= origin.getX() && target.getX() < origin.getX() + plan.width()
			&& target.getZ() >= origin.getZ() && target.getZ() < origin.getZ() + plan.depth()
			&& target.getY() >= TowerLayout.BASE_Y && target.getY() < top;
	}

	/** Whether any part of a mob standing here would be inside one of the floor's safe zones. */
	static boolean inSafeZone(final TowerPlan plan, final Mob mob, final Vec3 spot) {
		BlockPos origin = plan.origin();
		double half = mob.getBbWidth() / 2.0;
		int lowX = (int) Math.floor(spot.x - half) - origin.getX();
		int highX = (int) Math.floor(spot.x + half) - origin.getX();
		int lowZ = (int) Math.floor(spot.z - half) - origin.getZ();
		int highZ = (int) Math.floor(spot.z + half) - origin.getZ();
		int lowY = (int) Math.floor(spot.y) - TowerLayout.BASE_Y;
		int highY = (int) Math.floor(spot.y + mob.getBbHeight()) - TowerLayout.BASE_Y;
		for (int x = lowX; x <= highX; x++) {
			for (int z = lowZ; z <= highZ; z++) {
				for (int y = lowY; y <= highY; y++) {
					if (plan.inSafeZone(x, y, z)) {
						return true;
					}
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

	/**
	 * The box a floor's mobs are searched in: the largest room, whatever the floor's own size. A smaller room has only void
	 * past its walls, and the next floor's cell lies beyond the box.
	 */
	static AABB room(final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		return new AABB(o.getX(), o.getY() - 1, o.getZ(), o.getX() + TowerLayout.MAX_SIZE, o.getY() + TowerLayout.ROOM_HEIGHT, o.getZ() + TowerLayout.MAX_SIZE);
	}

	/** The most tower mobs a floor holds: the base cap times the floor's area against the base floor's, unless a test pinned it. */
	public static int floorCap(final ServerLevel tower, final int floor) {
		Integer pinned = PINNED_CAP.get(floor);
		return pinned != null ? pinned : scaled(FLOOR_CAP, TowerPlan.at(tower, floor));
	}

	/** The mobs a round of spawning adds to a floor at most: the base batch times the area factor. */
	public static int spawnBatch(final ServerLevel tower, final int floor) {
		return scaled(SPAWN_BATCH, TowerPlan.at(tower, floor));
	}

	/** A count made for the base floor, made for the plan's floor: times its area against the base floor's, to the nearest whole. */
	public static int scaled(final int base, final TowerPlan plan) {
		return (int) Math.round(base * plan.areaFactor());
	}

	/** Tower mobs standing on the floor now; its own record, not a kind, says which floor a mob is of. */
	public static int count(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(Mob.class, room(floor), mob -> {
			State state = stateOf(mob);
			// A ghost stands on the players' side, outside the floor's cap.
			return mob.isAlive() && state != null && state.floor() == floor && state.rank() == TowerRank.MOB && !(mob instanceof Ghostly);
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
			TowerPlan plan = TowerPlan.at(tower, floor);
			BlockPos middle = TowerLayout.origin(floor).offset(plan.width() / 2, 0, plan.depth() / 2);
			tower.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(middle), TowerFloors.chunkRadius(plan));
			TowerGhosts.haunt(tower, floor);
			GhostSquads.haunt(tower, floor);
			TowerPhantoms.haunt(tower, floor);
			TowerShamans.haunt(tower, floor);
			int room = floorCap(tower, floor) - count(tower, floor);
			for (int i = 0; i < Math.min(room, spawnBatch(tower, floor)); i++) {
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
