package cl.exequiel.royalspin;

import java.util.Random;

/** Pure deterministic audit of paid rounds including all nested free spins and retriggers. */
public final class CombinedMathAudit {
    private CombinedMathAudit() { }

    public static Report simulate(long paidRounds, long seed) {
        if (paidRounds <= 0) throw new IllegalArgumentException("paidRounds must be positive");
        StakeSlotEngine engine = new StakeSlotEngine();
        Random random = new Random(seed);
        long totalStake = 0L;
        long basePayout = 0L;
        long featurePayout = 0L;
        long triggerCount = 0L;
        long featureSpinCount = 0L;
        long retriggerCount = 0L;
        long maximumFeatureLength = 0L;

        for (long round = 0; round < paidRounds; round++) {
            StakeSlotEngine.SpinResult base = engine.spin(random, 1, GameMode.BASE_GAME);
            totalStake += base.totalBet;
            basePayout += base.totalPayout;
            if (!base.featureTrigger.isInitialFeature()) continue;

            triggerCount++;
            int spinsRemaining = FeatureRules.INITIAL_FREE_SPINS;
            int totalAwarded = spinsRemaining;
            int sessionLength = 0;
            while (spinsRemaining > 0) {
                spinsRemaining--;
                sessionLength++;
                featureSpinCount++;
                StakeSlotEngine.SpinResult free = engine.spin(random, 1, GameMode.FREE_SPINS);
                featurePayout += free.totalPayout;
                if (free.featureTrigger.isRetrigger()
                        && totalAwarded < FeatureRules.MAXIMUM_FEATURE_SPINS) {
                    int add = Math.min(FeatureRules.RETRIGGER_FREE_SPINS,
                            FeatureRules.MAXIMUM_FEATURE_SPINS - totalAwarded);
                    if (add > 0) {
                        totalAwarded += add;
                        spinsRemaining += add;
                        retriggerCount++;
                    }
                }
            }
            maximumFeatureLength = Math.max(maximumFeatureLength, sessionLength);
        }

        return new Report(paidRounds, totalStake, basePayout, featurePayout,
                triggerCount, featureSpinCount, retriggerCount, maximumFeatureLength);
    }

    public static final class Report {
        public final long paidRounds;
        public final long totalStake;
        public final long basePayout;
        public final long featurePayout;
        public final long triggerCount;
        public final long featureSpinCount;
        public final long retriggerCount;
        public final long maximumFeatureLength;

        Report(long paidRounds, long totalStake, long basePayout, long featurePayout,
               long triggerCount, long featureSpinCount, long retriggerCount,
               long maximumFeatureLength) {
            this.paidRounds = paidRounds;
            this.totalStake = totalStake;
            this.basePayout = basePayout;
            this.featurePayout = featurePayout;
            this.triggerCount = triggerCount;
            this.featureSpinCount = featureSpinCount;
            this.retriggerCount = retriggerCount;
            this.maximumFeatureLength = maximumFeatureLength;
        }

        public double baseRtp() { return basePayout / (double) totalStake; }
        public double featureRtp() { return featurePayout / (double) totalStake; }
        public double combinedRtp() { return (basePayout + featurePayout) / (double) totalStake; }
        public double triggerFrequency() { return triggerCount / (double) paidRounds; }
        public double averageFeatureLength() {
            return triggerCount == 0 ? 0d : featureSpinCount / (double) triggerCount;
        }
        public String summary() {
            return String.format(java.util.Locale.US,
                    "rounds=%d baseRtp=%.8f featureRtp=%.8f combinedRtp=%.8f "
                            + "trigger=%.8f avgFeature=%.4f retriggers=%d maxFeature=%d",
                    paidRounds, baseRtp(), featureRtp(), combinedRtp(), triggerFrequency(),
                    averageFeatureLength(), retriggerCount, maximumFeatureLength);
        }
    }
}
