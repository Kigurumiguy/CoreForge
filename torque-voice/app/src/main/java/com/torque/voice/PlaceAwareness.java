package com.torque.voice;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Foreground only: explicit owner-pinned locations, no hardcoded coordinates,
 * conservative arrival dwell, accuracy and speed checks and an event cooldown.
 */
final class PlaceAwareness {
    static final class Place {
        final String name, kind;
        final double latitude, longitude;
        final float radius;
        Place(String n,String type,double lat,double lon,float r){
            name=n;kind=type;latitude=lat;longitude=lon;radius=r;
        }
    }
    interface Listener { void onArrival(Place place); }

    private final SharedPreferences prefs;
    private final ArrayList<Place> saved=new ArrayList<>();
    private String candidateName="";
    private long candidateSince=0;
    private String lastArrivalName="";
    private long lastArrivalAt=0;
    private final long REQUIRED_DWELL_MS=25000;
    private final long COOLDOWN_MS=20*60*1000;
    private boolean armed=true;

    PlaceAwareness(Context c){
        prefs=c.getSharedPreferences("torque_places",Context.MODE_PRIVATE);
        try{
            JSONArray all=new JSONArray(prefs.getString("places","[]"));
            for(int i=0;i<all.length();i++){
                JSONObject j=all.getJSONObject(i);
                String name=j.optString("name","");
                String kind=j.optString("kind","Other");
                if(!name.isEmpty()&&saved.size()<20)
                    saved.add(new Place(name,kind,j.getDouble("lat"),j.getDouble("lon"),(float)j.optDouble("radius",75)));
            }
        }catch(Exception ignored){saved.clear();}
    }
    List<Place> places(){return new ArrayList<>(saved);}
    void setArmed(boolean value){armed=value;}
    void clear(){saved.clear();candidateName="";persist();}
    void remove(int position){if(position<0||position>=saved.size())return;saved.remove(position);candidateName="";persist();}
    boolean pin(String name,String kind,Location location){
        if(name==null||name.trim().isEmpty()||name.length()>42||location==null||saved.size()>=20)return false;
        if(!location.hasAccuracy()||location.getAccuracy()>40)return false;
        // Do not allow saving a pin while the phone is moving.
        if(location.hasSpeed()&&location.getSpeed()>1.5f)return false;
        saved.add(new Place(name.trim(),kind,location.getLatitude(),location.getLongitude(),75));
        persist();
        return true;
    }
    private void persist(){
        JSONArray all=new JSONArray();
        try{
            for(Place p:saved){
                JSONObject j=new JSONObject();
                j.put("name",p.name);j.put("kind",p.kind);j.put("lat",p.latitude);j.put("lon",p.longitude);j.put("radius",p.radius);
                all.put(j);
            }
            prefs.edit().putString("places",all.toString()).apply();
        }catch(JSONException ignored){}
    }
    void sample(Location position,Listener notify){
        if(!armed||position==null||!position.hasAccuracy()||position.getAccuracy()>45)return;
        long now=SystemClock.elapsedRealtime();
        if(position.hasSpeed()&&position.getSpeed()>2.5f){candidateName="";candidateSince=0;return;}
        Place nearby=null;
        float best=Float.MAX_VALUE;
        for(Place p:saved){
            Location target=new Location("saved_pin");
            target.setLatitude(p.latitude);target.setLongitude(p.longitude);
            float distance=position.distanceTo(target);
            if(distance<=p.radius && distance<best){best=distance;nearby=p;}
        }
        if(nearby==null){candidateName="";candidateSince=0;return;}
        if(!candidateName.equals(nearby.name)){
            candidateName=nearby.name;candidateSince=now;return;
        }
        if(now-candidateSince<REQUIRED_DWELL_MS)return;
        if(lastArrivalName.equals(nearby.name)&&now-lastArrivalAt<COOLDOWN_MS)return;
        lastArrivalName=nearby.name;lastArrivalAt=now;
        notify.onArrival(nearby);
    }
}
