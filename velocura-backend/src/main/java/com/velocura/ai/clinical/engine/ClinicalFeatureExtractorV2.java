package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.model.feature.StructuredClinicalFeature;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.FactPresence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraction Engine V2:
 * Deterministic, clause-level clinical feature extractor with generalized relationship binding.
 *
 * Guarantees:
 * 1. Clause segmentation prevents cross-pollination of temporal, negation, and laterality modifiers.
 * 2. Tri-state presence is strictly preserved: PRESENT, ABSENT_DENIED, UNKNOWN.
 * 3. Generalized attributes: character/quality, anatomy, strict laterality, onset, duration, progression.
 * 4. Relationship binding: radiation site, symptom migration (origin -> destination), relieving (better_with),
 *    aggravating (worse_with), and trigger contexts.
 * 5. Extracted features reliably enter both state.getSymptoms() and state.getNegatedFindings().
 * 6. Extraction confidence is explicitly separated from disease probability.
 */
@Slf4j
@Component
public class ClinicalFeatureExtractorV2 {

    // Clause boundary delimiters: conjunctions, commas, semicolons, sentence terminators
    // Note: avoid splitting 'and [then] moved/migrated/radiated/spread' to preserve relationship binding
    private static final Pattern CLAUSE_SPLITTER = Pattern.compile(
            "(?i)\\s*(?:\\.|;|\\bbut\\b|\\bhowever\\b|\\balthough\\b|\\bwhile\\b|\\byet\\b|,|\\n|\\band(?:\\s+then)?\\b(?!\\s*(?:moved|migrated|shifted|traveled|spread|radiat)))\\s*"
    );

    // Negation patterns within a clause
    private static final Pattern NEGATION_PATTERN = Pattern.compile(
            "(?i)\\b(no|not|without|denies|deny|denied|denying|never|neither|nor|negative\\s+for|don't\\s+have|dont\\s+have|haven't\\s+had|havent\\s+had|completely\\s+fine|is\\s+fine|feels\\s+fine|is\\s+normal|are\\s+normal|is\\s+okay|unremarkable)\\b"
    );

    // Uncertainty patterns (UNKNOWN status)
    private static final Pattern UNCERTAINTY_PATTERN = Pattern.compile(
            "(?i)\\b(not\\s+sure|unsure|maybe|might\\s+be|might\\s+have|don't\\s+know|dont\\s+know|possibly|hard\\s+to\\s+say|could\\s+be|questionable|unclear)\\b"
    );

    // Clause-bound temporal patterns
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:for|past|last|over\\s+the\\s+last|over\\s+the\\s+past)?\\s*(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|a|several|few)\\s*(days?|hours?|weeks?|months?|d|wks?|hrs?)\\b"
    );

    private static final Pattern ONSET_PATTERN = Pattern.compile(
            "(?i)\\b(started\\s*(?:suddenly|gradually|today|yesterday|recently|just\\s*now|last\\s*night|this\\s*morning|[0-9a-zA-Z\\s]+ago)|since\\s*(?:yesterday|today|last\\s*night|this\\s*morning)|began\\s*(?:suddenly|gradually|today|yesterday|recently)|today|yesterday|just\\s*started|sudden(?:ly)?|gradual(?:ly)?|acute(?:ly)?|abrupt(?:ly)?)\\b"
    );

    private static final Pattern PROGRESSION_PATTERN = Pattern.compile(
            "(?i)\\b(improved\\s*(?:for\\s*\\w+)?\\s*(?:and\\s*)?(?:then\\s*)?(?:became|got)\\s*worse|worse\\s*after\\s*(?:getting\\s*)?better|worse\\s*after\\s*improving|getting\\s*worse|worsening|became\\s*worse|more\\s*severe|getting\\s*better|improving|stable|constant|comes\\s*and\\s*goes|intermittent|persistent|persisting|continuous|resolved|gone|returned|came\\s*back|recurrent)\\b"
    );

    private static final Pattern SEVERITY_PATTERN = Pattern.compile(
            "(?i)\\b(mild|moderate|severe|critical|very\\s*severe|unbearable|slight|intense)\\b"
    );

    private static final Pattern CHARACTER_PATTERN = Pattern.compile(
            "(?i)\\b(sharp|dull|burning|pressure|cramping|cramps|throbbing|stabbing|aching|ache|colicky|crushing|tight|heaviness|heavy|gnawing)\\b"
    );

    private static final Pattern ANATOMICAL_SITE_PATTERN = Pattern.compile(
            "(?i)\\b(right\\s*lower\\s*(?:quadrant|abdomen)|left\\s*lower\\s*(?:quadrant|abdomen)|right\\s*upper\\s*(?:quadrant|abdomen)|left\\s*upper\\s*(?:quadrant|abdomen)|rlq|llq|ruq|luq|periumbilical|belly\\s*button|navel|umbilical|epigastric|epigastrium|suprapubic|flank|chest|retrosternal|throat|sinus|sinuses|maxillary|frontal|ethmoid|periorbital|orbital|face|facial|cheek|cheeks|forehead|nasal|nose|teeth|tooth|dental|ear|ears|stomach|abdomen|belly|tummy|back|lumbar|neck|cervical|head|pelvis|groin|joint|knee|ankle|shoulder|arm|leg|foot|hand|wrist|elbow)\\b"
    );

    private static final Pattern LATERALITY_PATTERN = Pattern.compile(
            "(?i)\\b(left[- ]sided|right[- ]sided|left\\s*side|right\\s*side|left|right|bilateral(?:ly)?|unilateral(?:ly)?|both\\s*(?:sides?|eyes?|ears?|knees?|nostrils?|arms?|legs?)|one[- ]sided|midline|central)\\b"
    );

    // Relationships patterns
    private static final Pattern RADIATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:radiat(?:ing|es|ed)?|shoots?|radiat(?:ion)?|spreads?)\\s*(?:to|into|down|towards?)\\s*(?:my\\s*)?([a-zA-Z\\s]+?)(?:\\s*(?:and|,|\\.|;|$|but))"
    );

    private static final Pattern MIGRATION_PATTERN_1 = Pattern.compile(
            "(?i)\\b(?:started|began)\\s*(?:in|around|at)?\\s*(?:my\\s*)?([a-zA-Z\\s]+?)\\s*(?:and|,|then)?\\s*(?:moved|migrated|shifted|traveled)\\s*to\\s*(?:my\\s*)?([a-zA-Z\\s]+?)(?:\\s*(?:and|,|\\.|;|$|but))"
    );

    private static final Pattern MIGRATION_PATTERN_2 = Pattern.compile(
            "(?i)\\b(?:moved|migrated|shifted)\\s*from\\s*(?:my\\s*)?([a-zA-Z\\s]+?)\\s*to\\s*(?:my\\s*)?([a-zA-Z\\s]+?)(?:\\s*(?:and|,|\\.|;|$|but))"
    );

    private static final Pattern BETTER_WITH_PATTERN = Pattern.compile(
            "(?i)\\b(?:better|relieved|eased|improves?|helped)\\s*(?:with|by|after|when)?\\s*(?:a\\s*|the\\s*)?([a-zA-Z\\s]+?)(?:\\s*(?:and|,|\\.|;|$|but))"
    );

    private static final Pattern WORSE_WITH_PATTERN = Pattern.compile(
            "(?i)\\b(?:worse|aggravated|increased|intensifies?)\\s*(?:with|by|after|when|on)?\\s*(?:a\\s*|the\\s*)?([a-zA-Z\\s]+?)(?:\\s*(?:and|,|\\.|;|$|but))"
    );

    private static final Pattern TRIGGER_PATTERN = Pattern.compile(
            "(?i)\\b(?:triggered\\s*by|happens\\s*after|occurs\\s*after|comes\\s*on\\s*after|following)\\s*(?:a\\s*|the\\s*)?([a-zA-Z\\s]+?)(?:\\s*(?:and|,|\\.|;|$|but))"
    );

    // ─── Concept Matchers ──────────────────────────────────────────────────────
    private static final Pattern COUGH_PATTERN = Pattern.compile(
            "(?i)\\b(cough|coughing|coughed|chesty\\s*cough|dry\\s*cough|wet\\s*cough|barking\\s*cough|hacking\\s*cough)\\b"
    );

    private static final Pattern SPUTUM_PATTERN = Pattern.compile(
            "(?i)\\b(phlegm|mucus|sputum|yellow\\s*(?:phlegm|mucus|sputum)|green\\s*(?:phlegm|mucus|sputum)|yellowish|greenish|purulent|clear\\s*(?:mucus|phlegm)|white\\s*(?:mucus|phlegm)|blood\\s*(?:in\\s*phlegm|specked)|hemoptysis)\\b"
    );

    private static final Pattern WHEEZE_PATTERN = Pattern.compile(
            "(?i)\\b(wheeze|wheezing|whistling\\s*(?:when\\s*i\\s*breathe|in\\s*chest|sound)|whistling\\s*breath|stridor)\\b"
    );

    private static final Pattern DYSPNEA_PATTERN = Pattern.compile(
            "(?i)\\b(short(?:ness)?\\s*of\\s*breath|breathless(?:ness)?|trouble\\s*breathing|difficulty\\s*breathing|hard\\s*to\\s*breathe|struggling\\s*for\\s*breath|can't\\s*catch\\s*my\\s*breath|cant\\s*catch\\s*my\\s*breath|air\\s*hunger|dyspnea)\\b"
    );

    private static final Pattern CHEST_TIGHT_PATTERN = Pattern.compile(
            "(?i)\\b(chest\\s*(?:tightness|feels\\s*tight|tight|congestion|heavy|heaviness|discomfort|soreness|pain|pressure|hurts)|pain\\s*in\\s*(?:my\\s*)?chest|tight\\s*chest)\\b"
    );

    private static final Pattern FEVER_PATTERN = Pattern.compile(
            "(?i)\\b(fever|high\\s*fever|chills|rigors|shivering|high\\s*temp(?:erature)?|temperature|febrile|pyrexia)\\b"
    );

    private static final Pattern SORE_THROAT_PATTERN = Pattern.compile(
            "(?i)\\b(sore\\s*throat|throat\\s*pain|scratchy\\s*throat|pharyngitis|red\\s*throat|pharyngeal\\s*(?:pain|erythema)|throat\\s*hurts)\\b"
    );

    private static final Pattern ODYNOPHAGIA_PATTERN = Pattern.compile(
            "(?i)\\b(painful\\s*swallowing|hurts?\\s*to\\s*swallow|hurts?\\s*when\\s*i\\s*swallow|pain\\s*when\\s*swallowing|odynophagia|pain\\s*on\\s*swallowing)\\b"
    );

    private static final Pattern DYSPHAGIA_PATTERN = Pattern.compile(
            "(?i)\\b(difficulty\\s*swallowing|hard\\s*to\\s*swallow|trouble\\s*swallowing|dysphagia)\\b"
    );

    private static final Pattern INABILITY_TO_SWALLOW_PATTERN = Pattern.compile(
            "(?i)\\b(can't\\s*swallow|cannot\\s*swallow|unable\\s*to\\s*swallow|cant\\s*swallow)\\b"
    );

    private static final Pattern FUNCTIONAL_SWALLOW_PATTERN = Pattern.compile(
            "(?i)\\b(can\\s*(?:still\\s*)?drink(?:\s*normally|\s*water)?|able\\s*to\\s*drink|swallowing\\s*is\\s*fine|can\\s*swallow\\s*liquids?|able\\s*to\\s*swallow|can\\s*drink\\s*normally)\\b"
    );

    private static final Pattern DROOLING_PATTERN = Pattern.compile(
            "(?i)\\b(drooling|drool|sialorrhea|spitting\\s*saliva|can't\\s*swallow\\s*(?:my\\s*)?saliva|cannot\\s*swallow\\s*(?:my\\s*)?saliva|unable\\s*to\\s*swallow\\s*saliva|cant\\s*swallow\\s*(?:my\\s*)?saliva)\\b"
    );

    private static final Pattern TONSILLAR_EXUDATE_PATTERN = Pattern.compile(
            "(?i)\\b(tonsillar\\s*exudate|exudate|white\\s*patches?\\s*(?:on\\s*tonsils?|in\\s*throat)|white\\s*spots?\\s*(?:on\\s*tonsils?|in\\s*throat)|pus\\s*on\\s*tonsils?|tonsillar\\s*pus)\\b"
    );

    private static final Pattern TONSILLAR_SWELLING_PATTERN = Pattern.compile(
            "(?i)\\b(tonsillar\\s*(?:swelling|enlargement|hypertrophy)|swollen\\s*tonsils?|enlarged\\s*tonsils?|red\\s*tonsils?)\\b"
    );

    private static final Pattern CERVICAL_ADENOPATHY_PATTERN = Pattern.compile(
            "(?i)\\b(tender\\s*(?:and\\s*)?(?:swollen\\s*)?(?:neck\\s*)?(?:glands|lymph\\s*nodes?)|swollen\\s*(?:and\\s*)?(?:tender\\s*)?(?:neck\\s*)?(?:glands|lymph\\s*nodes?)|swollen\\s*glands|anterior\\s*cervical\\s*(?:tenderness|nodes?|adenopathy)|painful\\s*(?:neck\\s*)?nodes?|lumps?\\s*in\\s*neck)\\b"
    );

    private static final Pattern VOICE_CHANGE_PATTERN = Pattern.compile(
            "(?i)\\b(muffled\\s*voice|hot\\s*potato\\s*voice|voice\\s*change|hoarse(?:ness)?|raspy\\s*voice|lost\\s*my\\s*voice)\\b"
    );

    private static final Pattern STRIDOR_PATTERN = Pattern.compile(
            "(?i)\\b(stridor|noisy\\s*breathing|high[- ]pitched\\s*breathing|noisy\\s*airway)\\b"
    );

    private static final Pattern HEADACHE_PATTERN = Pattern.compile(
            "(?i)\\b(headache|head\\s*pain|migraine|throbbing\\s*head|sar\\s*dard)\\b"
    );

    private static final Pattern ABDOMINAL_PAIN_PATTERN = Pattern.compile(
            "(?i)\\b(stomach\\s*ache|stomach\\s*pain|abdominal\\s*pain|belly\\s*pain|cramps|cramping|tummy\\s*ache|pet\\s*dard|pain\\s*(?:in|around)\\s*(?:my\\s*)?(?:belly\\s*button|stomach|abdomen|belly|tummy|rlq|llq|ruq|luq))\\b"
    );

    private static final Pattern NAUSEA_PATTERN = Pattern.compile(
            "(?i)\\b(nausea|nauseous|feel\\s*sick|queasy|ji\\s*ghabrana)\\b"
    );

    private static final Pattern VOMIT_PATTERN = Pattern.compile(
            "(?i)\\b(vomit|vomiting|threw\\s*up|throwing\\s*up|emesis)\\b"
    );

    private static final Pattern DIARRHEA_PATTERN = Pattern.compile(
            "(?i)\\b(diarrhea|loose\\s*motions?|watery\\s*stools?|dast)\\b"
    );

    private static final Pattern RHINORRHEA_PATTERN = Pattern.compile(
            "(?i)\\b(runny\\s*nose|nasal\\s*discharge|rhinorrhea|sneezing|congestion|stuffy\\s*nose|blocked\\s*nose)\\b"
    );

    private static final Pattern JOINT_PAIN_PATTERN = Pattern.compile(
            "(?i)\\b(joint\\s*pain|arthralgia|knee\\s*pain|shoulder\\s*pain|swollen\\s*joint|knees?\\s*(?:hurts?|ache|is\\s*fine|is\\s*normal|are\\s*fine)|shoulders?\\s*(?:hurts?|ache|is\\s*fine)|ankles?\\s*(?:hurts?|ache|is\\s*fine)|pain\\s*in\\s*(?:my\\s*)?(?:left\\s*|right\\s*|both\\s*)?(?:knees?|shoulders?|ankles?|joints?))\\b"
    );

    private static final Pattern EAR_PAIN_PATTERN = Pattern.compile(
            "(?i)\\b(ear\\s*(?:pain|ache|fullness|pressure)|earache|pain\\s*in\\s*(?:my\\s*)?(?:left\\s*|right\\s*|both\\s*)?ears?|ears?\\s*(?:hurts?|ache)|ears?\\s*is\\s*(?:completely\\s*)?(?:fine|normal|okay|clear))\\b"
    );

    private static final Pattern DIZZINESS_PATTERN = Pattern.compile(
            "(?i)\\b(dizzy|dizziness|lightheaded|lightheadedness|vertigo|chakkar)\\b"
    );

    // Rhinosinusitis & Upper Airway Patterns
    private static final Pattern FACIAL_PAIN_PATTERN = Pattern.compile(
            "(?i)\\b(facial\\s*(?:pain|pressure|fullness|tightness|heaviness|ache)|face\\s*(?:pain|pressure|fullness|ache|hurts)|sinus\\s*(?:pain|pressure|headache|fullness)|pain\\s*in\\s*(?:my\\s*)?(?:left\\s*|right\\s*|both\\s*)?(?:face|cheeks?|forehead|sinus(?:es)?|maxillary|frontal)|maxillary\\s*(?:pain|tenderness|pressure)|frontal\\s*(?:pain|pressure|headache)|pressure\\s*in\\s*(?:my\\s*)?(?:left\\s*|right\\s*|both\\s*)?(?:face|cheeks?|forehead|sinuses)|cheeks?\\s*(?:hurt|pain|pressure)|pain\\s*under\\s*(?:my\\s*)?eyes?|pressure\\s*under\\s*(?:my\\s*)?eyes?)\\b"
    );

    private static final Pattern NASAL_CONGESTION_PATTERN = Pattern.compile(
            "(?i)\\b(nasal\\s*(?:congestion|obstruction|blockage|block)|stuffy\\s*nose|blocked\\s*nose|congested\\s*nose|nose\\s*(?:is\\s*)?(?:blocked|stuffed|clogged)|congestion|cannot\\s*breathe\\s*through\\s*(?:my\\s*)?nose|can't\\s*breathe\\s*through\\s*(?:my\\s*)?nose)\\b"
    );

    private static final Pattern NASAL_DISCHARGE_PATTERN = Pattern.compile(
            "(?i)\\b(nasal\\s*discharge|runny\\s*nose|rhinorrhea|snot|mucus\\s*from\\s*nose|drainage\\s*from\\s*nose|blowing\\s*my\\s*nose|nose\\s*running|clear\\s*nasal\\s*discharge|clear\\s*runny\\s*nose)\\b"
    );

    private static final Pattern PURULENT_DISCHARGE_PATTERN = Pattern.compile(
            "(?i)\\b(purulent(?:\\s*nasal)?\\s*discharge|yellow\\s*(?:nasal\\s*)?discharge|green\\s*(?:nasal\\s*)?discharge|yellow[- ]green\\s*(?:nasal\\s*)?discharge|thick\\s*(?:yellow|green|cloudy)\\s*(?:mucus|snot|discharge)|discolored\\s*nasal\\s*discharge|pus\\s*from\\s*nose|cloudy\\s*nasal\\s*discharge)\\b"
    );

    private static final Pattern HYPOSMIA_PATTERN = Pattern.compile(
            "(?i)\\b(loss\\s*of\\s*smell|reduced\\s*(?:sense\\s*of\\s*)?smell|decreased\\s*smell|cannot\\s*smell|can't\\s*smell|cant\\s*smell|anosmia|hyposmia|impaired\\s*smell|diminished\\s*smell)\\b"
    );

    private static final Pattern POSTNASAL_DRIP_PATTERN = Pattern.compile(
            "(?i)\\b(post[- ]nasal\\s*(?:drip|drainage)|mucus\\s*(?:dripping|running)\\s*down\\s*(?:the\\s*)?(?:back\\s*of\\s*)?throat|phlegm\\s*in\\s*(?:the\\s*)?back\\s*of\\s*(?:my\\s*)?throat)\\b"
    );

    private static final Pattern MAXILLARY_TOOTHACHE_PATTERN = Pattern.compile(
            "(?i)\\b(upper\\s*teeth\\s*(?:ache|pain|hurt)|maxillary\\s*toothache|dental\\s*pain\\s*with\\s*sinus|toothache\\s*under\\s*sinus|upper\\s*jaw\\s*pain)\\b"
    );

    private static final Pattern ORBITAL_SWELLING_PATTERN = Pattern.compile(
            "(?i)\\b(orbital\\s*(?:swelling|edema|cellulitis)|periorbital\\s*(?:swelling|edema|cellulitis|puffiness)|swollen\\s*(?:around\\s*)?eyes?|eye\\s*swelling|eyelid\\s*swelling|proptosis|bulging\\s*eye|eye\\s*swollen\\s*shut|both\\s*eyes\\s*(?:are\\s*)?swollen)\\b"
    );

    private static final Pattern VISION_CHANGE_PATTERN = Pattern.compile(
            "(?i)\\b(diplopia|double\\s*vision|vision\\s*(?:change|changes|loss|blur|blurred)|blurred\\s*vision|blurry\\s*vision|can't\\s*see\\s*clearly|cannot\\s*see\\s*clearly|decreased\\s*vision|loss\\s*of\\s*vision)\\b"
    );

    private static final Pattern PAINFUL_EYE_MOVEMENT_PATTERN = Pattern.compile(
            "(?i)\\b(pain\\s*(?:with|on|when)\\s*(?:moving\\s*)?eye\\s*movements?|painful\\s*eye\\s*movements?|hurt(?:s)?\\s*to\\s*move\\s*(?:my\\s*)?eyes?|restricted\\s*eye\\s*movements?|ophthalmoplegia)\\b"
    );

    private static class MigrationInfo {
        String origin;
        String destination;
        MigrationInfo(String origin, String destination) {
            this.origin = origin;
            this.destination = destination;
        }
    }

    /**
     * Extracts structured clinical features with clause-level attribute binding.
     */
    public List<StructuredClinicalFeature> extractFeatures(String text, int turn) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<StructuredClinicalFeature> extractedFeatures = new ArrayList<>();
        String[] clauses = CLAUSE_SPLITTER.split(text);

        // Global narrative-level relationship detection
        MigrationInfo globalMigration = extractMigration(text);
        boolean isTextBiphasic = Pattern.compile("(?i)\\b(improved.*then.*(worse|worsen)|better.*then.*(worse|worsen)|worse\\s*after\\s*(?:getting\\s*)?better|worse\\s*after\\s*improving)\\b").matcher(text).find();
        boolean isTextReturned = Pattern.compile("(?i)\\b(returned|came\\s*back|recurrent)\\b").matcher(text).find();

        for (String clause : clauses) {
            String c = clause.trim();
            if (c.isEmpty()) continue;

            int featuresBeforeClause = extractedFeatures.size();

            // Determine clause-level epistemic presence: uncertainty takes precedence over simple negation words
            boolean isClauseUncertain = UNCERTAINTY_PATTERN.matcher(c).find();
            boolean isClauseNegated = !isClauseUncertain && NEGATION_PATTERN.matcher(c).find();
            FactPresence defaultPresence = isClauseUncertain ? FactPresence.UNKNOWN
                    : (isClauseNegated ? FactPresence.ABSENT_DENIED : FactPresence.PRESENT);

            // Extract clause-bound modifiers
            String clauseDuration = extractMatch(DURATION_PATTERN, c);
            String clauseOnset = extractMatch(ONSET_PATTERN, c);
            String clauseProgression = extractMatch(PROGRESSION_PATTERN, c);
            String clauseSeverity = extractMatch(SEVERITY_PATTERN, c);
            String clauseCharacter = extractMatch(CHARACTER_PATTERN, c);
            String clauseAnatomy = extractMatch(ANATOMICAL_SITE_PATTERN, c);
            String clauseLaterality = extractMatch(LATERALITY_PATTERN, c);

            // Extract relationship bindings
            String clauseRadiation = extractRadiationSite(c);
            MigrationInfo clauseMigration = extractMigration(c);
            String clauseTrigger = extractTrigger(c);
            String clauseBetterWith = extractBetterWith(c);
            String clauseWorseWith = extractWorseWith(c);

            boolean isMigrating = clauseMigration != null || globalMigration != null;
            String migrationOrigin = clauseMigration != null ? clauseMigration.origin : (globalMigration != null ? globalMigration.origin : null);
            String migrationDestination = clauseMigration != null ? clauseMigration.destination : (globalMigration != null ? globalMigration.destination : null);

            // Biphasic text-level detection
            if (isTextBiphasic && (clauseProgression == null || clauseProgression.toLowerCase(Locale.ROOT).contains("worse"))) {
                clauseProgression = "DOUBLE_WORSENING";
            } else if (isTextReturned && clauseProgression == null) {
                clauseProgression = "RETURNED";
            }

            // Match and bind features within this clause
            matchAndBind(c, COUGH_PATTERN, "cough", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, clauseAnatomy != null ? clauseAnatomy : "CHEST", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, SPUTUM_PATTERN, "sputum_production", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "CHEST", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, WHEEZE_PATTERN, "wheezing", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "CHEST", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, DYSPNEA_PATTERN, "dyspnea", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "CHEST", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, CHEST_TIGHT_PATTERN, "chest_symptoms", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "CHEST", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, FEVER_PATTERN, "fever", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, null, clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, SORE_THROAT_PATTERN, "sore_throat", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, ODYNOPHAGIA_PATTERN, "odynophagia", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, DYSPHAGIA_PATTERN, "dysphagia", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, INABILITY_TO_SWALLOW_PATTERN, "inability_to_swallow", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, FUNCTIONAL_SWALLOW_PATTERN, "preserved_swallowing", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, DROOLING_PATTERN, "drooling", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, TONSILLAR_EXUDATE_PATTERN, "tonsillar_exudate", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, TONSILLAR_SWELLING_PATTERN, "tonsillar_swelling", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, CERVICAL_ADENOPATHY_PATTERN, "cervical_adenopathy", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "NECK", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, VOICE_CHANGE_PATTERN, "voice_change", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, STRIDOR_PATTERN, "stridor", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "AIRWAY", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, HEADACHE_PATTERN, "headache", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "HEAD", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, ABDOMINAL_PAIN_PATTERN, "abdominal_pain", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, clauseAnatomy != null ? clauseAnatomy : "ABDOMEN", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, NAUSEA_PATTERN, "nausea", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "ABDOMEN", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, VOMIT_PATTERN, "vomiting", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "ABDOMEN", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, DIARRHEA_PATTERN, "diarrhea", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "ABDOMEN", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, RHINORRHEA_PATTERN, "cold_symptoms", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "NOSE", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, JOINT_PAIN_PATTERN, "joint_pain", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, clauseAnatomy != null ? clauseAnatomy : "JOINT", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, EAR_PAIN_PATTERN, "ear_pain", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "EAR", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, DIZZINESS_PATTERN, "dizziness", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "HEAD", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);

            // Rhinosinusitis & Upper Airway bindings
            matchAndBind(c, FACIAL_PAIN_PATTERN, "facial_pain", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, clauseAnatomy != null ? clauseAnatomy : "SINUS", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, NASAL_CONGESTION_PATTERN, "nasal_congestion", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "NASAL", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, NASAL_DISCHARGE_PATTERN, "nasal_discharge", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "NASAL", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, PURULENT_DISCHARGE_PATTERN, "purulent_nasal_discharge", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "NASAL", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, HYPOSMIA_PATTERN, "hyposmia", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "NASAL", null, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, POSTNASAL_DRIP_PATTERN, "postnasal_drip", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "THROAT", null, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, MAXILLARY_TOOTHACHE_PATTERN, "maxillary_toothache", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "DENTAL", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, ORBITAL_SWELLING_PATTERN, "orbital_swelling", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "ORBITAL", clauseLaterality != null ? clauseLaterality : "BILATERAL", clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, VISION_CHANGE_PATTERN, "vision_change", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "ORBITAL", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);
            matchAndBind(c, PAINFUL_EYE_MOVEMENT_PATTERN, "painful_eye_movement", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseCharacter, "ORBITAL", clauseLaterality, clauseRadiation, isMigrating, migrationOrigin, migrationDestination, clauseTrigger, clauseBetterWith, clauseWorseWith, turn, extractedFeatures);

            // Cross-clause attribute binding fallback:
            // If a dependent clause has relieving/aggravating/radiation modifiers but no concept was matched directly in it,
            // bind them to the immediately preceding feature from the narrative.
            if (featuresBeforeClause == extractedFeatures.size() && !extractedFeatures.isEmpty()) {
                StructuredClinicalFeature lastFeat = extractedFeatures.get(extractedFeatures.size() - 1);
                if (clauseBetterWith != null && lastFeat.getBetterWith() == null) {
                    lastFeat.setBetterWith(normalizeRelievingFactor(clauseBetterWith));
                }
                if (clauseWorseWith != null && lastFeat.getWorseWith() == null) {
                    lastFeat.setWorseWith(normalizeAggravatingFactor(clauseWorseWith));
                }
                if (clauseRadiation != null && lastFeat.getRadiationSite() == null) {
                    lastFeat.setRadiationSite(normalizeRadiationSite(clauseRadiation));
                }
            }
        }

        // Global narrative enrichment for migration and returned status
        if (globalMigration != null) {
            for (StructuredClinicalFeature f : extractedFeatures) {
                if (f.getCanonicalConcept().contains("pain") || "abdominal_pain".equals(f.getCanonicalConcept())) {
                    f.setMigrating(true);
                    f.setMigrationOrigin(normalizeAnatomy(globalMigration.origin));
                    f.setMigrationDestination(normalizeAnatomy(globalMigration.destination));
                }
            }
        }

        if (isTextReturned) {
            for (StructuredClinicalFeature f : extractedFeatures) {
                if (f.getProgression() == null || !f.getProgression().equals("DOUBLE_WORSENING")) {
                    f.setProgression("RETURNED");
                }
            }
        }

        // Post-processing: If functional preserved swallowing is reported present, do not allow inability_to_swallow
        boolean preservedSwallow = extractedFeatures.stream()
                .anyMatch(f -> "preserved_swallowing".equals(f.getCanonicalConcept()) && f.isPresent());
        if (preservedSwallow) {
            extractedFeatures.removeIf(f -> "inability_to_swallow".equals(f.getCanonicalConcept()) && f.isPresent());
        }

        return extractedFeatures;
    }

    private void matchAndBind(
            String clauseText,
            Pattern conceptPattern,
            String canonicalConcept,
            FactPresence presence,
            String duration,
            String onset,
            String progression,
            String severity,
            String character,
            String anatomy,
            String laterality,
            String radiationSite,
            boolean isMigrating,
            String migrationOrigin,
            String migrationDestination,
            String triggerContext,
            String betterWith,
            String worseWith,
            int turn,
            List<StructuredClinicalFeature> collector) {

        Matcher m = conceptPattern.matcher(clauseText);
        if (m.find()) {
            String rawMention = m.group(1);
            List<String> qualifiers = new ArrayList<>();

            // Specific qualifier enrichment
            if ("cough".equals(canonicalConcept)) {
                if (clauseText.contains("phlegm") || clauseText.contains("mucus") || clauseText.contains("yellow") || clauseText.contains("green") || clauseText.contains("wet") || clauseText.contains("productive") || clauseText.contains("chesty")) {
                    qualifiers.add("productive");
                }
                if (clauseText.contains("dry") || clauseText.contains("hacking")) {
                    qualifiers.add("dry");
                }
            } else if ("sputum_production".equals(canonicalConcept)) {
                if (clauseText.contains("yellow")) qualifiers.add("yellow");
                if (clauseText.contains("green")) qualifiers.add("green");
                if (clauseText.contains("purulent")) qualifiers.add("purulent");
                if (clauseText.contains("clear")) qualifiers.add("clear");
                if (clauseText.contains("white")) qualifiers.add("white");
                if (clauseText.contains("blood") || clauseText.contains("hemoptysis")) qualifiers.add("hemoptysis");
            } else if ("fever".equals(canonicalConcept)) {
                if (clauseText.contains("high") || clauseText.contains("chills") || clauseText.contains("rigor")) qualifiers.add("high_grade");
                if (clauseText.contains("mild") || clauseText.contains("low")) qualifiers.add("low_grade");
            } else if ("nasal_discharge".equals(canonicalConcept) || "purulent_nasal_discharge".equals(canonicalConcept)) {
                if (clauseText.contains("yellow") || clauseText.contains("green") || clauseText.contains("purulent") || clauseText.contains("discolored") || clauseText.contains("cloudy")) {
                    qualifiers.add("purulent");
                }
                if (clauseText.contains("clear") || clauseText.contains("watery")) {
                    qualifiers.add("clear");
                }
                if (clauseText.contains("thick")) {
                    qualifiers.add("thick");
                }
            } else if ("facial_pain".equals(canonicalConcept)) {
                if (clauseText.contains("pressure")) qualifiers.add("pressure");
                if (clauseText.contains("fullness")) qualifiers.add("fullness");
                if (clauseText.contains("maxillary") || clauseText.contains("cheek")) qualifiers.add("maxillary");
                if (clauseText.contains("frontal") || clauseText.contains("forehead")) qualifiers.add("frontal");
                if (clauseText.contains("ethmoid")) qualifiers.add("ethmoid");
            } else if ("nasal_congestion".equals(canonicalConcept)) {
                if (clauseText.contains("obstruction") || clauseText.contains("blocked")) qualifiers.add("obstruction");
            }

            String normChar = normalizeCharacter(character);
            if (normChar != null && !qualifiers.contains(normChar.toLowerCase(Locale.ROOT))) {
                qualifiers.add(normChar.toLowerCase(Locale.ROOT));
            }

            double confidence = (presence == FactPresence.UNKNOWN) ? 0.0 : 1.0;

            StructuredClinicalFeature feat = StructuredClinicalFeature.builder()
                    .canonicalConcept(canonicalConcept)
                    .rawMention(rawMention)
                    .presence(presence)
                    .duration(duration)
                    .onset(onset)
                    .progression(normalizeProgression(progression))
                    .severity(severity != null ? severity.toUpperCase(Locale.ROOT) : null)
                    .character(normChar)
                    .anatomicalSite(normalizeAnatomy(anatomy))
                    .laterality(normalizeLaterality(laterality))
                    .radiationSite(normalizeRadiationSite(radiationSite))
                    .isMigrating(isMigrating)
                    .migrationOrigin(normalizeAnatomy(migrationOrigin))
                    .migrationDestination(normalizeAnatomy(migrationDestination))
                    .triggerContext(normalizeTrigger(triggerContext))
                    .betterWith(normalizeRelievingFactor(betterWith))
                    .worseWith(normalizeAggravatingFactor(worseWith))
                    .qualifiers(qualifiers)
                    .extractionConfidence(confidence)
                    .sourceTurn(turn)
                    .build();

            collector.add(feat);
        }
    }

    private MigrationInfo extractMigration(String text) {
        if (text == null) return null;
        Matcher m1 = MIGRATION_PATTERN_1.matcher(text);
        if (m1.find()) {
            return new MigrationInfo(m1.group(1).trim(), m1.group(2).trim());
        }
        Matcher m2 = MIGRATION_PATTERN_2.matcher(text);
        if (m2.find()) {
            return new MigrationInfo(m2.group(1).trim(), m2.group(2).trim());
        }
        return null;
    }

    private String extractRadiationSite(String text) {
        if (text == null) return null;
        Matcher m = RADIATION_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractBetterWith(String text) {
        if (text == null) return null;
        Matcher m = BETTER_WITH_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractWorseWith(String text) {
        if (text == null) return null;
        Matcher m = WORSE_WITH_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractTrigger(String text) {
        if (text == null) return null;
        Matcher m = TRIGGER_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    public String normalizeLaterality(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT).trim();
        if (r.contains("left")) return "LEFT";
        if (r.contains("right")) return "RIGHT";
        if (r.contains("bilateral") || r.contains("both")) return "BILATERAL";
        if (r.contains("unilateral") || r.contains("one")) return "UNILATERAL";
        if (r.contains("midline") || r.contains("central")) return "MIDLINE";
        return raw.toUpperCase(Locale.ROOT).trim();
    }

    public String normalizeAnatomy(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT).trim();
        if (r.contains("rlq") || r.contains("right lower")) return "RLQ";
        if (r.contains("llq") || r.contains("left lower")) return "LLQ";
        if (r.contains("ruq") || r.contains("right upper")) return "RUQ";
        if (r.contains("luq") || r.contains("left upper")) return "LUQ";
        if (r.contains("periumbilical") || r.contains("belly button") || r.contains("navel") || r.contains("umbilic")) return "PERIUMBILICAL";
        if (r.contains("epigastr")) return "EPIGASTRIC";
        if (r.contains("maxillary") || r.contains("cheek")) return "MAXILLARY";
        if (r.contains("frontal") || r.contains("forehead")) return "FRONTAL";
        if (r.contains("ethmoid")) return "ETHMOID";
        if (r.contains("periorbital") || r.contains("orbital") || r.contains("eye")) return "ORBITAL";
        if (r.contains("nasal") || r.contains("nose")) return "NASAL";
        if (r.contains("sinus")) return "SINUS";
        if (r.contains("teeth") || r.contains("tooth") || r.contains("dental")) return "DENTAL";
        if (r.contains("ear")) return "EAR";
        if (r.contains("throat") || r.contains("pharynx")) return "THROAT";
        if (r.contains("chest") || r.contains("retrosternal")) return "CHEST";
        if (r.contains("head")) return "HEAD";
        if (r.contains("neck") || r.contains("cervical")) return "NECK";
        if (r.contains("face") || r.contains("facial")) return "FACIAL";
        if (r.contains("abdomen") || r.contains("belly") || r.contains("stomach") || r.contains("tummy")) return "ABDOMEN";
        if (r.contains("back") || r.contains("lumbar")) return "BACK";
        if (r.contains("pelvis")) return "PELVIS";
        if (r.contains("groin")) return "GROIN";
        if (r.contains("knee")) return "KNEE";
        if (r.contains("ankle")) return "ANKLE";
        if (r.contains("shoulder")) return "SHOULDER";
        if (r.contains("arm")) return "ARM";
        if (r.contains("leg")) return "LEG";
        if (r.contains("joint")) return "JOINT";
        return raw.toUpperCase(Locale.ROOT).trim();
    }

    public String normalizeCharacter(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT).trim();
        if (r.contains("sharp")) return "SHARP";
        if (r.contains("dull")) return "DULL";
        if (r.contains("burn")) return "BURNING";
        if (r.contains("pressure")) return "PRESSURE";
        if (r.contains("cramp")) return "CRAMPING";
        if (r.contains("throb")) return "THROBBING";
        if (r.contains("stab")) return "STABBING";
        if (r.contains("ach")) return "ACHING";
        if (r.contains("colic")) return "COLICKY";
        if (r.contains("crush")) return "CRUSHING";
        if (r.contains("heavy") || r.contains("heaviness")) return "HEAVY";
        if (r.contains("gnaw")) return "GNAWING";
        return r.toUpperCase(Locale.ROOT);
    }

    public String normalizeRadiationSite(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT).trim();
        if (r.contains("left arm")) return "LEFT_ARM";
        if (r.contains("right arm")) return "RIGHT_ARM";
        if (r.contains("arm")) return "ARM";
        if (r.contains("jaw")) return "JAW";
        if (r.contains("back")) return "BACK";
        if (r.contains("shoulder")) return "SHOULDER";
        if (r.contains("neck")) return "NECK";
        if (r.contains("groin")) return "GROIN";
        return r.toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    public String normalizeRelievingFactor(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT).trim();
        if (r.contains("rest")) return "REST";
        if (r.contains("antacid")) return "ANTACIDS";
        if (r.contains("lean") && r.contains("forward")) return "LEANING_FORWARD";
        if (r.contains("sit") && r.contains("up")) return "SITTING_UP";
        if (r.contains("lying") || r.contains("lie down")) return "LYING_DOWN";
        if (r.contains("dark")) return "DARK_ROOM";
        if (r.contains("ice")) return "ICE";
        if (r.contains("heat")) return "HEAT";
        return r.toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    public String normalizeAggravatingFactor(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT).trim();
        if (r.contains("stair") || r.contains("climbing")) return "CLIMBING_STAIRS";
        if (r.contains("walk")) return "WALKING";
        if (r.contains("movement") || r.contains("moving")) return "MOVEMENT";
        if (r.contains("cough")) return "COUGHING";
        if (r.contains("deep breath") || r.contains("breathing in") || r.contains("inspiration")) return "DEEP_BREATH";
        if (r.contains("eat") || r.contains("food") || r.contains("meal")) return "EATING";
        if (r.contains("exertion") || r.contains("exercise")) return "EXERTION";
        if (r.contains("lying") || r.contains("lie down")) return "LYING_DOWN";
        if (r.contains("bending") || r.contains("bend forward")) return "BENDING_FORWARD";
        return r.toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    public String normalizeTrigger(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT).trim();
        if (r.contains("meal") || r.contains("eat") || r.contains("food")) return "POST_MEAL";
        if (r.contains("exertion") || r.contains("exercise") || r.contains("stair")) return "ON_EXERTION";
        if (r.contains("cold")) return "COLD_AIR";
        if (r.contains("stress")) return "STRESS";
        return r.toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    public String normalizeProgression(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT);
        if (r.contains("double_worsening") || r.contains("worse after") || (r.contains("improved") && r.contains("worse")) || (r.contains("better") && r.contains("worse"))) return "DOUBLE_WORSENING";
        if (r.contains("returned") || r.contains("came back") || r.contains("recurrent")) return "RETURNED";
        if (r.contains("resolved") || r.contains("gone away") || r.contains("disappeared")) return "RESOLVED";
        if (r.contains("worse") || r.contains("worsening")) return "WORSENING";
        if (r.contains("better") || r.contains("improv")) return "IMPROVING";
        if (r.contains("stable") || r.contains("same")) return "STABLE";
        if (r.contains("intermittent") || r.contains("comes and goes")) return "INTERMITTENT";
        if (r.contains("persistent") || r.contains("constant") || r.contains("continuous") || r.contains("persisting")) return "PERSISTENT";
        return raw.toUpperCase(Locale.ROOT);
    }

    private String extractMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(0).trim();
        }
        return null;
    }

    /**
     * Executes feature extraction and updates ClinicalConversationState with strict epistemic status.
     */
    public void updateStateWithFeatures(List<StructuredClinicalFeature> features, ClinicalConversationState state, int turn) {
        if (features == null || state == null) return;

        for (StructuredClinicalFeature f : features) {
            String concept = f.getCanonicalConcept();

            if (f.isPresent()) {
                String val = !f.getQualifiers().isEmpty() ? String.join("_", f.getQualifiers()) : "present";
                ClinicalFact fact = ClinicalFact.present(concept, val, turn);
                fact.setDuration(f.getDuration());
                fact.setOnset(f.getOnset());
                fact.setSeverity(f.getSeverity());
                fact.setCharacter(f.getCharacter());
                fact.setRadiationSite(f.getRadiationSite());
                fact.setMigrationOrigin(f.getMigrationOrigin());
                fact.setMigrationDestination(f.getMigrationDestination());
                fact.setMigrating(f.isMigrating());
                fact.setTriggerContext(f.getTriggerContext());
                fact.setBetterWith(f.getBetterWith());
                fact.setWorseWith(f.getWorseWith());

                if (f.getLaterality() != null) {
                    fact.setLaterality(f.getLaterality());
                }
                if (f.getAnatomicalSite() != null) {
                    fact.getAttributes().put("anatomicalSite", f.getAnatomicalSite());
                }
                state.getSymptoms().put(concept, fact);
                state.addFact(concept, fact);

                // Populate timeline duration if present
                if (f.getDuration() != null) {
                    state.getTimeline().put("duration", f.getDuration());
                    state.addFact("duration", ClinicalFact.present("duration", f.getDuration(), turn));
                }
                if (f.getProgression() != null) {
                    state.getTimeline().put("progression", f.getProgression());
                    state.addFact("progression", ClinicalFact.present("progression", f.getProgression(), turn));
                    if ("DOUBLE_WORSENING".equals(f.getProgression())) {
                        state.setSymptomTrajectory("DOUBLE_WORSENING");
                    } else if ("PERSISTENT".equals(f.getProgression())) {
                        state.setSymptomTrajectory("PERSISTENT");
                    } else if ("GETTING WORSE".equals(f.getProgression()) || "WORSENING".equals(f.getProgression())) {
                        state.setSymptomTrajectory("WORSENING");
                    } else if ("IMPROVING".equals(f.getProgression())) {
                        state.setSymptomTrajectory("IMPROVING");
                    } else if ("RETURNED".equals(f.getProgression()) || "RECURRENT".equals(f.getProgression())) {
                        state.setSymptomTrajectory("RECURRENT");
                    } else if ("RESOLVED".equals(f.getProgression())) {
                        state.setSymptomTrajectory("RESOLVED");
                    }
                }
                if (f.getSeverity() != null && state.getSeverity() == null) {
                    state.setSeverity(f.getSeverity());
                }

                // If symptom was previously negated, remove from negatedFindings
                state.getNegatedFindings().remove(concept);

            } else if (f.isDenied()) {
                ClinicalFact deniedFact = ClinicalFact.denied(concept, turn);
                deniedFact.setLaterality(f.getLaterality());
                if (f.getAnatomicalSite() != null) {
                    deniedFact.getAttributes().put("anatomicalSite", f.getAnatomicalSite());
                }
                state.addFact(concept, deniedFact);
                state.getNegatedFindings().add(concept);

                // If existing symptom has different laterality, do not remove it
                ClinicalFact existing = state.getSymptoms().get(concept);
                if (existing != null) {
                    if (existing.getLaterality() == null || f.getLaterality() == null
                            || existing.getLaterality().equalsIgnoreCase(f.getLaterality())) {
                        state.getSymptoms().remove(concept);
                    }
                }

            } else if (f.isUnknown()) {
                ClinicalFact unknownFact = ClinicalFact.unknown(concept);
                unknownFact.setLaterality(f.getLaterality());
                state.addFact(concept, unknownFact);
                state.getUnknownFacts().add(concept);
            }
        }
    }
}
