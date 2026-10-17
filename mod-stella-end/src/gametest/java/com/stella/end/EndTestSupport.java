package com.stella.end;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Shared fixtures: the End level, a clean start, the castle held in memory, a player inside it, the summary line. */
final class EndTestSupport {
	private EndTestSupport() {
	}

	static ServerLevel end(final GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		helper.assertTrue(end != null, "the End is not loaded");
		return end;
	}

	/**
	 * Starts a test from nothing: any player an earlier test left in the End goes, every castle mob goes, the spawner
	 * forgets its records, the castle stands and its chunks stay loaded. The End and its castle are shared by every test.
	 */
	static ServerLevel fresh(final GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel end = end(helper);
		for (ServerPlayer leftover : List.copyOf(end.players())) {
			server.getPlayerList().remove(leftover);
		}
		EndGameTestInit.dragonFightRuns = false;
		// A fight that ran leaves its arena ticket,
		// nine chunks wide.
		// An enderman then teleports out of test reach.
		end.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.DRAGON, new net.minecraft.world.level.ChunkPos(0, 0), 9);
		clearMobs(end);
		FloorSpawner.reset();
		EndDragons.reset();
		UndeadSovereign.reset();
		UndeadBlazes.reset();
		SoulCharges.reset();
		WitherKnights.reset();
		BlazeSoul.reset();
		WitherDrain.reset();
		WitheringArms.reset();
		EndCastles.ensureBuilt(end);
		releaseRing(end);
		hold(end, EndCastle.min());
		restoreArena(end);
		return end;
	}

	/** The state every slice B test starts from: nobody has died, the door is shut and the arena holds no flame. */
	private static void restoreArena(final ServerLevel end) {
		EndState state = EndCastles.state(end);
		EndCastles.setState(end, new EndState(state.built(), state.clock(), EndState.NEVER, EndState.NEVER, false, 0));
		CastleBuilder.setExitDoor(end, EndCastles.layout(end), EndCastle.min(), false);
		int y = EndCastle.standY(EndCastle.FLOORS);
		for (int x = EndCastle.MIN_X + 1; x < EndCastle.MIN_X + EndCastle.SIZE - 1; x++) {
			for (int z = EndCastle.MIN_Z + 1; z < EndCastle.MIN_Z + EndCastle.SIZE - 1; z++) {
				BlockPos at = new BlockPos(x, y, z);
				if (end.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
					end.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/**
	 * Removes every castle mob, and the vanilla dragon the fight makes once a player stands in the End: it flies
	 * through the castle and would hurt mobs and test players in any test that runs after one that joined a player.
	 */
	static void clearMobs(final ServerLevel end) {
		for (Entity entity : List.copyOf(iterate(end))) {
			if (entity instanceof ServerPlayer) {
				continue;
			}
			if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
				|| entity instanceof net.minecraft.world.entity.projectile.hurtingprojectile.Fireball
				|| entity instanceof net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball
				|| entity instanceof net.minecraft.world.entity.AreaEffectCloud
				|| entity instanceof net.minecraft.world.entity.item.ItemEntity
				|| entity instanceof EndCrystal
				|| entity.entityTags().stream().anyMatch(tag -> tag.startsWith(EndMobs.FLOOR_TAG) || tag.startsWith("mod-stella-end"))) {
				entity.discard();
			}
		}
	}

	/**
	 * Kills a creature, again while an enchantment worn by it negates the blow. The equips mod's Deflection turns away a
	 * share of hits, kill blows included, and a knight carries six random enchantments on every piece.
	 */
	static void slay(final ServerLevel end, final net.minecraft.world.entity.LivingEntity target) {
		for (int attempt = 0; attempt < 400 && target.isAlive(); attempt++) {
			target.kill(end);
		}
	}

	static List<Entity> entities(final ServerLevel end) {
		return iterate(end);
	}

	private static List<Entity> iterate(final ServerLevel end) {
		java.util.ArrayList<Entity> all = new java.util.ArrayList<>();
		end.getAllEntities().forEach(all::add);
		return all;
	}

	/** The ring of chunks the dragon's tests hold beyond the castle: it flies within forty-eight blocks of the origin. */
	static final int RING_FROM = -3;
	static final int RING_TO = 2;

	/** The chunk span of a castle's outer zone for a keep corner, from its first chunk to its last, on each axis. */
	private static int zoneFrom(final int corner) {
		return corner - EndCastle.ZONE_MARGIN >> 4;
	}

	private static int zoneTo(final int corner) {
		return corner + EndCastle.SIZE + EndCastle.ZONE_MARGIN - 1 >> 4;
	}

	/** Loads and keeps the sixteen chunks of a castle's outer zone: a test player does not move chunk tickets as a real one does. */
	static void hold(final ServerLevel level, final BlockPos min) {
		int x0 = zoneFrom(min.getX());
		int z0 = zoneFrom(min.getZ());
		int x1 = zoneTo(min.getX());
		int z1 = zoneTo(min.getZ());
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				level.getChunk(cx, cz);
				level.setChunkForced(cx, cz, true);
			}
		}
		for (int i = 0; i < 1000 && level.getChunkSource().pollTask(); i++) {
		}
		// A saved chunk loads entities after blocks.
		// An entity added before then is refused.
		for (int i = 0; i < 20000 && !entitiesLoaded(level, x0, z0, 0, x1 - x0); i++) {
			level.getChunkSource().pollTask();
		}
	}

	private static boolean entitiesLoaded(final ServerLevel level, final int x0, final int z0, final int from, final int to) {
		for (int dx = from; dx <= to; dx++) {
			for (int dz = from; dz <= to; dz++) {
				if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(x0 + dx, z0 + dz))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether a chunk belongs to the castle's own sixteen, which every test holds. */
	private static boolean inCastle(final int cx, final int cz) {
		return cx >= zoneFrom(EndCastle.MIN_X) && cx <= zoneTo(EndCastle.MIN_X) && cz >= zoneFrom(EndCastle.MIN_Z) && cz <= zoneTo(EndCastle.MIN_Z);
	}

	/** Holds the ring round the castle too, for a test of something that flies off the roof; an enderman would teleport into it. */
	static void holdRing(final ServerLevel level) {
		for (int cx = RING_FROM; cx <= RING_TO; cx++) {
			for (int cz = RING_FROM; cz <= RING_TO; cz++) {
				if (!inCastle(cx, cz)) {
					level.getChunk(cx, cz);
					level.setChunkForced(cx, cz, true);
				}
			}
		}
		for (int i = 0; i < 1000 && level.getChunkSource().pollTask(); i++) {
		}
		for (int i = 0; i < 20000 && !entitiesLoaded(level, RING_FROM, RING_FROM, 0, RING_TO - RING_FROM); i++) {
			level.getChunkSource().pollTask();
		}
	}

	static void releaseRing(final ServerLevel level) {
		for (int cx = RING_FROM; cx <= RING_TO; cx++) {
			for (int cz = RING_FROM; cz <= RING_TO; cz++) {
				if (!inCastle(cx, cz)) {
					level.setChunkForced(cx, cz, false);
				}
			}
		}
	}

	/** Starts a test that has the dragon fly: like {@link #fresh}, and the ring round the castle held loaded. */
	static ServerLevel freshWide(final GameTestHelper helper) {
		ServerLevel end = fresh(helper);
		holdRing(end);
		return end;
	}

	static void release(final ServerLevel level, final BlockPos min) {
		for (int cx = zoneFrom(min.getX()); cx <= zoneTo(min.getX()); cx++) {
			for (int cz = zoneFrom(min.getZ()); cz <= zoneTo(min.getZ()); cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		releaseRing(level);
	}

	/** A player joined through the player list and sent to a spot of a level, so every system that counts players sees them. */
	static ServerPlayer join(final GameTestHelper helper, final ServerLevel level, final Vec3 at) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "climber"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleport(new TeleportTransition(level, at, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
		vulnerable(player);
		// Castle mobs hit hard, and a dead player stops every floor near them. The player takes no damage but stays a
		// target: an invulnerable player is dropped by every mob's target goals.
		player.addTag(EndGameTestInit.TEST_PLAYER_TAG);
		return player;
	}

	/**
	 * Lets a test player be hurt and hunted. A player with no client counts as changing dimension and as not yet
	 * loaded, and either makes them invulnerable; a real client clears both within moments.
	 */
	static void vulnerable(final ServerPlayer player) {
		player.hasChangedDimension();
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	/** The world position of a local castle cell at an absolute y. */
	static BlockPos world(final BlockPos min, final int lx, final int y, final int lz) {
		return new BlockPos(min.getX() + lx, y, min.getZ() + lz);
	}

	/** A player standing on the landing cell of a floor of the real castle. */
	static ServerPlayer joinOnFloor(final GameTestHelper helper, final ServerLevel end, final int floor) {
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(floor);
		BlockPos at = world(EndCastle.min(), plan.landingX(), EndCastle.standY(floor), plan.landingZ());
		return join(helper, end, Vec3.atBottomCenterOf(at));
	}

	/** The spawn cell of a floor nearest to a local cell among those between two distances of it, at the floor's standing height. */
	static BlockPos cellNear(final GameTestHelper helper, final CastleLayout.FloorPlan plan, final int floor, final int ox, final int oz, final double min, final double max) {
		int[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int[] cell : plan.spawnCells()) {
			double distance = Math.hypot(cell[0] - ox, cell[1] - oz);
			if (distance >= min && distance <= max && distance < bestDistance) {
				best = cell;
				bestDistance = distance;
			}
		}
		helper.assertTrue(best != null, "floor " + floor + " has no spawn cell " + min + " to " + max + " from " + ox + "," + oz);
		return world(EndCastle.min(), best[0], EndCastle.standY(floor), best[1]);
	}

	static BlockState state(final ServerLevel level, final BlockPos min, final int lx, final int y, final int lz) {
		return level.getBlockState(world(min, lx, y, lz));
	}

	/** The tag of a stand-in a test hits; it keeps `fresh` from leaving it behind. */
	static final String DUMMY_TAG = "mod-stella-end-test.dummy";

	/** A zombie that stands still and takes hits: claimed so no sibling rolls it, and as healthy as the attribute allows. */
	static Zombie dummy(final ServerLevel end, final Vec3 at) {
		Zombie zombie = EntityTypes.ZOMBIE.create(end, EntitySpawnReason.SPAWNER);
		LevelBridge.claim(zombie);
		zombie.addTag(DUMMY_TAG);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0E6);
		zombie.setHealth(zombie.getMaxHealth());
		zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		admitted(end, zombie, at);
		return zombie;
	}

	/** The level refuses an entity whose chunk has not loaded its entities; a refused stand-in would sit in a test and never tick. */
	private static void admitted(final ServerLevel end, final Entity entity, final Vec3 at) {
		if (!end.addFreshEntity(entity)) {
			throw new IllegalStateException("the level refused " + entity.getType().getDescriptionId() + " at " + at);
		}
	}

	/** A dummy that falls and flies when thrown, as a creature with AI on does, yet cannot walk off. */
	static Zombie mobileDummy(final ServerLevel end, final Vec3 at) {
		Zombie zombie = EntityTypes.ZOMBIE.create(end, EntitySpawnReason.SPAWNER);
		LevelBridge.claim(zombie);
		zombie.addTag(DUMMY_TAG);
		zombie.setPersistenceRequired();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0E6);
		zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		zombie.setHealth(zombie.getMaxHealth());
		zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		admitted(end, zombie, at);
		return zombie;
	}

	/**
	 * A player standing on the deck of the rooftop, a given offset from its middle. A ruined room or the hatch house may
	 * stand on the asked cell, so the player takes the nearest open cell of the deck instead.
	 */
	static ServerPlayer joinOnRoof(final GameTestHelper helper, final ServerLevel end, final double dx, final double dz) {
		return join(helper, end, openDeck(EndCastles.layout(end), dx, dz));
	}

	/** The point of the deck nearest to an offset from its middle that no ruined room or hatch house covers, within a cell of margin. */
	static Vec3 openDeck(final CastleLayout layout, final double dx, final double dz) {
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int ox = -8; ox <= 8; ox++) {
			for (int oz = -8; oz <= 8; oz++) {
				double x = dx + ox;
				double z = dz + oz;
				int lx = (int) Math.floor(16.5 + x);
				int lz = (int) Math.floor(16.5 + z);
				boolean covered = layout.roof().ruins().stream().anyMatch(ruin -> ruin.bounds().x0() - 1 <= lx && lx <= ruin.bounds().x1() + 1
					&& ruin.bounds().z0() - 1 <= lz && lz <= ruin.bounds().z1() + 1);
				CastleLayout.Rect house = layout.roof().hatchHouse();
				covered |= house.x0() - 1 <= lx && lx <= house.x1() + 1 && house.z0() - 1 <= lz && lz <= house.z1() + 1;
				double distance = ox * ox + oz * oz;
				if (!covered && distance < bestDistance) {
					best = new Vec3(0.5 + x, EndCastle.ROOF_Y + 1, 0.5 + z);
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/** An end crystal on the pillar of a spike, as the End's generator puts it. Its chunk is held loaded. */
	static EndCrystal crystal(final ServerLevel end, final int spike) {
		net.minecraft.world.level.levelgen.feature.EndSpikeFeature.EndSpike pillar = DragonBuffs.spikes(end).get(spike);
		int cx = pillar.getCenterX() >> 4;
		int cz = pillar.getCenterZ() >> 4;
		end.getChunk(cx, cz);
		end.setChunkForced(cx, cz, true);
		EndCrystal crystal = EntityTypes.END_CRYSTAL.create(end, EntitySpawnReason.STRUCTURE);
		crystal.snapTo(pillar.getCenterX() + 0.5, pillar.getHeight() + 1, pillar.getCenterZ() + 0.5, 0.0F, 0.0F);
		end.addFreshEntity(crystal);
		return crystal;
	}

	/** Lets the forced pillar chunks go and clears the fire a crystal sets at its foot. */
	static void releaseCrystals(final ServerLevel end) {
		for (net.minecraft.world.level.levelgen.feature.EndSpikeFeature.EndSpike pillar : DragonBuffs.spikes(end)) {
			end.setChunkForced(pillar.getCenterX() >> 4, pillar.getCenterZ() >> 4, false);
			BlockPos foot = new BlockPos(pillar.getCenterX(), pillar.getHeight() + 1, pillar.getCenterZ());
			if (end.getBlockState(foot).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
				end.setBlock(foot, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
	}

	static void result(final String test, final String outcome) {
		StellaEnd.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, outcome);
	}
}
