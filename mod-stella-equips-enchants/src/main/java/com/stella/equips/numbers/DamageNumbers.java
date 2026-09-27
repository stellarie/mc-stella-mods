package com.stella.equips.numbers;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A small number over an entity for each hit it takes (red) and each heal (green), rising and fading,
 * like an MMO. The number is a text display sent to the nearby players and never added to the level,
 * so it is never saved; the rise and the fade are one update the client interpolates.
 */
public final class DamageNumbers {
	public static final int LIFETIME_TICKS = 20;
	public static final float RISE_BLOCKS = 1.0F;
	public static final float SCALE = 0.7F;
	public static final double VIEW_RADIUS = 32.0;
	/** The text's alpha at the end of the fade; the client interpolates to it with the rise. */
	public static final byte END_OPACITY = 32;
	private static final double HEIGHT_ABOVE = 0.4;
	private static final double JITTER = 0.4;
	/** One damage number and one heal number per entity in this many ticks, so a mob farm is not a packet flood. */
	public static final int MIN_GAP_TICKS = 4;

	public interface Recorder {
		void shown(LivingEntity entity, Display.TextDisplay display, float amount, boolean heal);
	}

	private record Shown(Display.TextDisplay display, List<ServerPlayer> viewers, int bornTick) {
	}

	private static final Recorder SILENT = (entity, display, amount, heal) -> {
	};
	private static volatile Recorder recorder = SILENT;
	private static final List<Shown> LIVE = new ArrayList<>();
	/** Each entity's health and absorption as a hit began, until the hit resolves. */
	private static final Map<LivingEntity, Float> BEFORE_HIT = new WeakHashMap<>();
	/** The server tick each entity last showed a number, for damage and for heals apart. */
	private static final Map<LivingEntity, Integer> LAST_HIT_NUMBER = new WeakHashMap<>();
	private static final Map<LivingEntity, Integer> LAST_HEAL_NUMBER = new WeakHashMap<>();

	private DamageNumbers() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	public static void register() {
		// The event's damage figure comes before Resistance, so the number is the health the entity actually lost.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			synchronized (BEFORE_HIT) {
				BEFORE_HIT.put(entity, vitality(entity));
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> lost(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> lost(entity));
		ServerTickEvents.END_SERVER_TICK.register(DamageNumbers::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			synchronized (LIVE) {
				LIVE.clear();
			}
		});
	}

	private static void lost(final LivingEntity entity) {
		Float before;
		synchronized (BEFORE_HIT) {
			before = BEFORE_HIT.remove(entity);
		}
		if (before != null && before - vitality(entity) > 0.0F) {
			show(entity, before - vitality(entity), false);
		}
	}

	// Health and absorption together: a hit the golden hearts soak up is still a hit taken.
	private static float vitality(final LivingEntity entity) {
		return Math.max(0.0F, entity.getHealth()) + entity.getAbsorptionAmount();
	}

	/** Called with the health an entity actually gained from a heal. */
	public static void healed(final LivingEntity entity, final float amount) {
		if (amount > 0.0F) {
			show(entity, amount, true);
		}
	}

	private static void show(final LivingEntity entity, final float amount, final boolean heal) {
		// An invisible entity shows no number, which would give its place away.
		if (!(entity.level() instanceof ServerLevel level) || entity instanceof ArmorStand || entity.isInvisible()) {
			return;
		}
		int now = level.getServer().getTickCount();
		Map<LivingEntity, Integer> last = heal ? LAST_HEAL_NUMBER : LAST_HIT_NUMBER;
		synchronized (last) {
			Integer previous = last.get(entity);
			if (previous != null && now - previous < MIN_GAP_TICKS && now >= previous) {
				return;
			}
			last.put(entity, now);
		}

		Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		display.setId(level.getNextEntityId());
		double jitterX = (level.getRandom().nextDouble() * 2.0 - 1.0) * JITTER;
		double jitterZ = (level.getRandom().nextDouble() * 2.0 - 1.0) * JITTER;
		display.setPos(entity.getX() + jitterX, entity.getY() + entity.getBbHeight() + HEIGHT_ABOVE, entity.getZ() + jitterZ);
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		display.setText(Component.literal((heal ? "+" : "") + format(amount)).withStyle(heal ? ChatFormatting.GREEN : ChatFormatting.RED));
		display.setBackgroundColor(0);
		display.setTransformation(transformation(0.0F));
		display.updateDataBeforeSync();

		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			// A player never sees the number over their own head, where it would sit in their face.
			if (player != entity && player.distanceToSqr(entity) <= VIEW_RADIUS * VIEW_RADIUS) {
				viewers.add(player);
				player.connection.send(new ClientboundAddEntityPacket(
					display.getId(), display.getUUID(), display.getX(), display.getY(), display.getZ(),
					0.0F, 0.0F, EntityTypes.TEXT_DISPLAY, 0, Vec3.ZERO, 0.0
				));
				player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), display.getEntityData().getNonDefaultValues()));
			}
		}

		synchronized (LIVE) {
			LIVE.add(new Shown(display, viewers, level.getServer().getTickCount()));
		}
		recorder.shown(entity, display, amount, heal);
	}

	// On the tick after it appears, the number is sent its end state, and the client eases it there over its life.
	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		synchronized (LIVE) {
			for (Iterator<Shown> it = LIVE.iterator(); it.hasNext(); ) {
				Shown shown = it.next();
				Display.TextDisplay display = shown.display();
				int age = now - shown.bornTick();
				if (age == 1) {
					display.setTransformationInterpolationDelay(0);
					display.setTransformationInterpolationDuration(LIFETIME_TICKS - 1);
					display.setTransformation(transformation(RISE_BLOCKS));
					display.setTextOpacity(END_OPACITY);
					var dirty = display.getEntityData().packDirty();
					if (dirty != null) {
						for (ServerPlayer viewer : shown.viewers()) {
							viewer.connection.send(new ClientboundSetEntityDataPacket(display.getId(), dirty));
						}
					}
				} else if (age >= LIFETIME_TICKS) {
					for (ServerPlayer viewer : shown.viewers()) {
						viewer.connection.send(new ClientboundRemoveEntitiesPacket(display.getId()));
					}
					it.remove();
				}
			}
		}
	}

	private static Transformation transformation(final float rise) {
		return new Transformation(new Vector3f(0.0F, rise, 0.0F), new Quaternionf(), new Vector3f(SCALE, SCALE, SCALE), new Quaternionf());
	}

	// Whole numbers read bare, and anything else to one decimal.
	static String format(final float amount) {
		float rounded = Math.round(amount * 10.0F) / 10.0F;
		return rounded == Math.rint(rounded) ? Integer.toString((int) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
	}
}
