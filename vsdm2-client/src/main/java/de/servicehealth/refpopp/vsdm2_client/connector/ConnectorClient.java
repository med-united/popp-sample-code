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

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.servicehealth.refpopp.vsdm2_client.properties.ConnectorProperties;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Single SOAP gateway to the Konnektor. At creation it delegates to a {@link ServicePortProvider},
 * which downloads the service directory ({@code connector.sds}), resolves endpoints, and builds the
 * TLS-configured JAX-WS ports once. Subsequent calls reuse those ports (CXF client proxies are
 * thread-safe).
 */
@Slf4j
@Component
public class ConnectorClient {

  private static final String OCTET_STREAM_MIME_TYPE = "application/octet-stream";

  private final ContextType context;
  private final CertificateServicePortType certificateService;
  private final AuthSignatureServicePortType authSignatureService;

  public ConnectorClient(
      final ConnectorProperties properties, @Qualifier("konnektor") final SSLContext sslContext) {
    final ConnectorProperties.TerminalConfiguration.Context ctx =
        properties.terminalConfiguration().context();
    this.context =
        buildContext(
            ctx.clientSystemId(),
            ctx.mandantId(),
            ctx.workplaceId(),
            Optional.ofNullable(ctx.userId()));

    final ServicePortProvider portProvider =
        new ServicePortProvider(properties.endPointUrl(), sslContext);

    this.certificateService = portProvider.certificateServicePort();
    this.authSignatureService = portProvider.authSignatureServicePort();
  }

  public byte[] readCardCertificate(final String cardHandle) {
    log.info("Sending ReadCardCertificate request to connector");

    final ReadCardCertificate.CertRefList certRefList = new ReadCardCertificate.CertRefList();
    certRefList.getCertRef().add(CertRefEnum.C_AUT);

    final ReadCardCertificate request = new ReadCardCertificate();
    request.setCardHandle(cardHandle);
    request.setContext(context);
    request.setCertRefList(certRefList);

    final ReadCardCertificateResponse response;
    try {
      response = certificateService.readCardCertificate(request);
    } catch (final FaultMessage e) {
      throw new IllegalStateException("ReadCardCertificate fault: " + e.getMessage(), e);
    }

    final var infos = response.getX509DataInfoList().getX509DataInfo();
    if (infos.isEmpty()) {
      throw new IllegalStateException("ReadCardCertificate returned no certificate");
    }
    final byte[] cert = infos.getFirst().getX509Data().getX509Certificate();
    if (cert == null || cert.length == 0) {
      throw new IllegalStateException("ReadCardCertificate returned an empty certificate");
    }
    return cert;
  }

  public byte[] externalAuthenticate(final String cardHandle, final byte[] digestBytes) {
    log.info("Sending ExternalAuthenticate request to connector");

    final Base64Data base64Data = new Base64Data();
    base64Data.setMimeType(OCTET_STREAM_MIME_TYPE);
    base64Data.setValue(digestBytes);
    final BinaryDocumentType binaryString = new BinaryDocumentType();
    binaryString.setBase64Data(base64Data);

    final ExternalAuthenticate request = new ExternalAuthenticate();
    request.setCardHandle(cardHandle);
    request.setContext(context);
    request.setOptionalInputs(new ExternalAuthenticate.OptionalInputs());
    request.setBinaryString(binaryString);

    final ExternalAuthenticateResponse response;
    try {
      response = authSignatureService.externalAuthenticate(request);
    } catch (final de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage e) {
      throw new IllegalStateException("ExternalAuthenticate fault: " + e.getMessage(), e);
    }

    final byte[] signature = response.getSignatureObject().getBase64Signature().getValue();
    if (signature == null || signature.length == 0) {
      throw new IllegalStateException("ExternalAuthenticate returned an empty signature");
    }
    return signature;
  }

  private static ContextType buildContext(
      final String clientSystemId,
      final String mandantId,
      final String workplaceId,
      final Optional<String> userId) {
    final ContextType context = new ContextType();
    context.setClientSystemId(clientSystemId);
    context.setMandantId(mandantId);
    context.setWorkplaceId(workplaceId);
    userId.filter(u -> !u.isEmpty()).ifPresent(context::setUserId);
    return context;
  }
}
