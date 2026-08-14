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

package de.gematik.refpopp.popp_client.connector.authsignatureservice;

import static de.gematik.refpopp.popp_client.configuration.helper.SoapActionVersionHelper.buildSoapAction;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.SoapActions;
import de.gematik.refpopp.popp_client.connector.soap.SoapClient;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import lombok.extern.slf4j.Slf4j;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

/** Sends an <i>ExternalAuthenticate</i> request to the connector. */
@Component
@Lazy
@Slf4j
public class ExternalAuthenticateClient extends SoapClient {

  private static final String OCTET_STREAM_MIME_TYPE = "application/octet-stream";

  private final Context context;
  private final ServiceEndpointProvider serviceEndpointProvider;

  public ExternalAuthenticateClient(
      final Jaxb2Marshaller authSignatureServiceMarshaller,
      final Context context,
      final ServiceEndpointProvider serviceEndpointProvider,
      @Autowired(required = false) @Qualifier("httpClientWithBC") HttpClient httpClient) {
    super(
        authSignatureServiceMarshaller,
        () -> buildSoapAction(serviceEndpointProvider, SoapActions.EXTERNAL_AUTHENTICATE),
        httpClient);
    this.context = context;
    this.serviceEndpointProvider = serviceEndpointProvider;
  }

  public byte[] performExternalAuthenticate(final String cardHandle, final byte[] digestBytes) {
    final ExternalAuthenticate soapRequest = createExternalAuthenticate(cardHandle, digestBytes);
    final String endpoint = serviceEndpointProvider.getAuthSignatureServiceFullEndpoint();
    log.info("Sending ExternalAuthenticate request to connector at {}", endpoint);
    final ExternalAuthenticateResponse soapResponse =
        sendRequest(soapRequest, endpoint, ExternalAuthenticateResponse.class);

    final byte[] signature = soapResponse.getSignatureObject().getBase64Signature().getValue();
    if (signature == null || signature.length == 0) {
      throw new IllegalStateException("| ExternalAuthenticate returned an empty signature");
    }
    return signature;
  }

  private ExternalAuthenticate createExternalAuthenticate(
      final String cardHandle, final byte[] digestBytes) {
    final ContextType contextType = getContextType();
    final ExternalAuthenticate request = new ExternalAuthenticate();
    request.setCardHandle(cardHandle);
    request.setContext(contextType);

    final Base64Data base64Data = new Base64Data();
    base64Data.setMimeType(OCTET_STREAM_MIME_TYPE);
    base64Data.setValue(digestBytes);
    final BinaryDocumentType binaryString = new BinaryDocumentType();
    binaryString.setBase64Data(base64Data);
    request.setBinaryString(binaryString);

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
