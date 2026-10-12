# Produktformular – Produkterfassung mit Datei-Upload

Interaktives, deutschsprachiges Online-Formular zur Erfassung von Informationen und Anforderungen zu beliebig vielen Produkten. Es hat ein echtes Backend mit SQLite-Datenbank, verschlüsselten Zugangsdaten, gespeicherten Datei-Uploads, PDF-Export und einem geschützten Admin-Bereich.

**Keine externen Abhängigkeiten:** Es wird nur Node.js ≥ 22.5 benötigt (eingebautes `node:sqlite`). Ein `npm install` ist nicht nötig.

## Starten

```bash
cd produktformular
PF_ADMIN_PASSWORD='ein-sicheres-passwort' npm start
# → http://localhost:3000        Formular
# → http://localhost:3000/admin  Verwaltung (Einreichungen, Dateien, PDF, Zugangsdaten)
```

Ohne `PF_ADMIN_PASSWORD` wird beim ersten Start ein zufälliges Admin-Passwort erzeugt und **einmalig** in der Konsole ausgegeben.

Tests (API-Ende-zu-Ende: Upload, Validierung, Absenden, PDF, Admin, Zugriffsschutz):

```bash
npm test
```

## Online stellen: eigener Link für alle (Render)

Im Repository liegt eine fertige Konfiguration (`render.yaml` im Hauptordner und `produktformular/Dockerfile`). Damit bekommen Sie eine öffentliche Adresse wie `https://produktformular-xxxx.onrender.com`. Jeder kann das Formular darüber **ohne Login** öffnen und ausfüllen. Nur der Admin-Bereich `/admin` bleibt passwortgeschützt.

1. Konto auf [render.com](https://render.com) anlegen, am einfachsten mit „Sign in with GitHub“.
2. Im Dashboard **New → Blueprint** wählen und das Repository `phil3939120/SprayCanMod` verbinden. Dabei den Branch mit dem Formular auswählen (oder ihn vorher in `main` mergen).
3. Render liest `render.yaml` und legt den Dienst „produktformular“ mit 1 GB dauerhaftem Speicher an. Mit **Apply** bestätigen. Der erste Build dauert ein paar Minuten.
4. Danach steht oben im Dienst der Link (`…onrender.com`). Diesen Link geben Sie weiter.
5. Das Admin-Passwort hat Render automatisch erzeugt. Sie finden es im Dienst unter **Environment → PF_ADMIN_PASSWORD**. Damit melden Sie sich unter `https://…onrender.com/admin` an.

Wichtig:
- Dauerhafter Speicher (Disk) ist bei Render nur in kostenpflichtigen Tarifen enthalten, deshalb steht in `render.yaml` der Tarif „starter“. Im kostenlosen Tarif gehen Einreichungen und Dateien bei jedem Neustart verloren. Für dieses Formular ist er also nicht geeignet.
- `PF_ENCRYPTION_KEY` dürfen Sie nach dem ersten Start nicht mehr ändern, sonst lassen sich gespeicherte Zugangsdaten nicht mehr entschlüsseln.
- Eine eigene Domain (z. B. `formular.ihre-firma.de`) können Sie in Render unter **Settings → Custom Domains** eintragen.

**Andere Anbieter:** Das `Dockerfile` läuft bei jedem Docker-Hoster (z. B. Railway oder ein eigener Server). Nötig sind ein dauerhaftes Volume unter `/var/data` sowie die Variablen `PF_SECURE_COOKIES=1`, `PF_TRUST_PROXY=1`, `PF_ADMIN_PASSWORD` und `PF_ENCRYPTION_KEY`.

## Konfiguration (Umgebungsvariablen)

| Variable | Bedeutung | Standard |
|---|---|---|
| `PORT` / `HOST` | Port und Adresse des Servers | `3000` / `0.0.0.0` |
| `PF_ADMIN_PASSWORD` | Admin-Passwort (mind. 10 Zeichen) | wird erzeugt |
| `PF_ENCRYPTION_KEY` | Schlüssel für die Zugangsdaten (64 Hex-Zeichen oder ein Zufallswert mit mind. 32 Zeichen) | `data/secret.key` wird erzeugt |
| `PF_DATA_DIR` | Ablage für Datenbank und Uploads | `./data` |
| `PF_TRUST_PROXY` | `1` setzen hinter einem Reverse-Proxy (echte Client-IP für die Login-Sperre) | aus |
| `PF_SECURE_COOKIES` | `1` setzen, wenn hinter HTTPS betrieben (Pflicht im Produktivbetrieb) | aus |
| `PF_DRAFT_TTL_DAYS` | Nie abgesendete Entwürfe werden nach X Tagen gelöscht | `30` |

Für den Produktivbetrieb sollte der Server hinter einem HTTPS-Reverse-Proxy (z. B. nginx oder Caddy) laufen. Den Ordner `data/` sollten Sie regelmäßig sichern, und zwar zusammen mit dem Schlüssel: Ohne `secret.key` bzw. `PF_ENCRYPTION_KEY` lassen sich hinterlegte Zugangsdaten nicht mehr entschlüsseln.

## Funktionen

- **Sechs aufklappbare Abschnitte** mit Fortschrittsanzeige (klickbare Schritte, Status je Abschnitt).
- **Dynamische Produktverwaltung:** „+ Produkt hinzufügen“ gibt es in jedem Abschnitt. Die Produkte werden automatisch nummeriert und gelten für alle sechs Abschnitte. Sie haben optional einen Namen und eine Artikelnummer. Umbenennen geht in jedem Abschnitt und wird überall übernommen. Beim Entfernen werden auch die Dateien des Produkts gelöscht.
- **Abschnitt 2:** Bei „Ja“ ist die Anzahl der Tage ein Pflichtfeld (ganze Zahl von 1 bis 3650).
- **Abschnitt 4 (Lieferanten):** Mehrere Links sind möglich. Für den Zugang gibt es drei Optionen:
  1. keine Zugangsdaten nötig,
  2. *sichere separate Freigabe (empfohlen)* über Gastzugang oder Passwort-Manager, dabei werden keine Passwörter übertragen,
  3. *verschlüsselte Hinterlegung*: Die Daten werden mit AES-256-GCM verschlüsselt und in einer eigenen Tabelle gespeichert. Sie erscheinen nie im PDF, in Exporten oder in der Admin-Übersicht und lassen sich nur einzeln per Klick entschlüsseln; jeder Abruf wird protokolliert.

  Zugangsdaten werden außerdem nicht im Browser zwischengespeichert. Wer in Kommentare etwas wie „Passwort: …“ schreibt, bekommt einen Warnhinweis.
- **Datei-Upload:** Dateien lassen sich per Drag & Drop oder über „Datei auswählen“ hochladen, auch mehrere gleichzeitig, mit Fortschrittsbalken. Bilder bekommen eine Vorschau, angezeigt werden Name und Größe, und jede Datei kann einzeln gelöscht werden. Erlaubt sind JPG, PNG, WEBP, GIF, PDF, DOCX, DOC, XLSX, XLS, PPTX, ODT, ODS, TXT, CSV, MP4, MOV und ZIP bis 25 MB pro Datei. Der Server prüft Endung **und** Dateiinhalt (Magic Bytes).
- **Zwischenspeicherung:** Texteingaben bleiben im Browser gespeichert. Dateien liegen sofort auf dem Server und sind nach einem Neuladen wieder dem richtigen Produkt zugeordnet.
- **Validierung:** Pflichtfelder werden im Browser und auf dem Server gleich geprüft (`public/js/schema.js`). Fehler sind rot markiert, der betroffene Abschnitt klappt automatisch auf.
- **Absenden:** Zuerst erscheint eine Prüfansicht aller Angaben, nach dem Senden eine Bestätigung mit Referenznummer (z. B. `PF-20261012-QJXGR`) und ein PDF-Download.
- **PDF-Export:** Das PDF ist nach Produkten gegliedert und enthält alle Antworten, Kommentare, Links, Dateiverweise und das Schlusswort, aber keine Zugangsdaten.
- **Zugriffsschutz:** Dateien sind nur für die einreichende Sitzung (HttpOnly-Cookie mit Zufallstoken) und angemeldete Admins abrufbar. Weitere Schutzmaßnahmen: CSP, `nosniff`, SameSite=Strict-Cookies, Origin-Prüfung, Login-Drosselung, scrypt-Passwort-Hash und ein Audit-Log.

## Aufbau

```
server.js            HTTP-Server, Routing, Upload, Absenden, Admin-API
lib/db.js            SQLite-Schema
lib/security.js      Tokens, Passwort-Hash, AES-256-GCM, Dateisignaturen
lib/pdf.js           PDF-Generator (ohne Abhängigkeiten)
lib/report.js        Aufbau des PDF-Exports
public/              Formular (index.html), Admin (admin.html), CSS, JS
public/js/schema.js  Gemeinsame Abschnitte, Normalisierung, Validierung
test/api.test.js     End-to-End-Tests der API
```
