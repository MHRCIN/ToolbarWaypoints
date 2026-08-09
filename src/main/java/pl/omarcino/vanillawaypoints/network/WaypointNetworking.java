package pl.omarcino.vanillawaypoints.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import pl.omarcino.vanillawaypoints.waypoint.CustomWaypoint;
import pl.omarcino.vanillawaypoints.waypoint.WaypointData;
import pl.omarcino.vanillawaypoints.waypoint.WaypointKind;
import pl.omarcino.vanillawaypoints.waypoint.WaypointPreferences;
import pl.omarcino.vanillawaypoints.waypoint.WaypointSync;

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
		WaypointPreferences preferences = WaypointPreferences.get(player.level().getServer());
		UUID playerId = player.getUUID();
		List<WaypointPayloads.Entry> entries = data.all().stream()
				.filter(waypoint -> waypoint.ownerId().equals(playerId) || waypoint.shared())
				.sorted(Comparator.comparingLong(CustomWaypoint::createdAt).reversed())
				.limit(WaypointPayloads.MAX_SNAPSHOT_SIZE)
				.map(waypoint -> new WaypointPayloads.Entry(
						waypoint.id(),
						waypoint.name(),
						waypoint.dimension().identifier().toString(),
						waypoint.position(),
						waypoint.kind() == WaypointKind.DEATH ? 0xFFFFFF : waypoint.color(),
						preferences.enabledFor(playerId, waypoint),
						waypoint.shared(),
						preferences.renderInWorldFor(playerId, waypoint),
						waypoint.kind() == WaypointKind.DEATH,
						waypoint.ownerId().equals(playerId)
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
				true,
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
		WaypointPreferences preferences = WaypointPreferences.get(player.level().getServer());
		Optional<CustomWaypoint> existing = data.findById(payload.waypointId());
		if (existing.isEmpty()
				|| !existing.get().ownerId().equals(player.getUUID()) && !existing.get().shared()) {
			sendSnapshot(player);
			return;
		}
		CustomWaypoint waypoint = existing.get();
		boolean owned = waypoint.ownerId().equals(player.getUUID());
		UUID ownerId = waypoint.ownerId();

		boolean changed = switch (payload.action()) {
			case WaypointPayloads.Action.TOGGLE_VISIBILITY -> owned
					? data.updateOwnedById(
							ownerId, payload.waypointId(), current -> current.withEnabled(!current.enabled())
					).isPresent()
					: preferences.toggleVisibility(player.getUUID(), waypoint);
			case WaypointPayloads.Action.DELETE -> {
				boolean removed = owned && data.removeOwnedById(ownerId, payload.waypointId());
				if (removed) {
					preferences.removeWaypoint(payload.waypointId());
				}
				yield removed;
			}
			case WaypointPayloads.Action.SET_COLOR -> waypoint.kind() == WaypointKind.CUSTOM
					&& payload.value() >= 0
					&& payload.value() <= 0xFFFFFF
					&& data.updateOwnedById(
							ownerId, payload.waypointId(), current -> current.withColor(payload.value())
					).isPresent();
			case WaypointPayloads.Action.TOGGLE_WORLD_RENDERING -> owned
					? data.updateOwnedById(
							ownerId, payload.waypointId(), current -> current.withRenderInWorld(!current.renderInWorld())
					).isPresent()
					: preferences.toggleWorldRendering(player.getUUID(), waypoint);
			default -> false;
		};

		if (changed) {
			if (payload.action() == WaypointPayloads.Action.TOGGLE_VISIBILITY
					|| payload.action() == WaypointPayloads.Action.TOGGLE_WORLD_RENDERING) {
				WaypointSync.refreshPlayer(player);
			} else {
				WaypointSync.refreshAll(player.level().getServer());
			}
		} else {
			sendSnapshot(player);
		}
	}
}
