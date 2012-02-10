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
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.web.controller.PortletController;

public class RegimenPickupPortletController extends PortletController {
    
    private static String[] months = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    
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
        	
            Patient patient = (Patient)model.get("patient");
            log.info("populateModel, Patient=" + Integer.toString(patient.getId()));

            //get all non-voided observations for this patient, ordered by date ascending
            Concept medsDispensed = RegimenPickupUtil.getMedsDispensedConcept();
            List<Obs> pickupList = Context.getObsService().getObservationsByPersonAndConcept(patient, medsDispensed);
            Collections.sort(pickupList, new Comparator<Obs>() {
                public int compare(Obs left, Obs right) {
                    return OpenmrsUtil.compareWithNullAsEarliest(left.getObsDatetime(), right.getObsDatetime());
                }
            });

            //we create a 3 layer hierarchy
            //  a variable list of years where each element contains
            //    a fixed list of months where each element contains
            //      a variable list of pickups where each element contains
            //        a regimen code, date, obs_id
            List<List<String[]>[]> pickupsByYear = new ArrayList<List<String[]>[]>();
            List<String> years = new ArrayList<String>();
            List<String[]>[] pickupsByMonth;
            String regCode = null;

            //error checking/highlighting data structure storage
            List<String[]> invPickups = new ArrayList<String[]>();
            //this stores the css class that we will use to highlight error months
            List<String[]> hilite = new ArrayList<String[]>();
            boolean hasError = false;

            if(pickupList.size() > 0){
                
                // let's determine the bounds
                Obs startDateObj = pickupList.get(0); 
                Date startDate = startDateObj.getObsDatetime();
                Obs endDateObj = pickupList.get(pickupList.size() - 1);
                Date endDate = endDateObj.getObsDatetime();
                
                // add checks to handle bad data
                Date minDate = RegimenPickupUtil.getMinimumSupportedPickupDate();
                Date maxDate = new Date();
                
                // prune the list before the min date; but keep a reference to the first obj
                if(minDate != null && startDate.before(minDate)){
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
                Calendar c = new GregorianCalendar();
                c.setTime(pickupList.get(0).getObsDatetime());
                int curYear = c.get(Calendar.YEAR) - 1;
                int curMonth;
                Obs curObs;
                Date curDate = null;
                Date lastDate = null;
                
                int firstYearIndex = 0;
                int lastYearIndex = years.size()-1;
                int firstMonthIndex = -1;
                int lastMonthIndex = -1;
               
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
                    		String regCodeTmp = regCode;
                    		if(regCode == null){ //some obs have null value_text fields....
                    			regCodeTmp = "unk";
                    		}else if(regCode.length() > 3 ){
                    			regCodeTmp = regCode.substring(0, 2);
                    		}
                    		pickupsByMonth[curMonth].add(new String[]{Integer.toString(c.get(Calendar.DATE)), regCodeTmp, Integer.toString(curObs.getObsId())});
                    		i++;
                    		if(i < pickupList.size()){
                    			curObs = pickupList.get(i);
                    			c.setTime(curObs.getObsDatetime());
                    		}else{
                    			curYear++;
                    		}
                    		
        					firstMonthIndex = firstMonthIndex == -1 ? curMonth : firstMonthIndex;
        					lastMonthIndex = curMonth;
        					lastYearIndex = years.size()-1;
                    	}
                    	i--;
                	}
                }                

            	//now after we've built up our data structure, do some validation via highlighting
                //unfortunately, we have to iterate again through the entire loop twice
                List<String[]> pickups = null;
                int pickupCounter = -1;

                for(int i = 0; i < pickupsByYear.size(); i++){
            		pickupsByMonth = pickupsByYear.get(i);
            		for(int j = 0; j < pickupsByMonth.length; j++){
            			pickups = pickupsByMonth[j];
            			pickupCounter += pickups.size();
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
	                						//if this is dec and there is a next year...
	                						if(i+1 < pickupsByYear.size()){
	                							hilite.get(i+1)[0] = "DrugNeedsLg";
	                    					}
	                					}else{
	                						hilite.get(i)[j+1] = "DrugNeedsLg";
	                					}
	            					}else if(adjacentMonthIsEmpty(pickupList, pickupCounter-1, -1)){
	                					if(j == 0){
	                						//if this is jan and there is a prev year...
	                						if(i-1 > 0){
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
        						if (i == lastYearIndex && j > lastMonthIndex || (i == firstYearIndex && j < firstMonthIndex)) {
        							hilite.get(i)[j] = "DrugNeedsWh";
        						}
        						else {
        							hilite.get(i)[j] = "DrugNeedsOr";
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
            model.put("regCodes", RegimenPickupUtil.getMedsDispensedAnswers());
            model.put("regCodeSelected", regCode);
            
            //auto set the location
            model.put("locCodes", RegimenPickupUtil.getSupportedLocations());
            model.put("locCodeSelected", RegimenPickupUtil.getDefaultLocationIdForPatient(patient));
            
            //check locale
            String locale = "en_US";
            if(Context.getLocale().getCountry().equals("GB")){
            	locale = "en_GB";
            }
            model.put("locale", locale);
            
            //check required patient states, if applicable
            model.put("invalidState", RegimenPickupUtil.getInvalidStateWarningForPatient(patient));
            
            model.put("minDate", df.format(RegimenPickupUtil.getMinimumSupportedPickupDate()));
        }
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