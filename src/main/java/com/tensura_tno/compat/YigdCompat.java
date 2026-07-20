package com.tensura_tno.compat;

import com.tensura_tno.TensuraTNOMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Routes item drops from Tensura ICloning skill deaths (Unyielding, BodyDouble, etc.)
 * into a YIGD grave instead of dropping them to the floor.
 *
 * <h3>Root cause — execution order</h3>
 * When Unyielding/BodyDouble onDeath() fires (called by ManasCore's DEATH_EVENT_HIGH handler
 * which wraps the NeoForge LivingDeathEvent), it synchronously:
 *  a. Restores player health
 *  b. Creates a temporary CloneEntity at the death position with the player's inventory
 *  c. Calls clone.die(source) — which fires a NESTED NeoForge LivingDeathEvent + LivingDropsEvent
 *     for the CLONE right here — items scatter to the floor
 *  d. Returns false → ManasCore cancels the player's outer LivingDeathEvent
 * YIGD's LivingDeathEvent handler (LOWEST, receiveCanceled=false) runs AFTER step d,
 * long after items are already on the floor. Pre-recording on the player's event is too late.
 *
 * <h3>Fix</h3>
 * Intercept the CLONE's LivingDropsEvent at LOWEST. At that point the player hasn't been
 * teleported yet, the drops are the player's death-time vanilla items, and we can redirect
 * everything into a properly-positioned YIGD grave.
 */
public final class YigdCompat {

    private static volatile boolean yigdPresent = true;

    public static void register() {
        if (!ModList.get().isLoaded("yigd")) {
            yigdPresent = false;
            return;
        }

        // Intercept the clone's LivingDropsEvent at LOWEST.
        // When Unyielding/BodyDouble onDeath() runs, it synchronously spawns a CloneEntity,
        // copies the player's inventory into it, then calls clone.die() — which fires this
        // event for the CLONE (not the player). At this exact moment the player is health-
        // restored but not yet teleported, so owner.serverLevel() and owner.position()
        // still reflect the death dimension/position.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDropsEvent.class, event -> {
            if (!yigdPresent) return;

            Collection<ItemEntity> drops = event.getDrops();
            if (drops.isEmpty()) return;

            ServerPlayer owner = getCloneOwner(event.getEntity());
            if (owner == null) return;

            if (owner.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

            Vec3 deathPos = event.getEntity().position();
            DamageSource source = event.getSource();
            ServerLevel deathLevel = owner.serverLevel();

            try {
                createGraveWithCloneDrops(owner, deathPos, source, deathLevel, drops);
                drops.clear();
            } catch (ClassNotFoundException e) {
                yigdPresent = false;
            } catch (Exception e) {
                TensuraTNOMod.LOGGER.error("[TensuraTNO] YigdCompat: failed to create grave for clone drops", e);
            }
        });
    }

    /** Returns the owning ServerPlayer if {@code entity} is a CloneEntity, else null. */
    private static ServerPlayer getCloneOwner(LivingEntity entity) {
        try {
            Class<?> cloneClass = Class.forName("io.github.manasmods.tensura.entity.human.CloneEntity");
            if (!cloneClass.isInstance(entity)) return null;
            Object owner = cloneClass.getMethod("getOwner").invoke(entity);
            return owner instanceof ServerPlayer sp ? sp : null;
        } catch (Exception | NoClassDefFoundError ignored) {
            return null;
        }
    }

    /**
     * Creates a YIGD grave at {@code pos} for the player, containing Curios (captured from
     * the living player) and the clone's {@code drops} (= player's vanilla items at death time).
     *
     * <p>Vanilla inventory and XP are saved, zeroed before calling DeathHandler constructor
     * (so the InventoryComponent captures only Curios), then restored afterwards. The drops
     * are added via addItem() after construction. Unyielding will then overwrite vanilla
     * with the backup clone's inventory, so the restore is purely a safety measure.</p>
     */
    @SuppressWarnings("unchecked")
    private static void createGraveWithCloneDrops(
            ServerPlayer player, Vec3 pos, DamageSource source,
            ServerLevel level, Collection<ItemEntity> drops) throws Exception {

        Class<?> yigdClass = Class.forName("com.b1n_ry.yigd.Yigd");
        Field unfinishedField = yigdClass.getField("UNFINISHED_DEATHS");
        Map<UUID, Object> unfinishedDeaths = (Map<UUID, Object>) unfinishedField.get(null);

        if (unfinishedDeaths.containsKey(player.getUUID())) {
            // YIGD already has a handler for this player — just funnel the extra drops into it
            Object existingHandler = unfinishedDeaths.get(player.getUUID());
            Method addItem = existingHandler.getClass().getMethod("addItem", ItemStack.class);
            for (ItemEntity ie : drops) {
                if (!ie.getItem().isEmpty()) {
                    addItem.invoke(existingHandler, ie.getItem().copy());
                }
            }
            // finalizeDeath() will be called by YIGD's normal LivingDropsEvent for the player
            return;
        }

        // ── temporarily clear player vanilla inventory + XP ─────────────────────────
        // so DeathHandler's InventoryComponent captures an empty vanilla inventory.
        // Curios are intentionally left for DeathHandler to capture (they go to the grave).

        List<ItemStack> savedVanilla = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            savedVanilla.add(player.getInventory().getItem(i).copy());
        }
        int savedXpLevel = player.experienceLevel;
        float savedXpProgress = player.experienceProgress;
        int savedTotalXp = player.totalExperience;

        player.getInventory().clearContent();
        player.experienceLevel = 0;
        player.experienceProgress = 0f;
        player.totalExperience = 0;

        // ── create DeathHandler (captures empty vanilla + death Curios) ───────────
        Object deathHandler = null;
        try {
            Class<?> dhClass = Class.forName("com.b1n_ry.yigd.DeathHandler");
            Constructor<?> ctor = dhClass.getConstructor(
                    ServerPlayer.class, ServerLevel.class, Vec3.class, DamageSource.class);
            // DeathHandler constructor: captures InventoryComponent (empty vanilla + Curios),
            // clears player (nothing for vanilla since empty; clears Curios → good),
            // captures ExpComponent (0 XP → player won't lose XP to the grave).
            deathHandler = ctor.newInstance(player, level, pos, source);
        } finally {
            // ── always restore vanilla inventory + XP ────────────────────────────────
            // Unyielding will overwrite vanilla with backup inventory afterwards; that's fine.
            // We restore here so there is no net change from the player's perspective.
            for (int i = 0; i < Math.min(savedVanilla.size(), player.getInventory().getContainerSize()); i++) {
                player.getInventory().setItem(i, savedVanilla.get(i));
            }
            player.experienceLevel = savedXpLevel;
            player.experienceProgress = savedXpProgress;
            player.totalExperience = savedTotalXp;
        }

        if (deathHandler == null) return;

        // ── add clone drops to the grave (player's actual death-time vanilla items) ─
        Method addItem = deathHandler.getClass().getMethod("addItem", ItemStack.class);
        for (ItemEntity ie : drops) {
            if (!ie.getItem().isEmpty()) {
                addItem.invoke(deathHandler, ie.getItem().copy());
            }
        }

        // ── place the grave ───────────────────────────────────────────────────────
        Method finalizeDeath = deathHandler.getClass().getMethod("finalizeDeath");
        finalizeDeath.invoke(deathHandler);
        scheduleGraveLocationFromDeathHandler(player, deathHandler);
    }

    /**
     * Called before Tensura's CloneEntity.remove() scatters its inventory.
     * Creates a YIGD grave from the clone container only, leaving the living owner untouched.
     */
    public static boolean createGraveForManualCloneRemoval(LivingEntity clone) {
        if (!yigdPresent || !ModList.get().isLoaded("yigd") || clone.level().isClientSide()) return false;
        if (clone.isDeadOrDying()) return false;

        ServerPlayer owner = getCloneOwner(clone);
        if (owner == null) return false;
        if (!(clone.level() instanceof ServerLevel level)) return false;

        try {
            Container inventory = getCloneInventory(clone);
            if (inventory == null) return false;

            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
            if (stacks.isEmpty()) return false;

            GravePlacement gravePlacement = createGraveFromStacks(owner, level, clone.position(), owner.damageSources().generic(), stacks);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                inventory.setItem(i, ItemStack.EMPTY);
            }

            scheduleGraveLocationMessage(owner, gravePlacement.graveComponent(), gravePlacement.respawnComponent());
            return true;
        } catch (ClassNotFoundException e) {
            yigdPresent = false;
        } catch (Exception e) {
            TensuraTNOMod.LOGGER.error("[TensuraTNO] YigdCompat: failed to create grave for manually removed clone", e);
        }
        return false;
    }

    private static Container getCloneInventory(LivingEntity clone) throws Exception {
        Class<?> humanoidClass = Class.forName("io.github.manasmods.tensura.entity.template.TensuraHumanoidEntity");
        Field inventoryField = humanoidClass.getField("inventory");
        Object inventory = inventoryField.get(clone);
        return inventory instanceof Container container ? container : null;
    }

    private record GravePlacement(Object graveComponent, Object respawnComponent) {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static GravePlacement createGraveFromStacks(
            ServerPlayer owner, ServerLevel level, Vec3 pos,
            DamageSource source, List<ItemStack> stacks) throws Exception {

        NonNullList<Object> graveItems = NonNullList.create();
        Class<?> dropRuleClass = Class.forName("com.b1n_ry.yigd.util.DropRule");
        Object putInGrave = Enum.valueOf((Class<? extends Enum>) dropRuleClass.asSubclass(Enum.class), "PUT_IN_GRAVE");

        Class<?> graveItemClass = Class.forName("com.b1n_ry.yigd.data.GraveItem");
        Constructor<?> graveItemCtor = graveItemClass.getConstructor(ItemStack.class, dropRuleClass);
        for (ItemStack stack : stacks) {
            graveItems.add(graveItemCtor.newInstance(stack.copy(), putInGrave));
        }

        Class<?> inventoryComponentClass = Class.forName("com.b1n_ry.yigd.components.InventoryComponent");
        Constructor<?> inventoryCtor = inventoryComponentClass.getDeclaredConstructor(
                NonNullList.class, Map.class, int.class, int.class, int.class);
        inventoryCtor.setAccessible(true);
        Object inventoryComponent = inventoryCtor.newInstance(
                graveItems, Collections.emptyMap(),
                owner.getInventory().items.size(),
                owner.getInventory().armor.size(),
                owner.getInventory().offhand.size());

        Class<?> expComponentClass = Class.forName("com.b1n_ry.yigd.components.ExpComponent");
        Constructor<?> expCtor = expComponentClass.getDeclaredConstructor(int.class, double.class);
        expCtor.setAccessible(true);
        Object expComponent = expCtor.newInstance(0, 0.0D);

        Class<?> graveComponentClass = Class.forName("com.b1n_ry.yigd.components.GraveComponent");
        Constructor<?> graveCtor = graveComponentClass.getConstructor(
                ResolvableProfile.class, inventoryComponentClass, expComponentClass,
                ServerLevel.class, Vec3.class, Component.class, UUID.class);
        Object graveComponent = graveCtor.newInstance(
                new ResolvableProfile(owner.getGameProfile()),
                inventoryComponent,
                expComponent,
                level,
                pos.add(0.0D, 0.5D, 0.0D),
                source.getLocalizedDeathMessage(owner),
                source.getEntity() instanceof ServerPlayer killer ? killer.getUUID() : null);

        Method isEmpty = graveComponentClass.getMethod("isEmpty");
        if (!((Boolean) isEmpty.invoke(graveComponent))) {
            graveComponentClass.getMethod("backUp").invoke(graveComponent);
        }

        Class<?> respawnComponentClass = Class.forName("com.b1n_ry.yigd.components.RespawnComponent");
        Object respawnComponent = respawnComponentClass.getConstructor(ServerPlayer.class).newInstance(owner);

        Class<?> deathContextClass = Class.forName("com.b1n_ry.yigd.data.DeathContext");
        Constructor<?> deathContextCtor = deathContextClass.getConstructor(
                ServerPlayer.class, ServerLevel.class, Vec3.class, DamageSource.class);
        Object deathContext = deathContextCtor.newInstance(owner, level, pos, source);

        graveComponentClass.getMethod(
                "generateOrDrop", Direction.class, deathContextClass, respawnComponentClass)
                .invoke(graveComponent, owner.getDirection(), deathContext, respawnComponent);

        return new GravePlacement(graveComponent, respawnComponent);
    }

    private static Object getDeathHandlerGraveComponent(Object deathHandler) throws Exception {
        Field graveComponentField = deathHandler.getClass().getDeclaredField("graveComponent");
        graveComponentField.setAccessible(true);
        return graveComponentField.get(deathHandler);
    }

    private static Object getDeathHandlerRespawnComponent(Object deathHandler) throws Exception {
        Field respawnComponentField = deathHandler.getClass().getDeclaredField("respawnComponent");
        respawnComponentField.setAccessible(true);
        return respawnComponentField.get(deathHandler);
    }

    @SuppressWarnings("unchecked")
    private static void scheduleGraveLocationFromDeathHandler(ServerPlayer player, Object deathHandler) throws Exception {
        scheduleGraveLocationMessage(
                player,
                getDeathHandlerGraveComponent(deathHandler),
                getDeathHandlerRespawnComponent(deathHandler));
    }

    @SuppressWarnings("unchecked")
    private static void scheduleGraveLocationMessage(ServerPlayer player, Object graveComponent, Object respawnComponent) throws Exception {
        Class<?> yigdClass = Class.forName("com.b1n_ry.yigd.Yigd");
        Field endOfTickField = yigdClass.getField("END_OF_TICK");
        List<Runnable> endOfTick = (List<Runnable>) endOfTickField.get(null);
        endOfTick.add(() -> {
            try {
                if (wasGraveGenerated(respawnComponent)) {
                    sendGraveLocationMessage(player, graveComponent);
                }
            } catch (Exception e) {
                TensuraTNOMod.LOGGER.error("[TensuraTNO] YigdCompat: failed to announce grave location", e);
            }
        });
    }

    private static boolean wasGraveGenerated(Object respawnComponent) throws Exception {
        if (respawnComponent == null) return true;
        Method wasGraveGenerated = respawnComponent.getClass().getMethod("wasGraveGenerated");
        return (Boolean) wasGraveGenerated.invoke(respawnComponent);
    }

    private static void sendGraveLocationMessage(ServerPlayer player, Object graveComponent) throws Exception {
        if (!shouldInformGraveLocation() || graveComponent == null) return;

        Method getPos = graveComponent.getClass().getMethod("getPos");
        Method getWorldRegistryKey = graveComponent.getClass().getMethod("getWorldRegistryKey");
        BlockPos gravePos = (BlockPos) getPos.invoke(graveComponent);
        Object worldKey = getWorldRegistryKey.invoke(graveComponent);
        Method location = worldKey.getClass().getMethod("location");

        player.sendSystemMessage(Component.translatable("text.yigd.message.grave_location",
                gravePos.getX(), gravePos.getY(), gravePos.getZ(), location.invoke(worldKey).toString()));
    }

    private static boolean shouldInformGraveLocation() {
        try {
            Class<?> configClass = Class.forName("com.b1n_ry.yigd.config.YigdConfig");
            Object config = configClass.getMethod("getConfig").invoke(null);
            Field graveConfigField = config.getClass().getField("graveConfig");
            Object graveConfig = graveConfigField.get(config);
            Field informField = graveConfig.getClass().getField("informGraveLocation");
            return informField.getBoolean(graveConfig);
        } catch (ClassNotFoundException e) {
            yigdPresent = false;
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
