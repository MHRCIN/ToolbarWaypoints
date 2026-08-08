package pl.omarcino.vanillawaypoints.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import pl.omarcino.vanillawaypoints.waypoint.CustomWaypoint;
import pl.omarcino.vanillawaypoints.waypoint.WaypointData;
import pl.omarcino.vanillawaypoints.waypoint.WaypointKind;
import pl.omarcino.vanillawaypoints.waypoint.WaypointSync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WaypointNetworking {
	private static final int MAX_WAYPOINTS_PER_PLAYER = 128;
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

	private WaypointNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(WaypointPayloads.Snapshot.TYPE, WaypointPayloads.Snapshot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WaypointPayloads.Request.TYPE, WaypointPayloads.Request.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WaypointPayloads.Add.TYPE, WaypointPayloads.Add.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WaypointPayloads.Action.TYPE, WaypointPayloads.Action.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(WaypointPayloads.Request.TYPE, (payload, context) -> sendSnapshot(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(WaypointPayloads.Add.TYPE, (payload, context) -> handleAdd(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WaypointPayloads.Action.TYPE, (payload, context) -> handleAction(context.player(), payload));
	}

	public static void sendSnapshot(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, WaypointPayloads.Snapshot.TYPE)) {
			return;
		}

		WaypointData data = WaypointData.get(player.level().getServer());
		List<CustomWaypoint> owned = new ArrayList<>(data.ownedBy(player.getUUID()));
		owned.addAll(data.deathWaypoints(player.getUUID()));
		owned.sort(Comparator.comparingLong(CustomWaypoint::createdAt).reversed());

		List<WaypointPayloads.Entry> entries = owned.stream()
				.map(waypoint -> new WaypointPayloads.Entry(
						waypoint.id(),
						waypoint.name(),
						waypoint.dimension().identifier().toString(),
						waypoint.position(),
						waypoint.kind() == WaypointKind.DEATH ? 0xFFFFFF : waypoint.color(),
						waypoint.enabled(),
						waypoint.shared(),
						waypoint.kind() == WaypointKind.DEATH
				))
				.toList();

		ServerPlayNetworking.send(player, new WaypointPayloads.Snapshot(entries));
	}

	private static void handleAdd(ServerPlayer player, WaypointPayloads.Add payload) {
		if (!VALID_NAME.matcher(payload.name()).matches() || payload.color() < 0 || payload.color() > 0xFFFFFF) {
			sendSnapshot(player);
			return;
		}

		WaypointData data = WaypointData.get(player.level().getServer());
		if (data.ownedBy(player.getUUID()).size() >= MAX_WAYPOINTS_PER_PLAYER) {
			sendSnapshot(player);
			return;
		}

		BlockPos position = payload.customPosition() ? payload.position().immutable() : player.blockPosition().immutable();
		CustomWaypoint waypoint = new CustomWaypoint(
				UUID.randomUUID(),
				player.getUUID(),
				payload.name(),
				player.level().dimension(),
				position,
				payload.color(),
				true,
				false,
				System.currentTimeMillis(),
				WaypointKind.CUSTOM
		);

		if (data.add(waypoint)) {
			WaypointSync.refreshAll(player.level().getServer());
		} else {
			sendSnapshot(player);
		}
	}

	private static void handleAction(ServerPlayer player, WaypointPayloads.Action payload) {
		WaypointData data = WaypointData.get(player.level().getServer());
		Optional<CustomWaypoint> existing = data.findOwnedById(player.getUUID(), payload.waypointId());
		if (existing.isEmpty()) {
			sendSnapshot(player);
			return;
		}

		boolean changed = switch (payload.action()) {
			case WaypointPayloads.Action.TOGGLE_VISIBILITY -> data.updateOwnedById(
					player.getUUID(), payload.waypointId(), waypoint -> waypoint.withEnabled(!waypoint.enabled())
			).isPresent();
			case WaypointPayloads.Action.DELETE -> data.removeOwnedById(player.getUUID(), payload.waypointId());
			case WaypointPayloads.Action.SET_COLOR -> existing.get().kind() == WaypointKind.CUSTOM
					&& payload.value() >= 0
					&& payload.value() <= 0xFFFFFF
					&& data.updateOwnedById(
							player.getUUID(), payload.waypointId(), waypoint -> waypoint.withColor(payload.value())
					).isPresent();
			default -> false;
		};

		if (changed) {
			WaypointSync.refreshAll(player.level().getServer());
		} else {
			sendSnapshot(player);
		}
	}
}
