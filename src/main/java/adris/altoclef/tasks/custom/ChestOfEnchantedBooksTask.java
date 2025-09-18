package adris.altoclef.tasks.custom;

import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * Controller: fill a SINGLE chest (27 slots) with enchanted books matching `enchantmentQuery`
 * and store them at `chestPos`. Places a chest if you already carry one.
 *
 * NOTE: This version does not craft/trade the books yet. It will wait until a matching book
 * appears in your inventory (so you can hook in your producer), deposit it, and continue.
 */
public class ChestOfEnchantedBooksTask extends Task {

    private enum Stage { ENSURE_CHEST, WAIT_FOR_BOOK, DEPOSIT_ONE, DONE }

    private static final int SINGLE_CHEST_SLOTS = 27;

    private final String enchantmentQuery;
    private final BlockPos chestPos;

    private Stage stage = Stage.ENSURE_CHEST;
    private boolean started;
    private boolean finished;

    private int depositedCount = 0;
    private Task sub; // for nested tasks (place/deposit)

    public ChestOfEnchantedBooksTask(String enchantmentQuery, BlockPos chestPos) {
        this.enchantmentQuery = enchantmentQuery;
        this.chestPos = chestPos;
    }

    /* ===== Task lifecycle (no-arg API) ===== */

    @Override
    protected void onStart() {
        started = true;
        finished = false;
        stage = Stage.ENSURE_CHEST;
        sub = null;
        depositedCount = 0;
    }

    @Override
    protected Task onTick() {
        if (!started) onStart();

        final MinecraftClient mc = MinecraftClient.getInstance();
        final ClientPlayerEntity player = (mc != null) ? mc.player : null;
        final World world = (mc != null) ? mc.world : null;
        if (mc == null || player == null || world == null) return null;

        // Count how many target books we already stashed by simply tracking after each deposit.
        // If you want to *verify* chest contents, you can extend this later to read the chest UI.

        switch (stage) {
            case ENSURE_CHEST -> {
                if (world.getBlockState(chestPos).isOf(Blocks.CHEST)) {
                    stage = Stage.WAIT_FOR_BOOK;
                    return null;
                }
                // If we carry a chest item, place it. Otherwise wait (parent/user must supply one).
                boolean haveChestItem = hasHotbarOrInvItem(player, Items.CHEST);
                if (haveChestItem) {
                    sub = new PlaceBlockSmartTask(chestPos, Blocks.CHEST);
                    stage = Stage.WAIT_FOR_BOOK; // advance once placed
                    return sub;
                } else {
                    // No chest item available. We just idle; user or a parent task must provide it.
                    return null;
                }
            }

            case WAIT_FOR_BOOK -> {
                if (depositedCount >= SINGLE_CHEST_SLOTS) {
                    stage = Stage.DONE;
                    return null;
                }
                // Check player's own inventory directly (no AltoClef instance needed)
                if (hasMatchingBookInInventory(player, enchantmentQuery)) {
                    stage = Stage.DEPOSIT_ONE;
                }
                return null;
            }


            case DEPOSIT_ONE -> {
                // Deposit all matching books currently in inventory (usually 1, but we don’t assume)
                sub = new DepositEnchantedBooksInChestTask(enchantmentQuery, chestPos, () -> {
                    // we conservatively increment by 1 (minimum) to make forward progress;
                    // if you want exact accounting, count chest contents after opening it.
                    depositedCount = Math.min(SINGLE_CHEST_SLOTS, depositedCount + 1);
                    stage = (depositedCount >= SINGLE_CHEST_SLOTS) ? Stage.DONE : Stage.WAIT_FOR_BOOK;
                });
                return sub;
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
        // no-op; subtasks clean themselves up
    }

    @Override
    public boolean isEqual(Task other) {
        if (!(other instanceof ChestOfEnchantedBooksTask o)) return false;
        return Objects.equals(this.enchantmentQuery, o.enchantmentQuery)
                && Objects.equals(this.chestPos, o.chestPos);
    }

    @Override
    protected String toDebugString() {
        return "ChestOfEnchantedBooks(" + enchantmentQuery + ") @ " + chestPos.toShortString()
                + " deposited=" + depositedCount + "/" + SINGLE_CHEST_SLOTS + " stage=" + stage;
    }

    /* ===== helpers ===== */
    private static boolean hasMatchingBookInInventory(ClientPlayerEntity player, String enchantmentQuery) {
        // main inventory
        for (int i = 0; i < 36; i++) {
            if (InventoryHelpers.isEnchantedBookWith(player.getInventory().getStack(i), enchantmentQuery)) {
                return true;
            }
        }
        // offhand
        if (!player.getInventory().offHand.isEmpty()
                && InventoryHelpers.isEnchantedBookWith(player.getInventory().offHand.get(0), enchantmentQuery)) {
            return true;
        }
        return false;
    }


    private static boolean hasHotbarOrInvItem(ClientPlayerEntity player, net.minecraft.item.Item item) {
        // Hotbar 0..8
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        // Main inventory (9..35)
        for (int i = 9; i < 36; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        // Offhand
        if (!player.getInventory().offHand.isEmpty()
                && player.getInventory().offHand.get(0).getItem() == item) return true;
        return false;
    }

    /**
     * Tiny bridge because this file doesn’t import your AltoClef singleton directly.
     * Replace with your real accessor if you have one handy.
     */

}
