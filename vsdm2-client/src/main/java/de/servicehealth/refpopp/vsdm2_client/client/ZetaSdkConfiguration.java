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
import de.gematik.zeta.sdk.authentication.SubjectTokenProvider;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClient;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import de.gematik.zeta.sdk.storage.InMemoryStorage;
import de.gematik.zeta.sdk.storage.StorageConfig;
import de.servicehealth.refpopp.vsdm2_client.connector.ConnectorClient;
import de.servicehealth.refpopp.vsdm2_client.properties.ConnectorProperties;
import de.servicehealth.refpopp.vsdm2_client.properties.VsdmServerProperties;
import de.servicehealth.refpopp.vsdm2_client.properties.ZetaProperties;
import de.servicehealth.refpopp.vsdm2_client.support.LinuxPosture;
import io.ktor.client.plugins.logging.LogLevel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link ZetaSdkClient} once at startup with the SMC-B Konnektor token provider and
 * exposes a {@link ZetaHttpClient} singleton for VSDM calls. Mirrors popp-client's {@code
 * SecureWebSocketClient} BuildConfig shape (same scopes/attestation/aslProdEnv/timeout), but goes
 * via the SDK's HTTP path instead of WebSockets.
 */
@Slf4j
@Configuration
public class ZetaSdkConfiguration {

  private final VsdmServerProperties vsdmServer;
  private final ZetaProperties zeta;
  private final ConnectorClient connectorClient;
  private final String smcbCardHandle;

  private ZetaSdkClient sdkClient;
  private ZetaHttpClient httpClient;

  public ZetaSdkConfiguration(
      final VsdmServerProperties vsdmServer,
      final ZetaProperties zeta,
      final ConnectorProperties connector,
      final ConnectorClient connectorClient) {
    this.vsdmServer = vsdmServer;
    this.zeta = zeta;
    this.connectorClient = connectorClient;
    this.smcbCardHandle = connector.terminalConfiguration().smcbCardHandle();
  }

  @PostConstruct
  void build() {
    Log.INSTANCE.initDebugLogger();

    LinuxPosture.run(
        () -> {
          final SubjectTokenProvider tokenProvider =
              new SmcbKonnektorTokenProvider(connectorClient, smcbCardHandle);

          this.sdkClient =
              ZetaSdk.INSTANCE.build(
                  vsdmServer.url(),
                  new BuildConfig(
                      "demo-client",
                      "0.2.0",
                      "sdk-client",
                      new StorageConfig.Custom(new InMemoryStorage()),
                      new TpmConfig() {},
                      new AuthConfig(
                          List.of(zeta.scope()),
                          30L,
                          zeta.aslProd(),
                          tokenProvider,
                          AttestationConfig.software(),
                          zeta.requiredRoleOidOrEmpty()),
                      PlatformProductIds.fromCurrentOs(),
                      new ZetaHttpClientBuilder()
                          .disableServerValidation(zeta.client().disableServerValidation())
                          .logging(LogLevel.ALL),
                      null,
                      null,
                      null));

          this.httpClient =
              sdkClient.httpClient(
                  it -> {
                    it.logging(LogLevel.ALL);
                    it.disableServerValidation(zeta.client().disableServerValidation());
                    return Unit.INSTANCE;
                  });
        });
    log.info("ZetaSdkClient + ZetaHttpClient initialized for {}", vsdmServer.url());
  }

  @PreDestroy
  void close() {
    if (httpClient != null) {
      httpClient.close();
    }
  }

  @Bean
  public ZetaHttpClient zetaHttpClient() {
    return httpClient;
  }

  @Bean
  public ZetaSdkClient zetaSdkClient() {
    return sdkClient;
  }
}
