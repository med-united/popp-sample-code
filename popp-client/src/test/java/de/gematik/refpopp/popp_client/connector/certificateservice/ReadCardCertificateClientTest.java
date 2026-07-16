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

import static org.assertj.core.api.Assertions.assertThat;
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
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class ReadCardCertificateClientTest {

  private ReadCardCertificateClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;
  private Context contextMock;
  private Jaxb2Marshaller certificateServiceMarshaller;

  @BeforeEach
  void setUp() {
    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    ServiceEndpoint endpointMock = mock(ServiceEndpoint.class);
    when(serviceEndpointProviderMock.getCardServiceEndpoint()).thenReturn(endpointMock);
    when(endpointMock.getVersion()).thenReturn("6.0.0");

    certificateServiceMarshaller = mock(Jaxb2Marshaller.class);
    contextMock = mock(Context.class);
    sut =
        new ReadCardCertificateClient(
            certificateServiceMarshaller, contextMock, serviceEndpointProviderMock, null);
  }

  @Test
  void performReadCardCertificateAndCheckItExists() {
    // given
    final var soapResponseMock = mock(ReadCardCertificateResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getCertificateServiceFullEndpoint())
        .thenReturn("service.endpoint");
    when(soapResponseMock
            .getX509DataInfoList()
            .getX509DataInfo()
            .getFirst()
            .getX509Data()
            .getX509Certificate())
        .thenReturn(new byte[] {0x00});
    final ReadCardCertificateClient spySut = spy(sut);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), anyString(), eq(ReadCardCertificateResponse.class));

    // when
    final var actualResponse = spySut.performReadCardCertificate("cardHandle");

    // then
    assertThat(actualResponse).isNotNull();
  }

  @Test
  void performReadCardCertificateSetsContextFromContextConfiguration() {
    // given
    when(contextMock.getClientSystemId()).thenReturn("clientId");
    when(contextMock.getMandantId()).thenReturn("mandantId");
    when(contextMock.getWorkplaceId()).thenReturn("workplaceId");
    final ReadCardCertificateClient sutWithContext =
        new ReadCardCertificateClient(
            certificateServiceMarshaller, contextMock, serviceEndpointProviderMock, null);
    final var soapResponseMock = mock(ReadCardCertificateResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getCertificateServiceFullEndpoint())
        .thenReturn("service.endpoint");
    when(soapResponseMock
            .getX509DataInfoList()
            .getX509DataInfo()
            .getFirst()
            .getX509Data()
            .getX509Certificate())
        .thenReturn(new byte[] {0x00});
    final ReadCardCertificateClient spySut = spy(sutWithContext);
    final ArgumentCaptor<ReadCardCertificate> requestCaptor =
        ArgumentCaptor.forClass(ReadCardCertificate.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(requestCaptor.capture(), anyString(), eq(ReadCardCertificateResponse.class));

    // when
    spySut.performReadCardCertificate("cardHandle");

    // then
    final var actualContext = requestCaptor.getValue().getContext();
    assertThat(actualContext.getClientSystemId()).isEqualTo("clientId");
    assertThat(actualContext.getMandantId()).isEqualTo("mandantId");
    assertThat(actualContext.getWorkplaceId()).isEqualTo("workplaceId");
  }

  @Test
  void performReadCardCertificateUsesEndpointFromServiceEndpointProvider() {
    // given
    final var soapResponseMock = mock(ReadCardCertificateResponse.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getCertificateServiceFullEndpoint())
        .thenReturn("https://konnektor.example/certificate");
    when(soapResponseMock
            .getX509DataInfoList()
            .getX509DataInfo()
            .getFirst()
            .getX509Data()
            .getX509Certificate())
        .thenReturn(new byte[] {0x00});
    final ReadCardCertificateClient spySut = spy(sut);
    final ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(soapResponseMock)
        .when(spySut)
        .sendRequest(any(), endpointCaptor.capture(), eq(ReadCardCertificateResponse.class));

    // when
    spySut.performReadCardCertificate("cardHandle");

    // then
    assertThat(endpointCaptor.getValue()).isEqualTo("https://konnektor.example/certificate");
  }
}
