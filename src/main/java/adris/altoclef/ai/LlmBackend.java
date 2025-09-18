package adris.altoclef.ai;

import com.google.gson.JsonObject;
import adris.altoclef.player2api.ConversationHistory;

public interface LlmBackend {
    /** Muss ein Json mit mind. "message" liefern; "command" & "reason" optional. */
    JsonObject complete(ConversationHistory historyWithStatus) throws Exception;
    default String name() { return getClass().getSimpleName(); }
}
