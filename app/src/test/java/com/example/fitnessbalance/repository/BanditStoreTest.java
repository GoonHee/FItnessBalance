package com.example.fitnessbalance.repository;

import static org.junit.Assert.*;
import java.util.Collections;
import org.junit.Test;

public class BanditStoreTest {
  @Test public void defaultsToLearningWithoutOverridingAnExplicitOptOut() {
    assertTrue(BanditStore.enabled(Collections.emptyMap()));
    assertTrue(BanditStore.enabled(Collections.singletonMap("enabled", true)));
    assertFalse(BanditStore.enabled(Collections.singletonMap("enabled", false)));
  }
}
