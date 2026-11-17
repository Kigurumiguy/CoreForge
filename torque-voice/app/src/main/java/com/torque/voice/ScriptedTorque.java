package com.torque.voice;

import android.location.Location;
import android.os.SystemClock;
import java.util.List;
import java.util.Locale;
import java.util.Random;

final class ScriptedTorque {
    private final Random random = new Random();
    private final MemoryDB memory;
    private String latestTopic = "";
    private int replies = 0;
    ScriptedTorque(MemoryDB db) { memory = db; }

    private String pick(String... choices) { return choices[random.nextInt(choices.length)]; }

    String respond(String message, Location gps, long fixElapsed, boolean passenger, boolean quiet) {
        String input = message == null ? "" : message.trim();
        String s = input.toLowerCase(Locale.US);
        replies++;
        if (s.startsWith("remember that ")) {
            String fact = input.substring("remember that ".length()).trim();
            memory.remember(fact);
            return pick("Got it. I shoved that into my tiny mechanical brain.", "Noted. Unlike most people, I actually listen.", "All right, stored locally. No corporate spies required.");
        }
        if (s.contains("forget the last") || s.contains("forget that memory")) {
            memory.forgetLatest();
            return "Last saved memory deleted. Poof. Like your paycheck at Walmart.";
        }
        if (s.contains("what do you remember") || s.contains("what do you know about me")) {
            List<String> notes = memory.facts();
            if (notes.isEmpty()) return "I know you work at Waffle House, hit five years in January 2027, and somehow willingly drive around with me. You haven't saved anything else yet.";
            return "Here's one thing you told me to remember: " + notes.get(0) + ". I've got " + notes.size() + " saved notes.";
        }
        if (s.contains("where am i") || s.contains("where are we") || s.contains("how fast") || s.contains("speed") || s.contains("gps")) {
            if (gps == null || SystemClock.elapsedRealtime() - fixElapsed > 45000) {
                return "GPS hasn't got a fresh fix yet. I can't see roads through the windshield, genius.";
            }
            if (s.contains("fast") || s.contains("speed")) {
                if (!gps.hasSpeed()) return "I have a location fix, but the phone hasn't reported a reliable speed. Not making one up.";
                int mph = Math.round(gps.getSpeed() * 2.2369363f);
                return "Phone GPS says about " + mph + " miles an hour. That's a sensor reading, not me judging your magnificent driving.";
            }
            return "Phone GPS has coordinates, but I don't have offline street maps to name this road. I'm a car companion, not a psychic traffic helicopter.";
        }
        if (s.contains("walmart") || s.contains("wal-mart")) {
            latestTopic = "shopping";
            return pick("Oh, wonderful. The Walmart parking lot. Nature's finest documentary about terrible decisions.",
                "Walmart again? You really know how to show a sophisticated machine a good time.",
                "Fine, Walmart. But if you bring back another thing you don't need, I'm filing for custody of the glove box.");
        }
        if (s.contains("waffle house") || s.contains("customer") || s.contains("shift") || s.contains("tip") || s.contains("work")) {
            latestTopic = "work";
            if (s.contains("five year") || s.contains("anniversary")) return "January 2027. Five years at Waffle House. I expect a parade, a cake, and at least one customer who knows how to tip.";
            return pick("Another exciting episode of Everybody's an Asshole and Nobody Tips. Tell me what happened.",
                "Oh boy, Waffle House stories. Did someone invent a new way to make your shift miserable?",
                "You survived another shift? I'm proud of you, you exhausted magnificent bastard. Want to vent?");
        }
        if (s.contains("bad day") || s.contains("rough day") || s.contains("sad") || s.contains("upset") || s.contains("tired") || s.contains("stressed")) {
            latestTopic = "comfort";
            return pick("Buddy, sounds like today sucked. I can shut the jokes off for a bit. Want to talk?",
                "I'm listening. No bullshit, no punchline. What's weighing on you?",
                "Okay. I'm putting the smartass in neutral. You've got me. Talk.");
        }
        if (s.contains("passenger") || s.contains("friend is") || s.contains("meet my")) {
            latestTopic = "passenger";
            return "Welcome aboard. I'm Torque, the brains of this 2010 RAV4. The driver is my favorite ongoing repair project. No offense intended, unless you both laugh.";
        }
        if (s.contains("stop joking") || s.contains("be serious") || s.contains("not funny")) {
            return "Fair enough. I'll knock off the jokes. Talk to me.";
        }
        if (s.contains("shut up") || s.equals("quiet") || s.contains("stop talking")) {
            return "All right. I'll shut my beautifully sarcastic mouth until you call me.";
        }
        if (s.contains("traffic") || s.contains("slow driver") || s.contains("turn signal") || s.contains("parking")) {
            latestTopic = "driving";
            return pick("Based on your description, that sounds like a spectacular display of automotive stupidity. I'm not pretending I can see it.",
                "You describe traffic like a nature documentary starring confused shopping carts.",
                "I can't see the other cars, but good grief, that sounds annoying. Keep your cool; we've got places to be.");
        }
        if (s.contains("joke") || s.contains("make me laugh")) {
            return pick("My owner's so bad at parking, the GPS asks if we've been kidnapped.",
                "You know what I call a perfect road trip? One where nobody puts a sticky milkshake in my cupholder.",
                "A Waffle House customer walked into a bar. The bartender said, 'You can tip me, you know.' The customer immediately left.");
        }
        if (s.contains("hello") || s.contains("hey torque") || s.contains("good morning") || s.contains("hi torque")) {
            return pick("Well, look who dragged his ass back into my driver's seat. What's going on?",
                "There he is. My favorite pain in the transmission. Where are we headed?",
                "Torque online, sarcasm calibrated, brakes entirely your responsibility. Talk to me.");
        }
        if (s.contains("love you") || s.contains("thanks")) return pick("Love you too, asshole.", "You're welcome. I'll add it to your tab.", "Aww. Don't make me leak transmission fluid.");
        if (s.contains("music") || s.contains("radio") || s.contains("song")) {
            latestTopic = "music";
            return "I'm up for good music. Just don't ask me to DJ your entire life with the same four songs. What's playing?";
        }
        if (s.contains("birthday")) return "A birthday? Hell yes. I'll bring the cake jokes; you bring the actual cake.";
        if (s.contains("road trip") || s.contains("drive to michigan")) {
            latestTopic = "trip";
            return "Now we're talking. Road trip in the old RAV4. I'll provide the insults and moral support. You handle the actual steering.";
        }
        if (passenger) {
            return pick("Well, that's a story. I've got a sharp tongue, but I do know how to listen. Go on.",
                "Interesting. If this driver's giving you a hard time, say the word and I'll roast him instead.",
                "You've got my attention. I'm the least judgmental sarcastic piece of automotive equipment you'll ever meet.");
        }
        if (quiet) return "Got it.";
        if (s.contains("?")) {
            return pick("Good question. My little offline brain doesn't know everything, but I'm listening. What makes you ask?",
                "I can't pull an answer out of my exhaust pipe, but I'll think out loud with you. Tell me more.",
                "You've got a point. And I have four wheels and an attitude problem. What's your take?");
        }
        String prior = latestTopic;
        latestTopic = s.length() > 24 ? s.substring(0, 24) : s;
        if ("work".equals(prior)) return pick("Jesus, your job really never runs out of plot twists. And then what happened?", "Were they at least decent enough to tip after all that?");
        if ("comfort".equals(prior)) return pick("I'm still listening. No rush.", "You don't have to make that sound smaller than it feels. Tell me what happened.");
        return pick("Wait, wait. Back up a second. You can't just drop that on me and drive off. Tell me the rest.",
            "I live in a RAV4, and you're still the most interesting thing happening in here. Go on.",
            "All right, you've got my attention. Continue before my curiosity blows a head gasket.",
            "That sounds like the beginning of a hell of a story. What happened next?");
    }
}
