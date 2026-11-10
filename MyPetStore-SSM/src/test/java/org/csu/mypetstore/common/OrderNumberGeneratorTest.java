package org.csu.mypetstore.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberGeneratorTest {

    private final OrderNumberGenerator generator = new OrderNumberGenerator();

    @Test
    void orderNumberUsesDocumentedPrefixAndTimestamp() {
        assertThat(generator.orderNo()).matches("^ORD\\d{14}[A-HJ-NP-Z2-9]{6}$");
    }

    @Test
    void paymentNumberUsesMockPrefix() {
        assertThat(generator.paymentNo()).matches("^MOCK\\d{14}[A-HJ-NP-Z2-9]{6}$");
    }

    @Test
    void generatesDistinctValues() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            values.add(generator.orderNo());
        }
        assertThat(values).hasSize(100);
    }
}
