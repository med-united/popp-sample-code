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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Extracts claims from a PoPP token. No signature verification happens here — the VSDM backend
 * verifies the token; we only need the routing information.
 */
@Component
public class PoppTokenParser {

  private final ObjectMapper objectMapper;

  public PoppTokenParser(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Returns the {@code insurerId} claim (the insurer's IK number) of the given PoPP token. */
  public String insurerId(final String poppToken) {
    return claim(poppToken, "insurerId");
  }

  /** Returns the {@code patientId} claim (the insurant's KVNR) of the given PoPP token. */
  public String kvnr(final String poppToken) {
    return claim(poppToken, "patientId");
  }

  private String claim(final String poppToken, final String name) {
    final var parts = poppToken.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException("PoPP token is not a JWT");
    }
    final var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    final Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
    final var value = claims.get(name);
    return value == null ? null : String.valueOf(value);
  }
}
