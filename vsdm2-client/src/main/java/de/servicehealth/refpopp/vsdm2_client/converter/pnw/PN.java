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

package de.servicehealth.refpopp.vsdm2_client.converter.pnw;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.math.BigInteger;

/**
 * Prüfungsnachweis (PN) of the VSDM Fachdienst, namespace {@code
 * http://ws.gematik.de/fa/vsdm/pnw/v1.0}.
 *
 * <p>Hand-authored equivalent of the JAXB type that {@code vsdm-client} generates from {@code
 * Pruefungsnachweis.xsd}. The api-telematik artifact ships the XSD but not the compiled type, and
 * this module does not run schema code generation, so the binding lives here. Namespaces are
 * declared on the annotations (rather than via {@code package-info}) so the marshalled element and
 * its children are qualified exactly as the schema requires.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
    name = "",
    namespace = PN.NS,
    propOrder = {"ts", "e", "ec", "pz"})
@XmlRootElement(name = "PN", namespace = PN.NS)
public class PN {

  static final String NS = "http://ws.gematik.de/fa/vsdm/pnw/v1.0";

  @XmlElement(name = "TS", namespace = NS, required = true)
  protected String ts;

  @XmlElement(name = "E", namespace = NS, required = true)
  protected BigInteger e;

  @XmlElement(name = "EC", namespace = NS)
  protected BigInteger ec;

  @XmlElement(name = "PZ", namespace = NS)
  protected byte[] pz;

  @XmlAttribute(name = "CDM_VERSION", required = true)
  protected String cdmversion;

  public String getTS() {
    return ts;
  }

  public void setTS(final String value) {
    this.ts = value;
  }

  public BigInteger getE() {
    return e;
  }

  public void setE(final BigInteger value) {
    this.e = value;
  }

  public BigInteger getEC() {
    return ec;
  }

  public void setEC(final BigInteger value) {
    this.ec = value;
  }

  public byte[] getPZ() {
    return pz;
  }

  public void setPZ(final byte[] value) {
    this.pz = value;
  }

  public String getCDMVERSION() {
    return cdmversion;
  }

  public void setCDMVERSION(final String value) {
    this.cdmversion = value;
  }
}
