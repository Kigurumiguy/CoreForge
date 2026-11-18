package com.torque.voice;

import java.util.Locale;

/** Safe-by-default speech contexts, separate from speaker identity. */
final class TorqueMode {
    static final String CAR="Private RAV4";
    static final String HOME="Home / Hangout";
    static final String GUEST="Passenger";
    static final String WORK="Workplace / Waffle House";
    static final String FAMILY="Kids / Family";
    static final String QUIET="Quiet";
    static final String[] ALL={CAR,HOME,GUEST,WORK,FAMILY,QUIET};
    private TorqueMode(){}

    static boolean valid(String m){
        if(m==null)return false;
        for(String s:ALL)if(s.equals(m))return true;
        return false;
    }
    static boolean clean(String m){return !CAR.equals(m)&&!HOME.equals(m);}
    static boolean privateSpace(String m){return CAR.equals(m)||HOME.equals(m);}
    static boolean quiet(String m){return QUIET.equals(m);}
    static boolean isWork(String m){return WORK.equals(m);}
    static boolean isFamily(String m){return FAMILY.equals(m);}
    static String normalize(String m){return valid(m)?m:GUEST;}

    /** Recognize explicit commands. Private context requires user confirmation in UI. */
    static String command(String words){
        if(words==null)return null;
        String s=words.toLowerCase(Locale.US).replaceAll("[^a-z ]"," ").trim().replaceAll(" +"," ");
        if(s.contains("torque behave")||s.equals("behave torque"))return GUEST;
        if(s.contains("we re at work")||s.contains("torque work mode")||s.contains("we are at work"))return WORK;
        if(s.contains("kids are here")||s.contains("family mode")||s.contains("torque kids mode"))return FAMILY;
        if(s.contains("torque quiet mode")||s.contains("torque stay quiet"))return QUIET;
        if(s.contains("torque passenger mode"))return GUEST;
        if(s.contains("we re home")||s.contains("we are home")||s.contains("torque home mode"))return HOME;
        if(s.contains("torque off the clock")||s.contains("torque we re alone")||s.contains("torque we are alone")||s.contains("torque private mode"))return CAR;
        return null;
    }
    static boolean stopNow(String words){
        if(words==null)return false;
        String s=words.toLowerCase(Locale.US).replaceAll("[^a-z ]"," ").trim().replaceAll(" +"," ");
        return s.equals("torque enough")||s.equals("enough torque")||
            s.equals("stop torque")||s.equals("torque stop")||
            s.equals("mute torque")||s.equals("torque mute")||
            s.equals("torque stop listening")||s.equals("torque shut up")||
            s.equals("torque stop talking");
    }
}
