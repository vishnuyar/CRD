library BasicPractitionerInfo version '0.1.0'
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0' called FHIRHelpers

parameter device_request DeviceRequest
parameter service_request ServiceRequest

context Patient

define function "GetMiddleInitials"(name FHIR.HumanName):
  Substring(Combine((name.given given return Substring(given.value,0,1)),', '),3)

define "Today": Today()

// Get Practitioner ID
define "OrderingProviderId":
  Coalesce(device_request.performer, First(service_request.performer)).reference.value

// Get Practitioner
define "OrderingProvider": singleton from (
  [Practitioner] practitioner
    where ('Practitioner/' + practitioner.id) = "OrderingProviderId")

// Get Practitioner's Name
define "Name": singleton from (
  ("OrderingProvider".name name where name.use.value = 'official') 
  union 
  "OrderingProvider".name)

// Get Practitioner's Name elements
define "LastName": "Name".family.value
define "MiddleInitial": GetMiddleInitials("Name")
define "FirstName": "Name".given[0].value
define "FullName":
  Coalesce("FirstName" + ' ' + "MiddleInitial" + ' ' + "LastName", "FirstName" + ' ' + "LastName")
  

// Get Practitioner's NPI
define "NPI": (singleton from (
  "OrderingProvider".identifier identifier
    where identifier.system.value = 'http://hl7.org/fhir/sid/us-npi')).value.value

