package com.example.antiwurst.server;

import com.example.antiwurst.AntiWurstMod;
import com.example.antiwurst.config.AntiWurstConfig;
import com.example.antiwurst.network.AntiWurstChannels;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiCheatService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<ServerLoginNetworkHandler, Long> LOGIN_NONCES = new ConcurrentHashMap<>();
    private static final Map<UUID, HeartbeatSession> HEARTBEATS = new ConcurrentHashMap<>();
    private static AntiWurstConfig config;
    private static ServerBehaviorDetector behaviorDetector;
    private static boolean initialized;

    private AntiCheatService() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        config = AntiWurstConfig.load();
        behaviorDetector = new ServerBehaviorDetector(config);

        registerLoginHandshake();
        registerHeartbeat();
        behaviorDetector.register();
        initialized = true;
        AntiWurstMod.LOGGER.info("AntiWurst server protection initialized (require-client-mod={})",
                config.requireClientMod());
    }

    private static void registerLoginHandshake() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            if (!config.requireClientMod()) {
                return;
            }
            long nonce = RANDOM.nextLong();
            LOGIN_NONCES.put(handler, nonce);
            PacketByteBuf request = PacketByteBufs.create();
            request.writeVarInt(AntiWurstChannels.PROTOCOL_VERSION);
            request.writeLong(nonce);
            request.writeString(modVersion(), 64);
            sender.sendPacket(AntiWurstChannels.LOGIN_HANDSHAKE, request);
        });

        ServerLoginNetworking.registerGlobalReceiver(AntiWurstChannels.LOGIN_HANDSHAKE,
                (server, handler, understood, response, synchronizer, sender) -> {
                    Long expectedNonce = LOGIN_NONCES.remove(handler);
                    if (!config.requireClientMod()) {
                        return;
                    }
                    if (!understood || expectedNonce == null) {
                        rejectLogin(handler, "AntiWurst is required on your client (version 2.0.0 or compatible).");
                        return;
                    }
                    try {
                        int protocol = response.readVarInt();
                        long nonce = response.readLong();
                        String clientVersion = response.readString(64);
                        boolean clean = response.readBoolean();
                        String scanSummary = sanitize(response.readString(160));

                        if (protocol != AntiWurstChannels.PROTOCOL_VERSION) {
                            rejectLogin(handler, "Incompatible AntiWurst protocol. Server="
                                    + AntiWurstChannels.PROTOCOL_VERSION + ", client=" + protocol);
                        } else if (nonce != expectedNonce) {
                            rejectLogin(handler, "AntiWurst login challenge validation failed.");
                        } else if (!clean) {
                            AntiWurstMod.LOGGER.warn("Rejected a client reporting a threat: {}", scanSummary);
                            rejectLogin(handler, "AntiWurst detected a forbidden client modification: " + scanSummary);
                        } else {
                            AntiWurstMod.LOGGER.info("Accepted AntiWurst client version {}", clientVersion);
                        }
                    } catch (RuntimeException exception) {
                        AntiWurstMod.LOGGER.warn("Rejected malformed AntiWurst login response", exception);
                        rejectLogin(handler, "Malformed AntiWurst login response.");
                    }
                });

        ServerLoginConnectionEvents.DISCONNECT.register((handler, server) -> LOGIN_NONCES.remove(handler));
    }

    private static void registerHeartbeat() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            int tick = server.getTicks();
            HEARTBEATS.put(handler.player.getUuid(), new HeartbeatSession(tick + config.heartbeatIntervalTicks()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HEARTBEATS.remove(handler.player.getUuid());
            behaviorDetector.remove(handler.player.getUuid());
        });

        ServerPlayNetworking.registerGlobalReceiver(AntiWurstChannels.HEARTBEAT_RESPONSE,
                (server, player, handler, response, sender) -> {
                    long token;
                    int protocol;
                    try {
                        token = response.readLong();
                        protocol = response.readVarInt();
                    } catch (RuntimeException exception) {
                        server.execute(() -> disconnect(player, "Malformed AntiWurst heartbeat response."));
                        return;
                    }
                    server.execute(() -> acceptHeartbeat(player, token, protocol, server.getTicks()));
                });

        ServerTickEvents.END_SERVER_TICK.register(server -> tickHeartbeats(server.getTicks(),
                server.getPlayerManager().getPlayerList()));
    }

    private static void tickHeartbeats(int tick, Iterable<ServerPlayerEntity> players) {
        if (!config.requireClientMod()) {
            return;
        }
        for (ServerPlayerEntity player : players) {
            HeartbeatSession session = HEARTBEATS.computeIfAbsent(player.getUuid(),
                    ignored -> new HeartbeatSession(tick + config.heartbeatIntervalTicks()));
            if (session.pendingToken != null && tick > session.deadlineTick) {
                disconnect(player, "AntiWurst heartbeat timed out. Please reinstall the matching client mod.");
                continue;
            }
            if (session.pendingToken == null && tick >= session.nextChallengeTick) {
                if (!ServerPlayNetworking.canSend(player, AntiWurstChannels.HEARTBEAT_CHALLENGE)) {
                    disconnect(player, "AntiWurst client heartbeat channel is unavailable.");
                    continue;
                }
                long token = RANDOM.nextLong();
                session.pendingToken = token;
                session.deadlineTick = tick + config.heartbeatTimeoutTicks();
                PacketByteBuf challenge = PacketByteBufs.create();
                challenge.writeLong(token);
                challenge.writeVarInt(AntiWurstChannels.PROTOCOL_VERSION);
                ServerPlayNetworking.send(player, AntiWurstChannels.HEARTBEAT_CHALLENGE, challenge);
            }
        }
    }

    private static void acceptHeartbeat(ServerPlayerEntity player, long token, int protocol, int tick) {
        HeartbeatSession session = HEARTBEATS.get(player.getUuid());
        if (session == null || session.pendingToken == null || session.pendingToken != token
                || protocol != AntiWurstChannels.PROTOCOL_VERSION) {
            disconnect(player, "Invalid AntiWurst heartbeat response.");
            return;
        }
        session.pendingToken = null;
        session.nextChallengeTick = tick + config.heartbeatIntervalTicks();
    }

    private static void rejectLogin(ServerLoginNetworkHandler handler, String reason) {
        handler.disconnect(Text.literal(reason));
    }

    static void disconnect(ServerPlayerEntity player, String reason) {
        if (player.networkHandler.isConnectionOpen()) {
            player.networkHandler.disconnect(Text.literal(reason));
        }
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(AntiWurstMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), 160));
        for (int index = 0; index < value.length() && result.length() < 160; index++) {
            char character = value.charAt(index);
            if (!Character.isISOControl(character) && character != '\u00a7') {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static final class HeartbeatSession {
        private int nextChallengeTick;
        private int deadlineTick;
        private Long pendingToken;

        private HeartbeatSession(int nextChallengeTick) {
            this.nextChallengeTick = nextChallengeTick;
        }
    }
}
