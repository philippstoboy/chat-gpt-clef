package adris.altoclef.ai;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import adris.altoclef.player2api.ConversationHistory;

public final class OpenAiBackend implements LlmBackend {
  private final String apiKey;
  private final String model;
  private final String baseUrl;

  public OpenAiBackend(String apiKey, String model, String baseUrl) {
    if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("OPENAI_API_KEY missing");
    this.apiKey = apiKey;
    this.model  = (model == null || model.isBlank()) ? "gpt-4o-mini" : model;
    this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com" : baseUrl;
  }

  @Override
  public JsonObject complete(ConversationHistory historyWithStatus) throws Exception {
    // ---- JSON-Schema für strukturierten Output {message, command, reason}
    JsonObject props = new JsonObject();
    props.add("message", prop("string", "Chat output (<250 chars)"));
    props.add("command", prop("string", "ChatClef command or empty string"));
    props.add("reason",  prop("string", "Short internal rationale"));
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", props);
    schema.add("required", arr("message"));
    schema.addProperty("additionalProperties", false);
    JsonObject jsonSchema = new JsonObject();
    jsonSchema.addProperty("name", "ChatClefCommand");
    jsonSchema.add("schema", schema);

    // ---- Messages aus ConversationHistory übernehmen (Systemprompt ist dort enthalten)
    JsonArray messages = new JsonArray();
    for (JsonObject msg : historyWithStatus.getListJSON()) messages.add(msg);

    JsonObject responseFormat = new JsonObject();
    responseFormat.addProperty("type", "json_schema");
    responseFormat.add("json_schema", jsonSchema);

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", messages);
    body.add("response_format", responseFormat);
    body.addProperty("temperature", 0.2);

    // ---- HTTP POST
    URL url = new URI(baseUrl + "/v1/chat/completions").toURL();
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setConnectTimeout(15000);
    conn.setReadTimeout(60000);
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
    conn.setDoOutput(true);
    try (OutputStream os = conn.getOutputStream()) {
      os.write(body.toString().getBytes(StandardCharsets.UTF_8));
    }

    int code = conn.getResponseCode();
    InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
    String resp = readAll(is);
    if (code / 100 != 2) throw new IOException("OpenAI HTTP " + code + ": " + resp);

    JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
    String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                         .getAsJsonObject("message").get("content").getAsString();

    // content ist dank response_format valides JSON im gewünschten Schema
    JsonObject parsed = JsonParser.parseString(content).getAsJsonObject();
    if (!parsed.has("message")) parsed.addProperty("message", "");
    if (!parsed.has("command")) parsed.addProperty("command", "");
    if (!parsed.has("reason"))  parsed.addProperty("reason",  "");
    return parsed;
  }

  private static JsonObject prop(String type, String desc) {
    JsonObject o = new JsonObject();
    o.addProperty("type", type);
    o.addProperty("description", desc);
    return o;
  }
  private static JsonArray arr(String... vals) {
    JsonArray a = new JsonArray();
    for (String v : vals) a.add(v);
    return a;
  }
  private static String readAll(InputStream is) throws IOException {
    if (is == null) return "";
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      for (String line; (line = br.readLine()) != null; ) sb.append(line);
      return sb.toString();
    }
  }
}
