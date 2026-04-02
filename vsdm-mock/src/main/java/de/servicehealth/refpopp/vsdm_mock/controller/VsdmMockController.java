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

package de.servicehealth.refpopp.vsdm_mock.controller;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vsdm")
public class VsdmMockController {

  private static final Logger log = LoggerFactory.getLogger(VsdmMockController.class);

  @GetMapping(value = "/bundle/{kvnr}", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> getFhirBundle(
      @PathVariable String kvnr,
      @RequestHeader(value = "Authorization", required = false) String authorization) {

    log.info("Mock VSDM 2.0 backend called for KVNR: {} with token: {}", kvnr, authorization);

    try {
      ClassPathResource resource =
          new ClassPathResource("fhir_bundle/019aa697-e026-7735-b898-09ead32a7fa5.xml");
      byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
      return ResponseEntity.ok(new String(data, StandardCharsets.UTF_8));
    } catch (Exception e) {
      log.error("Error reading FHIR bundle from resources", e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
