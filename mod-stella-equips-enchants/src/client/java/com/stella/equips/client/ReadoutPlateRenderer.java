package com.stella.equips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stella.equips.readout.ReadoutPayload;
import com.stella.equips.readout.ReadoutPlateLayout;
import com.stella.equips.readout.ReadoutText;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws the plate in world space above the looked-at mob, turned to face the camera. */
final class ReadoutPlateRenderer {
	/** World size of one text pixel; the vanilla text display uses 0.025. */
	private static final float PIXEL_SCALE = 0.02F;
	/** Beyond this many blocks the plate grows with distance, so it stays readable. */
	private static final double FULL_SIZE_RANGE = 10.0;
	private static final double HEIGHT_OFFSET = 0.3;
	private static final int OPAQUE = 0xFF000000;
	private static final int HALF_LINE = 4;
	private static final Font.DisplayMode MODE = Font.DisplayMode.POLYGON_OFFSET;

	private ReadoutPlateRenderer() {
	}

	static void submit(final LevelRenderContext context) {
		ReadoutPayload payload = ReadoutPlateState.current();
		Minecraft minecraft = Minecraft.getInstance();

		if (payload == null || minecraft.level == null) {
			return;
		}

		Entity mob = minecraft.level.getEntity(payload.entityId());

		if (mob == null || !mob.isAlive()) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 anchor = new Vec3(
			Mth.lerp(partial, mob.xOld, mob.getX()),
			Mth.lerp(partial, mob.yOld, mob.getY()) + mob.getBbHeight() + HEIGHT_OFFSET,
			Mth.lerp(partial, mob.zOld, mob.getZ())
		);
		Vec3 relative = anchor.subtract(camera.pos);
		float scale = PIXEL_SCALE * (float) Math.max(1.0, relative.length() / FULL_SIZE_RANGE);
		draw(context.poseStack(), context.submitNodeCollector(), camera, minecraft.font, payload, relative, scale);
	}

	private static void draw(
		final PoseStack poseStack,
		final SubmitNodeCollector collector,
		final CameraRenderState camera,
		final Font font,
		final ReadoutPayload payload,
		final Vec3 relative,
		final float scale
	) {
		String label = ReadoutText.label(payload.rankValue(), payload.name());
		// A negative threat means no mobs mod: draw no number.
		String number = payload.threat() < 0 ? "" : Integer.toString(payload.threat());
		ReadoutPlateLayout.Plan plan = ReadoutPlateLayout.plan(
			payload.health(), payload.maxHealth(), payload.absorption(), font.width(label)
		);
		ReadoutPlateLayout.Rect bounds = plan.bounds();

		poseStack.pushPose();
		poseStack.translate(relative.x, relative.y, relative.z);
		poseStack.rotate(camera.orientation);
		Matrix4f pose = poseStack.last().pose();
		pose.rotate((float) Math.PI, 0.0F, 1.0F, 0.0F);
		pose.scale(-scale, -scale, -scale);
		pose.translate(-(bounds.x0() + bounds.x1()) / 2.0F, -bounds.y1(), 0.0F);

		for (ReadoutPlateLayout.Rect rect : plan.all()) {
			collector.submitTextBackground(
				poseStack, rect.x0(), rect.y0(), rect.x1(), rect.y1(), rect.color(), MODE, LightCoordsUtil.FULL_BRIGHT
			);
		}

		collector.submitText(
			poseStack, plan.numberCenterX() - font.width(number) / 2.0F, plan.numberCenterY() - HALF_LINE,
			Component.literal(number).getVisualOrderText(), false, MODE, LightCoordsUtil.FULL_BRIGHT,
			OPAQUE | ReadoutText.rgb(ReadoutText.color(payload.bandValue())), 0, 0
		);
		collector.submitText(
			poseStack, plan.nameX(), plan.nameY(), Component.literal(label).getVisualOrderText(), false, MODE,
			LightCoordsUtil.FULL_BRIGHT, OPAQUE | ReadoutText.rgb(ReadoutText.nameColor(payload.hostile())), 0, 0
		);
		poseStack.popPose();
	}
}
