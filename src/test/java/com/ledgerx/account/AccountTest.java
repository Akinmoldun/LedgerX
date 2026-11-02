package com.ledgerx.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static Account account(boolean allowOverdraft, long openingBalance) {
        Account a = new Account();
        setBalance(a, openingBalance);
        if (allowOverdraft) {
            setAllowOverdraft(a, true);
        }
        return a;
    }

    // Tests construct via backdoor setters because the entity deliberately has
    // no public setters on financial fields to preserve domain invariants; production code paths never
    // do this.
    private static void setBalance(Account a, long balanceMinor) {
        try {
            var field = Account.class.getDeclaredField("balanceMinor");
            field.setAccessible(true);
            field.setLong(a, balanceMinor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setAllowOverdraft(Account a, boolean allowOverdraft) {
        try {
            var field = Account.class.getDeclaredField("allowOverdraft");
            field.setAccessible(true);
            field.setBoolean(a, allowOverdraft);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Account untouched() {
        return account(false, 0L);
    }

    @Test
    @DisplayName("credit increases balance by the amount")
    void creditAdds() {
        Account a = account(false, 1000);
        a.credit(500);
        assertThat(a.getBalanceMinor()).isEqualTo(1500);
    }

    @Test
    @DisplayName("debit reduces balance when funds are available")
    void debitReduces() {
        Account a = account(false, 1000);
        a.debit(400);
        assertThat(a.getBalanceMinor()).isEqualTo(600);
    }

    @Test
    @DisplayName("debit beyond balance is rejected without overdraft and balance unchanged")
    void debitRejectsWhenShort() {
        Account a = account(false, 100);
        assertThatThrownBy(() -> a.debit(200)).isInstanceOf(IllegalStateException.class);
        assertThat(a.getBalanceMinor()).isEqualTo(100);
    }

    @Test
    @DisplayName("debit beyond balance succeeds when overdraft is allowed")
    void debitWithOverdraft() {
        Account a = account(true, 100);
        a.debit(200);
        assertThat(a.getBalanceMinor()).isEqualTo(-100);
    }

    @Test
    @DisplayName("intent methods reject zero and negative amounts")
    void rejectsNonPositive() {
        Account a = untouched();
        assertThatThrownBy(() -> a.credit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.credit(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.debit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.debit(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getBalanceMinor()).isZero();
    }

    @Test
    @DisplayName("financial getters expose state but there are no financial setters")
    void noFinancialSetters() {
        assertThat(Account.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equals("setBalanceMinor")
                        || m.getName().equals("setCurrency")
                        || m.getName().equals("setVersion"));
    }
}
