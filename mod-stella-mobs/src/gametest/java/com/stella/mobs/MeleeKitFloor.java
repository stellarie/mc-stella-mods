package com.stella.mobs;

import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A wide night floor for the melee kit tests. It forces the chunks that the floor covers, since a mob in a chunk
 * that the test area does not reach never ticks. It builds a survival player that a mob can target, and it pins
 * every spawn to a config without gear, elite, boss, or rider rolls, so no mob brings an extra mob along. It
 * tracks what it spawned, so the test can remove it and can tell its own monsters from a stranger. The level
 * clock barely moves in a test, so nothing here reads it.
 */
final class MeleeKitFloor {
	static final int SIZE = 40;
	// Keeps the players of other tests out of reach.
	static final int PADDING = 64;
	// Spawned on the floor, a mob is never onGround.
	static final int SPAWN_Y = 3;
	static final int FLOOR_TOP_Y = 1;
	// Health that no mob can spend in a test.
	static final float PLAYER_HEALTH = 10000.0F;
	static final float MOB_HEALTH = 100.0F;
	// A zombie this close is in melee reach.
	static final double CONTACT_BLOCKS = 2.0;
	private static final long NIGHT_TIME = 18000L;
	private static final String NO_ROLL_CONFIG = """
		{
			"version": 1,
			"entities": {},
			"roles": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 },
			"riders": { "chance": 0.0 }
		}""";

	private final GameTestHelper helper;
	private final List<Entity> entities = new ArrayList<>();
	private final List<ChunkPos> forced = new ArrayList<>();
	private boolean ended;

	private MeleeKitFloor(final GameTestHelper helper) {
		this.helper = helper;
	}

	static MeleeKitFloor build(final GameTestHelper helper) {
		MeleeKitFloor scene = new MeleeKitFloor(helper);
		helper.setTime(NIGHT_TIME);
		// Peaceful drops every player from canAttack.
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		BlockPos low = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos high = helper.absolutePos(new BlockPos(SIZE, 0, SIZE));
		for (int chunkX = low.getX() >> 4; chunkX <= high.getX() >> 4; chunkX++) {
			for (int chunkZ = low.getZ() >> 4; chunkZ <= high.getZ() >> 4; chunkZ++) {
				helper.getLevel().setChunkForced(chunkX, chunkZ, true);
				scene.forced.add(new ChunkPos(chunkX, chunkZ));
			}
		}

		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				// The lower layers fill the world's air pocket.
				helper.setBlock(x, -2, z, Blocks.STONE);
				helper.setBlock(x, -1, z, Blocks.STONE);
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}

		helper.runBeforeTestEnd(scene::end);
		return scene;
	}

	ServerPlayer player(final double x, final double z) {
		ServerPlayer player = Arena.addSurvivalPlayer(this.helper);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(PLAYER_HEALTH);
		player.setHealth(PLAYER_HEALTH);
		player.getAbilities().instabuild = false;
		Vec3 at = this.helper.absoluteVec(new Vec3(x, FLOOR_TOP_Y, z));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	<E extends Mob> E mob(final EntityType<E> type, final int x, final int y, final int z) {
		// The pinned config stops extra rolls.
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(NO_ROLL_CONFIG));
		E mob;
		try {
			mob = this.helper.spawnMob(type, x, y, z).spawn();
		} finally {
			GearConfigStore.set(previous);
		}

		Arena.neutralThreat(mob);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MOB_HEALTH);
		mob.setHealth(MOB_HEALTH);
		mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		this.entities.add(mob);
		return mob;
	}

	// Forced chunks tick a few ticks late.
	boolean ticking() {
		return this.entities.stream().allMatch(
			entity -> entity.isAlive() && this.helper.getLevel().isPositionEntityTicking(entity.blockPosition())
		);
	}

	void clearStrangers(final Entity center, final double radius) {
		this.helper.getLevel()
			.getEntitiesOfClass(Monster.class, center.getBoundingBox().inflate(radius), monster -> !this.entities.contains(monster))
			.forEach(Entity::discard);
	}

	void end() {
		if (this.ended) {
			return;
		}

		this.ended = true;
		this.entities.forEach(Entity::discard);
		this.forced.forEach(pos -> this.helper.getLevel().setChunkForced(pos.x(), pos.z(), false));
	}

	static double flat(final Entity first, final Entity second) {
		return Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
	}

	static void lookAt(final ServerPlayer player, final Entity target) {
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
	}

	static void lookAway(final ServerPlayer player, final Entity target) {
		Vec3 eye = player.getEyePosition();
		player.lookAt(EntityAnchorArgument.Anchor.EYES, eye.add(eye.subtract(target.getEyePosition())));
	}
}
