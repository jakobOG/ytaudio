# Minecraft YouTube Audio – Anleitung (Windows)

Die Mod spielt YouTube-Audio über den **Simple Voice Chat** ab. Andere Spieler hören es wie dein Mikrofon.
Ziel: **Minecraft 1.21.10 mit Fabric**.

## Teil 1: JAR-Datei bauen (kein Programmieren, alles bei GitHub)

1. Entpacke die ZIP-Datei. Du siehst Dateien wie `build.gradle`, `gradle.properties` und die Ordner `src` und `.github`.
2. Gehe auf https://github.com/new, gib einen Namen ein (z. B. `ytaudio`), wähle **Public** oder **Private** und klicke **Create repository**.
3. Klicke auf **uploading an existing file**. Ziehe den **gesamten Inhalt** des entpackten Ordners in das Fenster (nicht den Ordner selbst, sondern alles darin) und klicke unten **Commit changes**.
4. **Wichtig:** Prüfe, ob der Ordner `.github` hochgeladen wurde (im Repository muss `.github/workflows/build.yml` stehen). Falls nicht:
   - **Add file → Create new file**, als Name `.github/workflows/build.yml` eintippen,
   - den Inhalt der Datei `build.yml` aus der ZIP hineinkopieren und **Commit changes** klicken.
5. Öffne den Reiter **Actions**. Der Build startet automatisch (ca. 3–5 Minuten, später grüner Haken). Falls nicht: links **build** wählen → **Run workflow**.
6. Klicke auf den fertigen Durchlauf, scrolle nach unten zu **Artifacts** und lade **ytaudio-jar** herunter. In der ZIP liegt `ytaudio-1.0.0.jar`.
7. Bei einem roten Kreuz: Klicke den Durchlauf an, öffne den fehlgeschlagenen Schritt „Build“ und schick mir die Fehlermeldung. Ich behebe das.

## Teil 2: Installieren

1. Installiere den **Fabric Loader** für Minecraft **1.21.10** (https://fabricmc.net/use/installer/).
2. Lege in den Ordner `%appdata%\.minecraft\mods` diese Dateien:
   - `ytaudio-1.0.0.jar`
   - **Fabric API** für 1.21.10 (Version 0.138.x)
   - **Simple Voice Chat** für Fabric 1.21.10
3. Starte Minecraft mit dem Fabric-Profil.
4. Der Server, auf dem du spielst, braucht ebenfalls **Simple Voice Chat**, sonst läuft der Voice-Chat nicht und es kommt kein Audio an.

## Teil 3: Benutzen

- Standardtaste **O** öffnet das Menü (änderbar unter Optionen → Steuerung → „Minecraft YouTube Audio“).
- YouTube-Link einfügen → **Hinzufügen** → Titel in der Liste anklicken oder **Play**.
- Beim ersten Hinzufügen lädt die Mod einmalig yt-dlp, FFmpeg und Deno herunter (ca. 100 MB, dauert kurz).
  Die Programme liegen in `.minecraft\config\ytaudio\bin`.
- Einstellungen und Playlist werden in `.minecraft\config\ytaudio\config.json` gespeichert.
- **Selbst mithören**: AUS schalten, wenn du die Musik nicht selbst hören willst (andere hören sie trotzdem).

## Hinweise

- Die Musik wird nur gesendet, wenn der Voice-Chat verbunden und aktiv ist (Mikrofon-Symbol sichtbar, nicht deaktiviert).
- Mikrofon-Sprache und Musik werden gemischt. Bei Push-to-Talk bzw. Stummschaltung sollte die Musik trotzdem laufen (laut API-Dokumentation); bitte testen.
- Nutzung von YouTube-Audio außerhalb von YouTube kann gegen die YouTube-Nutzungsbedingungen verstoßen. Nutzung auf eigene Verantwortung.
- Wenn ein Link nicht mehr geht: Button **yt-dlp aktualisieren** drücken.
- Playlist-Links (`/playlist?list=...`) werden aktuell nicht unterstützt, nur einzelne Videos.
