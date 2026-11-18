package com.torque.voice;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceTurnGateTest {
    @Test public void stopCommandsWorkEvenDuringSpeaking() {
        VoiceTurnGate gate=new VoiceTurnGate();
        gate.speechStarted("Try finding some crayons, you magnificent bastard.", 1000);
        assertEquals(VoiceTurnGate.Decision.STOP,gate.evaluate("Torque, enough!",true,1400));
        assertEquals(VoiceTurnGate.Decision.STOP,gate.evaluate("TORQUE STOP TALKING",false,1600));
    }

    @Test public void partialTranscriptsNeverTriggerReplies() {
        VoiceTurnGate gate=new VoiceTurnGate();
        assertEquals(VoiceTurnGate.Decision.IGNORE,gate.evaluate("hey torque",true,1000));
        assertEquals(VoiceTurnGate.Decision.PROCESS,gate.evaluate("hey torque",false,1200));
    }

    @Test public void duplicateRecognitionResultsIgnored() {
        VoiceTurnGate gate=new VoiceTurnGate();
        assertEquals(VoiceTurnGate.Decision.PROCESS,gate.evaluate("Good morning",false,1000));
        assertEquals(VoiceTurnGate.Decision.IGNORE,gate.evaluate("good morning.",false,2000));
        assertEquals(1,gate.ignoredDuplicates());
        assertEquals(VoiceTurnGate.Decision.PROCESS,gate.evaluate("good morning",false,3500));
    }

    @Test public void ownSpokenReplyRejected() {
        VoiceTurnGate gate=new VoiceTurnGate();
        gate.speechStarted("This car is a magnificent moving dumpster",1000);
        assertEquals(VoiceTurnGate.Decision.IGNORE,gate.evaluate("This car is a magnificent moving dumpster",false,1300));
        gate.speechFinished(3000);
        assertEquals(VoiceTurnGate.Decision.IGNORE,gate.evaluate("this car is a magnificent moving dumpster",false,4000));
        assertEquals(2,gate.ignoredEchoes());
    }

    @Test public void afterSpeechCooldownAndRecovery() {
        VoiceTurnGate gate=new VoiceTurnGate();
        gate.speechStarted("Whatever",1000);
        gate.speechFinished(1500);
        assertEquals(VoiceTurnGate.Decision.IGNORE,gate.evaluate("can you hear me",false,1700));
        assertEquals(VoiceTurnGate.Decision.PROCESS,gate.evaluate("can you hear me",false,2400));
    }

    @Test public void llmThinkingBlocksBackgroundTranscripts() {
        VoiceTurnGate gate=new VoiceTurnGate();
        gate.thinkingStarted();
        assertEquals(VoiceTurnGate.Decision.IGNORE,gate.evaluate("go to walmart",false,1000));
        assertEquals(VoiceTurnGate.Decision.STOP,gate.evaluate("torque enough",true,1200));
        gate.thinkingEnded();
        assertEquals(VoiceTurnGate.Decision.PROCESS,gate.evaluate("go to walmart",false,2200));
    }

    @Test public void stopDoesNotGetSuppressedAsSelfEchoIfTorqueDidNotSayIt() {
        VoiceTurnGate gate=new VoiceTurnGate();
        gate.speechStarted("Where are we going today?",1000);
        assertEquals(VoiceTurnGate.Decision.STOP,gate.evaluate("torque enough",true,1200));
    }

    @Test public void stopClearsDebounceAndState() {
        VoiceTurnGate gate=new VoiceTurnGate();
        assertEquals(VoiceTurnGate.Decision.PROCESS,gate.evaluate("hello",false,1000));
        gate.stop();
        assertEquals(VoiceTurnGate.Decision.PROCESS,gate.evaluate("hello",false,1100));
    }

    @Test public void containsStopWordsInLongSpeechIsNotStop() {
        VoiceTurnGate gate=new VoiceTurnGate();
        assertEquals(VoiceTurnGate.Decision.PROCESS,
            gate.evaluate("My friend said torque enough but I disagree",false,1500));
    }

    @Test public void normalizesCommonPunctuation() {
        assertEquals("torque enough",VoiceTurnGate.normalize("Torque, ENOUGH!!!"));
        assertTrue(VoiceTurnGate.isStop("Torque—stop talking"));
    }
}
