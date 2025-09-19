# ChatGPTClef

## Short Description
ChatGPTClef is a client-side Minecraft (Fabric) AI copilot that can autonomously perform tasks (from gathering resources to beating the game) or accompany you as a second player. Originally built on Player2/AltoClef/Baritone, this variant can directly use the OpenAI Chat Completions API (JSON Schema output). Player2 acts as a fallback if no OpenAI API key is present.

## Key Features
- Automates complex gameplay (farming, crafting, exploration, combat, defeating the Ender Dragon)
- Voice/Text interaction (default hotkey: `Z`) – configurable
- Context awareness: maintains conversation & status history (inventory, world, agent state)
- Command / task pipeline via prefix `@` (e.g. `@gamer`, `@stop`)
- Modular LLM backend layer (OpenAI or Player2 fallback)
- Multi-version build (multiple Minecraft versions in one repository)
- Adjustable system prompt & extensible commands

---
## Table of Contents
1. Quick Start
2. OpenAI Backend Configuration
3. Installation & Multi-Version Build
10. Security & Notes
11. License
12. Example Session

(Sections 5–9 were listed in the German table of contents but not expanded there; placeholders are kept here for structural parity. If you want them filled, let me know.)

---
## 1. Quick Start
1. Install Java (Temurin/OpenJDK). Use Java 21 for 1.20.6+; Java 17 for older versions (< 1.20.6).  
2. Clone the repository.  
3. Open it in IntelliJ (recommended) and wait for Gradle sync.  
4. Create an OpenAI API key and set it as an environment variable (see section 2).  
5. Run the Gradle task `:1.21.1:runClient` (or the target version subproject).  
6. (Reserved for future: configure keybinds/extra setup.)

Prebuilt jars: See releases – each version produces `<mcVersion>-<mod_version>.jar`.
When using prebuild: Use in Prism Launcher and add an Enviroment Variable like this:
https://prnt.sc/p9x5staXs7eQ

---
## 2. OpenAI Backend Configuration
At startup the code checks:
- `OPENAI_API_KEY` – if present and non-empty, the OpenAI backend is used.
- Optional: `OPENAI_MODEL` (default: `gpt-4o-mini`)
- Optional: `OPENAI_BASE_URL` (default: `https://api.openai.com`, useful for Azure/OpenAI-compatible proxies)

If no key is found, ChatGPTClef automatically falls back to the Player2 backend (if the Player2 app is running).

### 2.1 Possible Errors / Messages
- Without key: log shows `[ChatGPTClef] Using Player2 backend`.
- With key: log shows `[ChatGPTClef] Using OpenAI backend`.
- Error `OPENAI_API_KEY missing`: code attempted to initialize the OpenAI backend without a valid key.

---
## 3. Installation & Multi-Version Build
### 3.1 Requirements
- Git
- Java 21 (for 1.20.6+) or Java 17 (for older versions)
- Gradle Wrapper (included)

### 3.2 Clone Project
```
git clone <repo-url>
cd chat-gpt-clef-main
```

### 3.3 Build All Jars
```
./gradlew build        # Linux/macOS
gradlew.bat build      # Windows
```
Artifacts are placed under: `versions/<mcVersion>/build/libs/`.

### 3.4 Run a Specific Version
```
gradlew.bat :1.21.1:runClient
```
(Replace `1.21.1` with another supported version, e.g. `:1.20.6:runClient`.)

### 3.5 Collect All Version Jars
Use the script: `gather_jars.sh` (Unix) or `gather_jars.ps1` (PowerShell). Copies jars into `./build`.

---
## 10. Security & Notes
- Never publish or commit your OpenAI key.
- Logs may contain conversation fragments; sanitize before sharing.
- Multiplayer: respect server rules—automation can be disallowed.
- Use at your own risk—no guarantee of flawless behavior.

---
## 11. License
See the `LICENSE` file. Upstream projects: AltoClef, Baritone, Player2 (thanks to their maintainers & communities).

---
## 12. Short Example Session (OpenAI active)
1. Start the game:  
   ```
   gradlew :1.21.1:runClient
   ```
2. In chat: `Can you get me 10 iron?`  
3. The AI plans a route and mines resources.  
4. Ask the AI for status (just talk/type).  
5. Tell it to stop (e.g. say "Stop" or run `@stop`).

Enjoy experimenting with ChatGPTClef!

---
## Next Steps (Optional Enhancements)
If you would like, I can expand the placeholder sections (5–9) or add:
- Detailed architecture diagram
- Command reference table
- Version adding walkthrough
- Troubleshooting matrix

Let me know and I will extend this English version accordingly.

