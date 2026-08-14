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

package de.servicehealth.refpopp.vsdm_client.endpoint;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.refpopp.vsdm_client.service.PoppService;
import de.servicehealth.refpopp.vsdm_client.service.VsdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Slf4j
@Endpoint
@RequiredArgsConstructor
public class VsdServiceEndpoint {

  private static final String NAMESPACE_URI = "http://ws.gematik.de/conn/vsds/VSDService/v5.2";

  private final PoppService poppService;
  private final VsdService vsdService;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "ReadVSD")
  @ResponsePayload
  public ReadVSDResponse readVsd(@RequestPayload ReadVSD request) {
    String ehcHandle = request.getEhcHandle();
    log.info("Fetching FHIR bundle for EhcHandle: {}", ehcHandle);

    TokenResponse tokenResponse = poppService.fetchPoppToken(ehcHandle);
    return vsdService.processReadVsd(tokenResponse.token());
  }
}
