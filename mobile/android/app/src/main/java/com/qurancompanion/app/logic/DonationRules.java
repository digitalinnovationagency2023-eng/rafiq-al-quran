package com.qurancompanion.app.logic;

import java.util.Arrays;
import java.util.List;

/** Client-side donation validation. Collection itself is decided by the server. */
public final class DonationRules {
    private DonationRules() {}

    public static final int MIN_MINOR = 500;
    public static final int MAX_MINOR = 5_000_000;
    public static final List<String> CURRENCIES = Arrays.asList("AED", "USD", "EUR", "GBP");

    /** Returns null when valid, otherwise a user-facing message. */
    public static String validate(int amountMinor, String currency) {
        if (amountMinor < MIN_MINOR) return "Amount must be at least " + MIN_MINOR;
        if (amountMinor > MAX_MINOR) return "Amount must be at most " + MAX_MINOR;
        if (currency == null || !CURRENCIES.contains(currency.toUpperCase())) return "Unsupported currency";
        return null;
    }

    public static String disabledNotice() {
        return "Donation collection is not enabled in this deployment. No payment is requested.";
    }

    public static String statement() {
        return "Donations are voluntary and never required to read, search or discuss Quranic verses. "
                + "No religious reward is promised and no charitable or tax-deductible status is claimed.";
    }
}
