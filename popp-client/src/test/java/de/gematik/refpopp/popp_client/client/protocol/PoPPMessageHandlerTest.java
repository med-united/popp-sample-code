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

package de.gematik.refpopp.popp_client.client.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.poppcommons.api.enums.CardConnectionType;
import de.gematik.poppcommons.api.messages.ConnectorScenarioMessage;
import de.gematik.poppcommons.api.messages.ErrorMessage;
import de.gematik.poppcommons.api.messages.PoPPMessage;
import de.gematik.poppcommons.api.messages.ScenarioResponseMessage;
import de.gematik.poppcommons.api.messages.ScenarioStep;
import de.gematik.poppcommons.api.messages.StandardScenarioMessage;
import de.gematik.poppcommons.api.messages.TokenMessage;
import de.gematik.refpopp.popp_client.client.session.CommunicationSessionRegistry;
import de.gematik.refpopp.popp_client.client.session.CommunicationSslSession;
import de.gematik.refpopp.popp_client.client.transport.ClientServerCommunicationService;
import de.gematik.refpopp.popp_client.connector.session.ConnectorSessionLifecycle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PoPPMessageHandlerTest {

  @Mock private ClientServerCommunicationService clientServerCommunicationService;
  @Mock private CommunicationSessionRegistry sessionRegistry;
  @Mock private StandardScenarioProcessor standardScenarioProcessor;
  @Mock private ConnectorScenarioProcessor connectorScenarioProcessor;
  @Mock private ConnectorSessionLifecycle connectorSessionLifecycle;

  private PoPPMessageHandler sut;

  private CommunicationSslSession wrapSslSession(final Map<String, Object> sslSession) {
    return new CommunicationSslSession(sslSession);
  }

  @BeforeEach
  void setUp() {
    sut =
        new PoPPMessageHandler(
            clientServerCommunicationService,
            sessionRegistry,
            standardScenarioProcessor,
            connectorScenarioProcessor,
            connectorSessionLifecycle);
  }

  @Test
  void handleTokenMessageCompletesTokenAndStopsConnectorSession() {
    final var sslSession = new HashMap<String, Object>();
    sslSession.put("clientSessionId", "session-id");
    sslSession.put("cardConnectionType", CardConnectionType.CONTACT_CONNECTOR);
    final var wrappedSession = wrapSslSession(sslSession);
    when(clientServerCommunicationService.getSslSession()).thenReturn(wrappedSession);
    when(sessionRegistry.completeToken("session-id", "token")).thenReturn(true);

    sut.handle(new TokenMessage("token", "pn"));

    verify(sessionRegistry).completeToken("session-id", "token");
    verify(connectorSessionLifecycle).stopSessionIfRequired(wrappedSession);
    verifyNoInteractions(standardScenarioProcessor, connectorScenarioProcessor);
  }

  @Test
  void handleStandardScenarioMessageDelegatesToProcessorAndSendsResponse() {
    final var sslSession = new HashMap<String, Object>();
    final var message =
        StandardScenarioMessage.builder()
            .version("1.0.0")
            .clientSessionId("session-id")
            .sequenceCounter(0)
            .timeSpan(0)
            .steps(List.of(new ScenarioStep("00A4040000", List.of("9000"))))
            .build();
    final var wrappedSession = wrapSslSession(sslSession);
    when(clientServerCommunicationService.getSslSession()).thenReturn(wrappedSession);
    when(standardScenarioProcessor.process(message, wrappedSession)).thenReturn(List.of("9000"));

    sut.handle(message);

    verify(standardScenarioProcessor).process(message, wrappedSession);
    final var responseCaptor = ArgumentCaptor.forClass(ScenarioResponseMessage.class);
    verify(clientServerCommunicationService).sendMessage(responseCaptor.capture());
    assertThat(responseCaptor.getValue().getSteps()).containsExactly("9000");
    verifyNoInteractions(connectorScenarioProcessor, sessionRegistry, connectorSessionLifecycle);
  }

  @Test
  void handleConnectorScenarioMessageDelegatesToProcessorAndSendsResponse() {
    final var sslSession = new HashMap<String, Object>();
    final var message = new ConnectorScenarioMessage("1.0.0", "signed-scenario");
    final var wrappedSession = wrapSslSession(sslSession);
    when(clientServerCommunicationService.getSslSession()).thenReturn(wrappedSession);
    when(connectorScenarioProcessor.process(message, wrappedSession)).thenReturn(List.of("9000"));

    sut.handle(message);

    verify(connectorScenarioProcessor).process(message, wrappedSession);
    final var responseCaptor = ArgumentCaptor.forClass(ScenarioResponseMessage.class);
    verify(clientServerCommunicationService).sendMessage(responseCaptor.capture());
    assertThat(responseCaptor.getValue().getSteps()).containsExactly("9000");
    verifyNoInteractions(standardScenarioProcessor, sessionRegistry, connectorSessionLifecycle);
  }

  @Test
  void handleErrorMessageFailsTokenWhenClientSessionIdExists() {
    final var sslSession = new HashMap<String, Object>();
    sslSession.put("clientSessionId", "session-id");
    when(clientServerCommunicationService.getSslSession()).thenReturn(wrapSslSession(sslSession));

    sut.handle(ErrorMessage.builder().errorCode("errorCode").errorDetail("errorDetail").build());

    final var exceptionCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
    verify(sessionRegistry).failToken(eq("session-id"), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).hasMessage("Server error errorCode: errorDetail");
    verifyNoInteractions(
        standardScenarioProcessor, connectorScenarioProcessor, connectorSessionLifecycle);
  }

  @Test
  void handleErrorMessageDoesNothingWithoutClientSessionId() {
    final var sslSession = new HashMap<String, Object>();
    when(clientServerCommunicationService.getSslSession()).thenReturn(wrapSslSession(sslSession));

    sut.handle(ErrorMessage.builder().errorCode("errorCode").errorDetail("errorDetail").build());

    verifyNoInteractions(
        sessionRegistry,
        standardScenarioProcessor,
        connectorScenarioProcessor,
        connectorSessionLifecycle);
  }

  @Test
  void handleUnknownMessageDoesNothing() {
    final PoPPMessage unknownMessage = mock(PoPPMessage.class);
    doReturn(null).when(unknownMessage).getType();

    sut.handle(unknownMessage);

    verifyNoInteractions(
        clientServerCommunicationService,
        sessionRegistry,
        standardScenarioProcessor,
        connectorScenarioProcessor,
        connectorSessionLifecycle);
  }
}
