package de.servicehealth.refpopp.vsdm_mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/vsdm")
public class VsdmMockController {

    private static final Logger logger = LoggerFactory.getLogger(VsdmMockController.class);

    @GetMapping(value = "/bundle/{kvnr}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getFhirBundle(
            @PathVariable String kvnr,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        logger.info("Mock VSDM 2.0 backend called for KVNR: {} with token: {}", kvnr, authorization);

        try {
            ClassPathResource resource =
                new ClassPathResource("fhir_bundle/019aa697-e026-7735-b898-09ead32a7fa5.xml");
            byte[] data = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return ResponseEntity.ok(new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error reading FHIR bundle from resources", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
