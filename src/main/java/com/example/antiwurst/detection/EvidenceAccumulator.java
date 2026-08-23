package com.example.antiwurst.detection;

import java.util.HashMap;
import java.util.Map;

/** Accumulates repeated independent signals while allowing old evidence to decay. */
public final class EvidenceAccumulator {
    private final Map<String, Integer> streaks = new HashMap<>();
    private final double decayPerTick;
    private double score;
    private long lastTick = Long.MIN_VALUE;

    public EvidenceAccumulator(double decayPerTick) {
        if (decayPerTick < 0.0) {
            throw new IllegalArgumentException("decayPerTick cannot be negative");
        }
        this.decayPerTick = decayPerTick;
    }

    public double record(String check, double weight, int requiredStreak, long tick) {
        decayTo(tick);
        int streak = streaks.merge(check, 1, Integer::sum);
        if (streak >= Math.max(1, requiredStreak)) {
            score += Math.max(0.0, weight);
        }
        return score;
    }

    public void clearStreak(String check, long tick) {
        decayTo(tick);
        streaks.remove(check);
    }

    public double decayTo(long tick) {
        if (lastTick != Long.MIN_VALUE && tick > lastTick) {
            score = Math.max(0.0, score - ((tick - lastTick) * decayPerTick));
        }
        lastTick = Math.max(lastTick, tick);
        return score;
    }

    public double score() {
        return score;
    }

    public int streak(String check) {
        return streaks.getOrDefault(check, 0);
    }
}
