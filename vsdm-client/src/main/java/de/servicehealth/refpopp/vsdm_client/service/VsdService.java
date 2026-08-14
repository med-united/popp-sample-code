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

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5.VSDStatusType;
import de.servicehealth.refpopp.vsdm_client.converter.VsdmConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VsdService {

  private final VsdmConverter vsdmConverter;
  private final Vsdm2Client vsdm2Client;

  public ReadVSDResponse processReadVsd(String poppToken) {
    log.info("Processing ReadVSD request in the service layer.");
    try {
      // Step 1: Call the VSDM 2.0 backend (mock) with the PoPP token
      String fhirBundle = vsdm2Client.handleReadVsdRequest(poppToken);

      // Step 2: Convert FHIR bundle to ReadVSDResponse
      return vsdmConverter.createReadVSDResponse(fhirBundle, poppToken);
    } catch (Exception e) {
      log.error("Error processing ReadVSD request", e);

      ReadVSDResponse errorResponse = new ReadVSDResponse();
      VSDStatusType errorStatus = new VSDStatusType();

      // Oder ein Gematik-spezifischer Fehlercode
      errorStatus.setStatus("-1");
      errorResponse.setVSDStatus(errorStatus);
      return errorResponse;
    }
  }
}
