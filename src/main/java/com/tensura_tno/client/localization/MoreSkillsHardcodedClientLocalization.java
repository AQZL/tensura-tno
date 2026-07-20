package com.tensura_tno.client.localization;

import com.tensura_tno.compat.moreskills.MoreSkillsHardcodedLocalization;
import net.minecraft.locale.Language;

import java.util.List;

/** Client-side adapter for legacy MoreSkills APIs which draw raw String values. */
public final class MoreSkillsHardcodedClientLocalization {

    private MoreSkillsHardcodedClientLocalization() {
    }

    public static String localize(String original) {
        if (!MoreSkillsHardcodedLocalization.hasCandidate(original)
                || !MoreSkillsHardcodedLocalization.isCalledFromMoreSkills()) {
            return original;
        }

        Language language = Language.getInstance();
        String exactKey = MoreSkillsHardcodedLocalization.findExactKey(original);
        if (exactKey != null) {
            return language.getOrDefault(exactKey, original);
        }

        List<MoreSkillsHardcodedLocalization.Segment> segments = MoreSkillsHardcodedLocalization.split(original);
        if (segments == null) return original;

        StringBuilder result = new StringBuilder(original.length());
        for (MoreSkillsHardcodedLocalization.Segment segment : segments) {
            result.append(segment.translationKey() == null
                    ? segment.text()
                    : language.getOrDefault(segment.translationKey(), segment.text()));
        }
        return result.toString();
    }

}
