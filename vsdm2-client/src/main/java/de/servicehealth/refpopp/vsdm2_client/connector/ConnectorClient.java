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

package de.servicehealth.refpopp.vsdm2_client.connector;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.gematik.zeta.sdk.authentication.smcb.BaseSmcbTokenProvider;
import java.util.Base64;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import kotlin.coroutines.Continuation;
import lombok.extern.slf4j.Slf4j;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Single SOAP gateway to the Konnektor. At creation it delegates to a {@link ServicePortProvider},
 * which downloads the service directory ({@code connector.sds}), resolves endpoints, and builds the
 * TLS-configured JAX-WS ports once. Subsequent calls reuse those ports (CXF client proxies are
 * thread-safe).
 *
 * <p>Also serves as the ZETA SDK's {@link BaseSmcbTokenProvider}: the SDK's SMC-B challenge is
 * signed via the Konnektor. The SMC-B is selected by the configured ICCSN (card handles are not
 * stable across card reinsertion or Konnektor restarts, the ICCSN is); without a configured ICCSN
 * the first SMC-B visible to the context is used. The two overrides are Kotlin {@code suspend
 * fun}s; from Java they appear as methods taking an extra {@code Continuation} parameter and
 * returning {@code Object}. Returning the value directly (rather than {@code COROUTINE_SUSPENDED})
 * signals synchronous completion to Kotlin's coroutine runtime.
 */
@Slf4j
@Component
public class ConnectorClient extends BaseSmcbTokenProvider {

  private static final String OCTET_STREAM_MIME_TYPE = "application/octet-stream";

  private final ContextType context;
  private final CertificateServicePortType certificateService;
  private final AuthSignatureServicePortType authSignatureService;
  private final EventServicePortType eventService;
  private final VSDServicePortType vsdService;
  private final String smcbIccsn;

  public ConnectorClient(
      final ConnectorProperties properties, @Qualifier("konnektor") final SSLContext sslContext) {
    this.smcbIccsn = properties.smcbIccsn();
    final ConnectorProperties.Context ctx = properties.context();
    this.context =
        buildContext(
            ctx.clientSystemId(),
            ctx.mandantId(),
            ctx.workplaceId(),
            Optional.ofNullable(ctx.userId()));

    final ServicePortProvider portProvider =
        new ServicePortProvider(properties.endPointUrl(), sslContext);

    this.certificateService = portProvider.certificateServicePort();
    this.authSignatureService = portProvider.authSignatureServicePort();
    this.eventService = portProvider.eventServicePort();
    this.vsdService = portProvider.vsdServicePort();
  }

  @Override
  protected Object readCertificate(final Continuation<? super byte[]> $completion) {
    return readCardCertificate(resolveSmcbCardHandle());
  }

  @Override
  protected Object externalAuthenticate(
      final String base64Challenge, final Continuation<? super byte[]> $completion) {
    final byte[] digest = Base64.getDecoder().decode(base64Challenge);
    return externalAuthenticate(resolveSmcbCardHandle(), digest);
  }

  /**
   * Reads the VSD directly from the eGK via the Konnektor's <i>VSDService</i> (classic VSDM 1.x
   * route). Used as fallback when the VSDM 2.0 Fachdienst is not reachable.
   */
  public ReadVSDResponse readVsd(final String kvnr) {
    log.info("Sending ReadVSD request to connector (VSDM 1.x fallback)");

    final ReadVSD request = new ReadVSD();
    request.setContext(context);
    request.setEhcHandle(resolveEgkCardHandle(kvnr));
    request.setHpcHandle(resolveSmcbCardHandle());
    request.setPerformOnlineCheck(true);
    request.setReadOnlineReceipt(true);

    try {
      return vsdService.readVSD(request);
    } catch (final de.gematik.ws.conn.vsds.vsdservice.v5_2.FaultMessage e) {
      throw new IllegalStateException("ReadVSD fault: " + e.getMessage(), e);
    }
  }

  /**
   * Resolves the card handle of the inserted eGK via <i>GetCards</i>, matched by KVNR if one is
   * given (there may be several eGKs inserted); otherwise the first eGK found is used.
   */
  public String resolveEgkCardHandle(final String kvnr) {
    log.info("Sending GetCards request to connector to find the eGK");

    final GetCards request = new GetCards();
    request.setContext(context);
    request.setCardType(CardTypeType.EGK);

    final GetCardsResponse response;
    try {
      response = eventService.getCards(request);
    } catch (final de.gematik.ws.conn.eventservice.wsdl.v7_2.FaultMessage e) {
      throw new IllegalStateException("GetCards fault: " + e.getMessage(), e);
    }

    final boolean filterByKvnr = kvnr != null && !kvnr.isBlank();
    return response.getCards().getCard().stream()
        .filter(card -> !filterByKvnr || kvnr.equalsIgnoreCase(card.getKvnr()))
        .map(card -> card.getCardHandle())
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    filterByKvnr
                        ? "No eGK with KVNR " + kvnr + " found at the connector"
                        : "No eGK found at the connector"));
  }

  /** Resolves the current card handle of the configured SMC-B via <i>GetCards</i>. */
  public String resolveSmcbCardHandle() {
    log.info("Sending GetCards request to connector");

    final GetCards request = new GetCards();
    request.setContext(context);
    request.setCardType(CardTypeType.SMC_B);

    final GetCardsResponse response;
    try {
      response = eventService.getCards(request);
    } catch (final de.gematik.ws.conn.eventservice.wsdl.v7_2.FaultMessage e) {
      throw new IllegalStateException("GetCards fault: " + e.getMessage(), e);
    }

    final boolean filterByIccsn = smcbIccsn != null && !smcbIccsn.isBlank();
    return response.getCards().getCard().stream()
        .filter(card -> !filterByIccsn || smcbIccsn.equalsIgnoreCase(card.getIccsn()))
        .map(card -> card.getCardHandle())
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    filterByIccsn
                        ? "No SMC-B with ICCSN " + smcbIccsn + " found at the connector"
                        : "No SMC-B found at the connector"));
  }

  public byte[] readCardCertificate(final String cardHandle) {
    log.info("Sending ReadCardCertificate request to connector");

    final ReadCardCertificate.CertRefList certRefList = new ReadCardCertificate.CertRefList();
    certRefList.getCertRef().add(CertRefEnum.C_AUT);

    final ReadCardCertificate request = new ReadCardCertificate();
    request.setCardHandle(cardHandle);
    request.setContext(context);
    request.setCertRefList(certRefList);

    final ReadCardCertificateResponse response;
    try {
      response = certificateService.readCardCertificate(request);
    } catch (final FaultMessage e) {
      throw new IllegalStateException("ReadCardCertificate fault: " + e.getMessage(), e);
    }

    final var infos = response.getX509DataInfoList().getX509DataInfo();
    if (infos.isEmpty()) {
      throw new IllegalStateException("ReadCardCertificate returned no certificate");
    }
    final byte[] cert = infos.getFirst().getX509Data().getX509Certificate();
    if (cert == null || cert.length == 0) {
      throw new IllegalStateException("ReadCardCertificate returned an empty certificate");
    }
    return cert;
  }

  public byte[] externalAuthenticate(final String cardHandle, final byte[] digestBytes) {
    log.info("Sending ExternalAuthenticate request to connector");

    final Base64Data base64Data = new Base64Data();
    base64Data.setMimeType(OCTET_STREAM_MIME_TYPE);
    base64Data.setValue(digestBytes);
    final BinaryDocumentType binaryString = new BinaryDocumentType();
    binaryString.setBase64Data(base64Data);

    final ExternalAuthenticate request = new ExternalAuthenticate();
    request.setCardHandle(cardHandle);
    request.setContext(context);
    request.setOptionalInputs(new ExternalAuthenticate.OptionalInputs());
    request.setBinaryString(binaryString);

    final ExternalAuthenticateResponse response;
    try {
      response = authSignatureService.externalAuthenticate(request);
    } catch (final de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage e) {
      throw new IllegalStateException("ExternalAuthenticate fault: " + e.getMessage(), e);
    }

    final byte[] signature = response.getSignatureObject().getBase64Signature().getValue();
    if (signature == null || signature.length == 0) {
      throw new IllegalStateException("ExternalAuthenticate returned an empty signature");
    }
    return signature;
  }

  private static ContextType buildContext(
      final String clientSystemId,
      final String mandantId,
      final String workplaceId,
      final Optional<String> userId) {
    final ContextType context = new ContextType();
    context.setClientSystemId(clientSystemId);
    context.setMandantId(mandantId);
    context.setWorkplaceId(workplaceId);
    userId.filter(u -> !u.isEmpty()).ifPresent(context::setUserId);
    return context;
  }
}
