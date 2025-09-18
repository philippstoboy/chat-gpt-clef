package adris.altoclef.ai;

import com.google.gson.JsonObject;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.Player2APIService;

public final class Player2Backend implements LlmBackend {
  @Override
  public JsonObject complete(ConversationHistory historyWithStatus) throws Exception {
    return Player2APIService.completeConversation(historyWithStatus);
  }
}
