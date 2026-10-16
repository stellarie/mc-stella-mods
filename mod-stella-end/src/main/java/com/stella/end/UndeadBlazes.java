package com.stella.end;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The Sovereign's undead blazes: vanilla blazes at level 115 that fire slow soul charges. The client draws them grey. */
public final class UndeadBlazes {
	public static final String TAG = "mod-stella-end.undead";
	public static final int WITHER_AMPLIFIER = 0;
	public static final int WITHER_TICKS = 80;
	/** What the client reads to tint a blaze: 1 for an undead blaze, 2 for the Sovereign. */
	public static final int MARK_BLAZE = 1;
	public static final int MARK_SOVEREIGN = 2;
	public static final AttachmentType<Integer> MARK = AttachmentRegistry.create(StellaEnd.id("undead_mark"), builder -> builder
		.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Identities of the undead blazes that stand loaded; load and unload keep it, so a cap counts what is really there. */
	private static final Set<UUID> LIVE = new LinkedHashSet<>();

	private UndeadBlazes() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (is(entity) && level.dimension().equals(Level.END)) {
				LIVE.add(entity.getUUID());
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (is(entity)) {
				LIVE.remove(entity.getUUID());
			}
		});
	}

	/** A level 115 undead blaze standing at a point, or null when the level refuses it. */
	public static @Nullable Blaze spawn(final ServerLevel end, final Vec3 at) {
		Blaze blaze = EntityTypes.BLAZE.create(end, EntitySpawnReason.EVENT);
		if (blaze == null) {
			return null;
		}
		LevelBridge.claim(blaze);
		blaze.addTag(TAG);
		blaze.setAttached(MARK, MARK_BLAZE);
		blaze.snapTo(at.x, at.y, at.z, end.getRandom().nextFloat() * 360.0F, 0.0F);
		blaze.setPersistenceRequired();
		LevelBridge.apply(blaze, EndLevels.UNDEAD_BLAZE);
		if (!end.addFreshEntity(blaze)) {
			return null;
		}
		LIVE.add(blaze.getUUID());
		return blaze;
	}

	public static boolean is(final @Nullable Entity entity) {
		return entity instanceof Blaze && entity.entityTags().contains(TAG);
	}

	/** The recorded undead blazes that stand loaded and alive. */
	public static List<Blaze> alive(final ServerLevel end) {
		List<Blaze> found = new ArrayList<>();
		for (UUID id : LIVE) {
			if (end.getEntity(id) instanceof Blaze blaze && blaze.isAlive()) {
				found.add(blaze);
			}
		}
		return found;
	}

	public static void reset() {
		LIVE.clear();
	}
}
