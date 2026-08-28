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

package de.servicehealth.refpopp.vsdm2_client.client;

import de.servicehealth.refpopp.vsdm2_client.properties.ServiceDiscoveryProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Resolves the VSDM backend for an insurer (IK number) from the TI service discovery catalog. The
 * catalog is cached and refreshed after {@code service-discovery.refresh-minutes}; an unknown
 * insurer triggers one immediate re-fetch so newly added insurers work without restart.
 */
@Component
@Slf4j
public class ServiceDiscoveryClient {

  private final RestClient restClient = RestClient.create();
  private final ServiceDiscoveryProperties properties;
  private volatile Catalog catalog;

  private record Catalog(
      Map<String, String> instanceUrls, Map<String, String> vsdmRouting, Instant fetchedAt) {}

  public ServiceDiscoveryClient(final ServiceDiscoveryProperties properties) {
    this.properties = properties;
  }

  public Optional<String> resolveVsdmUrl(final String insurerId) {
    refreshIfStale();
    var url = lookup(insurerId);
    if (url.isEmpty()) {
      refresh();
      url = lookup(insurerId);
    }
    return url;
  }

  private Optional<String> lookup(final String insurerId) {
    final var current = catalog;
    if (current == null) {
      return Optional.empty();
    }
    final var instance = current.vsdmRouting().get(insurerId);
    if (instance == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(current.instanceUrls().get(instance));
  }

  private void refreshIfStale() {
    final var current = catalog;
    if (current == null
        || current
            .fetchedAt()
            .plus(Duration.ofMinutes(properties.refreshMinutes()))
            .isBefore(Instant.now())) {
      refresh();
    }
  }

  @SuppressWarnings("unchecked")
  private synchronized void refresh() {
    try {
      final Map<String, Object> root =
          restClient.get().uri(properties.url()).retrieve().body(Map.class);

      final var instanceUrls = new HashMap<String, String>();
      final var instances = (Map<String, Object>) root.getOrDefault("service_instances", Map.of());
      instances.forEach(
          (name, value) ->
              instanceUrls.put(name, String.valueOf(((Map<String, Object>) value).get("url"))));

      final var vsdmRouting = new HashMap<String, String>();
      final var routing = (Map<String, Object>) root.getOrDefault("routing", Map.of());
      final var vsdm = (Map<String, Object>) routing.getOrDefault("vsdm", Map.of());
      vsdm.forEach((insurerId, instance) -> vsdmRouting.put(insurerId, String.valueOf(instance)));

      catalog = new Catalog(Map.copyOf(instanceUrls), Map.copyOf(vsdmRouting), Instant.now());
      log.info(
          "| Service discovery catalog loaded from {}: {} vsdm routes to {} instances",
          properties.url(),
          vsdmRouting.size(),
          instanceUrls.size());
    } catch (Exception e) {
      log.warn(
          "| Could not fetch service discovery catalog from {}: {}",
          properties.url(),
          e.getMessage());
    }
  }
}
