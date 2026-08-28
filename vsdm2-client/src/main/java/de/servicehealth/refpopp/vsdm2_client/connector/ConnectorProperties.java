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

package de.servicehealth.refpopp.vsdm2_client.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector")
public record ConnectorProperties(
    String endPointUrl, String smcbIccsn, Secure secure, Context context) {

  public record Secure(
      boolean enable,
      boolean hostnameValidation,
      String keystore,
      String keystorePassword,
      boolean trustAll,
      String truststore,
      String truststorePassword) {}

  public record Context(
      String clientSystemId, String mandantId, String workplaceId, String userId) {}
}
