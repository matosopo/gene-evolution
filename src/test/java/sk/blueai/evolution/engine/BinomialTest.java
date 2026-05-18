package sk.blueai.evolution.engine;

import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinomialTest {

    @Test
    void zeroProbabilityReturnsZero() {
        assertEquals(0L, Binomial.sample(1000, 0.0, new Random(1)));
    }

    @Test
    void unitProbabilityReturnsAll() {
        assertEquals(1000L, Binomial.sample(1000, 1.0, new Random(1)));
    }

    @Test
    void zeroTrialsReturnsZero() {
        assertEquals(0L, Binomial.sample(0, 0.5, new Random(1)));
    }

    @Test
    void smallNExactPathStaysWithinBounds() {
        Random rng = new Random(7);
        long total = 0;
        int draws = 10_000;
        long n = 10;
        double p = 0.3;
        for (int i = 0; i < draws; i++) {
            long k = Binomial.sample(n, p, rng);
            assertTrue(k >= 0 && k <= n, "k=" + k);
            total += k;
        }
        double meanObserved = total / (double) draws;
        double meanExpected = n * p;
        assertTrue(Math.abs(meanObserved - meanExpected) / meanExpected < 0.05,
                "expected ~" + meanExpected + " but got " + meanObserved);
    }

    @Test
    void largeNApproxMatchesMean() {
        Random rng = new Random(42);
        int draws = 5_000;
        long n = 10_000;
        double p = 0.1;
        long total = 0;
        for (int i = 0; i < draws; i++) {
            long k = Binomial.sample(n, p, rng);
            assertTrue(k >= 0 && k <= n);
            total += k;
        }
        double meanObserved = total / (double) draws;
        double meanExpected = n * p;
        assertTrue(Math.abs(meanObserved - meanExpected) / meanExpected < 0.02,
                "expected ~" + meanExpected + " but got " + meanObserved);
    }

    @Test
    void rejectsNegativeN() {
        assertThrows(IllegalArgumentException.class, () -> Binomial.sample(-1, 0.5, new Random(1)));
    }

    @Test
    void rejectsOutOfRangeP() {
        assertThrows(IllegalArgumentException.class, () -> Binomial.sample(10, -0.1, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> Binomial.sample(10, 1.1, new Random(1)));
    }
}
