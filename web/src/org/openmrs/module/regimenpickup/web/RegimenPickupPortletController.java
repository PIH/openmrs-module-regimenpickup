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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.PersonAttribute;
import org.openmrs.Program;
import org.openmrs.ProgramWorkflow;
import org.openmrs.ProgramWorkflowState;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.web.controller.PortletController;

public class RegimenPickupPortletController extends PortletController {
    
    private static String[] months = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
	private static ArrayList<String[]> regCodes = new ArrayList<String[]>();
	static {
		regCodes.add(new String[]{"", ""});
		regCodes.add(new String[]{"1a(30)", "1a"});
		regCodes.add(new String[]{"1b(30)", "1b"});
		regCodes.add(new String[]{"1c", "1c"});
		regCodes.add(new String[]{"1d", "1d"});
		regCodes.add(new String[]{"1e", "1e"});
		regCodes.add(new String[]{"1f", "1f"});
		regCodes.add(new String[]{"2a", "2a"});
		regCodes.add(new String[]{"2b", "2b"});
		regCodes.add(new String[]{"2c", "2c"});
		regCodes.add(new String[]{"2d", "2d"});
		regCodes.add(new String[]{"2e", "2e"});
		regCodes.add(new String[]{"4a", "4a"});
		regCodes.add(new String[]{"4b", "4b"});
		regCodes.add(new String[]{"4c", "4c"});
		regCodes.add(new String[]{"4d", "4d"});
		regCodes.add(new String[]{"5a", "5a"});
		regCodes.add(new String[]{"5b", "5b"});
		regCodes.add(new String[]{"S1", "S1"});
		regCodes.add(new String[]{"S2", "S2"});
		regCodes.add(new String[]{"S3", "S3"});
		regCodes.add(new String[]{"S4", "S4"});
		regCodes.add(new String[]{"S5", "S5"});
		regCodes.add(new String[]{"S6", "S6"});
		regCodes.add(new String[]{"AZT", "AZT"});
		regCodes.add(new String[]{"OTH", "Other"});
	}	
	private static ArrayList<String[]> locCodes = new ArrayList<String[]>();
	static {
		locCodes.add(new String[]{"", "", ""});
		locCodes.add(new String[]{"Bobete", "BB", "4"});
		locCodes.add(new String[]{"Lebakeng", "LK", "5"});	
		locCodes.add(new String[]{"Methalaneng", "MG", "7"});	
		locCodes.add(new String[]{"Nohana", "NH", "2"});	
		locCodes.add(new String[]{"Nkau", "NK", "3"});	
		locCodes.add(new String[]{"Manamaneng", "RA", "8"});	
		locCodes.add(new String[]{"Tlhanyaku", "TY", "10"});	
	}	
	
	/*
	 * functions:
	 *  autopopulate new pickup (last regimen), date (today), site (health center patient attr)
	 *  catch input errors: too past (2006-Jan-01), too future, pickup within 14 days of another
	 *  display warn pickups; missed pickups, invalid pickups
	 *  check not in pmtct or art status
	 *  disable pickup location if location is found
	 *  in order to submit, input errors and display errors must be resolved
	 */
    @SuppressWarnings("unchecked")
	@Override
    protected void populateModel(HttpServletRequest request, Map<String, Object> model) {
        if (Context.isAuthenticated()) {
                    
            String patientIdString = request.getParameter("patientId");
            Patient patient = Context.getPatientService().getPatient(Integer.valueOf(patientIdString));

            //get all unvoided observations for this patient
            ConceptService cs = Context.getConceptService();
            ObsService os = Context.getObsService();
            Concept medsDispensed = cs.getConcept(2876);
            List<Obs> pickupList = os.getObservationsByPersonAndConcept(patient, medsDispensed);

            //we create a 3 layer hierarchy
            //  a variable list of years where each element contains
            //    a fixed list of months where each element contains
            //      a variable list of pickups where each element contains
            //        a regimen code, date, obs_id
            ArrayList<ArrayList<String[]>[]> pickupsByYear = new ArrayList<ArrayList<String[]>[]>();
            ArrayList<String> years = new ArrayList<String>();
            ArrayList<String[]>[] pickupsByMonth;
            String regCode = null;

            //error checking/highlighting data structure storage
            ArrayList<String[]> invPickups = new ArrayList<String[]>();
            //this stores the css class that we will use to highlight error months
            ArrayList<String[]> hilite = new ArrayList<String[]>();
            boolean hasError = false;


            if(pickupList.size() > 0){
                //sort those obs
                Collections.sort(pickupList, new Comparator<Obs>() {
                    public int compare(Obs left, Obs right) {
                        return OpenmrsUtil.compareWithNullAsEarliest(left.getObsDatetime(), right.getObsDatetime());
                    }
                });
                
                // let's determine the bounds
                Obs startDateObj = pickupList.get(0); 
                Date startDate = startDateObj.getObsDatetime();
                Obs endDateObj = pickupList.get(pickupList.size() - 1);
                Date endDate = endDateObj.getObsDatetime();
                
                // add checks to handle bad data
                Date minDate = (new GregorianCalendar(2006, 1, 1)).getTime();
                Date maxDate = new Date();
                
                // prune the list before the min date; but keep a reference to the first obj
                if(startDate.before(minDate)){
                	while(startDate != null && startDate.before(minDate)){
                		pickupList.remove(0);
                		Obs o = pickupList.get(0);
                		if(o != null){
                			startDate = pickupList.get(0).getObsDatetime();
                		}else{
                			startDate = null;
                		}
                	}
                }else{
                	startDateObj = null;
                }
                
                // prune the list after the max date; but keep a reference to the last obj
                if(endDate.after(maxDate)){
                	while(endDate != null && endDate.after(maxDate)){
                		pickupList.remove(pickupList.size() - 1);
                		Obs o = pickupList.get(pickupList.size() - 1);
                		if(o != null){
                			endDate = pickupList.get(pickupList.size() - 1).getObsDatetime();
                		}else{
                			endDate = null;
                		}
                	}
                }else{
                	endDateObj = null;
                }

            
                //now we populate the pickupsByYear 3 level hierarchy
                GregorianCalendar c = new GregorianCalendar();
                c.setTime(pickupList.get(0).getObsDatetime());
                int curYear = c.get(Calendar.YEAR) - 1;
                int curMonth;
                Obs curObs;
                Date curDate = null;
                Date lastDate = null;
               
                for(int i = 0; i < pickupList.size(); i++){
                	curObs = pickupList.get(i);
                	c.setTime(curObs.getObsDatetime());
                	//create the array of months for the year if it is new
                	if(curYear < c.get(Calendar.YEAR)){
                		pickupsByMonth = new ArrayList[12];
                		for(int j = 0; j < 12; j++){
                			pickupsByMonth[j] = new ArrayList<String[]>();
                		}
                		pickupsByYear.add(pickupsByMonth);
                		curYear = c.get(Calendar.YEAR);
                		years.add(Integer.toString(curYear));
                		hilite.add(new String[12]);
                	}else{
                		pickupsByMonth = pickupsByYear.get(pickupsByYear.size()-1); 
                	}
                	
                	if(curYear == c.get(Calendar.YEAR)){
                    	while(curYear == c.get(Calendar.YEAR)){
                    		curMonth = c.get(Calendar.MONTH);
                    		//log.info("Adding: "+ Integer.toString(curYear) + "-" + Integer.toString(curMonth));
                    		regCode = curObs.getValueText();
                    		pickupsByMonth[curMonth].add(new String[]{Integer.toString(c.get(Calendar.DATE)), regCode.substring(0, 2), Integer.toString(curObs.getObsId())});
                    		i++;
                    		if(i < pickupList.size()){
                    			curObs = pickupList.get(i);
                    			c.setTime(curObs.getObsDatetime());
                    		}else{
                    			curYear++;
                    		}
                    	}
                    	i--;
                	}
                }                

            	//now after we've built up our data structure, do some validation via highlighting
                //unfortunately, we have to iterate again through the entire loop twice
                ArrayList<String[]> pickups = null;
                int pickupCounter = -1;
                c.setTime(pickupList.get(0).getObsDatetime());
                int firstYearIndex = 0;
                int firstMonth = c.get(Calendar.MONTH);
                c.setTime(new Date());
                int nowYearIndex = hilite.size() - 1;
                int nowMonth = c.get(Calendar.MONTH);

                for(int i = 0; i < pickupsByYear.size(); i++){
            		pickupsByMonth = pickupsByYear.get(i);
            		for(int j = 0; j < pickupsByMonth.length; j++){
            			pickups = pickupsByMonth[j];
            			pickupCounter += pickups.size();
            			//log.info("i: (" + i + ")  j: (" + j + ")  pickupCounter: ("+ pickupCounter + ")  pickups.size(): ("+ pickups.size() + ")");
        				if(pickups.size() > 0){
                    		curDate = pickupList.get(pickupCounter).getObsDatetime();
        					if(pickups.size() == 1){
	                        	//do the highlighting for assumed ok pickups that are empty
	                    		if(lastDate != null){
	                        		long dayGap = (curDate.getTime()-lastDate.getTime())/86400000;
	                    			//set the highlighting for the current year and month
	                    			if(dayGap < 14){
	                        			hilite.get(i)[j] = "DrugNeedsRe";
	                        			hasError = true;
	                    			}else if(dayGap >= 14 && dayGap < 27){
	                        			hilite.get(i)[j] = "DrugNeedsYe";
	                    			}else{
	                        			hilite.get(i)[j] = "DrugNeedsGr";
	                    			}
	                    		}else{
	                    			hilite.get(i)[j] = "DrugNeedsGr";
	                    		}
	        				}else if(pickups.size() > 1){
	            				if(pickups.size() == 2){
            						//last date can't be used so set it differently
            						Date firstDoublePickup = pickupList.get(pickupCounter - 1).getObsDatetime();
            						//this is a problem
	                        		long dayGap = (curDate.getTime()-firstDoublePickup.getTime())/86400000;
	                    			//set the highlighting for the current year and month
	                    			if(dayGap < 14){
	                        			hilite.get(i)[j] = "DrugNeedsRe";
	                        			hasError = true;
	                    			}else if(dayGap >= 14 && dayGap < 27){
	                        			hilite.get(i)[j] = "DrugNeedsYe";
	                    			}else{
	                        			hilite.get(i)[j] = "DrugNeedsGr";
	                    			}

	            					//check to see if next month is blank
	            					if(adjacentMonthIsEmpty(pickupList, pickupCounter, 1)){
	                					if(j == 11){
	                						//if this is dec and we there is a next year...
	                						if(i+1 < pickupsByYear.size()){
	                							hilite.get(i+1)[0] = "DrugNeedsLg";
	                    					}
	                					}else{
	                						hilite.get(i)[j+1] = "DrugNeedsLg";
	                					}
	            					}else if(adjacentMonthIsEmpty(pickupList, pickupCounter-1, -1)){
	                					if(j == 0){
	                						//if this is jan and we there is a prev year...
	                						if(i-1 < 0){
	                							hilite.get(i-1)[0] = "DrugNeedsLg";
	                    					}
	                					}else{
	                						hilite.get(i)[j-1] = "DrugNeedsLg";
	                					}
	            					}
	            				}else{
	            					//more than 2 pickups
	            					hilite.get(i)[j] = "DrugNeedsRe";
	            					hasError = true;
	            				}
	            			}
                    		lastDate = curDate;
        				}else{
        					if(hilite.get(i)[j] == null){
	        	            	//now set the highlighting for anything that isn't a pickup
        						//log.info("i: " + i + ", j: " + j + ", firstMonth: " + firstMonth + ", nowMonth: " + nowMonth);
	            				if(firstYearIndex == nowYearIndex){
	            					if(j >= firstMonth && j <= nowMonth){
    	            					hilite.get(i)[j] = "DrugNeedsOr";
	            					}else{
    	            					hilite.get(i)[j] = "DrugNeedsWh";
	            					}
	            				}else if((i > firstYearIndex && i < nowYearIndex) || 
	            						(i == firstYearIndex && j >= firstMonth) ||
	            						(i == nowYearIndex && j <= nowMonth)){
	            					hilite.get(i)[j] = "DrugNeedsOr";
		                		}else{
	            					hilite.get(i)[j] = "DrugNeedsWh";
		                		}
        					}
        				}
            		}
            	}

                //if we have either start date obj or end date obj, let's display them as an "invalid" year
                if(startDateObj != null){
                	c.setTime(startDateObj.getObsDatetime());
                	invPickups.add(new String[]{startDateObj.getId().toString(),
                			Integer.toString(c.get(Calendar.DATE)),
                			months[c.get(Calendar.MONTH)],
                			Integer.toString(c.get(Calendar.YEAR)),
                			startDateObj.getValueText()});
        			hasError = true;
                }
                if(endDateObj != null){
                	c.setTime(endDateObj.getObsDatetime());
                	invPickups.add(new String[]{endDateObj.getId().toString(),
                			Integer.toString(c.get(Calendar.DATE)),
                			months[c.get(Calendar.MONTH)],
                			Integer.toString(c.get(Calendar.YEAR)),
                			endDateObj.getValueText()});
        			hasError = true;
                }
            }
            
            
            model.put("pickupsByYear", pickupsByYear);
            model.put("years", years);
            model.put("invPickups", invPickups);
            model.put("months", months);
            model.put("hilite", hilite);
            model.put("hasError", hasError);
            
            //auto set the pickup date
            DateFormat df = Context.getDateFormat();
            String formattedDate = df.format(new Date());
            model.put("now", formattedDate);
            
            //auto set the regimen
            model.put("regCodes", regCodes);
            int regCodeSelected = -1;
            for(int i = 0; i < regCodes.size(); i++){
            	if((regCodes.get(i))[0].equals(regCode)){
            		regCodeSelected = i;
            	}
            }
            model.put("regCodeSelected", regCodeSelected);
            
            //auto set the hc
            PersonAttribute pa = Context.getPersonService().getPerson(patient.getPersonId()).getAttribute(7);
            String hc = null;
            if(pa != null){
            	hc = pa.getValue();
            }else{
            	hc = "";
            }
            //log.info(hc);
           	model.put("locCodes", locCodes);
            int locCodeSelected = -1;
            for(int i = 0; i < locCodes.size(); i++){
            	if((locCodes.get(i))[2].equals(hc)){
            		locCodeSelected = i;
            	}
            }
            model.put("locCodeSelected", locCodeSelected);
            
            //check locale
            String locale = "en_US";
            if(Context.getLocale().getCountry().equals("GB")){
            	locale = "en_GB";
            }
            model.put("locale", locale);
            
            //check patient state, if it isn't ART or PMTCT, then no good
            boolean invalidState = true;
            Integer state = getCurrentState(patient, 1, "TREATMENT STATUS");
            if(state != null){
            	if(state == 1 || state == 27){
            		invalidState = false;
            	}
            }
            model.put("invalidState", invalidState);
        }
    }
    
    /* 
     * stolen from Christian Neumann in Malawi
     */
    private Integer getCurrentState(Patient patient, int programId, String progWkflwConceptName) {
    	ProgramWorkflowService pws = Context.getProgramWorkflowService();
        Program program = pws.getProgram(programId);
        ProgramWorkflow pw = program.getWorkflowByName(progWkflwConceptName);
		List<PatientProgram> pps = pws.getPatientPrograms(patient, program, null, null, new Date(), null, false);
		
		//there should only be one active patient program
		if (pps.size() == 1) {
			PatientState ps = pps.get(0).getCurrentState(pw);
			if(ps != null){
				ProgramWorkflowState pwstate = ps.getState();
				if(pwstate != null){
					return pwstate.getProgramWorkflowStateId();
				}
			}
		}
		return null;
	}
    
    //tells us if the adjacent month from pickupPos is empty (depending on direction)
	//
    private boolean adjacentMonthIsEmpty(List<Obs> pickups, int pickupPos, int direction){
    	GregorianCalendar prev = new GregorianCalendar();
    	GregorianCalendar next = new GregorianCalendar();

    	if(direction > 0){//checking the future adjacent month
    		//this is the last pickup, so the next month is going to be empty
    		if(pickupPos == pickups.size()-1){
    			prev.setTime(pickups.get(pickupPos).getObsDatetime());
    			//if we should expect the pickup next month, then we don't mark it as semi-valid
    			if(prev.get(Calendar.DATE) < 27){
    				return false;
    			}else{
    				return true;
    			}
    		}else{
    			prev.setTime(pickups.get(pickupPos).getObsDatetime());
    			next.setTime(pickups.get(pickupPos+1).getObsDatetime());
    		}
    	}else{ //checking the past adjacent month
    		//this is the first pickup, so the prev month is going to be empty
    		if(pickupPos == 0){
    			return true;
    		}else{
    			prev.setTime(pickups.get(pickupPos-1).getObsDatetime());
    			next.setTime(pickups.get(pickupPos).getObsDatetime());
    		}    		
    	}
    	
    	int py = prev.get(Calendar.YEAR);
    	int ny = next.get(Calendar.YEAR);
    	int pm = prev.get(Calendar.MONTH);
    	int nm = next.get(Calendar.MONTH);
    	if(py == ny){
    		if(nm - pm > 1){
    			return true;
    		}else{
    			return false;
    		}
    	}else if(ny - py == 1){
    		if(pm == 12){
	    		if(nm > 1){
	    			return true;
	    		}else{
	    			return false;
	    		}
    		}else{
    			return true;
    		}
    	}
    	return true;
    }
}