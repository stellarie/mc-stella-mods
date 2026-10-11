package com.stella.tower.colosseum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.mobs.StellaMobs;
import com.stella.tower.StellaTower;
import com.stella.tower.mixin.MobSelectorAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Tower Guardians: a skeleton on a skeleton horse, twice the size, level 50, wreathed in flame and soul fire, and never burnt.
 * One fights with a netherite sword, one with the Rudra Bow. They are driven by {@link GuardianBrain}, not by goals, so
 * no vanilla goal pulls them out of the arena or sets them on each other.
 * <p>
 * Other mods are reached by share or by item id, and skipped when absent: the mobs mod fixes the level, the rpg mod sets the
 * stats, the spells mod gives the archer its Blitz Beat, the equips mod holds the bow and both drops.
 */
public final class Guardians {
	/** On the rider and the horse of both guardians. */
	public static final String TAG = "mod-stella-tower.guardian";
	public static final String TAG_HORSE = "mod-stella-tower.guardian_horse";
	/** A horse whose rider fell: it can be tamed, and stops being a guardian's when it is. */
	public static final String TAG_FREED = "mod-stella-tower.guardian_horse_freed";
	public static final int LEVEL = 50;
	public static final double SCALE = 2.0;
	public static final int RIDER_VIT = 100;
	public static final int HORSE_VIT = 150;
	/** The archer's stats are a tenth stronger, and its dexterity never falls under this. */
	public static final double ARCHER_EXTRA = 1.10;
	public static final int ARCHER_MIN_DEX = 50;
	/** Above a vanilla horse's best of 0.3375 and 1.0. */
	public static final double HORSE_SPEED = 0.45;
	public static final double HORSE_JUMP = 1.2;

	public static final String LEVEL_SHARE = "mod-stella-mobs:apply-level";
	public static final String STAT_SHARE = "mod-stella-rpg:mob-stat-set";
	public static final String BLITZ_SHARE = "mod-stella-spells:mob-blitz-beat";
	public static final Identifier RUDRA_BOW = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "rudra_bow");

	private static final String[] OTHER_STATS = {"str", "agi", "dex", "int", "luk"};
	private static final int DEX_AT = 2;
	private static final Set<UUID> FREED = ConcurrentHashMap.newKeySet();

	/** Where a guardian belongs: the arena's middle and radius, and the spot it stands on. */
	public record Home(BlockPos center, int radius, BlockPos post) {
		public static final Codec<Home> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("center").forGetter(Home::center),
			Codec.INT.fieldOf("radius").forGetter(Home::radius),
			BlockPos.CODEC.fieldOf("post").forGetter(Home::post)
		).apply(instance, Home::new));
	}

	public static final AttachmentType<Home> HOME = AttachmentRegistry.create(
		StellaTower.id("guardian_home"), builder -> builder.persistent(Home.CODEC)
	);

	/** A guardian and its horse. */
	public record Spawned(GuardianKind kind, Mob rider, Mob horse) {
	}

	private Guardians() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Guardians::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Guardians::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Guardians::afterDeath);
		ServerEntityEvents.ENTITY_LOAD.register(Guardians::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(Guardians::onUnload);
		ServerTickEvents.END_SERVER_TICK.register(Guardians::tick);
	}

	/** Whether the entity is a guardian or a guardian's horse. */
	public static boolean isPart(final @Nullable Entity entity) {
		return entity != null && entity.entityTags().contains(TAG);
	}

	// ---------------------------------------------------------------- spawning

	/** Raises both guardians at their posts and writes them into the world's record. */
	public static List<Spawned> raise(final ServerLevel overworld, final Colosseum.Layout layout) {
		List<Spawned> raised = new ArrayList<>();
		ColosseumData data = ColosseumData.get(overworld);
		for (GuardianKind kind : GuardianKind.values()) {
			Spawned spawned = spawn(overworld, kind, layout.home(kind));
			if (spawned != null) {
				data.raised(spawned.rider().getUUID());
				raised.add(spawned);
			}
		}
		return raised;
	}

	/** One guardian on its horse at its post; null if the entities could not be made. */
	public static @Nullable Spawned spawn(final ServerLevel level, final GuardianKind kind, final Home home) {
		SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.EVENT);
		Skeleton rider = EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
		if (horse == null || rider == null) {
			return null;
		}
		Vec3 post = Vec3.atBottomCenterOf(home.post());
		float yaw = kind == GuardianKind.MELEE ? 270.0F : 90.0F;
		for (Mob part : new Mob[] {horse, rider}) {
			part.snapTo(post.x, post.y, post.z, yaw, 0.0F);
			part.addTag(TAG);
			part.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
			part.addTag(StellaMobs.NO_BEHAVIOR_TAG);
			part.setAttached(HOME, home);
			part.setPersistenceRequired();
			part.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		}
		horse.addTag(TAG_HORSE);
		horse.setTamed(true);
		horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HORSE_SPEED);
		horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(HORSE_JUMP);
		rider.addTag(kind.tag());
		rider.setCustomName(Component.translatable(kind.nameKey()));
		equip(rider, kind);
		applyLevel(horse);
		applyLevel(rider);
		setStats(horse, Map.of("vit", HORSE_VIT));
		setStats(rider, rollStats(rider.getRandom(), kind));
		strip(horse);
		strip(rider);
		level.addFreshEntity(horse);
		level.addFreshEntity(rider);
		rider.startRiding(horse, true, false);
		// The load event can wait for the chunk to be tracked; the brain must not.
		GuardianBrain.attach(rider);
		if (kind == GuardianKind.RANGED) {
			blitzBeat(rider);
		}
		StellaTower.LOGGER.info("[COLOSSEUM] raised {} at {}", kind, home.post());
		return new Spawned(kind, rider, horse);
	}

	private static void equip(final Mob rider, final GuardianKind kind) {
		rider.setItemSlot(EquipmentSlot.HEAD, unbreakable(new ItemStack(Items.NETHERITE_HELMET)));
		rider.setItemSlot(EquipmentSlot.CHEST, unbreakable(new ItemStack(Items.NETHERITE_CHESTPLATE)));
		rider.setItemSlot(EquipmentSlot.LEGS, unbreakable(new ItemStack(Items.NETHERITE_LEGGINGS)));
		rider.setItemSlot(EquipmentSlot.FEET, unbreakable(new ItemStack(Items.NETHERITE_BOOTS)));
		Item weapon = kind == GuardianKind.MELEE ? Items.NETHERITE_SWORD : BuiltInRegistries.ITEM.getOptional(RUDRA_BOW).orElse(Items.BOW);
		rider.setItemSlot(EquipmentSlot.MAINHAND, unbreakable(new ItemStack(weapon)));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			rider.setDropChance(slot, 0.0F);
		}
	}

	/** Armour that does not wear: the sun cannot take the helmet, and a blow cannot break the kit. */
	private static ItemStack unbreakable(final ItemStack stack) {
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		return stack;
	}

	/** Vitality fixed; the other stats are the level's points, dealt out at random. The archer has a tenth more, and dexterity at least 50. */
	public static Map<String, Integer> rollStats(final RandomSource random, final GuardianKind kind) {
		int[] points = new int[OTHER_STATS.length];
		int budget = kind == GuardianKind.RANGED ? (int) Math.round(LEVEL * ARCHER_EXTRA) : LEVEL;
		if (kind == GuardianKind.RANGED) {
			points[DEX_AT] = ARCHER_MIN_DEX;
			budget -= ARCHER_MIN_DEX;
		}
		for (int i = 0; i < budget; i++) {
			points[random.nextInt(points.length)]++;
		}
		Map<String, Integer> stats = new LinkedHashMap<>();
		stats.put("vit", RIDER_VIT);
		for (int i = 0; i < OTHER_STATS.length; i++) {
			stats.put(OTHER_STATS[i], points[i]);
		}
		return stats;
	}

	@SuppressWarnings("unchecked")
	private static void applyLevel(final Mob mob) {
		Object share = FabricLoader.getInstance().getObjectShare().get(LEVEL_SHARE);
		if (share instanceof BiConsumer<?, ?> apply) {
			try {
				((BiConsumer<Mob, Integer>) apply).accept(mob, LEVEL);
			} catch (RuntimeException | LinkageError failure) {
				StellaTower.LOGGER.warn("[COLOSSEUM] the {} share failed: {}", LEVEL_SHARE, failure.toString());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void setStats(final Mob mob, final Map<String, Integer> stats) {
		Object share = FabricLoader.getInstance().getObjectShare().get(STAT_SHARE);
		if (share instanceof BiConsumer<?, ?> set) {
			try {
				((BiConsumer<Mob, Map<String, Integer>>) set).accept(mob, stats);
			} catch (RuntimeException | LinkageError failure) {
				StellaTower.LOGGER.warn("[COLOSSEUM] the {} share failed: {}", STAT_SHARE, failure.toString());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void blitzBeat(final Mob rider) {
		Object share = FabricLoader.getInstance().getObjectShare().get(BLITZ_SHARE);
		if (share instanceof Consumer<?> give) {
			try {
				((Consumer<Mob>) give).accept(rider);
			} catch (RuntimeException | LinkageError failure) {
				StellaTower.LOGGER.warn("[COLOSSEUM] the {} share failed: {}", BLITZ_SHARE, failure.toString());
			}
		}
	}

	/** Removes every vanilla goal, those that move and those that pick a target: the brain moves and fights, so nothing else may. */
	static void strip(final Mob mob) {
		mob.getGoalSelector().removeAllGoals(goal -> true);
		((MobSelectorAccessor) mob).stellaTower$targetSelector().removeAllGoals(goal -> true);
	}

	// ---------------------------------------------------------------- rules

	/** No guardian, nor its horse, ever hurts another; none is hurt by fire or by the sun. */
	private static boolean allowDamage(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!isPart(victim)) {
			return true;
		}
		if (isPart(source.getEntity()) || isPart(source.getDirectEntity())) {
			return false;
		}
		return !source.is(DamageTypeTags.IS_FIRE);
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (!isPart(victim) || damage <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || isPart(attacker)) {
			return;
		}
		GuardianBrain brain = GuardianBrain.of(victim);
		if (brain != null) {
			brain.hurtBy(attacker);
		}
	}

	private static void onLoad(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Mob mob) || !isPart(mob)) {
			return;
		}
		strip(mob);
		if (GuardianKind.of(mob) != null) {
			GuardianBrain.attach(mob);
		} else if (mob.entityTags().contains(TAG_FREED)) {
			FREED.add(mob.getUUID());
			freedGoals(mob);
		}
	}

	private static void onUnload(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob mob && isPart(mob)) {
			GuardianBrain.detach(mob);
			FREED.remove(mob.getUUID());
		}
	}

	// ---------------------------------------------------------------- death

	private static void afterDeath(final LivingEntity entity, final DamageSource source) {
		GuardianKind kind = GuardianKind.of(entity);
		if (kind == null || !(entity instanceof Mob rider) || !(rider.level() instanceof ServerLevel level)) {
			return;
		}
		GuardianBrain.detach(rider);
		MinecraftServer server = level.getServer();
		ColosseumData.get(server.overworld()).fallen(rider.getUUID());
		BuiltInRegistries.ITEM.getOptional(kind.drop()).ifPresent(item -> giveLoot(level, rider, source, new ItemStack(item)));
		if (rider.getVehicle() instanceof Mob horse && horse.entityTags().contains(TAG_HORSE) && horse.isAlive()) {
			free(horse);
			// The fallen rider drops off, so the horse has room for whoever tames it.
			rider.stopRiding();
		}
		StellaTower.LOGGER.info("[COLOSSEUM] {} fell", kind);
	}

	/**
	 * The loot goes straight into the killer's inventory. With no player killer, or no room, it drops; a dropped piece
	 * never burns in fire or lava.
	 */
	static void giveLoot(final ServerLevel level, final Mob rider, final DamageSource source, final ItemStack loot) {
		loot.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.IS_FIRE)));
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
			: rider.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
		if (killer != null && killer.getInventory().add(loot) && loot.isEmpty()) {
			return;
		}
		if (!loot.isEmpty()) {
			rider.spawnAtLocation(level, loot);
		}
	}

	/** The horse of a fallen rider can be tamed: it bucks a rider like any wild horse, and keeps its speed and jump once tamed. */
	static void free(final Mob horse) {
		horse.addTag(TAG_FREED);
		if (horse instanceof AbstractHorse tame) {
			tame.setTamed(false);
		}
		horse.getNavigation().stop();
		FREED.add(horse.getUUID());
		freedGoals(horse);
	}

	private static void freedGoals(final Mob horse) {
		if (horse instanceof AbstractHorse tame) {
			horse.getGoalSelector().addGoal(1, new PanicGoal(tame, 1.2));
			horse.getGoalSelector().addGoal(1, new RunAroundLikeCrazyGoal(tame, 1.2));
		}
	}

	/** A freed horse that has been tamed is a plain horse of the player's: its size and its guardian marks go, its speed stays. */
	private static void tick(final MinecraftServer server) {
		GuardianBrain.tickAll(server);
		if (FREED.isEmpty()) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID id : Set.copyOf(FREED)) {
				if (level.getEntity(id) instanceof AbstractHorse horse && horse.isTamed() && horse.entityTags().contains(TAG_FREED)) {
					release(horse);
					FREED.remove(id);
				}
			}
		}
	}

	static void release(final AbstractHorse horse) {
		horse.getAttribute(Attributes.SCALE).setBaseValue(1.0);
		horse.removeTag(TAG);
		horse.removeTag(TAG_HORSE);
		horse.removeTag(TAG_FREED);
		horse.removeTag(StellaMobs.NO_BEHAVIOR_TAG);
		StellaTower.LOGGER.info("[COLOSSEUM] a guardian's horse was tamed");
	}
}
