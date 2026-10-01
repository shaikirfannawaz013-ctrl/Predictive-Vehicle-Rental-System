package com.fleetiq.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static BigDecimal rupees(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    public static BigDecimal times(BigDecimal value, double factor) {
        return rupees(value.multiply(BigDecimal.valueOf(factor)));
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
