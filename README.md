# ChatGPTClef

## Kurzbeschreibung
ChatGPTClef ist ein clientseitiger Minecraft (Fabric) KI‑Copilot, der eigenständig Aufgaben ausführt (vom Ressourcensammeln bis zum Durchspielen) oder dich als zweiter Spieler unterstützt. Ursprünglich auf Player2/AltoClef/Baritone aufbauend, kann diese Variante direkt das OpenAI Chat Completions API (JSON Schema Output) nutzen. Player2 fungiert als Fallback, falls keine OpenAI API Keys bereitstehen.

## Kernfeatures
- Automatisches Ausführen komplexer Aufgaben (Farmen, Crafting, Exploration, Kampf, Enderdrache besiegen)
- Sprach-/Textinteraktion (Standard Hotkey: `Z`) – konfigurierbar
- Kontextbewusst: Hält Gesprächs- & Status-Historie (Inventar-/Welt-/Agentenstatus)
- Befehls- & Aufgabenpipeline über Präfix `@` (z.B. `@gamer`, `@stop`)
- Modularer LLM Backend Layer (OpenAI oder Player2 Fallback)
- Multi-Version Build (mehrere Minecraft Versionen parallel in einem Repo)
- Anpassbares System-Prompt & erweiterbare Commands

---
## Inhaltsverzeichnis
1. Schnellstart
2. OpenAI Backend Konfiguration
3. Installation & Build (Multi-Version)
4. Nutzung & Befehle
5. Speicherort von Daten & Logs
6. Hinzufügen einer neuen Minecraft Version (Beispiel 1.21.4)
7. Entwicklung / Baritone Fork Integration
8. Architekturüberblick
9. Troubleshooting & FAQ
10. Sicherheit & Hinweise
11. Lizenz

---
## 1. Schnellstart
1. Java (Temurin/OpenJDK) installieren – für 1.20.6+ wird Java 21 genutzt, darunter Java 17.
2. Repo klonen.
3. In IntelliJ (empfohlen) öffnen – Gradle sync abwarten.
4. OpenAI API Key anlegen und als Umgebungvariable setzen (siehe Abschnitt 2).
5. Gradle-Task `:1.21.1:runClient` (oder gewünschte Versions-Submodul) starten.
6. 

Prebuilt Jars: Siehe Releases – jede Version erzeugt `<mcVersion>-<mod_version>.jar`.

---
## 2. OpenAI Backend Konfiguration
Der Code prüft beim Start:
- `OPENAI_API_KEY` – wenn vorhanden & nicht leer, wird das OpenAI Backend genutzt.
- Optional: `OPENAI_MODEL` (Standard: `gpt-4o-mini`)
- Optional: `OPENAI_BASE_URL` (Standard: `https://api.openai.com` – nützlich für Azure/OpenAI-kompatible Proxys)

Wenn kein Key gefunden wird, fällt ChatGPTClef automatisch auf das Player2 Backend zurück (sofern Player2 App läuft).


### 2.1 Mögliche Fehler
Ohne Key: Log zeigt `[ChatGPTClef] Using Player2 backend`. Bei Key: `[ChatGPTClef] Using OpenAI backend`.
Ein Fehler `OPENAI_API_KEY missing` bedeutet, dass versucht wurde, das Backend ohne gültigen Key zu initialisieren.

---
## 3. Installation & Build
### 3.1 Voraussetzungen
- Git
- Java 21 (für 1.20.6+); Java 17 für ältere Versionen (<1.20.6). Projekt konfiguriert dies automatisch per `build.gradle`.
- Gradle Wrapper (liegt bei)

### 3.2 Projekt klonen
```
git clone <repo-url>
cd chat-gpt-clef-main
```

### 3.3 Alle Jars bauen
```
./gradlew build        (Linux/macOS)
gradlew.bat build      (Windows)
```
Artefakte liegen dann unter `versions/<mcVersion>/build/libs/`.

### 3.4 Einzelne Version starten
```
gradlew.bat :1.21.1:runClient
```
Andere Version analog (`:1.20.6:runClient`).

### 3.5 Alle Versionen Jars sammeln
Script: `gather_jars.sh` oder PowerShell Variante `gather_jars.ps1` (kopiert Jars nach `./build`).

---
## 10. Sicherheit & Hinweise
- OpenAI Schlüssel niemals veröffentlichen oder commiten.
- Logs können Gesprächsinhalte enthalten; bei Weitergabe anonymisieren.
- Einsatz auf Multiplayer-Servern: Beachte deren Regeln (Automatisierung kann verboten sein).
- Verantwortung: Nutzung auf eigenes Risiko – keine Garantie für fehlerfreies Verhalten.

---
## 11. Lizenz
Siehe `LICENSE` Datei. Ursprungsprojekte: AltoClef, Baritone, Player2 (danke an deren Maintainer & Community).

---


## Kurze Beispiel-Session (OpenAI aktiv)
1. Spiel starten (`gradlew :1.21.1:runClient`)
2. Frage im Chat: "Kannst du mir 10 Eisen besorgen?"
3. KI plant & führt Mining/Routen aus
4. Just talk to the AI and ask it for status
5. Just talk to the AI and tell it to Stop

Viel Spaß beim Experimentieren mit ChatGPTClef!
