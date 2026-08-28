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

package de.servicehealth.refpopp.vsdm2_client.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ZETA SDK / Guard settings.
 *
 * <p>The Guard uses its own scope vocabulary (zero:read / zero:write / zero:audience /
 * zero:register / zero:manage); {@code zero:audience} makes the Guard emit the audience claim on
 * the access token. {@code aslProd} toggles the ASL production environment (false for DEV/RU/TU).
 */
@ConfigurationProperties(prefix = "zeta")
public record ZetaProperties(
    String scope, boolean aslProd, Client client, String requiredRoleOid, Storage storage) {

  public record Client(boolean disableServerValidation) {}

  public record Storage(String aesB64Key) {}

  public String requiredRoleOidOrEmpty() {
    return requiredRoleOid == null ? "" : requiredRoleOid;
  }
}
