package com.example.fitnessbalance;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProgressNumbersTest {
    @Test public void negativeNetEnergyKeepsItsSign(){assertEquals("-200",ProgressNumbers.format(-200,Locale.US));}
    @Test public void weightDecreaseDoesNotBecomeUnavailable(){assertEquals("-1.5",ProgressNumbers.format(-1.5,Locale.US));}
    @Test public void knownZeroAndMissingRemainDifferent(){assertEquals("0",ProgressNumbers.format(0,Locale.US));assertNull(ProgressNumbers.format(null,Locale.US));}
    @Test public void nonFiniteValuesRemainUnavailable(){assertNull(ProgressNumbers.format(Double.NaN,Locale.US));assertNull(ProgressNumbers.format(Double.POSITIVE_INFINITY,Locale.US));}
    @Test public void formattingUsesTheChosenLocale(){assertEquals("-1,5",ProgressNumbers.format(-1.5,Locale.GERMANY));}
}
