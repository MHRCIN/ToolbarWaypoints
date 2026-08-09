package pl.omarcino.vanillawaypoints.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import pl.omarcino.vanillawaypoints.VanillaWaypoints;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WaypointPayloads {
	public static final int MAX_SNAPSHOT_SIZE = 256;

	private WaypointPayloads() {
	}

	public record Entry(
			UUID id,
			String name,
			String dimension,
			BlockPos position,
			int color,
			boolean enabled,
			boolean shared,
			boolean renderInWorld,
			boolean death,
			boolean owned
	) {
		private static void encode(RegistryFriendlyByteBuf buffer, Entry entry) {
			buffer.writeUUID(entry.id);
			buffer.writeUtf(entry.name, 32);
			buffer.writeUtf(entry.dimension, 256);
			buffer.writeBlockPos(entry.position);
			buffer.writeInt(entry.color);
			buffer.writeBoolean(entry.enabled);
			buffer.writeBoolean(entry.shared);
			buffer.writeBoolean(entry.renderInWorld);
			buffer.writeBoolean(entry.death);
			buffer.writeBoolean(entry.owned);
		}

		private static Entry decode(RegistryFriendlyByteBuf buffer) {
			return new Entry(
					buffer.readUUID(),
					buffer.readUtf(32),
					buffer.readUtf(256),
					buffer.readBlockPos(),
					buffer.readInt(),
					buffer.readBoolean(),
					buffer.readBoolean(),
					buffer.readBoolean(),
					buffer.readBoolean(),
					buffer.readBoolean()
			);
		}
	}

	public record Snapshot(List<Entry> entries) implements CustomPacketPayload {
		public static final Type<Snapshot> TYPE = new Type<>(VanillaWaypoints.id("waypoint_snapshot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
				(buffer, payload) -> {
					buffer.writeVarInt(payload.entries.size());
					for (Entry entry : payload.entries) {
						Entry.encode(buffer, entry);
					}
				},
				buffer -> {
					int size = buffer.readVarInt();
					if (size < 0 || size > MAX_SNAPSHOT_SIZE) {
						throw new IllegalArgumentException("Invalid waypoint snapshot size: " + size);
					}
					List<Entry> entries = new ArrayList<>(size);
					for (int index = 0; index < size; index++) {
						entries.add(Entry.decode(buffer));
					}
					return new Snapshot(List.copyOf(entries));
				}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Request() implements CustomPacketPayload {
		public static final Request INSTANCE = new Request();
		public static final Type<Request> TYPE = new Type<>(VanillaWaypoints.id("waypoint_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Add(String name, int color, boolean customPosition, BlockPos position) implements CustomPacketPayload {
		public static final Type<Add> TYPE = new Type<>(VanillaWaypoints.id("waypoint_add"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Add> CODEC = StreamCodec.of(
				(buffer, payload) -> {
					buffer.writeUtf(payload.name, 32);
					buffer.writeInt(payload.color);
					buffer.writeBoolean(payload.customPosition);
					buffer.writeBlockPos(payload.position);
				},
				buffer -> new Add(
						buffer.readUtf(32),
						buffer.readInt(),
						buffer.readBoolean(),
						buffer.readBlockPos()
				)
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Action(UUID waypointId, int action, int value) implements CustomPacketPayload {
		public static final int TOGGLE_VISIBILITY = 0;
		public static final int DELETE = 1;
		public static final int SET_COLOR = 2;
		public static final int TOGGLE_WORLD_RENDERING = 3;

		public static final Type<Action> TYPE = new Type<>(VanillaWaypoints.id("waypoint_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
				(buffer, payload) -> {
					buffer.writeUUID(payload.waypointId);
					buffer.writeVarInt(payload.action);
					buffer.writeInt(payload.value);
				},
				buffer -> new Action(buffer.readUUID(), buffer.readVarInt(), buffer.readInt())
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
