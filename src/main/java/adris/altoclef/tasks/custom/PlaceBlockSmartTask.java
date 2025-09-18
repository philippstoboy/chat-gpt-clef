package adris.altoclef.tasks.custom;

import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * Walks near the target pos (optional Baritone via reflection) and places the given block there
 * by selecting the correct hotbar slot and interactBlock()-clicking the position.
 * No compile-time dependency on any AltoClef place task.
 */
public class PlaceBlockSmartTask extends Task {

    private enum Stage { MOVE, SELECT_ITEM, PLACE, DONE }

    private final BlockPos pos;
    private final Block block;

    private Stage stage = Stage.MOVE;
    private boolean started;
    private boolean finished;

    public PlaceBlockSmartTask(BlockPos pos, Block block) {
        this.pos = pos;
        this.block = block;
    }

    @Override
    protected void onStart() {
        started = true;
        finished = false;
        stage = Stage.MOVE;
    }

    @Override
    protected Task onTick() {
        if (!started) onStart();
        final MinecraftClient mc = MinecraftClient.getInstance();
        final ClientPlayerEntity player = (mc != null) ? mc.player : null;
        final World world = (mc != null) ? mc.world : null;
        if (mc == null || player == null || world == null) return null;

        // Already placed?
        if (world.getBlockState(pos).isOf(block)) {
            stage = Stage.DONE;
        }

        switch (stage) {
            case MOVE -> {
                // If we are far, try Baritone via reflection; otherwise proceed
                if (player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 16.0) {
                    try {
                        Class<?> goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
                        Object goal = goalBlock
                                .getConstructor(int.class, int.class, int.class)
                                .newInstance(pos.getX(), pos.getY(), pos.getZ());

                        Object provider = Class.forName("baritone.api.BaritoneAPI")
                                .getMethod("getProvider").invoke(null);
                        Object primary = provider.getClass()
                                .getMethod("getPrimaryBaritone").invoke(provider);
                        Object process = primary.getClass()
                                .getMethod("getCustomGoalProcess").invoke(primary);
                        Class<?> goalIface = Class.forName("baritone.api.pathing.goals.Goal");
                        process.getClass().getMethod("setGoalAndPath", goalIface).invoke(process, goal);
                    } catch (Throwable ignored) {
                        // No Baritone; user/another task can walk; we’ll just wait
                    }
                    return null;
                }
                stage = Stage.SELECT_ITEM;
                return null;
            }
            case SELECT_ITEM -> {
                // Ensure the correct block item is selected in hotbar
                int hotbar = findHotbarSlot(player, block.asItem());
                if (hotbar == -1) {
                    // We don't have the item; nothing to do (a parent should acquire it).
                    // Yield until inventory is updated.
                    return null;
                }
                if (player.getInventory().selectedSlot != hotbar) {
                    player.getInventory().selectedSlot = hotbar;
                    return null; // give a tick after switching slot
                }
                stage = Stage.PLACE;
                return null;
            }
            case PLACE -> {
                if (world.getBlockState(pos).isOf(block)) {
                    stage = Stage.DONE;
                    return null;
                }

                // Aim at the block’s center and interact; this works for placing on replaceables.
                Vec3d hitVec = Vec3d.ofCenter(pos);
                BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);

                if (mc.interactionManager != null) {
                    // If a screen is open, close it (can block placement)
                    ScreenHandler sh = player.currentScreenHandler;
                    if (sh != null && player.currentScreenHandler != player.playerScreenHandler) {
                        player.closeHandledScreen();
                        return null;
                    }
                    mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                }
                // Let the world tick and then re-check placed state next tick
                return null;
            }
            case DONE -> {
                finished = true;
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        // no-op
    }

    @Override
    public boolean isEqual(Task other) {
        if (!(other instanceof PlaceBlockSmartTask o)) return false;
        return Objects.equals(this.pos, o.pos) && Objects.equals(this.block, o.block);
    }

    @Override
    protected String toDebugString() {
        return "PlaceBlockSmart(" + block.getName().getString() + ") @" + pos.toShortString() + " stage=" + stage;
    }

    /* === helpers === */

    private static int findHotbarSlot(ClientPlayerEntity player, Item item) {
        // Hotbar 0..8
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == item) return i;
        }
        return -1;
    }
}
