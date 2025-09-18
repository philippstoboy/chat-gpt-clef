package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.BotBehaviour;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.FollowPlayerTask;
import adris.altoclef.tasks.movement.RunAwayFromPositionTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasks.slot.ThrowCursorTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class GiveItemToPlayerTask extends Task {

    private final String playerName;
    private final ItemTarget[] targets;

    private final CataloguedResourceTask resourceTask;
    private final List<ItemTarget> throwTarget = new ArrayList<>();
    private boolean droppingItems;

    private Task throwTask;

    // Wait when we pick up and throw, gives the task some time to do slot actions and complete them.
    private TimerGame _throwTimeout = new TimerGame(0.4);

    public GiveItemToPlayerTask(String player, ItemTarget... targets) {
        playerName = player;
        this.targets = targets;
        resourceTask = TaskCatalogue.getSquashedItemTask(targets);
    }

    @Override
    protected void onStart() {
        droppingItems = false;
        throwTarget.clear();

        BotBehaviour botBehaviour = AltoClef.getInstance().getBehaviour();

        botBehaviour.push();
        botBehaviour.addProtectedItems(ItemTarget.getMatches(targets));
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (throwTask != null && throwTask.isActive() && !throwTask.isFinished()) {
            setDebugState("Throwing items");
            return throwTask;
        }

        Optional<Vec3d> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(playerName);

        if (lastPos.isEmpty()) {
            String nearbyUsernames = String.join(",", mod.getEntityTracker().getAllLoadedPlayerUsernames());
            fail("No user in render distance found with username \"" + this.playerName + "\". Maybe this was a typo or there is a user with a similar name around? Nearby users: [" + nearbyUsernames + "].");
            return null;
        }
        Vec3d targetPos = lastPos.get().add(0, 0.2f, 0);

        if (droppingItems) {
            setDebugState("Throwing items");

            // Throw at an interval
            if (!_throwTimeout.elapsed()) {
                // wait
                return null;
            }
            _throwTimeout.reset();

            // THROW ITEMS
            LookHelper.lookAt(mod, targetPos);
            for (int i = 0; i < throwTarget.size(); ++i) {
                ItemTarget target = throwTarget.get(i);
                if (target.getTargetCount() > 0) {
                    Optional<Slot> has = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, target.getMatches()).stream().findFirst();
                    if (has.isPresent()) {
                        Slot currentlyPresent = has.get();
                        System.out.println("Currently present: " + currentlyPresent);
                        if (Slot.isCursor(currentlyPresent)) {
                            ItemStack stack = StorageHelper.getItemStackInSlot(currentlyPresent);
                            // Update target
                            target = new ItemTarget(target, target.getTargetCount() - stack.getCount());
                            throwTarget.set(i, target);
                            Debug.logMessage("THROWING: " + has.get());
                            return new ThrowCursorTask();
                        } else {
                            mod.getSlotHandler().clickSlot(currentlyPresent, 0, SlotActionType.PICKUP);
                            return null;
                        }
                    }
                }
            }

            // STOP timing after this point.
            _throwTimeout.forceElapse();

            if (!targetPos.isInRange(mod.getPlayer().getPos(), 4)) {
                mod.log("Finished giving items.");
                stop();
                return null;
            }
            return new RunAwayFromPositionTask(6, WorldHelper.toBlockPos(targetPos));
        }

        if (!StorageHelper.itemTargetsMet(mod, targets)) {
            setDebugState("Collecting resources...");
            return resourceTask;
        }

        if (targetPos.isInRange(mod.getPlayer().getPos(), 4)) {
            if (!mod.getEntityTracker().isPlayerLoaded(playerName)) {
                String nearbyUsernames = String.join(",", mod.getEntityTracker().getAllLoadedPlayerUsernames());
                fail("Failed to get to player \"" + this.playerName + "\". We moved to where we last saw them but now have no idea where they are. Nearby players: [" + nearbyUsernames + "]");
                return null;
            }

            var p = mod.getEntityTracker().getPlayerEntity(playerName).get();

            // We must be At or ABOVE the player (or super close)
            if (p.getBlockPos().getY() <= mod.getPlayer().getBlockPos().getY() || p.getPos().distanceTo(mod.getPlayer().getPos()) <= 0.5) {
                // We must be able to SEE the player we're dropping to
                if (LookHelper.seesPlayer(p, mod.getPlayer(), 6)) {
                    droppingItems = true;
                    throwTarget.addAll(Arrays.asList(targets));
        
                    _throwTimeout.reset();    
                }
            }
        }

        setDebugState("Going to player...");
        return new FollowPlayerTask(playerName, 0.5);
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GiveItemToPlayerTask task) {
            if (!task.playerName.equals(playerName)) return false;
            return Arrays.equals(task.targets, targets);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Giving items to " + playerName;
    }
}
