package pl.omarcino.vanillawaypoints.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import pl.omarcino.vanillawaypoints.VanillaWaypoints;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WaypointPreferences extends SavedData {
	private record Key(UUID playerId, UUID waypointId) {
	}

	private record Preference(
			UUID playerId,
			UUID waypointId,
			boolean enabled,
			boolean renderInWorld
	) {
		private static final Codec<Preference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("player_id").forGetter(Preference::playerId),
				UUIDUtil.CODEC.fieldOf("waypoint_id").forGetter(Preference::waypointId),
				Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Preference::enabled),
				Codec.BOOL.optionalFieldOf("render_in_world", true).forGetter(Preference::renderInWorld)
		).apply(instance, Preference::new));
	}

	private static final Codec<WaypointPreferences> CODEC = Preference.CODEC.listOf().xmap(
			WaypointPreferences::new,
			data -> List.copyOf(data.preferences.values())
	);

	private static final SavedDataType<WaypointPreferences> TYPE = new SavedDataType<>(
			VanillaWaypoints.id("waypoint_preferences"),
			WaypointPreferences::new,
			CODEC,
			null
	);

	private final Map<Key, Preference> preferences = new HashMap<>();

	public WaypointPreferences() {
	}

	private WaypointPreferences(List<Preference> loadedPreferences) {
		for (Preference preference : loadedPreferences) {
			preferences.put(new Key(preference.playerId(), preference.waypointId()), preference);
		}
	}

	public static WaypointPreferences get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean enabledFor(UUID playerId, CustomWaypoint waypoint) {
		if (waypoint.ownerId().equals(playerId)) {
			return waypoint.enabled();
		}
		Preference preference = preferences.get(new Key(playerId, waypoint.id()));
		return preference == null || preference.enabled();
	}

	public boolean renderInWorldFor(UUID playerId, CustomWaypoint waypoint) {
		if (waypoint.ownerId().equals(playerId)) {
			return waypoint.renderInWorld();
		}
		Preference preference = preferences.get(new Key(playerId, waypoint.id()));
		return preference == null || preference.renderInWorld();
	}

	public boolean toggleVisibility(UUID playerId, CustomWaypoint waypoint) {
		if (waypoint.ownerId().equals(playerId)) {
			return false;
		}
		Key key = new Key(playerId, waypoint.id());
		Preference current = preferences.getOrDefault(
				key,
				new Preference(playerId, waypoint.id(), true, true)
		);
		preferences.put(key, new Preference(
				playerId,
				waypoint.id(),
				!current.enabled(),
				current.renderInWorld()
		));
		setDirty();
		return true;
	}

	public boolean toggleWorldRendering(UUID playerId, CustomWaypoint waypoint) {
		if (waypoint.ownerId().equals(playerId)) {
			return false;
		}
		Key key = new Key(playerId, waypoint.id());
		Preference current = preferences.getOrDefault(
				key,
				new Preference(playerId, waypoint.id(), true, true)
		);
		preferences.put(key, new Preference(
				playerId,
				waypoint.id(),
				current.enabled(),
				!current.renderInWorld()
		));
		setDirty();
		return true;
	}

	public void removeWaypoint(UUID waypointId) {
		if (preferences.entrySet().removeIf(entry -> entry.getKey().waypointId().equals(waypointId))) {
			setDirty();
		}
	}
}
