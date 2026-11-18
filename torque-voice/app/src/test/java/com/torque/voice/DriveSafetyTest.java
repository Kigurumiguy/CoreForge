package com.torque.voice;

import org.junit.Test;
import static org.junit.Assert.*;

public class DriveSafetyTest {
    @Test public void recognizesHighRiskDrowsiness() {
        assertTrue(DriveSafety.urgent("Torque, I'm falling asleep."));
        assertTrue(DriveSafety.urgent("I can't stay awake"));
        assertTrue(DriveSafety.urgent("I'm nodding off!"));
        assertTrue(DriveSafety.urgent("My eyes keep closing"));
        assertTrue(DriveSafety.response("I can't stay awake").contains("pull over"));
    }
    @Test public void distinguishesNormalConversation() {
        assertFalse(DriveSafety.urgent("I'm tired of this lousy traffic."));
        assertFalse(DriveSafety.urgent("What time is it?"));
        assertFalse(DriveSafety.tired("Waffle House is exhausting."));
        assertNull(DriveSafety.response("We're driving to Michigan"));
    }
    @Test public void respondsProportionatelyToSleepiness() {
        assertTrue(DriveSafety.tired("I am getting sleepy"));
        assertTrue(DriveSafety.response("I'm getting sleepy").contains("stop and rest"));
    }
}
