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

package de.servicehealth.refpopp.vsdm2_client.configuration.helper;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Delegates certificate trust checks to a real trust manager, but routes the socket/engine-bound
 * overloads to the basic checks — skipping the endpoint (hostname) identification step.
 */
public class NoHostNameValidationWrappedTrustManager extends X509ExtendedTrustManager {

  private final X509ExtendedTrustManager delegate;

  public NoHostNameValidationWrappedTrustManager(final X509ExtendedTrustManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public void checkClientTrusted(final X509Certificate[] chain, final String authType)
      throws CertificateException {
    delegate.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(final X509Certificate[] chain, final String authType)
      throws CertificateException {
    delegate.checkServerTrusted(chain, authType);
  }

  @Override
  public void checkClientTrusted(
      final X509Certificate[] chain, final String authType, final Socket socket)
      throws CertificateException {
    delegate.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(
      final X509Certificate[] chain, final String authType, final Socket socket)
      throws CertificateException {
    delegate.checkServerTrusted(chain, authType);
  }

  @Override
  public void checkClientTrusted(
      final X509Certificate[] chain, final String authType, final SSLEngine engine)
      throws CertificateException {
    delegate.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(
      final X509Certificate[] chain, final String authType, final SSLEngine engine)
      throws CertificateException {
    delegate.checkServerTrusted(chain, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return delegate.getAcceptedIssuers();
  }
}
