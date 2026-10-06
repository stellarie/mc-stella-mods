package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.MobAbility;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The two themed mobs of the crater, and the loot tables the crater owns. */
public final class CraterMobs {
	public static final ResourceKey<LootTable> VAULT_LOOT = table("chests/meteor_crater");
	public static final ResourceKey<LootTable> GOLEM_DROPS = table("entities/star_golem");
	public static final ResourceKey<LootTable> SLIME_DROPS = table("entities/ember_slime");

	public static final float GOLEM_SCALE = 1.3F;
	public static final int SLIME_SIZE = 2;
	/** Ticks between two strikes of one golem. */
	public static final int STRIKE_TICKS = 160;
	/** Ticks between the warning and the strike. */
	public static final int WARNING_TICKS = 20;
	/** Ticks a golem's strike cycle shifts for each slot, so the three golems never strike together. */
	public static final int STRIKE_STAGGER = 50;
	public static final float STRIKE_DAMAGE = 5.0F;
	/** How far from the marked spot a player still takes the strike. */
	public static final double STRIKE_RADIUS = 2.0;
	/** How far a golem looks for a player to hunt or to strike. */
	public static final double HUNT_RANGE = 24.0;
	public static final String SLOT_TAG_PREFIX = "mod-stella-ruins.meteor.slot.";
	private static final String SIZED_TAG = "mod-stella-ruins.sized";

	public static RuinMobKind STAR_GOLEM;
	public static RuinMobKind EMBER_SLIME;

	private CraterMobs() {
	}

	private static ResourceKey<LootTable> table(final String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(StellaRuins.MOD_ID, path));
	}

	public static void register() {
		STAR_GOLEM = RuinMobKind.builder("star_golem")
			.base(EntityTypes.IRON_GOLEM)
			.scale(GOLEM_SCALE)
			.effect(MobEffects.GLOWING, 0)
			.ability(new StarStrike())
			.hostile()
			.drops(GOLEM_DROPS)
			.register();
		EMBER_SLIME = RuinMobKind.builder("ember_slime")
			.base(EntityTypes.MAGMA_CUBE)
			.ability(new MobAbility() {
				@Override
				public void tick(final Mob mob, final ServerLevel level) {
					size(mob);
					forgetNonPlayers(mob);
				}
			})
			.drops(SLIME_DROPS)
			.register();
	}

	/** Grows a fresh Ember Slime to its size, once. */
	public static void size(final Mob mob) {
		if (mob instanceof AbstractCubeMob cube && !mob.entityTags().contains(SIZED_TAG)) {
			mob.addTag(SIZED_TAG);
			cube.setSize(SLIME_SIZE, true);
		}
	}

	/** A themed mob only fights players: a vanilla golem and a magma cube would fight each other. */
	static void forgetNonPlayers(final Mob mob) {
		LivingEntity target = mob.getTarget();
		if (target != null && !(target instanceof Player)) {
			mob.setTarget(null);
		}
	}

	/** The slot a golem was given at its wake, or 0. */
	public static int slotOf(final Mob mob) {
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(SLOT_TAG_PREFIX)) {
				try {
					return Integer.parseInt(tag.substring(SLOT_TAG_PREFIX.length()));
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 0;
	}

	/** The falling-star strike of a Star Golem: a warning, then 5 damage at the marked spot. */
	public static final class StarStrike implements MobAbility {
		private record Pending(Vec3 spot, int due) {
		}

		private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

		/** The strike a golem has marked and not yet dropped, or null. */
		public static @Nullable Vec3 pending(final Mob mob) {
			Pending pending = PENDING.get(mob.getUUID());
			return pending == null ? null : pending.spot();
		}

		public static int offset(final int slot) {
			return slot * STRIKE_STAGGER;
		}

		@Override
		public void tick(final Mob mob, final ServerLevel level) {
			forgetNonPlayers(mob);
			Player nearest = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), HUNT_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
			if (mob.getTarget() == null && nearest != null) {
				mob.setTarget(nearest);
			}
			Pending pending = PENDING.get(mob.getUUID());
			if (pending != null) {
				warn(level, pending.spot());
				if (mob.tickCount >= pending.due()) {
					PENDING.remove(mob.getUUID());
					land(mob, level, pending.spot());
				}
				return;
			}
			int phase = mob.tickCount - offset(slotOf(mob));
			if (phase >= STRIKE_TICKS && phase % STRIKE_TICKS < RuinMobs.ABILITY_TICKS && nearest != null) {
				Vec3 spot = nearest.position();
				PENDING.put(mob.getUUID(), new Pending(spot, mob.tickCount + WARNING_TICKS));
				StellaRuins.LOGGER.debug("[RUINS] star golem {} marks {}", mob.getUUID(), spot);
				warn(level, spot);
			}
		}

		private static void warn(final ServerLevel level, final Vec3 spot) {
			for (int i = 0; i < 12; i++) {
				double angle = i * Math.PI / 6.0;
				level.sendParticles(ParticleTypes.END_ROD, spot.x + Math.cos(angle) * STRIKE_RADIUS, spot.y + 0.1, spot.z + Math.sin(angle) * STRIKE_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.FIREWORK, spot.x, spot.y + 6.0, spot.z, 4, 0.2, 2.0, 0.2, 0.02);
		}

		private static void land(final Mob mob, final ServerLevel level, final Vec3 spot) {
			level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.5, spot.z, 2, 0.4, 0.2, 0.4, 0.0);
			level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 0.3, spot.z, 24, 0.8, 0.2, 0.8, 0.05);
			level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
			for (Player player : level.players()) {
				if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(spot) <= STRIKE_RADIUS * STRIKE_RADIUS) {
					player.hurtServer(level, level.damageSources().indirectMagic(mob, mob), STRIKE_DAMAGE);
				}
			}
		}
	}
}
