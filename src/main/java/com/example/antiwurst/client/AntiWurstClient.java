package com.example.antiwurst.client;

import com.example.antiwurst.AntiWurstMod;
import com.example.antiwurst.network.AntiWurstChannels;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;

import java.util.concurrent.CompletableFuture;

public final class AntiWurstClient implements ClientModInitializer {
    private ClientThreatScanner.ScanResult scanResult;

    @Override
    public void onInitializeClient() {
        scanResult = new ClientThreatScanner().scan();
        if (scanResult.clean()) {
            AntiWurstMod.LOGGER.info("Client integrity scan completed without known Wurst signatures");
        } else {
            AntiWurstMod.LOGGER.error("Client integrity scan found {}", scanResult.summary());
        }

        ClientLoginNetworking.registerGlobalReceiver(AntiWurstChannels.LOGIN_HANDSHAKE,
                (client, handler, request, listenerAdder) -> createHandshakeResponse(request));
        ClientPlayNetworking.registerGlobalReceiver(AntiWurstChannels.HEARTBEAT_CHALLENGE,
                (client, handler, request, sender) -> respondToHeartbeat(request, sender));
    }

    private CompletableFuture<PacketByteBuf> createHandshakeResponse(PacketByteBuf request) {
        try {
            int serverProtocol = request.readVarInt();
            long nonce = request.readLong();
            request.readString(64); // server version, reserved for diagnostics

            PacketByteBuf response = PacketByteBufs.create();
            response.writeVarInt(AntiWurstChannels.PROTOCOL_VERSION);
            response.writeLong(nonce);
            response.writeString(modVersion(), 64);
            response.writeBoolean(scanResult.clean());
            response.writeString(scanResult.summary(), 160);

            if (serverProtocol != AntiWurstChannels.PROTOCOL_VERSION) {
                AntiWurstMod.LOGGER.warn("Server uses incompatible AntiWurst protocol {}", serverProtocol);
            }
            return CompletableFuture.completedFuture(response);
        } catch (RuntimeException exception) {
            AntiWurstMod.LOGGER.warn("Rejected malformed AntiWurst login challenge", exception);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void respondToHeartbeat(PacketByteBuf request, net.fabricmc.fabric.api.networking.v1.PacketSender sender) {
        try {
            long token = request.readLong();
            int protocol = request.readVarInt();
            if (protocol != AntiWurstChannels.PROTOCOL_VERSION || !scanResult.clean()) {
                return;
            }
            PacketByteBuf response = PacketByteBufs.create();
            response.writeLong(token);
            response.writeVarInt(AntiWurstChannels.PROTOCOL_VERSION);
            sender.sendPacket(AntiWurstChannels.HEARTBEAT_RESPONSE, response);
        } catch (RuntimeException exception) {
            AntiWurstMod.LOGGER.warn("Ignored malformed AntiWurst heartbeat", exception);
        }
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(AntiWurstMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
