package com.torque.voice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
        memory = new MemoryDB(this);
        torque = new ScriptedTorque(memory);
        speakerProfile = new SpeakerProfile(this);
        vosk = new VoskController(this, new VoskController.Callbacks(){
            @Override public void onReady(){
                if(!active)setStatus("OFFLINE VOICE ENGINE READY");
                refreshVoicePrintStatus();
            }
            @Override public void onSpeech(String words, org.json.JSONObject result) {
                if(active && foreground) handleSpeech(words,result);
            }
            @Override public void onError(String explanation) {
                if(!active)setStatus(explanation);
                else if(vosk!=null && vosk.listening()){
                    stopConversation();
                    setStatus(explanation);
                }
            }
        });
        makeScreen();
        initTts();
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

        addButton(button("GPS: TURN ON / REFRESH",this::startGps));
        addButton(button("MEMORIES / DELETE",this::showMemories));

        TextView disclosure=text("Hands-free listening stays on between replies until you press Stop or leave the app. The screen stays awake while listening; Android may stop recognition if its speech service fails. Mic and GPS stop when you leave. No hidden mic, uploads, or ads. Requires installed offline speech recognition. Dialogue is lightweight and scripted.",11,Color.GRAY);
        panel.addView(disclosure);
    }

    private void rebuildButtons() {
        makeScreen();
        refreshVoiceText();
        refreshRideText();
        refreshVoicePrintStatus();
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
        if(active && vosk!=null && vosk.listening()) vosk.pause(true);
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
        active=true;
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
    }
    private void updateLocationLabel(){
        if(gpsText==null)return;
        if(currentFix==null){gpsText.setText("GPS OFF / NO LIVE FIX");return;}
        if(SystemClock.elapsedRealtime()-fixAt>45000){gpsText.setText("GPS FIX STALE — TAP GPS TO REFRESH");return;}
        String speed=currentFix.hasSpeed()?Math.round(currentFix.getSpeed()*2.2369363f)+" MPH":"SPEED UNKNOWN";
        String precision=currentFix.hasAccuracy()?" ±"+Math.round(currentFix.getAccuracy())+"m":"";
        gpsText.setText("PHONE GPS • "+speed+precision+"\n"+String.format(Locale.US,"%.5f, %.5f",currentFix.getLatitude(),currentFix.getLongitude()));
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
        if(vosk!=null){vosk.release();vosk=null;}
        if(tts!=null){tts.shutdown();tts=null;}
        if(memory!=null)memory.close();
        super.onDestroy();
    }
}
