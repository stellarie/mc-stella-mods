package com.stella.villages.mission;

import com.stella.mobs.scaling.ThreatState;
import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The duke's repeatable beginner contract: defeat one enraged red slime 128 to 256 blocks from the bell.
 * The slime is level 20, has 1000 health, and has permanent Speed II and Strength II. A player kill drops the Apprentice's
 * Chestplate. A player holds at most one slayer contract at a time.
 */
public final class SlayerContract {
	public static final Identifier TARGET = Identifier.parse("mod-stella-mobs:red_slime");
	public static final int LEVEL = 20;
	/** Speed II and Strength II: the amplifier is the level minus one. */
	public static final int EFFECT_AMPLIFIER = 1;
	public static final int MIN_DISTANCE = 128;
	public static final int MAX_DISTANCE = 256;
	/** The board slot of the contract icon. */
	public static final int SLOT = 0;
	public static final double HEALTH = 1000.0;
	public static final String NAME = "<Enraged> Red Slime";

	private SlayerContract() {
	}

	/** Sets the base maximum and fills the health. The red slime resets its stats on every load. */
	public static void restoreHealth(final Mob slime) {
		AttributeInstance health = slime.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(HEALTH);
			slime.setHealth(slime.getMaxHealth());
		}
	}

	public static ItemStack icon() {
		ItemStack icon = new ItemStack(Items.SLIME_BALL);
		icon.set(DataComponents.ITEM_NAME, Component.literal("Slay the Enraged Red Slime"));
		icon.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Slayer contract: one enraged red slime, level " + LEVEL + ", " + (int) HEALTH + " health"),
			Component.literal("Somewhere " + MIN_DISTANCE + " to " + MAX_DISTANCE + " blocks from the village"),
			Component.literal("Reward: Apprentice's Chestplate"),
			Component.literal("Repeatable, one at a time"),
			Component.literal("Click to accept")
		)));
		return icon;
	}

	public static boolean hasActive(final ServerLevel level, final UUID player) {
		return VillageWorldData.get(level).missions().stream()
			.anyMatch(mission -> mission.kind() == MissionKind.SLAYER && mission.ownerId().equals(player));
	}

	/** Spawns the enraged slime at the mission target and marks the mission active. Null when it cannot spawn. */
	public static @Nullable Mob spawnTarget(final ServerLevel level, final MissionRecord mission) {
		Mob slime = QuestMissions.create(level, mission);
		if (slime == null) {
			return null;
		}
		QuestMissions.place(slime, Vec3.atBottomCenterOf(mission.targetPosition()), mission);
		slime.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		((AttachmentTarget) slime).setAttached(ThreatState.TYPE, new ThreatState(LEVEL, 0, 0, 0));
		restoreHealth(slime);
		slime.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, EFFECT_AMPLIFIER));
		slime.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, EFFECT_AMPLIFIER));
		slime.setCustomName(Component.literal(NAME));
		QuestMissions.questName(slime);
		slime.setCustomNameVisible(true);
		if (!level.addFreshEntity(slime)) {
			McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=quest_rejected mission={} reason=entity_insertion",
				McStellaVillages.MOD_ID, mission.id());
			return null;
		}
		VillageWorldData.get(level).addMission(mission.spawned(Optional.of(slime.getUUID()), 1, List.of(slime.getUUID())));
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=slayer_quest_spawned mission={} entity={} owner={}",
			McStellaVillages.MOD_ID, mission.id(), slime.getUUID(), mission.ownerId());
		return slime;
	}
}
