package pl.omarcino.vanillawaypoints.waypoint;

import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import pl.omarcino.vanillawaypoints.VanillaWaypoints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public final class WaypointData extends SavedData {
	private static final Codec<WaypointData> CODEC = CustomWaypoint.CODEC.listOf().xmap(
			WaypointData::new,
			data -> List.copyOf(data.waypoints.values())
	);

	private static final SavedDataType<WaypointData> TYPE = new SavedDataType<>(
			VanillaWaypoints.id("waypoints"),
			WaypointData::new,
			CODEC,
			null
	);

	private final Map<UUID, CustomWaypoint> waypoints = new HashMap<>();

	public WaypointData() {
	}

	private WaypointData(List<CustomWaypoint> loadedWaypoints) {
		for (CustomWaypoint waypoint : loadedWaypoints) {
			waypoints.put(waypoint.id(), waypoint);
		}
	}

	public static WaypointData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean add(CustomWaypoint waypoint) {
		if (findByName(waypoint.ownerId(), waypoint.name()).isPresent()) {
			return false;
		}

		waypoints.put(waypoint.id(), waypoint);
		setDirty();
		return true;
	}

	public Optional<CustomWaypoint> findByName(UUID ownerId, String name) {
		String normalizedName = normalizeName(name);
		return waypoints.values().stream()
				.filter(waypoint -> waypoint.ownerId().equals(ownerId))
				.filter(waypoint -> waypoint.kind() == WaypointKind.CUSTOM)
				.filter(waypoint -> normalizeName(waypoint.name()).equals(normalizedName))
				.findFirst();
	}

	public Optional<CustomWaypoint> remove(UUID ownerId, String name) {
		Optional<CustomWaypoint> waypoint = findByName(ownerId, name);
		waypoint.ifPresent(value -> {
			waypoints.remove(value.id());
			setDirty();
		});
		return waypoint;
	}

	public Optional<CustomWaypoint> update(UUID ownerId, String name, UnaryOperator<CustomWaypoint> updater) {
		Optional<CustomWaypoint> existing = findByName(ownerId, name);
		if (existing.isEmpty()) {
			return Optional.empty();
		}

		CustomWaypoint updated = updater.apply(existing.get());
		waypoints.put(updated.id(), updated);
		setDirty();
		return Optional.of(updated);
	}

	public List<CustomWaypoint> ownedBy(UUID ownerId) {
		return waypoints.values().stream()
				.filter(waypoint -> waypoint.ownerId().equals(ownerId))
				.filter(waypoint -> waypoint.kind() == WaypointKind.CUSTOM)
				.sorted(Comparator.comparing(CustomWaypoint::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public List<CustomWaypoint> deathWaypoints(UUID ownerId) {
		return waypoints.values().stream()
				.filter(waypoint -> waypoint.ownerId().equals(ownerId))
				.filter(waypoint -> waypoint.kind() == WaypointKind.DEATH)
				.sorted(Comparator.comparingLong(CustomWaypoint::createdAt).reversed())
				.toList();
	}

	public void addDeathWaypoint(CustomWaypoint waypoint, long historyWindowMillis, int maximumHistorySize) {
		long oldestKeptTimestamp = waypoint.createdAt() - historyWindowMillis;
		waypoints.values().removeIf(existing -> existing.ownerId().equals(waypoint.ownerId())
				&& existing.kind() == WaypointKind.DEATH
				&& existing.createdAt() < oldestKeptTimestamp);

		List<CustomWaypoint> retained = deathWaypoints(waypoint.ownerId());
		for (int index = maximumHistorySize - 1; index < retained.size(); index++) {
			waypoints.remove(retained.get(index).id());
		}

		waypoints.put(waypoint.id(), waypoint);
		setDirty();
	}

	public boolean clearDeathWaypoint(UUID ownerId, UUID waypointId) {
		CustomWaypoint waypoint = waypoints.get(waypointId);
		if (waypoint == null || !waypoint.ownerId().equals(ownerId) || waypoint.kind() != WaypointKind.DEATH) {
			return false;
		}

		waypoints.remove(waypointId);
		setDirty();
		return true;
	}

	public int clearDeathWaypoints(UUID ownerId) {
		List<CustomWaypoint> deaths = deathWaypoints(ownerId);
		for (CustomWaypoint waypoint : deaths) {
			waypoints.remove(waypoint.id());
		}
		if (!deaths.isEmpty()) {
			setDirty();
		}
		return deaths.size();
	}

	public Collection<CustomWaypoint> all() {
		return new ArrayList<>(waypoints.values());
	}

	public Optional<CustomWaypoint> findOwnedById(UUID ownerId, UUID waypointId) {
		CustomWaypoint waypoint = waypoints.get(waypointId);
		return waypoint != null && waypoint.ownerId().equals(ownerId)
				? Optional.of(waypoint)
				: Optional.empty();
	}

	public Optional<CustomWaypoint> findById(UUID waypointId) {
		return Optional.ofNullable(waypoints.get(waypointId));
	}

	public Optional<CustomWaypoint> updateOwnedById(
			UUID ownerId,
			UUID waypointId,
			UnaryOperator<CustomWaypoint> updater
	) {
		Optional<CustomWaypoint> existing = findOwnedById(ownerId, waypointId);
		if (existing.isEmpty()) {
			return Optional.empty();
		}

		CustomWaypoint updated = updater.apply(existing.get());
		waypoints.put(waypointId, updated);
		setDirty();
		return Optional.of(updated);
	}

	public boolean removeOwnedById(UUID ownerId, UUID waypointId) {
		if (findOwnedById(ownerId, waypointId).isEmpty()) {
			return false;
		}

		waypoints.remove(waypointId);
		setDirty();
		return true;
	}

	public List<String> namesOwnedBy(UUID ownerId) {
		return ownedBy(ownerId).stream().map(CustomWaypoint::name).toList();
	}

	private static String normalizeName(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
