package com.torque.voice;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in, local-only x-vector speaker comparison.
 * This is NOT authentication or security identity verification.
 * Unknown/uncertain voices are deliberately NOT classified as owner.
 */
final class SpeakerProfile {
    static final int TARGET_SAMPLES = 3;
    private static final int MIN_FRAMES = 70;
    private static final int MIN_DIM = 64;
    private static final String KEY = "owner_voiceprint_v1";

    private final SharedPreferences prefs;
    private final ArrayList<double[]> enrollment = new ArrayList<>();
    private double threshold = 0.69;

    SpeakerProfile(Context c) {
        prefs = c.getSharedPreferences("torque_voiceprints", Context.MODE_PRIVATE);
        try {
            String stored = prefs.getString(KEY, "");
            if (!stored.isEmpty()) {
                JSONObject obj = new JSONObject(stored);
                JSONArray all = obj.getJSONArray("samples");
                for (int i=0;i<all.length();i++) {
                    JSONArray a=all.getJSONArray(i);
                    double[] v=new double[a.length()];
                    for (int k=0;k<v.length;k++) v[k]=a.getDouble(k);
                    if (usableVector(v)) enrollment.add(unit(v));
                }
                threshold=obj.optDouble("threshold",0.69);
            }
        } catch (Exception broken) {
            enrollment.clear();
        }
    }

    boolean enrolled() { return enrollment.size() >= TARGET_SAMPLES; }
    int samples() { return enrollment.size(); }
    void erase() { enrollment.clear(); prefs.edit().remove(KEY).apply(); }

    static double[] fromResult(JSONObject result) {
        if (result == null || result.optInt("spk_frames",0) < MIN_FRAMES) return null;
        JSONArray a=result.optJSONArray("spk");
        if (a == null || a.length()<MIN_DIM) return null;
        try {
            double[] v=new double[a.length()];
            for(int i=0;i<v.length;i++)v[i]=a.getDouble(i);
            return usableVector(v)?unit(v):null;
        } catch(JSONException ignored){return null;}
    }

    boolean addEnrollment(JSONObject result) {
        double[] v=fromResult(result);
        if(v==null || enrollment.size()>=TARGET_SAMPLES)return false;
        if(!enrollment.isEmpty() && enrollment.get(0).length!=v.length)return false;
        // Reject wildly inconsistent recordings; require new samples in a quiet environment.
        if(!enrollment.isEmpty() && similarity(v, enrollment.get(0))<0.45)return false;
        enrollment.add(v);
        if(enrolled()){
            double lowest=1;
            for(int i=0;i<enrollment.size();i++){
                for(int j=i+1;j<enrollment.size();j++)
                    lowest=Math.min(lowest, similarity(enrollment.get(i), enrollment.get(j)));
            }
            // Personal threshold with a conservative minimum. Never pretend it's security-grade.
            threshold=Math.max(0.62, Math.min(0.82, lowest-0.12));
        }
        persist();
        return true;
    }

    enum Identity { OWNER, UNKNOWN }
    Identity classify(JSONObject result) {
        if(!enrolled())return Identity.UNKNOWN;
        double[] v=fromResult(result);
        if(v==null || v.length!=enrollment.get(0).length)return Identity.UNKNOWN;
        double best=-1, sum=0;
        for(double[] p:enrollment){double s=similarity(v,p);sum+=s;best=Math.max(best,s);}
        double mean=sum/enrollment.size();
        return mean >= threshold && best >= threshold + .02 ? Identity.OWNER : Identity.UNKNOWN;
    }

    String diagnostic(JSONObject result) {
        if(!enrolled())return "Not enrolled";
        double[] v=fromResult(result);
        if(v==null || v.length!=enrollment.get(0).length)return "Uncertain: too little clear speech";
        double sum=0;for(double[] p:enrollment)sum+=similarity(v,p);
        return String.format(java.util.Locale.US,"Voice match score %.2f (cutoff %.2f) — not authentication",sum/enrollment.size(),threshold);
    }

    private void persist(){
        JSONArray all=new JSONArray();
        for(double[] v:enrollment){JSONArray arr=new JSONArray();for(double x:v)arr.put(x);all.put(arr);}
        JSONObject root=new JSONObject();
        try{root.put("samples",all);root.put("threshold",threshold);}
        catch(JSONException ignored){return;}
        prefs.edit().putString(KEY,root.toString()).apply();
    }

    private static boolean usableVector(double[] v){
        if(v==null || v.length<MIN_DIM)return false;
        double n=0;for(double x:v){if(Double.isNaN(x)||Double.isInfinite(x))return false;n+=x*x;}
        return n>1e-7;
    }
    private static double[] unit(double[] v){
        double n=0;for(double x:v)n+=x*x;
        n=Math.sqrt(n);
        double[] out=new double[v.length];for(int i=0;i<v.length;i++)out[i]=v[i]/n;
        return out;
    }
    private static double similarity(double[] a,double[] b){
        if(a.length!=b.length)return -1;
        double v=0;for(int i=0;i<a.length;i++)v+=a[i]*b[i];
        return v;
    }
}
