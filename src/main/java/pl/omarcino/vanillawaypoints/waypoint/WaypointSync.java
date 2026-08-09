package pl.omarcino.vanillawaypoints.waypoint;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAssets;

import pl.omarcino.vanillawaypoints.VanillaWaypoints;
import pl.omarcino.vanillawaypoints.network.WaypointNetworking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class WaypointSync {
	private static final Map<UUID, Set<UUID>> SENT_WAYPOINTS = new HashMap<>();
	private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSIONS = new HashMap<>();
	private static final ResourceKey<net.minecraft.world.waypoints.WaypointStyleAsset> DEATH_STYLE = ResourceKey.create(
			WaypointStyleAssets.ROOT_ID,
			VanillaWaypoints.id("death")
	);

	private WaypointSync() {
	}

	public static void registerEvents() {
		ServerPlayerEvents.JOIN.register(WaypointSync::refreshPlayer);
		ServerPlayerEvents.LEAVE.register(player -> clearRuntimeState(player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refreshPlayer(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(WaypointSync::refreshPlayersThatChangedDimension);
	}

	public static void refreshAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshPlayer(player);
		}
	}

	public static void refreshPlayer(ServerPlayer player) {
		WaypointData data = WaypointData.get(player.level().getServer());
		WaypointPreferences preferences = WaypointPreferences.get(player.level().getServer());
		UUID playerId = player.getUUID();

		Map<UUID, CustomWaypoint> desired = data.all().stream()
				.filter(waypoint -> preferences.enabledFor(playerId, waypoint))
				.filter(waypoint -> waypoint.dimension().equals(player.level().dimension()))
				.filter(waypoint -> waypoint.ownerId().equals(playerId) || waypoint.shared())
				.collect(Collectors.toMap(CustomWaypoint::id, Function.identity()));

		Set<UUID> previouslySent = SENT_WAYPOINTS.getOrDefault(playerId, Set.of());
		for (UUID removedId : previouslySent) {
			if (!desired.containsKey(removedId)) {
				player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(removedId));
			}
		}

		for (CustomWaypoint waypoint : desired.values()) {
			Waypoint.Icon icon = createIcon(waypoint);
			if (previouslySent.contains(waypoint.id())) {
				// Vanilla's UPDATE operation changes the position, but Vec3iWaypoint.update does not copy icon data.
				// Re-tracking is therefore required for color and style changes to reach the client.
				player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypoint.id()));
			}
			player.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(
					waypoint.id(), icon, waypoint.position()
			));
		}

		SENT_WAYPOINTS.put(playerId, new HashSet<>(desired.keySet()));
		LAST_DIMENSIONS.put(playerId, player.level().dimension());
		WaypointNetworking.sendSnapshot(player);
	}

	private static Waypoint.Icon createIcon(CustomWaypoint waypoint) {
		Waypoint.Icon icon = new Waypoint.Icon();
		icon.style = waypoint.kind() == WaypointKind.DEATH ? DEATH_STYLE : WaypointStyleAssets.DEFAULT;
		icon.color = Optional.of(waypoint.kind() == WaypointKind.DEATH
				? DeathWaypointManager.DEATH_COLOR
				: waypoint.color());
		return icon;
	}

	private static void refreshPlayersThatChangedDimension(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ResourceKey<Level> currentDimension = player.level().dimension();
			if (!currentDimension.equals(LAST_DIMENSIONS.get(player.getUUID()))) {
				refreshPlayer(player);
			}
		}
	}

	private static void clearRuntimeState(UUID playerId) {
		SENT_WAYPOINTS.remove(playerId);
		LAST_DIMENSIONS.remove(playerId);
	}
}
