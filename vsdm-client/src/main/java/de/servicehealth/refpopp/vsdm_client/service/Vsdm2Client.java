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

import de.gematik.zeta.sdk.BuildConfig;
import de.gematik.zeta.sdk.StorageConfig;
import de.gematik.zeta.sdk.TpmConfig;
import de.gematik.zeta.sdk.ZetaSdk;
import de.gematik.zeta.sdk.ZetaSdkClient;
import de.gematik.zeta.sdk.ZetaSdkClientExtension;
import de.gematik.zeta.sdk.attestation.model.AttestationConfig;
import de.gematik.zeta.sdk.attestation.model.PlatformProductId;
import de.gematik.zeta.sdk.authentication.AuthConfig;
import de.gematik.zeta.sdk.authentication.smb.SmbTokenProvider;
import de.gematik.zeta.sdk.network.http.client.HttpClientExtension;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClient;
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClientBuilder;
import de.gematik.zeta.sdk.storage.InMemoryStorage;
import io.ktor.client.plugins.logging.LogLevel;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class Vsdm2Client {
  public static final String POPP_TOKEN_HEADER_NAME = "PoPP";

  private static final Logger log = LoggerFactory.getLogger(Vsdm2Client.class);

  private final ZetaSdkClient zetaSdk;
  private final String vsdmRequestUrl;

  public Vsdm2Client(@Value("${vsdm.mock.url:https://localhost:8082}") String vsdmMockUrl) {
    this.vsdmRequestUrl = vsdmMockUrl + "/vsdm/bundle/123";

    String p12FilePath;
    try {
      InputStream p12InputStream =
          getClass().getClassLoader().getResourceAsStream("certificate/mock_smb.p12");
      if (p12InputStream == null) {
        throw new IllegalStateException("Mock SM-B P12 certificate not found in resources.");
      }

      Path tempFile = Files.createTempFile("mock_smb", ".p12");
      Files.copy(p12InputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
      tempFile.toFile().deleteOnExit(); // Ensure temporary file is deleted on exit

      p12FilePath = tempFile.toAbsolutePath().toString();
    } catch (IOException e) {
      throw new RuntimeException("Failed to load mock SM-B P12 certificate", e);
    }

    zetaSdk =
        ZetaSdk.INSTANCE.build(
            // "https://zeta-cd.westeurope.cloudapp.azure.com",
            vsdmMockUrl, // Corrected: Use vsdmMockUrl as the base URL for the SDK
            new BuildConfig(
                "sample-vsdm-client",
                "0.4.0",
                "vsdm-zeta-client",
                new StorageConfig(new InMemoryStorage(), ""),
                new TpmConfig() {},
                new AuthConfig(
                    List.of("zero:audience"),
                    30,
                    true,
                    new SmbTokenProvider(
                        new SmbTokenProvider.Credentials( // Use the path to the temporary file
                            p12FilePath,
                            "smb-test", // Alias
                            "")), // Password (empty)
                    AttestationConfig.software()),
                getPlatformProductId(),
                new ZetaHttpClientBuilder("")
                    .disableServerValidation(true)
                    .logging(LogLevel.ALL, System.out::println),
                null,
                null));
  }

  public String handleReadVsdRequest(String poppToken) {
    String response;

    Map<String, String> headers = new HashMap<>();
    if (poppToken != null) {
      headers.put(POPP_TOKEN_HEADER_NAME, poppToken);
    }
    // The VSDM mock currently only provides FHIR bundles in XML format.
    headers.put("Accept", MediaType.APPLICATION_XML_VALUE);

    log.info("Attempting to fetch FHIR bundle from URL: {}", vsdmRequestUrl);

    // Create an HttpClient instance from the ZetaSdkClient.
    // This client should be reused if possible, but for this example, we create it per request.
    // Note: The SDK client itself is not closed here anymore to allow reuse.
    try (ZetaHttpClient httpClient =
        zetaSdk.httpClient(
            it -> {
              it.logging(LogLevel.ALL, System.out::println);
              it.disableServerValidation(true);
              return Unit.INSTANCE;
            })) {

      response =
          HttpClientExtension.getAsync(httpClient, vsdmRequestUrl, headers)
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
