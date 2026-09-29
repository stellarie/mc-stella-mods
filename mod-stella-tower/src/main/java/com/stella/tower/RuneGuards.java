package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * An armor rune's signature, by its element: what it does for its wearer beyond turning blows. Each
 * works while any worn piece carries that element, at the strongest such piece's power.
 */
public final class RuneGuards {
	public static final ResourceKey<DamageType> DELAYED_WOUND = ResourceKey.create(Registries.DAMAGE_TYPE, StellaTower.id("delayed_wound"));
	public static final float HEAT_STORE_CAP = 20.0F;
	public static final double BIG_HIT_SHARE = 0.25;
	public static final double BIG_HIT_NOW = 0.25;
	public static final int SPREAD_PARTS = 3;
	public static final int SPREAD_TICKS = 20;
	public static final double VOID_RANGE = 8.0;
	public static final double DARK_PER_KILL = 0.01;
	public static final double DARK_CAP = 0.30;
	/** Dark armor's kills count while each comes within this long of the one before. */
	public static final int DARK_KILL_WINDOW_TICKS = 1200;

	private static final AttachmentType<Float> HEAT = AttachmentRegistry.create(StellaTower.id("rune_stored_heat"));
	private static final AttachmentType<Kills> KILLS = AttachmentRegistry.create(StellaTower.id("rune_floor_kills"));
	/** The part of the blow now landing that Water armor holds back, until the blow is known to have landed. */
	private static final AttachmentType<Held> HELD = AttachmentRegistry.create(StellaTower.id("rune_held_wound"));
	/** Set while a blow carries stored heat, so the heat is spent only once the blow has landed. */
	private static final AttachmentType<Boolean> KINDLING = AttachmentRegistry.create(StellaTower.id("rune_kindling"));

	private record Kills(int lastAt, int count) {
	}

	private record Held(float rest) {
	}

	/** What is left of a spread blow: its rest, the parts it still comes in, and the ticks to the next. */
	public record Wound(float rest, int parts, int delay) {
		public static final Codec<Wound> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("rest").forGetter(Wound::rest),
			Codec.INT.fieldOf("parts").forGetter(Wound::parts),
			Codec.INT.fieldOf("delay").forGetter(Wound::delay)
		).apply(instance, Wound::new));
	}

	/** Saved with the player, so a spread blow still lands after they log out and back in. */
	public static final AttachmentType<Wound> WOUND = AttachmentRegistry.create(
		StellaTower.id("rune_wound"), builder -> builder.persistent(Wound.CODEC)
	);

	private RuneGuards() {
	}

	public static void register() {
		// Lightning armor grounds a bolt whole.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(source.is(DamageTypeTags.IS_LIGHTNING) && worn(entity, TowerElement.LIGHTNING) != null));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Held held = entity.getAttached(HELD);
			entity.removeAttached(HELD);
			if (blocked || taken <= 0.0F || !(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
				return;
			}
			if (held != null) {
				// The rest is dealt later as blows of its own, which armor and resistance meet as they come.
				wound(player, held.rest());
			}
			if (source.is(DamageTypeTags.IS_FIRE) && worn(player, TowerElement.FIRE) != null) {
				player.setAttached(HEAT, Math.min(HEAT_STORE_CAP, storedHeat(player) + taken));
			}
			if (source.getEntity() instanceof Enemy && worn(player, TowerElement.LIGHTNING) != null) {
				RuneEffects.addCharge(player);
			}
		});
		// Stored heat is spent by the blow that carried it, once that blow has landed.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof Player player && player != entity && player.hasAttached(KINDLING)) {
				player.removeAttached(KINDLING);
				if (!blocked && taken > 0.0F) {
					player.removeAttached(HEAT);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Enemy) || entity instanceof TowerGhost || !(source.getEntity() instanceof ServerPlayer player)) {
				return;
			}
			int now = now(player);
			player.setAttached(KILLS, new Kills(now, recentKills(player) + 1));
		});
		ServerTickEvents.END_SERVER_TICK.register(RuneGuards::spread);
	}

	public static double windCut(final double power) {
		return 0.15 + power / 2000.0;
	}

	public static double holyCut(final double power) {
		return 0.20 + power / 1000.0;
	}

	/** The power of the strongest worn armor rune of this element, or null if none is worn. */
	public static @Nullable Double worn(final LivingEntity wearer, final TowerElement element) {
		// A mob that picks up runed armor wears it, but only a player carries its signatures.
		if (!(wearer instanceof Player)) {
			return null;
		}
		Double best = null;
		for (EquipmentSlot slot : TowerRunes.ARMOR_SLOTS) {
			ItemStack piece = wearer.getItemBySlot(slot);
			RuneData rune = TowerRunes.slotOf(piece) == TowerRunes.Slot.ARMOR ? TowerRunes.runeOf(piece) : null;
			if (rune != null && rune.element() == element) {
				double power = TowerRunes.power(piece, rune);
				best = best == null ? power : Math.max(best, power);
			}
		}
		return best;
	}

	public static float storedHeat(final Player player) {
		return player.getAttachedOrElse(HEAT, 0.0F);
	}

	public static float pendingWound(final Player player) {
		Wound wound = player.getAttached(WOUND);
		return wound == null ? 0.0F : wound.rest();
	}

	/** The mobs a player has slain, each within a minute of the last. */
	public static int recentKills(final Player player) {
		Kills kills = player.getAttached(KILLS);
		return kills != null && now(player) - kills.lastAt() <= DARK_KILL_WINDOW_TICKS ? kills.count() : 0;
	}

	private static int now(final LivingEntity entity) {
		return entity.level().getServer() == null ? entity.tickCount : entity.level().getServer().getTickCount();
	}

	/** Whether a Void-armored player stands near enough this hostile mob to halve what strengthens it. */
	public static boolean dampened(final LivingEntity mob) {
		return mob instanceof Enemy && !(mob instanceof TowerGhost) && mob.level() instanceof ServerLevel level
			&& level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), VOID_RANGE,
				player -> !player.isSpectator() && worn((LivingEntity) player, TowerElement.VOID) != null) != null;
	}

	/** A blow on a rune-armored wearer, once its armor signatures have had their say. */
	public static float guard(final LivingEntity victim, final DamageSource source, final float amount) {
		victim.removeAttached(HELD);
		if (!(victim instanceof Player) || amount <= 0.0F || source.is(DELAYED_WOUND)) {
			return amount;
		}
		float guarded = amount;
		Double wind = worn(victim, TowerElement.WIND);
		if (wind != null && source.is(DamageTypeTags.IS_PROJECTILE)) {
			guarded = (float) (guarded * (1.0 - windCut(wind)));
		}
		if (source.getEntity() instanceof Mob && worn(victim, TowerElement.DARK) != null && victim instanceof Player player) {
			guarded = (float) (guarded * (1.0 - Math.min(DARK_CAP, DARK_PER_KILL * recentKills(player))));
		}
		if (worn(victim, TowerElement.WATER) != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && guarded > BIG_HIT_SHARE * victim.getMaxHealth()) {
			float now = (float) (guarded * BIG_HIT_NOW);
			victim.setAttached(HELD, new Held(guarded - now));
			guarded = now;
		}
		return guarded;
	}

	/**
	 * A blow a Fire-armored player strikes, with the heat they have taken added after the weapon's tier, so a
	 * rune does not multiply it. The heat is spent once the blow lands.
	 */
	public static float kindle(final LivingEntity victim, final DamageSource source, final float amount) {
		if (!(source.getEntity() instanceof Player player) || player == victim) {
			return amount;
		}
		float heat = storedHeat(player);
		if (heat <= 0.0F) {
			return amount;
		}
		player.setAttached(KINDLING, true);
		return amount + heat;
	}

	/**
	 * An effect an entity is given, once armor runes have had their say: on the wearer, Ice halves slowness
	 * and Holy shortens every harm; on a hostile mob near a Void wearer, a boon comes at half its amplifier.
	 */
	public static MobEffectInstance temper(final LivingEntity wearer, final MobEffectInstance effect) {
		if (effect.getAmplifier() > 0 && effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL && dampened(wearer)) {
			return new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier() / 2, effect.isAmbient(), effect.isVisible(), effect.showIcon());
		}
		if (effect.isInfiniteDuration() || effect.getDuration() <= 0) {
			return effect;
		}
		double scale = 1.0;
		if (effect.is(MobEffects.SLOWNESS) && worn(wearer, TowerElement.ICE) != null) {
			scale *= 0.5;
		}
		Double holy = worn(wearer, TowerElement.HOLY);
		if (holy != null && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
			scale *= 1.0 - holyCut(holy);
		}
		if (scale >= 1.0) {
			return effect;
		}
		int duration = Math.max(1, (int) Math.round(effect.getDuration() * scale));
		return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	/** Frost gathering on an Ice-armored wearer gathers half as fast. */
	public static int thaw(final LivingEntity wearer, final int current, final int wanted) {
		int gain = wanted - current;
		if (gain <= 0 || worn(wearer, TowerElement.ICE) == null) {
			return wanted;
		}
		// A tick's single step of powder snow gets through every other tick, so half still gathers.
		return current + gain / 2 + (gain % 2 != 0 && wearer.tickCount % 2 == 0 ? 1 : 0);
	}

	private static void wound(final Player victim, final float rest) {
		if (rest <= 0.0F) {
			return;
		}
		Wound wound = victim.getAttached(WOUND);
		victim.setAttached(WOUND, wound == null ? new Wound(rest, SPREAD_PARTS, SPREAD_TICKS) : new Wound(wound.rest() + rest, SPREAD_PARTS, wound.delay()));
	}

	private static void spread(final MinecraftServer server) {
		List<ServerPlayer> hurt = new ArrayList<>();
		List<Float> parts = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Wound wound = player.getAttached(WOUND);
			if (wound == null) {
				continue;
			}
			if (!player.isAlive()) {
				player.removeAttached(WOUND);
				continue;
			}
			if (wound.delay() > 1) {
				player.setAttached(WOUND, new Wound(wound.rest(), wound.parts(), wound.delay() - 1));
				continue;
			}
			float part = wound.parts() <= 1 ? wound.rest() : wound.rest() / wound.parts();
			if (wound.parts() <= 1) {
				player.removeAttached(WOUND);
			} else {
				player.setAttached(WOUND, new Wound(wound.rest() - part, wound.parts() - 1, SPREAD_TICKS));
			}
			hurt.add(player);
			parts.add(part);
		}
		// Dealt outside the sweep, so a wound that kills cannot disturb it.
		for (int k = 0; k < hurt.size(); k++) {
			ServerPlayer player = hurt.get(k);
			ServerLevel level = player.level();
			player.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DELAYED_WOUND)), parts.get(k));
		}
	}
}
