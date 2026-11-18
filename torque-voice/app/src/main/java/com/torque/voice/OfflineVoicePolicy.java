package com.torque.voice;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;
import java.util.Set;

/**
 * Never silently choose a network-based TTS voice for Torque.
 * Actual offline playback still needs a real airplane-mode phone test.
 */
final class OfflineVoicePolicy {
    static final class Selection {
        final boolean ready;
        final String status;
        final String voiceName;
        Selection(boolean ready,String status,String voiceName) {
            this.ready=ready;this.status=status;this.voiceName=voiceName;
        }
    }
    private OfflineVoicePolicy(){}

    static Selection apply(TextToSpeech tts,String preferred) {
        if(tts==null)return new Selection(false,"Phone TTS unavailable","none");
        Set<Voice> voices=tts.getVoices();
        if(voices==null||voices.isEmpty())
            return new Selection(false,"No TTS voices installed. Install an offline English voice in Android TTS settings.","none");

        Voice selected=null;
        int bestScore=Integer.MIN_VALUE;
        for(Voice v:voices){
            if(v==null||v.getLocale()==null||v.isNetworkConnectionRequired())continue;
            if(!"en".equalsIgnoreCase(v.getLocale().getLanguage()))continue;
            if(preferred!=null&&!preferred.isEmpty()&&preferred.equals(v.getName())){
                selected=v;break;
            }
            int score=(Locale.US.getCountry().equalsIgnoreCase(v.getLocale().getCountry())?100:0);
            score+=v.getQuality();
            if(selected==null||score>bestScore){selected=v;bestScore=score;}
        }
        if(selected==null)
            return new Selection(false,"No offline English voice available. Install a downloaded English voice before your drive.","none");
        if(tts.setVoice(selected)!=TextToSpeech.SUCCESS)
            return new Selection(false,"The selected offline voice failed to load. Try another phone TTS voice.","none");
        Voice actual=tts.getVoice();
        if(actual==null||actual.isNetworkConnectionRequired())
            return new Selection(false,"Phone TTS selected a network-based voice; offline speech disabled.","none");
        return new Selection(true,
            "Offline-capable English voice selected (airplane-mode test still needed)",
            actual.getName());
    }
}
