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

import de.gematik.poppcommons.api.enums.CardConnectionType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommunicationSslSessionTest {

  @Test
  void returnsDefaultValuesWhenAttributesAreMissing() {
    final var sut = new CommunicationSslSession(new HashMap<>());

    assertThat(sut.getCardConnectionType()).isNull();
    assertThat(sut.getClientSessionId()).isNull();
    assertThat(sut.isConnectorMock()).isFalse();
    assertThat(sut.isVirtualCard()).isFalse();
  }

  @Test
  void storesAndReturnsSessionAttributes() {
    final Map<String, Object> attributes = new HashMap<>();
    final var sut = new CommunicationSslSession(attributes);

    sut.setCardConnectionType(CardConnectionType.CONTACT_CONNECTOR);
    sut.setClientSessionId("session-id");
    sut.setConnectorMock(true);
    sut.setVirtualCard(true);

    assertThat(sut.getCardConnectionType()).isEqualTo(CardConnectionType.CONTACT_CONNECTOR);
    assertThat(sut.getClientSessionId()).isEqualTo("session-id");
    assertThat(sut.isConnectorMock()).isTrue();
    assertThat(sut.isVirtualCard()).isTrue();
    assertThat(attributes)
        .containsEntry("cardConnectionType", CardConnectionType.CONTACT_CONNECTOR)
        .containsEntry("clientSessionId", "session-id")
        .containsEntry("virtualCard", true)
        .containsEntry("connectorMock", true);
  }

  @Test
  void booleanFlagsRemainFalseUnlessExplicitlySetToTrue() {
    final var attributes = new HashMap<String, Object>();
    attributes.put("connectorMock", false);
    attributes.put("virtualCard", Boolean.FALSE);

    final var sut = new CommunicationSslSession(attributes);

    assertThat(sut.isConnectorMock()).isFalse();
    assertThat(sut.isVirtualCard()).isFalse();
  }
}
