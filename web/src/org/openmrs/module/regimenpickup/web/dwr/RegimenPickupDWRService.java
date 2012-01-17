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
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
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
	    	 
	         Obs obsToDelete = os.getObs(obsId);
	         Encounter encToDelete = obsToDelete.getEncounter();

	         // Void the passed Obs, confirming first that the patient is the one we are working with
	         if (obsToDelete != null && obsToDelete.getPersonId().equals(patientId)){
	             os.voidObs(obsToDelete, "voided by regimenpickup module: delete pickup");
	         }
	         
	         // Void the associated Encounter, or any other Regimen Pickup encounter that occurs on the same date
	         // As the Obs, as long as there are no non-voided Obs within the Encounter
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
	         
	         // only invalidate ART Regimen Pickup Encounters that are now empty
	         if (encToDelete != null && encToDelete.getAllObs().isEmpty()) {
	        	 es.voidEncounter(encToDelete, "voided by regimenpickup module: delete pickup");
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
     public boolean addPickup(String regimen, String dateString, String locationName, Integer patientId) {
    	 try {
    		 // get rid of all existing drug orders
    		 Patient patient = Context.getPatientService().getPatient(patientId);
    		 List<DrugOrder> drugOrders = Context.getOrderService().getDrugOrdersByPatient(patient, OrderService.ORDER_STATUS.NOTVOIDED);
    		 for (DrugOrder o : drugOrders) {
    			 Context.getOrderService().voidOrder(o, "voided by regimenpickup module: cleanup existing drug orders");
    		 }

    		 // create the obs and encounter
    		 Date pickupDate = Context.getDateFormat().parse(dateString);

             Concept medsDispensed = Context.getConceptService().getConcept("2876");
             Location l = getLocation(locationName);

             Obs o = new Obs(patient, medsDispensed, pickupDate, l);
             o.setValueText(regimen);
    		 
    		 Encounter e = new Encounter();
             e.setLocation(l);
             e.setPatient(patient);
             e.setEncounterDatetime(pickupDate);
             e.setEncounterType(getEncounterType());
             e.setProvider(Context.getPersonService().getPerson(2211));
             e.addObs(o);   
                
             Context.getEncounterService().saveEncounter(e);
         } 
    	 catch (Exception ex){
             log.error("Exception thrown: " + ex);
             return false;
         }
         return true;
     } 

     /**
      * Utility method to retrieve the Encounter Type for a regimen pickup encounter
      */
	 private EncounterType getEncounterType(){
    	 EncounterType encType = null;
	     try {
	    	 String encTypeId = Context.getAdministrationService().getGlobalProperty("regimenpickup.regimenPickupEncounterId");
	    	 encType = Context.getEncounterService().getEncounterType(Integer.parseInt(encTypeId));
	     }
	     catch (Exception e) {
	    	 encType = Context.getEncounterService().getEncounterType(11);
	    	 log.warn("Not setting the encounter type on the regimen pickup encounter. Use default of 11." +
			        	" Please configure global property regimenpickup.regimenPickupEncounterId.");
	     }
	     return encType;
	 }
	 
	 /**
	  * Utility method to retrieve a Location by name, or default to Unknown Location if not found
	  */
	 private Location getLocation(String name){
         Location site = null;
         try {
        	 site = Context.getLocationService().getLocation(name);
         }
         catch(Exception e){
             site = Context.getLocationService().getLocation("Unknown Location");
         }
         return site;
	 }
}