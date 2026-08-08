package pl.omarcino.vanillawaypoints.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

import pl.omarcino.vanillawaypoints.VanillaWaypoints;
import pl.omarcino.vanillawaypoints.client.gui.WaypointScreen;
import pl.omarcino.vanillawaypoints.network.WaypointPayloads;

public final class VanillaWaypointsClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			VanillaWaypoints.id("waypoints")
	);

	private static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.vanilla-waypoints.open_menu",
			InputConstants.Type.KEYSYM,
			InputConstants.KEY_U,
			CATEGORY
	));

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(WaypointPayloads.Snapshot.TYPE, (payload, context) ->
				WaypointClientState.replace(payload.entries())
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_MENU.consumeClick()) {
				if (WaypointScreen.closeIfOpen()) {
					continue;
				}
				if (client.player != null && ClientPlayNetworking.canSend(WaypointPayloads.Request.TYPE)) {
					client.setScreenAndShow(new WaypointScreen());
					ClientPlayNetworking.send(WaypointPayloads.Request.INSTANCE);
				}
			}
		});
	}

	public static boolean matchesOpenMenu(KeyEvent event) {
		return OPEN_MENU.matches(event);
	}

	public static void clearPendingOpenMenuClicks() {
		while (OPEN_MENU.consumeClick()) {
			// The screen handled this key press directly; do not reopen it on the next client tick.
		}
	}
}
