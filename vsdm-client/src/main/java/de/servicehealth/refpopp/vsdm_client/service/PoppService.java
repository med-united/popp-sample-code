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

package de.servicehealth.refpopp.vsdm_client.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import de.servicehealth.refpopp.vsdm_client.endpoint.TokenRequest;
import de.servicehealth.refpopp.vsdm_client.endpoint.TokenResponse;
import de.servicehealth.refpopp.vsdm_client.properties.PoppClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class PoppService {

    private final RestTemplate restTemplate;
    private final PoppClientProperties properties;

    public TokenResponse fetchPoppToken(String egkHandle) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_JSON);

        TokenRequest tokenRequest = new TokenRequest(properties.communicationType(), egkHandle);
        HttpEntity<TokenRequest> entity = new HttpEntity<>(tokenRequest, headers);

        return restTemplate.postForObject(properties.apiUrl(), entity, TokenResponse.class);
    }
}
