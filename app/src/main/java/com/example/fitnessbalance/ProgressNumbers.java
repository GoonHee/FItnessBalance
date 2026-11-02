package com.example.fitnessbalance;
import java.text.NumberFormat;
import java.util.Locale;
final class ProgressNumbers {
    private ProgressNumbers(){}
    static String format(Object value,Locale locale){if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue()))return null;NumberFormat f=NumberFormat.getNumberInstance(locale);f.setMaximumFractionDigits(1);return f.format(((Number)value).doubleValue());}
}
