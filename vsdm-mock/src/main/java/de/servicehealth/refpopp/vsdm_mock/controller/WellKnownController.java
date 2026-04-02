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

package de.servicehealth.refpopp.vsdm_mock.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WellKnownController {

  private static final Logger log = LoggerFactory.getLogger(WellKnownController.class);

  @GetMapping(
      value = "/.well-known/oauth-protected-resource",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getOAuthProtectedResource() {
    log.info("Mock VSDM 2.0 backend called for /.well-known/oauth-protected-resource");

    // This endpoint provides metadata about the protected resource (VSDM 2.0 backend).
    String metadata =
        """
        {
          "audience": "zero:audience",
          "dpop_signing_alg_values_supported": [
            "ES256"
          ],
          "grant_types_supported": [
            "client_credentials"
          ],
          "resource": "https://vsdm-mock:8082/vsdm/bundle/123",
          "scopes_supported": [
            "vsdm:read"
          ],
          "authorization_servers": [
            "https://vsdm-mock:8082/.well-known/oauth-authorization-server"
          ],
          "zeta_asl_use": "not_supported",
          "token_endpoint_auth_methods_supported": [
            "private_key_jwt"
          ]
        }
        """;
    return ResponseEntity.ok(metadata);
  }

  @GetMapping(
      value = "/.well-known/oauth-authorization-server",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getOAuthAuthorizationServer() {
    log.info("Mock VSDM 2.0 backend called for /.well-known/oauth-authorization-server");

    // Minimal OAuth 2.0 Authorization Server Metadata
    String metadata =
        """
        {
          "issuer": "https://vsdm-mock:8082",
          "authorization_endpoint": "https://vsdm-mock:8082/oauth2/authorize",
          "token_endpoint": "https://vsdm-mock:8082/oauth2/token",
          "jwks_uri": "https://vsdm-mock:8082/oauth2/jwks",
          "response_types_supported": [
            "code"
          ],
          "scopes_supported": [
            "zero:audience",
            "openid",
            "vsdm:read"
          ],
          "grant_types_supported": [
            "client_credentials",
            "authorization_code"
          ],
          "nonce_endpoint": "https://vsdm-mock:8082/oauth2/nonce",
          "openid_providers_endpoint": "https://vsdm-mock:8082/oauth2/op",
          "token_endpoint_auth_signing_alg_values_supported": [
            "ES256"
          ],
          "ui_locales_supported": ["de", "en"],
          "code_challenge_methods_supported": ["S256"],
          "token_endpoint_auth_methods_supported": [
            "private_key_jwt"
          ]
        }
        """;
    return ResponseEntity.ok(metadata);
  }

  @GetMapping(value = "/oauth2/op", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getOpenIdProviders() {
    log.info("Mock VSDM 2.0 backend called for /oauth2/op");
    // As per OpenID Connect Discovery, this endpoint typically returns a list of OpenID Provider
    // configurations.
    // For a mock, an empty array is a valid and minimal JSON response.
    String response = "[]";
    return ResponseEntity.ok(response);
  }

  @GetMapping(value = "/oauth2/nonce", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getNonce() {
    log.info("Mock VSDM 2.0 backend called for /oauth2/nonce");
    // In a real scenario, this would generate a cryptographically secure random nonce.
    // "mock-nonce-1234567890" Base64 encoded is "bW9jay1ub25jZS0xMjM0NTY3ODkw"
    String base64EncodedNonce = "bW9jay1ub25jZS0xMjM0NTY3ODkw";

    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(base64EncodedNonce);
  }

  @PostMapping(
      value = "/oauth2/op",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> registerClient(@RequestBody String requestBody) {
    log.info("Mock VSDM 2.0 backend called for POST /oauth2/op with body: {}", requestBody);

    // For a mock, we return a fixed client_id and echo back some of the requested parameters.
    // In a real implementation, a unique client_id would be generated and client details stored.
    String response =
        """
        {
          "client_id": "mock-client-id-12345",
          "client_name": "vsdm-zeta-client",
          "token_endpoint_auth_method": "private_key_jwt",
          "grant_types": ["urn:ietf:params:oauth:grant-type:token-exchange", "refresh_token"],
          "response_types": ["token"],
          "jwks": {
            "keys": [
              {
                "kid": "6zZnNDvlWOSshhmjZgehINELkiPKcqXLpNWaVUMEqfc",
                "kty": "EC",
                "alg": "ES256",
                "use": "sig",
                "crv": "P-256",
                "x": "N88PWdoFflNOcNB5D5tWqOL51WBMm9p5U2_FlGBqRgk",
                "y": "P07Q-C0P1e2SWEz3Ywi8bcoRgIOdv_fEA3Nls9753QI"
              }
            ]
          }
        }
        """;
    // A 201 Created status is standard for successful client registration.
    return ResponseEntity.status(201).body(response);
  }

  @PostMapping(
      value = "/oauth2/token",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> postOAuthToken(@RequestBody String requestBody) {
    log.info("Mock VSDM 2.0 backend called for POST /oauth2/token with body: {}", requestBody);

    // For this mock, we return a fixed, plausible token response.
    String response =
        """
        {
          "access_token": "mock-access-token-1234567890",
          "token_type": "Bearer",
          "expires_in": 3600,
          "scope": "zero:audience",
          "refresh_token": "mock-refresh-token-abcdefg"
        }
        """;
    return ResponseEntity.ok(response);
  }
}
