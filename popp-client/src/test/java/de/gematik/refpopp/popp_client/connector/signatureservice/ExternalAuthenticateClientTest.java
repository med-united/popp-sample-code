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

package de.gematik.refpopp.popp_client.connector.signatureservice;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_client.connector.Context;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpoint;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class ExternalAuthenticateClientTest {

  private ExternalAuthenticateClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;
  private Context contextMock;
  private Jaxb2Marshaller signatureServiceMarshaller;

  @BeforeEach
  void setUp() {
    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    ServiceEndpoint endpointMock = mock(ServiceEndpoint.class);
    when(serviceEndpointProviderMock.getCardServiceEndpoint()).thenReturn(endpointMock);
    when(endpointMock.getVersion()).thenReturn("7.4.0");

    signatureServiceMarshaller = mock(Jaxb2Marshaller.class);
    contextMock = mock(Context.class);
    sut =
        new ExternalAuthenticateClient(
            signatureServiceMarshaller, contextMock, serviceEndpointProviderMock, null);
  }

  @Test
  void performExternalAuthenticateAndCheckItExists() {
    // given
    final var soapResponseMock = mock(ExternalAuthenticateResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getAuthSignatureServiceFullEndpoint())
        .thenReturn("service.endpoint");
    when(soapResponseMock.getSignatureObject().getBase64Signature().getValue())
        .thenReturn(new byte[] {0x00});
    final ExternalAuthenticateClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), anyString(), eq(ExternalAuthenticateResponse.class));

    // when
    final var actualResponse = spySut.performExternalAuthenticate("base64Challenge", "cardHandle");

    // then
    assertThat(actualResponse).isNotNull();
  }

  @Test
  void performExternalAuthenticateSetsContextFromContextConfiguration() {
    // given
    when(contextMock.getClientSystemId()).thenReturn("clientId");
    when(contextMock.getMandantId()).thenReturn("mandantId");
    when(contextMock.getWorkplaceId()).thenReturn("workplaceId");
    final ExternalAuthenticateClient sutWithContext =
        new ExternalAuthenticateClient(
            signatureServiceMarshaller, contextMock, serviceEndpointProviderMock, null);
    final var soapResponseMock = mock(ExternalAuthenticateResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getAuthSignatureServiceFullEndpoint())
        .thenReturn("service.endpoint");
    when(soapResponseMock.getSignatureObject().getBase64Signature().getValue())
        .thenReturn(new byte[] {0x00});
    final ExternalAuthenticateClient spySut = spy(sutWithContext);
    final ArgumentCaptor<ExternalAuthenticate> requestCaptor =
        ArgumentCaptor.forClass(ExternalAuthenticate.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(ExternalAuthenticateResponse.class));

    // when
    spySut.performExternalAuthenticate("base64Challenge", "cardHandle");

    // then
    final var actualContext = requestCaptor.getValue().getContext();
    Assertions.assertThat(actualContext.getClientSystemId()).isEqualTo("clientId");
    Assertions.assertThat(actualContext.getMandantId()).isEqualTo("mandantId");
    Assertions.assertThat(actualContext.getWorkplaceId()).isEqualTo("workplaceId");
  }

  @Test
  void performExternalAuthenticateUsesEndpointFromServiceEndpointProvider() {
    // given
    final var soapResponseMock = mock(ExternalAuthenticateResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getAuthSignatureServiceFullEndpoint())
        .thenReturn("https://konnektor.example/signature");
    when(soapResponseMock.getSignatureObject().getBase64Signature().getValue())
        .thenReturn(new byte[] {0x00});
    final ExternalAuthenticateClient spySut = spy(sut);
    final ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), endpointCaptor.capture(), eq(ExternalAuthenticateResponse.class));

    // when
    spySut.performExternalAuthenticate("base64Challenge", "cardHandle");

    // then
    Assertions.assertThat(endpointCaptor.getValue())
        .isEqualTo("https://konnektor.example/signature");
  }
}
