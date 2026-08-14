# VSDM Client

Das `vsdm-client` Modul ist eine Beispielimplementierung eines Clients für die VSDM 2.0 Schnittstelle. Es demonstriert die Kommunikation mit einem VSDM-Backend (hier dem `vsdm-mock`) unter Verwendung der gematik Zeta SDK für OAuth2 (DPoP) und integriert sich mit dem PoPP-Client zur Beschaffung von PoPP-Tokens. Der Client stellt einen SOAP-Endpunkt für `ReadVSD`-Anfragen bereit.

## Funktionen

-   Kommunikation mit einem VSDM 2.0 Backend (Mock).
-   Verwendung der gematik Zeta SDK für OAuth2 Token-Exchange (DPoP).
-   Integration mit dem PoPP-Client zur Beschaffung von PoPP-Tokens.
-   Abruf eines FHIR-Bundles vom VSDM-Backend.
-   Bereitstellung eines SOAP-Endpunkts (`http://localhost:8080/ws/`) für `ReadVSD`-Anfragen.

## Erste Schritte

Um den `vsdm-client` zu starten, folge diesen Schritten:

1.  **Build des `vsdm-client` Moduls:**
    Navigieren Sie zum Hauptverzeichnis des Projekts (`popp-sample-code`) und führen Sie den folgenden Maven-Befehl aus, um nur das `vsdm-client` Modul zu bauen und die Tests zu überspringen:

    ```bash
    ./mvnw clean package -pl vsdm-client "-DskipTests"
    ```

    **Hinweis:** Wenn Sie das gesamte Projekt mit `./mvnw clean install "-Dskip.dockerbuild=false" "-DskipTests=true"` bauen, ist dieser spezifische Modul-Build nicht erforderlich.

2.  **Starten mit Docker Compose:**
    Nachdem das Modul gebaut wurde, können Sie die gesamte Anwendung (einschließlich `vsdm-client`) mit Docker Compose starten:

    ```bash
    docker compose -f docker/compose.yaml --profile full up
    ```

## Konfiguration

Der `vsdm-client` wird über Umgebungsvariablen konfiguriert, insbesondere wenn er über Docker Compose gestartet wird:

-   **`VSDM_MOCK_URL`**: Die Basis-URL des VSDM-Mock-Servers. Muss `https` verwenden, z.B. `https://vsdm-mock:8082`.
-   **`POPP_CLIENT_API_URL`**: Der Endpunkt des PoPP-Clients zur Beschaffung von PoPP-Tokens, z.B. `http://popp-client:8081/token`.

### Zeta SDK und SM-B Zertifikat

Der Client verwendet die gematik Zeta SDK für die Authentifizierung. Hierfür wird ein Mock SM-B (Sicherheitsmodul-Betreiber) P12-Zertifikat (`mock_smb.p12`) aus den Ressourcen geladen. Dieses Zertifikat wird temporär in eine Datei extrahiert und für die `SmbTokenProvider`-Konfiguration der Zeta SDK verwendet.

-   **Alias:** `smb-test`
-   **Passwort:** (leer)

### FHIR Bundle Format

Beim Abruf von FHIR-Bundles vom VSDM-Backend erwartet der Client XML-Format. Der `Accept`-Header wird explizit auf `application/xml` gesetzt, um die Kompatibilität mit dem `vsdm-mock` zu gewährleisten.

## Nutzung

Der `vsdm-client` stellt einen SOAP-Endpunkt unter `http://localhost:8080/ws/` bereit, der `ReadVSD`-Anfragen verarbeitet. Diese Anfragen werden intern an das VSDM-Backend weitergeleitet, nachdem ein PoPP-Token beschafft und der OAuth2 Token-Exchange durchgeführt wurde. Der Client fordert dabei spezifisch das FHIR-Bundle mit der ID `123` vom VSDM-Backend an.
