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

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureService;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateService;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.servicedirectory.v3.ConnectorServices;
import de.gematik.ws.conn.serviceinformation.v2.ServiceType;
import de.gematik.ws.conn.serviceinformation.v2.VersionType;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.ws.BindingProvider;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;

/**
 * Downloads and parses the connector service directory ({@code connector.sds}), then builds the
 * TLS-configured JAX-WS ports for the Konnektor services. Constructed once by {@link
 * ConnectorClient}; resolves each service's endpoint from the SDS and wires the CXF client with the
 * Konnektor TLS context.
 */
@Slf4j
public class ServicePortProvider {

  private static final String CERTIFICATE_SERVICE = "CertificateService";
  private static final String AUTH_SIGNATURE_SERVICE = "AuthSignatureService";
  private static final String VSD_SERVICE_BINDING = "VSDServiceBinding";

  private final String connectorUrl;
  private final SSLContext sslContext;
  private final ConnectorServices connectorServices;

  public ServicePortProvider(final String connectorUrl, final SSLContext sslContext) {
    this.connectorUrl = connectorUrl;
    this.sslContext = sslContext;
    this.connectorServices = parse(downloadConnectorSds(connectorUrl, sslContext));
  }

  private static String downloadConnectorSds(
      final String connectorUrl, final SSLContext sslContext) {
    final URI sdsUrl = URI.create(connectorUrl + "/connector.sds");
    log.info("Downloading connector.sds from '{}'", sdsUrl);

    final HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).build();
    final HttpResponse<String> response;
    try {
      response =
          httpClient.send(
              HttpRequest.newBuilder(sdsUrl).GET().build(), HttpResponse.BodyHandlers.ofString());
    } catch (final IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to load connector.sds from " + sdsUrl, e);
    }
    if (response.statusCode() != 200 || response.body() == null) {
      throw new IllegalStateException(
          "Failed to load connector.sds from "
              + sdsUrl
              + " (status "
              + response.statusCode()
              + ")");
    }
    return response.body();
  }

  private static ConnectorServices parse(final String connectorSds) {
    try {
      return (ConnectorServices)
          JAXBContext.newInstance(ConnectorServices.class)
              .createUnmarshaller()
              .unmarshal(new StringReader(connectorSds));
    } catch (final JAXBException e) {
      throw new IllegalStateException("Failed to parse connector.sds", e);
    }
  }

  public CertificateServicePortType certificateServicePort() {
    return configure(new CertificateService().getCertificateServicePort(), CERTIFICATE_SERVICE);
  }

  public AuthSignatureServicePortType authSignatureServicePort() {
    return configure(
        new AuthSignatureService().getAuthSignatureServicePort(), AUTH_SIGNATURE_SERVICE);
  }

  /** Point a JAX-WS port at its resolved endpoint and apply the Konnektor TLS config. */
  private <P> P configure(final P port, final String serviceName) {
    ((BindingProvider) port)
        .getRequestContext()
        .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, extractServiceEndpoint(serviceName));
    final HTTPConduit conduit = (HTTPConduit) ClientProxy.getClient(port).getConduit();
    final TLSClientParameters tls = new TLSClientParameters();
    tls.setSslContext(sslContext);
    tls.setDisableCNCheck(true);
    conduit.setTlsClientParameters(tls);
    return port;
  }

  /** Resolve the full endpoint URL for {@code serviceName} from the connector.sds. */
  private String extractServiceEndpoint(final String serviceName) {
    final VersionType version = latestVersion(serviceName);
    final var tlsEndpoint = version.getEndpointTLS();
    final String location =
        tlsEndpoint != null ? tlsEndpoint.getLocation() : version.getEndpoint().getLocation();
    return connectorUrl + relativePath(location);
  }

  private VersionType latestVersion(final String serviceName) {
    for (final ServiceType service : connectorServices.getServiceInformation().getService()) {
      if (service.getName().equalsIgnoreCase(serviceName)) {
        final List<VersionType> versions = service.getVersions().getVersion();
        versions.sort(ServicePortProvider::compareVersions);
        return versions.getLast();
      }
    }
    throw new IllegalStateException("Service '" + serviceName + "' not defined in connector.sds");
  }

  private static int compareVersions(final VersionType l, final VersionType r) {
    final String[] left = l.getVersion().split("\\.");
    final String[] right = r.getVersion().split("\\.");
    final int length = Math.min(left.length, right.length);
    for (int i = 0; i < length; i++) {
      if (!left[i].equals(right[i])) {
        return Integer.compare(Integer.parseInt(left[i]), Integer.parseInt(right[i]));
      }
    }
    return 0;
  }

  private String relativePath(final String serviceUrl) {
    final String konnektorPath = URI.create(connectorUrl).getPath();
    final String servicePath = URI.create(serviceUrl).getPath();
    if (konnektorPath.isBlank() || konnektorPath.length() < 2) {
      return servicePath;
    }
    return servicePath.replaceFirst("^" + konnektorPath, "");
  }
}
