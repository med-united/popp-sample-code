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

package de.gematik.refpopp.popp_client.connector.soap;

import de.gematik.refpopp.popp_client.connector.ConnectorServicesFactory;
import de.gematik.ws.conn.servicedirectory.ServiceType;
import de.gematik.ws.conn.servicedirectory.VersionType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ServicePathExtractor {

  private static final String EVENT_SERVICE_NAME = "EventService";
  private static final String CARD_SERVICE_NAME = "CardService";
  @Getter private final String connectorUrl;
  private final boolean isSecureConnectionEnabled;
  /**
   * Out virtual connector (popp-smartphone-connector) has to provide two different versions
   * of EventService in its service discovery document.
   * One version is being used by the primary service. This version points to the service implemented by
   * the virtual connector itself where getCards will return the egks connected via cardlink websocket.
   *
   * One version is used by the popp client (i.e. this app). That version points has a location pointed
   * at the real connector proxy implemented by the virtual connector. The proxy just passes requests on to the
   * real hardware connector. This way the popp client can retrieve the real smc-bs from the real connector.
   *
   * This is a bit of a hack. The versions are just used to distinguish between a service implemented by the virtual
   * connector itself and the proxy that just forwards requests.
   */
  private final String realConnectorEventServiceVersion;
  private final ConnectorServicesFactory connectorServicesFactory;

  public ServicePathExtractor(
      @Value("${connector.end-point-url}") final String connectorUrl,
      @Value("${connector.secure.enable:false}") final boolean isSecureConnectionEnabled,
      @Value("${connector.real-connector-event-service-version:}") final String realConnectorEventServiceVersion,
      final ConnectorServicesFactory connectorServicesFactory) {
    this.connectorUrl = connectorUrl;
    this.isSecureConnectionEnabled = isSecureConnectionEnabled;
    this.realConnectorEventServiceVersion = realConnectorEventServiceVersion;
    this.connectorServicesFactory = connectorServicesFactory;
  }

  public ServicePath getEventServicePath() {
    return getServicePath(EVENT_SERVICE_NAME, realConnectorEventServiceVersion);
  }

  public ServicePath getCardServicePath() {
    return getServicePath(CARD_SERVICE_NAME, null);
  }

  private ServicePath getServicePath(final String serviceName, String version) {
    log.info("Extracting service information for '{}' from connector.sds", serviceName);
    final List<VersionType> serviceVersions = getServiceVersions(serviceName);

    if (serviceVersions.isEmpty()) {
      log.warn("Service '{}' not defined in connector.sds", serviceName);
      return null;
    }
    VersionType versionType = null;

    if(!Objects.isNull(version) && !version.isEmpty()){
      var serviceOfVersion = serviceVersions.stream().filter(v -> v.getVersion().equals(version)).findFirst();
      if(serviceOfVersion.isPresent()){
      versionType = serviceOfVersion.get();
     }
    }

    if(versionType == null){
      versionType = getLatestVersion(serviceVersions);
    }


    final String location = getLocation(versionType);
    final String endpointPath = getPath(location);

    log.info("Service path for '{}' is '{}'", serviceName, endpointPath);

    final ServicePath servicePath = new ServicePath();
    servicePath.setVersion(versionType.getVersion());
    servicePath.setPath(endpointPath);
    return servicePath;
  }

  private String getLocation(final VersionType versionType) {
    if (isSecureConnectionEnabled && versionType.getEndpointTLS() != null) {
      return versionType.getEndpointTLS().getLocation();
    }

    return versionType.getEndpoint().getLocation();
  }

  private String getPath(final String serviceUrlString) {
    final URI konnektorUri = URI.create(connectorUrl);
    final URI serviceUri = URI.create(serviceUrlString);

    final String konnektorPath = konnektorUri.getPath();

    // Return the service path if konnektor url does not contain a path or path is just one
    // character e.g. "/"
    if (konnektorPath.isBlank() || konnektorPath.length() < 2) {
      return serviceUri.getPath();
    }

    // If the konnektor url contains a path, it must be removed from the service url
    return serviceUri.getPath().replaceFirst("^" + konnektorPath, "");
  }

  private List<VersionType> getServiceVersions(final String serviceName) {
    final var connectorServices = connectorServicesFactory.createConnectorServices();
    final List<ServiceType> services = connectorServices.getServiceInformation().getService();

    for (final ServiceType serviceType : services) {
      if (serviceType.getName().equalsIgnoreCase(serviceName)) {
        return serviceType.getVersions().getVersion();
      }
    }

    return new ArrayList<>();
  }

  private VersionType getLatestVersion(final List<VersionType> serviceVersions) {
    serviceVersions.sort(
        (l, r) -> {
          final String[] left = l.getVersion().split("\\.");
          final String[] right = r.getVersion().split("\\.");
          final int iterableLength = Math.min(left.length, right.length);
          for (int i = 0; i < iterableLength; i++) {
            if (!left[i].equals(right[i])) {
              return Integer.parseInt(left[i]) - Integer.parseInt(right[i]);
            }
          }
          return 0;
        });

    return serviceVersions.getLast();
  }
}
