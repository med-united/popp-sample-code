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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vzd.token")
@Data
public class VzdTokenProperties {

  /** OAuth token endpoint used to obtain the TI provider token (client_credentials grant). */
  private String tokenUrl =
      "https://auth-ref.vzd.ti-dienste.de:9443/auth/realms/Service-Authenticate/protocol/openid-connect/token";

  /**
   * Service-authenticate endpoint used to exchange the TI provider token for the provider token.
   */
  private String serviceAuthUrl =
      "https://fhir-directory-ref.vzd.ti-dienste.de/service-authenticate";

  /** OAuth client id. */
  private String clientId;

  /** OAuth client secret. */
  private String clientSecret;

  /** Seconds subtracted from the token lifetime to avoid using an (almost) expired token. */
  private int skewSeconds = 30;
}
