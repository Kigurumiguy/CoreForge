package com.torque.voice;

import java.util.Locale;

/** Non-diagnostic driver fatigue safety phrases; not a replacement for sleep. */
final class DriveSafety {
    private DriveSafety(){}

    static boolean urgent(String said) {
        if(said==null)return false;
        String s=said.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]"," ").replaceAll(" +"," ");
        return contains(s,"falling asleep")||contains(s,"about to fall asleep")||
            contains(s,"can't stay awake")||contains(s,"cannot stay awake")||
            contains(s,"cant stay awake")||contains(s,"nodding off")||
            contains(s,"dozing off")||contains(s,"keep my eyes open")||
            contains(s,"eyes keep closing")||contains(s,"almost fell asleep")||
            contains(s,"driving drowsy")||contains(s,"microsleep");
    }

    static boolean tired(String said) {
        if(said==null)return false;
        String s=said.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]"," ").replaceAll(" +"," ");
        return contains(s,"really sleepy")||contains(s,"getting sleepy")||
            contains(s,"so tired")||contains(s,"exhausted driving")||
            contains(s,"tired driving")||contains(s,"need to sleep");
    }

    static String response(String said) {
        if(urgent(said))return "Hey. Seriously, pull over somewhere safe as soon as you can and rest. No joke is worth nodding off behind the wheel. I'll be here when you're parked and awake.";
        if(tired(said))return "Sounds like you're getting worn out. If you're sleepy behind the wheel, find somewhere safe to stop and rest. Conversation isn't a substitute for sleep, buddy.";
        return null;
    }
    private static boolean contains(String s,String word){return s.contains(word);}
}
