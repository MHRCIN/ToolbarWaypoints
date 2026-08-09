package pl.omarcino.vanillawaypoints.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import pl.omarcino.vanillawaypoints.client.WaypointClientState;
import pl.omarcino.vanillawaypoints.network.WaypointPayloads;

import java.util.List;

public final class WaypointWorldRenderer {
	private WaypointWorldRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(WaypointWorldRenderer::collectWaypoints);
	}

	private static void collectWaypoints(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}

		String currentDimension = client.level.dimension().identifier().toString();
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (camera == null || camera.pos == null) {
			return;
		}

		List<WaypointPayloads.Entry> entries = WaypointClientState.entries();
		PoseStack poseStack = context.poseStack();
		for (WaypointPayloads.Entry entry : entries) {
			if (!entry.enabled() || !entry.renderInWorld() || !entry.dimension().equals(currentDimension)) {
				continue;
			}

			BlockPos position = entry.position();
			Vec3 center = Vec3.atCenterOf(position);
			double exactDistance = center.distanceTo(camera.pos);
			int distance = (int) Math.round(exactDistance);
			float labelScale = Mth.clamp((float) (exactDistance / 12.0), 1.75F, 80.0F);
			Component name = entry.death()
					? Component.translatable("gui.vanilla-waypoints.death.numbered", deathDisplayNumber(entries, entry))
					: Component.literal(entry.name());
			Component label = Component.translatable("gui.vanilla-waypoints.world_label", name, distance)
					.withColor(entry.death() ? 0xFFFFFF : entry.color());

			poseStack.pushPose();
			poseStack.translate(
					center.x - camera.pos.x,
					position.getY() + 2.0 - camera.pos.y,
					center.z - camera.pos.z
			);
			poseStack.scale(labelScale, labelScale, labelScale);
			context.submitNodeCollector().submitNameTag(
					poseStack,
					new Vec3(0.0, -0.5, 0.0),
					0,
					label,
					true,
					LightCoordsUtil.FULL_BRIGHT,
					camera
			);
			poseStack.popPose();
		}
	}

	private static int deathDisplayNumber(List<WaypointPayloads.Entry> entries, WaypointPayloads.Entry selected) {
		int number = 0;
		for (WaypointPayloads.Entry entry : entries) {
			if (entry.death()) {
				number++;
				if (entry.id().equals(selected.id())) {
					return number;
				}
			}
		}
		return number;
	}
}
