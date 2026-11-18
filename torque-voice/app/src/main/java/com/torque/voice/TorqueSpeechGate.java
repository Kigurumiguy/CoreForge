package com.torque.voice;

import java.util.Locale;

/**
 * Conservative local echo/duplicate suppression for the conversational mic.
 *
 * Vosk may emit the same phrase in both onResult and onFinalResult. The car
 * speakers may also be transcribed by the mic. While Torque is speaking or
 * awaiting AI generation, only an explicit STOP phrase is accepted.
 *
 * This is NOT acoustic echo cancellation. Confirm with physical-phone tests.
 */
final class TorqueSpeechGate {
    enum Decision { HANDLE, STOP, IGNORE }
    private String lastOutput = "";
    private String lastHandled = "";
    private long lastHandledAt = -100000L;
    private long outputAt = -100000L;
    private long outputEndedAt = -100000L;

    void startedOutput(String spoken, long elapsedMs) {
        lastOutput = normalize(spoken);
        outputAt = elapsedMs;
        outputEndedAt = -100000L;
    }

    void endedOutput(long elapsedMs) {
        outputEndedAt = elapsedMs;
    }

    Decision inspect(String heard, boolean isSpeaking, boolean isGenerating, long elapsedMs) {
        String phrase = normalize(heard);
        if (phrase.isEmpty()) return Decision.IGNORE;

        // Do not interpret the app's own TTS as a stop command.
        boolean soundsLikeOutput = isEcho(phrase, elapsedMs);
        if (TorqueMode.stopNow(heard)) {
            return soundsLikeOutput ? Decision.IGNORE : Decision.STOP;
        }

        // Don't start a second answer while talking or generating another.
        if (isSpeaking || isGenerating) return Decision.IGNORE;

        // Avoid replaying the same Vosk onResult/onFinalResult twice.
        if (phrase.equals(lastHandled) && elapsedMs >= lastHandledAt &&
            elapsedMs - lastHandledAt < 1700) return Decision.IGNORE;

        if (soundsLikeOutput) return Decision.IGNORE;

        lastHandled = phrase;
        lastHandledAt = elapsedMs;
        return Decision.HANDLE;
    }

    private boolean isEcho(String phrase, long elapsedMs) {
        if (lastOutput.length() < 7 || phrase.length() < 7) return false;
        long lastTime = outputEndedAt >= 0 ? outputEndedAt : outputAt;
        if (elapsedMs < lastTime || elapsedMs - lastTime > 1800) return false;

        // A recognized segment of Torque's recent spoken line is likely echo.
        // Keep threshold conservative; long full phrases also match exactly.
        return (lastOutput.contains(phrase) && phrase.length() >= 9) ||
            (phrase.contains(lastOutput) && lastOutput.length() >= 12);
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N} ]", " ")
            .trim().replaceAll(" +", " ");
    }
}
