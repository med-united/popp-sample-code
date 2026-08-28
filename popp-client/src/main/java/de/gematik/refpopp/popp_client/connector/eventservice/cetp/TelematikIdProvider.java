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

package de.gematik.refpopp.popp_client.connector.eventservice.cetp;

import de.gematik.refpopp.popp_client.connector.certificateservice.ReadCardCertificateClient;
import de.gematik.refpopp.popp_client.connector.eventservice.GetCardsClient;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.asn1.isismtt.x509.Admissions;
import org.bouncycastle.asn1.isismtt.x509.ProfessionInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Determines the telematik id of this installation from the SMC-B's C.AUT certificate (the
 * registration number in the Admission extension). Read once via the connector and cached.
 */
@Component
@ConditionalOnProperty(prefix = "connector.cetp", name = "enabled", havingValue = "true")
@Slf4j
public class TelematikIdProvider {

  private final GetCardsClient getCardsClient;
  private final ReadCardCertificateClient readCardCertificateClient;
  private volatile String telematikId;

  public TelematikIdProvider(
      @Lazy final GetCardsClient getCardsClient,
      @Lazy final ReadCardCertificateClient readCardCertificateClient) {
    this.getCardsClient = getCardsClient;
    this.readCardCertificateClient = readCardCertificateClient;
  }

  public String getTelematikId() {
    if (telematikId == null) {
      synchronized (this) {
        if (telematikId == null) {
          telematikId = readTelematikIdFromSmcb();
        }
      }
    }
    return telematikId;
  }

  private String readTelematikIdFromSmcb() {
    try {
      log.info("| Reading telematik id from SMC-B via connector");
      final var cardHandles =
          getCardsClient.performGetCards("", CardTypeType.SMC_B).getCardHandles();
      if (cardHandles.isEmpty()) {
        throw new IllegalStateException("No SMC-B found at the connector");
      }
      final var certificateBytes =
          readCardCertificateClient.performReadCardCertificate(cardHandles.getFirst());
      final var registrationNumber = extractRegistrationNumber(certificateBytes);
      log.info("| Determined telematik id '{}' from SMC-B certificate", registrationNumber);
      return registrationNumber;
    } catch (Exception e) {
      log.warn("| Could not determine telematik id from SMC-B certificate: {}", e.getMessage());
      return null;
    }
  }

  private String extractRegistrationNumber(final byte[] certificateBytes) throws Exception {
    final var certificateFactory = CertificateFactory.getInstance("X.509");
    final var certificate =
        (X509Certificate)
            certificateFactory.generateCertificate(new ByteArrayInputStream(certificateBytes));
    final var extensionValue =
        certificate.getExtensionValue(ISISMTTObjectIdentifiers.id_isismtt_at_admission.getId());
    if (extensionValue == null) {
      throw new IllegalStateException("SMC-B certificate has no admission extension");
    }
    final var octets = ASN1OctetString.getInstance(extensionValue).getOctets();
    final var admission = AdmissionSyntax.getInstance(ASN1Primitive.fromByteArray(octets));
    for (final Admissions admissions : admission.getContentsOfAdmissions()) {
      for (final ProfessionInfo professionInfo : admissions.getProfessionInfos()) {
        final var registrationNumber = professionInfo.getRegistrationNumber();
        if (registrationNumber != null && !registrationNumber.isBlank()) {
          return registrationNumber;
        }
      }
    }
    throw new IllegalStateException("No registration number in admission extension");
  }
}
