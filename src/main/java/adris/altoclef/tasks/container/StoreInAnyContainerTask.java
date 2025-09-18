package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Dumps items in any container, placing a chest if we can't find any.
 */
public class StoreInAnyContainerTask extends Task {

    private static final int TOO_FAR_RANGE = 50;
    private static final int TOO_FAR_RANGE_EXTRA = 70;

    private final ItemTarget[] _toStore;
    private final boolean _getIfNotPresent;
    private final HashSet<BlockPos> _dungeonChests = new HashSet<>();
    private final HashSet<BlockPos> _nonDungeonChests = new HashSet<>();
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker();
    private final ContainerStoredTracker _storedItems = new ContainerStoredTracker(slot -> true);
    private BlockPos _currentChestTry = null;

    public StoreInAnyContainerTask(boolean getIfNotPresent, ItemTarget... toStore) {
        _getIfNotPresent = getIfNotPresent;
        _toStore = toStore;
    }

    @Override
    protected void onStart() {
        _storedItems.startTracking();
        _dungeonChests.clear();
        _nonDungeonChests.clear();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        // Get more if we don't have & "get if not present" is true.
        if (_getIfNotPresent) {
            for (ItemTarget target : _toStore) {
                int inventoryNeed = target.getTargetCount() - _storedItems.getStoredCount(target.getMatches());
                if (inventoryNeed > mod.getItemStorage().getItemCount(target)) {
                    return TaskCatalogue.getItemTask(new ItemTarget(target, inventoryNeed));
                }
            }
        }

        // ItemTargets we haven't stored yet
        ItemTarget[] notStored = _storedItems.getUnstoredItemTargetsYouCanStore(mod, _toStore);

        Predicate<BlockPos> validContainer = containerPos -> {

            // If it's a chest and the block above can't be broken, we can't open this one.
            boolean isChest = WorldHelper.isChest(containerPos);
            if (isChest && WorldHelper.isSolidBlock(containerPos.up()) && !WorldHelper.canBreak(containerPos.up()))
                return false;

            //if (!_acceptableContainer.test(containerPos))
            //    return false;

            Optional<ContainerCache> data = mod.getItemStorage().getContainerAtPosition(containerPos);

            if (data.isPresent() && data.get().isFull()) return false;

            if (isChest && mod.getModSettings().shouldAvoidSearchingForDungeonChests()) {
                boolean cachedDungeon = _dungeonChests.contains(containerPos) && !_nonDungeonChests.contains(containerPos);
                if (cachedDungeon) {
                    return false;
                }
                // Spawner
                int range = 6;
                for (int dx = -range; dx <= range; ++dx) {
                    for (int dz = -range; dz <= range; ++dz) {
                        BlockPos offset = containerPos.add(dx,0,dz);
                        if (mod.getWorld().getBlockState(offset).getBlock() == Blocks.SPAWNER) {
                            _dungeonChests.add(containerPos);
                            return false;
                        }
                    }
                }
                _nonDungeonChests.add(containerPos);
            }
            return true;
        };

        Optional<BlockPos> closest = mod.getBlockScanner().getNearestBlock(StoreInContainerTask.CONTAINER_BLOCKS);
        if (closest.isPresent() && (closest.get().isWithinDistance(mod.getPlayer().getPos(), TOO_FAR_RANGE) || (_currentChestTry != null && _currentChestTry.isWithinDistance(mod.getPlayer().getPos(), TOO_FAR_RANGE_EXTRA)))) {

            setDebugState("Going to container and depositing items");

            if (!_progressChecker.check(mod) && _currentChestTry != null) {
                Debug.logMessage("Failed to open container. Suggesting it may be unreachable.");
                mod.getBlockScanner().requestBlockUnreachable(_currentChestTry, 2);
                _currentChestTry = null;
                _progressChecker.reset();
            }

            return new DoToClosestBlockTask(
                    blockPos -> {
                        if (_currentChestTry != blockPos) {
                            _progressChecker.reset();
                        }
                        _currentChestTry = blockPos;
                        return new StoreInContainerTask(blockPos, _getIfNotPresent, notStored);
                    },
                    validContainer,
                    StoreInContainerTask.CONTAINER_BLOCKS);
        }

        _progressChecker.reset();
        // Craft + place chest nearby
        for (Block couldPlace : StoreInContainerTask.CONTAINER_BLOCKS) {
            if (mod.getItemStorage().hasItem(couldPlace.asItem())) {
                setDebugState("Placing container nearby");
                return new PlaceBlockNearbyTask(canPlace -> {
                    // For chests, above must be air OR breakable.
                    if (WorldHelper.isChest(couldPlace)) {
                        return WorldHelper.isAir(canPlace.up()) || WorldHelper.canBreak(canPlace.up());
                    }
                    return true;
                }, couldPlace);
            }
        }
        setDebugState("Obtaining a chest item (by default)");
        return TaskCatalogue.getItemTask(Items.CHEST, 1);
    }

    @Override
    public boolean isFinished() {
        // We've stored all items
        return _storedItems.getUnstoredItemTargetsYouCanStore(AltoClef.getInstance(), _toStore).length == 0;
    }

    @Override
    protected void onStop(Task interruptTask) {
        _storedItems.stopTracking();
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof StoreInAnyContainerTask task) {
            return task._getIfNotPresent == _getIfNotPresent && Arrays.equals(task._toStore, _toStore);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Storing in any container: " + Arrays.toString(_toStore);
    }
}
