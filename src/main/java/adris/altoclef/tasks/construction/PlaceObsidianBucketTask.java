package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClef;
import adris.altoclef.BotBehaviour;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commands.BlockScanner;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.Arrays;

/**
 * Places obsidian at a position using buckets and a cast.
 */
public class PlaceObsidianBucketTask extends Task {

    public static final Vec3i[] CAST_FRAME = new Vec3i[]{
            new Vec3i(0, -1, 0),
            new Vec3i(0, -1, -1),
            new Vec3i(0, -1, 1),
            new Vec3i(-1, -1, 0),
            new Vec3i(1, -1, 0),
            new Vec3i(0, 0, -1),
            new Vec3i(0, 0, 1),
            new Vec3i(-1, 0, 0),
            new Vec3i(1, 0, 0),
            new Vec3i(1, 1, 0)
    };
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker();
    private final BlockPos _pos;

    private BlockPos _currentCastTarget;
    private BlockPos _currentDestroyTarget;

    public PlaceObsidianBucketTask(BlockPos pos) {
        _pos = pos;
    }

    @Override
    protected void onStart() {
        BotBehaviour botBehaviour = AltoClef.getInstance().getBehaviour();

        // Push the behaviour onto the behaviour stack
        botBehaviour.push();

        // Avoid breaking blocks within the specified conditions
        botBehaviour.avoidBlockBreaking(this::isBlockInCastFrame);

        // Avoid placing blocks within the specified conditions
        botBehaviour.avoidBlockPlacing(this::isBlockInCastWaterOrLava);

        // Reset the progress checker
        _progressChecker.reset();

        // Logging statements for debugging
        Debug.logInternal("Started onStart method");
        Debug.logInternal("Behaviour pushed");
        Debug.logInternal("Avoiding block breaking");
        Debug.logInternal("Avoiding block placing");
        Debug.logInternal("Progress checker reset");
    }

    private boolean isBlockInCastFrame(BlockPos block) {
        return Arrays.stream(PlaceObsidianBucketTask.CAST_FRAME)
                .map(_pos::add)
                .anyMatch(block::equals);
    }

    /**
     * Checks if a given block position is either the same as the current position or the position above it.
     *
     * @param blockPos The block position to check
     * @return True if the block position is the same as the current position or the position above it, false otherwise
     */
    private boolean isBlockInCastWaterOrLava(BlockPos blockPos) {
        // Calculate the position above the current position
        BlockPos waterTarget = _pos.up();

        // Logging statement for debugging
        Debug.logInternal("blockPos: " + blockPos);
        Debug.logInternal("waterTarget: " + waterTarget);

        // Check if the block position is either the same as the current position or the position above it
        return blockPos.equals(_pos) || blockPos.equals(waterTarget);
    }

    /**
     * This method is called periodically to perform a specific task.
     * It handles the logic for casting a spell using lava and water buckets.
     *
     * @return The next task to be executed
     */
    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        // Reset progress if pathing
        if (mod.getClientBaritone().getPathingBehavior().isPathing()) {
            _progressChecker.reset();
        }

        // Clear leftover water
        if (mod.getBlockScanner().isBlockAtPosition(_pos, Blocks.OBSIDIAN) && mod.getBlockScanner().isBlockAtPosition(_pos.up(), Blocks.WATER)) {
            return new ClearLiquidTask(_pos.up());
        }

        // Make sure we have a water bucket
        if (!mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
            _progressChecker.reset();
            return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
        }

        // Make sure we have a lava bucket
        if (!mod.getItemStorage().hasItem(Items.LAVA_BUCKET)) {
            // The only excuse is that we have lava at our position.
            if (!mod.getBlockScanner().isBlockAtPosition(_pos, Blocks.LAVA)) {
                _progressChecker.reset();
                return TaskCatalogue.getItemTask(Items.LAVA_BUCKET, 1);
            }
        }

        // Check progress
        if (!_progressChecker.check(mod)) {
            mod.getClientBaritone().getPathingBehavior().forceCancel();
            mod.getBlockScanner().requestBlockUnreachable(_pos);
            _progressChecker.reset();
            return new TimeoutWanderTask(5);
        }

        // Build cast frame if not already built
        if (_currentCastTarget != null) {
            if (WorldHelper.isSolidBlock(_currentCastTarget)) {
                _currentCastTarget = null;
            } else {
                return new PlaceBlockTask(_currentCastTarget,
                        Arrays.stream(ItemHelper.itemsToBlocks(mod.getModSettings().getThrowawayItems(mod))).filter((b)-> !Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.LEAVES)).toList().contains(b)).toArray(Block[]::new)
                );
            }
        }

        // Destroy block if needed
        if (_currentDestroyTarget != null) {
            if (!WorldHelper.isSolidBlock(_currentDestroyTarget)) {
                _currentDestroyTarget = null;
            } else {
                return new DestroyBlockTask(_currentDestroyTarget);
            }
        }

        // Build the cast frame if not already built
        if (_currentCastTarget != null && WorldHelper.isSolidBlock(_currentCastTarget)) {
            // Current cast frame already built.
            _currentCastTarget = null;
        }
        for (Vec3i castPosRelative : CAST_FRAME) {
            BlockPos castPos = _pos.add(castPosRelative);
            if (!WorldHelper.isSolidBlock(castPos)) {
                _currentCastTarget = castPos;
                Debug.logInternal("Building cast frame...");
                return null;
            }
        }

        // Place lava
        if (mod.getWorld().getBlockState(_pos).getBlock() != Blocks.LAVA) {
            // Don't place lava at our position!
            // Would lead to an embarrassing death.
            BlockPos targetPos = _pos.add(-1,1,0);
            if (!mod.getPlayer().getBlockPos().equals(targetPos) && mod.getItemStorage().hasItem(Items.LAVA_BUCKET)) {
                Debug.logInternal("Positioning player before placing lava...");
                return new GetToBlockTask(targetPos, false);
            }
            if (WorldHelper.isSolidBlock(_pos)) {
                Debug.logInternal("Clearing space around lava...");
                _currentDestroyTarget = _pos;
                return null;
            }
            // Clear the upper two as well, to make placing more reliable.
            if (WorldHelper.isSolidBlock(_pos.up())) {
                Debug.logInternal("Clearing space around lava...");
                _currentDestroyTarget = _pos.up();
                return null;
            }
            if (WorldHelper.isSolidBlock(_pos.up(2))) {
                Debug.logInternal("Clearing space around lava...");
                _currentDestroyTarget = _pos.up(2);
                return null;
            }
            Debug.logInternal("Placing lava for cast...");
            return new InteractWithBlockTask(new ItemTarget(Items.LAVA_BUCKET, 1), Direction.WEST, _pos.add(1,0,0), false);
        }
        // Lava placed, Now, place water.
        BlockPos waterCheck = _pos.up();
        if (mod.getWorld().getBlockState(waterCheck).getBlock() != Blocks.WATER) {
            Debug.logInternal("Placing water for cast...");
            // Get to position to avoid weird stuck scenario
            BlockPos targetPos = _pos.add(-1,1,0);
            if (!mod.getPlayer().getBlockPos().equals(targetPos) && mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                Debug.logInternal("Positioning player before placing water...");
                return new GetToBlockTask(targetPos, false);
            }
            if (WorldHelper.isSolidBlock(waterCheck)) {
                _currentDestroyTarget = waterCheck;
                return null;
            }
            if (WorldHelper.isSolidBlock(waterCheck.up())) {
                _currentDestroyTarget = waterCheck.up();
                return null;
            }
            return new InteractWithBlockTask(new ItemTarget(Items.WATER_BUCKET, 1), Direction.WEST, _pos.add(1,1,0), true);
        }
        return null;
    }

    /**
     * This method is called when the task is interrupted.
     *
     * @param interruptTask The task that caused the interruption.
     */
    @Override
    protected void onStop(Task interruptTask) {
        // Check if the mod's behaviour is not null
        if (AltoClef.getInstance().getBehaviour() != null) {
            // Pop the behaviour from the stack
            AltoClef.getInstance().getBehaviour().pop();
            // Log a message indicating that the behaviour was popped
            Debug.logInternal("Behaviour popped.");
        }
    }

    /**
     * Check if the current task is finished.
     * The task is considered finished if the block at the specified position is obsidian
     * and there is no water block above it.
     *
     * @return True if the task is finished, False otherwise.
     */
    @Override
    public boolean isFinished() {
        // Get the BlockTracker instance from the mod
        BlockScanner blockTracker = AltoClef.getInstance().getBlockScanner();

        // Get the position of the block to check
        BlockPos pos = _pos;

        // Check if the block at the specified position is obsidian
        boolean isObsidian = blockTracker.isBlockAtPosition(pos, Blocks.OBSIDIAN);
        Debug.logInternal("isObsidian: " + isObsidian);

        // Check if there is no water block above the specified position
        boolean isNotWaterAbove = !blockTracker.isBlockAtPosition(pos.up(), Blocks.WATER);
        Debug.logInternal("isNotWaterAbove: " + isNotWaterAbove);

        // The task is considered finished if the block is obsidian and there is no water above
        boolean isFinished = isObsidian && isNotWaterAbove;
        Debug.logInternal("isFinished: " + isFinished);

        return isFinished;
    }

    /**
     * Checks if the given task is equal to this PlaceObsidianBucketTask.
     * Two PlaceObsidianBucketTasks are considered equal if their positions are equal.
     * Overrides the isEqual() method from the parent class.
     *
     * @param other the task to compare with
     * @return true if the tasks are equal, false otherwise
     */
    @Override
    protected boolean isEqual(Task other) {
        // Check if the other task is an instance of PlaceObsidianBucketTask
        if (other instanceof PlaceObsidianBucketTask task) {
            // Check if the positions are equal
            boolean isEqual = task.getPos().equals(getPos());
            // Log the result of the comparison
            Debug.logInternal("isEqual: " + isEqual);
            // Return the result
            return isEqual;
        }
        // Log that the tasks are not equal
        Debug.logInternal("isEqual: false");
        // Return false
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Placing obsidian at " + _pos + " with a cast";
    }

    /**
     * Retrieves the position of the object.
     *
     * @return The position of the object.
     */
    public BlockPos getPos() {
        // Added logging statement for debugging
        Debug.logInternal("Entering getPos()");

        return _pos;
    }
}
