package pl.omarcino.vanillawaypoints.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record CustomWaypoint(
		UUID id,
		UUID ownerId,
		String name,
		ResourceKey<Level> dimension,
		BlockPos position,
		int color,
		boolean enabled,
		boolean shared,
		boolean renderInWorld,
		long createdAt,
		WaypointKind kind
) {
	public static final int DEFAULT_COLOR = 0x55FFFF;

	public static final Codec<CustomWaypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(CustomWaypoint::id),
			UUIDUtil.CODEC.fieldOf("owner_id").forGetter(CustomWaypoint::ownerId),
			Codec.STRING.fieldOf("name").forGetter(CustomWaypoint::name),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(CustomWaypoint::dimension),
			BlockPos.CODEC.fieldOf("position").forGetter(CustomWaypoint::position),
			Codec.INT.optionalFieldOf("color", DEFAULT_COLOR).forGetter(CustomWaypoint::color),
			Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CustomWaypoint::enabled),
			Codec.BOOL.optionalFieldOf("shared", false).forGetter(CustomWaypoint::shared),
			Codec.BOOL.optionalFieldOf("render_in_world", true).forGetter(CustomWaypoint::renderInWorld),
			Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(CustomWaypoint::createdAt),
			WaypointKind.CODEC.optionalFieldOf("kind", WaypointKind.CUSTOM).forGetter(CustomWaypoint::kind)
	).apply(instance, CustomWaypoint::new));

	public CustomWaypoint withColor(int newColor) {
		return new CustomWaypoint(id, ownerId, name, dimension, position, newColor, enabled, shared, renderInWorld, createdAt, kind);
	}

	public CustomWaypoint withEnabled(boolean newEnabled) {
		return new CustomWaypoint(id, ownerId, name, dimension, position, color, newEnabled, shared, renderInWorld, createdAt, kind);
	}

	public CustomWaypoint withShared(boolean newShared) {
		return new CustomWaypoint(id, ownerId, name, dimension, position, color, enabled, newShared, renderInWorld, createdAt, kind);
	}

	public CustomWaypoint withRenderInWorld(boolean newRenderInWorld) {
		return new CustomWaypoint(id, ownerId, name, dimension, position, color, enabled, shared, newRenderInWorld, createdAt, kind);
	}
}
