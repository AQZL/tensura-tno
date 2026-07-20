package com.tensura_tno;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stores quest name data for language-switch support. */
public final class QuestNameCache {
    /** idPath → original English name (for non-@lang quests from server) */
    public static final Map<String, String> ORIGINAL_NAMES = new HashMap<>();
    /** idPath → lang key (for @lang: prefixed quests from datapack) */
    public static final Map<String, String> LANG_KEYS = new HashMap<>();
    /** All QuestDefinition objects received from server */
    public static final List<Object> QUESTS = new ArrayList<>();

    private QuestNameCache() {}
}
