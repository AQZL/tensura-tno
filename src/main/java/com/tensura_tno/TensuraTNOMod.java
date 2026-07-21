package com.tensura_tno;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.tensura_tno.ability.skill.SpiritSummonLimits;
import com.tensura_tno.client.MagicOreSenseXray;
import com.tensura_tno.command.TNOAdminCommands;
import com.tensura_tno.compat.AncientArtifactsExperienceCompat;
import com.tensura_tno.compat.CuriosSoulBoundDeathHandler;
import com.tensura_tno.compat.jade.JadeCompatBootstrap;
import com.tensura_tno.compat.TRUniqueMonstersCompat;
import com.tensura_tno.compat.TrAddonCompat;
import com.tensura_tno.compat.YigdCompat;
import com.tensura_tno.config.TNOConfigInjector;
import com.tensura_tno.config.TNOGeneralConfig;
import com.tensura_tno.event.ContractLittleFoxEvents;
import com.tensura_tno.event.GhoulDaySleepHandler;
import com.tensura_tno.event.HarvestFestivalAchievementHandler;
import com.tensura_tno.event.HumanFormRaceChangeHandler;
import com.tensura_tno.event.RimuruModeReincarnationHandler;
import com.tensura_tno.event.SlimePredationHumanFormHandler;
import com.tensura_tno.food.FoodEPHandler;
import com.tensura_tno.food.FoodEPManager;
import com.tensura_tno.ftb.TNOFtbQuests;
import com.tensura_tno.network.ContractLittleFoxPackets;
import com.tensura_tno.network.EdoTenseiPackets;
import com.tensura_tno.network.MagicOreSensePackets;
import com.tensura_tno.network.ReincarnationRulesPackets;
import com.tensura_tno.network.RimuruModePackets;
import com.tensura_tno.network.SlimeHumanFormPackets;
import com.tensura_tno.network.SpiritSummonPackets;
import com.tensura_tno.race.SlimeHumanFormState;
import com.tensura_tno.race.fox_spirit.SpiritSummonPocketPersistence;
import com.tensura_tno.race.fox_spirit.SpiritSummonNamingHandler;
import com.tensura_tno.race.fox_spirit.SpiritSummonRaceSyncHandler;
import com.tensura_tno.registry.TensuraTNOBackpacks;
import com.tensura_tno.registry.TensuraTNOBlocks;
import com.tensura_tno.registry.TensuraTNOEntities;
import com.tensura_tno.registry.TensuraTNOItems;
import com.tensura_tno.registry.TensuraTNOLootModifiers;
import com.tensura_tno.registry.TensuraTNORaces;
import com.tensura_tno.registry.TensuraTNORecipeSerializers;
import com.tensura_tno.registry.TensuraTNOSkills;
import com.tensura_tno.world.TensuraTNOGameRules;
import com.tensura_tno.world.spawn.BiomeBlacklistConfig;
import com.tensura_tno.world.spawn.SpawnRelocator;
import com.tensura_tno.world.spawn.SpawnRespawnRelocator;

import io.github.manasmods.manascore.config.ConfigRegistry;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod("tensura_tno")
public class TensuraTNOMod {
    public static final String MOD_ID = "tensura_tno";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean compatInitDone = new AtomicBoolean(false);

    public TensuraTNOMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, TensuraTNOCompatConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, TensuraTNOCompatConfig.CLIENT_SPEC);
        ConfigRegistry.registerConfig(new TNOGeneralConfig());

        // 游戏规则必须在任何世界加载前注册
        TensuraTNOGameRules.init();

        register(modBus);
        JadeCompatBootstrap.registerIfLoaded();

        // 出生点群系黑名单（spawn-biome-blacklist）
        // 独立 spec 文件名 tensura_tno-spawn.toml，避免污染 TensuraTNOCompatConfig
        modContainer.registerConfig(
            ModConfig.Type.COMMON,
            BiomeBlacklistConfig.COMMON_SPEC,
            "tensura_tno-spawn.toml");
        modBus.addListener(BiomeBlacklistConfig::onConfigLoad);

        NeoForge.EVENT_BUS.addListener(SpawnRelocator::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SpawnRelocator::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SpawnRespawnRelocator::onPlayerRespawn);

        // RegisterEvent fires AFTER NewRegistryEvent completes, so all custom registries
        // (including manascore_skill:skills) are available. HIGHEST priority ensures this
        // runs before any DeferredRegister baking for the same registry.
        modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class,
            event -> {
                if (compatInitDone.compareAndSet(false, true)) {
                    TRUniqueMonstersCompat.triggerDeferredInit();
                    TrAddonCompat.triggerDeferredInit();
                }
            });

        YigdCompat.register();
        CuriosSoulBoundDeathHandler.register();
        AncientArtifactsExperienceCompat.register();

        // FTBQuests 自定义任务与奖励注册（仅当 FTBQuests 已安装时）
        if (ModList.get().isLoaded("ftbquests")) {
            TNOFtbQuests.init();
        }

        FoodEPManager.init();

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
            event -> TNOAdminCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(EdoTenseiPackets::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(GhoulDaySleepHandler::onCanPlayerSleep);
        NeoForge.EVENT_BUS.addListener(GhoulDaySleepHandler::onCanContinueSleeping);
        NeoForge.EVENT_BUS.addListener(GhoulDaySleepHandler::onSleepFinished);
        NeoForge.EVENT_BUS.addListener(FoodEPHandler::onItemUseStart);
        NeoForge.EVENT_BUS.addListener(FoodEPHandler::onItemUseFinish);
        NeoForge.EVENT_BUS.addListener(FoodEPHandler::onTooltip);
        NeoForge.EVENT_BUS.addListener(FoodEPHandler::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(FoodEPHandler::onItemSmelted);
        NeoForge.EVENT_BUS.addListener(SpiritSummonPocketPersistence::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(SlimeHumanFormState::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(SlimeHumanFormState::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SlimeHumanFormState::onPlayerRespawn);
        // 玩家登录时扫描收纳口袋，移除违规条目（非原版生物 / 最大HP>100）
        NeoForge.EVENT_BUS.addListener(SpiritSummonLimits::onPlayerLoggedIn);

        EdoTenseiPackets.registerC2S();
        SpiritSummonPackets.registerC2S();
        ContractLittleFoxPackets.registerC2S();
        ReincarnationRulesPackets.registerC2S();
        RimuruModePackets.registerC2S();
        SlimeHumanFormPackets.registerC2S();
        // 注册 Architectury 事件监听（SkillEvents.REMOVE_SKILL 重置卷轴清理契约狐灵状态）
        ContractLittleFoxEvents.registerArchitecturyEvents();
        // 人形状态：种族切换时移除残留的 SCALE 修饰器，避免新种族体型偏移
        HumanFormRaceChangeHandler.register();
        // 转生回狐灵种族时同步 absorbedTypesCount，修复种族任务计数与口袋脱节
        SpiritSummonRaceSyncHandler.register();
        SpiritSummonNamingHandler.register();
        // 进入收获祭第 1 阶段时颁发「真王降诞」成就
        HarvestFestivalAchievementHandler.register();
        // 史莱姆种族用捕食者/暴食者吞噬异世界人或玩家时永久切换人形
        SlimePredationHumanFormHandler.register();
        // 玩家在转生 GUI 勾选「利姆露模式」并选 slime 后自动学 RIMURU_SKILLS
        RimuruModeReincarnationHandler.register();
        // S2C 包必须双端注册：服务端需要知道包类型才能编码/发送，客户端注册 handler 处理
        EdoTenseiPackets.registerS2C();
        SpiritSummonPackets.registerS2C();
        ContractLittleFoxPackets.registerS2C();
        MagicOreSensePackets.registerS2C();
        ReincarnationRulesPackets.registerS2C();
        RimuruModePackets.registerS2C();
        SlimeHumanFormPackets.registerS2C();
        if (FMLEnvironment.dist.isClient()) {
            MagicOreSenseXray.init();
        }

        // 初始化种族注册（Architectury Registrar，不需要 IEventBus）
        TensuraTNORaces.init();

        // 将本模组初始种族注入 Tensura 的 reincarnation_config.toml
        TNOConfigInjector.addToConfig();

        LOGGER.info("[TensuraTNO] 加载完成");
    }

    private void register(IEventBus modBus) {
        TensuraTNOBlocks.register(modBus);
        TensuraTNOBackpacks.register(modBus);
        TensuraTNOItems.register(modBus);
        TensuraTNORecipeSerializers.register(modBus);
        TensuraTNOSkills.register(modBus);
        TensuraTNOEntities.register(modBus);
        TensuraTNOLootModifiers.register(modBus);
    }
}
