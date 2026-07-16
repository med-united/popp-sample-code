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

import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardService;
import de.gematik.refpopp.popp_client.cardreader.card.VirtualCardSessionState;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommunicationSessionRegistryTest {

  private CommunicationSessionRegistry sut;

  @BeforeEach
  void setUp() {
    sut = new CommunicationSessionRegistry();
  }

  @Test
  void registerTokenWaiterStoresPendingTokenFuture() {
    final CompletableFuture<String> future = sut.registerTokenWaiter("session-id");

    assertThat(sut.hasPendingToken("session-id")).isTrue();
    assertThat(future).isNotDone();
  }

  @Test
  void registerVirtualCardStoresServiceAndCreatesSessionState() {
    final var virtualCardService = Mockito.mock(VirtualCardService.class);

    sut.registerVirtualCard("session-id", virtualCardService);

    assertThat(
            sut.getVirtualCardServiceOrDefault(
                "session-id", Mockito.mock(VirtualCardService.class)))
        .isSameAs(virtualCardService);
    assertThat(sut.getOrCreateVirtualCardSessionState("session-id"))
        .isSameAs(sut.getOrCreateVirtualCardSessionState("session-id"));
  }

  @Test
  void getVirtualCardServiceOrDefaultReturnsFallbackWhenNoServiceWasRegistered() {
    final var defaultService = Mockito.mock(VirtualCardService.class);

    assertThat(sut.getVirtualCardServiceOrDefault("session-id", defaultService))
        .isSameAs(defaultService);
  }

  @Test
  void completeTokenCompletesFutureAndCleansUpVirtualCardData() {
    final var tokenFuture = sut.registerTokenWaiter("session-id");
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard("session-id", virtualCardService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState("session-id");
    final var defaultService = Mockito.mock(VirtualCardService.class);

    final var completed = sut.completeToken("session-id", "token-value");

    assertThat(completed).isTrue();
    assertThat(tokenFuture).isCompletedWithValue("token-value");
    assertThat(sut.hasPendingToken("session-id")).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault("session-id", defaultService))
        .isSameAs(defaultService);
    assertThat(sut.getOrCreateVirtualCardSessionState("session-id")).isNotSameAs(sessionState);
  }

  @Test
  void failTokenCompletesExceptionallyAndCleansUpVirtualCardData() {
    final var tokenFuture = sut.registerTokenWaiter("session-id");
    final var virtualCardService = Mockito.mock(VirtualCardService.class);
    sut.registerVirtualCard("session-id", virtualCardService);
    final var sessionState = sut.getOrCreateVirtualCardSessionState("session-id");
    final var defaultService = Mockito.mock(VirtualCardService.class);
    final var failure = new IllegalStateException("broken");

    final var completed = sut.failToken("session-id", failure);

    assertThat(completed).isTrue();
    assertThat(tokenFuture).isCompletedExceptionally();
    assertThat(sut.hasPendingToken("session-id")).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault("session-id", defaultService))
        .isSameAs(defaultService);
    assertThat(sut.getOrCreateVirtualCardSessionState("session-id")).isNotSameAs(sessionState);
  }

  @Test
  void completeTokenReturnsFalseWhenNoTokenIsRegistered() {
    final var defaultService = Mockito.mock(VirtualCardService.class);
    final var sessionState = new VirtualCardSessionState();
    sut.registerVirtualCard("session-id", defaultService);
    assertThat(sut.getOrCreateVirtualCardSessionState("session-id")).isNotSameAs(sessionState);

    final var completed = sut.completeToken("missing-session", "token");

    assertThat(completed).isFalse();
    assertThat(sut.getVirtualCardServiceOrDefault("session-id", defaultService))
        .isSameAs(defaultService);
  }
}
