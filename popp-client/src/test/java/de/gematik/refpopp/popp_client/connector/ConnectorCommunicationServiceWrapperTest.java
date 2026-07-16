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

package de.gematik.refpopp.popp_client.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import de.gematik.refpopp.popp_client.client.session.CommunicationSslSession;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorCommunicationServiceWrapperTest {

  @Mock ClientServerCommunicationService clientServerCommunicationService;

  @Mock RealConnectorCommunicationService realConnectorCommunicationService;

  @Mock MockConnectorCommunicationService mockConnectorCommunicationService;

  @Mock CommunicationSslSession communicationSslSession;

  @InjectMocks ConnectorCommunicationServiceWrapper wrapper;

  @BeforeEach
  void setup() {
    when(clientServerCommunicationService.getSslSession()).thenReturn(communicationSslSession);
  }

  // -------- getConnectedEgkCard --------

  @Test
  void whenMockIsSet_thenMockServiceIsUsed_forGetConnectedEgkCard() {
    when(communicationSslSession.isConnectorMock()).thenReturn(true);
    when(mockConnectorCommunicationService.getConnectedEgkCard()).thenReturn("mock-card");

    String result = wrapper.getConnectedEgkCard("kvnr");

    assertEquals("mock-card", result);
    verify(mockConnectorCommunicationService).getConnectedEgkCard();
    verifyNoInteractions(realConnectorCommunicationService);
  }

  @Test
  void whenMockIsSet_andKvnrIsNull_thenMockServiceIsUsed_forGetConnectedEgkCard() {
    when(communicationSslSession.isConnectorMock()).thenReturn(true);
    when(mockConnectorCommunicationService.getConnectedEgkCard()).thenReturn("mock-card");

    String result = wrapper.getConnectedEgkCard(null);

    assertEquals("mock-card", result);
    verify(mockConnectorCommunicationService).getConnectedEgkCard();
    verifyNoInteractions(realConnectorCommunicationService);
  }

  @Test
  void whenMockIsNotSet_thenRealServiceIsUsed_forGetConnectedEgkCard() {
    when(communicationSslSession.isConnectorMock()).thenReturn(false);
    when(realConnectorCommunicationService.getConnectedEgkCard("kvnr")).thenReturn("real-card");

    String result = wrapper.getConnectedEgkCard("kvnr");

    assertEquals("real-card", result);
    verify(realConnectorCommunicationService).getConnectedEgkCard("kvnr");
    verifyNoInteractions(mockConnectorCommunicationService);
  }

  @Test
  void whenMockIsNotSet_andKvnrIsNull_thenRealServiceReceivesNullKvnr_forGetConnectedEgkCard() {
    when(communicationSslSession.isConnectorMock()).thenReturn(false);
    when(realConnectorCommunicationService.getConnectedEgkCard(null)).thenReturn("real-card");

    String result = wrapper.getConnectedEgkCard(null);

    assertEquals("real-card", result);
    verify(realConnectorCommunicationService).getConnectedEgkCard(null);
    verifyNoInteractions(mockConnectorCommunicationService);
  }

  @Test
  void whenSslSessionIsMissing_thenRealServiceIsUsed_forGetConnectedEgkCard() {
    when(clientServerCommunicationService.getSslSession())
        .thenThrow(new RuntimeException("No SSL session"));
    when(realConnectorCommunicationService.getConnectedEgkCard("kvnr")).thenReturn("real-card");

    String result = wrapper.getConnectedEgkCard("kvnr");

    assertEquals("real-card", result);
    verify(realConnectorCommunicationService).getConnectedEgkCard("kvnr");
  }

  @Test
  void whenSslAttributeIsNull_thenRealServiceIsUsed_forGetConnectedEgkCard() {
    when(communicationSslSession.isConnectorMock()).thenReturn(false);
    when(realConnectorCommunicationService.getConnectedEgkCard("kvnr")).thenReturn("real-card");

    String result = wrapper.getConnectedEgkCard("kvnr");

    assertEquals("real-card", result);
    verify(realConnectorCommunicationService).getConnectedEgkCard("kvnr");
  }

  @Test
  void whenSslGetValueThrowsException_thenRealServiceIsUsed_forGetConnectedEgkCard() {
    when(communicationSslSession.isConnectorMock())
        .thenThrow(new IllegalStateException("broken session"));
    when(realConnectorCommunicationService.getConnectedEgkCard("kvnr")).thenReturn("real-card");

    String result = wrapper.getConnectedEgkCard("kvnr");

    assertEquals("real-card", result);
    verify(realConnectorCommunicationService).getConnectedEgkCard("kvnr");
  }

  // -------- startCardSession --------

  @Test
  void whenMockIsSet_thenMockServiceIsUsed_forStartStandardCardReaderCardSession() {
    when(communicationSslSession.isConnectorMock()).thenReturn(true);
    when(mockConnectorCommunicationService.startCardSession("card")).thenReturn("mock-session");

    String result = wrapper.startCardSession("card");

    assertEquals("mock-session", result);
    verify(mockConnectorCommunicationService).startCardSession("card");
    verifyNoInteractions(realConnectorCommunicationService);
  }

  @Test
  void whenMockIsNotSet_thenRealServiceIsUsed_forStartStandardCardReaderCardSession() {
    when(communicationSslSession.isConnectorMock()).thenReturn(false);
    when(realConnectorCommunicationService.startCardSession("card")).thenReturn("real-session");

    String result = wrapper.startCardSession("card");

    assertEquals("real-session", result);
    verify(realConnectorCommunicationService).startCardSession("card");
  }

  // -------- stopCardSession --------

  @Test
  void whenMockIsSet_thenMockServiceIsUsed_forStopCardSession() {
    when(communicationSslSession.isConnectorMock()).thenReturn(true);

    Status status = new Status();
    when(mockConnectorCommunicationService.stopCardSession("session")).thenReturn(status);

    Status result = wrapper.stopCardSession("session");

    assertEquals(status, result);
    verify(mockConnectorCommunicationService).stopCardSession("session");
  }

  @Test
  void whenMockIsNotSet_thenRealServiceIsUsed_forStopCardSession() {
    when(communicationSslSession.isConnectorMock()).thenReturn(false);

    Status status = new Status();
    when(realConnectorCommunicationService.stopCardSession("session")).thenReturn(status);

    Status result = wrapper.stopCardSession("session");

    assertEquals(status, result);
    verify(realConnectorCommunicationService).stopCardSession("session");
  }

  // -------- secureSendApdu --------

  @Test
  void whenMockIsSet_thenMockServiceIsUsed_forSecureSendApdu() {
    when(communicationSslSession.isConnectorMock()).thenReturn(true);

    List<String> response = List.of("9000");
    when(mockConnectorCommunicationService.secureSendApdu("signed")).thenReturn(response);

    List<String> result = wrapper.secureSendApdu("signed");

    assertEquals(response, result);
    verify(mockConnectorCommunicationService).secureSendApdu("signed");
  }

  @Test
  void whenMockIsNotSet_thenRealServiceIsUsed_forSecureSendApdu() {
    when(communicationSslSession.isConnectorMock()).thenReturn(false);

    List<String> response = List.of("9000");
    when(realConnectorCommunicationService.secureSendApdu("signed")).thenReturn(response);

    List<String> result = wrapper.secureSendApdu("signed");

    assertEquals(response, result);
    verify(realConnectorCommunicationService).secureSendApdu("signed");
  }
}
