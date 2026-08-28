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

package de.servicehealth.refpopp.vsdm2_client.service;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5.VSDStatusType;
import de.gematik.zeta.sdk.network.http.client.HttpClientExtension;
import de.servicehealth.refpopp.vsdm2_client.client.ServiceDiscoveryClient;
import de.servicehealth.refpopp.vsdm2_client.client.ZetaHttpClientProvider;
import de.servicehealth.refpopp.vsdm2_client.connector.ConnectorClient;
import de.servicehealth.refpopp.vsdm2_client.converter.VsdmConverter;
import de.servicehealth.refpopp.vsdm2_client.converter.VsdmProcessingException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Reads a VSDM 2.0 FHIR bundle from the Fachdienst via the ZETA SDK HTTP client. */
@Slf4j
@Service
@RequiredArgsConstructor
public class VsdmService {

  public static final String FHIR_JSON = "application/fhir+json";

  private static final String VSDMBUNDLE_PATH = "vsdservice/v1/vsdmbundle?profileVersion=1.1";
  private static final String POPP_HEADER = "PoPP";
  private static final String VSD_STATUS_ERROR = "-1";

  private final ZetaHttpClientProvider zetaHttpClientProvider;
  private final ServiceDiscoveryClient serviceDiscoveryClient;
  private final PoppTokenParser poppTokenParser;
  private final VsdmConverter vsdmConverter;
  private final ConnectorClient connectorClient;

  /** Fetches the VSDM bundle for the given PoPP token. */
  public String readVsdmBundle(final String poppToken) {
    final Map<String, String> headers = new HashMap<>();
    headers.put(POPP_HEADER, poppToken);
    headers.put("Accept", FHIR_JSON);
    // VSDM2 requires If-None-Match for ETag-based conditional reads. On first request with
    // no previous ETag, send "0" per spec (VSDSERVICE_INVALID_PATIENT_RECORD_VERSION otherwise).
    headers.put("If-None-Match", "\"0\"");

    final var backendUrl = resolveBackendUrl(poppToken);
    final var httpClient = zetaHttpClientProvider.getClient(backendUrl);
    log.info("Calling VSDM2 {} at {} with PoPP token", VSDMBUNDLE_PATH, backendUrl);

    return HttpClientExtension.getAsync(httpClient, VSDMBUNDLE_PATH, headers)
        .thenCompose(HttpClientExtension::bodyAsText)
        .join();
  }

  /**
   * Resolves the VSDM backend for the token's insurer (IK number) via the TI service discovery
   * catalog.
   */
  private String resolveBackendUrl(final String poppToken) {
    String insurerId = null;
    try {
      insurerId = poppTokenParser.insurerId(poppToken);
    } catch (Exception e) {
      log.warn("Could not extract insurerId from PoPP token: {}", e.getMessage());
    }
    if (insurerId != null) {
      final var resolved = serviceDiscoveryClient.resolveVsdmUrl(insurerId);
      if (resolved.isPresent()) {
        log.info("Routing insurerId {} to VSDM backend {}", insurerId, resolved.get());
        return resolved.get();
      }
    }
    throw new VsdmProcessingException("No VSDM backend found for insurer " + insurerId);
  }

  /**
   * Fetches the VSDM bundle and maps it onto a gematik {@code ReadVSDResponse}. If the VSDM 2.0
   * route fails, falls back to the classic VSDM 1.x route: ReadVSD via the Konnektor's VSDService.
   * Only if that fails too, an error response with status {@code -1} is returned rather than
   * propagating the exception (mirrors vsdm-client's {@code VsdService.processReadVsd}).
   */
  public ReadVSDResponse readVsdmBundleAsVsd(final String poppToken) {
    log.info("Processing ReadVSD request in the service layer.");
    String fhirBundle = null;
    try {
      fhirBundle = readVsdmBundle(poppToken);
      return vsdmConverter.createReadVSDResponse(fhirBundle, poppToken);
    } catch (final Exception e) {
      log.warn(
          "VSDM 2.0 read failed, falling back to ReadVSD via the connector. Response body was: {}",
          fhirBundle,
          e);
      return readVsdViaConnector(poppToken);
    }
  }

  private ReadVSDResponse readVsdViaConnector(final String poppToken) {
    try {
      String kvnr = null;
      try {
        kvnr = poppTokenParser.kvnr(poppToken);
      } catch (final Exception e) {
        log.warn("Could not extract KVNR from PoPP token: {}", e.getMessage());
      }
      return connectorClient.readVsd(kvnr);
    } catch (final Exception e) {
      log.error("Connector ReadVSD fallback failed as well", e);
      final ReadVSDResponse errorResponse = new ReadVSDResponse();
      final VSDStatusType errorStatus = new VSDStatusType();
      errorStatus.setStatus(VSD_STATUS_ERROR);
      errorResponse.setVSDStatus(errorStatus);
      return errorResponse;
    }
  }
}
