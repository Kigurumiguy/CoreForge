package com.torque.voice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;
import java.util.Set;
import android.speech.tts.Voice;

/**
 * Explicit user-started microphone foreground service.
 * No boot receiver, no automatic relaunch, no covert recording or uploads.
 * Screen-off voice conversation is experimental and uses local Vosk STT.
 * GPS/arrivals are deliberately NOT monitored in the background.
 */
public final class TorqueVoiceService extends Service {
    static final String ACTION_START="com.torque.voice.action.START_SCREEN_OFF";
    static final String ACTION_STOP="com.torque.voice.action.STOP_SCREEN_OFF";
    static final String EXTRA_MODE="context";
    static final String CHANNEL="torque_visible_microphone";
    static final String PREFS="torque_screen_off";
    static final String KEY_RUNNING="running";
    private static final int NOTIFICATION_ID=2031;
    private final Handler main=new Handler(Looper.getMainLooper());

    private static volatile boolean SCREEN_OFF_ACTIVE=false;
    private boolean running=false;
    private final VoiceTurnGate turnGate=new VoiceTurnGate();
    private boolean speaking=false;
    private boolean waitingForModel=false;
    private boolean voiceReady=false;
    private String mode=TorqueMode.GUEST;
    private long utterance=0;
    private String lastWords="";
    private VoskController vosk;
    private TorqueLocalBrain brain;
    private MemoryDB memory;
    private ScriptedTorque scripted;
    private SpeakerProfile owner;
    private final RideContext ride=new RideContext();
    private final TorqueContext context=new TorqueContext();
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private android.media.AudioManager audioManager;
    private boolean holdsAudioFocus=false;

    static boolean isRunning(Context context){
        return SCREEN_OFF_ACTIVE;
    }

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences("torque_prefs",MODE_PRIVATE);
        audioManager=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopNow();
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(action) || running){
            if(!running)stopSelf();
            return START_NOT_STICKY;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            stopSelf();return START_NOT_STICKY;
        }
        try {
            Notification notification=notification("Preparing local voice engine…");
            if(Build.VERSION.SDK_INT>=29)
                startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIFICATION_ID,notification);
        } catch(RuntimeException denied){
            stopSelf();return START_NOT_STICKY;
        }
        // Caller is the visible, user-confirmed Activity; never elevate context from GPS.
        mode=TorqueMode.normalize(intent.getStringExtra(EXTRA_MODE));
        running=true;
        SCREEN_OFF_ACTIVE=true;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(KEY_RUNNING,true).apply();
        memory=new MemoryDB(this);
        scripted=new ScriptedTorque(memory);
        owner=new SpeakerProfile(this);
        initializeSpeech();
        initializeBrain();
        initializeVosk();
        return START_NOT_STICKY; // Never auto-restart the microphone after OS kills us.
    }

    private void initializeSpeech(){
        tts=new TextToSpeech(this, code->{
            if(!running)return;
            if(code!=TextToSpeech.SUCCESS){
                updateNotification("Phone speech output unavailable • Stop");
                stopNow();stopSelf();return;
            }
            voiceReady=true;
            tts.setLanguage(Locale.US);
            tts.setPitch(prefs.getFloat("pitch",0.87f));
            tts.setSpeechRate(0.97f);
            String selected=prefs.getString("voice","");
            Set<Voice> installed=tts.getVoices();
            if(installed!=null){
                for(Voice v:installed){
                    if(v.getName().equals(selected)&&!v.isNetworkConnectionRequired()){
                        tts.setVoice(v);break;
                    }
                }
            }
            tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){main.post(()->{
                    if(!running)return;
                    speaking=true;updateNotification("Torque speaking • Say 'Torque, enough' to stop");
                });}
                @Override public void onDone(String id){main.post(()->{
                    if(!running)return;
                    speaking=false;
                    turnGate.speechFinished(android.os.SystemClock.elapsedRealtime());
                    abandonAudioFocus();
                    updateNotification("Microphone ON • Listening for you");
                });}
                @Override public void onError(String id){main.post(()->{
                    speaking=false;turnGate.speechFinished(android.os.SystemClock.elapsedRealtime());abandonAudioFocus();
                    if(running)updateNotification("Speech playback error • Listening");
                });}
            });
            resumeListeningIfReady();
        });
    }

    private void initializeVosk(){
        vosk=new VoskController(this,new VoskController.Callbacks(){
            @Override public void onReady(){resumeListeningIfReady();}
            @Override public void onSpeech(String words,org.json.JSONObject result){
                main.post(()->{if(running)onHeard(words,result);});
            }
            @Override public void onPartial(String words){
                main.post(()->onPartialHeard(words));
            }
            @Override public void onError(String message){
                main.post(()->{
                    if(!running)return;
                    updateNotification("Mic error: "+message+" • Tap Stop");
                    stopNow();stopSelf();
                });
            }
        });
        vosk.prepare();
    }

    private void initializeBrain(){
        brain=new TorqueLocalBrain(this,new TorqueLocalBrain.Callbacks(){
            @Override public void modelReady(String description){
                // Speech continues while model loads, via an explicit scripted fallback.
                if(running)updateNotification("Microphone ON • Offline AI ready");
            }
            @Override public void answer(String reply,long millis,int tokens){
                if(!running||!waitingForModel)return;
                waitingForModel=false;
                turnGate.thinkingEnded();
                String text=reply.trim();
                if(text.isEmpty())text=scripted.respond(lastWords,null,0L,false,false,ride);
                speak(text);
            }
            @Override public void failed(String reason){
                if(!running||!waitingForModel)return;
                waitingForModel=false;
                turnGate.thinkingEnded();
                speak(scripted.respond(lastWords,null,0L,false,false,ride));
            }
        });
        String uri=prefs.getString("gguf_uri","");
        if(!uri.isEmpty()){
            updateNotification("Preparing offline GGUF • Mic initializing");
            brain.load(uri);
        }
    }

    private void resumeListeningIfReady(){
        if(!running||!voiceReady||vosk==null||!vosk.ready())return;
        if(!vosk.listening())vosk.start();
        if(vosk.listening())updateNotification("Microphone ON • Screen-off listening");
    }

    private void onPartialHeard(String words){
        if(!running)return;
        if(turnGate.evaluate(words,true,android.os.SystemClock.elapsedRealtime())==VoiceTurnGate.Decision.STOP){
            stopNow();
            stopSelf();
        }
    }

    private void onHeard(String words,org.json.JSONObject raw){
        if(words==null||words.trim().isEmpty())return;
        VoiceTurnGate.Decision decision=turnGate.evaluate(words,false,android.os.SystemClock.elapsedRealtime());
        if(decision==VoiceTurnGate.Decision.IGNORE)return;
        if(decision==VoiceTurnGate.Decision.STOP){
            // Stop is handled even during TTS. No "last word" gag.
            stopNow();stopSelf();return;
        }
        if(speaking||waitingForModel)return; // Never respond to our own speaker echo.
        String requested=TorqueMode.command(words);
        if(requested!=null){
            if(TorqueMode.privateSpace(requested)){
                speak("Private mode needs your confirmation on the phone.");
                return;
            }
            mode=requested;
            updateNotification("Microphone ON • "+mode);
            return;
        }
        lastWords=words;
        boolean ownerKnown=owner.enrolled();
        boolean matched=ownerKnown && raw!=null && owner.classify(raw)==SpeakerProfile.Identity.OWNER;
        boolean guest=TorqueMode.clean(mode) || ride.hasGuests() || (ownerKnown && !matched);
        String presence=ride.hearPresence(words);
        if(TorqueMode.privateSpace(mode)&&!guest&&presence==null&&brain!=null&&brain.isReady()){
            waitingForModel=true;
            turnGate.thinkingStarted();
            updateNotification("Microphone ON • Offline AI thinking");
            brain.answer(buildPrompt(words));
            return;
        }
        String specialized=context.answer(words,mode,guest);
        String reply=presence!=null && !TorqueMode.clean(mode)?presence:
            specialized!=null?specialized:
            TorqueMode.privateSpace(mode)?
                scripted.respond(words,null,0L,guest,false,ride):
                context.answer("hello",mode,true);
        if(TorqueMode.privateSpace(mode)&&!guest&&presence==null)
            memory.log(words,reply);
        speak(reply);
    }

    private String buildPrompt(String words){
        StringBuilder p=new StringBuilder();
        p.append("System: You are Torque, a foul-mouthed but loyal, warm, spontaneous best friend for the owner of a 2010 RAV4. This session was explicitly started in confirmed private mode. You are NOT an online service. Respond in 1 to 3 natural sentences. Avoid repeating jokes. You cannot see the road or people, and no GPS is available to this screen-off session. Don't invent weather, location, traffic, passenger identities, or sensor readings. Never encourage distracted driving. No sensitive personal revelations to guests.\n");
        p.append("Owner birthday June 18 1982. Age now ").append(TorqueContext.ownerAge()).append(". Phone local time ").append(java.time.ZonedDateTime.now().toString()).append(".\n");
        p.append("The owner reports working Waffle House second shift and wants to return to Michigan. User-reported history, not verified live details.\n");
        p.append("Owner: ").append(words).append("\nTorque:");
        return p.toString();
    }

    private void speak(String text){
        if(!running||!voiceReady||tts==null||text==null||text.isEmpty())return;
        requestAudioFocus();
        speaking=true;
        turnGate.speechStarted(text,android.os.SystemClock.elapsedRealtime());
        updateNotification("Torque speaking • Say 'Torque, enough' to stop");
        tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"screen-off-"+(++utterance));
    }

    private void requestAudioFocus(){
        if(audioManager==null)return;
        // Legacy focus works back to API 26; navigation/multimedia may duck it.
        int result=audioManager.requestAudioFocus(null,android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        holdsAudioFocus=result==android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    private void abandonAudioFocus(){
        if(holdsAudioFocus&&audioManager!=null)
            audioManager.abandonAudioFocus(null);
        holdsAudioFocus=false;
    }

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Torque active microphone",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible notification whenever Torque is listening with the screen off");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
        }
    }
    private Notification notification(String status){
        Intent open=new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
        PendingIntent tap=PendingIntent.getActivity(this,11,open,flags);
        Intent stop=new Intent(this,TorqueVoiceService.class).setAction(ACTION_STOP);
        PendingIntent off=PendingIntent.getService(this,12,stop,flags);
        Notification.Builder n=new Notification.Builder(this,CHANNEL)
            .setContentTitle("TORQUE • Screen-off conversation")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause,"STOP TORQUE",off);
        return n.build();
    }
    private void updateNotification(String status){
        if(!running)return;
        NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        n.notify(NOTIFICATION_ID,notification(status));
    }

    private void stopNow(){
        running=false;
        SCREEN_OFF_ACTIVE=false;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(KEY_RUNNING,false).apply();
        waitingForModel=false; speaking=false;voiceReady=false;
        turnGate.stop();
        if(brain!=null){brain.release();brain=null;}
        if(vosk!=null){vosk.release();vosk=null;}
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        abandonAudioFocus();
        if(memory!=null){memory.close();memory=null;}
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override public void onDestroy(){stopNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onTaskRemoved(Intent rootIntent){
        // The user may keep it running after dismissing the app; STOP notification remains.
        super.onTaskRemoved(rootIntent);
    }
}
