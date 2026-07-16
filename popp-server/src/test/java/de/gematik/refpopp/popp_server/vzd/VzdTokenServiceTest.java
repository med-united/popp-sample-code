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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class VzdTokenServiceTest {

  @Test
  void getAccessTokenFetchesAndCachesTokenWhenMissing() throws Exception {
    // given
    var props = new VzdTokenProperties();
    props.setClientId("client-id");
    props.setClientSecret("client-secret");
    props.setSkewSeconds(30);
    var svc = new VzdTokenService(props);

    RestClient restClientMock = Mockito.mock(RestClient.class);
    RestClient.RequestBodyUriSpec postSpecMock = Mockito.mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec postRequestSpecMock = Mockito.mock(RestClient.RequestBodySpec.class);
    RestClient.RequestHeadersUriSpec getSpecMock =
        Mockito.mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec getRequestSpecMock =
        Mockito.mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec oauthResponseSpecMock = Mockito.mock(RestClient.ResponseSpec.class);
    RestClient.ResponseSpec providerResponseSpecMock = Mockito.mock(RestClient.ResponseSpec.class);

    installRestClient(svc, restClientMock);

    when(restClientMock.post()).thenReturn(postSpecMock);
    when(postSpecMock.uri(anyString())).thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.body(any(LinkedMultiValueMap.class))).thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.retrieve()).thenReturn(oauthResponseSpecMock);

    when(restClientMock.get()).thenReturn(getSpecMock);
    when(getSpecMock.uri(anyString())).thenReturn(getRequestSpecMock);
    when(getRequestSpecMock.header(anyString(), anyString())).thenReturn(getRequestSpecMock);
    when(getRequestSpecMock.retrieve()).thenReturn(providerResponseSpecMock);

    var oauthPayload = new HashMap<String, Object>();
    oauthPayload.put("access_token", "ti-token");
    oauthPayload.put("expires_in", 120);

    var providerPayload = new HashMap<String, Object>();
    providerPayload.put("access_token", "provider-token");
    providerPayload.put("expires_in", 90);

    doAnswer(invocation -> oauthPayload)
        .when(oauthResponseSpecMock)
        .body(any(ParameterizedTypeReference.class));
    doAnswer(invocation -> providerPayload)
        .when(providerResponseSpecMock)
        .body(any(ParameterizedTypeReference.class));

    // when
    String token = svc.getAccessToken();

    // then
    assertThat(token).isEqualTo("provider-token");
    verify(restClientMock).post();
    verify(restClientMock).get();
    verify(postRequestSpecMock).body(any(LinkedMultiValueMap.class));
    verify(getRequestSpecMock).header(HttpHeaders.AUTHORIZATION, "Bearer ti-token");
  }

  @Test
  void getAccessTokenReturnsCachedTokenIfValid() throws Exception {
    // given
    var props = new VzdTokenProperties();
    var svc = new VzdTokenService(props);

    // Build a CachedToken instance reflectively
    Class<?> cachedTokenClass =
        Class.forName("de.gematik.refpopp.popp_server.vzd.VzdTokenService$CachedToken");
    Constructor<?> ctor = cachedTokenClass.getDeclaredConstructor(String.class, int.class);
    ctor.setAccessible(true);
    String tokenValue = "cached-token-123";
    int expiresAt = (int) Instant.now().getEpochSecond() + 60; // still valid
    Object cachedTokenInstance = ctor.newInstance(tokenValue, expiresAt);

    // Set the AtomicReference value inside the service
    Field cachedField = VzdTokenService.class.getDeclaredField("cachedToken");
    cachedField.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<Object> ref = (AtomicReference<Object>) cachedField.get(svc);
    ref.set(cachedTokenInstance);

    // when
    String result = svc.getAccessToken();

    // then
    assertThat(result).isEqualTo(tokenValue);
  }

  @Test
  void getAccessTokenReturnsCachedTokenWithoutCallingUpstream() throws Exception {
    // given
    var props = new VzdTokenProperties();
    var svc = new VzdTokenService(props);
    installRestClient(svc, Mockito.mock(RestClient.class));

    Class<?> cachedTokenClass =
        Class.forName("de.gematik.refpopp.popp_server.vzd.VzdTokenService$CachedToken");
    Constructor<?> ctor = cachedTokenClass.getDeclaredConstructor(String.class, int.class);
    ctor.setAccessible(true);
    Object cachedTokenInstance =
        ctor.newInstance("cached-token", (int) Instant.now().getEpochSecond() + 60);

    Field cachedField = VzdTokenService.class.getDeclaredField("cachedToken");
    cachedField.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<Object> ref = (AtomicReference<Object>) cachedField.get(svc);
    ref.set(cachedTokenInstance);

    // when
    String result = svc.getAccessToken();

    // then
    assertThat(result).isEqualTo("cached-token");
  }

  @Test
  void refreshTokenThrowsWhenMissingCredentials() {
    // given
    var props = new VzdTokenProperties(); // clientId/clientSecret are null by default
    var svc = new VzdTokenService(props);

    // when / then
    assertThrows(VzdTokenException.class, svc::getAccessToken);
  }

  @Test
  void getAccessTokenWrapsOauthUpstreamFailure() throws Exception {
    // given
    var props = new VzdTokenProperties();
    props.setClientId("client-id");
    props.setClientSecret("client-secret");
    var svc = new VzdTokenService(props);

    RestClient restClientMock = Mockito.mock(RestClient.class);
    RestClient.RequestBodyUriSpec postSpecMock = Mockito.mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec postRequestSpecMock = Mockito.mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpecMock = Mockito.mock(RestClient.ResponseSpec.class);
    installRestClient(svc, restClientMock);

    when(restClientMock.post()).thenReturn(postSpecMock);
    when(postSpecMock.uri(anyString())).thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.body(any(LinkedMultiValueMap.class))).thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.retrieve()).thenReturn(responseSpecMock);
    doAnswer(
            invocation -> {
              throw new RestClientException("oauth down");
            })
        .when(responseSpecMock)
        .body(any(ParameterizedTypeReference.class));

    // when / then
    var ex = assertThrows(VzdTokenException.class, svc::getAccessToken);
    assertThat(ex.getMessage()).contains("oauth upstream failed");
  }

  @Test
  void getAccessTokenWrapsServiceAuthUpstreamFailure() throws Exception {
    // given
    var props = new VzdTokenProperties();
    props.setClientId("client-id");
    props.setClientSecret("client-secret");
    var svc = new VzdTokenService(props);

    RestClient restClientMock = Mockito.mock(RestClient.class);
    RestClient.RequestBodyUriSpec postSpecMock = Mockito.mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec postRequestSpecMock = Mockito.mock(RestClient.RequestBodySpec.class);
    RestClient.RequestHeadersUriSpec getSpecMock =
        Mockito.mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec getRequestSpecMock =
        Mockito.mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec oauthResponseSpecMock = Mockito.mock(RestClient.ResponseSpec.class);
    RestClient.ResponseSpec providerResponseSpecMock = Mockito.mock(RestClient.ResponseSpec.class);
    installRestClient(svc, restClientMock);

    when(restClientMock.post()).thenReturn(postSpecMock);
    when(postSpecMock.uri(anyString())).thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.body(any(LinkedMultiValueMap.class))).thenReturn(postRequestSpecMock);
    when(postRequestSpecMock.retrieve()).thenReturn(oauthResponseSpecMock);

    when(restClientMock.get()).thenReturn(getSpecMock);
    when(getSpecMock.uri(anyString())).thenReturn(getRequestSpecMock);
    when(getRequestSpecMock.header(anyString(), anyString())).thenReturn(getRequestSpecMock);
    when(getRequestSpecMock.retrieve()).thenReturn(providerResponseSpecMock);

    var oauthPayload = new HashMap<String, Object>();
    oauthPayload.put("access_token", "ti-token");
    oauthPayload.put("expires_in", 120);
    doAnswer(invocation -> oauthPayload)
        .when(oauthResponseSpecMock)
        .body(any(ParameterizedTypeReference.class));
    doAnswer(
            invocation -> {
              throw new RestClientException("service-auth down");
            })
        .when(providerResponseSpecMock)
        .body(any(ParameterizedTypeReference.class));

    // when / then
    var ex = assertThrows(VzdTokenException.class, svc::getAccessToken);
    assertThat(ex.getMessage()).contains("service-auth upstream failed");
  }

  @Test
  void extractTokenAccessTokenWithNumericExpires() throws Exception {
    // given
    var props = new VzdTokenProperties();
    var svc = new VzdTokenService(props);
    Method extract =
        VzdTokenService.class.getDeclaredMethod("extractToken", Map.class, String.class);
    extract.setAccessible(true);

    // when
    Map<String, Object> payload = new HashMap<>();
    payload.put("access_token", "tok1");
    payload.put("expires_in", 123);
    Object tokenObj = extract.invoke(svc, payload, "msg");
    Method accessTokenMethod = tokenObj.getClass().getDeclaredMethod("accessToken");
    Method expiresInMethod = tokenObj.getClass().getDeclaredMethod("expiresIn");
    accessTokenMethod.setAccessible(true);
    expiresInMethod.setAccessible(true);

    // then
    assertThat(accessTokenMethod.invoke(tokenObj)).isEqualTo("tok1");
    assertThat(((Integer) expiresInMethod.invoke(tokenObj))).isEqualTo(123);
  }

  @Test
  void extractTokenTokenFallbackWithStringExpires() throws Exception {
    // given
    var props = new VzdTokenProperties();
    var svc = new VzdTokenService(props);
    Method extract =
        VzdTokenService.class.getDeclaredMethod("extractToken", Map.class, String.class);
    extract.setAccessible(true);

    // when
    Map<String, Object> payload = new HashMap<>();
    payload.put("token", "tok2");
    payload.put("expires-in", "45");
    Object tokenObj = extract.invoke(svc, payload, "msg");
    Method accessTokenMethod = tokenObj.getClass().getDeclaredMethod("accessToken");
    Method expiresInMethod = tokenObj.getClass().getDeclaredMethod("expiresIn");
    accessTokenMethod.setAccessible(true);
    expiresInMethod.setAccessible(true);

    // then
    assertThat(accessTokenMethod.invoke(tokenObj)).isEqualTo("tok2");
    assertThat(((Integer) expiresInMethod.invoke(tokenObj))).isEqualTo(45);
  }

  @Test
  void extractTokenMissingExpiresUsesDefault() throws Exception {
    // given
    var props = new VzdTokenProperties();
    var svc = new VzdTokenService(props);
    Method extract =
        VzdTokenService.class.getDeclaredMethod("extractToken", Map.class, String.class);
    extract.setAccessible(true);

    // when
    Map<String, Object> payload = new HashMap<>();
    payload.put("access_token", "tok3");
    Object tokenObj = extract.invoke(svc, payload, "msg");
    Method accessTokenMethod = tokenObj.getClass().getDeclaredMethod("accessToken");
    Method expiresInMethod = tokenObj.getClass().getDeclaredMethod("expiresIn");
    accessTokenMethod.setAccessible(true);
    expiresInMethod.setAccessible(true);

    // then
    assertThat(accessTokenMethod.invoke(tokenObj)).isEqualTo("tok3");
    int defaultExpires = 300; // DEFAULT_EXPIRES_IN_SECONDS
    assertThat(((Integer) expiresInMethod.invoke(tokenObj))).isEqualTo(defaultExpires);
  }

  @Test
  void extractTokenNonNumericExpiresUsesDefault() throws Exception {
    // given
    var props = new VzdTokenProperties();
    var svc = new VzdTokenService(props);
    Method extract =
        VzdTokenService.class.getDeclaredMethod("extractToken", Map.class, String.class);
    extract.setAccessible(true);

    // when
    Map<String, Object> payload = new HashMap<>();
    payload.put("access_token", "tok4");
    payload.put("expires", "not-a-number");
    Object tokenObj = extract.invoke(svc, payload, "msg");
    Method accessTokenMethod = tokenObj.getClass().getDeclaredMethod("accessToken");
    Method expiresInMethod = tokenObj.getClass().getDeclaredMethod("expiresIn");
    accessTokenMethod.setAccessible(true);
    expiresInMethod.setAccessible(true);

    // then
    assertThat(accessTokenMethod.invoke(tokenObj)).isEqualTo("tok4");
    int defaultExpires = 300; // DEFAULT_EXPIRES_IN_SECONDS
    assertThat(((Integer) expiresInMethod.invoke(tokenObj))).isEqualTo(defaultExpires);
  }

  @Test
  void extractTokenThrowsWhenNoTokenPresent() throws Exception {
    // given
    var props = new VzdTokenProperties();
    var svc = new VzdTokenService(props);

    Method extract =
        VzdTokenService.class.getDeclaredMethod("extractToken", Map.class, String.class);
    extract.setAccessible(true);

    Map<String, Object> payload = new HashMap<>();
    payload.put("expires_in", 10);

    // when / then
    Exception ex = assertThrows(Exception.class, () -> extract.invoke(svc, payload, "missing"));
    // unwrap InvocationTargetException if present
    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
    assertThat(cause).isInstanceOf(VzdTokenException.class);
  }

  private static void installRestClient(VzdTokenService svc, RestClient restClient)
      throws Exception {
    Field restClientField = VzdTokenService.class.getDeclaredField("restClient");
    restClientField.setAccessible(true);
    restClientField.set(svc, restClient);
  }
}
