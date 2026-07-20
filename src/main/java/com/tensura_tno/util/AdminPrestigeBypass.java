package com.tensura_tno.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 用于管理员指令强制声望时，临时标记玩家跳过 canPrestige() 检查。
 * 线程安全（Minecraft 服务端单线程）。
 */
public class AdminPrestigeBypass {

    private static final Set<UUID> BYPASS_SET = Collections.synchronizedSet(new HashSet<>());

    public static void enable(UUID uuid) {
        BYPASS_SET.add(uuid);
    }

    public static void disable(UUID uuid) {
        BYPASS_SET.remove(uuid);
    }

    public static boolean isEnabled(UUID uuid) {
        return BYPASS_SET.contains(uuid);
    }
}
