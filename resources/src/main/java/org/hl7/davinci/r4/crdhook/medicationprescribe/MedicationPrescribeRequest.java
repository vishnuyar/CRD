package org.hl7.davinci.r4.crdhook.medicationprescribe;

import org.cdshooks.CdsRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import org.hl7.davinci.r4.Utilities;
import org.hl7.davinci.r4.crdhook.CrdPrefetch;

public class MedicationPrescribeRequest extends
    CdsRequest<MedicationPrescribeContext, CrdPrefetch> {

  private HashMap<String, Object> mapForPrefetchTemplates = null;

  /**
   * Gets the data from the context to put into the prefetch template.
   * @return a map of prefetch attributes to their values
   */
  @JsonIgnore
  public Object getDataForPrefetchToken() {
    if (mapForPrefetchTemplates != null) {
      return mapForPrefetchTemplates;
    }
    mapForPrefetchTemplates = new HashMap<>();
    mapForPrefetchTemplates.put("user", this.getUser());

    HashMap<String, Object> contextMap = new HashMap<>();
    contextMap.put("patientId", getContext().getPatientId());
    contextMap.put("encounterId", getContext().getEncounterId());
    contextMap.put("medications", Utilities.bundleAsHashmap(getContext().getMedications()));
    mapForPrefetchTemplates.put("context", contextMap);

    return mapForPrefetchTemplates;
  }

}