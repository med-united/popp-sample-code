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
import de.gematik.zeta.sdk.network.http.client.ZetaHttpClient;
import de.servicehealth.refpopp.vsdm2_client.converter.VsdmConverter;
import de.servicehealth.refpopp.vsdm2_client.support.LinuxPosture;
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

  private static final String VSDMBUNDLE_PATH = "vsdservice/v1/vsdmbundle?profileVersion=1.0";
  private static final String POPP_HEADER = "PoPP";
  private static final String VSD_STATUS_ERROR = "-1";

  private final ZetaHttpClient httpClient;
  private final VsdmConverter vsdmConverter;

  /**
   * Fetches the VSDM bundle for the given PoPP token. The SDK call runs under a {@linkplain
   * LinuxPosture linux posture} so the per-request attestation stays consistent with the platform
   * product id set at SDK build time.
   */
  public String readVsdmBundle(final String poppToken) {
    final Map<String, String> headers = new HashMap<>();
    headers.put(POPP_HEADER, poppToken);
    headers.put("Accept", FHIR_JSON);
    // VSDM2 requires If-None-Match for ETag-based conditional reads. On first request with
    // no previous ETag, send "0" per spec (VSDSERVICE_INVALID_PATIENT_RECORD_VERSION otherwise).
    headers.put("If-None-Match", "\"0\"");

    log.info("Calling VSDM2 {} with PoPP token", VSDMBUNDLE_PATH);

    return LinuxPosture.call(
        () ->
            HttpClientExtension.getAsync(httpClient, VSDMBUNDLE_PATH, headers)
                .thenCompose(HttpClientExtension::bodyAsText)
                .join());
  }

  /**
   * Fetches the VSDM bundle and maps it onto a gematik {@code ReadVSDResponse}. Mirrors
   * vsdm-client's {@code VsdService.processReadVsd}: on any failure an error response with status
   * {@code -1} is returned rather than propagating the exception.
   */
  public ReadVSDResponse readVsdmBundleAsVsd(final String poppToken) {
    log.info("Processing ReadVSD request in the service layer.");
    try {
      final String fhirBundle = readVsdmBundle(poppToken);
      return vsdmConverter.createReadVSDResponse(fhirBundle, poppToken);
    } catch (final Exception e) {
      log.error("Error processing ReadVSD request", e);
      final ReadVSDResponse errorResponse = new ReadVSDResponse();
      final VSDStatusType errorStatus = new VSDStatusType();
      errorStatus.setStatus(VSD_STATUS_ERROR);
      errorResponse.setVSDStatus(errorStatus);
      return errorResponse;
    }
  }
}
