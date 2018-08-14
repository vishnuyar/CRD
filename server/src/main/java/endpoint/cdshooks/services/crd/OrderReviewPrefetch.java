package endpoint.cdshooks.services.crd;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderReviewPrefetch {
  static final Logger logger = LoggerFactory.getLogger(OrderReviewPrefetch.class);

  private Patient patient;
  private Coverage coverage;
  private Location location;
  private Organization insurer;
  private Practitioner provider;

  private PractitionerRole practitionerRole;

  private FhirContext fhirContext;

  /**
   * Constructor that creates a FhirContext used in parsing the FHIR resources
   * out of the request.
   */
  public OrderReviewPrefetch() {
    fhirContext = FhirContext.forR4();
  }

  public Patient getPatient() {
    return patient;
  }

  public Coverage getCoverage() {
    return coverage;
  }

  public Location getLocation() {
    return location;
  }

  public Organization getInsurer() {
    return insurer;
  }

  public Practitioner getProvider() {
    return provider;
  }

  public PractitionerRole getPractitionerRole() {
    return practitionerRole;
  }

  /**
   * Parse the FHIR Patient from the JSON request.
   * @param patientFhirResourceJsonNode is the input JSON node containing the FHIR resource.
   */
  @JsonSetter("patient")
  public void setPatientFhirResource(JsonNode patientFhirResourceJsonNode) {
    String patientString = patientFhirResourceJsonNode.toString();
    try {
      patient = fhirContext.newJsonParser().parseResource(Patient.class, patientString);
    } catch (Exception e) {
      logger.warn("failed to parse patient: " + e.getMessage());
    }
  }

  /**
   * Parse the FHIR Coverage from the JSON request.
   * @param coverageFhirResourceJsonNode is the input JSON node containing the FHIR resource.
   */
  @JsonSetter("coverage")
  public void setCoverageFhirResource(JsonNode coverageFhirResourceJsonNode) {
    String coverageString = coverageFhirResourceJsonNode.toString();
    try {
      coverage = fhirContext.newJsonParser().parseResource(Coverage.class, coverageString);
    } catch (Exception e) {
      logger.warn("failed to parse coverage: " + e.getMessage());
    }
  }

  /**
   * Parse the FHIR Location from the JSON request.
   * @param locationFhirResourceJsonNode is the input JSON node containing the FHIR resource.
   */
  @JsonSetter("location")
  public void setLocationFhirResource(JsonNode locationFhirResourceJsonNode) {
    String locationString = locationFhirResourceJsonNode.toString();
    try {
      location = fhirContext.newJsonParser().parseResource(Location.class, locationString);
    } catch (Exception e) {
      logger.warn("failed to parse location: " + e.getMessage());
    }
  }

  /**
   * Parse the FHIR Organization from the JSON request.
   * @param insurerFhirResourceJsonNode is the input JSON node containing the FHIR resource.
   */
  @JsonSetter("insurer")
  public void setInsurerFhirResource(JsonNode insurerFhirResourceJsonNode) {
    String insurerString = insurerFhirResourceJsonNode.toString();
    try {
      insurer = fhirContext.newJsonParser().parseResource(Organization.class, insurerString);
    } catch (Exception e) {
      logger.warn("failed to parse insurer: " + e.getMessage());
    }
  }

  /**
   * Parse the FHIR Practitioner from the JSON request.
   * @param providerFhirResource is the input JSON node containing the FHIR resource.
   */
  @JsonSetter("provider")
  public void setProviderFhirResource(JsonNode providerFhirResource) {
    String providerString = providerFhirResource.toString();
    try {
      provider = fhirContext.newJsonParser().parseResource(Practitioner.class, providerString);
    } catch (Exception e) {
      logger.warn("failed to parse provider: " + e.getMessage());
    }
  }

  /**
   * Parse the FHIR PractitionerRole from the JSON request.
   * @param practitionerRoleFhirResource is the input JSON node containing the FHIR resource.
   */
  @JsonSetter("practitionerRole")
  public void setPractitionerRoleFhirResource(JsonNode practitionerRoleFhirResource) {
    String practitionerRoleString = practitionerRoleFhirResource.toString();
    try {
      practitionerRole = fhirContext.newJsonParser().parseResource(
          PractitionerRole.class, practitionerRoleString);
    } catch (Exception e) {
      logger.warn("failed to parse practitionerRole: " + e.getMessage());
    }
  }
}