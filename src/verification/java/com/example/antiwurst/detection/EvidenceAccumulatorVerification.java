package com.example.antiwurst.detection;

public final class EvidenceAccumulatorVerification {
    private EvidenceAccumulatorVerification() {
    }

    public static void main(String[] args) {
        requiresAStreakBeforeAddingEvidence();
        cleanSampleBreaksTheStreak();
        evidenceDecaysWithoutBecomingNegative();
        rejectsNegativeDecay();
    }

    private static void requiresAStreakBeforeAddingEvidence() {
        EvidenceAccumulator accumulator = new EvidenceAccumulator(0.0);
        equal(0.0, accumulator.record("speed", 2.0, 3, 10));
        equal(0.0, accumulator.record("speed", 2.0, 3, 11));
        equal(2.0, accumulator.streak("speed"));
        equal(2.0, accumulator.record("speed", 2.0, 3, 12));
    }

    private static void cleanSampleBreaksTheStreak() {
        EvidenceAccumulator accumulator = new EvidenceAccumulator(0.0);
        accumulator.record("reach", 2.0, 2, 1);
        accumulator.clearStreak("reach", 2);
        equal(0.0, accumulator.record("reach", 2.0, 2, 3));
        equal(2.0, accumulator.record("reach", 2.0, 2, 4));
    }

    private static void evidenceDecaysWithoutBecomingNegative() {
        EvidenceAccumulator accumulator = new EvidenceAccumulator(0.25);
        accumulator.record("flight", 2.0, 1, 5);
        equal(1.0, accumulator.decayTo(9));
        equal(0.0, accumulator.decayTo(100));
    }

    private static void rejectsNegativeDecay() {
        try {
            new EvidenceAccumulator(-0.1);
            throw new AssertionError("Negative decay should have been rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void equal(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
