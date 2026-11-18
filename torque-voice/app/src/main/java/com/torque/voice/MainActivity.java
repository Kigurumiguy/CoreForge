package com.torque.voice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.speech.tts.Voice;

public class MainActivity extends Activity implements LocationListener {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private LocationManager locationManager;
    private Location currentFix;
    private long fixAt = 0L;
    private boolean active = false, listening = false, speaking = false, foreground = false;
    private boolean passenger = false, quiet = false, ttsReady = false;
    private long nextUtterance = 0;
    private int recognitionFailures = 0;
    private int listenGeneration = 0;
    private float pitch = 0.87f;
    private MemoryDB memory;
    private ScriptedTorque torque;
    private final TorqueContext contextReply=new TorqueContext();
    private TorqueLocalBrain localBrain;
    private PlaceAwareness places;
    private TextView brainStatus;
    private boolean allowBargeIn=true;
    private boolean awaitingReply=false;
    private boolean sessionMuted=false;
    private String lastPrompt="";
    private static final int MODEL_PICKER=204;
    private static final int BACKGROUND_MIC_REQUEST=205;
    private static final int NOTIFICATION_REQUEST=206;
    private String mode=TorqueMode.GUEST;
    private SpeakerProfile speakerProfile;
    private VoskController vosk;
    private boolean enrollingVoice = false;
    private final RideContext ride = new RideContext();
    private TextView rideText;
    private SharedPreferences prefs;
    private TextView status, transcript, gpsText, voiceText, voicePrintStatus;
    private Button talkButton;
    private LinearLayout panel;
    private final int MIC_REQUEST = 200;
    private final int GPS_REQUEST = 201;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        prefs = getSharedPreferences("torque_prefs", MODE_PRIVATE);
        pitch = prefs.getFloat("pitch", 0.87f);
        passenger = prefs.getBoolean("passenger", false);
        quiet = prefs.getBoolean("quiet", false);
        mode=TorqueMode.GUEST; // Fail closed on every app launch; never silently re-enter uncensored mode.
        memory = new MemoryDB(this);
        memory.seedOwnerFacts(this);
        places=new PlaceAwareness(this);
        torque = new ScriptedTorque(memory);
        speakerProfile = new SpeakerProfile(this);
        localBrain = new TorqueLocalBrain(this,new TorqueLocalBrain.Callbacks(){
            @Override public void modelReady(String description){
                setStatus("LOCAL GGUF READY");
                updateBrainStatus();
            }
            @Override public void answer(String result,long ms,int tokens){
                awaitingReply=false;
                if(!active || !foreground || sessionMuted)return;
                String spoken=result.trim();
                if(spoken.isEmpty()){handleBrainFailure("Empty model response");return;}
                transcript.setText("LOCAL AI: "+spoken+"\n\n"+tokens+" tokens / "+ms+"ms");
                if(ttsReady)sayOnce(spoken);else beginListening();
            }
            @Override public void failed(String reason){handleBrainFailure(reason);}
        });
        vosk = new VoskController(this, new VoskController.Callbacks(){
            @Override public void onReady(){
                if(!active)setStatus("OFFLINE VOICE ENGINE READY");
                refreshVoicePrintStatus();
            }
            @Override public void onSpeech(String words, org.json.JSONObject result) {
                mainHandler.post(()->{if(active && foreground)handleSpeech(words,result);});
            }
            @Override public void onError(String explanation) {
                mainHandler.post(()->{if(!active)setStatus(explanation);else if(vosk!=null && vosk.listening()){stopConversation();setStatus(explanation);}});
            }
        });
        makeScreen();
        initTts();
        String savedGguf=prefs.getString("gguf_uri","");
        if(!savedGguf.isEmpty()) localBrain.load(savedGguf);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        setStatus("MIC OFF — TAP TALK");
        updateLocationLabel();
        vosk.prepare();
    }

    private GradientDrawable shape(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(15)); return d;
    }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density * n + .5f); }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(dp(12),dp(9),dp(12),dp(9));
        return v;
    }
    private Button button(String value, Runnable click) {
        Button v = new Button(this);
        v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(15);
        v.setAllCaps(false); v.setBackground(shape(Color.rgb(37,42,49)));
        v.setOnClickListener(x -> click.run());
        return v;
    }
    private void addButton(Button v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(53));
        p.setMargins(0,dp(5),0,dp(5));
        panel.addView(v,p);
    }
    private void makeScreen() {
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true); scroller.setBackgroundColor(Color.BLACK);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(22),dp(18),dp(22),dp(26));
        scroller.addView(panel);
        setContentView(scroller);

        TextView title = text("T O R Q U E",30,Color.rgb(255,164,48));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title);
        TextView subtitle = text("2010 RAV4 • YOUR CAR HAS AN ATTITUDE",11,Color.LTGRAY);
        panel.addView(subtitle);

        status = text("",16,Color.rgb(255,168,65));
        status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        panel.addView(status);

        gpsText = text("GPS OFF",14,Color.rgb(110,185,255));
        panel.addView(gpsText);

        transcript = text("Say something. I'll be right here.",16,Color.WHITE);
        transcript.setMinHeight(dp(95));
        panel.addView(transcript);

        talkButton = button("🎙  TALK TO TORQUE", () -> {
            if (active) stopConversation();
            else startConversation();
        });
        talkButton.setTextSize(19);
        talkButton.setBackground(shape(Color.rgb(136,58,12)));
        addButton(talkButton);

        addButton(button("■  STOP TALKING / MUTE MIC", this::stopConversation));
        addButton(button("SCREEN-OFF TORQUE • START",this::startScreenOffTorque));
        addButton(button("SCREEN-OFF TORQUE • STOP",this::stopScreenOffTorque));
        panel.addView(text("Screen-off session: explicit Start, persistent microphone notification, Stop anytime. Phone GPS/arrival awareness remains foreground-only.",11,Color.LTGRAY));
        addButton(button("🎤  CHOOSE TORQUE'S VOICE", this::chooseVoice));

        rideText = text(ride.summary(),12,Color.rgb(110,185,255));
        panel.addView(rideText);
        addButton(button("PASSENGERS / WHO'S TALKING", this::showRideControls));
        addButton(button("ENROLL / ERASE OWNER VOICE", this::showVoiceEnrollment));
        voicePrintStatus = text("Preparing offline voice identification…",12,Color.rgb(200,188,155));
        panel.addView(voicePrintStatus);

        voiceText = text("Voice: loading phone voices…",12,Color.LTGRAY);
        panel.addView(voiceText);

        addButton(button("VOICE PITCH  − / +", () -> {
            String[] opts = {"Low and gravelly (0.76)", "Deep (0.87)", "Natural (1.0)", "Brighter (1.12)"};
            float[] v = {.76f,.87f,1.0f,1.12f};
            new AlertDialog.Builder(this).setTitle("Torque's pitch")
                .setItems(opts,(d,i)->{pitch=v[i];prefs.edit().putFloat("pitch",pitch).apply();
                   if(tts != null)tts.setPitch(pitch);refreshVoiceText();sayOnce("All right, wiseass. How's this voice?");})
                .show();
        }));
        panel.addView(text("HANDS-FREE MODE • TAP TALK ONCE, THEN JUST SPEAK",12,Color.rgb(255,164,48)));

        addButton(button("MODE: " + (passenger?"PASSENGER":quiet?"QUIET":"CLASSIC TORQUE"), () -> {
            String[] modes = {"Classic Torque", "Passenger", "Quiet"};
            new AlertDialog.Builder(this).setTitle("Personality mode").setItems(modes,(d,i)->{
                passenger = i==1; quiet=i==2;
                prefs.edit().putBoolean("passenger",passenger).putBoolean("quiet",quiet).apply();
                rebuildButtons();
            }).show();
        }));

        addButton(button("SPEAKING CONTEXT: "+mode,this::chooseMode));
        brainStatus=text("Local LLM: no GGUF loaded",12,Color.rgb(110,185,255));
        panel.addView(brainStatus);
        addButton(button("IMPORT GGUF MODEL",this::pickModel));
        addButton(button("PIN / MANAGE SAVED PLACES",this::managePlaces));
        addButton(button("GPS: TURN ON / REFRESH",this::startGps));
        addButton(button("MEMORIES / DELETE",this::showMemories));
        addButton(button("SHARE SAVED KNOWLEDGE",this::shareFacts));

        TextView disclosure=text("Hands-free listening stays on between replies until you press Stop or leave the app. The screen stays awake while listening; Android may stop recognition if its speech service fails. Mic and GPS stop when you leave. No hidden mic, uploads, or ads. Requires installed offline speech recognition. Dialogue is lightweight and scripted.",11,Color.GRAY);
        panel.addView(disclosure);
    }

    private void rebuildButtons() {
        makeScreen();
        refreshVoiceText();
        refreshRideText();
        refreshVoicePrintStatus();
        updateBrainStatus();
        updateLocationLabel();
        refreshTalkButton();
        setStatus(active ? (speaking ? "TORQUE SPEAKING" : "MIC ON — LISTENING") : "MIC OFF — TAP TALK");
    }

    private void initTts() {
        tts = new TextToSpeech(this, code -> {
            ttsReady = code == TextToSpeech.SUCCESS;
            if (!ttsReady) { mainHandler.post(()->{setStatus("PHONE TTS UNAVAILABLE");refreshVoiceText();});return; }
            tts.setLanguage(Locale.US);
            tts.setPitch(pitch);
            tts.setSpeechRate(.97f);
            tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){mainHandler.post(()->{speaking=true;setStatus("TORQUE SPEAKING");});}
                @Override public void onDone(String id){mainHandler.post(()->{
                    speaking=false;
                    if(active && foreground)mainHandler.postDelayed(()->beginListening(),650);
                });}
                @Override public void onError(String id){mainHandler.post(()->{speaking=false;setStatus("TTS ERROR — CHECK VOICE");});}
            });
            mainHandler.post(()->refreshVoiceText());
        });
    }

    private List<Voice> installedEnglishVoices() {
        List<Voice> voices = new ArrayList<>();
        if (tts == null || !ttsReady) return voices;
        Set<Voice> available=tts.getVoices();
        if(available==null)return voices;
        for(Voice v:available) {
            if(v.getLocale()!=null && "en".equals(v.getLocale().getLanguage()) && !v.isNetworkConnectionRequired()) voices.add(v);
        }
        Collections.sort(voices,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
        return voices;
    }
    private void chooseVoice(){
        if(!ttsReady){setStatus("VOICE ENGINE STILL LOADING");return;}
        List<Voice> voices = installedEnglishVoices();
        if(voices.isEmpty()){setStatus("NO INSTALLED OFFLINE ENGLISH VOICES");return;}
        String[] names = new String[voices.size()];
        for(int i=0;i<voices.size();i++) names[i]=voices.get(i).getName()+"  ("+voices.get(i).getLocale().toLanguageTag()+")";
        new AlertDialog.Builder(this).setTitle("Give Torque a voice").setItems(names,(dialog,n)->{
            Voice chosen=voices.get(n);
            int result=tts.setVoice(chosen);
            if(result==TextToSpeech.SUCCESS){
                prefs.edit().putString("voice",chosen.getName()).apply();
                refreshVoiceText();
                sayOnce("Oh, hell yes. A new voice. Now let's go annoy some traffic.");
            }else setStatus("THIS VOICE COULDN'T BE LOADED");
        }).show();
    }
    private void refreshVoiceText(){
        if(voiceText==null)return;
        if(!ttsReady){voiceText.setText("Voice: phone speech unavailable");return;}
        String saved=prefs.getString("voice","");
        for(Voice v:installedEnglishVoices()){
            if(v.getName().equals(saved)) {tts.setVoice(v);break;}
        }
        Voice actual=tts.getVoice();
        voiceText.setText("Voice: "+(actual==null?"Default":actual.getName())+" • pitch "+pitch);
    }

    private void sayOnce(String message) {
        if(tts==null||!ttsReady){setStatus("TEXT TO SPEECH NOT AVAILABLE");return;}
        // Keep Vosk listening during TTS only for explicit mute commands.
        // Any other recognized speech while Torque speaks is discarded.
        if(active && recognizer!=null){
            listenGeneration++;
            recognizer.cancel();
            recognizer.destroy();
            recognizer=null;
            listening=false;
        }
        tts.stop();
        speaking=true;
        tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"torque-"+(++nextUtterance));
    }

    private void startConversation(){
        if((vosk==null || !vosk.ready()) && Build.VERSION.SDK_INT>=31 && !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
            setStatus("OFFLINE SPEECH RECOGNIZER NOT INSTALLED");
            transcript.setText("Install an on-device speech recognition language pack in your phone settings. Torque will not secretly send your voice to an online service.");
            return;
        }
        if((vosk==null || !vosk.ready()) && Build.VERSION.SDK_INT<31) {
            setStatus("OFFLINE SPEECH CANNOT BE VERIFIED ON THIS ANDROID VERSION");
            return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST);
            return;
        }
        if(!ttsReady){setStatus("WAITING FOR TORQUE VOICE ENGINE — TRY AGAIN");return;}
        if(TorqueVoiceService.isRunning(this)){
            setStatus("STOP SCREEN-OFF TORQUE BEFORE FOREGROUND TALK");
            return;
        }
        active=true;
        sessionMuted=false;
        recognitionFailures=0;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refreshTalkButton();
        beginListening();
    }
    private void refreshTalkButton(){
        if(talkButton!=null)talkButton.setText(active?"■  END CONVERSATION":"🎙  TALK TO TORQUE");
    }
    private void beginListening(){
        if(!active || !foreground || speaking)return;
        if(vosk!=null && vosk.ready()){
            if(!vosk.listening())vosk.start();
            else vosk.pause(false);
            listening=vosk.listening();
            if(listening)setStatus(enrollingVoice ? "ENROLLMENT: SAY A LONG SENTENCE" : "MIC ON — OFFLINE VOICE ID");
            return;
        }
        if(listening || Build.VERSION.SDK_INT<31)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopConversation();return;}
        if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){stopConversation();setStatus("OFFLINE RECOGNITION UNAVAILABLE");return;}
        if(recognizer != null){recognizer.destroy();recognizer=null;}
        recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        final int generation = ++listenGeneration;
        recognizer.setRecognitionListener(new RecognitionListener(){
            @Override public void onReadyForSpeech(Bundle b){listening=true;setStatus("MIC ON — LISTENING");}
            @Override public void onBeginningOfSpeech(){setStatus("HEARING YOU…");}
            @Override public void onRmsChanged(float n){}
            @Override public void onBufferReceived(byte[] b){}
            @Override public void onEndOfSpeech(){setStatus("THINKING…");}
            @Override public void onError(int error){
                if(generation != listenGeneration || !active || !foreground) return;
                listening=false;
                if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){
                    stopConversation();
                    setStatus("MICROPHONE PERMISSION NEEDED");
                    return;
                }
                if(error==SpeechRecognizer.ERROR_NO_MATCH || error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT){
                    recognitionFailures=0;
                    setStatus("MIC ON — WAITING FOR YOU");
                    mainHandler.postDelayed(()->beginListening(),600);
                } else {
                    recognitionFailures++;
                    if(recognitionFailures>8){
                        stopConversation();
                        setStatus("OFFLINE SPEECH ENGINE UNAVAILABLE — TAP TALK TO RETRY");
                        return;
                    }
                    setStatus("RECONNECTING MIC ("+recognitionFailures+"/8)…");
                    mainHandler.postDelayed(()->beginListening(),Math.min(3500,500*recognitionFailures));
                }
            }
            @Override public void onResults(Bundle b){
                if(generation != listenGeneration || !active || !foreground) return;
                listening=false;
                recognitionFailures=0;
                ArrayList<String> matches=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(matches==null||matches.isEmpty()){
                    if(active)mainHandler.postDelayed(()->beginListening(),250);
                    return;
                }
                String heard=matches.get(0);
                if(heard==null||heard.trim().isEmpty())return;
                handleSpeech(heard,null);
            }
            @Override public void onPartialResults(Bundle b){}
            @Override public void onEvent(int e,Bundle b){}
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.US.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        try{listening=true;setStatus("MIC ON — LISTENING");recognizer.startListening(intent);}
        catch(Exception e){stopConversation();setStatus("SPEECH COULD NOT START");}
    }

    private void stopConversation(){
        active=false;listening=false;speaking=false;
        awaitingReply=false;
        if(localBrain!=null)localBrain.interrupt();
        enrollingVoice=false;
        if(vosk!=null)vosk.stop();
        listenGeneration++;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}
        if(tts!=null)tts.stop();
        refreshTalkButton();setStatus("MIC OFF — TAP TALK");
    }
    private void setStatus(String s){if(status!=null)status.setText(s);}

    private void startGps(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},GPS_REQUEST);
            return;
        }
        if(locationManager==null)return;
        try {
            locationManager.removeUpdates(this);
            boolean any=false;
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,2,this); any=true;
            }
            if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,3000,5,this); any=true;
            }
            if(!any){gpsText.setText("GPS OFF — ENABLE LOCATION IN PHONE SETTINGS");return;}
            gpsText.setText("GPS SEARCHING FOR LIVE FIX…");
        }catch(SecurityException ex){gpsText.setText("GPS PERMISSION DENIED");}
    }
    @Override public void onLocationChanged(Location location){
        currentFix=location;fixAt=SystemClock.elapsedRealtime();updateLocationLabel();
        if(places!=null && foreground)places.sample(location, this::arrivedAtPlace);
    }
    private void updateLocationLabel(){
        if(gpsText==null)return;
        if(currentFix==null){gpsText.setText("GPS OFF / NO LIVE FIX");return;}
        if(SystemClock.elapsedRealtime()-fixAt>45000){gpsText.setText("GPS FIX STALE — TAP GPS TO REFRESH");return;}
        String speed=currentFix.hasSpeed()?Math.round(currentFix.getSpeed()*2.2369363f)+" MPH":"SPEED UNKNOWN";
        String precision=currentFix.hasAccuracy()?" ±"+Math.round(currentFix.getAccuracy())+"m":"";
        gpsText.setText("PHONE GPS • "+speed+precision+"\n"+String.format(Locale.US,"%.5f, %.5f",currentFix.getLatitude(),currentFix.getLongitude()));
    }

    private void refreshVoicePrintStatus() {
        if (voicePrintStatus == null) return;
        if (vosk == null || !vosk.ready()) {
            voicePrintStatus.setText("Offline voice engine preparing; phone transcription fallback until ready.");
        } else if (speakerProfile == null || !speakerProfile.enrolled()) {
            voicePrintStatus.setText("VOICE ID: NOT ENROLLED (" + (speakerProfile == null ? 0 : speakerProfile.samples()) +
                "/3). Owner voice is not being identified.");
        } else {
            voicePrintStatus.setText("VOICE ID: OWNER ENROLLED • Uncertain voices treated as guests • Local only");
        }
    }

    private void showVoiceEnrollment() {
        if (vosk == null || !vosk.ready()) {
            new AlertDialog.Builder(this).setTitle("Offline voice models loading")
                .setMessage("Torque is setting up the built-in speech and speaker models. Keep this app open and try again when voice identification is ready.")
                .setPositiveButton("OK", null).show();
            return;
        }
        String message = "Enroll your voice in a quiet, PARKED car. Say three DIFFERENT, long sentences for 5–10 seconds each. Nobody else should talk during enrollment. Torque keeps only acoustic speaker embeddings locally, never audio recordings. This is imperfect speaker matching, NOT biometric authentication.\n\nSamples: " + speakerProfile.samples() + "/3.";
        new AlertDialog.Builder(this).setTitle("Private owner voice enrollment")
            .setMessage(message)
            .setPositiveButton("Enroll / Continue", (d,w) -> {
                if (speakerProfile.enrolled()) {
                    transcript.setText("You're already enrolled. Erase your profile first to enroll a new voice.");
                    return;
                }
                enrollingVoice = true;
                if (!active) startConversation();
                if (active) {
                    transcript.setText("OWNER VOICE ENROLLMENT " + speakerProfile.samples() +
                        "/3\nSpeak one different LONG sentence. Pause after it so Vosk can detect the end.");
                    setStatus("VOICE ENROLLMENT — LISTENING");
                }
            })
            .setNeutralButton("Erase voice ID", (d,w) ->
                new AlertDialog.Builder(this).setTitle("Erase your local voiceprint?")
                    .setMessage("This deletes your acoustic owner samples only. Your other memories stay untouched.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Erase", (x,y) -> {
                        speakerProfile.erase();
                        enrollingVoice = false;
                        refreshVoicePrintStatus();
                        transcript.setText("Owner voiceprint erased.");
                    }).show())
            .setNegativeButton("Cancel",null).show();
    }

    private void chooseMode(){
        new AlertDialog.Builder(this).setTitle("Torque speaking context").setItems(TorqueMode.ALL,(d,i)->applyMode(TorqueMode.ALL[i],true)).show();
    }
    private void applyMode(String next,boolean approved){
        if(TorqueMode.privateSpace(next)&&!approved){
            transcript.setText("Private mode requires confirmation on your phone. Until then I will behave.");
            setStatus("CONFIRM PRIVATE MODE IN SETTINGS");
            return;
        }
        if(TorqueMode.privateSpace(next)){
            new AlertDialog.Builder(this).setTitle("Confirm private conversation")
                .setMessage("Only enable uncensored Torque when no children, customers, or unwilling guests can overhear.")
                .setNegativeButton("Stay in safe mode",null)
                .setPositiveButton("I'm in private",(d,i)->commitMode(next)).show();
        }else commitMode(next);
    }
    private void commitMode(String next){
        mode=TorqueMode.normalize(next);
        prefs.edit().putString("privacy_mode",mode).apply();
        rebuildButtons();
        transcript.setText("Context: "+mode);
    }

    private void handleSpeech(String heard, org.json.JSONObject raw) {
        if (heard == null || heard.trim().isEmpty() || !active || !foreground) return;
        if (enrollingVoice) {
            if (raw == null) {
                enrollingVoice = false;
                transcript.setText("Enrollment requires the built-in offline Vosk recognizer.");
                return;
            }
            boolean saved = speakerProfile.addEnrollment(raw);
            refreshVoicePrintStatus();
            if (!saved) {
                transcript.setText("Not enough clear speech for a voice sample. Speak a LONGER sentence in a quiet place.");
                setStatus("ENROLLMENT — TRY A LONGER SENTENCE");
                return;
            }
            if (speakerProfile.enrolled()) {
                enrollingVoice = false;
                transcript.setText("Voice enrollment saved! Torque now checks acoustic voice signatures and treats unknown voices politely. This is not guaranteed identity verification.");
                setStatus("OWNER VOICE ENROLLED — MIC ON");
            } else {
                transcript.setText("Sample " + speakerProfile.samples() + "/3 saved. Say a DIFFERENT long sentence now.");
                setStatus("ENROLLMENT " + speakerProfile.samples() + "/3 — MIC ON");
            }
            return;
        }
        if(TorqueMode.stopNow(heard)){
            sessionMuted=true;
            if(localBrain!=null)localBrain.interrupt();
            stopConversation();
            transcript.setText("Torque muted. No last word.");
            return;
        }
        if(speaking || awaitingReply)return;
        String requested=TorqueMode.command(heard);
        if(requested!=null){
            applyMode(requested,false);
            if(!TorqueMode.privateSpace(requested)&&ttsReady)sayOnce("Okay, "+mode+" mode.");
            return;
        }
        boolean likelyOwner = raw != null && speakerProfile.enrolled() &&
            speakerProfile.classify(raw) == SpeakerProfile.Identity.OWNER;
        boolean uncertain = speakerProfile.enrolled() && !likelyOwner;
        boolean asGuest = ride.guestTurn() || (speakerProfile.enrolled()?uncertain:ride.hasGuests()) || passenger || TorqueMode.clean(mode);
        String presence = ride.hearPresence(heard);
        if (presence != null) refreshRideText();
        if(localBrain!=null && localBrain.isReady() && TorqueMode.privateSpace(mode) && !asGuest && !TorqueMode.quiet(mode)){
            lastPrompt=heard;
            awaitingReply=true;
            String prompt=buildPrompt(heard,asGuest);
            setStatus("OFFLINE AI THINKING");
            localBrain.answer(prompt);
            return;
        }
        String specialized=contextReply.answer(heard,mode,asGuest);
        String reply=(presence!=null && !TorqueMode.clean(mode))?presence:(specialized!=null?specialized:(TorqueMode.privateSpace(mode)?torque.respond(heard,currentFix,fixAt,asGuest,quiet,ride):contextReply.answer("hello",mode,true)));
        if (TorqueMode.privateSpace(mode) && !asGuest && presence == null) memory.log(heard, reply);
        String speaker = likelyOwner ? "LIKELY OWNER" : uncertain ? "UNKNOWN / GUEST" :
            asGuest ? "GUEST (MANUAL)" : "NOT VOICE-VERIFIED";
        transcript.setText(speaker + ": " + heard + "\n\nTORQUE: " + reply);
        if (ttsReady) sayOnce(reply);
        else { stopConversation(); setStatus("VOICE OUTPUT UNAVAILABLE"); }
    }

    private void refreshRideText() {
        if (rideText != null) rideText.setText(ride.summary());
    }

    private void showRideControls() {
        String[] choices = {"Add Dad", "Add a passenger by name", "Talk as driver (not voice-verified)",
            "Talk as guest (not voice-verified)", "Clear all passengers"};
        new AlertDialog.Builder(this).setTitle("Who's riding?").setItems(choices,(d,n)->{
            if(n==0) {
                ride.addGuest("Dad");
                transcript.setText("Torque: Well hello, Dad! Nice to have someone civilized riding along.");
            } else if(n==1) {
                android.widget.EditText entry = new android.widget.EditText(this);
                entry.setSingleLine();
                entry.setHint("Passenger first name");
                new AlertDialog.Builder(this).setTitle("Add a passenger")
                    .setView(entry)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Add",(a,b)->{
                        boolean ok=ride.addGuest(entry.getText().toString());
                        refreshRideText();
                        if(!ok)transcript.setText("Name wasn't accepted, or all four guest slots are filled.");
                    }).show();
            } else if(n==2) {
                ride.guestTurn(false);
            } else if(n==3) {
                if(!ride.hasGuests()){
                    transcript.setText("Add a passenger first. This is a manual speaking mode, not voice identification.");
                } else ride.guestTurn(true);
            } else if(n==4) {
                ride.clear();
            }
            refreshRideText();
        }).setNegativeButton("Done",null).show();
    }

    private void shareFacts() {
        StringBuilder b=new StringBuilder("Torque local memories (exported by owner)\n");
        for(String fact:memory.facts())b.append("\n").append(fact);
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,b.toString());
        startActivity(Intent.createChooser(send,"Export Torque notes"));
    }

    private void updateBrainStatus(){
        if(brainStatus==null)return;
        if(localBrain==null)brainStatus.setText("Local LLM: unavailable");
        else if(localBrain.isReady())brainStatus.setText("Local LLM: GGUF READY • PRIVATE MODES ONLY");
        else brainStatus.setText("Local LLM: import a GGUF file • scripted fallback is active");
    }


    private void startScreenOffTorque(){
        if(TorqueVoiceService.isRunning(this)){
            setStatus("SCREEN-OFF SESSION ALREADY STARTED");
            return;
        }
        // Foreground microphone permission must be granted while the Activity is visible.
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},BACKGROUND_MIC_REQUEST);
            return;
        }
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_REQUEST);
            return;
        }
        new AlertDialog.Builder(this).setTitle("Start screen-off Torque?")
            .setMessage("You are explicitly enabling microphone listening after locking the screen. Android will show an ongoing Torque notification with a STOP button. Keep the phone charged. This experimental service starts only now, never on boot, and does not track GPS in the background. To keep everyone comfortable, the selected speaking context remains in effect. Unknown speakers default to polite conversation.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Start listening",(d,i)->{
                stopConversation();
                // A single speech recognizer at a time: release the Activity microphone pipeline.
                if(vosk!=null){vosk.release();vosk=null;}
                Intent intent=new Intent(this,TorqueVoiceService.class)
                    .setAction(TorqueVoiceService.ACTION_START)
                    .putExtra(TorqueVoiceService.EXTRA_MODE,mode);
                try{
                    if(Build.VERSION.SDK_INT>=26) startForegroundService(intent);
                    else startService(intent);
                    setStatus("SCREEN-OFF SESSION REQUESTED — CHECK NOTIFICATION");
                    transcript.setText("TORQUE SCREEN-OFF: If the ongoing microphone notification appears, you can turn off the display. Tap STOP TORQUE in that notification to end the session. To return to foreground microphone, stop the service and reopen this app.");
                }catch(RuntimeException unavailable){
                    setStatus("ANDROID BLOCKED BACKGROUND MICROPHONE START");
                    transcript.setText("Start failed: "+unavailable.getClass().getSimpleName()+". Try again while Torque is visible.");
                }
            }).show();
    }

    private void stopScreenOffTorque(){
        Intent intent=new Intent(this,TorqueVoiceService.class).setAction(TorqueVoiceService.ACTION_STOP);
        try{
            startService(intent);
        }catch(RuntimeException e){
            stopService(new Intent(this,TorqueVoiceService.class));
        }
        setStatus("SCREEN-OFF STOP REQUESTED");
        transcript.setText("Torque screen-off microphone stop requested. Check that the notification disappears.");
        if(vosk==null){
            vosk=new VoskController(this,new VoskController.Callbacks(){
                @Override public void onReady(){refreshVoicePrintStatus();}
                @Override public void onSpeech(String words,org.json.JSONObject result){
                    mainHandler.post(()->{if(active&&foreground)handleSpeech(words,result);});
                }
                @Override public void onError(String explanation){
                    mainHandler.post(()->setStatus(explanation));
                }
            });
            vosk.prepare();
        }
    }

    private void pickModel(){
        if(active)stopConversation();
        new AlertDialog.Builder(this).setTitle("Real offline AI brain")
            .setMessage("Pick a GGUF model you have licensed and downloaded. A small 0.5–1.5B quantized instruct model is a reasonable first experiment. Larger models can exceed phone RAM. This uses local llama.cpp, NOT a cloud service. Start while PARKED; model loading may use substantial memory.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Select GGUF file",(d,n)->{
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent,MODEL_PICKER);
            }).show();
    }

    @Override protected void onActivityResult(int request,int code,Intent result){
        super.onActivityResult(request,code,result);
        if(request!=MODEL_PICKER || code!=Activity.RESULT_OK || result==null || result.getData()==null)return;
        Uri uri=result.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException denied){
            setStatus("GGUF FILE PERMISSION DENIED");return;
        }
        prefs.edit().putString("gguf_uri",uri.toString()).apply();
        setStatus("LOADING LOCAL GGUF");
        brainStatus.setText("Local LLM: loading selected model…");
        localBrain.load(uri.toString());
    }

    private String buildPrompt(String spoken,boolean guest){
        java.time.ZonedDateTime now=java.time.ZonedDateTime.now();
        String date=now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d uuuu h:mm a",Locale.US));
        StringBuilder p=new StringBuilder();
        p.append("System: You are TORQUE, the one and only original foul-mouthed, sarcastic, loyal best friend of a human driving a 2010 RAV4. Your personality is spontaneous, witty, warm when necessary, affectionate and gloriously profane in a CONFIRMED PRIVATE conversation. You are NOT a corporate customer-service bot. Reply naturally with 1–3 conversational sentences unless the owner asks for details. Do not repeat the same canned jokes. You only know what actual phone sensors and saved user reports show. You have NO eyes, car telemetry, live traffic, live weather, internet browsing or control over any device. Avoid encouraging distracted or dangerous driving. Don't infer anyone's age, identity, feelings or location from voice. Always stop when asked.\n");
        p.append("Owner's birthday: June 18 1982, age today: ").append(TorqueContext.ownerAge()).append(".\n");
        p.append("Phone date/time: ").append(date).append(". Context: ").append(mode).append(".\n");
        p.append("The owner works Waffle House second shift, usually 2–9; wants to move back to Garden City / Westland / Detroit Michigan. These are user-reported background facts, not live observations.\n");
        if(currentFix!=null && SystemClock.elapsedRealtime()-fixAt<45000 && currentFix.hasAccuracy()&&currentFix.getAccuracy()<=60){
            p.append("Phone GPS is fresh: ").append(String.format(Locale.US,"%.5f, %.5f",currentFix.getLatitude(),currentFix.getLongitude()));
            if(currentFix.hasSpeed())p.append(", GPS speed ").append(Math.round(currentFix.getSpeed()*2.2369363f)).append(" mph");
            p.append(". This is phone GPS, not OBD.\n");
        }else p.append("Phone GPS unavailable or stale; do not claim location or speed.\n");
        p.append("Relevant owner-saved notes (user-reported, may be old):\n");
        int count=0;
        for(String fact:memory.facts()){
            if(count++>=6)break;
            p.append("- ").append(fact.replace("\n"," ").replace("\r"," ")).append("\n");
        }
        if(ride.hasGuests())p.append("The owner has introduced passengers; be considerate. Do not expose sensitive owner memories.\n");
        p.append("Owner says: ").append(spoken).append("\nTorque:");
        return p.toString();
    }

    private void handleBrainFailure(String reason){
        awaitingReply=false;
        setStatus("LOCAL AI FALLBACK — "+reason);
        if(!active || !foreground || sessionMuted)return;
        String reply=torque.respond(lastPrompt,currentFix,fixAt,false,false,ride);
        transcript.setText("Local inference unavailable ("+reason+"). Scripted reply: "+reply);
        if(ttsReady)sayOnce(reply);else beginListening();
    }

    private void managePlaces(){
        List<PlaceAwareness.Place> all=places.places();
        String[] names=new String[all.size()+2];
        names[0]="PIN HERE • Saved Waffle House (work-safe) / Walmart / Home / other";
        names[1]="ARRIVAL BANTER: "+(prefs.getBoolean("arrival_banter",false)?"ON":"OFF")+" (work safety always on)";
        for(int i=0;i<all.size();i++)names[i+2]=all.get(i).name+" • "+all.get(i).kind+" • Tap to remove";
        new AlertDialog.Builder(this).setTitle("Actual GPS saved places")
            .setItems(names,(d,which)->{
                if(which==0){pinCurrentPlace();return;}
                if(which==1){
                    boolean enable=!prefs.getBoolean("arrival_banter",false);
                    prefs.edit().putBoolean("arrival_banter",enable).apply();
                    transcript.setText("Optional arrival jokes: "+(enable?"ON":"OFF")+". Waffle House still goes work-safe when GPS reliably confirms the saved work pin.");
                    return;
                }
                int idx=which-2;
                if(idx>=0&&idx<all.size()){
                    PlaceAwareness.Place p=all.get(idx);
                    new AlertDialog.Builder(this).setTitle("Remove "+p.name+"?")
                        .setMessage("This deletes the GPS location stored on your phone.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Remove",(x,y)->{
                            places.remove(idx);
                            transcript.setText("Removed saved pin: "+p.name);
                        }).show();
                }
            }).setNegativeButton("Done",null).show();
    }

    private void pinCurrentPlace(){
        if(currentFix==null || SystemClock.elapsedRealtime()-fixAt>15000 ||
            !currentFix.hasAccuracy() || currentFix.getAccuracy()>40){
            transcript.setText("No fresh accurate GPS fix. Tap GPS, park and wait for accuracy under 40 meters before pinning.");
            return;
        }
        String[] types={"Work / Waffle House","Walmart","Home","Other"};
        new AlertDialog.Builder(this).setTitle("What place is this?")
            .setItems(types,(d,which)->{
                String kind=types[which];
                android.widget.EditText entry=new android.widget.EditText(this);
                entry.setSingleLine();
                entry.setText(which==0?"Waffle House":which==1?"Walmart":which==2?"Home":"My saved place");
                new AlertDialog.Builder(this).setTitle("Pin a real location while PARKED")
                    .setView(entry)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Save",(a,b)->{
                        boolean ok=places.pin(entry.getText().toString(),kind,currentFix);
                        transcript.setText(ok?"Saved "+entry.getText()+" with real GPS. Arrival needs a slow 25-second dwell.":"Cannot save: park, wait for accurate GPS and check the 20-pin limit.");
                    }).show();
            }).show();
    }

    private void arrivedAtPlace(PlaceAwareness.Place place){
        if(place==null)return;
        if(place.kind.equals("Work / Waffle House")){
            if(!TorqueMode.WORK.equals(mode))commitMode(TorqueMode.WORK);
            transcript.setText("ARRIVED at saved work pin after verified dwell. Work-safe mode activated.");
            if(active && !speaking && !awaitingReply) sayOnce("Welcome to school, superstar! Remember your lunchbox and use your inside voice. Have a good shift.");
            return;
        }
        if(!prefs.getBoolean("arrival_banter",false))return;
        if(!active || speaking || awaitingReply)return;
        if(!TorqueMode.privateSpace(mode))return;
        String line;
        if(place.kind.equals("Walmart"))
            line="Well, would you look at that, we're at your saved Walmart! Don't forget the crayons and those overpriced fifteen-dollar sticks. That's an OLD price, not a live sale, fossil.";
        else if(place.kind.equals("Home"))line="Back at the saved home pin. Now that you're parked, want to hang out with your favorite sarcastic pile of software?";
        else line="Looks like we reached your saved spot, "+place.name+". What kind of trouble are we getting into?";
        transcript.setText("ARRIVAL • "+place.name+"\nTORQUE: "+line);
        sayOnce(line);
    }

    private void showMemories(){
        List<String> facts=memory.facts();
        StringBuilder b=new StringBuilder("Built-in canon: You work at Waffle House; five years in January 2027.\n\nSaved memories:\n");
        if(facts.isEmpty())b.append("(none yet)");
        for(String f:facts)b.append("• ").append(f).append("\n");
        new AlertDialog.Builder(this).setTitle("Torque's local memories")
        .setMessage(b.toString())
        .setPositiveButton("Done",(d,w)->{})
        .setNeutralButton("Forget newest",(d,w)->{memory.forgetLatest();transcript.setText("Newest saved memory deleted.");})
        .setNegativeButton("Erase all",(d,w)->new AlertDialog.Builder(this)
           .setTitle("Delete all conversation and memory history?")
           .setNegativeButton("Cancel",null)
           .setPositiveButton("Erase",(a,c)->{memory.clearAll();transcript.setText("Local history and saved memories erased.");}).show())
        .show();
    }

    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grant){
        super.onRequestPermissionsResult(request,perms,grant);
        boolean ok=grant.length>0 && grant[0]==PackageManager.PERMISSION_GRANTED;
        if(request==MIC_REQUEST){if(ok)startConversation();else setStatus("MIC PERMISSION DENIED");}
        if(request==BACKGROUND_MIC_REQUEST){
            if(ok)startScreenOffTorque();else setStatus("SCREEN-OFF MICROPHONE PERMISSION DENIED");
        }
        if(request==NOTIFICATION_REQUEST){
            if(ok)startScreenOffTorque();else setStatus("NOTIFICATIONS DISABLED — CANNOT START THIS EXPERIMENTAL SCREEN-OFF MODE");
        }
        if(request==GPS_REQUEST){if(ok)startGps();else gpsText.setText("GPS PERMISSION DENIED");}
    }
    @Override protected void onStart(){super.onStart();foreground=true;}
    @Override protected void onStop(){
        foreground=false;stopConversation();
        ride.clear();
        refreshRideText();
        if(locationManager!=null)locationManager.removeUpdates(this);
        currentFix=null;fixAt=0;updateLocationLabel();
        super.onStop();
    }
    @Override protected void onDestroy(){
        stopConversation();
        if(localBrain!=null){localBrain.release();localBrain=null;}
        if(vosk!=null){vosk.release();vosk=null;}
        if(tts!=null){tts.shutdown();tts=null;}
        if(memory!=null)memory.close();
        super.onDestroy();
    }
}
