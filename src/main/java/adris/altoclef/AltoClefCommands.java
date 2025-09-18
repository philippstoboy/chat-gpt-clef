package adris.altoclef;

import adris.altoclef.commands.*;
import adris.altoclef.commands.random.ScanCommand;
import adris.altoclef.commandsystem.CommandException;

/**
 * Initializes altoclef's built in commands.
 */
public class AltoClefCommands {

    public static void init() throws CommandException {
        // List commands here
        AltoClef.getCommandExecutor().registerNewCommand(
                new HelpCommand(),
                new GetCommand(),
                new EquipCommand(),
                new DepositCommand(),
                // disabled: not useful to LLM agent
                // new StashCommand(),
                new CustomCommand(),
                new GotoCommand(),
                new IdleCommand(),
                new HeroCommand(),
                new LocateStructureCommand(),
                new StopCommand(),
                new SetGammaCommand(),
                new FoodCommand(),
                new MeatCommand(),
                new ReloadSettingsCommand(),
                new ResetMemoryCommand(),
                new GamerCommand(),
                new FollowCommand(),
                new GiveCommand(),
                new ScanCommand(),
                new AttackPlayerOrMobCommand(),
                new SetAIBridgeEnabledCommand()
        );
    }
}
