package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasks.custom.ChestOfEnchantedBooksTask;
import net.minecraft.util.math.BlockPos;

public class CustomCommand extends Command {

    // Usage: @customcommand <enchantment> <x> <y> <z>
    public CustomCommand() {
        super("customcommand", "Gather a single chest of <enchantment> books and store them at <x y z>");
    }

    @Override
    protected void call(AltoClef mod, ArgParser args) {
        try {
            final String enchantment = args.get(String.class);   // consumes the next arg as String
            final int x = args.get(Integer.class);               // next as Integer
            final int y = args.get(Integer.class);
            final int z = args.get(Integer.class);

            BlockPos chestPos = new BlockPos(x, y, z);
            mod.runUserTask(new ChestOfEnchantedBooksTask(enchantment, chestPos), this::finish);

        } catch (Throwable t) {
            mod.log("Usage: @customcommand <enchantment> <x> <y> <z>");
            finish();
        }
    }
}
