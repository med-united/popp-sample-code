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

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.refpopp.vsdm_client.converter.VsdmConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class VsdService {

  private static final Logger logger = LoggerFactory.getLogger(VsdService.class);

  private final VsdmConverter vsdmConverter;
  private final Vsdm2Client vsdm2Client;
  private final RestTemplate restTemplate;

  @Value("${popp.client.api.url:http://localhost:8081/token}")
  private String poppClientApiUrl;

  @Value("${popp.client.communication-type:contact-virtual}")
  private String communicationType;

  @Value("${popp.client.session-id:123456}")
  private String clientSessionId;

  @Value("${vsdm.mock.url:https://localhost:8082}")
  private String vsdmMockUrl;

  public VsdService(VsdmConverter vsdmConverter, Vsdm2Client vsdm2Client) {
    this.vsdmConverter = vsdmConverter;
    this.vsdm2Client = vsdm2Client;

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    this.restTemplate = new RestTemplate(factory);
  }

  public ReadVSDResponse processReadVsd(ReadVSD request) {
    logger.info("Processing ReadVSD request in the service layer.");

    try {
      // Step 1: Get the PoPP token
      String poppToken = fetchPoppToken();

      // Step 2: Extract EhcHandle from the request (card handle, not KVNR)
      String ehcHandle = request.getEhcHandle();
      logger.info("Fetching FHIR bundle for EhcHandle: {}", ehcHandle);

      // Step 3: Call the VSDM 2.0 backend (mock) with the PoPP token
      // String fhirBundle = fetchFhirBundle(ehcHandle, poppToken);
      String fhirBundle = vsdm2Client.handleReadVsdRequest(poppToken);

      // Step 4: Convert FHIR bundle to ReadVSDResponse
      return vsdmConverter.createReadVSDResponse(fhirBundle, poppToken);

    } catch (Exception e) {
      logger.error("Error processing ReadVSD request", e);
      return new ReadVSDResponse();
    }
  }

  private String fetchPoppToken() {
    logger.info("Retrieving POPP token from API: {}", poppClientApiUrl);

    TokenRequest requestPayload = new TokenRequest(communicationType, clientSessionId);

    try {
      TokenResponse response =
          restTemplate.postForObject(poppClientApiUrl, requestPayload, TokenResponse.class);

      if (response != null && response.token() != null && !response.token().isBlank()) {
        return response.token();
      } else {
        logger.warn("Unexpected response or empty token. Response was: {}", response);
        return "";
      }
    } catch (Exception e) {
      logger.error("HTTP request to POPP client failed: {}", e.getMessage());
      return "";
    }
  }

  private record TokenRequest(String communicationType, String clientSessionId) {}

  private record TokenResponse(String token, String error) {}
}
