/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.regimenpickup.web.dwr;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;

/**
 * Provides ajax methods for handling regimen pickups
 */
public class RegimenPickupDWRService {
    
    protected static final Log log = LogFactory.getLog(RegimenPickupDWRService.class);

    /**
     * Voids the pickup associated with the given obsId
     * patientId also must be passed as an extra validation check
     */
     public boolean voidPickup(Integer obsId, Integer patientId) {
         try {
	    	 ObsService os = Context.getObsService(); 
	    	 EncounterService es = Context.getEncounterService();
	    	 
	    	 log.info("voidPickup, Patient=" + Integer.toString(patientId) + ", Obs=" + Integer.toString(obsId));
	    	 
	         Obs obsToDelete = os.getObs(obsId);
	         Encounter encToDelete = obsToDelete.getEncounter();

	         // Void the passed Obs, confirming first that the patient is the one we are working with
	         if (obsToDelete != null && obsToDelete.getPersonId().equals(patientId)){
	             os.voidObs(obsToDelete, "voided by regimenpickup module: delete pickup");
	         
		         // Locate the associated Encounter, or another Regimen Pickup encounter that occurs on the same date
		         if (encToDelete == null) {
			         List<EncounterType> ets = new ArrayList<EncounterType>();
			         ets.add(getEncounterType());
			         Patient patient = Context.getPatientService().getPatient(patientId);
		        	 List<Encounter> encs = es.getEncounters(patient, null, obsToDelete.getObsDatetime(), obsToDelete.getObsDatetime(), null, ets, null, false);
		        	 for (Encounter e : encs) {
		        		 if (encToDelete == null && e.getEncounterType() != null && e.getEncounterType().equals(getEncounterType())) {
		        			 encToDelete = e;
		        		 }
		        	 }
		         }
	         
		         // If the found Encounter has no non-voided Obs, void this Encounter as well
		         if (encToDelete != null && encToDelete.getAllObs().isEmpty()) {
		        	 es.voidEncounter(encToDelete, "voided by regimenpickup module: delete pickup");
		         }
	         }
         } 
         catch (Exception ex) {
        	 log.error("Exception thrown: ", ex);
             return false;
         }
         return true;
     }     
     
     /**
      * Adds a new pickup for the given regimen, date, location, and patient
      */
     public boolean addPickup(String regimen, String dateString, String locationId, Integer patientId) {
    	 try {
	    	 log.info("addPickup, Patient=" + Integer.toString(patientId) + ", Date=" + dateString);

	    	 // Parse the input parameters
    		 Patient patient = Context.getPatientService().getPatient(patientId);
    		 Date pickupDate = Context.getDateFormat().parse(dateString);
    		 Location l = getLocation(locationId);
             Concept medsDispensed = getMedsDispensedConcept();
             
             // Create a new Encounter and Observation for this medication pickup
    		 Encounter e = new Encounter();
             e.setLocation(l);
             e.setPatient(patient);
             e.setEncounterDatetime(pickupDate);
             e.setEncounterType(getEncounterType());
             e.setProvider(getDefaultProvider());
             
             Obs o = new Obs(patient, medsDispensed, pickupDate, l);
             o.setValueText(regimen);
             e.addObs(o);   
                
             Context.getEncounterService().saveEncounter(e);
         } 
    	 catch (Exception ex) {
             log.error("Exception thrown: " + ex);
             return false;
         }
         return true;
     } 

     /**
      * Utility method to retrieve the Encounter Type for a regimen pickup encounter
      */
	 private EncounterType getEncounterType() {
    	 EncounterType encType = null;
	     try {
	    	 String encTypeId = Context.getAdministrationService().getGlobalProperty("regimenpickup.regimenPickupEncounterId");
	    	 encType = Context.getEncounterService().getEncounterType(Integer.parseInt(encTypeId));
	     }
	     catch (Exception e) {
	     }
	     if (encType == null) {
	    	 throw new RuntimeException("Unable to find regimen pickup encounter type.  Please configure your global properties.");
	     }
	     return encType;
	 }

     /**
      * Utility method to retrieve the Concept for the medication dispensed question
      */
	 private Concept getMedsDispensedConcept() {
    	 Concept c = null;
	     try {
	    	 String conceptId = Context.getAdministrationService().getGlobalProperty("regimenpickup.medsDispensedConceptId");
	    	 c = Context.getConceptService().getConcept(Integer.parseInt(conceptId));
	     }
	     catch (Exception e) {
	     }
	     if (c == null) {
	    	 throw new RuntimeException("Unable to find medication dispensed concept.  Please configure your global properties.");
	     }
	     return c;
	 }
	 
	 /**
	  * Utility method to retrieve a Location by name, or default to Unknown Location if not found
	  */
	 private Location getLocation(String locationId) {
         Location site = null;
         try {
        	 site = Context.getLocationService().getLocation(Integer.parseInt(locationId));
         }
         catch(Exception e) {
             site = Context.getLocationService().getLocation("Unknown Location");
         }
         return site;
	 }

	 /**
	  * Utility method to retrieve the default Provider by id
	  */
	 private Person getDefaultProvider() {
    	 Person p = null;
	     try {
	    	 String personId = Context.getAdministrationService().getGlobalProperty("regimenpickup.defaultProviderId");
	    	 p = Context.getPersonService().getPerson(Integer.parseInt(personId));
	     }
	     catch (Exception e) {
	     }
	     if (p == null) {
	    	 throw new RuntimeException("Unable to find default provider.  Please configure your global properties.");
	     }
	     return p;
	 }
}
