package com.stella.rpg;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** A melee hit sends no damage-indicator particles. The recorder is the attacker's own connection. */
public final class NoHeartsGameTest {
	@GameTest
	public void noDamageIndicator(final GameTestHelper helper) {
		RpgTestSupport.Recorded recorded = RpgTestSupport.recordedPlayer(helper);
		ServerPlayer attacker = recorded.player();
		drain(recorded);
		attacker.level().sendParticles(ParticleTypes.DAMAGE_INDICATOR, attacker.getX(), attacker.getY(), attacker.getZ(), 3, 0.1, 0.0, 0.1, 0.2);
		int control = indicators(recorded);

		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.bare(victim);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		attacker.attack(victim);
		float lost = before - victim.getHealth();
		int hearts = indicators(recorded);
		RpgTestSupport.result("noDamageIndicator", "control=" + control + " healthLost=" + lost + " hearts=" + hearts);
		helper.assertTrue(control == 1, "the recorder saw " + control + " control particle packets, so it proves nothing");
		helper.assertTrue(lost > 2.0F, "the hit lost only " + lost + " health, so vanilla would send hearts");
		helper.assertTrue(hearts == 0, "a melee hit sent " + hearts + " damage indicator packets");
		helper.succeed();
	}

	private static void drain(final RpgTestSupport.Recorded recorded) {
		while (recorded.channel().readOutbound() != null) {
			// Discard the join packets.
		}
	}

	private static int indicators(final RpgTestSupport.Recorded recorded) {
		int count = 0;
		Object message;
		while ((message = recorded.channel().readOutbound()) != null) {
			if (message instanceof ClientboundLevelParticlesPacket packet && packet.particle().getType() == ParticleTypes.DAMAGE_INDICATOR) {
				count++;
			}
		}
		return count;
	}
}
