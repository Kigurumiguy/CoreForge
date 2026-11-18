# TORQUE Voice v1.0 — offline road-trip beta

A native Android app designed for an owner-invoked, voice-led companion in a 2010 Toyota RAV4 and at home. This is **not** a navigation app, crash avoidance system, driver alertness monitor, or warranty of staying connected forever.

## Reality of v1.0

- Offline speech-to-text: the APK bundles Vosk small English and Vosk speaker-model assets. Models expand into private storage on first startup.
- Offline text-to-speech: selects ONLY installed English voices reporting `isNetworkConnectionRequired() == false`. If none exist, spoken conversation will not start until an offline TTS voice is installed. Some phones may still have offline voice engine bugs; perform airplane-mode test.
- Optional local generative brain: import an appropriately small, lawfully downloaded GGUF through the system file picker. Native llama.cpp inference is linked. **No GGUF weights are bundled**. If no model is loaded, scripted fallback replies are used—this is not generative AI.
- Persistent facts, guest roster, private mode confirmation, age and local clock, and foreground-only GPS saved-place arrival logic are included.
- Screen-off conversation: opt in while app is visible, start a foreground microphone service with a notification and STOP action. Limited microphone retry and a bounded CPU wake lock. If Android kills the app, Torque might stop; it does NOT auto-activate its microphone at boot.
- Work context and passenger privacy: explicitly select safe modes. Owner voice identification is approximate, not authentication.
- Android AudioRecord microphone recording with available platform echo cancellation/noise suppression; cannot guarantee the AUX output won't leak back into the microphone.

## Road-trip readiness (parked-car test)

1. Install APK and grant mic permission. Open **MICHIGAN TRIP • OFFLINE READINESS**.
2. Choose a downloaded offline English TTS voice on the phone. A voice listed in the picker as offline-capable is **not** proof until an airplane-mode test passes.
3. Import a compatible small GGUF if you want spontaneous, non-scripted conversation. Check it reports **LOCAL GGUF READY** after loading; also check performance and heat.
4. In a *parked* car with the usual AUX and power setup, use Android airplane mode. Start TALK and exchange several turns. Test both spoken STOP and touch STOP.
5. End the session. Start **SCREEN-OFF TORQUE** explicitly, check the microphone notification, lock the phone and try several turns again. Verify STOP from the notification.
6. Repeat while stationary with the car's speaker volume raised to realistic levels. Look at **MIC / ECHO CALIBRATION** to see whether AEC and noise suppression actually enabled; vary settings and check transcription quality.
7. Check power, battery temperature, phone audio interruptions, session restarts, and memory consumption. Test over several hours before depending on it for an extended journey.
8. The phone's own GPS can work without cellular connectivity when there is satellite reception; offline navigation maps are a **separate** setup task. In v1.0, screen-off mode has no GPS/arrival processing.
9. Torque can keep you company, but if you are drowsy while driving, safely pull over and rest. Conversation cannot reliably prevent falling asleep.

## Acceptance checks and honest limitations

| Check | Status | Evidence |
|---|---|---|
| Android debug APK compilation | CI-verifiable | GitHub Actions `assembleDebug` |
| Pure Java voice-turn gate tests | CI-verifiable | GitHub Actions `testDebugUnitTest` |
| Drowsiness phrase handling tests | CI-verifiable | GitHub Actions `DriveSafetyTest` |
| Bundled offline ASR files | CI-verifiable | Workflow downloads and validates upstream Vosk zip assets |
| Offline TTS selection logic | Implemented, device test needed | `OfflineVoicePolicy.java`; reports non-network English voice |
| Airplane-mode STT, TTS and LLM | NOT DEVICE-TESTED | Requires real Android target and imported GGUF |
| Screen-off and partial wake-lock endurance | NOT DEVICE-TESTED | Requires real phone, battery settings and long-duration test |
| AUX speaker echo cancellation effectiveness | NOT DEVICE-TESTED | Android effects status cannot prove effectiveness |
| Owner speaker verification accuracy | NOT DEVICE-TESTED | Requires multiple actual voices, noise and error measurements |
| Automatic background GPS arrival | NOT IMPLEMENTED | Foreground-only location by design |
| Continuous true off-grid generative dialogue | CONDITIONAL | Requires compatible local GGUF, successful load and performance |
| Wake-word without tapping the app | NOT IMPLEMENTED | Consent-based button to start the session |
| Full offline turn-by-turn navigation | NOT IMPLEMENTED | Use separate downloaded offline navigation maps |

## Source and build

From the repository's `torque-voice` folder, run `gradle :app:testDebugUnitTest :app:assembleDebug` using Java 17, Android SDK platform 36, Build Tools 36.0.0 and Gradle 8.13. Or use the checked-in GitHub Actions build workflow, which also bundles Vosk model archives.

Vosk model URLs are in `.github/workflows/torque-voice-apk.yml`. Their upstream license must be reviewed prior to redistribution. The native GGUF inference library is `io.github.ljcamargo:llamacpp-kotlin:0.4.0`, installed from Maven. Confirm the license of any user-imported GGUF weights separately. TTS voice files are supplied by the device, not by this APK.

A GitHub Actions debug build proves it compiles; it does not mean the app has passed a physical-device test. Check the current release asset SHA-256 in the GitHub release API after publishing.
