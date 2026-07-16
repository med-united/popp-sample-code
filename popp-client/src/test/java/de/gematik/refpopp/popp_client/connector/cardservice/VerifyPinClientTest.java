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

package de.gematik.refpopp.popp_client.connector.cardservice;

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
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import jakarta.xml.bind.JAXBElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

class VerifyPinClientTest {

  private VerifyPinClient sut;
  private ServiceEndpointProvider serviceEndpointProviderMock;
  private Context contextMock;
  private Jaxb2Marshaller cardServiceMarshaller;

  @BeforeEach
  void setUp() {
    serviceEndpointProviderMock = mock(ServiceEndpointProvider.class);
    ServiceEndpoint endpointMock = mock(ServiceEndpoint.class);
    when(serviceEndpointProviderMock.getCardServiceEndpoint()).thenReturn(endpointMock);
    when(endpointMock.getVersion()).thenReturn("8.2.1");

    cardServiceMarshaller = mock(Jaxb2Marshaller.class);
    contextMock = mock(Context.class);
    sut =
        new VerifyPinClient(cardServiceMarshaller, contextMock, serviceEndpointProviderMock, null);
  }

  @Test
  void performVerifyPinReturnsStatus() {
    // given
    final var soapResponseMock = mock(PinResponseType.class, RETURNS_DEEP_STUBS);
    when(serviceEndpointProviderMock.getCardServiceFullEndpoint()).thenReturn("service.endpoint");
    when(soapResponseMock.getStatus().getResult()).thenReturn("OK");
    JAXBElement<PinResponseType> element = mock(JAXBElement.class);
    when(element.getValue()).thenReturn(soapResponseMock);
    VerifyPinClient spySut = spy(sut);
    doReturn(element).when(spySut).sendRequest(any(), anyString(), eq(JAXBElement.class));

    // when
    PinResponseType actual = spySut.performVerifyPin("cardHandle");

    // then
    assertThat(actual.getStatus().getResult()).isEqualTo("OK");
  }
}
