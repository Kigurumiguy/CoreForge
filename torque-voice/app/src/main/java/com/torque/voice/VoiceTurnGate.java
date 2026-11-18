package com.torque.voice;

import java.util.Locale;

/**
 * Pure, deterministic voice-turn policy used by both the visible Activity and
 * the screen-off microphone service. No sensor, cloud or device assumptions.
 *
 * This does NOT perform acoustic echo cancellation. It reduces self-transcript
 * and duplicate-result loops; speech spoken over a loud speaker is inherently
 * difficult to separate from the owner without acoustic testing.
 */
final class VoiceTurnGate {
    enum Decision { IGNORE, PROCESS, STOP }

    private static final long DUPLICATE_WINDOW_MS = 1900L;
    private static final long AFTER_TTS_COOLDOWN_MS = 850L;
    private static final long ECHO_WINDOW_MS = 5000L;
    private boolean talking = false;
    private boolean thinking = false;
    private long lastAcceptedAt = Long.MIN_VALUE;
    private String lastAccepted = "";
    private long lastTtsEndedAt = Long.MIN_VALUE;
    private long ttsStartedAt = Long.MIN_VALUE;
    private String spokenReply = "";
    private int ignoredDuplicates = 0;
    private int ignoredEchoes = 0;

    static String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.US)
            .replaceAll("[^a-z0-9 ]", " ")
            .trim().replaceAll(" +", " ");
    }

    static boolean isStop(String input) {
        String s = normalize(input);
        return s.equals("torque enough") || s.equals("enough torque")
            || s.equals("stop torque") || s.equals("torque stop")
            || s.equals("mute torque") || s.equals("torque mute")
            || s.equals("torque stop listening") || s.equals("torque shut up")
            || s.equals("torque stop talking")
            || s.equals("torque be quiet")
            || s.equals("torque please stop");
    }

    /**
     * Accept a short partial just for immediate stop; longer speech is only
     * processed on final recognition results, once per actual turn.
     */
    Decision evaluate(String incoming, boolean partial, long nowElapsedMs) {
        String normalized = normalize(incoming);
        if (normalized.isEmpty()) return Decision.IGNORE;
        if (isStop(normalized)) {
            // A generated response should never be a voice command, but guard
            // against Torque's own speaker saying the exact stop phrase.
            if (isRecentSelfEcho(normalized, nowElapsedMs)) {
                ignoredEchoes++;
                return Decision.IGNORE;
            }
            return Decision.STOP;
        }
        // Check the acoustic self-transcript first, including while TTS is
        // still speaking, so the duplicate is measured and ignored.
        if (isRecentSelfEcho(normalized, nowElapsedMs)) {
            ignoredEchoes++;
            return Decision.IGNORE;
        }
        if (partial || talking || thinking) return Decision.IGNORE;
        if (lastTtsEndedAt != Long.MIN_VALUE &&
            nowElapsedMs >= lastTtsEndedAt &&
            nowElapsedMs - lastTtsEndedAt < AFTER_TTS_COOLDOWN_MS) {
            return Decision.IGNORE;
        }
        if (normalized.equals(lastAccepted) &&
            lastAcceptedAt != Long.MIN_VALUE &&
            nowElapsedMs >= lastAcceptedAt &&
            nowElapsedMs - lastAcceptedAt <= DUPLICATE_WINDOW_MS) {
            ignoredDuplicates++;
            return Decision.IGNORE;
        }
        lastAccepted = normalized;
        lastAcceptedAt = nowElapsedMs;
        return Decision.PROCESS;
    }

    private boolean isRecentSelfEcho(String normalized, long now) {
        if (spokenReply.isEmpty()) return false;
        long base = talking ? ttsStartedAt : lastTtsEndedAt;
        if (base == Long.MIN_VALUE || now < base || now-base > ECHO_WINDOW_MS) return false;
        // Do not reject unrelated human utterances merely because a word repeats.
        if (normalized.length() < 12) return false;
        return spokenReply.equals(normalized) ||
            (normalized.length() >= 18 && spokenReply.contains(normalized));
    }

    void thinkingStarted() { thinking = true; }
    void thinkingEnded() { thinking = false; }

    void speechStarted(String text, long now) {
        spokenReply = normalize(text);
        ttsStartedAt = now;
        lastTtsEndedAt = Long.MIN_VALUE;
        talking = true;
        thinking = false;
    }
    void speechFinished(long now) {
        talking = false;
        thinking = false;
        lastTtsEndedAt = now;
    }
    void stop() {
        talking = false;
        thinking = false;
        spokenReply = "";
        ttsStartedAt = Long.MIN_VALUE;
        lastTtsEndedAt = Long.MIN_VALUE;
        lastAccepted = "";
        lastAcceptedAt = Long.MIN_VALUE;
    }

    boolean isTalking() { return talking; }
    int ignoredDuplicates() { return ignoredDuplicates; }
    int ignoredEchoes() { return ignoredEchoes; }
}
