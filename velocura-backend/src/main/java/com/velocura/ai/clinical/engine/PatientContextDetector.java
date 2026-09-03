package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.PatientContext;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether the patient is the user themselves or a third party (child, parent, spouse),
 * along with clinically relevant age, pediatric/infant status, gender, and pregnancy context.
 */
@Component
public class PatientContextDetector {

    private static final Pattern MOTHER = Pattern.compile("(?i)\\b(my\\s*(mother|mom|maa|mummy))\\b");
    private static final Pattern FATHER = Pattern.compile("(?i)\\b(my\\s*(father|dad|papa|pitaji))\\b");
    private static final Pattern SPOUSE_HUSBAND = Pattern.compile("(?i)\\b(my\\s*(husband|pati))\\b");
    private static final Pattern SPOUSE_WIFE = Pattern.compile("(?i)\\b(my\\s*(wife|patni))\\b");
    private static final Pattern SON = Pattern.compile("(?i)\\b(my\\s*(son|beta))\\b");
    private static final Pattern DAUGHTER = Pattern.compile("(?i)\\b(my\\s*(daughter|beti))\\b");
    private static final Pattern CHILD = Pattern.compile("(?i)\\b(my\\s*(child|kid|baby|baccha))\\b");

    private static final Pattern AGE_MONTHS = Pattern.compile("(?i)\\b(\\d{1,2})\\s*(?:-|\\s*)month(?:s)?(?:-|\\s*)old\\b");
    private static final Pattern AGE_YEARS = Pattern.compile("(?i)\\b(?:age\\s*(?:is|:)?\\s*)?(\\d{1,3})\\s*(?:-|\\s*)year(?:s)?(?:-|\\s*)old\\b");
    private static final Pattern AGE_SHORT = Pattern.compile("(?i)\\b(?:i am|my\\s*\\w+\\s*,?\\s*)(\\d{1,2})\\s*(?:yo|y/o)\\b");

    private static final Pattern PREGNANCY = Pattern.compile("(?i)\\b(pregnant|pregnancy|trimester|expecting|breastfeeding|postpartum|just\\s*gave\\s*birth)\\b");

    private static final Pattern COUNTRY_INDIA = Pattern.compile("(?i)\\b(india|delhi|mumbai|bangalore|bengaluru|hyderabad|chennai|kolkata|pune|noida|gurgaon|jaipur|lucknow|ahmedabad)\\b");
    private static final Pattern COUNTRY_US = Pattern.compile("(?i)\\b(usa|united\\s*states|america|california|texas|new\\s*york|florida)\\b");

    public PatientContext detectContext(String text, PatientContext existingContext) {
        PatientContext context = existingContext != null ? existingContext : PatientContext.defaultSelf();
        if (text == null || text.isBlank()) return context;

        String lower = text.toLowerCase();

        // 1. Relationship detection
        if (MOTHER.matcher(lower).find()) {
            context.setUserRole(PatientContext.UserRole.FAMILY_MEMBER);
            context.setRelationship("mother");
            context.setGender("female");
        } else if (FATHER.matcher(lower).find()) {
            context.setUserRole(PatientContext.UserRole.FAMILY_MEMBER);
            context.setRelationship("father");
            context.setGender("male");
        } else if (SPOUSE_HUSBAND.matcher(lower).find()) {
            context.setUserRole(PatientContext.UserRole.FAMILY_MEMBER);
            context.setRelationship("husband");
            context.setGender("male");
        } else if (SPOUSE_WIFE.matcher(lower).find()) {
            context.setUserRole(PatientContext.UserRole.FAMILY_MEMBER);
            context.setRelationship("wife");
            context.setGender("female");
        } else if (SON.matcher(lower).find()) {
            context.setUserRole(PatientContext.UserRole.FAMILY_MEMBER);
            context.setRelationship("son");
            context.setGender("male");
        } else if (DAUGHTER.matcher(lower).find()) {
            context.setUserRole(PatientContext.UserRole.FAMILY_MEMBER);
            context.setRelationship("daughter");
            context.setGender("female");
        } else if (CHILD.matcher(lower).find()) {
            context.setUserRole(PatientContext.UserRole.FAMILY_MEMBER);
            context.setRelationship("child");
            context.setPediatric(true);
        }

        // 2. Age Detection
        Matcher monthMatcher = AGE_MONTHS.matcher(lower);
        if (monthMatcher.find()) {
            int months = Integer.parseInt(monthMatcher.group(1));
            context.setAgeMonths(months);
            context.setAgeYears(months / 12.0);
            context.setPediatric(true);
            if (months <= 12) {
                context.setInfant(true);
            }
        }

        Matcher yearMatcher = AGE_YEARS.matcher(lower);
        if (yearMatcher.find()) {
            double years = Double.parseDouble(yearMatcher.group(1));
            context.setAgeYears(years);
            if (years < 18.0) {
                context.setPediatric(true);
            }
            if (years < 1.0) {
                context.setInfant(true);
            }
        }

        Matcher shortYearMatcher = AGE_SHORT.matcher(lower);
        if (shortYearMatcher.find() && context.getAgeYears() == null) {
            double years = Double.parseDouble(shortYearMatcher.group(1));
            context.setAgeYears(years);
            if (years < 18.0) context.setPediatric(true);
        }

        // 3. Pregnancy Status
        if (PREGNANCY.matcher(lower).find()) {
            if (lower.contains("postpartum") || lower.contains("just gave birth")) {
                context.setPregnancyStatus(PatientContext.PregnancyStatus.POSTPARTUM);
            } else {
                context.setPregnancyStatus(PatientContext.PregnancyStatus.PREGNANT);
            }
            context.setGender("female");
        }

        // 4. Country Location
        if (COUNTRY_INDIA.matcher(lower).find()) {
            context.setCountryLocation("IN");
        } else if (COUNTRY_US.matcher(lower).find()) {
            context.setCountryLocation("US");
        }

        return context;
    }
}
