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

package de.gematik.refpopp.popp_server.vzd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.refpopp.popp_server.vzd.dto.VzdLocation;
import de.gematik.refpopp.popp_server.vzd.dto.VzdSearchResult;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirAddress;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirBundle;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirEntry;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirIdentifier;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirLink;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirPosition;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirReference;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirResource;
import de.gematik.refpopp.popp_server.vzd.fhir.FhirTelecom;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class VzdSearchServiceTest {

  @Mock private RestClient restClientMock;
  @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpecMock;
  @Mock private RestClient.RequestHeadersSpec requestHeadersSpecMock;
  @Mock private RestClient.ResponseSpec responseSpecMock;
  @Mock private ObjectProvider<RestClient> restClientProviderMock;
  @Mock private VzdTokenService vzdTokenServiceMock;

  private AutoCloseable closeable;
  private VzdTokenProperties properties;
  private VzdSearchService service;

  @BeforeEach
  void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
    when(restClientProviderMock.getIfAvailable(any())).thenReturn(restClientMock);
    when(restClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(anyString())).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.header(anyString(), anyString()))
        .thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

    properties = new VzdTokenProperties();
    service = new VzdSearchService(vzdTokenServiceMock, restClientProviderMock, properties);
  }

  @AfterEach
  void tearDown() throws Exception {
    closeable.close();
  }

  @Test
  void searchByTelematikIdReturnsMappedResult() {
    // given
    var telematikId = "T-123";
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");

    var bundle = getFhirBundle(telematikId);
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByTelematikId(telematikId);

    // then
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.telematikId()).isEqualTo(telematikId);
    assertThat(entry.organizationName()).isEqualTo("OrgName");
    assertThat(entry.phoneNumbers()).containsExactly("+49-123");
    assertThat(entry.address()).isNotNull();
    assertThat(entry.location()).isEqualTo(new VzdLocation(22.22, 11.11));

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock)
        .uri(org.mockito.ArgumentMatchers.contains("HealthcareService"));
  }

  @Test
  void searchByTelematikIdThrowsOnRestClientError() {
    // given
    var telematikId = "T-ERR";
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenThrow(new RestClientException("boom"));

    // when / then
    var ex = assertThrows(VzdSearchException.class, () -> service.searchByTelematikId(telematikId));
    assertThat(ex.getMessage()).contains(telematikId);
  }

  @Test
  void searchByNameOrLocationValidatesLatLonPair() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");

    // when / then
    assertThrows(
        IllegalArgumentException.class,
        () -> service.searchByNameOrLocation("Name", null, 51.0, null, null, null));
  }

  @Test
  void searchByNameUsesTextQueryAndMapsResult() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-123");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result =
        service.searchByNameOrLocation("OrgName", null, null, null, null, null);

    // then
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.organizationName()).isEqualTo("OrgName");

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock).uri(org.mockito.ArgumentMatchers.contains("_text"));
  }

  @Test
  void searchByNameOrLocationPrefersFulltextAndAppliesDefaultPagination() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-123");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result =
        service.searchByNameOrLocation("IgnoredName", "PreferredText", 51.0, 7.0, null, null);

    // then
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.entries()).hasSize(1);

    verify(requestHeadersUriSpecMock)
        .uri(org.mockito.ArgumentMatchers.contains("_text=PreferredText"));
    verify(requestHeadersUriSpecMock)
        .uri(org.mockito.ArgumentMatchers.contains("location.near=51.0%7C7.0%7C10%7Ckm"));
    verify(requestHeadersUriSpecMock).uri(org.mockito.ArgumentMatchers.contains("_count=20"));
    verify(requestHeadersUriSpecMock).uri(org.mockito.ArgumentMatchers.contains("_offset=0"));
  }

  @Test
  void searchByNameOrLocationAddsLocationNearAndGeneratesNextLinkWhenPageFull() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-LOC");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByNameOrLocation(null, null, 11.11, 22.22, 5, 1);

    // then
    assertThat(result.entries()).hasSize(1);
    // next page url should be synthesized because page appears full and no next link provided
    assertThat(result.nextPageUrl()).isNotNull();
    assertThat(result.nextPageUrl()).contains("_offset=1");

    verify(requestHeadersUriSpecMock).uri(org.mockito.ArgumentMatchers.contains("location.near"));
    verify(requestHeadersUriSpecMock).uri(org.mockito.ArgumentMatchers.contains("_sort"));
  }

  @Test
  void searchByNameOrLocationReturnsEmptyResultForNullBundle() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(null);

    // when
    VzdSearchResult result = service.searchByNameOrLocation("Name", null, null, null, null, null);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.entries()).isEmpty();
    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByLocationTextUsesPostalCodeAndMapsResult() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-LOC");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByLocationText("12345");

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.total()).isEqualTo(1);
    var entry = result.entries().getFirst();
    assertThat(entry.location()).isEqualTo(new VzdLocation(22.22, 11.11));

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock)
        .uri(org.mockito.ArgumentMatchers.contains("location.address-postalcode"));
  }

  @Test
  void searchByLocationTextTrimsInputBeforePostalCodeDetection() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle =
        new FhirBundle(
            "Bundle",
            "b2",
            "searchset",
            0,
            null,
            List.of(new FhirLink("self", "https://example.org/fhir/Bundle")));
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByLocationText(" 12345 ");

    // then
    assertThat(result.total()).isZero();
    assertThat(result.entries()).isEmpty();
    verify(requestHeadersUriSpecMock)
        .uri(org.mockito.ArgumentMatchers.contains("location.address-postalcode=12345"));
  }

  @Test
  void searchByLocationTextUsesAddressAndMapsResult() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-ADDR");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByLocationText("Some Street");

    // then
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.organizationName()).isEqualTo("OrgName");

    verify(requestHeadersUriSpecMock)
        .uri(org.mockito.ArgumentMatchers.contains("location.address"));
  }

  @Test
  void searchByLocationTextThrowsOnBlankText() {
    // when / then
    assertThrows(IllegalArgumentException.class, () -> service.searchByLocationText(""));
  }

  @Test
  void searchByLocationTextThrowsOnRestClientError() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenThrow(new RestClientException("boom"));

    // when / then
    var ex = assertThrows(VzdSearchException.class, () -> service.searchByLocationText("Berlin"));
    assertThat(ex.getMessage()).contains("Berlin");
  }

  @Test
  void searchByPageUrlThrowsOnBlankUrl() {
    // when / then
    assertThrows(IllegalArgumentException.class, () -> service.searchByPageUrl(""));
  }

  @Test
  void searchByPageUrlReturnsMappedResultAndSynthesizesNextLink() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-PAGE");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    String url = "https://example.org/fhir/Bundle?_count=1";
    VzdSearchResult result = service.searchByPageUrl(url);

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.nextPageUrl()).isNotNull();
    assertThat(result.nextPageUrl()).contains("_offset=1");

    verify(restClientMock).get();
    verify(requestHeadersUriSpecMock).uri(org.mockito.ArgumentMatchers.contains("example.org"));
  }

  @Test
  void searchByPageUrlKeepsExistingNextLinkAndDoesNotSynthesizeAnotherOne() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle =
        new FhirBundle(
            "Bundle",
            "b3",
            "searchset",
            1,
            List.of(
                new FhirEntry(
                    new FhirResource(
                        "HealthcareService",
                        "hs1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))),
            List.of(new FhirLink("next", "https://example.org/fhir/Bundle?_count=1&_offset=5")));
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result =
        service.searchByPageUrl("https://example.org/fhir/Bundle?_count=1&_offset=5");

    // then
    assertThat(result.nextPageUrl())
        .isEqualTo("https://example.org/fhir/Bundle?_count=1&_offset=5");
  }

  @Test
  void searchByPageUrlDoesNotSynthesizeWithoutCountParameter() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-PAGE");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByPageUrl("https://example.org/fhir/Bundle");

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByPageUrlIgnoresInvalidCountValues() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var bundle = getFhirBundle("T-PAGE");
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByPageUrl("https://example.org/fhir/Bundle?_count=abc");

    // then
    assertThat(result.entries()).hasSize(1);
    assertThat(result.nextPageUrl()).isNull();
  }

  @Test
  void searchByPageUrlThrowsOnRestClientError() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    when(responseSpecMock.body(FhirBundle.class)).thenThrow(new RestClientException("boom"));

    // when / then
    String url = "https://example.org/fhir/Bundle";
    var ex = assertThrows(VzdSearchException.class, () -> service.searchByPageUrl(url));
    assertThat(ex.getMessage()).contains(url);
  }

  @Test
  void searchByTelematikIdMapsMissingOptionalFieldsToNullOrEmptyValues() {
    // given
    when(vzdTokenServiceMock.getAccessToken()).thenReturn("atk");
    var organization =
        new FhirResource(
            "Organization", "org1", "OrgName", null, null, null, null, null, null, null);
    var location =
        new FhirResource("Location", "loc1", null, null, null, null, null, null, null, null);
    var healthcare =
        new FhirResource(
            "HealthcareService",
            "hs1",
            null,
            null,
            null,
            null,
            null,
            new FhirReference("Organization/org1"),
            List.of(new FhirReference("Location/loc1")),
            null);
    var bundle =
        new FhirBundle(
            "Bundle",
            "b4",
            "searchset",
            1,
            List.of(
                new FhirEntry(healthcare), new FhirEntry(organization), new FhirEntry(location)),
            null);
    when(responseSpecMock.body(FhirBundle.class)).thenReturn(bundle);

    // when
    VzdSearchResult result = service.searchByTelematikId("T-EMPTY");

    // then
    assertThat(result.entries()).hasSize(1);
    var entry = result.entries().getFirst();
    assertThat(entry.telematikId()).isNull();
    assertThat(entry.organizationName()).isEqualTo("OrgName");
    assertThat(entry.phoneNumbers()).isEmpty();
    assertThat(entry.address()).isNull();
    assertThat(entry.location()).isNull();
  }

  private static @NonNull FhirBundle getFhirBundle(String telematikId) {
    var organization =
        new FhirResource(
            "Organization",
            "org1",
            "OrgName",
            null,
            List.of(new FhirTelecom("phone", "+49-123")),
            List.of(new FhirAddress(List.of("Street 1"), "12345", "City", null, null)),
            List.of(new FhirIdentifier("https://gematik.de/fhir/sid/telematik-id", telematikId)),
            null,
            null,
            null);

    var location =
        new FhirResource(
            "Location",
            "loc1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new FhirPosition(11.11, 22.22, null));

    var healthcare =
        new FhirResource(
            "HealthcareService",
            "hs1",
            null,
            null,
            null,
            null,
            null,
            new FhirReference("Organization/org1"),
            List.of(new FhirReference("Location/loc1")),
            null);

    return new FhirBundle(
        "Bundle",
        "b1",
        "searchset",
        1,
        List.of(new FhirEntry(healthcare), new FhirEntry(organization), new FhirEntry(location)),
        null);
  }
}
