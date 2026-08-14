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

package de.gematik.refpopp.popp_client.client;

import de.gematik.refpopp.popp_client.connector.authsignatureservice.ExternalAuthenticateClient;
import de.gematik.refpopp.popp_client.connector.certificateservice.ReadCardCertificateClient;
import de.gematik.zeta.sdk.authentication.smcb.BaseSmcbTokenProvider;
import java.util.Base64;
import kotlin.coroutines.Continuation;

/**
 * Java implementation of the ZETA SDK's {@link BaseSmcbTokenProvider}. Delegates the SMC-B
 * Konnektor SOAP calls to popp-client's own clients instead of the SDK's Ktor-based {@code
 * ConnectorApiImpl}, so popp-client owns all Konnektor I/O.
 *
 * <p>The two overrides are Kotlin {@code suspend fun}s; from Java they appear as methods taking an
 * extra {@code Continuation} parameter and returning {@code Object}. Returning the value directly
 * (rather than {@code COROUTINE_SUSPENDED}) signals synchronous completion to Kotlin's coroutine
 * runtime.
 */
public class PoppSubjectTokenProvider extends BaseSmcbTokenProvider {

  private final ReadCardCertificateClient readCardCertificateClient;
  private final ExternalAuthenticateClient externalAuthenticateClient;
  private final String cardHandle;

  public PoppSubjectTokenProvider(
      final ReadCardCertificateClient readCardCertificateClient,
      final ExternalAuthenticateClient externalAuthenticateClient,
      final String cardHandle) {
    this.readCardCertificateClient = readCardCertificateClient;
    this.externalAuthenticateClient = externalAuthenticateClient;
    this.cardHandle = cardHandle;
  }

  @Override
  protected Object readCertificate(final Continuation<? super byte[]> $completion) {
    return readCardCertificateClient.performReadCardCertificate(cardHandle);
  }

  @Override
  protected Object externalAuthenticate(
      final String base64Challenge, final Continuation<? super byte[]> $completion) {
    final byte[] digest = Base64.getDecoder().decode(base64Challenge);
    return externalAuthenticateClient.performExternalAuthenticate(cardHandle, digest);
  }
}
