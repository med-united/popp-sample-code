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

package de.gematik.refpopp.popp_server.vzd;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches an access token for the VZD (Verzeichnisdienst) FHIR directory.
 *
 * <p>The token is obtained in two steps:
 *
 * <ol>
 *   <li>A TI provider token is requested from the OAuth token endpoint using the {@code
 *       client_credentials} grant.
 *   <li>The TI provider token is exchanged for the actual provider (access) token at the
 *       service-authenticate endpoint.
 * </ol>
 *
 * <p>The resulting access token is cached until shortly before it expires.
 */
@Slf4j
@Service
public class VzdTokenService {

  private static final int DEFAULT_EXPIRES_IN_SECONDS = 300;

  private final RestClient restClient;
  private final VzdTokenProperties properties;
  private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

  public VzdTokenService(VzdTokenProperties properties) {
    this.restClient = RestClient.create();
    this.properties = properties;
  }

  /**
   * Returns a valid VZD access token, using the cached token if it is still valid or fetching a new
   * one otherwise.
   *
   * @return a valid bearer access token
   * @throws VzdTokenException if the token could not be obtained
   */
  public String getAccessToken() {
    var current = cachedToken.get();
    if (current != null && current.isValid()) {
      log.debug("| Returning cached VZD access token (source=cache)");
      return current.token();
    }
    return refreshToken();
  }

  private synchronized String refreshToken() {
    var current = cachedToken.get();
    if (current != null && current.isValid()) {
      return current.token();
    }

    if (!StringUtils.hasText(properties.getClientId())
        || !StringUtils.hasText(properties.getClientSecret())) {
      throw new VzdTokenException("Missing VZD clientId/clientSecret configuration");
    }

    var tiProviderToken = fetchTiProviderToken();
    var providerToken = fetchProviderToken(tiProviderToken);

    var expiresAt =
        (int) Instant.now().getEpochSecond()
            + Math.max(providerToken.expiresIn() - properties.getSkewSeconds(), 0);
    cachedToken.set(new CachedToken(providerToken.accessToken(), expiresAt));
    log.debug(
        "| Fetched new VZD access token (source=fetched, expiresIn={}s)",
        providerToken.expiresIn());
    return providerToken.accessToken();
  }

  private String fetchTiProviderToken() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());

    Map<String, Object> payload;
    try {
      payload =
          restClient
              .post()
              .uri(properties.getTokenUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(MAP_TYPE);
    } catch (RestClientException exc) {
      throw new VzdTokenException("oauth upstream failed: " + exc.getMessage(), exc);
    }

    var token = extractToken(payload, "No access_token in token response");
    return token.accessToken();
  }

  private Token fetchProviderToken(String tiProviderToken) {
    Map<String, Object> payload;
    try {
      payload =
          restClient
              .get()
              .uri(properties.getServiceAuthUrl())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + tiProviderToken)
              .retrieve()
              .body(MAP_TYPE);
    } catch (RestClientException exc) {
      throw new VzdTokenException("service-auth upstream failed: " + exc.getMessage(), exc);
    }

    return extractToken(payload, "No access_token in provider response");
  }

  private Token extractToken(Map<String, Object> payload, String missingTokenMessage) {
    if (payload == null) {
      throw new VzdTokenException(missingTokenMessage + ": <empty response>");
    }

    Object token = firstNonNull(payload.get("access_token"), payload.get("token"));
    if (token == null || !StringUtils.hasText(token.toString())) {
      throw new VzdTokenException(missingTokenMessage + ": " + payload);
    }

    Object expiresInValue =
        firstNonNull(payload.get("expires_in"), payload.get("expires-in"), payload.get("expires"));
    var expiresIn = parseExpiresIn(expiresInValue);

    return new Token(token.toString(), expiresIn);
  }

  private int parseExpiresIn(Object value) {
    if (value == null) {
      return DEFAULT_EXPIRES_IN_SECONDS;
    }
    try {
      if (value instanceof Number number) {
        return number.intValue();
      }
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException e) {
      return DEFAULT_EXPIRES_IN_SECONDS;
    }
  }

  private static Object firstNonNull(Object... values) {
    for (Object value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private record Token(String accessToken, int expiresIn) {}

  private record CachedToken(String token, int expiresAt) {
    boolean isValid() {
      return Instant.now().getEpochSecond() < expiresAt;
    }
  }
}
