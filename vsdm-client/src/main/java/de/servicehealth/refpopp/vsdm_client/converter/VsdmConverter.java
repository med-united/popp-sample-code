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

package de.servicehealth.refpopp.vsdm_client.converter;

import de.gematik.ws.conn.vsds.vsdservice.v5.ObjectFactory;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5.VSDStatusType;
import de.gematik.ws.fa.vsdm.pnw.v1.PN;
import de.gematik.ws.fa.vsdm.vsd.v5.LandType;
import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCGeschuetzteVersichertendatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.soap.*;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VsdmConverter {

  private static final Logger LOG = LoggerFactory.getLogger(VsdmConverter.class);

  private static final String VSD_STATUS_OK = "0";
  private static final String CDM_VERSION = "5.2.0";
  private static final String DEFAULT_VERSICHERTENART = "1";
  private static final String ENCODING_ISO_8859_15 = "ISO-8859-15";
  private static final String INDENT_YES = "yes";
  private static final String INDENT_AMOUNT = "2";

  // Constants for FHIR Extensions and Codes
  private static final String EXT_NAMENSZUSATZ = "humanname-namenszusatz";
  private static final String EXT_OWN_NAME = "humanname-own-name";
  private static final String EXT_STREET = "iso21090-ADXP-streetName";
  private static final String EXT_HOUSE_NUMBER = "iso21090-ADXP-houseNumber";
  private static final String EXT_WOP = "gkv/wop";
  private static final String EXT_VERSICHERTENART = "gkv/versichertenart";
  private static final String EXT_PERSONENGRUPPE = "gkv/besondere-personengruppe";
  private static final String EXT_ZUZAHLUNGSSTATUS = "gkv/zuzahlungsstatus";
  private static final String EXT_DMP_KENNZEICHNUNG = "gkv/dmp-kennzeichnung";

  private final VsdmFhirParser parser;
  private final JAXBContext readVsdResponseContext;
  private final Map<Class<?>, JAXBContext> innerContexts = new ConcurrentHashMap<>();

  public VsdmConverter(VsdmFhirParser parser) {
    this.parser = parser;
    try {
      this.readVsdResponseContext = JAXBContext.newInstance(ReadVSDResponse.class);
    } catch (JAXBException e) {
      throw new RuntimeException("Failed to initialize JAXBContext", e);
    }
  }

  public ReadVSDResponse createReadVSDResponse(String fhirXmlContent, String vsdmPz) {
    // 1. Parse FHIR and extract relevant top-level resources
    VsdmFhirParser.ExtractedVsdmData data = parser.parseAndExtract(fhirXmlContent);

    // 2. Map directly to SOAP Response Object
    try {
      LOG.debug(
          "Creating ReadVSDResponse for KVNR: {}",
          data.patient().getIdentifierFirstRep().getValue());

      ObjectFactory factory = new ObjectFactory();
      ReadVSDResponse response = factory.createReadVSDResponse();

      response.setPersoenlicheVersichertendaten(compress(generatePersonalDataXml(data.patient())));
      response.setAllgemeineVersicherungsdaten(
          compress(generateAllgemeineVersicherungsdatenXml(data.coverage(), data.payor())));
      response.setGeschuetzteVersichertendaten(
          compress(generateGeschuetzteVersichertendatenXml(data.coverage())));

      VSDStatusType status = factory.createVSDStatusType();
      status.setStatus(VSD_STATUS_OK);
      status.setVersion(CDM_VERSION);

      GregorianCalendar c = new GregorianCalendar();
      c.setTime(new Date());
      XMLGregorianCalendar date = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
      status.setTimestamp(date);

      response.setVSDStatus(status);

      response.setPruefungsnachweis(compress(generatePruefungsnachweisXml(vsdmPz)));

      return response;
    } catch (Exception e) {
      LOG.error("Error creating ReadVSDResponse", e);
      throw new VsdmProcessingException("Failed to create SOAP response object", e);
    }
  }

  public String marshallToSoapString(ReadVSDResponse response) {
    try {
      MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
      SOAPMessage soapMessage = mf.createMessage();
      SOAPBody soapBody = soapMessage.getSOAPBody();

      Marshaller m = readVsdResponseContext.createMarshaller();
      m.marshal(response, soapBody);

      soapMessage.saveChanges();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer transformer = transformerFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, INDENT_YES);
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", INDENT_AMOUNT);

      DOMSource source = new DOMSource(soapMessage.getSOAPPart());
      StreamResult result = new StreamResult(out);
      transformer.transform(source, result);

      return out.toString("UTF-8");
    } catch (Exception e) {
      LOG.error("Error marshalling SOAP message", e);
      throw new VsdmProcessingException("Failed to marshal SOAP message", e);
    }
  }

  private String generatePersonalDataXml(Patient p) throws Exception {
    de.gematik.ws.fa.vsdm.vsd.v5.ObjectFactory vsdFactory =
        new de.gematik.ws.fa.vsdm.vsd.v5.ObjectFactory();
    UCPersoenlicheVersichertendatenXML xml = vsdFactory.createUCPersoenlicheVersichertendatenXML();
    xml.setCDMVERSION(CDM_VERSION);

    UCPersoenlicheVersichertendatenXML.Versicherter versicherter =
        vsdFactory.createUCPersoenlicheVersichertendatenXMLVersicherter();

    if (p.hasIdentifier()) {
      versicherter.setVersichertenID(p.getIdentifierFirstRep().getValue());
    }

    UCPersoenlicheVersichertendatenXML.Versicherter.Person person =
        vsdFactory.createUCPersoenlicheVersichertendatenXMLVersicherterPerson();

    if (p.hasBirthDateElement()) {
      person.setGeburtsdatum(p.getBirthDateElement().getValueAsString().replace("-", ""));
    }

    if (p.hasName()) {
      HumanName name = p.getNameFirstRep();
      person.setVorname(name.getGivenAsSingleString());

      if (name.hasFamilyElement()) {
        StringType family = name.getFamilyElement();
        String ownName = "";
        String nameZusatz = "";

        for (Extension ext : family.getExtension()) {
          String url = ext.getUrl();
          if (url.endsWith(EXT_NAMENSZUSATZ) && ext.getValue() instanceof StringType) {
            nameZusatz = ((StringType) ext.getValue()).getValue();
          } else if (url.endsWith(EXT_OWN_NAME) && ext.getValue() instanceof StringType) {
            ownName = ((StringType) ext.getValue()).getValue();
          }
        }
        person.setNachname(!ownName.isEmpty() ? ownName : family.getValue());
        person.setNamenszusatz(nameZusatz);
      }
    }

    if (p.hasGender()) {
      String g = p.getGender().toCode();
      if ("male".equalsIgnoreCase(g)) person.setGeschlecht("M");
      else if ("female".equalsIgnoreCase(g)) person.setGeschlecht("W");
      else person.setGeschlecht("X");
    }

    UCPersoenlicheVersichertendatenXML.Versicherter.Person.StrassenAdresse adresse =
        vsdFactory.createUCPersoenlicheVersichertendatenXMLVersicherterPersonStrassenAdresse();
    if (p.hasAddress()) {
      Address addr = p.getAddressFirstRep();
      adresse.setPostleitzahl(addr.getPostalCode());
      adresse.setOrt(addr.getCity());

      if (addr.hasLine()) {
        for (StringType line : addr.getLine()) {
          for (Extension ext : line.getExtension()) {
            String url = ext.getUrl();
            if (url.endsWith(EXT_STREET) && ext.getValue() instanceof StringType) {
              adresse.setStrasse(((StringType) ext.getValue()).getValue());
            } else if (url.endsWith(EXT_HOUSE_NUMBER) && ext.getValue() instanceof StringType) {
              adresse.setHausnummer(((StringType) ext.getValue()).getValue());
            }
          }
        }
      }

      LandType land = vsdFactory.createLandType();
      if (addr.hasCountry()) {
        land.setWohnsitzlaendercode(getDeuevCountryCode(addr.getCountryElement()));
      }
      adresse.setLand(land);
    }

    person.setStrassenAdresse(adresse);
    versicherter.setPerson(person);
    xml.setVersicherter(versicherter);

    return marshalInnerXml(xml, UCPersoenlicheVersichertendatenXML.class);
  }

  private String generateAllgemeineVersicherungsdatenXml(Coverage c, Organization kostentraegerOrg)
      throws Exception {
    de.gematik.ws.fa.vsdm.vsd.v5.ObjectFactory vsdFactory =
        new de.gematik.ws.fa.vsdm.vsd.v5.ObjectFactory();
    UCAllgemeineVersicherungsdatenXML xml = vsdFactory.createUCAllgemeineVersicherungsdatenXML();
    xml.setCDMVERSION(CDM_VERSION);

    UCAllgemeineVersicherungsdatenXML.Versicherter versicherter =
        vsdFactory.createUCAllgemeineVersicherungsdatenXMLVersicherter();

    UCAllgemeineVersicherungsdatenXML.Versicherter.Versicherungsschutz schutz =
        vsdFactory.createUCAllgemeineVersicherungsdatenXMLVersicherterVersicherungsschutz();
    if (c.hasPeriod()) {
      if (c.getPeriod().hasStart())
        schutz.setBeginn(c.getPeriod().getStartElement().getValueAsString().replace("-", ""));
      if (c.getPeriod().hasEnd())
        schutz.setEnde(c.getPeriod().getEndElement().getValueAsString().replace("-", ""));
    }

    UCAllgemeineVersicherungsdatenXML.Versicherter.Versicherungsschutz.Kostentraeger kostentraeger =
        vsdFactory
            .createUCAllgemeineVersicherungsdatenXMLVersicherterVersicherungsschutzKostentraeger();
    if (kostentraegerOrg != null) {
      kostentraeger.setName(kostentraegerOrg.getName());
      if (kostentraegerOrg.hasIdentifier()) {
        kostentraeger.setKostentraegerkennung(
            new BigInteger(kostentraegerOrg.getIdentifierFirstRep().getValue()));
        String laenderCode = "D";
        if (kostentraegerOrg.hasAddress()
            && kostentraegerOrg.getAddressFirstRep().hasCountryElement()) {
          laenderCode =
              getDeuevCountryCode(kostentraegerOrg.getAddressFirstRep().getCountryElement());
        }
        kostentraeger.setKostentraegerlaendercode(laenderCode);
      }
    }
    schutz.setKostentraeger(kostentraeger);

    UCAllgemeineVersicherungsdatenXML.Versicherter.Zusatzinfos zusatzinfos =
        vsdFactory.createUCAllgemeineVersicherungsdatenXMLVersicherterZusatzinfos();
    UCAllgemeineVersicherungsdatenXML.Versicherter.Zusatzinfos.ZusatzinfosGKV gkv =
        vsdFactory.createUCAllgemeineVersicherungsdatenXMLVersicherterZusatzinfosZusatzinfosGKV();
    gkv.setVersichertenart(DEFAULT_VERSICHERTENART);

    UCAllgemeineVersicherungsdatenXML.Versicherter.Zusatzinfos.ZusatzinfosGKV
            .ZusatzinfosAbrechnungGKV
        abrechnung =
            vsdFactory
                .createUCAllgemeineVersicherungsdatenXMLVersicherterZusatzinfosZusatzinfosGKVZusatzinfosAbrechnungGKV();

    for (Extension ext : c.getExtension()) {
      String url = ext.getUrl();
      if (ext.getValue() instanceof Coding) {
        String code = ((Coding) ext.getValue()).getCode();
        if (url.endsWith(EXT_WOP)) abrechnung.setWOP(code);
        else if (url.endsWith(EXT_VERSICHERTENART)) gkv.setVersichertenart(code);
      }
    }

    gkv.setZusatzinfosAbrechnungGKV(abrechnung);

    zusatzinfos.setZusatzinfosGKV(gkv);
    versicherter.setVersicherungsschutz(schutz);
    versicherter.setZusatzinfos(zusatzinfos);
    xml.setVersicherter(versicherter);

    return marshalInnerXml(xml, UCAllgemeineVersicherungsdatenXML.class);
  }

  private String generateGeschuetzteVersichertendatenXml(Coverage c) throws Exception {
    de.gematik.ws.fa.vsdm.vsd.v5.ObjectFactory vsdFactory =
        new de.gematik.ws.fa.vsdm.vsd.v5.ObjectFactory();
    UCGeschuetzteVersichertendatenXML xml = vsdFactory.createUCGeschuetzteVersichertendatenXML();
    xml.setCDMVERSION(CDM_VERSION);

    UCGeschuetzteVersichertendatenXML.Zuzahlungsstatus zuzahlung =
        vsdFactory.createUCGeschuetzteVersichertendatenXMLZuzahlungsstatus();
    zuzahlung.setStatus(BigInteger.ZERO); // Default to 0

    for (Extension ext : c.getExtension()) {
      String url = ext.getUrl();
      if (ext.getValue() instanceof Coding) {
        String code = ((Coding) ext.getValue()).getCode();
        if (url.endsWith(EXT_PERSONENGRUPPE)) xml.setBesonderePersonengruppe(new BigInteger(code));
        else if (url.endsWith(EXT_ZUZAHLUNGSSTATUS)) zuzahlung.setStatus(new BigInteger(code));
        else if (url.endsWith(EXT_DMP_KENNZEICHNUNG)) xml.setDMPKennzeichnung(new BigInteger(code));
      }
    }

    xml.setZuzahlungsstatus(zuzahlung);

    // Selektivvertraege is a required element according to Gematik Schema (9 = not used)
    UCGeschuetzteVersichertendatenXML.Selektivvertraege selektivvertraege =
        vsdFactory.createUCGeschuetzteVersichertendatenXMLSelektivvertraege();
    selektivvertraege.setAerztlich(new BigInteger("9"));
    selektivvertraege.setZahnaerztlich(new BigInteger("9"));
    xml.setSelektivvertraege(selektivvertraege);

    return marshalInnerXml(xml, UCGeschuetzteVersichertendatenXML.class);
  }

  private String generatePruefungsnachweisXml(String vsdmPz) throws Exception {
    de.gematik.ws.fa.vsdm.pnw.v1.ObjectFactory pnwFactory =
        new de.gematik.ws.fa.vsdm.pnw.v1.ObjectFactory();
    PN xml = pnwFactory.createPN();
    xml.setCDMVERSION("1.0.0"); // PN requires CDM_VERSION
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
    xml.setTS(sdf.format(new Date()));
    xml.setE(BigInteger.ZERO);
    if (vsdmPz != null) {
      xml.setPZ(vsdmPz.getBytes(ENCODING_ISO_8859_15));
    }
    return marshalInnerXml(xml, PN.class);
  }

  private <T> String marshalInnerXml(T object, Class<T> clazz) throws Exception {
    JAXBContext context =
        innerContexts.computeIfAbsent(
            clazz,
            c -> {
              try {
                return JAXBContext.newInstance(c);
              } catch (JAXBException e) {
                throw new RuntimeException(e);
              }
            });
    Marshaller m = context.createMarshaller();
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    m.setProperty(Marshaller.JAXB_ENCODING, ENCODING_ISO_8859_15);
    StringWriter sw = new StringWriter();
    m.marshal(object, sw);
    return sw.toString();
  }

  private byte[] compress(String data) throws Exception {
    if (data == null) return new byte[0];
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(data.getBytes(ENCODING_ISO_8859_15));
    }
    return baos.toByteArray();
  }

  private String getDeuevCountryCode(StringType countryElement) {
    if (countryElement != null && countryElement.hasExtension()) {
      for (Extension ext : countryElement.getExtension()) {
        if (ext.getValue() instanceof Coding coding
            && "http://fhir.de/CodeSystem/deuev/anlage-8-laenderkennzeichen"
                .equals(coding.getSystem())) {
          return coding.getCode();
        }
      }
    }
    return "D"; // Default to Germany if not explicitly found
  }
}
