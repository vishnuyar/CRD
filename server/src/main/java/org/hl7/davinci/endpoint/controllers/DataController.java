package org.hl7.davinci.endpoint.controllers;

import org.hl7.davinci.endpoint.Application;
import org.hl7.davinci.endpoint.Utils;
import org.hl7.davinci.endpoint.config.YamlConfig;
import org.hl7.davinci.endpoint.database.*;
import org.hl7.davinci.endpoint.files.FileResource;
import org.hl7.davinci.endpoint.files.FileStore;
import org.hl7.davinci.endpoint.vsac.ValueSetCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Arrays;

import java.io.IOException;
import java.util.logging.Logger;


import javax.servlet.http.HttpServletRequest;


/**
 * Provides the REST interface that can be interacted with at [base]/api/data.
 */
@RestController
public class DataController {
  private static Logger logger = Logger.getLogger(Application.class.getName());


  @Autowired
  private RequestRepository requestRepository;

  @Autowired
  private YamlConfig myConfig;

  @Autowired
  private FileStore fileStore;

  org.hl7.davinci.endpoint.fhir.r4.Metadata r4Metadata = new org.hl7.davinci.endpoint.fhir.r4.Metadata();
  org.hl7.davinci.endpoint.fhir.stu3.Metadata stu3Metadata = new org.hl7.davinci.endpoint.fhir.stu3.Metadata();

  /**
   * Basic constructor to initialize both data repositories.
   * @param requestRepository the database for request logging
   */
  @Autowired
  public DataController(RequestRepository requestRepository) {
    this.requestRepository = requestRepository;

  }

  @GetMapping(value = "/api/requests")
  @CrossOrigin
  public Iterable<RequestLog> showAllLogs() {
    // logger.info("showAllLogs: GET /api/requests");

    boolean[] timelineTrue = new boolean[5];
    Arrays.fill(timelineTrue, Boolean.TRUE);
    boolean[] timelineFalse = new boolean[5];
    Arrays.fill(timelineFalse, Boolean.FALSE);

    Iterable<RequestLog> list = requestRepository.findAll();
    return list;
  }

  @GetMapping(value = "/api/data")
  @CrossOrigin
  public Iterable<RuleMapping> showAll() {
    logger.info("showAll: GET /api/data");
    return fileStore.findAll();
  }

  @GetMapping(path = "/fhir/{fhirVersion}/metadata")
  public ResponseEntity<String> getConformanceStatement(HttpServletRequest request, @PathVariable String fhirVersion) throws IOException {
    fhirVersion = fhirVersion.toUpperCase();
    logger.info("GET /fhir/" + fhirVersion + "/metadata");
    String baseUrl = Utils.getApplicationBaseUrl(request).toString() + "/";

    String json = "";
    if (fhirVersion.equalsIgnoreCase("R4")) {
      json = r4Metadata.getMetadata(baseUrl);
    } else if (fhirVersion.equalsIgnoreCase("STU3")) {
      json = stu3Metadata.getMetadata(baseUrl);
    } else {
      logger.warning("Unsupported FHIR version: " + fhirVersion);
    }

    return new ResponseEntity<String>(json, HttpStatus.OK);
  }

  private ResponseEntity<Resource> processFileResource(FileResource fileResource) {
    if (fileResource == null) {
      logger.warning("file / fhir resource not found, return error (404)");
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileResource.getFilename() + "\"")
        .contentType(MediaType.parseMediaType("application/octet-stream"))
        .body(fileResource.getResource());
  }

  /**
   * Get a FHIR ValueSet expansion by canonical URL. This pretends to be a ValueSet/$expand operator.
   * 
   * @param url The Canonical URL of the ValueSet.
   * @return
   */
  @GetMapping(path = "fhir/r4/ValueSet/$expand")
  public ResponseEntity<Resource> getFhirValueSetExpansion(HttpServletRequest request, @RequestParam String url){
    String baseUrl = Utils.getApplicationBaseUrl(request).toString() + "/";
    logger.info("GET /fhir/R4/ValueSet/$expand");

    if (url != null) {
      // If URL starts with this server's base url, pull out id and search by id
      if (url.startsWith(baseUrl)) {
        String valueSetId = url.split("ValueSet/")[1];
        FileResource fileResource = fileStore.getFhirResourceById("R4", "valueset", "valueset/" + valueSetId, baseUrl);
        return processFileResource(fileResource);

      // If the URL is from elsewhere, look by URL
      } else {
        FileResource fileResource = fileStore.getFhirResourceByUrl("R4", "valueset", url, baseUrl);
        return processFileResource(fileResource);
      }

    // if the URL was not provided, we cannot provide an expansion. return 401 bad request
    } else {
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Retrieve a FHIR resource by id
   * @param fhirVersion (converted to uppercase)
   * @param resource (converted to lowercase)
   * @param id (converted to lowercase)
   * @return
   * @throws IOException
   */
  @GetMapping(path = "/fhir/{fhirVersion}/{resource}/{id}")
  public ResponseEntity<Resource> getFhirResourceById(HttpServletRequest request, @PathVariable String fhirVersion, @PathVariable String resource, @PathVariable String id) throws IOException {
    fhirVersion = fhirVersion.toUpperCase();
    resource = resource.toLowerCase();
    id = id.toLowerCase();
    logger.info("GET /fhir/" + fhirVersion + "/" + resource + "/" + id);
    String baseUrl = Utils.getApplicationBaseUrl(request).toString() + "/";

    FileResource fileResource = fileStore.getFhirResourceById(fhirVersion, resource, resource + "/" + id, baseUrl);
    return processFileResource(fileResource);
  }

  /**
   * Retrieve a FHIR resource by name.
   * @param fhirVersion (converted to uppercase)
   * @param resource (converted to lowercase)
   * @param name (converted to lowercase)
   * @param url The Canonical URL of the resource.
   * @return
   * @throws IOException
   */
  @GetMapping(path = "/fhir/{fhirVersion}/{resource}") //?name={topic}
  public ResponseEntity<Resource> searchFhirResource(HttpServletRequest request, @PathVariable String fhirVersion, 
    @PathVariable String resource, @RequestParam(required = false) String name, @RequestParam(required = false) String url) throws IOException {

    fhirVersion = fhirVersion.toUpperCase();
    resource = resource.toLowerCase();
    String baseUrl = Utils.getApplicationBaseUrl(request).toString() + "/";

    FileResource fileResource = null;

    if (name != null) {
      name = name.toLowerCase();
      logger.info("GET /fhir/" + fhirVersion + "/" + resource + "?name=" + name);
      fileResource = fileStore.getFhirResourceByTopic(fhirVersion, resource, name, baseUrl);
    }
    else if (url != null) {
      logger.info("GET /fhir/" + fhirVersion + "/" + resource + "?url=" + url);
      fileResource = fileStore.getFhirResourceByUrl(fhirVersion, resource, url, baseUrl);  
    }

    return processFileResource(fileResource);
  }



  /**
   * Retrieve a file from the File Store.
   * @param topic (case sensitive)
   * @param fhirVersion (converted to uppercase)
   * @param fileName (case sensitive)
   * @param noconvert
   * @return
   * @throws IOException
   */
  @GetMapping(path = "/files/{topic}/{fhirVersion}/{fileName}")
  public ResponseEntity<Resource> getFile(@PathVariable String topic, @PathVariable String fhirVersion, @PathVariable String fileName, @RequestParam(required = false) boolean noconvert) throws IOException {
    fhirVersion = fhirVersion.toUpperCase();
    logger.info("GET /files/" + topic + "/" + fhirVersion + "/" + fileName);

    FileResource fileResource = fileStore.getFile(topic, fileName, fhirVersion, !noconvert);
    return processFileResource(fileResource);
  }

  /**
   * Reload the entire File Store.
   * @return
   */
  @PostMapping(path = "/reload")
  public RedirectView reload(@RequestParam String vsac_username, @RequestParam String vsac_password) {
    logger.info("reload rule file index");

    if (vsac_username != null && vsac_password != null) {
      fileStore.reinitializeVSACLoader(vsac_username, vsac_password);
    } else {
      fileStore.reinitializeVSACLoader();
    }

    fileStore.reload();
    String newUrl = "/data";

    return new RedirectView(newUrl);
  }

}
