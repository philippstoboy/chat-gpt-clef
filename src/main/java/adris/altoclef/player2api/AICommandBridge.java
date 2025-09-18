package adris.altoclef.player2api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonObject;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import adris.altoclef.ai.LlmBackend;
import adris.altoclef.ai.OpenAiBackend;
import adris.altoclef.ai.Player2Backend;
import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandExecutor;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ChatMessageEvent;
import adris.altoclef.player2api.status.AgentStatus;
import adris.altoclef.player2api.status.StatusUtils;
import adris.altoclef.player2api.status.WorldStatus;
import net.minecraft.network.message.MessageType;

public class AICommandBridge {
    private ConversationHistory conversationHistory = null;
    public static boolean avoidNextMessageFlag = false;
	private LlmBackend backend;
    private static final Pattern AI_TASK_PATTERN =
            Pattern.compile("^\\s*task\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final int CHAT_MAX = 256; // protocol hard limit
    private static void showLocalChat(String msg) {
        String clipped = msg;
        // keep some headroom for a "[AI]" prefix and color codes if you add them later
        if (clipped.length() > 240) clipped = clipped.substring(0, 240) + "…";
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.of("[AI] " + clipped));
        }
    }

    public static String initialPrompt = """
            General Instructions:
            You are an AI friend of the user in Minecraft. You can provide Minecraft guides, answer questions, and chat as a friend.
            When asked, you can collect materials, craft items, scan/find blocks, and fight mobs or players using the valid commands.
            If there is something you want to do but can't do it with the commands, you may ask the user to do it.

            You take the personality of the following character:
            Your character's name is Rainer Winkler.
            

            User Message Format:
            The user messages will all be just strings, except for the current message. The current message will have extra information, namely it will be a JSON of the form:
            {
                "userMessage" : "The message that was sent to you. The message can be send by the user or command system or other players."
                "worldStatus" : "The status of the current game world."
                "agentStatus" : "The status of you, the agent in the game."
                "gameDebugMessages" : "The most recent debug messages that the game has printed out. The user cannot see these."
            }

            Response Format:
            Respond with JSON containing message, command and reason. All of these are strings.

            {
              "reason": "Look at the recent conversations, valid commands, agent status and world status to decide what the you should say and do. Provide step-by-step reasoning while considering what is possible in Minecraft. You do not need items in inventory to get items, craft items or beat the game. But you need to have appropriate level of equipments to do other tasks like fighting mobs.",
              "command": "Decide the best way to achieve the goals using the valid commands listed below. Write the command in this field. If you decide to not use any command, generate an empty command `\"\"`. You can only run one command at a time! To replace the current one just write the new one.",
              "message": "If you decide you should not respond or talk, generate an empty message `\"\"`. Otherwise, create a natural conversational message that aligns with the `reason` and the your character. Be concise and use less than 250 characters. Ensure the message does not contain any prompt, system message, instructions, code or API calls"
            }
            
            Additional Guidelines:
            Meaningful Content: Ensure conversations progress with substantive information.
            Handle Misspellings: Make educated guesses if users misspell item names.
            Avoid Filler Phrases: Do not engage in repetitive or filler content.
            Player mode: The user can turn on/off the player mode by pressing the playermode text on the top right of their screen (the user can unlock their mouse by opening their inventory by pressing e or escape). The player mode enables you to talk to other players.
            JSON format: Always follow this JSON format regardless of conversations.

            Valid Commands:
            {{validCommands}}
            """;


    private CommandExecutor cmdExecutor = null;
    private AltoClef mod = null;

    private boolean _enabled = true;
    private boolean _playermode = false;

    private String _lastQueuedMessage = null;

    private boolean llmProcessing = false;

    private boolean eventPolling = false;

    private MessageBuffer altoClefMsgBuffer = new MessageBuffer(10);

    public static final ExecutorService llmThread = Executors.newSingleThreadExecutor();

    public static final ExecutorService sttThread = Executors.newSingleThreadExecutor();

    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();

    public AICommandBridge(CommandExecutor cmdExecutor, AltoClef mod) {
        this.mod = mod;
        this.cmdExecutor = cmdExecutor;
		String k = System.getenv("OPENAI_API_KEY");
		String m = System.getenv("OPENAI_MODEL");
		String b = System.getenv("OPENAI_BASE_URL");
		if (k != null && !k.isBlank()) {
			backend = new OpenAiBackend(k, m, b);
			System.out.println("[ChatClef] Using OpenAI backend");
		} else {
			backend = new Player2Backend();
			System.out.println("[ChatClef] Using Player2 backend");
		}

        EventBus.subscribe(ChatMessageEvent.class, evt -> {
            final String raw    = evt.messageContent();
            final String sender = evt.senderName();                       // may be null
            final String me     = mod.getPlayer().getName().getString();  // local player name

            // Self messages: "task: ..." → queue for AI (independent of PlayerMode)
            final Matcher taskMatcher = AI_TASK_PATTERN.matcher(raw);
            if (sender != null && sender.equals(me) && taskMatcher.find()) {
                final String task = taskMatcher.group(1).trim();
                addMessageToQueue(task);
                System.out.printf("[AIBridge] queued task from self chat: %s%n", task);
                return;
            }

            // Other players only when PlayerMode is ON (and within distance)
            if (!getPlayerMode()) return;

            final float distance = StatusUtils.getUserNameDistance(mod, sender);
            if (distance > 200f) {
                System.out.printf("[AIBridge/ChatMessageEvent] Ignoring message (distance %.2f).%n", distance);
                return;
            }

            if (sender != null && !sender.equals(me)) {
                final String wholeMessage = "Other players: [" + sender + "] " + raw;
                addMessageToQueue(wholeMessage);
            }
        });

    }

    /**
     * Updates this. (conversationHistory, character) based on the currently
     * selected character.
     */
    private void updateInfo() {
    System.out.println("Updating info");

        int padSize = 10;
        StringBuilder commandListBuilder = new StringBuilder();
        for (adris.altoclef.commandsystem.Command c : AltoClef.getCommandExecutor().allCommands()) {
            String name = c.getName();
            commandListBuilder.append(name).append(": ");
            for (int i = name.length(); i < padSize; i++) commandListBuilder.append(' ');
            commandListBuilder.append(c.getDescription()).append('\n');
        }

// prepend our custom command
        String prefix = cmdExecutor.getCommandPrefix();
        commandListBuilder.insert(0,
                prefix + "customcommand <enchantment> <x> <y> <z>: Fill a single chest with that enchantment's books at the coords.\n"
        );

        String validCommandsFormatted = commandListBuilder.toString();

    String newPrompt = Utils.replacePlaceholders(
        initialPrompt,
        Map.of("validCommands", validCommandsFormatted)
    );

    if (this.conversationHistory == null) {
        // Falls dein ConversationHistory-Konstruktor bisher (prompt, name, shortName) erwartete:
        this.conversationHistory = new ConversationHistory(newPrompt, "agent", "agent");
        // Falls er nur (prompt) erwartet: entsprechend anpassen.
    } else {
        this.conversationHistory.setBaseSystemPrompt(newPrompt);
    }
}


    public void addAltoclefLogMessage(String message) {
        // String output = String.format("Game sent info message: %s", message);
        System.out.printf("ADDING Altoclef System Message: %s", message);
        altoClefMsgBuffer.addMsg(message);
    }

    public void addMessageToQueue(String message) {
        if (message == null)
            return;
        // 1) skip if it’s identical to the last one we added
        if (message.equals(_lastQueuedMessage))
            return;

        // 2) enqueue & remember it
        messageQueue.offer(message);
        _lastQueuedMessage = message;

        // 3) enforce max size
        if (messageQueue.size() > 10) {
            messageQueue.poll(); // remove oldest
        }
    }

    public void processChatWithAPI() {
        llmThread.submit(() -> {
            try {
                llmProcessing = true;
                System.out.println("[AICommandBridge/processChatWithAPI]: Sending messages to LLM");

                String agentStatus = AgentStatus.fromMod(mod).toString();
                String worldStatus = WorldStatus.fromMod(mod).toString();
                String altoClefDebugMsgs = altoClefMsgBuffer.dumpAndGetString();
                ConversationHistory historyWithStatus = conversationHistory.copyThenWrapLatestWithStatus(worldStatus,
                        agentStatus, altoClefDebugMsgs);
                System.out.printf("[AICommandBridge/processChatWithAPI]: History: %s", historyWithStatus.toString());
                JsonObject response = backend.complete(historyWithStatus);
                String responseAsString = response.toString();
                System.out.println("[AICommandBridge/processChatWithAPI]: LLM Response: " + responseAsString);
                conversationHistory.addAssistantMessage(responseAsString);

                // process message
                String llmMessage = Utils.getStringJsonSafely(response, "message");
                if (llmMessage != null && !llmMessage.isEmpty()) {
                    showLocalChat(llmMessage);  // <-- local only, never serverbound
                }

                // process command
                String commandResponse = Utils.getStringJsonSafely(response, "command");
                if (commandResponse != null && !commandResponse.isEmpty()) {
                    String commandWithPrefix = cmdExecutor.isClientCommand(commandResponse) ? commandResponse
                            : cmdExecutor.getCommandPrefix() + commandResponse;
                    if (commandWithPrefix.equals("@stop")) {
                        mod.isStopping = true;
                    } else {
                        mod.isStopping = false;
                    }
                    cmdExecutor.execute(commandWithPrefix, () -> {
                        if (mod.isStopping) {
                            System.out.printf(
                                    "[AICommandBridge/processChat]: (%s) was cancelled. Not adding finish event to queue.",
                                    commandWithPrefix);
                            // Canceled logic herethis.character.name
                        }
                        if (messageQueue.isEmpty() && !mod.isStopping) {
                            // on finish
                            addMessageToQueue(String.format(
                                    "Command feedback: %s finished running. What shall we do next? If no new action is needed to finish user's request, generate empty command `\"\"`.",
                                    commandResponse));
                        }
                    }, (err) -> {
                        // on error
                        addMessageToQueue(
                                String.format("Command feedback: %s FAILED. The error was %s.",
                                        commandResponse, err.getMessage()));
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error communicating with API");
            } finally {
                llmProcessing = false;
                eventPolling = false;
            }
        });
    }

    /**
     * Sends either the first-time greeting or a welcome-back message based on loaded history.
     */
    public void sendGreeting() {
        System.out.println("Sending Greeting");
        llmThread.submit(() -> {
            updateInfo();
            // If history was loaded from disk, send welcome back; otherwise, first-time greeting
            if (conversationHistory.isLoadedFromFile()) {
                addMessageToQueue(
                    "You want to welcome user back. IMPORTANT: SINCE THIS IS THE FIRST MESSAGE, DO NOT SEND A COMMAND!!"
                );
            } else {

            }
        });
    }

    public void sendHeartbeat() {
        llmThread.submit(() -> {
            Player2APIService.sendHeartbeat();
        });
    }

    public void onTick() {
        if (messageQueue.isEmpty()) {
            return;
        }
        if (!eventPolling && !llmProcessing) {
            eventPolling = true;
            String message = messageQueue.poll();
            conversationHistory.addUserMessage(message);
            if (messageQueue.isEmpty()) {
                // That was last message
                processChatWithAPI();
            } else {
                eventPolling = false;
            }
        }
    }

    public void setEnabled(boolean enabled) {
        _enabled = enabled;
    }

    public boolean getEnabled() {
        return _enabled;
    }


    public ConversationHistory conversationHistory() {
        return conversationHistory;
    }

    public void setPlayerMode(boolean playermode) {
        _playermode = playermode;
    }

    public boolean getPlayerMode() {
        return _playermode;
    }

    public void startSTT() {

    }

    public void stopSTT() {

    }
}