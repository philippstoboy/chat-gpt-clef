package adris.altoclef.tasks.custom;

import adris.altoclef.tasksystem.Task;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Orchestrator that deposits all enchanted books matching `enchantmentQuery`
 * into the chest at `chestPos`. Uses StoreFilteredInChestTask to do the work.
 */
public class DepositEnchantedBooksInChestTask extends Task {

    private enum Stage { PREPARE, DEPOSIT, DONE }

    private final String enchantmentQuery;
    private final BlockPos chestPos;
    private final Runnable onDeposited;

    private Stage stage = Stage.PREPARE;
    private boolean started;
    private boolean finished;

    // spawned subtask (runs one tick at a time via onTick() return)
    private Task sub;

    public DepositEnchantedBooksInChestTask(String enchantmentQuery,
                                            BlockPos chestPos,
                                            Runnable onDeposited) {
        this.enchantmentQuery = enchantmentQuery;
        this.chestPos = chestPos;
        this.onDeposited = onDeposited;
    }

    /* ===== Task lifecycle (no-arg signatures) ===== */

    @Override
    protected void onStart() {
        started = true;
        finished = false;
        stage = Stage.PREPARE;
        sub = null;
    }

    @Override
    protected Task onTick() {
        if (!started) onStart();

        switch (stage) {
            case PREPARE -> {
                // Build a predicate for “enchanted book containing <enchantmentQuery>”
                Predicate<ItemStack> filter = InventoryHelpers.filterFor(enchantmentQuery);

                // Spawn the real deposit worker; when done, run callback and finish.
                sub = new StoreFilteredInChestTask(chestPos, filter, () -> {
                    if (onDeposited != null) onDeposited.run();
                    stage = Stage.DONE;
                    finished = true;
                });
                stage = Stage.DEPOSIT;
                return null;
            }
            case DEPOSIT -> {
                // Let the subtask do its thing; AltoClef will run the returned task.
                return sub;
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
        // nothing special; StoreFilteredInChestTask closes its GUI in onStop()
    }

    @Override
    public boolean isEqual(Task other) {
        if (!(other instanceof DepositEnchantedBooksInChestTask o)) return false;
        return Objects.equals(this.enchantmentQuery, o.enchantmentQuery)
                && Objects.equals(this.chestPos, o.chestPos);
    }

    @Override
    protected String toDebugString() {
        return "DepositEnchantedBooks(" + enchantmentQuery + ") @ " + chestPos.toShortString()
                + " stage=" + stage + (sub != null ? " sub=" + sub.getClass().getSimpleName() : "");
    }
}
