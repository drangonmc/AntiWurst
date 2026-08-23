package com.example.antiwurst.network;

import com.example.antiwurst.AntiWurstMod;
import net.minecraft.util.Identifier;

public final class AntiWurstChannels {
    public static final int PROTOCOL_VERSION = 2;
    public static final Identifier LOGIN_HANDSHAKE = id("login_handshake");
    public static final Identifier HEARTBEAT_CHALLENGE = id("heartbeat_challenge");
    public static final Identifier HEARTBEAT_RESPONSE = id("heartbeat_response");

    private AntiWurstChannels() {
    }

    private static Identifier id(String path) {
        return new Identifier(AntiWurstMod.MOD_ID, path);
    }
}
