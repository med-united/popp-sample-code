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

package de.gematik.refpopp.popp_client.controller;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.cardreader.CardReader;
import de.gematik.refpopp.popp_client.client.CommunicationService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/token")
@Slf4j
public class CardRestController {

  private final CommunicationService communicationService;
  private final CardReader cardReaderService;

  public CardRestController(
      final CommunicationService communicationService, final CardReader cardReaderService) {
    this.communicationService = communicationService;
    this.cardReaderService = cardReaderService;
  }

  @GetMapping("/{communicationType}")
  public String getToken(
      @PathVariable final String communicationType,
      @RequestParam(name = "clientsessionid", required = false, defaultValue = "")
          final String clientSessionId) {

    Future<String> tokenFuture;
    try {
      switch (communicationType.toLowerCase()) {
        case "contact-standard":
          cardReaderService.startCheckForCardReader();
          tokenFuture =
              communicationService.start(CardConnectionType.CONTACT_STANDARD, clientSessionId);
          break;
        case "contact-connector":
          tokenFuture =
              communicationService.start(CardConnectionType.CONTACT_CONNECTOR, clientSessionId);
          break;
        case "contact-connector-via-standard-terminal":
          cardReaderService.startCheckForCardReader();
          communicationService.startConnectorMock(
              CardConnectionType.CONTACT_CONNECTOR, clientSessionId);
          return "eyJraWQiOiI0SVZZSHk3MjFLMHJualo4XzlmbnNLb2ZzMGVLaEdPY3FFRFZvMFJCWkZRIiwidHlwIjoidm5kLnRlbGVtYXRpay5wb3BwK2p3dCIsImFsZyI6IkVTMjU2In0.eyJwcm9vZk1ldGhvZCI6ImVoYy1wcmFjdGl0aW9uZXItdHJ1c3RlZGNoYW5uZWwiLCJwYXRpZW50UHJvb2ZUaW1lIjoxNzcwNzUzNjI4LCJhY3RvcklkIjoidGVsZW1hdGlrLWlkIiwicGF0aWVudElkIjoiSzIxMDE0MDE1NSIsImF1dGhvcml6YXRpb25fZGV0YWlscyI6ImRldGFpbHMiLCJpc3MiOiJodHRwczovL3BvcHAuZXhhbXBsZS5jb20iLCJhY3RvclByb2Zlc3Npb25PaWQiOiIxLjIuMjc2LjAuNzYuNC41MCIsInZlcnNpb24iOiIxLjAuMCIsImlhdCI6MTc3MDc1MzYyOCwiaW5zdXJlcklkIjoiMTAyMTcxMDEyIn0.9vmGOxSxwiebQHw_pqogWOVm-gbUp1MlytSY9uUdoNglthYV6-qwrnJgR_FBi_NOgMO_ZuGI8aN1hBaE-1nyoA";
        case "contactless-standard":
          cardReaderService.startCheckForCardReader();
          tokenFuture =
              communicationService.start(CardConnectionType.CONTACTLESS_STANDARD, clientSessionId);
          break;
        case "contactless-connector":
          tokenFuture =
              communicationService.start(CardConnectionType.CONTACTLESS_CONNECTOR, clientSessionId);
          break;
        case "g3":
          log.info("| G3 not yet implemented");
          return "G3 not yet implemented";
        default:
          log.error("| Unknown command: {}", communicationType);
          return "Unknown command";
      }
      return tokenFuture != null ? tokenFuture.get(10, TimeUnit.SECONDS) : "No token received";
    } catch (final Exception e) {
      log.error("| Error during communication: {}", e.getMessage());
      return "Error during communication: " + e.getMessage();
    }
  }
}
