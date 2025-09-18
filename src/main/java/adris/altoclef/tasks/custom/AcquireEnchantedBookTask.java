package adris.altoclef.tasks.custom;

import adris.altoclef.tasksystem.Task;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.Objects;

/**
 * Minimal, mapping-safe watcher task:
 * - FINISHES as soon as the player carries at least one enchanted book that contains `enchantmentQuery`.
 * - Calls the provided onHaveOne callback exactly once when that condition becomes true.
 *
 * This version intentionally does not try to produce the book itself (no villager/enchant pipeline),
 * so it compiles without extra dependencies. You can wire a real producer later and run it in parallel.
 */
public class AcquireEnchantedBookTask extends Task {

    private enum Stage { CHECK, DONE }

    private final String enchantmentQuery;
    private final Runnable onHaveOne;

    private boolean started;
    private boolean finished;
    private Stage stage = Stage.CHECK;
    private boolean callbackFired;

    public AcquireEnchantedBookTask(String enchantmentQuery, int ignoredCount, Runnable onHaveOne) {
        // `ignoredCount` kept for compatibility with earlier calls; we only need >=1.
        this.enchantmentQuery = enchantmentQuery;
        this.onHaveOne = onHaveOne;
    }

    /* ===== Task lifecycle (no-arg API) ===== */

    @Override
    protected void onStart() {
        started = true;
        finished = false;
        callbackFired = false;
        stage = Stage.CHECK;
    }

    @Override
    protected Task onTick() {
        if (!started) onStart();

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = (mc != null) ? mc.player : null;
        if (player == null) return null;

        switch (stage) {
            case CHECK -> {
                if (hasMatchingBookInInventory(player, enchantmentQuery)) {
                    fireOnce();
                    stage = Stage.DONE;
                    finished = true;
                }
                return null;
            }
            case DONE -> {
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
        if (!(other instanceof AcquireEnchantedBookTask o)) return false;
        return Objects.equals(this.enchantmentQuery, o.enchantmentQuery);
    }

    @Override
    protected String toDebugString() {
        return "AcquireEnchantedBook(watch \"" + enchantmentQuery + "\") stage=" + stage;
    }

    /* ===== helpers ===== */

    private void fireOnce() {
        if (!callbackFired && onHaveOne != null) {
            callbackFired = true;
            try { onHaveOne.run(); } catch (Throwable ignored) {}
        }
    }

    private static boolean hasMatchingBookInInventory(ClientPlayerEntity player, String enchantmentQuery) {
        // main inventory 0..35
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (InventoryHelpers.isEnchantedBookWith(s, enchantmentQuery)) return true;
        }
        // offhand
        if (!player.getInventory().offHand.isEmpty()
                && InventoryHelpers.isEnchantedBookWith(player.getInventory().offHand.get(0), enchantmentQuery)) {
            return true;
        }
        return false;
    }
}
