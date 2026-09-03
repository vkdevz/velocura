package com.velocura.ai.clinical.engine;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes mixed English, Hindi, Hinglish, colloquialisms, typos, and units
 * while faithfully preserving the user's clinical meaning.
 */
@Component
public class InputNormalizer {

    private static final Pattern TEMP_FAHRENHEIT = Pattern.compile("(?i)\\b(\\d{2,3}(?:\\.\\d)?)\\s*(?:°?\\s*f(?:ahrenheit)?|\\s*temp(?:erature)?)\\b");
    private static final Pattern TEMP_CELSIUS = Pattern.compile("(?i)\\b(\\d{2}(?:\\.\\d)?)\\s*°?\\s*c(?:elsius)?\\b");
    private static final Pattern BLOOD_PRESSURE = Pattern.compile("(?i)\\b(\\d{2,3})\\s*/\\s*(\\d{2,3})\\s*(?:mm\\s*hg)?\\b");

    public static class NormalizedInput {
        private final String original;
        private final String normalized;
        private final String extractedVitals;

        public NormalizedInput(String original, String normalized, String extractedVitals) {
            this.original = original;
            this.normalized = normalized;
            this.extractedVitals = extractedVitals;
        }

        public String getOriginal() { return original; }
        public String getNormalized() { return normalized; }
        public String getExtractedVitals() { return extractedVitals; }
    }

    public NormalizedInput normalize(String input) {
        if (input == null || input.isBlank()) {
            return new NormalizedInput("", "", "");
        }

        String raw = input.trim();
        String processed = " " + raw.toLowerCase() + " ";

        // 1. Common clinical Hinglish & Hindi transliterations
        processed = processed.replaceAll("(?i)\\bbukhar\\b", "fever");
        processed = processed.replaceAll("(?i)\\bbujaar\\b", "fever");
        processed = processed.replaceAll("(?i)\\bfevr\\b", "fever");
        processed = processed.replaceAll("(?i)\\byestredy\\b", "yesterday");
        processed = processed.replaceAll("(?i)\\bkal\\s*se\\b", "since yesterday");
        processed = processed.replaceAll("(?i)\\baaj\\s*se\\b", "since today");
        processed = processed.replaceAll("(?i)\\bparso\\b", "two days ago");
        processed = processed.replaceAll("(?i)\\bpet\\s*(?:mein)?\\s*dard\\b", "stomach pain");
        processed = processed.replaceAll("(?i)\\bpet\\s*kharab\\b", "upset stomach");
        processed = processed.replaceAll("(?i)\\bsar\\s*(?:mein)?\\s*dard\\b", "headache");
        processed = processed.replaceAll("(?i)\\bsir\\s*(?:mein)?\\s*dard\\b", "headache");
        processed = processed.replaceAll("(?i)\\bdard\\b", "pain");
        processed = processed.replaceAll("(?i)\\bkhansi\\b", "cough");
        processed = processed.replaceAll("(?i)\\bulti\\b", "vomiting");
        processed = processed.replaceAll("(?i)\\bdast\\b", "diarrhea");
        processed = processed.replaceAll("(?i)\\bgardan\\b", "neck");
        processed = processed.replaceAll("(?i)\\bchakkar\\b", "dizziness");
        processed = processed.replaceAll("(?i)\\bkamjori\\b", "weakness");
        processed = processed.replaceAll("(?i)\\bthakan\\b", "fatigue");
        processed = processed.replaceAll("(?i)\\bsujan\\b", "swelling");
        processed = processed.replaceAll("(?i)\\bkhoon\\b", "blood");
        processed = processed.replaceAll("(?i)\\bsaans\\s*lene\\s*m(?:ein)?\\s*dikkat\\b", "difficulty breathing");
        processed = processed.replaceAll("(?i)\\bsaans\\s*phool\\s*(?:rahi|raha)?\\b", "shortness of breath");
        processed = processed.replaceAll("(?i)\\bchhati\\s*(?:mein)?\\s*dard\\b", "chest pain");
        processed = processed.replaceAll("(?i)\\bjalan\\b", "burning");
        processed = processed.replaceAll("(?i)\\bkhujli\\b", "itching");

        // Hindi Script (Devanagari) basics
        processed = processed.replaceAll("बुखार", "fever");
        processed = processed.replaceAll("दर्द", "pain");
        processed = processed.replaceAll("खांसी", "cough");
        processed = processed.replaceAll("उल्टी", "vomiting");
        processed = processed.replaceAll("सांस", "breath");

        // Colloquial fillers
        processed = processed.replaceAll("(?i)\\b(bhai|yaar|ji|kya\\s*kru|kya\\s*karein|hai\\s*na|plz|pls)\\b", " ");

        // 2. Fuzzy spelling correction for clinical terms (e.g. fingr -> finger, urnie -> urine)
        String[] tokens = processed.split("\\s+");
        StringBuilder reconstructed = new StringBuilder(" ");
        for (String tok : tokens) {
            String cleanTok = tok.replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (cleanTok.length() >= 4 && !MEDICAL_CORRECTION_VOCABULARY.contains(cleanTok)) {
                String bestMatch = tok;
                int minDistance = Integer.MAX_VALUE;
                for (String vocab : MEDICAL_CORRECTION_VOCABULARY) {
                    if (cleanTok.charAt(0) != vocab.charAt(0)) continue;
                    int dist = levenshteinDistance(cleanTok, vocab);
                    int threshold = (cleanTok.length() <= 4) ? 1 : 2;
                    if (dist <= threshold && dist < minDistance) {
                        minDistance = dist;
                        bestMatch = vocab;
                    }
                }
                reconstructed.append(bestMatch).append(" ");
            } else if (!tok.isBlank()) {
                reconstructed.append(tok).append(" ");
            }
        }
        processed = reconstructed.toString();

        // 3. Unit and Vitals Normalization
        StringBuilder vitalsFound = new StringBuilder();

        Matcher tempFMatcher = TEMP_FAHRENHEIT.matcher(raw);
        if (tempFMatcher.find()) {
            double val = Double.parseDouble(tempFMatcher.group(1));
            // Only consider reasonable human body temperatures
            if (val >= 95.0 && val <= 108.0) {
                vitalsFound.append("Temperature: ").append(val).append("°F; ");
            }
        }

        Matcher tempCMatcher = TEMP_CELSIUS.matcher(raw);
        if (tempCMatcher.find()) {
            double val = Double.parseDouble(tempCMatcher.group(1));
            if (val >= 35.0 && val <= 43.0) {
                vitalsFound.append("Temperature: ").append(val).append("°C; ");
            }
        }

        Matcher bpMatcher = BLOOD_PRESSURE.matcher(raw);
        if (bpMatcher.find()) {
            int sys = Integer.parseInt(bpMatcher.group(1));
            int dia = Integer.parseInt(bpMatcher.group(2));
            if (sys >= 60 && sys <= 260 && dia >= 40 && dia <= 160) {
                vitalsFound.append("Blood Pressure: ").append(sys).append("/").append(dia).append(" mmHg; ");
            }
        }

        String cleaned = processed.replaceAll("\\s+", " ").trim();
        return new NormalizedInput(raw, cleaned, vitalsFound.toString().trim());
    }

    private static final List<String> MEDICAL_CORRECTION_VOCABULARY = List.of(
        "finger", "thumb", "toe", "foot", "feet", "hand", "head", "headache", "stomach", "urine",
        "throat", "cough", "fever", "bleed", "bleeding", "wound", "sprain", "tooth", "teeth", "chest",
        "ankle", "knee", "wrist", "elbow", "shoulder", "burn", "burning", "numbness", "dizziness"
    );

    private static int levenshteinDistance(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int lenA = a.length();
        int lenB = b.length();
        if (Math.abs(lenA - lenB) > 2) return 3;

        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) prev[j] = j;

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, lenB + 1);
        }
        return prev[lenB];
    }
}
