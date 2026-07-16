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

package de.gematik.refpopp.popp_server.vzd.fhir;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Generic representation of a FHIR resource contained in a bundle entry.
 *
 * <p>Depending on {@code resourceType} only a subset of the fields is populated (e.g. {@code
 * HealthcareService}, {@code Organization} or {@code Location}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FhirResource(
    String resourceType,
    String id,
    String name,
    List<String> alias,
    List<FhirTelecom> telecom,
    // Organization has an address array, Location a single address object -> accept both as a list.
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<FhirAddress> address,
    List<FhirIdentifier> identifier,
    FhirReference providedBy,
    List<FhirReference> location,
    FhirPosition position) {}
