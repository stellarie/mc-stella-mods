package com.stella.end;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The two Wither Knights that rise with the Sovereign: huge wither skeletons in enchanted netherite with a withering
 * sword each. They blink toward a player and swing a charged slash. The floor 6 exit stays sealed until they and the
 * Sovereign are dead. The saved state keeps one bit for each kind that stands, so the count survives an unloaded chunk.
 */
public final class WitherKnights {
	public static final String TAG = Factions.PREFIX + "knight";
	public static final float MAX_HEALTH = 125000.0F;
	public static final double SCALE = 1.6;
	/** Random enchantments on each armour piece and on the sword. */
	public static final int ENCHANTS = 6;
	public static final double CHASE_DISTANCE = 8.0;
	public static final int CHASE_COOLDOWN = 120;
	/** A knight stands this far from the Sovereign, tried in this order, and the first safe spot serves. */
	private static final double[] STAND_RADII = {5.0, 4.0, 6.0, 7.0, 3.5};
	private static final double[] STAND_TURNS = {0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0};
	private static final int KEPT = 64;

	/** The two knights differ by the equips enchantment on the sword. The bit is the kind's place in the saved state. */
	public enum Kind {
		CHILL("weapon/chill", 1, 0.0),
		WAVEFORM("weapon/waveform", 2, 180.0);

		private final Identifier special;
		private final int bit;
		private final double side;

		Kind(final String path, final int bit, final double side) {
			this.special = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", path);
			this.bit = bit;
			this.side = side;
		}

		public Identifier special() {
			return this.special;
		}

		public int bit() {
			return this.bit;
		}

		public String tag() {
			return TAG + "_" + name().toLowerCase(Locale.ROOT);
		}
	}

	/** One rising: the knights it added. */
	public record Raise(long at, List<UUID> knights) {
	}

	/** Identities of the knights that stand loaded; load and unload keep it. */
	private static final Set<UUID> LIVE = new LinkedHashSet<>();
	private static final List<Raise> RAISED = new ArrayList<>();

	private WitherKnights() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof WitherSkeleton knight && is(knight) && level.dimension().equals(Level.END)) {
				LIVE.add(knight.getUUID());
				arm(knight);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (is(entity)) {
				LIVE.remove(entity.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof WitherSkeleton knight && is(knight) && knight.level() instanceof ServerLevel level && level.dimension().equals(Level.END)) {
				fall(level, knight);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	public static void reset() {
		LIVE.clear();
		RAISED.clear();
	}

	/** Gives a knight its goals. They are not saved with it, so every load puts them back, once. */
	private static void arm(final WitherSkeleton knight) {
		var goals = knight.getGoalSelector();
		boolean chasing = goals.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof TeleportChaseGoal);
		boolean slashing = goals.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof KnightSlashGoal);
		if (!chasing) {
			goals.addGoal(1, new TeleportChaseGoal(knight, CHASE_DISTANCE, CHASE_COOLDOWN));
		}
		if (!slashing) {
			goals.addGoal(1, new KnightSlashGoal(knight));
		}
	}

	/** Raises the knights that do not stand yet, beside the Sovereign's anchor. Returns the ones it added. */
	public static List<WitherSkeleton> raise(final ServerLevel end) {
		EndState state = EndCastles.state(end);
		int bits = state.knights();
		List<WitherSkeleton> added = new ArrayList<>();
		for (Kind kind : Kind.values()) {
			if ((bits & kind.bit()) != 0) {
				continue;
			}
			WitherSkeleton knight = make(end, kind);
			if (knight != null && end.addFreshEntity(knight)) {
				added.add(knight);
				bits |= kind.bit();
			}
		}
		if (!added.isEmpty()) {
			EndCastles.setState(end, EndCastles.state(end).withKnights(bits));
			RAISED.add(new Raise(EndClock.now(end), added.stream().map(Entity::getUUID).toList()));
			if (RAISED.size() > KEPT) {
				RAISED.remove(0);
			}
			BehaviorLog.info("[END] knights risen added={} bits={}", added.size(), bits);
		}
		return added;
	}

	private static @Nullable WitherSkeleton make(final ServerLevel end, final Kind kind) {
		WitherSkeleton knight = EntityTypes.WITHER_SKELETON.create(end, EntitySpawnReason.EVENT);
		if (knight == null) {
			return null;
		}
		LevelBridge.claim(knight);
		knight.addTag(TAG);
		knight.addTag(kind.tag());
		knight.setPersistenceRequired();
		knight.setCanPickUpLoot(false);
		knight.setCustomName(Component.translatable("entity.mod-stella-end.wither_knight"));
		knight.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
		knight.refreshDimensions();
		LevelBridge.apply(knight, EndLevels.KNIGHT);
		HealthBridge.fixMaxHealth(knight, MAX_HEALTH);
		Registry<Enchantment> enchantments = end.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RandomSource random = knight.getRandom();
		knight.setItemSlot(EquipmentSlot.MAINHAND, sword(random, enchantments, kind.special()));
		MobGear.netheriteArmor(knight, ENCHANTS, random, enchantments);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			knight.setDropChance(slot, 0.0F);
		}
		Vec3 at = stand(end, knight, kind);
		knight.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
		return knight;
	}

	/** The first safe spot on the knight's side of the Sovereign, scanning outward and around; the anchor itself when none serves. */
	private static Vec3 stand(final ServerLevel end, final WitherSkeleton knight, final Kind kind) {
		Vec3 anchor = Vec3.atBottomCenterOf(EndCastle.arenaAnchor());
		for (double radius : STAND_RADII) {
			for (double turn : STAND_TURNS) {
				double angle = Math.toRadians(kind.side + turn);
				Vec3 at = anchor.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
				if (CastleTeleport.safe(end, knight, at)) {
					return at;
				}
			}
		}
		return anchor;
	}

	/** A knight fell: its bit clears, it leaves a wither core, and the exit opens if it was the last of the three. */
	private static void fall(final ServerLevel end, final WitherSkeleton knight) {
		Kind kind = kindOf(knight);
		if (kind != null) {
			EndCastles.setState(end, EndCastles.state(end).withKnights(EndCastles.state(end).knights() & ~kind.bit()));
		}
		knight.spawnAtLocation(end, new ItemStack(EndItems.witherCore()));
		BehaviorLog.info("[END] knight fell kind={} at={}", kind, knight.blockPosition());
		ExitSeal.update(end);
	}

	public static List<Raise> raised() {
		return List.copyOf(RAISED);
	}

	/** The knights that stand loaded and alive, in the order of their kinds. */
	public static List<WitherSkeleton> alive(final ServerLevel end) {
		List<WitherSkeleton> found = new ArrayList<>();
		for (UUID id : LIVE) {
			if (end.getEntity(id) instanceof WitherSkeleton knight && knight.isAlive()) {
				found.add(knight);
			}
		}
		found.sort(Comparator.comparingInt(knight -> {
			Kind kind = kindOf(knight);
			return kind == null ? Integer.MAX_VALUE : kind.ordinal();
		}));
		return found;
	}

	public static boolean is(final @Nullable Entity entity) {
		return entity instanceof WitherSkeleton && entity.entityTags().contains(TAG);
	}

	public static @Nullable Kind kindOf(final @Nullable Entity entity) {
		if (entity == null) {
			return null;
		}
		for (Kind kind : Kind.values()) {
			if (entity.entityTags().contains(kind.tag())) {
				return kind;
			}
		}
		return null;
	}

	/** A withering sword with {@link #ENCHANTS} random enchantments and, when the registry holds it, the special one at level four. */
	public static ItemStack sword(final RandomSource random, final Registry<Enchantment> registry, final Identifier special) {
		ItemStack sword = new ItemStack(WitheringArms.sword());
		Holder<Enchantment> holder = MobGear.find(registry, special);
		if (holder != null) {
			MobGear.setLevel(sword, holder, MobGear.SPECIAL_LEVEL);
		}
		MobGear.enchantRandom(sword, ENCHANTS, random, registry, MobGear.SPECIAL_SWORD);
		return sword;
	}
}
