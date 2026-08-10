/*
 * Copyright (Date see Readme), gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.refpopp.popp_client.connector.eventservice.cetp;

import de.gematik.ws.conn.eventservice.v7.Event;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/**
 * Listens for CETP event messages pushed by the connector. The connector acts as the TLS client: it
 * connects to the address announced via <i>Subscribe</i> (EventTo) and delivers each event as a
 * SOAP envelope framed with a "CETP" magic header and a 4-byte payload length.
 */
@Component
@ConditionalOnProperty(prefix = "connector.cetp", name = "enabled", havingValue = "true")
@Slf4j
public class CetpEventListener {

  private static final byte[] CETP_MAGIC = {'C', 'E', 'T', 'P'};
  private static final int FRAME_HEADER_LENGTH = 8;
  private static final int MAX_MESSAGE_SIZE = 1024 * 1024;
  private static final String TOPIC_CARD_INSERTED = "CARD/INSERTED";
  private static final String TOPIC_CARD_REMOVED = "CARD/REMOVED";
  private static final String CARD_TYPE_EGK = "EGK";

  private final Jaxb2Marshaller eventServiceMarshaller;
  private final ApplicationEventPublisher eventPublisher;
  private final int port;
  private final boolean tlsEnabled;
  private final String keystorePath;
  private final String keystorePassword;

  private final ExecutorService connectionExecutor =
      Executors.newCachedThreadPool(
          runnable -> {
            final var thread = new Thread(runnable, "cetp-connection");
            thread.setDaemon(true);
            return thread;
          });
  private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
  private ServerSocket serverSocket;
  private volatile boolean running;

  public CetpEventListener(
      final Jaxb2Marshaller eventServiceMarshaller,
      final ApplicationEventPublisher eventPublisher,
      @Value("${connector.cetp.event-to-port}") final int port,
      @Value("${connector.cetp.tls-enabled}") final boolean tlsEnabled,
      @Value("${connector.secure.keystore}") final String keystorePath,
      @Value("${connector.secure.keystore-password}") final String keystorePassword) {
    this.eventServiceMarshaller = eventServiceMarshaller;
    this.eventPublisher = eventPublisher;
    this.port = port;
    this.tlsEnabled = tlsEnabled;
    this.keystorePath = keystorePath;
    this.keystorePassword = keystorePassword;
  }

  public synchronized void start() {
    if (running) {
      return;
    }
    try {
      serverSocket = createServerSocket();
    } catch (GeneralSecurityException | IOException e) {
      throw new IllegalStateException("Could not start CETP listener on port " + port, e);
    }
    running = true;
    final var acceptThread = new Thread(this::acceptLoop, "cetp-listener");
    acceptThread.setDaemon(true);
    acceptThread.start();
    log.info("| CETP listener started on port {} (TLS enabled: {})", port, tlsEnabled);
  }

  public synchronized void stop() {
    if (!running) {
      return;
    }
    running = false;
    closeQuietly(serverSocket);
    openSockets.forEach(this::closeQuietly);
    connectionExecutor.shutdownNow();
    log.info("| CETP listener stopped");
  }

  private void acceptLoop() {
    while (running) {
      try {
        final var socket = serverSocket.accept();
        openSockets.add(socket);
        connectionExecutor.submit(() -> handleConnection(socket));
      } catch (IOException e) {
        if (running) {
          log.error("| Error accepting CETP connection: {}", e.getMessage());
        }
      }
    }
  }

  private void handleConnection(final Socket socket) {
    log.debug("| CETP connection from {}", socket.getRemoteSocketAddress());
    try (socket) {
      final var inputStream = socket.getInputStream();
      byte[] message;
      while ((message = readFramedMessage(inputStream)) != null) {
        processEventMessage(message);
      }
    } catch (IOException e) {
      if (running) {
        log.warn("| CETP connection error: {}", e.getMessage());
      }
    } finally {
      openSockets.remove(socket);
    }
  }

  private byte[] readFramedMessage(final InputStream inputStream) throws IOException {
    final var header = inputStream.readNBytes(FRAME_HEADER_LENGTH);
    if (header.length == 0) {
      return null; // orderly end of stream
    }
    if (header.length < FRAME_HEADER_LENGTH || !hasCetpMagic(header)) {
      throw new IOException("Invalid CETP frame header");
    }
    final int length = ByteBuffer.wrap(header, CETP_MAGIC.length, 4).getInt();
    if (length <= 0 || length > MAX_MESSAGE_SIZE) {
      throw new IOException("Invalid CETP message length: " + length);
    }
    final var payload = inputStream.readNBytes(length);
    if (payload.length < length) {
      throw new IOException("Truncated CETP message");
    }
    return payload;
  }

  private boolean hasCetpMagic(final byte[] header) {
    for (int i = 0; i < CETP_MAGIC.length; i++) {
      if (header[i] != CETP_MAGIC[i]) {
        return false;
      }
    }
    return true;
  }

  private void processEventMessage(final byte[] soapMessage) {
    final Event event;
    try {
      event = unmarshalEvent(soapMessage);
    } catch (Exception e) {
      log.error("| Could not parse CETP event message: {}", e.getMessage());
      return;
    }

    final var parameters = extractParameters(event);
    log.info("| Received CETP event with topic {} and parameters {}", event.getTopic(), parameters);

    if (TOPIC_CARD_INSERTED.equalsIgnoreCase(event.getTopic())) {
      if (!CARD_TYPE_EGK.equalsIgnoreCase(parameters.get("CardType"))) {
        log.debug("| Ignoring CARD/INSERTED event for card type {}", parameters.get("CardType"));
        return;
      }
      eventPublisher.publishEvent(
          new ConnectorCardInsertedEvent(
              parameters.get("CardHandle"),
              parameters.get("CardType"),
              parameters.get("CardVersion"),
              parameters.get("ICCSN"),
              parameters.get("CtID"),
              parameters.get("SlotID"),
              parameters.get("InsertTime"),
              parameters.get("KVNR")));
    } else if (TOPIC_CARD_REMOVED.equalsIgnoreCase(event.getTopic())) {
      eventPublisher.publishEvent(
          new ConnectorCardRemovedEvent(
              parameters.get("CardHandle"),
              parameters.get("CardType"),
              parameters.get("CtID"),
              parameters.get("SlotID")));
    }
  }

  private Event unmarshalEvent(final byte[] soapMessage) throws Exception {
    final var documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    documentBuilderFactory.setExpandEntityReferences(false);
    final var document =
        documentBuilderFactory.newDocumentBuilder().parse(new ByteArrayInputStream(soapMessage));

    final var eventNodes = document.getElementsByTagNameNS("*", "Event");
    if (eventNodes.getLength() == 0) {
      throw new IllegalArgumentException("No Event element found in CETP message");
    }
    return (Event) eventServiceMarshaller.unmarshal(new DOMSource(eventNodes.item(0)));
  }

  private Map<String, String> extractParameters(final Event event) {
    final var parameters = new HashMap<String, String>();
    if (event.getMessage() != null) {
      event
          .getMessage()
          .getParameter()
          .forEach(parameter -> parameters.put(parameter.getKey(), parameter.getValue()));
    }
    return parameters;
  }

  private ServerSocket createServerSocket() throws GeneralSecurityException, IOException {
    if (!tlsEnabled) {
      return new ServerSocket(port);
    }
    final var keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
    try (final var keyStoreStream = openKeyStoreStream(keystorePath)) {
      keyStore.load(keyStoreStream, keystorePassword.toCharArray());
    }
    final var keyManagerFactory = KeyManagerFactory.getInstance("PKIX");
    keyManagerFactory.init(keyStore, keystorePassword.toCharArray());

    final var sslContext =
        SSLContext.getInstance("TLSv1.2", BouncyCastleJsseProvider.PROVIDER_NAME);
    sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
    return sslContext.getServerSocketFactory().createServerSocket(port);
  }

  private InputStream openKeyStoreStream(final String location) throws IOException {
    final var resolved = location == null ? "" : location.trim();
    if (resolved.startsWith("classpath:")) {
      final var resourcePath = resolved.substring("classpath:".length());
      final var stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (stream == null) {
        throw new IOException("Classpath resource not found: " + resourcePath);
      }
      return stream;
    }

    final var path = Path.of(resolved);
    if (Files.exists(path)) {
      return Files.newInputStream(path);
    }

    final var stream = getClass().getClassLoader().getResourceAsStream(resolved);
    if (stream == null) {
      throw new IOException("Keystore not found at path or classpath: " + resolved);
    }
    return stream;
  }

  private void closeQuietly(final AutoCloseable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (Exception e) {
      log.debug("| Error closing CETP resource: {}", e.getMessage());
    }
  }
}
