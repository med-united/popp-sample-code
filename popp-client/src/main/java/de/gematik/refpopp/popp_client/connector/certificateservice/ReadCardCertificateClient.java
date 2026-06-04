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

package de.gematik.refpopp.popp_client.connector.certificateservice;

import static de.gematik.refpopp.popp_client.configuration.helper.SoapActionVersionHelper.buildSoapAction;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapActions;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/** Sends a <i>ReadCardCertificate</i> request to the connector. */
@Component
@Lazy
@Slf4j
public class ReadCardCertificateClient extends SoapClient {

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;

  public ReadCardCertificateClient(
      final Jaxb2Marshaller certificateServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    super(
        certificateServiceMarshaller,
        () -> buildSoapAction(serviceEndpointProvider, SoapActions.READ_CARD_CERTIFICATE),
        httpClient);
    this.context = context;
    this.serviceEndpointProvider = serviceEndpointProvider;
  }

  public byte[] performReadCardCertificate(final String cardHandle) {
    final ReadCardCertificate soapRequest = createReadCardCertificate(cardHandle);
    final String endpoint = serviceEndpointProvider.getCertificateServiceFullEndpoint();
    log.info("Sending ReadCardCertificate request to connector at {}", endpoint);
    final ReadCardCertificateResponse soapResponse =
        sendRequest(soapRequest, endpoint, ReadCardCertificateResponse.class);

    final var x509DataInfo = soapResponse.getX509DataInfoList().getX509DataInfo();
    if (x509DataInfo.isEmpty()) {
      throw new IllegalStateException("| ReadCardCertificate returned no certificate");
    }
    final byte[] cert = x509DataInfo.getFirst().getX509Data().getX509Certificate();
    if (cert == null || cert.length == 0) {
      throw new IllegalStateException("| ReadCardCertificate returned an empty certificate");
    }
    return cert;
  }

  private ReadCardCertificate createReadCardCertificate(final String cardHandle) {
    final ContextType contextType = getContextType();
    final ReadCardCertificate request = new ReadCardCertificate();
    request.setCardHandle(cardHandle);
    request.setContext(contextType);
    final ReadCardCertificate.CertRefList certRefList = new ReadCardCertificate.CertRefList();
    certRefList.getCertRef().add(CertRefEnum.C_AUT);
    request.setCertRefList(certRefList);

    return request;
  }

  private ContextType getContextType() {
    final ContextType contextType = new ContextType();
    contextType.setClientSystemId(context.getClientSystemId());
    contextType.setMandantId(context.getMandantId());
    contextType.setWorkplaceId(context.getWorkplaceId());

    return contextType;
  }
}
