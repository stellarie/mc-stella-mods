package com.stella.tower;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tower's two surprises. When the last hostile mob of a floor dies, a chime plays and a bonus chest
 * appears at the foot of the exit stair, once for each visit to the floor. When a player first reaches a
 * floor that is a multiple of {@link #MILESTONE_EVERY}, fireworks burst over them and a milestone chest
 * appears beside the arrival, holding a rune and a floor key.
 * <p>
 * Both chests roll the floor's own chest loot ({@link TowerChests}) more often than a floor's chests do.
 */
public final class TowerFloorRewards {
	/** The floors that are milestones: every tenth. */
	public static final int MILESTONE_EVERY = 10;
	/** The rolls a reward chest makes over a floor's chest, and one more for each {@link #FLOORS_PER_ROLL} floors, up to the cap. */
	public static final int EXTRA_ROLLS = 8;
	public static final int FLOORS_PER_ROLL = 10;
	public static final int EXTRA_ROLLS_CAP = 6;
	/** How far from its anchor a reward chest may stand, in blocks. */
	private static final int REACH = 6;
	/** How high over the player a firework bursts: out of the reach of its blast. */
	private static final double FIREWORK_HEIGHT = 8.0;
	private static final int[] FIREWORK_COLORS = {0xFFD54A, 0x4FE6E0, 0xE040FB, 0xFF6F61};
	private static final int VISIT_CHECK_TICKS = 20;

	/** The floors whose bonus chest this visit has been given; a floor leaves it when no player stands on it. */
	private static final Set<Integer> PAID = ConcurrentHashMap.newKeySet();

	private TowerFloorRewards() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob) {
				onMobDeath(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel tower = server.getLevel(TowerTravel.TOWER);
			if (tower != null && server.getTickCount() % VISIT_CHECK_TICKS == 0) {
				PAID.removeIf(floor -> TowerMobs.playersOn(tower, floor).isEmpty());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PAID.clear());
	}

	/** The rolls a reward chest makes on a floor. */
	public static int bonusRolls(final int floor) {
		return TowerChests.ROLLS + EXTRA_ROLLS + Math.min(EXTRA_ROLLS_CAP, Math.max(0, floor) / FLOORS_PER_ROLL);
	}

	/** Whether the floor's bonus chest has been given in this visit. */
	public static boolean paid(final int floor) {
		return PAID.contains(floor);
	}

	private static void onMobDeath(final Mob dying) {
		if (!(dying.level() instanceof ServerLevel tower) || !tower.dimension().equals(TowerTravel.TOWER) || !Hostiles.isHostile(dying)) {
			return;
		}
		TowerMobs.State state = TowerMobs.stateOf(dying);
		if (state == null || TowerLayout.floorAt(dying.blockPosition()) != state.floor() || PAID.contains(state.floor())) {
			return;
		}
		int floor = state.floor();
		List<ServerPlayer> players = TowerMobs.playersOn(tower, floor);
		if (players.isEmpty() || hostilesLeft(tower, floor, dying) > 0) {
			return;
		}
		PAID.add(floor);
		BlockPos chest = placeChest(tower, floor, exitLanding(tower, floor), List.of(), false);
		StellaTower.LOGGER.debug("[TOWER] floor={} CLEARED bonusChest={}", floor, chest);
		if (chest == null) {
			return;
		}
		Component message = Component.translatable("message.mod-stella-tower.floor_cleared", floor);
		for (ServerPlayer player : players) {
			player.sendOverlayMessage(message);
			chime(tower, player.getX(), player.getY(), player.getZ());
		}
		tower.sendParticles(ParticleTypes.END_ROD, chest.getX() + 0.5, chest.getY() + 1.0, chest.getZ() + 0.5, 30, 0.4, 0.5, 0.4, 0.05);
		tower.sendParticles(ParticleTypes.HAPPY_VILLAGER, chest.getX() + 0.5, chest.getY() + 0.8, chest.getZ() + 0.5, 14, 0.5, 0.4, 0.5, 0.0);
	}

	/** The hostile tower mobs alive on a floor, not counting the one that just fell. */
	static int hostilesLeft(final ServerLevel tower, final int floor, final Mob fallen) {
		return tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor), mob -> {
			TowerMobs.State state = TowerMobs.stateOf(mob);
			return mob != fallen && mob.isAlive() && state != null && state.floor() == floor && Hostiles.isHostile(mob);
		}).size();
	}

	private static void chime(final ServerLevel tower, final double x, final double y, final double z) {
		tower.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.3F);
		tower.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.9F);
		tower.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.0F);
	}

	/** Called as a player first stands on a floor higher than any they stood on before. */
	static void reached(final ServerPlayer player, final int floor) {
		if (floor < MILESTONE_EVERY || floor % MILESTONE_EVERY != 0 || player.isSpectator()
			|| !(player.level() instanceof ServerLevel tower) || !tower.dimension().equals(TowerTravel.TOWER)) {
			return;
		}
		RandomSource random = tower.getRandom();
		TowerElement element = TowerElement.values()[random.nextInt(TowerElement.values().length)];
		List<ItemStack> given = List.of(TowerRunes.rune(element, floor), FloorKeyItem.forFloor(floor));
		Vec3 arrival = TowerPlan.at(tower, floor).arrivalPos();
		BlockPos chest = placeChest(tower, floor, new Anchor(arrival.x, arrival.z, 1), given, true);
		StellaTower.LOGGER.debug("[TOWER] player={} MILESTONE floor={} chest={}", player.getScoreboardName(), floor, chest);
		fireworks(tower, player);
		player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.milestone", floor));
		tower.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	private static void fireworks(final ServerLevel tower, final ServerPlayer player) {
		FireworkExplosion ball = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
			IntList.of(FIREWORK_COLORS[0], FIREWORK_COLORS[1]), IntList.of(0xFFFFFF), true, true);
		FireworkExplosion star = new FireworkExplosion(FireworkExplosion.Shape.STAR,
			IntList.of(FIREWORK_COLORS[2], FIREWORK_COLORS[3]), IntList.of(0xFFFFFF), false, true);
		double[][] spread = {{0.0, 0.0}, {3.0, 2.0}, {-3.0, 2.0}, {0.0, -3.0}};
		for (int i = 0; i < spread.length; i++) {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(i % 2 == 0 ? ball : star)));
			tower.addFreshEntity(new FireworkRocketEntity(tower, player.getX() + spread[i][0], player.getY() + FIREWORK_HEIGHT, player.getZ() + spread[i][1], rocket));
		}
	}

	/** Where a reward chest is looked for round: a point on the floor, and the nearest ring it may stand on. */
	record Anchor(double x, double z, int nearest) {
	}

	/** The foot of the floor's exit stair, where the way down lands. */
	static Anchor exitLanding(final ServerLevel tower, final int floor) {
		Vec3 at = TowerBackPortals.landing(tower, floor).position();
		return new Anchor(at.x, at.z, 2);
	}

	/**
	 * Sets a tower chest on the nearest free ground to the anchor and stocks it with the floor's loot at
	 * {@link #bonusRolls}. Null when no ground within reach is free.
	 */
	static @Nullable BlockPos placeChest(final ServerLevel tower, final int floor, final Anchor anchor, final List<ItemStack> given, final boolean guaranteed) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos centre = BlockPos.containing(anchor.x(), TowerLayout.BASE_Y, anchor.z());
		for (int ring = anchor.nearest(); ring <= REACH; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					BlockPos feet = free(tower, plan, centre.getX() + dx, centre.getZ() + dz);
					if (feet != null && keepsClearOfStair(plan, feet)) {
						Direction facing = Direction.getApproximateNearest(-dx, 0.0, -dz);
						BlockState chest = TowerBlocks.TOWER_CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
						tower.setBlock(feet, chest, Block.UPDATE_ALL);
						TowerChests.fill(tower, feet, floor, guaranteed, bonusRolls(floor), given, tower.getRandom());
						return feet;
					}
				}
			}
		}
		StellaTower.LOGGER.warn("Tower floor {}: no free ground for a reward chest near {}, {}", floor, anchor.x(), anchor.z());
		return null;
	}

	/** The feet spot of free standing ground in a column of the floor, or null. */
	private static @Nullable BlockPos free(final ServerLevel tower, final TowerPlan plan, final int worldX, final int worldZ) {
		BlockPos origin = plan.origin();
		int stand = plan.standY(worldX - origin.getX(), worldZ - origin.getZ());
		// In a hub only the floor is ground: a column that stands higher is its wall's top or a post of the way back.
		if (stand == BiomeDraft.NONE || stand != 0 && plan.inHub(worldX - origin.getX(), worldZ - origin.getZ())) {
			return null;
		}
		BlockPos feet = new BlockPos(worldX, TowerLayout.BASE_Y + stand, worldZ);
		boolean open = tower.getBlockState(feet).isAir() && tower.getBlockState(feet.above()).isAir();
		boolean ground = tower.getBlockState(feet.below()).isFaceSturdy(tower, feet.below(), Direction.UP);
		return open && ground ? feet : null;
	}

	/** A chest at the exit keeps off the lanes the stair is walked along. */
	private static boolean keepsClearOfStair(final TowerPlan plan, final BlockPos feet) {
		if (!plan.isBiome()) {
			return true;
		}
		int z = feet.getZ() - plan.origin().getZ();
		return z < plan.portalZ() - 1 || z > plan.portalZ() + TowerLayout.PORTAL_INNER_WIDTH;
	}
}
