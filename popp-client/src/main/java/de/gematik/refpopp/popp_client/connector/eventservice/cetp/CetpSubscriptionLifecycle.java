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

import de.gematik.refpopp.popp_client.connector.eventservice.SubscribeClient;
import de.gematik.refpopp.popp_client.connector.eventservice.UnsubscribeClient;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts the CETP listener and keeps a CARD/INSERTED subscription alive at the connector.
 * Subscriptions expire (max 24h), so the client re-subscribes periodically; re-subscribing also
 * recovers a subscription lost to a connector restart.
 */
@Component
@ConditionalOnProperty(prefix = "connector.cetp", name = "enabled", havingValue = "true")
@Slf4j
public class CetpSubscriptionLifecycle {

  // Topic matching is prefix-based: subscribing to CARD delivers CARD/INSERTED, CARD/REMOVED, ...
  private static final String TOPIC_CARD = "CARD";

  private final CetpEventListener cetpEventListener;
  private final SubscribeClient subscribeClient;
  private final UnsubscribeClient unsubscribeClient;
  private final String eventToUrl;
  private final int eventToPort;
  private final long resubscribeIntervalHours;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            final var thread = new Thread(runnable, "cetp-resubscribe");
            thread.setDaemon(true);
            return thread;
          });
  private volatile String subscriptionId;

  public CetpSubscriptionLifecycle(
      final CetpEventListener cetpEventListener,
      @Lazy final SubscribeClient subscribeClient,
      @Lazy final UnsubscribeClient unsubscribeClient,
      @Value("${connector.cetp.event-to-url}") final String eventToUrl,
      @Value("${connector.cetp.event-to-port}") final int eventToPort,
      @Value("${connector.cetp.resubscribe-interval-hours}") final long resubscribeIntervalHours) {
    this.cetpEventListener = cetpEventListener;
    this.subscribeClient = subscribeClient;
    this.unsubscribeClient = unsubscribeClient;
    this.eventToUrl = eventToUrl;
    this.eventToPort = eventToPort;
    this.resubscribeIntervalHours = resubscribeIntervalHours;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (eventToUrl == null || eventToUrl.isBlank()) {
      throw new IllegalStateException(
          "connector.cetp.event-to-url must be set when connector.cetp.enabled=true");
    }
    cetpEventListener.start();
    scheduler.scheduleAtFixedRate(this::subscribe, 0, resubscribeIntervalHours, TimeUnit.HOURS);
  }

  private void subscribe() {
    try {
      final var eventTo = "cetp://" + eventToUrl + ":" + eventToPort;
      final var response = subscribeClient.performSubscribe(eventTo, TOPIC_CARD);
      subscriptionId = response.getSubscriptionID();
      log.info(
          "| Subscribed to {} events at {} (subscription {}, valid until {})",
          TOPIC_CARD,
          eventTo,
          subscriptionId,
          response.getTerminationTime());
    } catch (Exception e) {
      log.error("| Could not subscribe to connector events: {}", e.getMessage());
    }
  }

  @PreDestroy
  public void shutdown() {
    scheduler.shutdownNow();
    unsubscribe();
    cetpEventListener.stop();
  }

  private void unsubscribe() {
    if (subscriptionId == null) {
      return;
    }
    try {
      unsubscribeClient.performUnsubscribe(subscriptionId);
      log.info("| Unsubscribed from connector events (subscription {})", subscriptionId);
    } catch (Exception e) {
      log.warn("| Could not unsubscribe from connector events: {}", e.getMessage());
    }
  }
}
