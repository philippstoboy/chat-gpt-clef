package adris.altoclef.tasks.custom;

import adris.altoclef.tasksystem.Task;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Opens the chest at chestPos and QUICK_MOVEs every inventory stack that matches `filter` into it.
 * Works without any AltoClef container helpers. Baritone pathing is optional via reflection.
 */
public class StoreFilteredInChestTask extends Task {

    private enum Stage { MOVE, OPEN, TRANSFER, CLOSE, DONE }

    private final BlockPos chestPos;
    private final Predicate<ItemStack> filter;
    private final Runnable onDone;

    private Stage stage = Stage.MOVE;
    private boolean started;
    private boolean finished;

    public StoreFilteredInChestTask(BlockPos chestPos,
                                    Predicate<ItemStack> filter,
                                    Runnable onDone) {
        this.chestPos = chestPos;
        this.filter = filter;
        this.onDone = onDone;
    }

    /* ===== Task lifecycle (no-arg signatures in your fork) ===== */

    @Override
    protected void onStart() {
        started = true;
        stage = Stage.MOVE;
        finished = false;
    }

    @Override
    protected Task onTick() {
        if (!started) onStart();
        final MinecraftClient mc = MinecraftClient.getInstance();
        final ClientPlayerEntity player = (mc != null) ? mc.player : null;
        if (mc == null || player == null) return null;

        switch (stage) {
            case MOVE -> {
                if (player.getPos().squaredDistanceTo(Vec3d.ofCenter(chestPos)) > 16.0) {
                    // Try Baritone via reflection (no compile-time deps)
                    try {
                        Class<?> goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
                        Object goal = goalBlock
                                .getConstructor(int.class, int.class, int.class)
                                .newInstance(chestPos.getX(), chestPos.getY(), chestPos.getZ());

                        Object provider = Class.forName("baritone.api.BaritoneAPI")
                                .getMethod("getProvider").invoke(null);
                        Object primary = provider.getClass()
                                .getMethod("getPrimaryBaritone").invoke(provider);
                        Object process = primary.getClass()
                                .getMethod("getCustomGoalProcess").invoke(primary);

                        Class<?> goalIface = Class.forName("baritone.api.pathing.goals.Goal");
                        process.getClass()
                                .getMethod("setGoalAndPath", goalIface)
                                .invoke(process, goal);
                    } catch (Throwable ignored) {
                        // No Baritone on classpath or call failed; user can walk closer manually,
                        // or another movement task can bring us there.
                    }
                    return null;
                } else {
                    stage = Stage.OPEN;
                    return null;
                }
            }
            case OPEN -> {
                if (isChestScreen(player.currentScreenHandler)) {
                    stage = Stage.TRANSFER;
                    return null;
                }
                // Interact to open
                if (mc.interactionManager != null) {
                    BlockHitResult hit = new BlockHitResult(
                            Vec3d.ofCenter(chestPos), Direction.UP, chestPos, false);
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                }
                return null;
            }
            case TRANSFER -> {
                ScreenHandler h = player.currentScreenHandler;
                if (!isChestScreen(h)) { // closed unexpectedly → reopen
                    stage = Stage.OPEN;
                    return null;
                }

                int containerSlots = getContainerSlotCount(h);
                int totalSlots = h.slots.size();
                int playerInvStart = containerSlots;        // start of player inventory
                int playerInvEnd   = totalSlots - 1;        // inclusive

                // Shift-click matching stacks from player inv → chest
                for (int slot = playerInvStart; slot <= playerInvEnd; slot++) {
                    ItemStack st = h.getSlot(slot).getStack();
                    if (st != null && !st.isEmpty() && filter.test(st)) {
                        quickMove(mc, h, slot);
                        return null; // yield; avoid packet spam
                    }
                }
                stage = Stage.CLOSE;
                return null;
            }
            case CLOSE -> {
                if (isChestScreen(player.currentScreenHandler)) {
                    player.closeHandledScreen();
                    return null;
                }
                if (onDone != null) onDone.run();
                stage = Stage.DONE;
                finished = true;
                return null;
            }
            case DONE -> { return null; }
        }
        return null;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null && isChestScreen(mc.player.currentScreenHandler)) {
            mc.player.closeHandledScreen();
        }
    }

    @Override
    public boolean isEqual(Task other) {
        if (!(other instanceof StoreFilteredInChestTask o)) return false;
        return Objects.equals(this.chestPos, o.chestPos);
    }

    @Override
    protected String toDebugString() {
        return "StoreFilteredInChest@" + chestPos.toShortString() + " stage=" + stage;
    }

    /* ===== helpers ===== */

    private static boolean isChestScreen(ScreenHandler h) {
        return h instanceof GenericContainerScreenHandler;
    }

    private static int getContainerSlotCount(ScreenHandler h) {
        if (h instanceof GenericContainerScreenHandler g) return g.getRows() * 9; // 27 single, 54 double
        return 27;
    }

    private static void quickMove(MinecraftClient mc, ScreenHandler h, int slot) {
        if (mc != null && mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }
}
