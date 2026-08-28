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

package de.servicehealth.refpopp.vsdm2_client.configuration;

import de.servicehealth.refpopp.vsdm2_client.configuration.helper.NoHostNameValidationWrappedTrustManager;
import de.servicehealth.refpopp.vsdm2_client.configuration.helper.TrustAllTrustManager;
import de.servicehealth.refpopp.vsdm2_client.connector.ConnectorProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class KonnektorTlsConfiguration {

  private final ConnectorProperties.Secure secure;

  public KonnektorTlsConfiguration(final ConnectorProperties properties) {
    this.secure = properties.secure();
  }

  @PostConstruct
  void registerBouncyCastle() {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
    log.info("Bouncy Castle registered as primary provider.");

    if (ECNamedCurveTable.getParameterSpec("brainpoolP256r1") != null) {
      log.info("BrainpoolP256r1 curve is available.");
    } else {
      log.info("BrainpoolP256r1 NOT found!");
    }
  }

  @Bean("konnektor")
  public SSLContext konnektorSslContext()
      throws NoSuchAlgorithmException,
          KeyStoreException,
          IOException,
          CertificateException,
          KeyManagementException,
          NoSuchProviderException,
          UnrecoverableKeyException {
    if (!secure.enable() || secure.keystore() == null || secure.keystore().isBlank()) {
      log.info("Konnektor TLS disabled — returning default SSLContext.");
      return SSLContext.getDefault();
    }

    System.setProperty(
        "jdk.tls.namedGroups",
        """
        x25519, secp256r1, secp384r1, secp521r1, x448, \
        ffdhe2048, ffdhe3072, ffdhe4096, ffdhe6144, ffdhe8192, \
        brainpoolP256r1, brainpoolP384r1, brainpoolP512r1\
        """);

    final String ksPwd = secure.keystorePassword() == null ? "" : secure.keystorePassword();
    KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
    try (InputStream keyStoreStream = openKeyStoreStream(secure.keystore())) {
      keyStore.load(keyStoreStream, ksPwd.toCharArray());
    }

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
    kmf.init(keyStore, ksPwd.toCharArray());

    TrustManager[] trustManagers = new TrustManager[] {new TrustAllTrustManager()};
    if (!secure.trustAll()) {
      final String tsPwd = secure.truststorePassword() == null ? "" : secure.truststorePassword();
      if (secure.truststore() == null || secure.truststore().isBlank()) {
        throw new IllegalStateException(
            "connector.secure.truststore is required when trust-all=false");
      }
      KeyStore trustStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
      try (InputStream trustStoreStream = openKeyStoreStream(secure.truststore())) {
        trustStore.load(trustStoreStream, tsPwd.toCharArray());
      }

      TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
      tmf.init(trustStore);
      trustManagers = tmf.getTrustManagers();
      if (!secure.hostnameValidation()) {
        X509ExtendedTrustManager delegate =
            Arrays.stream(tmf.getTrustManagers())
                .filter(X509ExtendedTrustManager.class::isInstance)
                .map(X509ExtendedTrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No X509ExtendedTrustManager"));

        trustManagers = new TrustManager[] {new NoHostNameValidationWrappedTrustManager(delegate)};
      }
    }
    SSLContext sslContext =
        SSLContext.getInstance("TLSv1.2", BouncyCastleJsseProvider.PROVIDER_NAME);
    sslContext.init(kmf.getKeyManagers(), trustManagers, null);
    return sslContext;
  }

  private InputStream openKeyStoreStream(String location) throws IOException {
    String resolved = location == null ? "" : location.trim();
    if (resolved.startsWith("classpath:")) {
      String resourcePath = resolved.substring("classpath:".length());
      InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (stream == null) {
        throw new IOException("Classpath resource not found: " + resourcePath);
      }
      return stream;
    }

    Path path = Path.of(resolved);
    if (Files.exists(path)) {
      return Files.newInputStream(path);
    }

    InputStream stream = getClass().getClassLoader().getResourceAsStream(resolved);
    if (stream == null) {
      throw new IOException("Keystore not found at path or classpath: " + resolved);
    }
    return stream;
  }
}
