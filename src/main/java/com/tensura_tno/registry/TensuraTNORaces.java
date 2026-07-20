package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.race.fox_spirit.*;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.impl.RaceRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * 狐灵种族注册表。使用 Architectury {@link RaceRegistry#RACES} 注册所有种族。
 * <p>
 * 添加新种族的方法：
 * <pre>
 *   public static final RegistrySupplier&lt;ManasRace&gt; MY_RACE =
 *       register("my_race", MyRace::new);
 * </pre>
 */
public final class TensuraTNORaces {

    // ======================== 狐灵种族线 ========================

    /** 幼灵狐 — 初始种族（10 HP，EXTREME 难度） */
    public static final RegistrySupplier<ManasRace> BABY_SPIRIT_FOX =
            register("baby_spirit_fox", BabySpiritFoxRace::new);

    /** 狐灵使 — 第二阶（20 HP，获得契约灵狐技能） */
    public static final RegistrySupplier<ManasRace> FOX_SPIRIT_ENVOY =
            register("fox_spirit_envoy", FoxSpiritEnvoyRace::new);

    /** 灵狐契主 — 第三阶（50 HP） */
    public static final RegistrySupplier<ManasRace> SPIRIT_FOX_CONTRACT_MASTER =
            register("spirit_fox_contract_master", SpiritFoxContractMasterRace::new);

    /** 玄灵狐主 — 第四阶（200 HP，占位符） */
    public static final RegistrySupplier<ManasRace> MYSTIC_FOX_MASTER =
            register("mystic_fox_master", MysticFoxMasterRace::new);

    /** 天灵狐尊 — 终极种族（450 HP，占位符） */
    public static final RegistrySupplier<ManasRace> HEAVENLY_FOX_SOVEREIGN =
            register("heavenly_fox_sovereign", HeavenlyFoxSovereignRace::new);

    // ============================================================

    private TensuraTNORaces() {
    }

    private static <E extends ManasRace> RegistrySupplier<E> register(String name, java.util.function.Supplier<E> supplier) {
        return RaceRegistry.RACES.register(
                ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, name), supplier);
    }

    /**
     * 触发类加载，确保所有静态字段被初始化（即所有种族注册到 RaceRegistry）。
     * 需在主模组类构造器中调用。
     */
    public static void init() {
        // 触发静态初始化
    }
}
