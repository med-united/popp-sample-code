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

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Fetches the VSDM 2.0 data as ReadVSDResponse XML from the vsdm2-client using a PoPP token. */
@Component
@ConditionalOnProperty(prefix = "connector.cetp", name = "enabled", havingValue = "true")
@Slf4j
public class Vsdm2ReadClient {

  private final RestClient restClient = RestClient.create();
  private final String vsdm2ClientUrl;

  public Vsdm2ReadClient(@Value("${vsdm2-client.url:}") final String vsdm2ClientUrl) {
    this.vsdm2ClientUrl = vsdm2ClientUrl;
    if (isConfigured()) {
      log.info("| VSDM 2.0 fetch enabled, vsdm2-client at {}", vsdm2ClientUrl);
    } else {
      log.warn("| No vsdm2-client.url configured, VSDM 2.0 fetch is disabled");
    }
  }

  public boolean isConfigured() {
    return vsdm2ClientUrl != null && !vsdm2ClientUrl.isBlank();
  }

  public String readVsdResponseXml(final String poppToken) {
    final var uri = vsdm2ClientUrl + "/vsdm/xml";
    log.info("| Fetching VSDM 2.0 data from {}", uri);
    return restClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_XML)
        .body(Map.of("poppToken", poppToken))
        .retrieve()
        .body(String.class);
  }
}
