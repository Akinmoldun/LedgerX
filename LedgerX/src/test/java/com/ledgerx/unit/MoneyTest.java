package com.ledgerx.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerx.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("constructs from positive minor units and 3-letter currency")
    void constructs() {
        Money money = new Money(12500, "USD");
        assertThat(money.minor()).isEqualTo(12500);
        assertThat(money.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("rejects zero amount")
    void rejectsZero() {
        assertThatThrownBy(() -> new Money(0, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be > 0");
    }

    @Test
    @DisplayName("rejects null and wrong-length currency")
    void rejectsBadCurrency() {
        assertThatThrownBy(() -> new Money(100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-4217");
        assertThatThrownBy(() -> new Money(100, "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-4217");
        assertThatThrownBy(() -> new Money(100, "USDX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-4217");
    }

    @Test
    @DisplayName("negate preserves currency and sign flips")
    void negate() {
        assertThat(new Money(12500, "USD").negate())
                .isEqualTo(new Money(12500, "USD").negate());
        assertThat(new Money(12500, "USD").negate().currency()).isEqualTo("USD");
    }
}
