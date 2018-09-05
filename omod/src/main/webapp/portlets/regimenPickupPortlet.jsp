<%@ include file="/WEB-INF/template/include.jsp" %>
<script src='<%= request.getContextPath() %>/dwr/interface/RegimenPickupDWRService.js'></script>
<script type="text/javascript">
function enGbStrToDate(strDate){
	var dates = strDate.split('/', 3);
	if(dates.length != 3){
	  return null;
	}
	
	var da = dates[0];
	var mo = dates[1] - 1;
	var ye = dates[2];
	var d = new Date(ye, mo, da, 0, 0, 0, 0);
	return d;
}

function voidPickup(obsId){
    var answer = confirm("Are you sure you want to void this pickup?");
    if(answer){
		RegimenPickupDWRService.voidPickup(obsId, ${model.patient.patientId}, function(ret){
			if(ret == false){alert("Unable to void this pickup");}else{history.go(0);}			
		});
	}
}

function addPickup(){
	//check if the patient state is ART or PMTCT
	var invalidState = "${model.invalidState}";
	if(invalidState){
		  alert(invalidState);
		  return;
	}
	
	//make sure the locale is correct
	var locale = "${model.locale}";
	if(locale != "en_GB"){
	  alert('Please set your locale to English (United Kingdom) so that your date format is dd/mm/yyyy');
	  return;
	}

	//if the patient has any errors correct them
	var hasError = ${model.hasError};
	if(hasError){
		  alert('Please correct all errors in red highlighting before trying to add a pickup');
		  return;
	}
	
	var regimens = document.getElementById("regimens");
	var pickupDate = document.getElementById("pickupDate");
	var locations = document.getElementById("locations");
	
	if (regimens.value == "" || pickupDate.value == "" || locations.value == ""){
		alert("You must enter a pickup date, location and regimen.");
		return;
	}

	//check if this is a future or past date
	var pdate = enGbStrToDate(pickupDate.value);
	var now = new Date();
	if(pdate > now){
		alert('Pickup Date can not be in the future!');
		return;
	}
    var minDate = enGbStrToDate("${model.minDate}");
    
	if(pdate < minDate){
		alert('Pickup Date can not be before ${model.minDate}!');
		return;
	}		
	
	var pastPickups = new Array();<c:set var="iter" value="0" />
	<c:forEach items="${model.pickupsByYear}" var="year" varStatus="i"><c:forEach items="${year}" var="pickupsByMonth" varStatus="j"><c:forEach items="${pickupsByMonth}" var="pickup" varStatus="l">
	pastPickups[${iter}] = new Date(${model.years[i.index]},${j.index},${pickup[0]},0,0,0);<c:set var="iter" value="${iter+1}" /></c:forEach></c:forEach></c:forEach>

	var lastPickup = -1;
	//check if this is a date of an existing pickup
	if(pastPickups.length > 0){
		for(var i = 0; i < pastPickups.length; i++){
			var compare = pdate - pastPickups[i]; 
			if(compare == 0){
				alert('There is already a pickup on that date!');
				return;
			}else if(compare < 0){
				break;
			}
			lastPickup = i;
		}
	}

	if(lastPickup > -1 ){
		var compare = pdate - pastPickups[lastPickup];
		if(compare < 86400000*14){//86400000ms = 1 day = 1000*60*60*24
			alert('Pickup is within 14 days after an existing pickup!');
			return;
		}
		if(lastPickup+1 < pastPickups.length){
			compare = pastPickups[lastPickup+1] - pdate;
			if(compare < 86400000*14){//86400000ms = 1 day = 1000*60*60*24
				alert('Pickup is within 14 days before an existing pickup!');
				return;
			}
		}
	}
	

	var reg = regimens.value;
	var date = pickupDate.value;
	var loc = locations.value;
	
	RegimenPickupDWRService.addPickup(reg, date, loc, ${model.patient.patientId}, function(ret){
		if(ret == false){alert("Unable to add this pickup");}else{history.go(0);}			
	});
}
</script>
<style>
 .DrugNeedsWh{background-color:#FFFFFF;}
 .DrugNeedsGr{background-color:#82D692;}
 .DrugNeedsLg{background-color:#C6EFCE;} 
 .DrugNeedsYe{background-color:#FFEB9C;}
 .DrugNeedsOr{background-color:#FFD040;}
 .DrugNeedsRe{background-color:#FFC7CE;}
</style>
<div class="boxHeader">New Pickup</div>
<div class="box">
	Drug Regimen:
	<select id="regimens">
		<c:forEach items="${model.regCodes}" var="regCodeEntry" varStatus="i">
			<option value="${regCodeEntry.key}"<c:if test="${model.regCodeSelected == regCodeEntry.key}"> selected</c:if>>
				${regCodeEntry.value}
			</option>
		</c:forEach>
	</select>
	&nbsp;&nbsp;&nbsp;
	Pickup Date:
	<input id="pickupDate" style="width:90px;" value="${model.now}" onClick="showCalendar(this)"">
	&nbsp;&nbsp;&nbsp;
	Location:
	<select id="locations" <c:if test="${!empty model.locCodeSelected}">disabled</c:if>>
		<c:forEach items="${model.locCodes}" var="locCodeEntry" varStatus="i">
			<option value="${locCodeEntry.key}"<c:if test="${model.locCodeSelected == locCodeEntry.key}"> selected</c:if>>${locCodeEntry.value}</option>
		</c:forEach>
	</select>
	&nbsp;&nbsp;&nbsp;
	<input type="button" value="submit" onClick="javascript:addPickup();"/>
</div>

<div class="boxHeader">Past Pickups</div>
<div class="box">
<c:choose>
<c:when test="${empty model.pickupsByYear}">
no prior pickups
</c:when>
<c:otherwise>
	<c:if test="${!empty model.invPickups}">
	<table class="DrugNeedsRe" width="600"style="display:inline;border-collapse:collapse;padding:0px;">
		<tr><td style="vertical-align:bottom;border-width:1px;border-color:grey;border-style:solid;padding:0px;"><h2>Invalid Pickups</td></tr>
		<c:forEach items="${model.invPickups}" var="invPickup" varStatus="x">
		<tr>
		<td class="DrugNeedsRe" style="vertical-align:top;border-width:1px;border-color:grey;border-style:solid;padding:0px;">
			<a href="#" onclick="javascript:voidPickup(${invPickup[0]});return false;" style="text-decoration:none;color:black;" title="click to void this pickup">${invPickup[1]}/${invPickup[2]}/${invPickup[3]}</a>
	 		<a href="#" onclick="javascript:voidPickup(${invPickup[0]});return false;" style="text-decoration:none;color:black;" title="click to void this pickup">${invPickup[4]}</a>
		</td>
		</tr>
		</c:forEach>
		<tr><td>&nbsp;</td></tr>            
	</table>
	</c:if>
	<table width="600"style="display:inline;border-collapse:collapse;padding:0px;">
	<c:set var="lastIndex" value="${fn:length(model.pickupsByYear) - 1}" />
	<c:forEach items="${model.pickupsByYear}" var="year" varStatus="i">
		<tr><td colspan="12" style="vertical-align:bottom;border-width:1px;border-color:grey;border-style:solid;padding:0px;"><h2>${model.years[lastIndex - i.index]}</h2></td></tr>            
		<tr>
		<c:forEach items="${model.pickupsByYear[lastIndex - i.index]}" var="pickupsByMonth" varStatus="j">
			<td width="50" class="${model.hilite[lastIndex - i.index][j.index]}" style="vertical-align:top;border-width:1px;border-color:grey;border-style:solid;padding:0px;">
				<table>
					<tr><td width="20" style="padding:0px"><strong>${model.months[j.index]}</strong></td><td width="30" style="padding:0px">Rx</td></tr>
				<c:choose>
			    <c:when test="${empty pickupsByMonth}">
					<tr><td style="padding:0px" colspan="2">&nbsp;</td></tr>
			    </c:when>
	    		<c:otherwise>
					<c:forEach items="${pickupsByMonth}" var="pickup" varStatus="l">
						<tr>
							<td style="padding:0px"><a href="#" onclick="javascript:voidPickup(${pickup[2]});return false;" style="text-decoration:none;color:black;" title="click to void this pickup">${pickup[0]}</a></td>
							<td style="padding:0px"><a href="#" onclick="javascript:voidPickup(${pickup[2]});return false;" style="text-decoration:none;color:blue;" title="click to void this pickup">${pickup[1]}</a></td>
						</tr>
					</c:forEach>
			    </c:otherwise>
				</c:choose>
				</table>
			</td>
		</c:forEach>
		</tr>
		<tr><td colspan="12">&nbsp;</td></tr>            
	</c:forEach>
	</table>

	<table>
	<tr><td colspan="2">Key:</td></tr>
	<tr><td class="DrugNeedsGr" style="border-width:1px;border-color:grey;border-style:solid;">&nbsp; &nbsp; </td><td>Valid Pickup</td></tr>
	<tr><td class="DrugNeedsLg" style="border-width:1px;border-color:grey;border-style:solid;">&nbsp; &nbsp; </td><td>Assumed Valid Pickup (since previous month was a double pickup)</td></tr>
	<tr><td class="DrugNeedsWh" style="border-width:1px;border-color:grey;border-style:solid;">&nbsp; &nbsp; </td><td>No Expected Pickup</td></tr>
	<tr><td class="DrugNeedsYe" style="border-width:1px;border-color:grey;border-style:solid;">&nbsp; &nbsp; </td><td>Warning (within 14-27 days of the previous pickup)</td></tr>
	<tr><td class="DrugNeedsOr" style="border-width:1px;border-color:grey;border-style:solid;">&nbsp; &nbsp; </td><td>Warning (missed pickup)</td></tr>
	<tr><td class="DrugNeedsRe" style="border-width:1px;border-color:grey;border-style:solid;">&nbsp; &nbsp; </td><td>Invalid Pickup (within &lt;14 days of the previous pickup or more than 2 pickups)</td></tr>
	</table>
</c:otherwise>
</c:choose>
</div>