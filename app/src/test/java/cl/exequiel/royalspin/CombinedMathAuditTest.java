package cl.exequiel.royalspin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CombinedMathAuditTest {
    @Test
    public void combinedLifecycleRemainsInsideSafeSampleBand() {
        CombinedMathAudit.Report report = CombinedMathAudit.simulate(250_000L, 20260724L);
        System.out.println("ROYAL_SPIN_MATH_AUDIT " + report.summary());
        assertTrue(report.triggerCount > 100L);
        assertTrue(report.averageFeatureLength() >= 30d);
        assertTrue(report.maximumFeatureLength <= FeatureRules.MAXIMUM_FEATURE_SPINS);
        // This fast Java sample validates invariants; the 10M vectorized audit calibrates RTP.
        assertTrue(report.baseRtp() > 0.84d && report.baseRtp() < 1.02d);
        assertTrue(report.featureRtp() > 0.005d && report.featureRtp() < 0.065d);
        assertTrue(report.combinedRtp() > 0.88d && report.combinedRtp() < 1.06d);
    }

    @Test
    public void auditIsDeterministic() {
        CombinedMathAudit.Report first = CombinedMathAudit.simulate(20_000L, 77L);
        CombinedMathAudit.Report second = CombinedMathAudit.simulate(20_000L, 77L);
        assertEquals(first.basePayout, second.basePayout);
        assertEquals(first.featurePayout, second.featurePayout);
        assertEquals(first.triggerCount, second.triggerCount);
        assertEquals(first.featureSpinCount, second.featureSpinCount);
    }
}
