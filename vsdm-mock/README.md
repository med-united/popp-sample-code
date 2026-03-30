# VSDM Mock

Das `vsdm-mock` Modul stellt einen Mock-Server für die VSDM 2.0 Schnittstelle bereit. Es simuliert die notwendigen Endpunkte für die OAuth2-Authentifizierung (Token-Exchange) und den Abruf von FHIR-Bundles, um die Entwicklung und Tests des `vsdm-client` zu ermöglichen, ohne eine echte VSDM-Umgebung zu benötigen.

## Funktionen

- Bereitstellung von OAuth2 Discovery-Metadaten (`/.well-known/oauth-protected-resource`, `/.well-known/oauth-authorization-server`).
- Simulation des OAuth2 Token-Endpunkts (`/oauth2/token`) für den Token-Exchange.
- Bereitstellung eines Mock FHIR-Bundles über den Endpunkt `/vsdm/bundle/{kvnr}`.
- Unterstützung für DPoP und PoPP Header in Anfragen.

## Erste Schritte

Um den `vsdm-mock` zu starten, folge diesen Schritten:

1.  **Build des `vsdm-mock` Moduls:**
    Navigieren Sie zum Hauptverzeichnis des Projekts (`popp-sample-code`) und führen Sie den folgenden Maven-Befehl aus, um nur das `vsdm-mock` Modul zu bauen und die Tests zu überspringen:

    ```bash
    ./mvnw clean package -pl vsdm-mock "-DskipTests"
    ```
    
    **Hinweis:** Wenn Sie das gesamte Projekt mit `./mvnw clean install "-Dskip.dockerbuild=false" "-DskipTests=true"` bauen, ist dieser spezifische Modul-Build nicht erforderlich.

2.  **Starten mit Docker Compose:**
    Nachdem das Modul gebaut wurde, können Sie die gesamte Anwendung (einschließlich `vsdm-mock`) mit Docker Compose starten:

    ```bash
    docker compose -f docker/compose.yaml --profile full up
    ```

## SSL/TLS Konfiguration

Der `vsdm-mock` ist für die Verwendung von HTTPS auf Port `8082` konfiguriert. Die SSL/TLS-Einstellungen werden in der `application.yaml` Datei definiert:

- **Keystore-Datei:** `keystore.p12` (befindet sich im `classpath`, d.h. im `src/main/resources` Verzeichnis des `vsdm-mock` Moduls).
- **Keystore-Passwort:** `password`
- **Keystore-Typ:** `PKCS12`

Diese Konfiguration ist für die Kommunikation mit dem `vsdm-client` über HTTPS erforderlich.