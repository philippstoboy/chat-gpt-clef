package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

public class FollowPlayerTask extends Task {

    private final String _playerName;

    private final double _followDistance;

    public FollowPlayerTask(String playerName, double followDistance) {
        _playerName = playerName;
        _followDistance = followDistance;
    }

    public FollowPlayerTask(String playerName) {
        this(playerName, 2);
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        Optional<Vec3d> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(_playerName);

        if (lastPos.isEmpty()) {
            setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
            return null;
        }
        Vec3d target = lastPos.get();

        if (target.isInRange(mod.getPlayer().getPos(), 1) && !mod.getEntityTracker().isPlayerLoaded(_playerName)) {
            mod.logWarning("Failed to get to player \"" + _playerName + "\". We moved to where we last saw them but now have no idea where they are.");
            stop();
            return null;
        }

        Optional<PlayerEntity> player = mod.getEntityTracker().getPlayerEntity(_playerName);
        if (player.isEmpty()) {
            // Go to last location
            return new GetToBlockTask(new BlockPos((int) target.x, (int) target.y, (int) target.z), false);
        }
        return new GetToEntityTask(player.get(), _followDistance);
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof FollowPlayerTask task) {
            return task._playerName.equals(_playerName) && Math.abs(_followDistance - task._followDistance) < 0.1;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Going to player " + _playerName;
    }
}
