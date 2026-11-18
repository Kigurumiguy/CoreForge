package com.torque.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.SpeakerModel;
import org.vosk.android.SpeechService;
import org.vosk.android.RecognitionListener;

import java.io.File;
import java.io.IOException;

/** One microphone pipeline for speech AND x-vector identification. No audio is saved. */
final class VoskController {
    interface Callbacks {
        void onReady();
        void onSpeech(String text, JSONObject result);
        void onError(String explanation);
    }
    private final Context context;
    private final Callbacks callbacks;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Model language;
    private SpeakerModel speaker;
    private Recognizer recognizer;
    private SpeechService service;
    private volatile boolean ready=false;
    private volatile boolean preparing=false;
    private boolean listening=false;
    private boolean paused=false;
    private boolean disposed=false;

    VoskController(Context context, Callbacks callbacks) {
        this.context=context.getApplicationContext();
        this.callbacks=callbacks;
    }

    boolean ready(){return ready;}
    boolean listening(){return listening;}

    void prepare(){
        if(preparing||ready||disposed)return;
        preparing=true;
        new Thread(()->{
            try{
                File[] locations=VoskModels.ensureReady(context);
                Model l=new Model(locations[0].getAbsolutePath());
                SpeakerModel s;
                try{s=new SpeakerModel(locations[1].getAbsolutePath());}
                catch(Exception problem){l.close();throw problem;}
                main.post(()->{
                    if(disposed){l.close();s.close();return;}
                    language=l;speaker=s;ready=true;preparing=false;
                    callbacks.onReady();
                });
            }catch(Exception ex){
                String message=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                main.post(()->{preparing=false;if(!disposed)callbacks.onError("Offline voice engine: "+message);});
            }
        },"torque-vosk-install").start();
    }

    void start(){
        if(!ready||disposed||listening)return;
        try{
            recognizer=new Recognizer(language,16000f);
            recognizer.setSpeakerModel(speaker);
            service=new SpeechService(recognizer,16000f);
            paused=false;
            listening=true;
            service.startListening(new RecognitionListener(){
                @Override public void onPartialResult(String result){}
                @Override public void onResult(String result){ process(result); }
                @Override public void onFinalResult(String result){ process(result); }
                @Override public void onError(Exception e){
                    if(listening&&!disposed)callbacks.onError("Offline microphone: "+e.getMessage());
                }
                @Override public void onTimeout(){ }
            });
        }catch(Exception failure){
            stop();
            callbacks.onError("Offline speech cannot start: "+failure.getMessage());
        }
    }

    private void process(String raw){
        if(!listening||paused||disposed)return;
        try{
            JSONObject o=new JSONObject(raw);
            String text=o.optString("text","").trim();
            if(!text.isEmpty())callbacks.onSpeech(text,o);
        }catch(Exception ex){ callbacks.onError("Speech result was unreadable"); }
    }

    void pause(boolean shouldPause){
        paused=shouldPause;
        if(service!=null){
            service.setPause(shouldPause);
            if(!shouldPause) service.reset();
        }
    }

    void stop(){
        listening=false;paused=true;
        if(service!=null){
            service.cancel();
            service.shutdown();
            service=null;
        }
        if(recognizer!=null){recognizer.close();recognizer=null;}
    }

    void release(){
        disposed=true;
        stop();
        if(speaker!=null){speaker.close();speaker=null;}
        if(language!=null){language.close();language=null;}
        ready=false;
    }
}
