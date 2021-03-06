codesystem "ICD-10": 'http://hl7.org/fhir/sid/icd-10'
codesystem "ICD-10-CM": 'http://hl7.org/fhir/sid/icd-10-cm'
codesystem "LOINC": 'http://loinc.org'
codesystem "SNOMED-CT": 'http://snomed.info/sct'

// Neuromuscular disease
code "G12.0": 'G12.0' from "ICD-10-CM"
code "G12.1": 'G12.1' from "ICD-10-CM"
code "G12.20": 'G12.20' from "ICD-10-CM"
code "G12.21": 'G12.21' from "ICD-10-CM"
code "G12.22": 'G12.22' from "ICD-10-CM"
code "G12.23": 'G12.23' from "ICD-10-CM"
code "G12.24": 'G12.24' from "ICD-10-CM"
code "G12.25": 'G12.25' from "ICD-10-CM"
code "G12.29": 'G12.29' from "ICD-10-CM"
code "G12.8": 'G12.8' from "ICD-10-CM"
code "G12.9": 'G12.9' from "ICD-10-CM"

// Thoracic restrictive disease
code "J98.4": 'J98.4' from "ICD-10-CM"
code "36485005": '36485005' from "SNOMED-CT"

// Chest Wall Deformity
code "M95.4": 'M95.4' from "ICD-10-CM"
code "Q67.8": 'Q67.8' from "ICD-10-CM"

// Chronic respiratory failure consequent to COPD
code "J96.10": 'J96.10' from "ICD-10-CM"
code "J96.11": 'J96.11' from "ICD-10-CM"
code "J96.12": 'J96.12' from "ICD-10-CM"
code "J96.20": 'J96.20' from "ICD-10-CM"
code "J96.21": 'J96.21' from "ICD-10-CM"
code "J96.22": 'J96.22' from "ICD-10-CM"
code "J96.90": 'J96.90' from "ICD-10-CM"
code "J96.91": 'J96.91' from "ICD-10-CM"
code "J96.92": 'J96.92' from "ICD-10-CM"


parameter device_request DeviceRequest


define "Neuromuscular_Codes": { "G12.0", "G12.1", "G12.20", "G12.21", "G12.22", "G12.23", "G12.24", "G12.25", "G12.29", "G12.8", "G12.9"}
define "COPD_Codes": { "J96.10", "J96.11", "J96.12", "J96.20", "J96.21", "J96.22", "J96.90", "J96.91", "J96.22"}
define "Thoracic_Restrictive_Codes": { "J98.4", "36485005" }
define "Chest_Wall_Codes": {"M95.4", "Q67.8"}

define "All_Codes": "Neuromuscular_Codes" union "COPD_Codes" union "Thoracic_Restrictive_Codes" union "Chest_Wall_Codes"

context Patient

// coverage requirement info
define RelevantDiagnoses: 
  CodesFromConditions(Confirmed(ActiveOrRecurring([Condition: "All_Codes"]))) 

define DeviceRequestHcpcsCoding: singleton from (
  ((cast device_request.code as CodeableConcept).coding) coding
    where coding.system.value = 'https://bluebutton.cms.gov/resources/codesystem/hcpcs')

define DeviceRequestDescription: 'HCPCS ' + "DeviceRequestHcpcsCoding".code.value + ' - ' + "DeviceRequestHcpcsCoding".display.value

define VentilatorDeviceRequested:
  if "DeviceRequestHcpcsCoding".code.value = 'E0465' then 'E0465'
  else if  "DeviceRequestHcpcsCoding".code.value = 'E0466' then 'E0466'
  else 'null'

define function CodesFromConditions(CondList List<Condition>):
  distinct(flatten(
    CondList C
      let DiagnosesCodings:
          (C.code.coding) CODING where CODING.system.value in {
            'http://hl7.org/fhir/sid/icd-10',
            'http://hl7.org/fhir/sid/icd-10-cm',
            'http://snomed.info/sct'
          }
          return FHIRHelpers.ToCode(CODING)
      return DiagnosesCodings
  ))

////////////////////////////// Taken from CDS Connect Commons for FHIR, could replace with r4 version of helper library
define function ActiveOrRecurring(CondList List<Condition>):
  CondList C where C.clinicalStatus.value in {'active', 'relapse'}

define function ObservationLookBack(ObsList List<Observation>, LookBack System.Quantity):
  ObsList O
    let LookBackInterval: Interval[Now() - LookBack, Now()]
    where (cast O.effective as dateTime).value in LookBackInterval
      or NullSafeToInterval(cast O.effective as Period) overlaps LookBackInterval
      or O.issued in LookBackInterval

define function NullSafeToInterval(Pd FHIR.Period):
  if Pd is not null then Interval[Pd."start".value, Pd."end".value] else null

define function Verified(ObsList List<Observation>):
  ObsList O where O.status.value in {'final', 'amended'}

define function WithUnit(ObsList List<Observation>, Unit String):
  ObsList O where (cast O.value as Quantity).unit.value = Unit or (cast O.value as Quantity).code.value = Unit

define function HighestObservation(ObsList List<Observation>):
  Max(ObsList O return NullSafeToQuantity(cast O.value as Quantity))

define function Confirmed(CondList List<Condition>):
  CondList C where C.verificationStatus.value = 'confirmed'

define function NullSafeToQuantity(Qty FHIR.Quantity):
  if Qty is not null then
    System.Quantity {
      value: Qty.value.value,
      unit: Coalesce(Qty.unit.value, Qty.code.value)
    }
  else null

define function LowestObservation(ObsList List<Observation>):
  Min(ObsList O return NullSafeToQuantity(cast O.value as Quantity))
