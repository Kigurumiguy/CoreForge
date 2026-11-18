package com.torque.voice;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.SpeakerModel;

/**
 * Offline speech and speaker embeddings with an app-owned microphone stream.
 * No captured audio is stored or uploaded.
 * Android AEC/NS effects are attached to THIS AudioRecord's session when present.
 * Presence of an active effect does not prove it actually cancels RAV4 AUX echo.
 */
final class VoskController {
    interface Callbacks {
        void onReady();
        void onSpeech(String text, JSONObject result);
        void onPartial(String text);
        void onAudioStatus(String explanation);
        void onError(String explanation);
    }
    static final int RATE=16000;
    private final Context context;
    private final Callbacks callbacks;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object stateLock=new Object();
    private volatile Model language;
    private volatile SpeakerModel speaker;
    private volatile boolean ready=false;
    private volatile boolean preparing=false;
    private volatile boolean listening=false;
    private volatile boolean paused=false;
    private volatile boolean disposed=false;
    private volatile boolean running=false;
    private volatile AudioRecord recorder;
    private volatile Thread worker;
    private volatile int epoch=0;
    private String audioStatus="Mic idle • AEC not active";
    private long lastPartialAt=0;

    VoskController(Context c,Callbacks cb){ context=c.getApplicationContext();callbacks=cb; }

    boolean ready(){return ready;}
    boolean listening(){return listening;}
    String audioStatus(){return audioStatus;}

    void prepare(){
        if(preparing||ready||disposed)return;
        preparing=true;
        new Thread(()->{
            try{
                java.io.File[] paths=VoskModels.ensureReady(context);
                Model l=new Model(paths[0].getAbsolutePath());
                SpeakerModel sp;
                try{sp=new SpeakerModel(paths[1].getAbsolutePath());}
                catch(Exception e){l.close();throw e;}
                main.post(()->{
                    if(disposed){l.close();sp.close();return;}
                    language=l;speaker=sp;ready=true;preparing=false;callbacks.onReady();
                });
            }catch(Exception failure){
                String msg=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();
                main.post(()->{preparing=false;if(!disposed)callbacks.onError("Offline voice models: "+msg);});
            }
        },"torque-vosk-install").start();
    }

    @android.annotation.SuppressLint("MissingPermission")
    void start(){
        if(!ready||disposed||listening)return;
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            callbacks.onError("Microphone permission denied");return;
        }
        synchronized(stateLock){
            if(running||disposed||!ready)return;
            running=true;
            listening=true;
            paused=false;
            final int token=++epoch;
            worker=new Thread(()->runMic(token),"torque-vosk-audio");
            worker.start();
        }
    }

    private void runMic(int token){
        AudioRecord capture=null;
        AcousticEchoCanceler aec=null;
        NoiseSuppressor ns=null;
        Recognizer decoder=null;
        String failure=null;
        try {
            SharedPreferences policy=context.getSharedPreferences("torque_audio",Context.MODE_PRIVATE);
            boolean useComm=policy.getBoolean("voice_communication",true);
            boolean wantAec=policy.getBoolean("aec_enabled",true);
            boolean wantNs=policy.getBoolean("ns_enabled",true);

            int source=useComm?MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                MediaRecorder.AudioSource.VOICE_RECOGNITION;
            capture=buildRecorder(source);
            if(capture==null && useComm){
                capture=buildRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION);
                source=MediaRecorder.AudioSource.VOICE_RECOGNITION;
            }
            if(capture==null)throw new IllegalStateException("Android couldn't open a 16kHz microphone stream.");
            if(!running||token!=epoch)return;
            recorder=capture;
            int sessionId=capture.getAudioSessionId();

            if(AcousticEchoCanceler.isAvailable()){
                try {
                    aec=AcousticEchoCanceler.create(sessionId);
                    if(aec!=null)aec.setEnabled(wantAec);
                }catch(RuntimeException ignored){}
            }
            if(NoiseSuppressor.isAvailable()){
                try {
                    ns=NoiseSuppressor.create(sessionId);
                    if(ns!=null)ns.setEnabled(wantNs);
                }catch(RuntimeException ignored){}
            }
            boolean aecOn=aec!=null && aec.getEnabled();
            boolean nsOn=ns!=null && ns.getEnabled();
            String status=(source==MediaRecorder.AudioSource.VOICE_COMMUNICATION?"VOICE_COMMUNICATION":"VOICE_RECOGNITION")+
                " 16kHz • AEC "+(aecOn?"ACTIVE":aec==null?"UNAVAILABLE":"OFF")+
                " • NS "+(nsOn?"ACTIVE":ns==null?"UNAVAILABLE":"OFF");
            audioStatus=status;
            main.post(()->{
                if(!disposed&&token==epoch)callbacks.onAudioStatus(status);
            });

            decoder=new Recognizer(language,RATE);
            decoder.setSpeakerModel(speaker);
            capture.startRecording();
            if(capture.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("Android microphone did not begin recording.");

            // 0.2 seconds of mono PCM16 at 16kHz, same format as the Vosk model.
            short[] samples=new short[RATE/5];
            while(running && token==epoch && !Thread.currentThread().isInterrupted()){
                int n=capture.read(samples,0,samples.length,AudioRecord.READ_BLOCKING);
                if(n==AudioRecord.ERROR_DEAD_OBJECT || n==AudioRecord.ERROR_INVALID_OPERATION ||
                    n==AudioRecord.ERROR_BAD_VALUE || n==AudioRecord.ERROR)
                    throw new IllegalStateException("Microphone stream error: "+n);
                if(n<=0)continue;
                if(paused)continue;
                if(decoder.acceptWaveForm(samples,n)){
                    final String line=decoder.getResult();
                    postResult(line,token,false);
                } else {
                    long now=SystemClock.elapsedRealtime();
                    if(now-lastPartialAt>=180){
                        lastPartialAt=now;
                        final String part=decoder.getPartialResult();
                        postResult(part,token,true);
                    }
                }
            }
        }catch(Exception e){
            failure=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        }finally{
            // Never release AudioRecord or Recognizer while the decoder thread is using them.
            if(aec!=null)try{aec.release();}catch(RuntimeException ignored){}
            if(ns!=null)try{ns.release();}catch(RuntimeException ignored){}
            if(capture!=null){
                try{if(capture.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)capture.stop();}catch(RuntimeException ignored){}
                try{capture.release();}catch(RuntimeException ignored){}
            }
            if(decoder!=null)try{decoder.close();}catch(Exception ignored){}
            if(recorder==capture)recorder=null;
            final String error=failure;
            final boolean wasUnexpected=running && token==epoch;
            if(token==epoch){listening=false;running=false;}
            if(error!=null && wasUnexpected){
                main.post(()->{if(!disposed&&token==epoch)callbacks.onError("Offline microphone: "+error);});
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private AudioRecord buildRecorder(int source){
        try{
            int minBytes=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minBytes<=0)return null;
            int capacity=Math.max(minBytes,RATE*2/2);
            AudioFormat format=new AudioFormat.Builder()
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build();
            AudioRecord mic=new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(capacity)
                .build();
            if(mic.getState()!=AudioRecord.STATE_INITIALIZED){
                mic.release();return null;
            }
            return mic;
        }catch(RuntimeException unsupported){return null;}
    }

    private void postResult(String raw,int token,boolean partial){
        if(!running||paused||disposed||token!=epoch)return;
        try{
            JSONObject json=new JSONObject(raw);
            String text=json.optString(partial?"partial":"text","").trim();
            if(text.isEmpty())return;
            main.post(()->{
                if(!running||paused||disposed||token!=epoch)return;
                if(partial)callbacks.onPartial(text);
                else callbacks.onSpeech(text,json);
            });
        }catch(Exception badJson){
            // Ignore malformed isolated recognizer fragments, not an entire session.
        }
    }

    void pause(boolean value){
        paused=value;
        // Pausing discards audio; resuming is a fresh phrase after TTS.
        // The user-requested STOP remains detectable when the session is unpaused.
    }

    void stop(){
        running=false;
        listening=false;
        paused=true;
        epoch++;
        AudioRecord current=recorder;
        if(current!=null){
            try{current.stop();}catch(RuntimeException ignored){}
        }
        Thread t=worker;
        if(t!=null && t!=Thread.currentThread()){
            t.interrupt();
            try{t.join(1600);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
        }
        worker=null;
        audioStatus="Mic off • audio effects released";
    }

    void release(){
        disposed=true;
        stop();
        Model l=language;SpeakerModel sp=speaker;
        language=null;speaker=null;ready=false;
        if(sp!=null)try{sp.close();}catch(Exception ignored){}
        if(l!=null)try{l.close();}catch(Exception ignored){}
    }
}
