package pl.omarcino.vanillawaypoints.waypoint;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class DeathWaypointManager {
	public static final int DEATH_COLOR = 0xFFFFFF;
	private static final long HISTORY_WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();
	private static final int MAXIMUM_HISTORY_SIZE = 5;
	private static final int ARRIVAL_MANHATTAN_DISTANCE = 4;
	private static final long MINIMUM_LIFETIME_MILLIS = 5_000L;

	private DeathWaypointManager() {
	}

	public static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayer player) {
					recordDeath(player);
				}
		});
		ServerTickEvents.END_SERVER_TICK.register(DeathWaypointManager::removeReachedWaypoints);
	}

	public static int clearAllDeathWaypoints(ServerPlayer player) {
		int removed = WaypointData.get(player.level().getServer()).clearDeathWaypoints(player.getUUID());
		if (removed == 0) {
			return 0;
		}

		WaypointSync.refreshPlayer(player);
		player.sendSystemMessage(Component.translatable("message.vanilla-waypoints.death.cleared", removed));
		return removed;
	}

	private static void recordDeath(ServerPlayer player) {
		BlockPos position = player.blockPosition().immutable();
		CustomWaypoint waypoint = new CustomWaypoint(
				UUID.randomUUID(),
				player.getUUID(),
				"death",
				player.level().dimension(),
				position,
				DEATH_COLOR,
				true,
				false,
				System.currentTimeMillis(),
				WaypointKind.DEATH
		);

		WaypointData.get(player.level().getServer()).addDeathWaypoint(
				waypoint,
				HISTORY_WINDOW_MILLIS,
				MAXIMUM_HISTORY_SIZE
		);
		WaypointSync.refreshAll(player.level().getServer());
	}

	private static void removeReachedWaypoints(MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}

			WaypointData data = WaypointData.get(server);
			List<CustomWaypoint> reached = data.deathWaypoints(player.getUUID()).stream()
					.filter(waypoint -> waypoint.dimension().equals(player.level().dimension()))
					.filter(waypoint -> now - waypoint.createdAt() >= MINIMUM_LIFETIME_MILLIS)
					.filter(waypoint -> player.blockPosition().distManhattan(waypoint.position()) <= ARRIVAL_MANHATTAN_DISTANCE)
					.toList();
			if (!reached.isEmpty()) {
				for (CustomWaypoint waypoint : reached) {
					data.clearDeathWaypoint(player.getUUID(), waypoint.id());
				}
				WaypointSync.refreshPlayer(player);
				playReachedSound(player);
			}
		}
	}

	private static void playReachedSound(ServerPlayer player) {
		player.connection.send(new ClientboundSoundEntityPacket(
				Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP),
				SoundSource.PLAYERS,
				player,
				0.35F,
				1.25F,
				player.getRandom().nextLong()
		));
	}
}
