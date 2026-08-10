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
 * Reacts to CARD/INSERTED events from the connector by running the existing PoPP token retrieval
 * flow for the inserted eGK and publishing the token to the card terminal's STOMP topic.
 */
@Component
@ConditionalOnProperty(prefix = "connector.cetp", name = "enabled", havingValue = "true")
@Slf4j
public class CardInsertedTokenHandler {

  static final String TOKEN_TOPIC_PREFIX = "/topic/popp-token/";

  private final CommunicationService communicationService;
  private final SimpMessagingTemplate messagingTemplate;
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
      final SimpMessagingTemplate messagingTemplate) {
    this.communicationService = communicationService;
    this.messagingTemplate = messagingTemplate;
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
    publish(event.ctId(), new CardInsertedMessage(event));
    retrieval.future = executor.submit(() -> retrieveToken(event, retrieval));
  }

  @EventListener
  public void onCardRemoved(final ConnectorCardRemovedEvent event) {
    publish(event.ctId(), new CardRemovedMessage(event));
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
      publish(event.ctId(), new PoppTokenMessage(event, token));
      log.info("| PoPP token for inserted eGK {} published", event.cardHandle());
    } catch (Exception e) {
      if (retrieval.cancelled) {
        log.info("| Token retrieval for eGK {} aborted after card removal", event.cardHandle());
        return;
      }
      log.error(
          "| PoPP token retrieval for inserted eGK {} failed: {}",
          event.cardHandle(),
          e.getMessage());
      publish(event.ctId(), new TokenRetrievalFailedMessage(event, e.getMessage()));
    } finally {
      currentRetrieval = null;
      inFlight.set(false);
    }
  }

  private void publish(final String ctId, final Object message) {
    messagingTemplate.convertAndSend(TOKEN_TOPIC_PREFIX + ctId, message);
  }
}
