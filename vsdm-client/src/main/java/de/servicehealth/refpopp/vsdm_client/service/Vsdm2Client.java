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

package de.servicehealth.refpopp.vsdm_client.service;

import static io.ktor.client.plugins.logging.LogLevel.ALL;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import de.gematik.zeta.sdk.BuildConfig;
import de.gematik.zeta.sdk.TpmConfig;
import de.gematik.zeta.sdk.ZetaSdk;
import de.gematik.zeta.sdk.ZetaSdkClient;
import de.gematik.zeta.sdk.ZetaSdkClientExtension;
import de.gematik.zeta.sdk.attestation.model.AttestationConfig;
import de.gematik.zeta.sdk.attestation.model.PlatformProductId;
import de.gematik.zeta.sdk.authentication.AuthConfig;
import de.gematik.zeta.sdk.authentication.SubjectTokenProvider;
import de.gematik.zeta.sdk.authentication.smb.SmbTokenProvider;
import de.gematik.zeta.sdk.network.http.client.HttpClientExtension;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClient;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import de.gematik.zeta.sdk.storage.InMemoryStorage;
import de.gematik.zeta.sdk.storage.StorageConfig;
import de.servicehealth.refpopp.vsdm_client.properties.VsdServerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Vsdm2Client {

  public static final String POPP_TOKEN_HEADER_NAME = "PoPP";

  private final VsdServerProperties vsdmServerProperties;

  private ZetaSdkClient zetaSdk;

  @PostConstruct
  public void init() {
    zetaSdk =
        ZetaSdk.INSTANCE.build(
            vsdmServerProperties.apiUrl(),
            new BuildConfig(
                "sample-vsdm-client",
                "0.4.0",
                "vsdm-zeta-client",
                new StorageConfig.Custom(new InMemoryStorage()),
                new TpmConfig() {},
                new AuthConfig(
                    List.of("popp"),
                    30L,
                    true,
                    getTokenProvider(),
                    AttestationConfig.software(),
                    ""),
                getPlatformProductId(),
                new ZetaHttpClientBuilder().disableServerValidation(true).logging(ALL),
                null,
                null,
                null));
  }

  private SubjectTokenProvider getTokenProvider() {
    try {
      Resource resource = new ClassPathResource("certificate/mock_smb.p12");
      String path = resource.getFile().getAbsolutePath();
      return new SmbTokenProvider(new SmbTokenProvider.Credentials(path, "smb-test", "", ""));
    } catch (IOException e) {
      throw new RuntimeException("Failed to load mock SM-B P12 certificate", e);
    }
  }

  public String handleReadVsdRequest(String poppToken) {
    String response;

    Map<String, String> headers = new HashMap<>();
    if (poppToken != null) {
      headers.put(POPP_TOKEN_HEADER_NAME, poppToken);
    }
    // The VSDM mock only provides FHIR bundles in XML format.
    headers.put("Accept", APPLICATION_XML_VALUE);

    log.info("Attempting to fetch FHIR bundle from URL: {}", vsdmServerProperties.apiUrl());

    try (ZetaHttpClient httpClient =
        zetaSdk.httpClient(
            it -> {
              it.logging(ALL);
              it.disableServerValidation(true);
              return Unit.INSTANCE;
            })) {
      response =
          HttpClientExtension.getAsync(httpClient, vsdmServerProperties.apiUrl(), headers)
              .thenCompose(HttpClientExtension::bodyAsText)
              .whenComplete(
                  (body, ex) -> {
                    if (ex != null) {
                      log.error("Http Get failed", ex);
                    } else {
                      log.info("Body: {}", body);
                    }
                  })
              .toCompletableFuture()
              .join();
    }

    return response;
  }

  @PreDestroy
  public void destroy() {
    ZetaSdkClientExtension.close(zetaSdk);
  }

  private PlatformProductId getPlatformProductId() {
    String os = System.getProperty("os.name").toLowerCase();

    if (os.contains("win")) {
      return new PlatformProductId.WindowsProductId("windows", "storeId", "");
    } else if (os.contains("mac")) {
      return new PlatformProductId.AppleProductId("apple", "macos", List.of());
    } else if (os.contains("nux")) {
      return new PlatformProductId.LinuxProductId("linux", "storeId", "", "");
    }
    throw new RuntimeException("Unsupported OS: " + os);
  }
}
