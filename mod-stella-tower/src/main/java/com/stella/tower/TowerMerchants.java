package com.stella.tower;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wandering ghost merchant. When a player comes onto a floor, the floor may get one at its entrance. Near it the tower is
 * at peace: no mob is raised within {@value #AURA} blocks of it, and none targets a player within that reach. It sells its goods
 * ({@link MerchantGoods}) for emeralds, in the tower only.
 */
public final class TowerMerchants {
	/** The chance a player coming onto a floor finds a merchant at its entrance. */
	public static final double CHANCE = 0.10;
	/** The reach of its peace, in blocks. */
	public static final double AURA = 8.0;
	/** The ticks between two looks at where each player in the tower stands. */
	public static final int WATCH_TICKS = 10;
	/** How far from the entrance's arrival spot the merchant stands, and how far it strays. */
	public static final int STAND_OFF = 2;
	public static final int STRAY = 6;
	public static final String COUNT_SHARE = "mod-stella-equips-enchants:emerald-count";
	public static final String SPEND_SHARE = "mod-stella-equips-enchants:emerald-spend";

	public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, StellaTower.id("ghost_merchant"));
	public static final EntityType<GhostMerchant> MERCHANT = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.<GhostMerchant>of(GhostMerchant::new, MobCategory.CREATURE).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).build(KEY));

	/** The highest floor each player has stood on, which no teleport or death takes away. */
	public static final AttachmentType<Integer> BEST_FLOOR = AttachmentRegistry.create(
		StellaTower.id("best_floor"), builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** The floor a player was last greeted on, so a floor greets a player once. */
	private static final AttachmentType<Integer> GREETED = AttachmentRegistry.create(
		StellaTower.id("greeted_floor"), builder -> builder.persistent(Codec.INT));

	/** The merchants that are in a level now; the targeting and spawning checks ask this and nothing slower. */
	private static final Set<GhostMerchant> LIVE = ConcurrentHashMap.newKeySet();
	private static final java.util.Map<Integer, Double> PINNED_CHANCE = new ConcurrentHashMap<>();
	private static volatile @Nullable Double defaultChance;

	private TowerMerchants() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(MERCHANT, GhostMerchant.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof GhostMerchant merchant) {
				LIVE.add(merchant);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof GhostMerchant merchant) {
				LIVE.remove(merchant);
			}
		});
		// A player who leaves the tower is greeted afresh on coming back, to the floor they left or another.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (origin.dimension().equals(TowerTravel.TOWER)) {
				player.removeAttached(GREETED);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TowerMerchants::tick);
		StellaTower.LOGGER.debug("Registered {}", KEY);
	}

	/** The test seam: pins, by floor, the chance a player coming onto it finds a merchant; null restores the default. */
	public static void pinChance(final int floor, final @Nullable Double chance) {
		if (chance == null) {
			PINNED_CHANCE.remove(floor);
		} else {
			PINNED_CHANCE.put(floor, chance);
		}
	}

	/** The test seam: the chance on every floor not pinned; null restores {@link #CHANCE}. */
	public static void pinDefaultChance(final @Nullable Double chance) {
		defaultChance = chance;
	}

	public static double chance(final int floor) {
		Double pin = PINNED_CHANCE.get(floor);
		Double standing = defaultChance;
		return pin != null ? pin : standing != null ? standing : CHANCE;
	}

	// ---------------------------------------------------------------- appearing

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % WATCH_TICKS != 0) {
			return;
		}
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null || tower.players().isEmpty()) {
			return;
		}
		for (ServerPlayer player : tower.players()) {
			int floor = TowerLayout.floorAt(player.blockPosition());
			if (floor <= 0 || player.isSpectator() || !TowerFloors.isBuilt(tower, floor)) {
				continue;
			}
			if (floor > player.getAttachedOrElse(BEST_FLOOR, 0)) {
				player.setAttached(BEST_FLOOR, floor);
			}
			if (player.getAttachedOrElse(GREETED, 0) != floor) {
				player.setAttached(GREETED, floor);
				greet(tower, floor, tower.getRandom(), chance(floor));
			}
		}
	}

	/**
	 * A player has come onto a floor: with the given chance, and if the floor has none yet, a merchant appears at its entrance.
	 * The merchant it raised, or null.
	 */
	public static @Nullable GhostMerchant greet(final ServerLevel tower, final int floor, final RandomSource random, final double chance) {
		if (random.nextDouble() >= chance || !on(tower, floor).isEmpty()) {
			return null;
		}
		return appear(tower, floor, random);
	}

	/** The merchants on a floor. */
	public static List<GhostMerchant> on(final ServerLevel tower, final int floor) {
		return tower.getEntitiesOfClass(GhostMerchant.class, TowerMobs.room(floor), Mob::isAlive);
	}

	/** Raises a merchant, stocked, beside a floor's entrance. */
	public static @Nullable GhostMerchant appear(final ServerLevel tower, final int floor, final RandomSource random) {
		GhostMerchant merchant = MERCHANT.create(tower, EntitySpawnReason.EVENT);
		if (merchant == null) {
			return null;
		}
		Vec3 arrival = TowerPlan.at(tower, floor).arrivalPos();
		merchant.snapTo(arrival);
		int[][] offsets = {{STAND_OFF, 0}, {-STAND_OFF, 0}, {0, STAND_OFF}, {0, -STAND_OFF}, {STAND_OFF, STAND_OFF}, {-STAND_OFF, -STAND_OFF},
			{STAND_OFF, -STAND_OFF}, {-STAND_OFF, STAND_OFF}, {STAND_OFF + 1, 0}, {-STAND_OFF - 1, 0}, {0, STAND_OFF + 1}, {0, -STAND_OFF - 1}};
		for (int[] offset : offsets) {
			merchant.snapTo(arrival.x + offset[0], arrival.y, arrival.z + offset[1], random.nextFloat() * 360.0F, 0.0F);
			if (TowerMobs.standsFree(tower, merchant, merchant.blockPosition())) {
				break;
			}
			merchant.snapTo(arrival);
		}
		merchant.stock(MerchantGoods.roll(random));
		merchant.setHomeTo(BlockPos.containing(arrival), STRAY);
		if (!tower.addFreshEntity(merchant)) {
			merchant.discard();
			return null;
		}
		announce(tower, merchant);
		StellaTower.LOGGER.debug("[TOWER] floor={} a ghost merchant appears at {}", floor, merchant.blockPosition());
		return merchant;
	}

	/** The merchant's arrival: a burst of soul fire, a chime, and a word to every player near. */
	private static void announce(final ServerLevel tower, final GhostMerchant merchant) {
		tower.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, merchant.getX(), merchant.getY() + 1.0, merchant.getZ(), 40, 0.5, 0.9, 0.5, 0.05);
		tower.sendParticles(ParticleTypes.ENCHANT, merchant.getX(), merchant.getY() + 1.5, merchant.getZ(), 30, 0.6, 0.6, 0.6, 0.6);
		tower.playSound(null, merchant.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5F, 0.7F);
		for (ServerPlayer player : tower.players()) {
			if (player.distanceToSqr(merchant) <= 32.0 * 32.0) {
				player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.merchant.appears"));
			}
		}
	}

	// ---------------------------------------------------------------- the peace

	/**
	 * Whether no mob may take this target: the merchant itself, or a player within {@value #AURA} blocks of a merchant or
	 * standing by a mob that is.
	 */
	public static boolean shelters(final LivingEntity attacker, final LivingEntity target) {
		if (LIVE.isEmpty()) {
			return false;
		}
		if (target instanceof GhostMerchant) {
			return true;
		}
		if (!(target instanceof Player)) {
			return false;
		}
		for (GhostMerchant merchant : LIVE) {
			if (merchant.isAlive() && merchant.level() == target.level()
				&& (merchant.distanceToSqr(target) <= AURA * AURA || merchant.distanceToSqr(attacker) <= AURA * AURA)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a spot of a level lies within a merchant's peace, where no mob is raised. */
	public static boolean forbids(final LevelAccessor level, final Vec3 spot) {
		if (LIVE.isEmpty() || !(level instanceof ServerLevelAccessor accessor)) {
			return false;
		}
		for (GhostMerchant merchant : LIVE) {
			if (merchant.isAlive() && merchant.level() == accessor.getLevel() && merchant.position().distanceToSqr(spot) <= AURA * AURA) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the shop

	/** The highest floor a player has climbed to, from what the tower has seen of them; one at the least. */
	public static int bestFloor(final ServerPlayer player) {
		int best = Math.max(player.getAttachedOrElse(BEST_FLOOR, 0), TowerTravel.lastFloor(player));
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			best = Math.max(best, FloorKeyItem.floorOf(player.getInventory().getItem(slot)));
		}
		if (player.level().dimension().equals(TowerTravel.TOWER)) {
			best = Math.max(best, TowerLayout.floorAt(player.blockPosition()));
		}
		return Math.max(1, best);
	}

	/** A player speaks to the merchant: in the tower its shop opens; anywhere else it will not sell. */
	static void talk(final GhostMerchant merchant, final ServerPlayer player) {
		if (!merchant.level().dimension().equals(TowerTravel.TOWER)) {
			player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.merchant.tower_only"));
			return;
		}
		if (!merchant.isStocked()) {
			merchant.stock(MerchantGoods.roll(merchant.getRandom()));
		}
		MerchantShopMenu.open(player, merchant);
	}

	/**
	 * Sells the good at an index to the player for its price in emeralds. Refused unless the merchant stands in the tower, the
	 * shelf holds a good there and the player carries the emeralds. The player's purse is the equips mod's when it is present, so
	 * emerald bags count.
	 */
	public static boolean buy(final ServerPlayer player, final GhostMerchant merchant, final int index) {
		if (!(merchant.level() instanceof ServerLevel level) || !level.dimension().equals(TowerTravel.TOWER)) {
			player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.merchant.tower_only"));
			return false;
		}
		List<MerchantGoods.Good> goods = merchant.goods();
		if (index < 0 || index >= goods.size()) {
			return false;
		}
		MerchantGoods.Good good = goods.get(index);
		ItemStack item = good.make().apply(new MerchantGoods.Sale(level, player, level.getRandom()));
		if (item.isEmpty()) {
			return false;
		}
		int held = emeralds(player);
		if (held < good.price() || !spend(player, good.price())) {
			player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.merchant.short", good.price(), held));
			return false;
		}
		ItemStack given = item.copy();
		if (!player.getInventory().add(given)) {
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), given));
		}
		level.playSound(null, merchant.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.3F);
		player.sendSystemMessage(Component.translatable("message.mod-stella-tower.merchant.bought", item.getHoverName(), good.price()));
		StellaTower.LOGGER.debug("[TOWER] player={} bought {} for {} emeralds", player.getScoreboardName(), good.id(), good.price());
		return true;
	}

	/** Every emerald the player carries: through the equips mod's purse when it is present, else the loose ones. */
	@SuppressWarnings("unchecked")
	public static int emeralds(final Player player) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(COUNT_SHARE);
		if (shared instanceof Function<?, ?> function) {
			Integer value = ((Function<Object, Integer>) function).apply(player);
			if (value != null) {
				return value;
			}
		}
		return loose(player);
	}

	/** Takes the whole price, or nothing when the player carries too few. */
	@SuppressWarnings("unchecked")
	private static boolean spend(final Player player, final int price) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(SPEND_SHARE);
		if (shared instanceof BiFunction<?, ?, ?> function) {
			return Boolean.TRUE.equals(((BiFunction<Object, Integer, Boolean>) function).apply(player, price));
		}
		if (loose(player) < price) {
			return false;
		}
		int left = price;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.EMERALD)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
		player.getInventory().setChanged();
		return true;
	}

	private static int loose(final Player player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.EMERALD)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
