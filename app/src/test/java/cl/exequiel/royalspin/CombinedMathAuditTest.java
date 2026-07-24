package cl.exequiel.royalspin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CombinedMathAuditTest {
    @Test
    public void combinedLifecycleRemainsInsideCalibratedBand() {
        CombinedMathAudit.Report report = CombinedMathAudit.simulate(250_000L, 20260724L);
        System.out.println("ROYAL_SPIN_MATH_AUDIT " + report.summary());
        assertTrue(report.triggerCount > 100L);
        assertTrue(report.averageFeatureLength() >= 30d);
        assertTrue(report.maximumFeatureLength <= FeatureRules.MAXIMUM_FEATURE_SPINS);
        assertTrue(report.baseRtp() > 0.89d && report.baseRtp() < 0.96d);
        assertTrue(report.featureRtp() > 0.01d && report.featureRtp() < 0.05d);
        assertTrue(report.combinedRtp() > 0.935d && report.combinedRtp() < 0.975d);
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
