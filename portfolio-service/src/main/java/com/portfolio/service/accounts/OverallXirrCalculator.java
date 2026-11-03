package com.portfolio.service.accounts;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Newton-Raphson XIRR on dated cashflows. Returns null when inputs are insufficient.
 */
final class OverallXirrCalculator {

    private OverallXirrCalculator() {}

    record Cashflow(LocalDate date, double amount) {}

    static Double annualizedPct(List<Cashflow> cashflows) {
        if (cashflows == null || cashflows.size() < 2) {
            return null;
        }
        List<Cashflow> sorted = new ArrayList<>(cashflows);
        sorted.sort(Comparator.comparing(Cashflow::date));
        LocalDate base = sorted.get(0).date();
        boolean hasOut = false;
        boolean hasIn = false;
        for (Cashflow cf : sorted) {
            if (cf.amount() < 0) {
                hasOut = true;
            }
            if (cf.amount() > 0) {
                hasIn = true;
            }
        }
        if (!hasOut || !hasIn) {
            return null;
        }
        if (ChronoUnit.DAYS.between(base, sorted.get(sorted.size() - 1).date()) < 1) {
            return null;
        }

        double rate = 0.1;
        for (int i = 0; i < 64; i++) {
            double npv = 0;
            double dNpv = 0;
            for (Cashflow cf : sorted) {
                double years = ChronoUnit.DAYS.between(base, cf.date()) / 365.0;
                double denom = Math.pow(1.0 + rate, years);
                if (denom == 0 || Double.isNaN(denom) || Double.isInfinite(denom)) {
                    return null;
                }
                npv += cf.amount() / denom;
                if (years != 0) {
                    dNpv -= years * cf.amount() / (denom * (1.0 + rate));
                }
            }
            if (Math.abs(dNpv) < 1e-12) {
                break;
            }
            double next = rate - npv / dNpv;
            if (Double.isNaN(next) || Double.isInfinite(next) || next <= -0.9999) {
                return null;
            }
            if (Math.abs(next - rate) < 1e-8) {
                rate = next;
                break;
            }
            rate = next;
        }
        if (Double.isNaN(rate) || Double.isInfinite(rate) || rate <= -0.9999 || Math.abs(rate) > 100) {
            return null;
        }
        return rate * 100.0;
    }

    static LocalDate toDate(LocalDateTime t) {
        return t == null ? null : t.toLocalDate();
    }
}
