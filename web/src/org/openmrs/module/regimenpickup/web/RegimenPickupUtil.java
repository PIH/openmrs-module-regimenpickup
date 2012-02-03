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
package org.openmrs.module.regimenpickup.web;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.Program;
import org.openmrs.ProgramWorkflow;
import org.openmrs.api.context.Context;

/**
 * Provides common utility methods for the module
 */
public class RegimenPickupUtil {
    
    protected static final Log log = LogFactory.getLog(RegimenPickupUtil.class);

     /**
      * Utility method to retrieve the Encounter Type for a regimen pickup encounter
      */
	 public static EncounterType getEncounterType() {
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
	 public static Concept getMedsDispensedConcept() {
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
	 
	 public static Map<String, String> getMedsDispensedAnswers() {
    	 Map<String, String> c = new LinkedHashMap<String, String>();
    	 c.put("", "");
	     try {
	    	 String gp = Context.getAdministrationService().getGlobalProperty("regimenpickup.medsDispensedAllowedAnswers");
	    	 for (String answerVal : gp.split(",")) {
	    		 String[] split = answerVal.split("\\:", 2);
	    		 c.put(split[0], split.length > 1 ? split[1] : split[0]);
	    	 }
	     }
	     catch (Exception e) {
	     }
	     if (c.size() == 1) {
	    	 throw new RuntimeException("Unable to configure medication dispensed answers.  Please configure your global properties.");
	     }
	     return c;
	 }
	 
	 /**
	  * @return a Map from supported location name to display name
	  */
	 public static Map<String, String> getSupportedLocations() {
    	 Map<String, String> c = new LinkedHashMap<String, String>();
    	 boolean useDescription = "true".equals(Context.getAdministrationService().getGlobalProperty("regimenpickup.displayLocationDescription"));
    	 c.put("", "");
	     try {
	    	 String gp = Context.getAdministrationService().getGlobalProperty("regimenpickup.supportedLocations");
	    	 for (String locationName : gp.split(",")) {
	    		 Location l = Context.getLocationService().getLocation(locationName);
	    		 c.put(l.getLocationId().toString(), useDescription ? l.getDescription() : l.getName());
	    	 }
	     }
	     catch (Exception e) {
	     }
	     if (c.size() == 1) {
	    	 throw new RuntimeException("Unable to configure configured locations.  Please configure your global properties.");
	     }
	     return c;
	 }
	 
	 /**
	  * Utility method to retrieve a Location by locationId, or default to Unknown Location if not found
	  */
	 public static Location getLocation(String locationId) {
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
	 public static Person getDefaultProvider() {
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
	 
	 public static Date getMinimumSupportedPickupDate() {
		 String val = Context.getAdministrationService().getGlobalProperty("regimenpickup.minimumSupportedPickupDate");
		 try {
			 DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			 return df.parse(val);
		 }
		 catch (Exception e) {
			 
		 }
		 return null;
	 }
	 
	 /**
	  * Utility method to retrieve the default default Location for a given Patient
	  */
	 public static String getDefaultLocationIdForPatient(Patient p) {
	     try {
	    	 String attId = Context.getAdministrationService().getGlobalProperty("regimenpickup.healthCenterPersonAttributeTypeId");
	    	 PersonAttributeType type = Context.getPersonService().getPersonAttributeType(Integer.parseInt(attId));
	    	 PersonAttribute pa = p.getAttribute(type);
	    	 if (pa != null && StringUtils.isNotBlank(pa.getValue())) {
	    		 return pa.getValue();
	    	 }
	     }
	     catch (Exception e) {
	     }
	     return null;
	 }
	 
	 /**
	  * Utility method to retrieve any warning messages, if any about invalid states for a patient
	  */
	 public static String getInvalidStateWarningForPatient(Patient p) {
	     try {
	    	 String statesToCheck = Context.getAdministrationService().getGlobalProperty("regimenpickup.programStatesToCheck");
	    	 String[] split = statesToCheck.split("\\:");
	    	 Program program = Context.getProgramWorkflowService().getProgramByName(split[0]);
	    	 ProgramWorkflow workflow = program.getWorkflowByName(split[1]);
	    	 String[] stateNames = split[2].split("\\|");
	    	 
	    	 List<PatientProgram> pps = Context.getProgramWorkflowService().getPatientPrograms(p, program, null, null, new Date(), null, false);
	    	 
	    	 //there should only be one active patient program
	    	 if (pps.size() == 1) {
	    		 PatientState ps = pps.get(0).getCurrentState(workflow);
	    		 if (ps != null) {
	    			 String stateName = ps.getState().getConcept().getDisplayString();
	    			 for (String validState : stateNames) {
	    				 if (validState.equals(stateName)) {
	    					 return null;
	    				 }
	    			 }
	    		 }
	    	 }else if (pps.size() == 0){
	    		 return "Patient is not enrolled in an active " + split[0] + ".  Please enroll the patient or remove the completion date if the patient is already enrolled.";	    		 
	    	 }else if(pps.size() > 1){
	    		 return "There are multiple " + split[0] + "S for this patient.  Please ensure there is only one active " + split[0] + ".";
	    	 }
	    	 
	    	 StringBuilder stateNameString = new StringBuilder();
	    	 for (String stateName : stateNames) {
	    		 stateNameString.append(stateNameString.length() == 0 ? "" : " or ");
	    		 stateNameString.append(stateName);
	    	 }
	    	 
	    	 return "Patient must be in " + stateNameString + " " + split[1];
	     }
	     catch (Exception e) {
	     }
	     return null;
	 }
}
