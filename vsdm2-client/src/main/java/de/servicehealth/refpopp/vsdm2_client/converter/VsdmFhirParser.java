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

package de.servicehealth.refpopp.vsdm2_client.converter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

/**
 * Parses the VSDM 2.0 FHIR bundle and extracts the resources the {@link VsdmConverter} maps onto a
 * {@code ReadVSDResponse}. The VSDM 2.0 Fachdienst returns {@code application/fhir+json}, so the
 * bundle is read with the JSON parser.
 */
@Component
public class VsdmFhirParser {

  private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
  private static final String EXT_KOSTENTRAEGER_ROLLE = "VSDMKostentraegerRolle";
  private static final String ROLE_HAUPTKOSTENTRAEGER = "H";

  public record ExtractedVsdmData(Patient patient, Coverage coverage, Organization payor) {}

  public ExtractedVsdmData parseAndExtract(final String fhirJsonContent) {
    try {
      IParser parser = FHIR_CONTEXT.newJsonParser();
      Bundle fhirBundle = parser.parseResource(Bundle.class, fhirJsonContent);

      Patient patient =
          findResource(fhirBundle, Patient.class)
              .orElseThrow(
                  () -> new VsdmProcessingException("Patient resource not found in FHIR bundle"));

      Coverage coverage =
          findResource(fhirBundle, Coverage.class)
              .orElseThrow(
                  () -> new VsdmProcessingException("Coverage resource not found in FHIR bundle"));

      Map<String, Organization> orgMap = indexOrganizations(fhirBundle);

      Organization payor =
          findPayor(coverage, orgMap)
              .orElseThrow(
                  () ->
                      new VsdmProcessingException(
                          "Payor (Hauptkostenträger) not found in FHIR bundle"));

      return new ExtractedVsdmData(patient, coverage, payor);
    } catch (Exception e) {
      throw new VsdmProcessingException("Error parsing or extracting FHIR bundle", e);
    }
  }

  private <T extends Resource> Optional<T> findResource(Bundle bundle, Class<T> resourceType) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(resourceType::isInstance)
        .map(resourceType::cast)
        .findFirst();
  }

  private Map<String, Organization> indexOrganizations(Bundle fhirBundle) {
    return fhirBundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Organization.class::isInstance)
        .map(Organization.class::cast)
        .collect(Collectors.toMap(org -> org.getIdElement().getIdPart(), Function.identity()));
  }

  private Optional<Organization> findPayor(Coverage coverage, Map<String, Organization> orgMap) {
    for (Reference ref : coverage.getPayor()) {
      for (Extension ext : ref.getExtension()) {
        if (ext.getUrl().endsWith(EXT_KOSTENTRAEGER_ROLLE)
            && ext.getValue() instanceof Coding coding) {
          if (ROLE_HAUPTKOSTENTRAEGER.equals(coding.getCode()) && ref.hasReference()) {
            return Optional.ofNullable(orgMap.get(ref.getReferenceElement().getIdPart()));
          }
        }
      }
    }
    // Some Fachdienste (e.g. TK DEV) send the payor references without the
    // VSDMKostentraegerRolle extension; the Hauptkostenträger is the first payor entry then.
    for (Reference ref : coverage.getPayor()) {
      if (ref.hasReference()) {
        Organization organization = orgMap.get(ref.getReferenceElement().getIdPart());
        if (organization != null) {
          return Optional.of(organization);
        }
      }
    }
    return Optional.empty();
  }
}
