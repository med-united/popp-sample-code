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

package de.servicehealth.refpopp.vsdm2_client.support;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Runs an action with {@code os.name} temporarily reported as {@code linux} on macOS hosts.
 *
 * <p>The ZETA Guard policy rejects an apple+software posture. Both the SDK build (which emits
 * {@code platform=linux + posture_type=software} and derives the {@code platform_product_id}) and
 * each HTTP call (whose attestation must stay consistent with that product id) have to observe
 * {@code os.name=linux}. JNA loads native libraries with the real {@code os.name} at startup, so
 * this temporary override does not affect native lib loading. Same workaround as popp-client's
 * {@code ClientServerCommunicationService.connect()}.
 */
public final class LinuxPosture {

  private LinuxPosture() {}

  public static <T> T call(final Supplier<T> action) {
    final String originalOsName = System.getProperty("os.name");
    final boolean isMac =
        originalOsName != null && originalOsName.toLowerCase(Locale.ROOT).contains("mac");
    try {
      if (isMac) {
        System.setProperty("os.name", "linux");
      }
      return action.get();
    } finally {
      if (isMac) {
        System.setProperty("os.name", originalOsName);
      }
    }
  }

  public static void run(final Runnable action) {
    call(
        () -> {
          action.run();
          return null;
        });
  }
}
