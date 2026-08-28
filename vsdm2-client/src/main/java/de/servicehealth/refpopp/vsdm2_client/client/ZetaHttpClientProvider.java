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

import de.gematik.zeta.logging.Log;
import de.gematik.zeta.sdk.BuildConfig;
import de.gematik.zeta.sdk.TpmConfig;
import de.gematik.zeta.sdk.ZetaSdk;
import de.gematik.zeta.sdk.ZetaSdkClient;
import de.gematik.zeta.sdk.attestation.model.AttestationConfig;
import de.gematik.zeta.sdk.authentication.AuthConfig;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClient;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import de.gematik.zeta.sdk.storage.InMemoryStorage;
import de.gematik.zeta.sdk.storage.StorageConfig;
import de.servicehealth.refpopp.vsdm2_client.connector.ConnectorClient;
import de.servicehealth.refpopp.vsdm2_client.properties.ZetaProperties;
import io.ktor.client.plugins.logging.LogLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds and caches one {@link ZetaHttpClient} per VSDM backend URL. Each backend sits behind its
 * own ZETA guard, so the SDK client (registration, authentication) is created lazily on the first
 * request to that backend and reused afterwards.
 */
@Slf4j
@Component
public class ZetaHttpClientProvider {

  private final ZetaProperties zeta;
  private final ConnectorClient connectorClient;

  private final Map<String, ZetaHttpClient> httpClients = new ConcurrentHashMap<>();

  public ZetaHttpClientProvider(final ZetaProperties zeta, final ConnectorClient connectorClient) {
    this.zeta = zeta;
    this.connectorClient = connectorClient;
  }

  @PostConstruct
  void init() {
    Log.INSTANCE.initDebugLogger();
  }

  public ZetaHttpClient getClient(final String backendUrl) {
    return httpClients.computeIfAbsent(backendUrl, this::buildClient);
  }

  private ZetaHttpClient buildClient(final String backendUrl) {
    log.info("Building ZetaSdkClient for {}", backendUrl);
    final ZetaSdkClient sdkClient =
        ZetaSdk.INSTANCE.build(
            backendUrl,
            new BuildConfig(
                "demo-client",
                "0.2.0",
                "sdk-client",
                createStorageConfig(),
                new TpmConfig() {},
                new AuthConfig(
                    List.of(zeta.scope()),
                    30L,
                    zeta.aslProd(),
                    connectorClient,
                    AttestationConfig.software(),
                    zeta.requiredRoleOidOrEmpty()),
                PlatformProductIds.fromCurrentOs(),
                new ZetaHttpClientBuilder()
                    .disableServerValidation(zeta.client().disableServerValidation())
                    .logging(LogLevel.ALL),
                null,
                null,
                null));

    return sdkClient.httpClient(
        it -> {
          it.logging(LogLevel.ALL);
          it.disableServerValidation(zeta.client().disableServerValidation());
          return Unit.INSTANCE;
        });
  }

  private StorageConfig createStorageConfig() {
    final var storage = zeta.storage();
    final String aesB64Key = storage != null ? storage.aesB64Key() : null;
    if (aesB64Key != null && !aesB64Key.isBlank()) {
      return new StorageConfig.Default(aesB64Key, null, "");
    }
    return new StorageConfig.Custom(new InMemoryStorage());
  }

  @PreDestroy
  void close() {
    httpClients.values().forEach(ZetaHttpClient::close);
  }
}
