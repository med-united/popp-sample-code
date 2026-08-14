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

package de.servicehealth.refpopp.vsdm2_client.api;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.refpopp.vsdm2_client.service.VsdmService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vsdm")
@RequiredArgsConstructor
public class VsdmController {

  private final VsdmService vsdmService;

  /** Returns the raw VSDM 2.0 FHIR bundle (application/fhir+json) for the given PoPP token. */
  @PostMapping(
      path = "/fhir",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = VsdmService.FHIR_JSON)
  public ResponseEntity<?> readVsdAsFhir(@RequestBody final VsdmReadRequest request) {
    if (request == null || request.poppToken() == null || request.poppToken().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "poppToken is required"));
    }
    return ResponseEntity.ok(vsdmService.readVsdmBundle(request.poppToken()));
  }

  /**
   * Returns the same data mapped onto a gematik {@code ReadVSDResponse} (application/xml): the FHIR
   * bundle is fetched as in {@link #readVsdAsFhir} and then converted.
   */
  @PostMapping(
      path = "/xml",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<ReadVSDResponse> readVsdAsXml(@RequestBody final VsdmReadRequest request) {
    if (request == null || request.poppToken() == null || request.poppToken().isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(vsdmService.readVsdmBundleAsVsd(request.poppToken()));
  }

  public record VsdmReadRequest(String poppToken) {}
}
