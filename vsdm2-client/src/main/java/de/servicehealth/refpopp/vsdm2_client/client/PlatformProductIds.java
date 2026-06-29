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

package de.servicehealth.refpopp.vsdm2_client.client;

import de.gematik.zeta.sdk.attestation.model.PlatformProductId;
import java.util.List;
import java.util.Locale;

/** Maps the host operating system to the ZETA {@link PlatformProductId} expected by the Guard. */
final class PlatformProductIds {

  static final String APPLE_PLATFORM_TYPE_MACOS = "macos";
  static final String LINUX_PACKAGING_TYPE_JAR = "jar";
  static final String PLATFORM_PRODUCT_APPLICATION_ID = "testhub";
  static final String PLATFORM_PRODUCT_VERSION = "latest";

  private PlatformProductIds() {}

  static PlatformProductId fromCurrentOs() {
    return from(System.getProperty("os.name", ""));
  }

  static PlatformProductId from(final String osName) {
    final var normalizedOsName = osName.toLowerCase(Locale.ROOT);

    if (normalizedOsName.contains("mac")) {
      return new PlatformProductId.AppleProductId(
          PlatformProductId.PLATFORM_APPLE, APPLE_PLATFORM_TYPE_MACOS, List.of());
    }
    if (normalizedOsName.contains("win")) {
      return new PlatformProductId.WindowsProductId(PlatformProductId.PLATFORM_WINDOWS, "", "");
    }
    if (normalizedOsName.contains("linux")
        || normalizedOsName.contains("nux")
        || normalizedOsName.contains("nix")) {
      return new PlatformProductId.LinuxProductId(
          PlatformProductId.PLATFORM_LINUX,
          LINUX_PACKAGING_TYPE_JAR,
          PLATFORM_PRODUCT_APPLICATION_ID,
          PLATFORM_PRODUCT_VERSION);
    }
    throw new IllegalStateException(
        "Unsupported operating system for ZETA platform product id: " + osName);
  }
}
