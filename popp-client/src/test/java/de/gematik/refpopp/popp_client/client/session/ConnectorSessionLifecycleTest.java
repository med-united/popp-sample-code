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

package de.gematik.refpopp.popp_client.client.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.refpopp.popp_client.connector.ConnectorCommunicationServiceWrapper;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.soap.client.SoapFaultClientException;

@ExtendWith(MockitoExtension.class)
class ConnectorSessionLifecycleTest {

  @Mock private ConnectorCommunicationServiceWrapper connectorCommunicationServiceWrapper;

  private ConnectorSessionLifecycle sut;

  private CommunicationSslSession wrapSslSession(final Map<String, Object> sslSession) {
    return new CommunicationSslSession(sslSession);
  }

  @BeforeEach
  void setUp() {
    sut = new ConnectorSessionLifecycle(connectorCommunicationServiceWrapper);
  }

  @Test
  void startSessionUsesCardHandleFromConnectedEgkCard() {
    when(connectorCommunicationServiceWrapper.getConnectedEgkCard("kvnr"))
        .thenReturn("card-handle");
    when(connectorCommunicationServiceWrapper.startCardSession("card-handle"))
        .thenReturn("session-id");

    final var result = sut.startSession("kvnr");

    assertThat(result).isEqualTo("session-id");
    verify(connectorCommunicationServiceWrapper).getConnectedEgkCard("kvnr");
    verify(connectorCommunicationServiceWrapper).startCardSession("card-handle");
  }

  @Test
  void stopSessionIfRequiredDoesNothingForNonConnectorSession() {
    final Map<String, Object> sslSession = new HashMap<>();
    sslSession.put("cardConnectionType", CardConnectionType.CONTACT_STANDARD);
    sslSession.put("clientSessionId", "session-id");

    assertThatCode(() -> sut.stopSessionIfRequired(wrapSslSession(sslSession)))
        .doesNotThrowAnyException();

    verifyNoInteractions(connectorCommunicationServiceWrapper);
  }

  @Test
  void stopSessionIfRequiredStopsConnectorSession() {
    final Map<String, Object> sslSession = new HashMap<>();
    sslSession.put("cardConnectionType", CardConnectionType.CONTACT_CONNECTOR);
    sslSession.put("clientSessionId", "session-id");

    sut.stopSessionIfRequired(wrapSslSession(sslSession));

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void stopSessionIfRequiredStopsContactlessConnectorSession() {
    final Map<String, Object> sslSession = new HashMap<>();
    sslSession.put("cardConnectionType", CardConnectionType.CONTACTLESS_CONNECTOR);
    sslSession.put("clientSessionId", "session-id");

    sut.stopSessionIfRequired(wrapSslSession(sslSession));

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void stopSessionIfRequiredIgnoresCancellationException() {
    final Map<String, Object> sslSession = new HashMap<>();
    sslSession.put("cardConnectionType", CardConnectionType.CONTACT_CONNECTOR);
    sslSession.put("clientSessionId", "session-id");
    doThrow(new CancellationException("cancelled"))
        .when(connectorCommunicationServiceWrapper)
        .stopCardSession("session-id");

    assertThatCode(() -> sut.stopSessionIfRequired(wrapSslSession(sslSession)))
        .doesNotThrowAnyException();

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void stopSessionIfRequiredIgnoresUnknownSessionSoapFault() {
    final Map<String, Object> sslSession = new HashMap<>();
    sslSession.put("cardConnectionType", CardConnectionType.CONTACT_CONNECTOR);
    sslSession.put("clientSessionId", "session-id");
    final SoapFaultClientException exception = mock(SoapFaultClientException.class);
    when(exception.getFaultStringOrReason()).thenReturn("Unbekannte Session ID");
    doThrow(exception).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");

    assertThatCode(() -> sut.stopSessionIfRequired(wrapSslSession(sslSession)))
        .doesNotThrowAnyException();

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }

  @Test
  void stopSessionIfRequiredRethrowsUnexpectedSoapFault() {
    final Map<String, Object> sslSession = new HashMap<>();
    sslSession.put("cardConnectionType", CardConnectionType.CONTACT_CONNECTOR);
    sslSession.put("clientSessionId", "session-id");
    final SoapFaultClientException exception = mock(SoapFaultClientException.class);
    when(exception.getFaultStringOrReason()).thenReturn("Some other fault");
    doThrow(exception).when(connectorCommunicationServiceWrapper).stopCardSession("session-id");

    assertThatThrownBy(() -> sut.stopSessionIfRequired(wrapSslSession(sslSession)))
        .isSameAs(exception);

    verify(connectorCommunicationServiceWrapper).stopCardSession("session-id");
  }
}
