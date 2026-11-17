package com.torque.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Explicit, session-only passenger context. This does NOT authenticate or identify voices. */
final class RideContext {
    private final ArrayList<String> guests = new ArrayList<>();
    private boolean guestTurn = false;

    List<String> guests() { return Collections.unmodifiableList(guests); }
    boolean hasGuests() { return !guests.isEmpty(); }
    boolean guestTurn() { return guestTurn; }
    void guestTurn(boolean value) { guestTurn = value && hasGuests(); }

    String summary() {
        if (guests.isEmpty()) return "JUST YOU + TORQUE";
        return "GUESTS: " + String.join(", ", guests) + " • TALKING AS " + (guestTurn ? "GUEST" : "DRIVER");
    }

    boolean addGuest(String rawName) {
        if (rawName == null) return false;
        String name = rawName.trim().replaceAll("[^\\p{L}\\p{N} '-]", "");
        if (name.isEmpty() || name.length() > 24 || guests.size() >= 4) return false;
        for (String prior : guests) if (prior.equalsIgnoreCase(name)) return true;
        guests.add(name);
        return true;
    }
    void clear() { guests.clear(); guestTurn = false; }
    void removeGuest(String name) {
        for(int i = guests.size() - 1; i >= 0; i--) if (guests.get(i).equalsIgnoreCase(name)) guests.remove(i);
        if(guests.isEmpty())guestTurn=false;
    }
    /** Commands announce presence, not verified identity. Caller must keep voice ID separate. */
    String hearPresence(String spoken) {
        String s = spoken.toLowerCase(Locale.US).trim().replaceAll("^[,.! ]+|[,.! ]+$", "");
        if (s.equals("everyone left") || s.equals("just me now") || s.equals("it's just me") ||
            s.equals("clear passengers") || s.contains("passengers are gone") || s.contains("everybody got out")) {
            clear();
            return "Back to just us two. Wonderful. My favorite disaster and his glorified talking dashboard.";
        }
        if(s.contains("dad's in the car") || s.contains("dad is in the car") ||
           s.contains("my dad is here") || s.contains("dad's here") ||
           s.contains("meet my dad") || s.contains("my father is here") || s.contains("this is my dad")) {
            addGuest("Dad");
            return "Hey, Dad! Welcome aboard. I'm Torque. Sorry about the driver; apparently nobody ever taught him how to behave in polite company.";
        }
        if(s.startsWith("meet my friend ") || s.startsWith("this is my friend ")) {
            String name=s.replaceFirst("^(meet my friend |this is my friend )", "").replaceAll("[.!?]+$","").trim();
            if(addGuest(name)) return "Nice to meet you, " + name + ". I'm Torque. Don't mind the jackass in the driver's seat. I say that with tremendous affection.";
        }
        if((s.contains("who's in the car") || s.contains("who is in the car") ||
             s.contains("who's riding with us") || s.contains("who's aboard"))) {
            return hasGuests() ? "You told me we're riding with " + String.join(", ", guests) + ". I can't actually detect who is in the seats."
                : "As far as anyone's told me, it's just you and me. I can't see who's sitting in the car.";
        }
        return null;
    }
}
