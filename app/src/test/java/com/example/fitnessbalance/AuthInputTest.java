package com.example.fitnessbalance;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class AuthInputTest {
    @Test public void rejectsNonFiniteAndOutOfRangeWeights() {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0,-1,301}) assertFalse(AuthInput.validWeight(value));
        assertTrue(AuthInput.validWeight(70.5)); assertTrue(AuthInput.validWeight(300));
    }
    @Test public void incompleteLegacyProfilesRequireSetup() {
        Map<String,Object> data=new HashMap<>();
        assertFalse(AuthInput.completeProfile(data));
        data.put("fullName","Demo User");data.put("weight",70);data.put("fitnessLevel","Beginner");
        assertTrue(AuthInput.completeProfile(data));
        data.put("fitnessLevel","Expert");assertFalse(AuthInput.completeProfile(data));
        data.put("fitnessLevel","Advanced");data.put("weight",Double.NaN);assertFalse(AuthInput.completeProfile(data));
    }
    @Test public void namesAndLevelsMatchProfileContract() {
        assertFalse(AuthInput.validName(null));assertFalse(AuthInput.validName("  "));
        assertFalse(AuthInput.validName(new String(new char[201]).replace('\0','a')));
        assertTrue(AuthInput.validName("Demo"));assertTrue(AuthInput.validLevel("Intermediate"));
        assertFalse(AuthInput.validLevel("Expert"));
    }
}
