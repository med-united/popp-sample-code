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

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.client.CommunicationService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Reacts to CETP card events: publishes the raw event XML to the card terminal's STOMP topic, runs
 * the PoPP token retrieval for an inserted eGK, publishes the token and finally the VSDM 2.0 data
 * fetched with it.
 */
@Component
@ConditionalOnProperty(prefix = "connector.cetp", name = "enabled", havingValue = "true")
@Slf4j
public class CardInsertedTokenHandler {

  static final String TOKEN_TOPIC_PREFIX = "/topic/popp-token/";

  private final CommunicationService communicationService;
  private final SimpMessagingTemplate messagingTemplate;
  private final TelematikIdProvider telematikIdProvider;
  private final Vsdm2ReadClient vsdm2ReadClient;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            final var thread = new Thread(runnable, "cetp-token-retrieval");
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicBoolean inFlight = new AtomicBoolean(false);
  private volatile Retrieval currentRetrieval;

  private static final class Retrieval {
    final String cardHandle;
    volatile boolean started;
    volatile boolean cancelled;
    volatile Future<?> future;

    Retrieval(final String cardHandle) {
      this.cardHandle = cardHandle;
    }
  }

  public CardInsertedTokenHandler(
      @Lazy final CommunicationService communicationService,
      final SimpMessagingTemplate messagingTemplate,
      final TelematikIdProvider telematikIdProvider,
      final Vsdm2ReadClient vsdm2ReadClient) {
    this.communicationService = communicationService;
    this.messagingTemplate = messagingTemplate;
    this.telematikIdProvider = telematikIdProvider;
    this.vsdm2ReadClient = vsdm2ReadClient;
  }

  @EventListener
  public void onCardInserted(final ConnectorCardInsertedEvent event) {
    if (!inFlight.compareAndSet(false, true)) {
      log.info(
          "| Token retrieval already in progress, ignoring CARD/INSERTED event for card {}",
          event.cardHandle());
      return;
    }
    final var retrieval = new Retrieval(event.cardHandle());
    currentRetrieval = retrieval;
    publish(event.ctId(), new CetpEventMessage(event.ctId(), telematikId(), event.cetpXml()));
    retrieval.future = executor.submit(() -> retrieveToken(event, retrieval));
  }

  @EventListener
  public void onCardRemoved(final ConnectorCardRemovedEvent event) {
    publish(event.ctId(), new CetpEventMessage(event.ctId(), telematikId(), event.cetpXml()));
    final var retrieval = currentRetrieval;
    if (retrieval == null
        || retrieval.cancelled
        || !retrieval.cardHandle.equals(event.cardHandle())) {
      return;
    }
    retrieval.cancelled = true;
    log.info("| Card {} removed during token retrieval, cancelling retrieval", event.cardHandle());
    final var future = retrieval.future;
    if (future != null && future.cancel(true) && !retrieval.started) {
      // The task was cancelled before it started running, so its cleanup will never execute.
      currentRetrieval = null;
      inFlight.set(false);
    }
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }

  private void retrieveToken(final ConnectorCardInsertedEvent event, final Retrieval retrieval) {
    retrieval.started = true;
    try {
      log.info(
          "| eGK inserted (handle {}, terminal {}, slot {}), starting PoPP token retrieval",
          event.cardHandle(),
          event.ctId(),
          event.slotId());
      final var token =
          communicationService.startWithConnector(
              CardConnectionType.CONTACT_CONNECTOR, event.kvnr());
      if (retrieval.cancelled) {
        log.info("| Card {} was removed, discarding retrieved PoPP token", event.cardHandle());
        return;
      }
      publish(event.ctId(), new PoppTokenMessage(event.ctId(), telematikId(), token));
      log.info("| PoPP token for inserted eGK {} published", event.cardHandle());
      fetchAndPublishVsd(event, retrieval, token);
    } catch (Exception e) {
      if (retrieval.cancelled) {
        log.info("| Token retrieval for eGK {} aborted after card removal", event.cardHandle());
        return;
      }
      log.error(
          "| PoPP token retrieval for inserted eGK {} failed: {}",
          event.cardHandle(),
          e.getMessage());
    } finally {
      currentRetrieval = null;
      inFlight.set(false);
    }
  }

  private void fetchAndPublishVsd(
      final ConnectorCardInsertedEvent event, final Retrieval retrieval, final String token) {
    if (!vsdm2ReadClient.isConfigured()) {
      log.warn("| No vsdm2-client.url configured, skipping VSDM 2.0 fetch");
      return;
    }
    try {
      final var readVsdResponseXml = vsdm2ReadClient.readVsdResponseXml(token);
      if (retrieval.cancelled) {
        log.info("| Card {} was removed, discarding VSDM 2.0 data", event.cardHandle());
        return;
      }
      publish(
          event.ctId(),
          new ReadVSDResponseMessage(event.ctId(), telematikId(), readVsdResponseXml));
      log.info("| VSDM 2.0 data for inserted eGK {} published", event.cardHandle());
    } catch (Exception e) {
      log.error(
          "| VSDM 2.0 fetch for inserted eGK {} failed: {}", event.cardHandle(), e.getMessage());
    }
  }

  private String telematikId() {
    return telematikIdProvider.getTelematikId();
  }

  private void publish(final String ctId, final Object message) {
    messagingTemplate.convertAndSend(TOKEN_TOPIC_PREFIX + ctId, message);
  }
}
