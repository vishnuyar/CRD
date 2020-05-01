package org.hl7.davinci.endpoint.files;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.hl7.ShortNameMaps;
import org.hl7.davinci.endpoint.config.YamlConfig;
import org.hl7.davinci.endpoint.cql.CqlRule;
import org.hl7.davinci.endpoint.database.*;
import org.hl7.davinci.endpoint.rules.CoverageRequirementRuleCriteria;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.Files;
import java.util.List;


public abstract class CommonFileStore implements FileStore {

  static final Logger logger = LoggerFactory.getLogger(CommonFileStore.class);

  @Autowired
  private RuleFinder ruleFinder;

  @Autowired
  protected RuleMappingRepository lookupTable;

  @Autowired
  protected FhirResourceRepository fhirResources;

  @Autowired
  protected YamlConfig config;


  // must define in child class
  public abstract void reload();
  public abstract CqlRule getCqlRule(String topic, String fhirVersion);
  public abstract FileResource getFile(String topic, String fileName, String fhirVersion, boolean convert);

  protected abstract FileResource readFhirResourceFromFile(List<FhirResource> fhirResourceList, String fhirVersion, String baseUrl);


  public FileResource getFhirResourceByTopic(String fhirVersion, String resourceType, String name, String baseUrl) {
    logger.info("CommonFileStore::getFhirResourceByTopic(): " + fhirVersion + "/" + resourceType + "/" + name);

    FhirResourceCriteria criteria = new FhirResourceCriteria();
    criteria.setFhirVersion(fhirVersion)
        .setResourceType(resourceType)
        .setName(name);
    List<FhirResource> fhirResourceList = fhirResources.findByName(criteria);
    return readFhirResourceFromFile(fhirResourceList, fhirVersion, baseUrl);
  }

  public FileResource getFhirResourceById(String fhirVersion, String resourceType, String id, String baseUrl) {
    logger.info("CommonFileStore::getFhirResourceById(): " + fhirVersion + "/" + resourceType + "/" + id);

    FhirResourceCriteria criteria = new FhirResourceCriteria();
    criteria.setFhirVersion(fhirVersion)
        .setResourceType(resourceType)
        .setId(id);
    List<FhirResource> fhirResourceList = fhirResources.findById(criteria);
    return readFhirResourceFromFile(fhirResourceList, fhirVersion, baseUrl);
  }

  // from RuleFinder
  public List<RuleMapping> findRules(CoverageRequirementRuleCriteria criteria) {
    logger.info("CommonFileStore::findRules(): " + criteria.toString());
    return ruleFinder.findRules(criteria);
  }

  public List<RuleMapping> findAll() {
    logger.info("CommonFileStore::findAll()");
    return ruleFinder.findAll();
  }

  protected void reloadFromFolder(String path) throws IOException {

    File filePath = new File(path);
    if (!filePath.exists()) {
      String error = "file path " + path + " does not exist";
      throw new IOException(error);
    }

    File[] topics = new File(path).listFiles();
    for (File topic: topics) {
      if (topic.isDirectory()) {

        String topicName = topic.getName();

        // skip the shared folder for now...
        if (topicName.equalsIgnoreCase("Shared")) {
          logger.info("  CommonFileStore::reloadFromFolder() found Shared files");

          File[] fhirFolders = topic.listFiles();
          for (File fhirFolder: fhirFolders) {
            if (fhirFolder.isDirectory()) {
              String fhirVersion = fhirFolder.getName();
              processFhirFolder(topicName, fhirVersion, fhirFolder);
            }
          }

        } else if (topicName.startsWith(".")) {
          //logger.info("  CommonFileStore::reloadFromFolder() skipping all folders starting with .: " + topicName);
        } else {
          logger.info("  CommonFileStore::reloadFromFolder() found topic: " + topicName);

          // process the metadata file
          File[] fhirFolders = topic.listFiles();
          for (File file: fhirFolders) {
            String fileName = file.getName();
            if (fileName.equalsIgnoreCase("TopicMetadata.json")) {
              ObjectMapper objectMapper = new ObjectMapper();

              try {
                // read the file
                String content = new String(Files.readAllBytes(file.toPath()));

                // convert to object
                TopicMetadata metadata = objectMapper.readValue(content, TopicMetadata.class);

                for (Mapping mapping: metadata.getMappings()) {
                  for (String code: mapping.getCodes()) {
                    for (String payer: metadata.getPayers()) {
                      for (String fhirVersion: metadata.getFhirVersions()) {

                        String mainCqlLibraryName = metadata.getTopic() + "Rule";
                        File mainCqlFile = findFile(path, metadata.getTopic(), fhirVersion, mainCqlLibraryName, ".cql");
                        if (mainCqlFile == null) {
                          logger.warn("CommonFileStore::reloadFromFolder(): failed to find main CQL file for topic: " + metadata.getTopic());
                        } else {
                          logger.info("    Added: " + metadata.getTopic() + ": " + payer + ", "
                              + mapping.getCodeSystem() + ", " + code + " (" + fhirVersion + ")");

                          // create table entry and store it back to the table
                          RuleMapping ruleMappingEntry = new RuleMapping();
                          ruleMappingEntry.setPayer(ShortNameMaps.PAYOR_SHORT_NAME_TO_FULL_NAME.get(payer))
                              .setCodeSystem(ShortNameMaps.CODE_SYSTEM_SHORT_NAME_TO_FULL_NAME.get(mapping.getCodeSystem()))
                              .setCode(code)
                              .setFhirVersion(fhirVersion)
                              .setTopic(metadata.getTopic())
                              .setRuleFile(mainCqlFile.getName());
                          lookupTable.save(ruleMappingEntry);
                        }
                      }
                    }
                  }
                }

              } catch (IOException e) {
                logger.info("failed to open file: " + file.getAbsoluteFile());
              }
            } else {
              if (file.isDirectory()) {
                String fhirVersion = fileName;
                processFhirFolder(topicName, fhirVersion, file);
              }
            }
          }
        }
      }
    }

    /* uncomment to print contents of FhirResource table on reload
    // loop through the fhir resources table and print it out
    logger.info("FhirResource: " + FhirResource.getColumnsString());
    for (FhirResource resource : fhirResources.findAll()) {
      logger.info(resource.toString());
    }
    */

  }

  private void processFhirFolder(String topic, String fhirVersion, File fhirPath) {
    fhirVersion = fhirVersion.toUpperCase();
    logger.info("      CommonFileStore::processFhirFolder(): " + fhirVersion + ": " + fhirPath.getName());

    // setup the proper FHIR Context for the version of FHIR we are dealing with
    FhirContext ctx = null;
    if (fhirVersion.equalsIgnoreCase("R4")) {
      ctx = new org.hl7.davinci.r4.FhirComponents().getFhirContext();
    } else if (fhirVersion.equalsIgnoreCase("STU3")) {
      ctx = new org.hl7.davinci.stu3.FhirComponents().getFhirContext();
    } else {
      logger.warn("unsupported FHIR version: " + fhirVersion + ", skipping folder");
      return;
    }
    IParser parser = ctx.newJsonParser();
    parser.setParserErrorHandler(new SuppressParserErrorHandler()); // suppress the unknown element warnings


    File[] directories = fhirPath.listFiles();
    for (File folder : directories) {
      if (folder.getName().equalsIgnoreCase("resources") && folder.isDirectory()) {

        File[] resources = folder.listFiles();
        for (File resource : resources) {
          if (resource.isFile()) {
            String filename = resource.getName();
            logger.info("        process: FHIR Resource: " + filename);

            String[] parts = filename.split("-");
            if (parts.length > 2) {
              String resourceType;// = parts[0];

              if (!parts[1].equalsIgnoreCase(fhirVersion)) {
                logger.warn("CommonFileStore::processFhirFolder() warning: FhirVersion doesn't match!");
                continue;
              }


              // parse the the resource file into the correct FHIR resource
              String resourceId = "";
              String resourceName = "";
              try {
                IBaseResource baseResource = parser.parseResource(new FileInputStream(resource));
                resourceType = baseResource.fhirType(); // grab the FHIR resource type out of the resource
                resourceType = resourceType.toLowerCase();

                if (fhirVersion.equalsIgnoreCase("R4")) {
                  if (resourceType.equalsIgnoreCase("Questionnaire")) {
                    org.hl7.fhir.r4.model.Questionnaire questionnaire = (org.hl7.fhir.r4.model.Questionnaire) baseResource;
                    resourceId = questionnaire.getId();
                    resourceName = questionnaire.getName();
                  } else if (resourceType.equalsIgnoreCase("Library")) {
                    org.hl7.fhir.r4.model.Library library = (org.hl7.fhir.r4.model.Library) baseResource;
                    resourceId = library.getId();
                    resourceName = library.getName();
                  } else if (resourceType.equalsIgnoreCase("ValueSet")) {
                    org.hl7.fhir.r4.model.ValueSet valueSet = (org.hl7.fhir.r4.model.ValueSet) baseResource;
                    resourceId = valueSet.getId();
                    resourceName = valueSet.getName();
                  }
                } else if (fhirVersion.equalsIgnoreCase("STU3")) {
                  if (resourceType.equalsIgnoreCase("Questionnaire")) {
                    org.hl7.fhir.dstu3.model.Questionnaire questionnaire = (org.hl7.fhir.dstu3.model.Questionnaire) baseResource;
                    resourceId = questionnaire.getId();
                    resourceName = questionnaire.getName();
                  } else if (resourceType.equalsIgnoreCase("Library")) {
                    org.hl7.fhir.dstu3.model.Library library = (org.hl7.fhir.dstu3.model.Library) baseResource;
                    resourceId = library.getId();
                    resourceName = library.getName();
                  } else if (resourceType.equalsIgnoreCase("ValueSet")) {
                    org.hl7.fhir.dstu3.model.ValueSet valueSet = (org.hl7.fhir.dstu3.model.ValueSet) baseResource;
                    resourceId = valueSet.getId();
                    resourceName = valueSet.getName();
                  }
                }
              } catch (FileNotFoundException e) {
                logger.warn("could not find file: " + resource.getPath());
                continue;
              }

              if (resourceId == null) {
                // this should never happen, there should always be an ID
                logger.error("Could not find ID for: " + filename + ", defaulting to '" + filename + "' as the ID");
                resourceId = filename;
              }

              if (resourceName == null) {
                resourceName = stripNameFromResourceFilename(filename, fhirVersion);
                logger.info("Could not find name for: " + filename + ", defaulting to '" + resourceName + "' as the name");
              }

              resourceId = resourceId.toLowerCase();
              resourceName = resourceName.toLowerCase();

              // create a FhirResource and save it back to the table
              FhirResource fhirResource = new FhirResource();
              fhirResource.setId(resourceId)
                  .setFhirVersion(fhirVersion)
                  .setResourceType(resourceType)
                  .setTopic(topic)
                  .setFilename(filename)
                  .setName(resourceName);
              fhirResources.save(fhirResource);
            }
          }
        }
      }
    }
  }

  protected File findFile(String localPath, String topic, String fhirVersion, String name, String extension) {
    String cqlFileLocation = localPath + topic + "/" + fhirVersion + "/files/";
    File dir = new File(cqlFileLocation);
    String regex = name + "-\\d.\\d.\\d" + extension;
    FileFilter fileFilter = new RegexFileFilter(regex);
    File[] files = dir.listFiles(fileFilter);
    if (files.length > 0) {
      // just return the first one
      return files[0];
    }
    logger.info("CommonFileStore::findFile(): no files match: " + cqlFileLocation + regex);
    return null;
  }

  protected String stripNameFromResourceFilename(String filename, String fhirVersion) {
    // example filename: Library-R4-HomeOxygenTherapy-prepopulation.json
    int fhirIndex = filename.toUpperCase().indexOf(fhirVersion.toUpperCase());
    int startIndex = fhirIndex + fhirVersion.length() + 1;
    int extensionIndex = filename.toUpperCase().indexOf(".json".toUpperCase());
    return filename.substring(startIndex, extensionIndex);
  }
}