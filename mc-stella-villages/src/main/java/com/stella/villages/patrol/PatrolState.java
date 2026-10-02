package com.stella.villages.patrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.villages.McStellaVillages;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

public record PatrolState(UUID villageId, Role role, BlockPos lastPosition, boolean alive) {
	public static final Codec<PatrolState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UUIDUtil.CODEC.fieldOf("village_id").forGetter(PatrolState::villageId),
		Role.CODEC.fieldOf("role").forGetter(PatrolState::role),
		BlockPos.CODEC.optionalFieldOf("last_position", BlockPos.ZERO).forGetter(PatrolState::lastPosition),
		Codec.BOOL.optionalFieldOf("alive", true).forGetter(PatrolState::alive)
	).apply(instance, PatrolState::new));
	public static final AttachmentType<PatrolState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "patrol_state"), CODEC
	);

	public static void register() {
		McStellaVillages.LOGGER.debug("Registered patrol state attachment {}", TYPE.identifier());
	}

	public static @Nullable PatrolState get(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(TYPE);
	}

	public static @Nullable UUID villageId(final Mob mob) {
		PatrolState state = get(mob);
		return state == null ? null : state.villageId();
	}

	public static @Nullable Role role(final Mob mob) {
		PatrolState state = get(mob);
		return state == null ? null : state.role();
	}

	public static boolean isMarked(final Mob mob) {
		return get(mob) != null;
	}

	public static void attach(final Mob mob, final UUID villageId, final Role role) {
		((AttachmentTarget) mob).setAttached(TYPE, new PatrolState(villageId, role, mob.blockPosition(), true));
	}

	public static void updatePosition(final Mob mob) {
		PatrolState state = get(mob);
		if (state != null) {
			((AttachmentTarget) mob).setAttached(TYPE, new PatrolState(state.villageId(), state.role(), mob.blockPosition(), state.alive()));
		}
	}

	public static void markDead(final Mob mob) {
		PatrolState state = get(mob);
		if (state != null && state.alive()) {
			((AttachmentTarget) mob).setAttached(TYPE, new PatrolState(state.villageId(), state.role(), mob.blockPosition(), false));
		}
	}

	public enum Role {
		HEAD,
		VINDICATOR,
		// The melee guard. Its id is kept for saves.
		PILLAGER,
		EVOKER,
		RAVAGER,
		WITCH,
		VILLAGE_WITCH,
		RANGED_GUARD;

		public static final Codec<Role> CODEC = Codec.STRING.xmap(Role::decode, Role::name);

		private static Role decode(final String name) {
			try {
				return Role.valueOf(name);
			} catch (IllegalArgumentException error) {
				return VINDICATOR;
			}
		}
	}
}
