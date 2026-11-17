package com.torque.voice;

import android.location.Location;
import android.os.SystemClock;
import java.util.List;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

final class ScriptedTorque {
    private final Random random = new Random();
    private final MemoryDB memory;
    private String latestTopic = "";
    private int replies = 0;
    ScriptedTorque(MemoryDB db) { memory = db; }

    private String pick(String... choices) { return choices[random.nextInt(choices.length)]; }

    String respond(String message, Location gps, long fixElapsed, boolean passenger, boolean quiet, RideContext ride) {
        String input = message == null ? "" : message.trim();
        String s = input.toLowerCase(Locale.US);
        replies++;
        ZonedDateTime current = ZonedDateTime.now();
        if (s.contains("what time is it") || s.contains("what's the time") ||
            s.contains("tell me the time") || s.contains("current time") ||
            s.contains("what time do you have")) {
            return pick("It's " + current.format(DateTimeFormatter.ofPattern("h:mm a",Locale.US)) + ". I checked the phone, not the stars.",
                "Phone says " + current.format(DateTimeFormatter.ofPattern("h:mm a",Locale.US)) + ". Just in case your eyeballs forgot how clocks work.");
        }
        if (s.contains("what day is it") || s.contains("what day of the week") ||
            s.contains("what day of the month") || s.contains("what's today's date") ||
            s.contains("what is today's date") || s.contains("what date is it") ||
            s.contains("what is the date") || s.contains("what's the date") ||
            s.contains("what month is it") || s.contains("what year is it") ||
            s.equals("what's today") || s.equals("what is today")) {
            return "Today is " + current.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu",Locale.US)) +
                ". Local time's " + current.format(DateTimeFormatter.ofPattern("h:mm a",Locale.US)) +
                ". Straight from your phone's clock, you calendar-challenged jackass.";
        }
        if (ride != null && ride.guestTurn()) {
            if (s.contains("stop") || s.contains("quiet") || s.contains("not funny"))
                return "Of course. I'll behave. Tell me what you'd like to talk about.";
            if (s.contains("sad") || s.contains("upset") || s.contains("bad day") || s.contains("tired"))
                return "That sounds rough. I'm happy to listen. And I promise to keep my mouth in neutral.";
            if (s.startsWith("remember that ") || s.contains("forget the last") || s.contains("forget that memory"))
                return "I'm not changing the driver's saved memories while guest conversation is selected.";
            if (s.contains("driver") || s.contains("how is he") || s.contains("tell me about him") ||
                s.contains("what's he like") || s.contains("roast him") || s.contains("tell me about your owner"))
                return pick("Oh, he's a good guy underneath all that chaos. But he treats Walmart like a recreational sport.",
                    "He's my favorite jackass. Don't tell him I said favorite; he'll get a big head.",
                    "He's a solid human being. His decision-making at a drive-through, on the other hand? A national emergency.");
            return pick("Nice to have you aboard. I reserve my worst insults for the driver; he's had years to earn them.",
                "Oh, absolutely. I'm the car's designated smartass, but the guest seats get premium service.",
                "Fair point. If he starts acting up, I'll give him hell for both of us.");
        }
        if (ride != null && ride.hasGuests() &&
            (s.contains("roast me") || s.contains("make fun of me") ||
             s.contains("tell dad about me") || s.contains("tell my dad about me"))) {
            return pick("Dad, I'm sorry you have to put up with this magnificent jackass. I've got no choice; I'm bolted into his life.",
                "Ladies and gentlemen, your driver: a man who can survive Waffle House but not a simple shopping list.",
                "Folks, I'm obligated to report that the driver has excellent taste in cars and spectacularly questionable taste in everything else.");
        }
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
        if (ride != null && ride.hasGuests() && (s.contains("joke") || s.contains("make me laugh"))) {
            return pick("The driver once asked me to find the car keys. I said, buddy, they're in the ignition. We're already driving.",
                "Why do I love my passengers? Because statistically at least one of you knows how to read a parking sign.",
                "I'd tell you a joke about my owner, but then I'd have to call it a documentary.");
        }
        if (s.contains("joke") || s.contains("make me laugh")) {
            return pick("My owner's so bad at parking, the GPS asks if we've been kidnapped.",
                "You know what I call a perfect road trip? One where nobody puts a sticky milkshake in my cupholder.",
                "A Waffle House customer walked into a bar. The bartender said, 'You can tip me, you know.' The customer immediately left.");
        }
        if (ride != null && ride.hasGuests() &&
            (s.contains("hello") || s.contains("hey torque") || s.contains("good morning") || s.contains("hi torque"))) {
            return pick("Hey everybody! Lovely having company. Driver, try not to embarrass me in front of the nice people.",
                "Well, well. We have guests! Welcome aboard. I promise only the driver gets the premium asshole experience.",
                "Hello, distinguished passengers! And hello to the jackass with the keys. Where are we off to?");
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
