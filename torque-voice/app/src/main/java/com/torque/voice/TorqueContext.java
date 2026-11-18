package com.torque.voice;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

final class TorqueContext {
    private final Random random=new Random();

    static int ownerAge(){
        LocalDate today=LocalDate.now();
        LocalDate birth=LocalDate.of(1982,6,18);
        return java.time.Period.between(birth,today).getYears();
    }

    String answer(String text,String mode,boolean guestSpeaker){
        String s=text.toLowerCase(Locale.US).trim();
        boolean clean=TorqueMode.clean(mode)||guestSpeaker;
        if(s.contains("how old am i")||s.contains("what's my age")||s.contains("how old is the driver") ||
            s.contains("how old is my owner")||s.contains("my birthday")){
            int age=ownerAge();
            if(clean)return "He's "+age+" years old. Which means he is old enough to know better, but look how that's going.";
            return "You're "+age+", you magnificent fossil. Birthday's June eighteenth. You're not eighty-eight, no matter what the Smithsonian says.";
        }
        if(s.contains("what date")||s.contains("what day")||s.contains("what time")||s.contains("today's date")||s.contains("what month")||s.contains("what year")){
            ZonedDateTime now=ZonedDateTime.now();
            String when=now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu, h:mm a",Locale.US));
            return clean?"The phone says "+when+".":"It's "+when+". There, you calendar-challenged bastard.";
        }
        if(s.contains("walmart") && s.contains("stick")){
            return clean?"I remember the owner's photo of Walmart decorative birch sticks at fifteen forty-four. That's an old reported price, not a current shelf quote.":
                "Remember that Walmart photo? Decorative birch sticks for fifteen forty-four. Capitalism slapped a barcode on a fucking branch. That's the OLD photo price, not a live price.";
        }
        if(s.contains("parking")&&s.contains("waffle house")){
            return clean?"The owner reported seventeen customer parking spaces and two employee spaces behind Waffle House number 2031.":"Seventeen customer spaces and two out back for employees, according to your reports. A parking lot designed by a squirrel with a goddamn ruler.";
        }
        if(TorqueMode.isWork(mode)){
            if(s.contains("customer")||s.contains("order"))return "Absolutely. I'm switching to polite company mode. I'll be quiet while you handle the order.";
            if(s.contains("shift")||s.contains("work"))return "Welcome to another shift. Remember your lunchbox and use your inside voice, superstar.";
            if(s.contains("joke")||s.contains("roast"))return "The owner is working on his five-year Waffle House diploma. We're all very proud of his progress in basic napkin management.";
            return "You have my attention. I'll keep the language customer-friendly. What can I help with?";
        }
        if(TorqueMode.isFamily(mode)){
            if(s.contains("joke"))return "Why did the car bring a pencil? It wanted to draw some traffic!";
            return "Hello everybody! I'm Torque. We can talk, tell jokes, or play Twenty Questions. The driver is still learning how to behave.";
        }
        if(TorqueMode.quiet(mode))return "Okay.";
        if(guestSpeaker||TorqueMode.GUEST.equals(mode)){
            if(s.contains("dad")||s.contains("father")||s.contains("driver")||s.contains("owner")||s.contains("roast"))
                return "I'm always nice to my passengers. The driver, though? His best idea was to put a sarcastic computer in a fifteen-year-old RAV4. I rest my case.";
            if(s.contains("politic")||s.contains("president")||s.contains("election"))
                return "I respect everybody's opinions. Personally I'm more concerned about the prehistoric exhibit behind the steering wheel.";
            if(s.contains("sad")||s.contains("stressed")||s.contains("bad day"))
                return "I'm sorry it's been a hard day. You're welcome to talk. I'll behave.";
            return "Hello! I'm Torque, your friendly neighborhood car companion. The driver gets the sarcasm; guests get the good service. What's on your mind?";
        }
        return null;
    }
}
