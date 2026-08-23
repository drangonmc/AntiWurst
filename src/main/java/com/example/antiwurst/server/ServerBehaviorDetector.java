package com.example.antiwurst.server;

import com.example.antiwurst.AntiWurstMod;
import com.example.antiwurst.config.AntiWurstConfig;
import com.example.antiwurst.detection.EvidenceAccumulator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ServerBehaviorDetector {
    private static final double TELEPORT_DISTANCE_SQUARED = 256.0;
    private final AntiWurstConfig config;
    private final Map<UUID, PlayerState> states = new HashMap<>();

    ServerBehaviorDetector(AntiWurstConfig config) {
        this.config = config;
    }

    void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                sampleMovement(player, tick);
            }
        });
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || world.isClient()) {
                return ActionResult.PASS;
            }
            return inspectAttack(serverPlayer, target, serverPlayer.getServer().getTicks());
        });
    }

    void remove(UUID playerId) {
        states.remove(playerId);
    }

    private void sampleMovement(ServerPlayerEntity player, int tick) {
        PlayerState state = state(player);
        Vec3d position = player.getPos();
        if (state.lastPosition == null) {
            state.resetPosition(position, tick);
            return;
        }

        Vec3d delta = position.subtract(state.lastPosition);
        state.lastPosition = position;
        state.evidence.decayTo(tick);
        if (delta.lengthSquared() > TELEPORT_DISTANCE_SQUARED || movementExempt(player)) {
            state.hoverTicks = 0;
            state.evidence.clearStreak("speed", tick);
            state.evidence.clearStreak("vertical", tick);
            return;
        }

        inspectHorizontalSpeed(player, state, delta, tick);
        inspectVerticalMovement(player, state, delta, tick);
    }

    private void inspectHorizontalSpeed(ServerPlayerEntity player, PlayerState state, Vec3d delta, int tick) {
        double horizontal = Math.sqrt((delta.x * delta.x) + (delta.z * delta.z));
        double movementAttribute = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        double threshold = 0.82 + Math.max(0.0, movementAttribute - 0.1) * 5.0;
        BlockState below = player.getWorld().getBlockState(player.getBlockPos().down());
        boolean slipperySurface = below.getBlock().getSlipperiness() > 0.65F;

        if (!slipperySurface && horizontal > threshold) {
            flag(player, state, "speed", 1.2, 3, tick,
                    String.format("horizontal=%.3f threshold=%.3f", horizontal, threshold));
        } else {
            state.evidence.clearStreak("speed", tick);
        }
    }

    private void inspectVerticalMovement(ServerPlayerEntity player, PlayerState state, Vec3d delta, int tick) {
        boolean supportBelow = !player.getWorld().isSpaceEmpty(player,
                player.getBoundingBox().offset(0.0, -0.16, 0.0));
        double verticalVelocity = player.getVelocity().y;

        if (!player.isOnGround() && !supportBelow && Math.abs(delta.y) < 0.025
                && Math.abs(verticalVelocity) < 0.08) {
            state.hoverTicks++;
            if (state.hoverTicks > 25 && state.hoverTicks % 5 == 0) {
                flag(player, state, "flight-hover", 1.5, 1, tick,
                        "airborne without expected vertical motion for " + state.hoverTicks + " ticks");
            }
        } else {
            state.hoverTicks = 0;
            state.evidence.clearStreak("flight-hover", tick);
        }

        if (delta.y > 1.1 && player.getVelocity().lengthSquared() < 2.25) {
            flag(player, state, "vertical", 1.8, 2, tick,
                    String.format("vertical delta=%.3f", delta.y));
        } else {
            state.evidence.clearStreak("vertical", tick);
        }
    }

    private ActionResult inspectAttack(ServerPlayerEntity player, Entity target, int tick) {
        if (player.isCreative() || player.isSpectator() || target == player) {
            return ActionResult.PASS;
        }
        PlayerState state = state(player);
        boolean cancel = false;

        double reach = distanceToBox(player.getEyePos(), target.getBoundingBox().expand(0.1));
        if (reach > config.maximumAttackReach()) {
            flag(player, state, "reach", 2.5, 2, tick,
                    String.format("reach=%.3f maximum=%.3f", reach, config.maximumAttackReach()));
            cancel |= state.evidence.streak("reach") >= 2;
        } else {
            state.evidence.clearStreak("reach", tick);
        }

        Vec3d targetDirection = target.getBoundingBox().getCenter().subtract(player.getEyePos());
        if (targetDirection.lengthSquared() > 0.0001) {
            double facing = player.getRotationVec(1.0F).dotProduct(targetDirection.normalize());
            if (facing < -0.2) {
                flag(player, state, "backtrack", 2.0, 2, tick,
                        String.format("facing dot=%.3f", facing));
                cancel |= state.evidence.streak("backtrack") >= 2;
            } else {
                state.evidence.clearStreak("backtrack", tick);
            }
        }

        if (!player.canSee(target)) {
            flag(player, state, "wall-hit", 1.8, 3, tick, "target is not visible to the server");
            cancel |= state.evidence.streak("wall-hit") >= 3;
        } else {
            state.evidence.clearStreak("wall-hit", tick);
        }

        state.attackTicks.addLast(tick);
        while (!state.attackTicks.isEmpty() && state.attackTicks.peekFirst() <= tick - 20) {
            state.attackTicks.removeFirst();
        }
        if (state.attackTicks.size() > config.maximumAttacksPerSecond()) {
            flag(player, state, "attack-rate", 0.8, 2, tick,
                    "attacks/second=" + state.attackTicks.size());
            cancel |= state.attackTicks.size() > config.maximumAttacksPerSecond() + 5;
        } else {
            state.evidence.clearStreak("attack-rate", tick);
        }

        return cancel && config.cancelSuspiciousAttacks() ? ActionResult.FAIL : ActionResult.PASS;
    }

    private boolean movementExempt(ServerPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        return player.isCreative() || player.isSpectator() || player.getAbilities().allowFlying
                || player.getAbilities().flying || player.hasVehicle() || player.isFallFlying()
                || player.isTouchingWater() || player.isClimbing()
                || player.hasStatusEffect(StatusEffects.LEVITATION)
                || player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || velocity.lengthSquared() > 2.25;
    }

    private void flag(ServerPlayerEntity player, PlayerState state, String check, double weight,
                      int requiredStreak, int tick, String details) {
        double score = state.evidence.record(check, weight, requiredStreak, tick);
        if (config.logViolations()) {
            AntiWurstMod.LOGGER.warn("{} failed {}: {} (score={})", player.getGameProfile().getName(),
                    check, details, String.format("%.2f", score));
        }
        if (score >= config.violationKickScore()) {
            AntiCheatService.disconnect(player, "AntiWurst detected abnormal behavior (" + check + ").");
        }
    }

    private PlayerState state(ServerPlayerEntity player) {
        return states.computeIfAbsent(player.getUuid(),
                ignored -> new PlayerState(config.violationDecayPerTick()));
    }

    private static double distanceToBox(Vec3d point, Box box) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.x));
        double y = Math.max(box.minY, Math.min(box.maxY, point.y));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.z));
        return point.distanceTo(new Vec3d(x, y, z));
    }

    private static final class PlayerState {
        private final EvidenceAccumulator evidence;
        private final Deque<Integer> attackTicks = new ArrayDeque<>();
        private Vec3d lastPosition;
        private int hoverTicks;

        private PlayerState(double decayPerTick) {
            evidence = new EvidenceAccumulator(decayPerTick);
        }

        private void resetPosition(Vec3d position, int tick) {
            lastPosition = position;
            hoverTicks = 0;
            evidence.decayTo(tick);
        }
    }
}
