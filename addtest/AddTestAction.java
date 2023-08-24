package com.insta.hms.diagnosticsmasters.addtest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.BasicDynaBean;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.DynaProperty;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionRedirect;
import org.apache.struts.actions.DispatchAction;

import com.bob.hms.adminmasters.organization.OrgMasterDAO;
import com.bob.hms.adminmasters.organization.RateMasterDAO;
import com.bob.hms.common.AutoIncrementId;
import com.bob.hms.common.DBUtil;
import com.bob.hms.common.HSSFWorkbookUtils;
import com.bob.hms.common.RequestContext;
import com.insta.hms.adminmasters.bedmaster.BedMasterDAO;
import com.insta.hms.common.CommonUtils;
import com.insta.hms.common.ConversionUtils;
import com.insta.hms.common.FlashScope;
import com.insta.hms.common.GenericDAO;
import com.insta.hms.common.HealthAuthorityReportableMapping;
import com.insta.hms.common.PagedList;
import com.insta.hms.common.cachemanagement.CacheMasterDAO;
import com.insta.hms.diagnosticmodule.prescribetest.DiagnoDAOImpl;
import com.insta.hms.diagnosticsmasters.Result;
import com.insta.hms.diagnosticsmasters.ResultRangesDAO;
import com.insta.hms.diagnosticsmasters.Test;
import com.insta.hms.diagnosticsmasters.TestObservations;
import com.insta.hms.diagnosticsmasters.TestTemplate;
import com.insta.hms.master.CenterMaster.CenterMasterDAO;
import com.insta.hms.master.DiagResultsCenterApplicability.DiagResultsCenterApplicabilityDAO;
import com.insta.hms.master.RatePlanCategoryMaster.RatePlanCategoryDAO;
import com.insta.hms.master.ServiceSubGroup.ServiceSubGroupDAO;
import com.insta.hms.xls.ExportImport.ChargesImportExporter;
import com.insta.hms.xls.ExportImport.DetailsImportExporter;
import com.nmc.hms.master.inactive.MasterItemInactiveValidationBO;

import flexjson.JSONSerializer;

public class AddTestAction extends DispatchAction {

    static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AddTestAction.class);

    TestChargesDAO  testChargesdao = new TestChargesDAO();

    /*
     * Lists all the tests as a dashboard. (pages/masters/hosp/diagnostics/TestList.jsp)
     */
    public ActionForward listTests(ActionMapping mapping, ActionForm form, HttpServletRequest req,
            HttpServletResponse response) throws SQLException, Exception {
        Map requestParams = new HashMap();
        requestParams.putAll(req.getParameterMap());
        String orgId = req.getParameter("org_id");
        if (orgId == null || orgId.equals("")) {
            String[] org_id = {"ORG0001"};
            requestParams.put("org_id", org_id);
            orgId = "ORG0001";
        }
        PagedList list = AddTestDAOImpl.searchTests(requestParams,
                    ConversionUtils.getListingParameter(requestParams));
        List<String> testIds = new ArrayList<String>();
        for (Map obj : (List<Map>) list.getDtoList()) {
            testIds.add((String) obj.get("test_id"));
        }
        List<String> bedTypes = new BedMasterDAO().getUnionOfBedTypes();
        List testChargesList = AddTestDAOImpl.getTestChargesForAllBedTypes(orgId, bedTypes, testIds);
        Map testChargesMap = ConversionUtils.listBeanToMapMap(testChargesList, "test_id");
        JSONSerializer js = new JSONSerializer().exclude("class");
        req.setAttribute("pagedList", list);
        req.setAttribute("bedTypes", bedTypes);
        req.setAttribute("testCharges", testChargesMap);
        req.setAttribute("testnames", js.serialize(AddTestDAOImpl.getAllTestNames()));
        req.setAttribute("orgId", orgId);
        req.setAttribute("loggedInCenter",RequestContext.getCenterId());
        req.setAttribute("VATPreferences", new CacheMasterDAO().getCachedVATPreferencesList());
        return mapping.findForward("getTestListScreen");
    }

    /*
     * Shows the Add New Test screen.
     */
    public ActionForward getAddTest(ActionMapping mapping, ActionForm form,HttpServletRequest request,
            HttpServletResponse response) throws SQLException {
        saveToken(request);
        request.setAttribute("newEdit", "new");
        request.setAttribute("method", "insertTestDetails");
        JSONSerializer js = new JSONSerializer().exclude("class");
        request.setAttribute("diagdepts", js.serialize(AddTestDAOImpl.getDiagDepartments()));
        request.setAttribute("reportformats",AddTestDAOImpl.getReportFormats());
        request.setAttribute("reportformatsJson", js.deepSerialize(ConversionUtils.listBeanToListMap(AddTestDAOImpl.getAllReportFormats())));
        request.setAttribute("testList", js.serialize((AddTestDAOImpl.getTestNames())));
        request.setAttribute("orgId", request.getParameter("orgId"));
        request.setAttribute("serviceSubGroupsList", js.serialize(ServiceSubGroupDAO.getAllActiveServiceSubGroups()));
        request.setAttribute("hl7Interfaces", new GenericDAO("hl7_lab_interfaces").
                listAll(null, "status", "A"));
        request.setAttribute("codeTypesJSON",
                js.serialize(ConversionUtils.copyListDynaBeansToMap(
                        new GenericDAO("mrd_supported_codes").listAll(null, "code_category", "Observations"))));
        request.setAttribute("impressions", new GenericDAO("histo_impression_master").findAllByKey( "status", "A"));
        request.setAttribute("antibiotics", new GenericDAO("micro_abst_antibiotic_master").listAll());
        request.setAttribute("test_timestamp", new DiagnoDAOImpl().getCountFromDiagTimeStamp());
        request.setAttribute("methodologies", js.serialize(ConversionUtils.copyListDynaBeansToMap
        		(new GenericDAO("diag_methodology_master").findAllByKey("status", "I"))));
        List centers = CenterMasterDAO.getCentersList();
        request.setAttribute("centers_json", js.deepSerialize(ConversionUtils.copyListDynaBeansToMap(centers)));
        request.setAttribute("loggedInCenter",RequestContext.getCenterId());
        request.setAttribute("centers", CenterMasterDAO.getAllCentersAndSuperCenterAsFirst());
        request.setAttribute("RatePlanCategoryData", RatePlanCategoryDAO.getData());
        request.setAttribute("allPackagesforthisOperationList", "null");
		request.setAttribute("allOrderPack", "null");
		List<BasicDynaBean> healthAuthorities = new GenericDAO("health_authority_master").listAll();
		request.setAttribute("healthAuthorities", healthAuthorities);
		
		List columns = new ArrayList<>();
		columns.add("description");
		columns.add("sr_id");
		
		Map<String, String> filterMap = new HashMap<String, String>();
		filterMap.put("status", "A");
		filterMap.put("type", "S");
		
		List specialReporting = new GenericDAO("special_reporting").listAll(columns, filterMap, "sr_id");
		request.setAttribute("specialReporting", specialReporting);
		
		Map<String, BasicDynaBean> diagListBeansMap = new HashMap<String, BasicDynaBean>();
		GenericDAO haDiagTemplateDAO = new GenericDAO("ha_diag_templates");
		for(int i=0; i<healthAuthorities.size(); i++) {
			String healthAuth = String.valueOf(healthAuthorities.get(i).get("health_authority"));
			diagListBeansMap.put(healthAuth, haDiagTemplateDAO.getBean());
		}
		request.setAttribute("diagListBeansMap", diagListBeansMap);
		BasicDynaBean centerMasterBean = new GenericDAO("hospital_center_master").findByKey("center_id", RequestContext.getCenterId());
		String centerHealthAuthority = (String)centerMasterBean.get("health_authority");
		request.setAttribute("healthAuthority", centerHealthAuthority); 
        return mapping.findForward("getAddTestScreen");
    }

    /*
     * Shows the Edit Test screen
     */
    public ActionForward getEditTest(ActionMapping mapping, ActionForm form,HttpServletRequest request,
            HttpServletResponse response) throws SQLException {

        saveToken(request);
        JSONSerializer js = new JSONSerializer().exclude("class");
        String testId = request.getParameter("testid");
        String orgId = request.getParameter("orgId");
        String orgName = request.getParameter("orgName");
        AddTestForm atf = (AddTestForm) form;

        List<BasicDynaBean> testDetails = AddTestDAOImpl.getTestDetails1(testId);

        BasicDynaBean bean = null;
        for(int i=0;i<testDetails.size();i++){
            bean = testDetails.get(i);
            String diagCode = (String)(bean).get("diag_code");
            String testName = (String)(bean).get("test_name");
            testId = (String)(bean).get("test_id");
            String ddeptId = (String)(bean).get("ddept_id");
            String ddeptName = (String)(bean).get("ddept_name");
            Integer typeOfSpeciman = (Integer)(bean).get("sample_type_id");
            String needOfSample = (String)(bean).get("sample_needed");
            String conductionInTemplate = (String)(bean).get("conduction_format");
            String status = (String)(bean).get("status");
            String remarks = (String)(bean).get("remarks");
            BigDecimal routineCharge = (BigDecimal)(bean).get("routine_charge");
            BigDecimal statCharge = (BigDecimal)(bean).get("stat_charge");
            BigDecimal scheduleCharge = (BigDecimal)(bean).get("schedule_charge");
            request.setAttribute("typeOfSpeciman", typeOfSpeciman);
            request.setAttribute("testName", testName);

            List centers = CenterMasterDAO.getCentersList();
            request.setAttribute("centers_json", js.deepSerialize(ConversionUtils.copyListDynaBeansToMap(centers)));

            int serviceSubGroupId = (Integer)(bean).get("service_sub_group_id");
            String groupId = new ServiceSubGroupDAO().findByKey("service_sub_group_id", serviceSubGroupId).get("service_group_id").toString();
            request.setAttribute("groupId", groupId);
            request.setAttribute("serviceSubGroup", serviceSubGroupId);

            atf.setTestName(testName);
            atf.setTestId(testId);
            atf.setDiagCode(diagCode);
            atf.setSpecimen(typeOfSpeciman);
            atf.setSampleNeed(needOfSample);
            atf.setDdeptId(ddeptId);
            atf.setRoutineCharge(routineCharge.toString());
            atf.setRateplan_category_id((Integer)(bean).get("rateplan_category_id"));
            /*atf.setStatCharge(statCharge.toString());
            atf.setScheduleCharge(scheduleCharge.toString());*/
            atf.setReportGroup(conductionInTemplate);
            atf.setTestStatus(status);
            atf.setConduction_applicable((Boolean)bean.get("conduction_applicable"));
            atf.setResults_entry_applicable((Boolean)bean.get("results_entry_applicable"));
            atf.setConducting_doc_mandatory((String)bean.get("conducting_doc_mandatory"));
            atf.setHl7ExportCode((String)bean.get("hl7_export_code"));
         // atf.setHl7ExportCode((String[])bean.get("hl7_export_code"));
            atf.setSampleCollectionInstructions((String)bean.get("sample_collection_instructions"));
            atf.setConductionInstructions((String)bean.get("conduction_instructions"));
            atf.setPreAuthReq((String)bean.get("prior_auth_required"));
            atf.setResultsValidation((String)bean.get("results_validation"));
            atf.setRemarks(remarks);
            atf.setAllow_rate_increase((Boolean)bean.get("allow_rate_increase"));
            atf.setAllow_rate_decrease((Boolean)bean.get("allow_rate_decrease"));
            String conductingRoleId = (String)bean.get("conducting_role_id");
            atf.setConductingRoleIds(conductingRoleId != null ? conductingRoleId.split(",") : null);
            String symptomsIds = (String)bean.get("symptoms_id");
            atf.setSymptomsIds(symptomsIds != null ? symptomsIds.split(",") : null);
            String cancerScreeningIds = (String)bean.get("cancer_screening_id");
            atf.setConsent_required((String)bean.get("consent_required"));
            atf.setTestShortName((String)bean.get("test_short_name"));
            atf.setCancerScreeningIds(cancerScreeningIds != null ? cancerScreeningIds.split(",") : null);
            atf.setIs_sensitivity((String)bean.get("is_sensitivity"));
            
            request.setAttribute("ddeptName", ddeptName);
            request.setAttribute("reportGroup", conductionInTemplate);
            request.setAttribute("cApplicable", (Boolean)bean.get("conduction_applicable"));
            request.setAttribute("ResEntryApplicable", (Boolean)bean.get("results_entry_applicable"));
            request.setAttribute("cDocRequired", (String)bean.get("conducting_doc_mandatory"));
            request.setAttribute("allRateIncr", (Boolean)bean.get("allow_rate_increase"));
            request.setAttribute("allRateDcr", (Boolean)bean.get("allow_rate_decrease"));
            request.setAttribute("testDetails", bean);
            request.setAttribute("loggedInCenter",RequestContext.getCenterId());
            request.setAttribute("testIdforCenter", testId);
            request.setAttribute("RatePlanCategoryData", RatePlanCategoryDAO.getData());

            if(null != conductionInTemplate && conductionInTemplate.equals("T")){
                ArrayList<String> templateList = AddTestDAOImpl.getTemplateList(testId);
                String templates[] = null;
                templates   = populateListValuesTOArray(templates,templateList);
                atf.setFormatName(templates);
            }
            if(null != conductionInTemplate && conductionInTemplate.equals("M")){
            	String nogrowthTemplate = AddTestDAOImpl.getNogrowthTemplate(testId);
	            atf.setNogrowthtemplate(nogrowthTemplate);
	            request.setAttribute("nogrowthtemplateId", nogrowthTemplate);
            }

            /*
             * Getting interface for each test and setting it into form for displaying on UI as a selected
             */
            ArrayList<String> interfaceList = AddTestDAOImpl.getHl7InterfaceDetails(testId);
            String[] interfaceNames = null;
            interfaceNames = populateListValuesTOArray(interfaceNames,interfaceList);
            atf.setHl7ExportInterface(interfaceNames);

        }

		List<BasicDynaBean> healthAuthorities = new GenericDAO("health_authority_master").listAll();
		request.setAttribute("healthAuthorities", healthAuthorities);
		
		BasicDynaBean centerMasterBean = new GenericDAO("hospital_center_master").findByKey("center_id", RequestContext.getCenterId());
		String centerHealthAuthority = (String)centerMasterBean.get("health_authority");
		request.setAttribute("healthAuthority", centerHealthAuthority); 
		
        //request.setAttribute("testDetails", js.serialize(testDetails));
        ArrayList testResults = AddTestDAOImpl.getTestResults(testId);
        for (Object object : testResults) {
        	if(object instanceof Hashtable) {
        		Hashtable resultMap = (Hashtable) object;
        		Map<String, TestObservations> testObservationsMap = AddTestDAOImpl.getResultLabelObservations(Integer.parseInt((String)resultMap.get("RESULTLABEL_ID")));
        		
        		for(BasicDynaBean healthAuthBean: healthAuthorities) {
        			if(healthAuthBean.get("health_authority") != centerHealthAuthority) {
        				TestObservations testObservations = testObservationsMap.get(healthAuthBean.get("health_authority"));
        				if(null != testObservations) {
	        				resultMap.put("ha_name_"+healthAuthBean.get("health_authority"), healthAuthBean.get("health_authority"));
	        				resultMap.put(healthAuthBean.get("health_authority")+"_result_units", testObservations.getUnits());
	        				resultMap.put(healthAuthBean.get("health_authority")+"_result_code", testObservations.getResult_code());
	        				resultMap.put(healthAuthBean.get("health_authority")+"_result_code_type", testObservations.getCode_type());
        				}
        			}
        		}
        		
        	}
		}
        request.setAttribute("testsRanges",
        		ConversionUtils.copyListDynaBeansToMap(ResultRangesDAO.listAllTestResultReferences(testId)));
        request.setAttribute("newEdit", "edit");
        request.setAttribute("method", "updateTestDetails");
        request.setAttribute("diagdepts", js.serialize(AddTestDAOImpl.getDiagDepartments()));
        request.setAttribute("reportformats",AddTestDAOImpl.getReportFormats(atf.getDdeptId()));
        request.setAttribute("testId", testId);
        request.setAttribute("orgId", orgId);
        request.setAttribute("orgName", orgName);
        request.setAttribute("insurance_category_id", bean.get("insurance_category_id"));
        request.setAttribute("serviceSubGroupsList", js.serialize(ServiceSubGroupDAO.getAllActiveServiceSubGroups()));
        request.setAttribute("hl7Interfaces", new GenericDAO("hl7_lab_interfaces").
                listAll(null, "status", "A"));
        request.setAttribute("codeTypesJSON",
                js.serialize(ConversionUtils.copyListDynaBeansToMap(
                        new GenericDAO("mrd_supported_codes").listAll(null, "code_category", "Observations"))));
        request.setAttribute("impressions", new GenericDAO("histo_impression_master").findAllByKey( "status", "A"));
        request.setAttribute("antibiotics", new GenericDAO("micro_abst_antibiotic_master").listAll());
        request.setAttribute("test_timestamp", new DiagnoDAOImpl().getCountFromDiagTimeStamp());
        request.setAttribute("methodologies", js.serialize(ConversionUtils.copyListDynaBeansToMap
        		(new GenericDAO("diag_methodology_master").findAllByKey("status", "I"))));

        request.setAttribute("results_json", js.deepSerialize(ConversionUtils.
				copyListDynaBeansToMap(DiagResultsCenterApplicabilityDAO.getResultsListForJson(testId))));
		request.setAttribute("centers", CenterMasterDAO.getAllCentersAndSuperCenterAsFirst());
		List<BasicDynaBean> centerwiseStandardTAT = AddTestDAOImpl.getCenterWiseTATs(testId);
		request.setAttribute("testTATs", centerwiseStandardTAT);
		request.setAttribute("allPackagesforthisOperationList", js.serialize(MasterItemInactiveValidationBO.getAllPackagesForInvestigation(testId)));
		request.setAttribute("allOrderPack", js.serialize(MasterItemInactiveValidationBO.getAllOrderPack(testId,"I")));
		
		List<BasicDynaBean> diagListBeans = new GenericDAO("ha_diagnosis").findAllByKey("test_id", testId);
		Map<String, String> haDiagnosisMap = new HashMap<String, String>();
		for(BasicDynaBean diagBean : diagListBeans) {
			haDiagnosisMap.put((String)diagBean.get("health_authority"), (String)diagBean.get("is_reportable"));
		}
		
		request.setAttribute("testResults", testResults);
		request.setAttribute("haReportable", haDiagnosisMap);
		request.setAttribute("reportableTo", "Reportable to Claim");
		
		List columns = new ArrayList<>();
		columns.add("description");
		columns.add("sr_id");
		
		Map<String, String> filterMap = new HashMap<String, String>();
		filterMap.put("status", "A");
		filterMap.put("type", "S");
		
		List specialReporting = new GenericDAO("special_reporting").listAll(columns, filterMap, "sr_id");
		request.setAttribute("specialReporting", specialReporting);
		
		
		return mapping.findForward("getAddTestScreen");
    }

    /*
     * Inserts one new test
     */
    public ActionForward insertTestDetails(ActionMapping mapping, ActionForm form,HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        if(isTokenValid(request,true)){
	        resetToken(request);
	        Map requestMap = request.getParameterMap();
	        String[] code_type = (String[])requestMap.get("code_type");
	        String[] result_code = (String[])requestMap.get("result_code");
	        String[] dataAllowed = (String[])requestMap.get("data_allowed");
	        String[] sourceIfList = (String[])requestMap.get("source_if_list");
	        String[] listSeverities = (String[])requestMap.get("list_severities");
	        String[] hl7_interface = (String[])requestMap.get("hl7_interface");
	        String[] method_Ids = (String[])requestMap.get("methodId");
	        String[] conductingRoles = request.getParameterValues("conductingRoleId");
	        String[] symptomsIds = request.getParameterValues("symptomsId");
	        String[] is_edited = (String[])requestMap.get("is_observations_edited");
	        
	        AddTestForm atf = (AddTestForm) form;
	        FlashScope scope = FlashScope.getScope(request);
	
	        request.setAttribute("loggedInCenter",RequestContext.getCenterId());
	
	        JSONSerializer js = new JSONSerializer().exclude("class");
	        Test test = new Test();
	        BeanUtils.copyProperties(test, atf); 
	        if(request.getParameter("clinical_information_form")!=null && request.getParameter("clinical_information_form").length()>0)
	        	test.setClinical_Information_Form(Integer.parseInt(request.getParameter("clinical_information_form")));
	        else
	        	test.setClinical_Information_Form(0);
			test.setConductingRoleIds(conductingRoles);
			test.setSymptomsIds(symptomsIds);
	        test.setUserName((String)request.getSession(false).getAttribute("userid"));
	        test.setCancerScreeningIds((String[])requestMap.get("cancer_screening_id"));
	        ArrayList<Result> results = new ArrayList<Result>();
	        boolean validExpr = true ;
	        Result r = null;
	        List<BasicDynaBean> checkBean = new GenericDAO("hl7_lab_interfaces").listAll(null, "status", "A");
	        List<BasicDynaBean> healthAuthorities = new GenericDAO("health_authority_master").listAll();

	        BasicDynaBean centerMasterBean = new GenericDAO("hospital_center_master").findByKey("center_id", RequestContext.getCenterId());
			String centerHealthAuthority = (String)centerMasterBean.get("health_authority");
	        
			ArrayList<TestObservations> addedTestObservations = new ArrayList<TestObservations>();
	        if (atf.getUnits() != null) {
	            for (int i=0; i<atf.getUnits().length-1; i++) {
	                String[] resultlabel_id = new String[atf.getUnits().length-1];
	                int resultlabelId = AddTestDAOImpl.getNextSequence();
	                resultlabel_id[i] = new Integer(resultlabelId).toString();
	                atf.setResultlabel_id(resultlabel_id);
	                if(is_edited[i].equalsIgnoreCase("Y")) {
	                    for(BasicDynaBean healthAuthBean: healthAuthorities) {
	            			if((!(healthAuthBean.get("health_authority").toString().equalsIgnoreCase(centerHealthAuthority)))) {
	            				if(CommonUtils.isNotEmpty(((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code_type"))[i]) 
	            						&& CommonUtils.isNotEmpty(((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code"))[i]) ) {
		            				TestObservations testObservations = new TestObservations(((String[])requestMap.get("ha_name_"+healthAuthBean.get("health_authority")))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code_type"))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code"))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_units"))[i],resultlabelId, (String)request.getSession(false).getAttribute("userid"));
		            				addedTestObservations.add(testObservations);
	            				}
	            			}
	                    }
                    }
	                if(checkBean != null && checkBean.size() > 0) {
	                	r = new Result(test.getTestId(),atf.getResultLabel()[i],atf.getResultLabelShort()[i],
	                			atf.getUnits()[i], atf.getOrder()[i],
	                			atf.getResultlabel_id()[i],atf.getExpression()[i],atf.getHl7_interface()[i]);
	                	r.setCode_type(code_type[i]);
	                	r.setResult_code(result_code[i]);
	                	r.setDataAllowed(dataAllowed[i]);
	                	r.setSourceIfList(sourceIfList[i]);
	                	r.setListSeverities(listSeverities[i]);
	                	r.setHl7_interface(hl7_interface[i]);
	                	r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
	                } else {
	                	r = new Result(test.getTestId(),atf.getResultLabel()[i],atf.getResultLabelShort()[i],
	                			atf.getUnits()[i], atf.getOrder()[i],
	                			atf.getResultlabel_id()[i],atf.getExpression()[i]);
	                	r.setCode_type(code_type[i]);
	                	r.setResult_code(result_code[i]);
	                	r.setDataAllowed(dataAllowed[i]);
	                	r.setSourceIfList(sourceIfList[i]);
	                	r.setListSeverities(listSeverities[i]);
	                	r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
	                }
	                results.add(r);
	            }
	        }
	        ArrayList<TestTemplate> templateList = new ArrayList<TestTemplate>();
	        String template[] = atf.getFormatName();
	        TestTemplate t = null;
	        if(template !=null){
	            for(int i=0;i<template.length;i++){
	                t = new TestTemplate();
	                t.setTestId(null);
	                t.setTemplateId(template[i]);
	                templateList.add(t);
	            }
	        }
	
	        TestBO bo = new TestBO();
	        StringBuilder msg = new StringBuilder();
	        boolean success = bo.addNewTest(test, results,templateList,msg,requestMap);
	        success = success && bo.updateTestObservations(addedTestObservations, new ArrayList<TestObservations>(), new ArrayList<TestObservations>());
	        ActionRedirect redirect = new ActionRedirect(mapping.findForward("showtest"));
	
	        if (success) {
	            //atf.reset(mapping, request);
	        	//code to save is_reportable for test
	            HealthAuthorityReportableMapping reportableMapping = new HealthAuthorityReportableMapping(requestMap, test.getTestId(), "ha_diagnosis");
	            reportableMapping.insertReportable();
	            
	            scope.success("New test has been added successfully....");
	            redirect.addParameter("testid",test.getTestId());
	            redirect.addParameter("orgId", request.getParameter("orgId"));
	            redirect.addParameter("testName",atf.getTestName());
	            redirect.addParameter("orgName", atf.getOrgName());
	            redirect.addParameter("serviceSubGroup", test.getServiceSubGroupId());
	            redirect.addParameter("insurance_category_id", atf.getInsurance_category_id());
	        } else {
	        	redirect = new ActionRedirect(mapping.findForward("addtest"));
	        	if(msg.toString().isEmpty())
	        		scope.error("Failed to save test details....");
	        	else
	        		scope.error(msg.toString());
	        }
	        redirect.addParameter(FlashScope.FLASH_KEY, scope.key());
	        return redirect;
		} else {
			ActionRedirect redirect = new ActionRedirect(mapping.findForward("addtest"));
			FlashScope scope = FlashScope.getScope(request);
			scope.error("Failed to Insert Test Details....");
			redirect.addParameter(FlashScope.FLASH_KEY, scope.key());
			return redirect;
		}
    }

    /*
     * Updates an existing test
     */
    public ActionForward updateTestDetails(ActionMapping mapping, ActionForm form,HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        AddTestForm atf = (AddTestForm) form;
        atf.setRoutineCharge("0");

        request.setAttribute("loggedInCenter",RequestContext.getCenterId());

        Map<String, String[]> requestMap = request.getParameterMap();
        String[] code_type = (String[])requestMap.get("code_type");
        String[] result_code = (String[])requestMap.get("result_code");
        String[] dataAllowed = (String[])requestMap.get("data_allowed");
        String[] sourceInList = (String[])requestMap.get("source_if_list");
        String[] list_severities = (String[])requestMap.get("list_severities");
        String[] hl7_interface = (String[])requestMap.get("hl7_interface");
        String[] method_Ids = (String[])requestMap.get("methodId");
        String[] prevMethodIds = (String[])requestMap.get("prevMethodId");
        String[] conductingRoles = request.getParameterValues("conductingRoleId");
        String[] cancerScreeningIds = request.getParameterValues("cancer_screening_id");
        String[] symptomsIds = request.getParameterValues("symptomsId");
        String[] health_autority_HAAD = (String[])requestMap.get("ha_name_HAAD");
        String[] HAAD_code_type = (String[])requestMap.get("HAAD_result_code_type");
        String[] HAAD_result_code = (String[])requestMap.get("HAAD_result_code");
        String[] HAAD_units = (String[])requestMap.get("HAAD_result_units");
        String[] health_autority_DHA = (String[])requestMap.get("ha_name_DHA");
        String[] DHA_code_type = (String[])requestMap.get("DHA_result_code_type");
        String[] DHA_result_code = (String[])requestMap.get("DHA_result_code");
        String[] DHA_units = (String[])requestMap.get("DHA_result_units");
        String[] health_autority_MOH = (String[])requestMap.get("ha_name_MOH");
        String[] MOH_code_type = (String[])requestMap.get("MOH_result_code_type");
        String[] MOH_result_code = (String[])requestMap.get("MOH_result_code");
        String[] MOH_units = (String[])requestMap.get("MOH_result_units");
        String[] is_edited = (String[])requestMap.get("is_observations_edited");

        
        Test test = new Test();

        //code to update is_reportable for test
        HealthAuthorityReportableMapping reportableMapping = new HealthAuthorityReportableMapping(requestMap, atf.getTestId(),"ha_diagnosis");
        reportableMapping.insertReportable();

        if (atf.getDiagCode() != null && atf.getDiagCode().equals(""))
            atf.setDiagCode(null);
        BeanUtils.copyProperties(test, atf);
        
        if(request.getParameter("clinical_information_form")!=null && request.getParameter("clinical_information_form").length()>0)
        	test.setClinical_Information_Form(Integer.parseInt(request.getParameter("clinical_information_form")));
        else
        	test.setClinical_Information_Form(0);
        
		test.setConductingRoleIds(conductingRoles);
		test.setSymptomsIds(symptomsIds);
		test.setCancerScreeningIds(cancerScreeningIds);
        test.setUserName((String)request.getSession(false).getAttribute("userid"));

        ArrayList<Result> addedResults = new ArrayList<Result>();
        ArrayList<Result> modifiedResults = new ArrayList<Result>();
        ArrayList<Result> deletedResults = new ArrayList<Result>();
        
        ArrayList<TestObservations> addedTestObservations = new ArrayList<TestObservations>();
        ArrayList<TestObservations> modifiedTestObservations = new ArrayList<TestObservations>();
        ArrayList<TestObservations> deletedTestObservations = new ArrayList<TestObservations>();
        
        boolean validExpr = true ;
        FlashScope flash = FlashScope.getScope(request);
        Result r= null;

        List<BasicDynaBean> healthAuthorities = new GenericDAO("health_authority_master").listAll();
		request.setAttribute("healthAuthorities", healthAuthorities);
		
		BasicDynaBean centerMasterBean = new GenericDAO("hospital_center_master").findByKey("center_id", RequestContext.getCenterId());
		String centerHealthAuthority = (String)centerMasterBean.get("health_authority");
		
        List<BasicDynaBean> checkBean = new GenericDAO("hl7_lab_interfaces").listAll(null, "status", "A");
        if (atf.getResultOp() != null) {
            for (int i=0; i<atf.getResultOp().length; i++) {

                String op = atf.getResultOp()[i];
             
                if (op.equals("add")) {
                    String[] resultlabel_id = new String[atf.getResultOp().length];
                    int resultlabelId = AddTestDAOImpl.getNextSequence();
                    resultlabel_id[i] = new Integer(resultlabelId).toString();
                    atf.setResultlabel_id(resultlabel_id);
                    if(is_edited[i].equalsIgnoreCase("Y")) {
	                    for(BasicDynaBean healthAuthBean: healthAuthorities) {
	            			if((!(healthAuthBean.get("health_authority").toString().equalsIgnoreCase(centerHealthAuthority)))) {
	            				if(CommonUtils.isNotEmpty(((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code_type"))[i]) 
	            						&& CommonUtils.isNotEmpty(((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code"))[i]) ) {
		            				TestObservations testObservations = new TestObservations(((String[])requestMap.get("ha_name_"+healthAuthBean.get("health_authority")))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code_type"))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code"))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_units"))[i],resultlabelId, (String)request.getSession(false).getAttribute("userid"));
		            				addedTestObservations.add(testObservations);
	            				}
	            			}
	                    }
                    }
                    if(checkBean != null && checkBean.size() > 0) {
                    	r = new Result(test.getTestId(), atf.getResultLabel()[i],atf.getResultLabelShort()[i],
                    			atf.getUnits()[i], atf.getOrder()[i],atf.getResultlabel_id()[i],atf.getExpression()[i],atf.getHl7_interface()[i]);
                    	r.setCode_type(code_type[i]);
                    	r.setResult_code(result_code[i]);
                    	r.setDataAllowed(dataAllowed[i]);
                    	r.setSourceIfList(sourceInList[i]);
                    	r.setListSeverities(list_severities[i]);
                    	r.setHl7_interface(hl7_interface[i]);
                    	r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
                    } else {
                    	r = new Result(test.getTestId(), atf.getResultLabel()[i],atf.getResultLabelShort()[i],
                    			atf.getUnits()[i], atf.getOrder()[i],atf.getResultlabel_id()[i],atf.getExpression()[i]);
                    	r.setCode_type(code_type[i]);
                    	r.setResult_code(result_code[i]);
                    	r.setDataAllowed(dataAllowed[i]);
                    	r.setSourceIfList(sourceInList[i]);
                    	r.setListSeverities(list_severities[i]);
                    	r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
                    }
                    addedResults.add(r);
                    

                } else if (op.equals("mod")) {
                	if(is_edited[i].equalsIgnoreCase("Y")) {
	                	for(BasicDynaBean healthAuthBean: healthAuthorities) {
	            			if((!(healthAuthBean.get("health_authority").toString().equalsIgnoreCase(centerHealthAuthority)))) {
	            				if(CommonUtils.isNotEmpty(((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code_type"))[i]) 
	            						&& CommonUtils.isNotEmpty(((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code"))[i]) ) {
		            				TestObservations testObservations = new TestObservations(((String[])requestMap.get("ha_name_"+healthAuthBean.get("health_authority")))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code_type"))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_code"))[i],
		            						((String[])requestMap.get(healthAuthBean.get("health_authority")+"_result_units"))[i],Integer.parseInt(atf.getResultlabel_id()[i]), (String)request.getSession(false).getAttribute("userid"));
		            				modifiedTestObservations.add(testObservations);
	            				}
	            			}
	                    }
                	}
                	if(checkBean != null && checkBean.size() > 0) {
                		r = new Result(test.getTestId(), atf.getResultLabel()[i],atf.getResultLabelShort()[i],
                				atf.getUnits()[i], atf.getOrder()[i],atf.getResultlabel_id()[i],atf.getExpression()[i],atf.getHl7_interface()[i]);
                		r.setCode_type(code_type[i]);
                		r.setResult_code(result_code[i]);
                		r.setDataAllowed(dataAllowed[i]);
                		r.setSourceIfList(sourceInList[i]);
                		r.setListSeverities(list_severities[i]);
                		r.setHl7_interface(hl7_interface[i]);
                		r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
                	//	r.setPrevMethodId((prevMethodIds[i] != null && !prevMethodIds[i].equals("")) ? Integer.parseInt(prevMethodIds[i]) : null);
                	} else {
                		r = new Result(test.getTestId(), atf.getResultLabel()[i],atf.getResultLabelShort()[i],
                				atf.getUnits()[i], atf.getOrder()[i],atf.getResultlabel_id()[i],atf.getExpression()[i]);
                		r.setCode_type(code_type[i]);
                		r.setResult_code(result_code[i]);
                		r.setDataAllowed(dataAllowed[i]);
                		r.setSourceIfList(sourceInList[i]);
                		r.setListSeverities(list_severities[i]);
                		r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
                	//	r.setPrevMethodId((prevMethodIds[i] != null && !prevMethodIds[i].equals("")) ? Integer.parseInt(prevMethodIds[i]) : null);
                	}
                	modifiedResults.add(r);

                } else if (op.equals("del")) {
                	
                	TestObservations testObservations = new TestObservations(null, null, null, null, Integer.parseInt(atf.getResultlabel_id()[i]),
                			(String)request.getSession(false).getAttribute("userid"));
                	if(checkBean != null && checkBean.size() > 0) {
                		r = new Result(test.getTestId(), atf.getResultLabel()[i],atf.getResultLabelShort()[i],
                                atf.getUnits()[i], atf.getOrder()[i],atf.getResultlabel_id()[i],atf.getExpression()[i],atf.getHl7_interface()[i]);
                		r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
                	//	r.setPrevMethodId((prevMethodIds[i] != null && !prevMethodIds[i].equals("")) ? Integer.parseInt(prevMethodIds[i]) : null);
                	} else {
                		r = new Result(test.getTestId(), atf.getResultLabel()[i],atf.getResultLabelShort()[i],
                                atf.getUnits()[i], atf.getOrder()[i],atf.getResultlabel_id()[i],atf.getExpression()[i]);
                		r.setMethodId((method_Ids[i] != null && !method_Ids[i].equals("")) ? Integer.parseInt(method_Ids[i]) : null);
                	//	r.setPrevMethodId((prevMethodIds[i] != null && !prevMethodIds[i].equals("")) ? Integer.parseInt(prevMethodIds[i]) : null);
                	}
                    deletedResults.add(r);
                    deletedTestObservations.add(testObservations);
                }
            }
        }
        ArrayList<TestTemplate> templateList = new ArrayList<TestTemplate>();
        String template[] = atf.getFormatName();
        TestTemplate t = null;
        if(template !=null){
            for(int i=0;i<template.length;i++){
                t = new TestTemplate();
                t.setTestId(test.getTestId());
                t.setTemplateId(template[i]);
                templateList.add(t);
            }
        }

        TestBO bo = new TestBO();
        StringBuilder msg = new StringBuilder();
        String status = test.getTestStatus();
        boolean success = bo.updateTestDetails(test, addedResults, modifiedResults, deletedResults,templateList,msg,requestMap);
        success = success && bo.updateTestObservations(addedTestObservations, modifiedTestObservations, deletedTestObservations);
        if (success) {
//        	if("I".equalsIgnoreCase(status))
//        		MasterItemInactiveValidationBO.deleteRecordFromDoctorFavorite(null, (String)test.getTestId(), "I");
        	flash.put("success", "Test details updated successfully");
            atf.reset(mapping, request);
        } else {
        	if(msg.toString().isEmpty())
        		flash.put("error", "Error updating test details");
        	else
        		flash.put("error", msg);
        }

        ActionRedirect redirect = new ActionRedirect(mapping.findForward("showtest"));
        redirect.addParameter("testid",test.getTestId());
        redirect.addParameter("orgId", request.getParameter("orgId"));
        redirect.addParameter("testName",atf.getTestName());
        redirect.addParameter(FlashScope.FLASH_KEY, flash.key());

        return redirect;
    }
    private void setAttributes(HttpServletRequest request) throws SQLException{
        JSONSerializer js = new JSONSerializer().exclude("class");
        request.setAttribute("diagdepts", js.serialize(AddTestDAOImpl.getDiagDepartments()));
        request.setAttribute("reportformats",AddTestDAOImpl.getReportFormats());
    }


    private static String[]  populateListValuesTOArray(String[] a,ArrayList<String>al){
        Iterator<String>  it = al.iterator();
        a = new String[al.size()];

        int i=0;
        while(it.hasNext()){
            a[i++]= it.next();
        }

        return a;
    }


    public ActionForward editTestCharges(ActionMapping mapping, ActionForm form,HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        AddTestForm atf = (AddTestForm) form;
        JSONSerializer js = new JSONSerializer().exclude("class");
        String orgId = request.getParameter("orgId");
        String testid = request.getParameter("testid");
        String chargeType = request.getParameter("chargeType");

        ArrayList<Hashtable<String,String>> testDetails = AddTestDAOImpl.getTestDetails(testid);
        Iterator<Hashtable<String,String>> it = testDetails.iterator();

        if(it.hasNext()){
            Hashtable<String,String> ht = it.next();
            atf.setTestName(ht.get("TEST_NAME"));
            atf.setTestId(testid);
            atf.setOrgId(orgId);
            atf.setChargeType(chargeType);
            BasicDynaBean bean = OrgMasterDAO.getOrgdetailsDynaBean(orgId);
            atf.setOrgName((String)bean.get("org_name"));
        }

        List<BasicDynaBean> list = AddTestDAOImpl.getOrgItemCode(orgId, testid);
        if (!list.isEmpty()){
            BasicDynaBean bean = list.get(0);
            atf.setOrgItemCode((String)bean.get("item_code"));
            atf.setApplicable((Boolean)bean.get("applicable"));
            atf.setCodeType((String)bean.get("code_type"));
        }

        List activeRateSheets = OrgMasterDAO.getActiveOrgIdNamesExcludeOrg(orgId);
        request.setAttribute("activeRateSheets", activeRateSheets);

        List<String> notApplicableRatePlans = AddTestDAOImpl.getTestNotApplicableRatePlans(testid, orgId);
        request.setAttribute("ratePlansNotApplicable", notApplicableRatePlans);

        List<BasicDynaBean> derivedRatePlanDetails = testChargesdao.getDerivedRatePlanDetails(orgId, testid);

        TestBO bo = new TestBO();
        Map map = bo.editTestCharges(orgId,testid);
        request.setAttribute("testid", testid);
        request.setAttribute("chargeMap", map);
        request.setAttribute("method", "updateTestCharges");
        request.setAttribute("testsList", js.serialize(AddTestDAOImpl.getTestsNamesAndIds()));
        request.setAttribute("codeType", atf.getCodeType());
        if(derivedRatePlanDetails.size()<0)
        	request.setAttribute("derivedRatePlanDetails", js.serialize(Collections.EMPTY_LIST));
        else
        	request.setAttribute("derivedRatePlanDetails", js.serialize(ConversionUtils.copyListDynaBeansToMap(derivedRatePlanDetails)));
        setAttributes(request);
        request.setAttribute("loggedInCenter",RequestContext.getCenterId());
        return mapping.findForward("getEditCharges");
    }

    public ActionForward updateTestCharges(ActionMapping mapping, ActionForm form,HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        AddTestForm atf = (AddTestForm) form;
        String testId = atf.getTestId();
        String orgId = atf.getOrgId();

        String bedType[] = atf.getBedTypes();
        Double regularcharge[] =atf.getRegularCharges();
        Double discount[] =atf.getDiscount();
        FlashScope flash = FlashScope.getScope(request);

        String[] derivedRateplanIds = request.getParameterValues("ratePlanId");
        String[] ratePlanApplicable = request.getParameterValues("applicable");

        ArrayList<TestCharge> al = new ArrayList<TestCharge>();

        TestCharge tc = null;
        for(int i=0;i<bedType.length;i++){
            tc = new TestCharge();
            tc.setBedType(bedType[i]);
            tc.setCharge(new BigDecimal(regularcharge[i]));
            tc.setDiscount(new BigDecimal(discount[i]));
            tc.setOrgId(orgId);
            tc.setPriority("R");
            tc.setTestId(testId);
            tc.setUserName((String)request.getSession(false).getAttribute("userid"));
            al.add(tc);
        }

        boolean stat = true;

        Connection con = null;
        try {

        	con = DBUtil.getConnection();
        	con.setAutoCommit(false);

        	// Reset Not Applicable first.
        	GenericDAO testOrgDAO = new GenericDAO("test_org_details");
        	Map<String, String> keys = new HashMap<String, String>();
        	keys.put("test_id", testId);
        	keys.put("org_id", orgId);

        	Map<String, Boolean> fields = new HashMap<String, Boolean>();
        	fields.put("applicable", true);
        	int result = testOrgDAO.update(con, fields, keys);
        	stat = (result > 0);

        }finally {
        	DBUtil.commitClose(con, stat);
        }

        TestBO bo = new TestBO();

        stat = stat && bo.updateTestCharge(al,testId, orgId, true, atf.getOrgItemCode(),
                atf.getCodeType());

        try {
	    	con = DBUtil.getConnection();
	    	con.setAutoCommit(false);
	        if(null != derivedRateplanIds && derivedRateplanIds.length > 0) {
	        	stat = stat && testChargesdao.updateOrgForDerivedRatePlans(con,derivedRateplanIds,ratePlanApplicable,testId, atf.getOrgItemCode(),
	                    atf.getCodeType());
	        	stat = stat && testChargesdao.updateChargesForDerivedRatePlans(con,orgId,derivedRateplanIds,bedType,
	        			regularcharge, testId, discount,ratePlanApplicable);
	        }
	        RateMasterDAO rdao = new RateMasterDAO();
			List<BasicDynaBean> allDerivedRatePlanIds = rdao.getDerivedRatePlanIds(orgId);
			if(null != allDerivedRatePlanIds) {
				testChargesdao.updateApplicableflagForDerivedRatePlans(con, allDerivedRatePlanIds, "diagnostics", "test_id",
						testId, "test_org_details", orgId);
			}
        }finally{
        	DBUtil.commitClose(con, stat);
        }

        if (stat) {
            flash.put("success", "Test charges updated successfully");
        } else {
            flash.put("error", "Error updating test charges");
        }
        ActionRedirect redirect = new ActionRedirect(mapping.findForward("showcharges"));
        redirect.addParameter(FlashScope.FLASH_KEY, flash.key());
        redirect.addParameter("orgId",orgId);
        redirect.addParameter("testid",testId);
        redirect.addParameter("testName",atf.getTestName());
        request.setAttribute("loggedInCenter",RequestContext.getCenterId());
        return redirect;
    }

    /*
     * Group Update: called from the main test list screen, updates the charges of all/selected
     * tests by a formula: +/- a certain amount or percentage,
     */
    public ActionForward groupUpdate(ActionMapping mapping, ActionForm form,HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        AddTestForm af = (AddTestForm) form;

        String orgId = af.getOrgId();
        String amtType = af.getAmtType();
        BigDecimal amount = af.getAmount();
        BigDecimal roundOff = af.getRoundOff();
        String updateTable = af.getUpdateTable();
        String userName = (String) request.getSession().getAttribute("userid");

        if (af.getIncType().equals("-"))
            amount = amount.negate();

        List<String> selectTests = null;
        if ( (af.getSelectTest() != null) && !af.getAllTests().equals("yes") )
            selectTests = Arrays.asList(af.getSelectTest());

        List<String> bedTypes = null;
        if ( (af.getSelectBedType() != null)  && !af.getAllBedTypes().equals("yes") )
            bedTypes = Arrays.asList(af.getSelectBedType());

        Connection con = null;
        boolean success = false;

        try {
            con = DBUtil.getConnection();
            con.setAutoCommit(false);
            AddTestDAOImpl dao = new AddTestDAOImpl(con);
            dao.groupIncreaseTestCharges(orgId, bedTypes, selectTests, amount, amtType.equals("%"),
            		roundOff,updateTable, (String)request.getSession(false).getAttribute("userid"));
            dao.updateDiagnosticTimeStamp();
            success = true;
            if(success)con.commit();
        } finally {
            DBUtil.commitClose(con, success);
        }

        if(success) testChargesdao.updateChargesForDerivedRatePlans(orgId, userName, "tests", false);

        FlashScope flash = FlashScope.getScope(request);
        if (success)
            flash.put("success", "Charges updated successfully");
        else
            flash.put("error", "Error updating charges");

        ActionRedirect redirect = new ActionRedirect(request.getHeader("Referer").
                replaceAll("&" + FlashScope.FLASH_KEY + "=\\d*", ""));
        redirect.addParameter(FlashScope.FLASH_KEY, flash.key());

        return redirect;
    }

    /*
     * Export the set of test charges for each bed type as a XLS. Called from the main tests
     * list screen.
     */

    private static ChargesImportExporter importExporter;

    static {

        importExporter = new ChargesImportExporter("diagnostics", "test_org_details", "diagnostic_charges",
                "diagnostics_departments", "test_id", "ddept_id", "ddept_name",
				new String[] {"test_name", "status"}, new String[]{"Test Name", "Status"},
                new String[] {"applicable", "item_code"}, new String[]{"Applicable", "Code"},
                new String[] {"charge", "discount"}, new String[]{ "Charge", "Discount"}
				);

		importExporter.setItemWhereFieldKeys(new String[] {"test_id"});
	    importExporter.setOrgWhereFieldKeys(new String[] {"test_id", "org_id"});
	    importExporter.setChargeWhereFieldKeys(new String[] {"test_id", "org_name"});
		importExporter.setMandatoryFields(new String[] {"test_name"});
		importExporter.setItemName("test_name");
		importExporter.setChgTabOrgColName("org_name");
    }

    public ActionForward exportTestChargesCSV(ActionMapping m, ActionForm f, HttpServletRequest req,
            HttpServletResponse res) throws SQLException, java.io.IOException {

        String orgId = req.getParameter("orgId");
        String orgName = (String)OrgMasterDAO.getOrgdetailsDynaBean(orgId).get("org_name");
        HSSFWorkbook workbook = new HSSFWorkbook();
        HSSFSheet workSheet = workbook.createSheet("TEST CHARGES");
        importExporter.exportCharges(orgId, workSheet, null, "A");

        res.setHeader("Content-type", "application/vnd.ms-excel");
        res.setHeader("Content-disposition","attachment; filename="+"\"TestRates_" + orgName + ".xls\"");
        res.setHeader("Readonly", "true");

        java.io.OutputStream outputStream = res.getOutputStream();
        workbook.write(outputStream);
        outputStream.flush();
        outputStream.close();

        return null;

    }

    /*
     * Import a XLS file to update a set of test charges.
     */
    public ActionForward importTestChargesXLS(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws SQLException, IOException,Exception {

        String orgId = request.getParameter("org_id");
        String userName = (String)request.getSession().getAttribute("userid");
        AddTestForm suForm = (AddTestForm) form;
        HSSFWorkbook workBook = new HSSFWorkbook(suForm.getXlsChargeFile().getInputStream());
        HSSFSheet sheet = workBook.getSheetAt(0);
        this.errors = new StringBuilder();

        FlashScope flash = FlashScope.getScope(request);
        String referer = request.getHeader("Referer");
        referer = referer.replaceAll("&" + FlashScope.FLASH_KEY + "=\\d*", "");
        ActionRedirect redirect = new ActionRedirect(referer);
        String userId = (String) request.getSession().getAttribute("userid");
        Connection con =  DBUtil.getConnection();
        AddTestDAOImpl dao = new AddTestDAOImpl(con);
        /*
         * Keep a backup of the rates for safety: TODO: be able to checkpoint and revert
         * to a previous version if required.
         */
        dao.backupCharges(orgId, userId);
        if (con != null)
        	con.close();
        importExporter.setUseAuditLogHint(true);
        importExporter.importCharges(true, orgId, sheet, userId, this.errors);

        testChargesdao.updateChargesForDerivedRatePlans(orgId,userName,"tests",true);

        if (this.errors.length() > 0)
            flash.put("error", this.errors);
        else
			flash.put("info", "File successfully uploaded");

        redirect.addParameter(FlashScope.FLASH_KEY, flash.key());
        return redirect;
    }


    private static final String GET_DOCTOR_SHARE_FOR_SERVICE = "SELECT sp.* FROM service_payment_master sp" +
    " WHERE sp.service_id=? AND sp.doc_id=? and sp.status = 'A' ";

    public static List<BasicDynaBean> getDoctorSharesForServices(String serviceId, String doctorId)throws SQLException {
        Connection con = null;
        PreparedStatement ps = null;
        try{
            con = DBUtil.getReadOnlyConnection();
            ps = con.prepareStatement(GET_DOCTOR_SHARE_FOR_SERVICE);
            ps.setString(1,serviceId );
            ps.setString(2,doctorId);
            return DBUtil.queryToDynaList(ps);
        }finally{
            DBUtil.closeConnections(con, ps);
        }
    }


    public ActionForward exportTestDetailsToXls(ActionMapping m, ActionForm f, HttpServletRequest req,
            HttpServletResponse res) throws SQLException, java.io.IOException {

    	List<String> diagColumnNames = Arrays.asList(new String[]
 		                           {"test_id","Test Name","Sample Needed",
 						   		   "Dept Name","Type Of Specimen","Conduct In Report Format","Status",
 						   		   "Service Group Name","Service Sub Group Name",
 						   		   "Conduction Applicable","Conducting Doctor Mandatory","Unit Charge","Results Entry Applicable","Alias","STAT","Consent Required","Cross Match Test"});

 		List<String> testResulLabelsColumnNames = Arrays.asList(new String[]
 		                           {"resultlabel_id","Test Name","Dept Name","Resultlabel","Center","Methodology",
 								   "Units","Display Order"});

 		List<String> testTemplateColumnNames = Arrays.asList(new String[] {"Test Name", "Dept Name", "Format Name"});
 		List<String> testMicroTemplateColumnNames = Arrays.asList(new String[] {"Test Name", "Dept Name", "No Growth Template Name"});

 		HSSFWorkbook workbook = new HSSFWorkbook();
 		// Sheet for diagnostic table
 		HSSFSheet diagWorkSheet = workbook.createSheet("DIAGNOSTICS");
 		List<BasicDynaBean> testDetails = AddTestDAOImpl.getAllTestDetails();
 		Map<String, List> columnNamesMap = new HashMap<String, List>();
 		columnNamesMap.put("mainItems", diagColumnNames);
 		HSSFWorkbookUtils.createPhysicalCellsWithValues(testDetails, columnNamesMap, diagWorkSheet, true);

 		// Sheet for test_results_master table
 		HSSFSheet testResultLabelworksheet = workbook.createSheet("TEST RESULTS");
 		List<BasicDynaBean> testResultLabelDetails = AddTestDAOImpl.getAllTestResultLabelDetails();
 		Map<String, List> columnNamesMap2 = new HashMap<String, List>();
 		columnNamesMap2.put("mainItems", testResulLabelsColumnNames);
 		HSSFWorkbookUtils.createPhysicalCellsWithValues(testResultLabelDetails,
 				columnNamesMap2, testResultLabelworksheet, true);

 		//Sheet for test_template_master table
 		HSSFSheet testTemplateWorkSheet = workbook.createSheet("TEST TEMPLATE");
 		List<BasicDynaBean> testTemplateDetails = AddTestDAOImpl.getTestTemplates();
 		Map<String, List> columnNamesMap3 = new HashMap<String, List>();
 		columnNamesMap3.put("mainItems", testTemplateColumnNames);
 		HSSFWorkbookUtils.createPhysicalCellsWithValues(testTemplateDetails, columnNamesMap3,
 				testTemplateWorkSheet, false);

 		//Sheet for test_no_growth_template_master table
 		HSSFSheet microTemplateWorkSheet = workbook.createSheet("MICRO TEMPLATE");
 		List<BasicDynaBean> microTemplateDetails = AddTestDAOImpl.getTestMicroTemplates();
 		Map<String, List> columnNamesMap4 = new HashMap<String, List>();
 		columnNamesMap4.put("mainItems", testMicroTemplateColumnNames);
 		HSSFWorkbookUtils.createPhysicalCellsWithValues(microTemplateDetails, columnNamesMap4,
 				microTemplateWorkSheet, false);

 		res.setHeader("Content-type", "application/vnd.ms-excel");
 		res.setHeader("Content-disposition","attachment; filename=TestDefinationDetails.xls");
 		res.setHeader("Readonly", "true");
 		java.io.OutputStream os = res.getOutputStream();
 		workbook.write(os);
 		os.flush();
 		os.close();

 		return null;
 	}

 	public static DetailsImportExporter detailsImporExp;

 	static {
 		detailsImporExp = new DetailsImportExporter("diagnostics", "test_org_details", "diagnostic_charges");

 	}

    public ActionForward importTestDetailsFromXls(ActionMapping mapping, ActionForm form,
    		HttpServletRequest request,HttpServletResponse response) throws java.io.IOException, SQLException{


    	AddTestForm serviceForm = (AddTestForm) form;
		ByteArrayInputStream byteStream = new ByteArrayInputStream(serviceForm.getXlsTestFile().getFileData());
		HSSFWorkbook workBook = new HSSFWorkbook(byteStream);
		HSSFSheet sheet = workBook.getSheetAt(0);
		String userName = (String)request.getSession(false).getAttribute("userid");

		this.errors = new StringBuilder();
		Connection con = null;
		boolean success = false;
		Map<String, String> aliasMap = new HashMap<String, String>();

		aliasMap.put("test name", "test_name");
		aliasMap.put("sample needed", "sample_needed");
		aliasMap.put("type of specimen", "type_of_specimen");
		aliasMap.put("status", "status");
		aliasMap.put("code", "diag_code");
		aliasMap.put("conduct in report format", "conduction_format");
		aliasMap.put("conduction applicable", "conduction_applicable");
		aliasMap.put("dept name", "ddept_id");
		aliasMap.put("conducting doctor mandatory", "conducting_doc_mandatory");
		aliasMap.put("service sub group name", "service_sub_group_name");
		aliasMap.put("service group name", "service_group_name");
		aliasMap.put("unit charge", "unit charge") ;
		aliasMap.put("alias", "diag_code") ;
		aliasMap.put("results entry applicable", "results_entry_applicable") ;
		aliasMap.put("stat", "stat") ;
		aliasMap.put("consent required", "consent_required");
		aliasMap.put("cross match test", "cross_match_test");

		List<String> mandatoryList = Arrays.asList("test_name", "status", "ddept_id", "service_sub_group_name",
				"sample_needed","service_group_name","stat");

		String referer = request.getHeader("Referer");
		referer = referer.replaceAll("&" + FlashScope.FLASH_KEY + "=\\d*", "");
		ActionRedirect redirect = new ActionRedirect(referer);
		FlashScope flash = FlashScope.getScope(request);

		importTestDetails(sheet, aliasMap, mandatoryList, errors, userName);

		importTestResults(workBook.getSheetAt(1), errors);

		importTestTemplates(workBook.getSheetAt(2), errors);

		if (this.errors.length() > 0)
			flash.put("error", this.errors);
		else
			flash.put("info", "File successfully uploaded");
		redirect.addParameter(FlashScope.FLASH_KEY, flash.key());
		return redirect;

	}

    
    public ActionForward getAllergenTest(ActionMapping mapping, ActionForm form,
    		HttpServletRequest request, HttpServletResponse response) throws Exception {
    	request.setAttribute("availableAllergen", AddTestDAOImpl.getAllergenAvailable(request.getParameter("test_id")));
    	request.setAttribute("selectedAllergen", AddTestDAOImpl.getAllergenSelected(request.getParameter("test_id")));
    	return mapping.findForward("allergen_test");
    }
    
	public ActionRedirect insertOrUpdateAllergen(ActionMapping mapping, ActionForm form,
    		HttpServletRequest request, HttpServletResponse response) throws Exception {
		if(request.getParameter("test_id")==null ||request.getParameter("test_id").toString().trim().length()<1 || request.getParameter("allergen_id")==null)
			return null;
    	String testId = request.getParameter("test_id");
    	String[] allergnId = request.getParameterValues("allergen_id");
    	if(allergnId==null || allergnId.length<0)
    		return null;
		GenericDAO dao = new GenericDAO("diagnostics_allergen");
		Connection con = DBUtil.getConnection();
		con.setAutoCommit(false);
		boolean status = true;
		try {
			dao.delete(con, "test_id", testId);
			for(String allergn:allergnId) {
				if(allergn.trim()!= "") {
					BasicDynaBean insertBean = dao.getBean();
					insertBean.set("test_id", testId);
					insertBean.set("allergen_id", (allergn!=null && !allergn.isEmpty())?BigDecimal.valueOf(Long.valueOf(allergn)):0);
					status = (status) ? dao.insert(con, insertBean) : status; 
				}
			}
		} finally {
			if (status) {
				con.commit();
			} else {
				con.rollback();
			}
			con.close();
		}
		ActionRedirect redirect = new ActionRedirect(mapping.findForwardConfig("update"));
		redirect.addParameter("test_id", testId);
		redirect.addParameter("test_name", request.getParameter("test_name"));
    	return redirect;
    }
    
    
	private void importTestDetails(HSSFSheet sheet, Map aliasUnmsToDBnmsMap,
			List<String> mandatoryFields, StringBuilder errors, String userName) throws SQLException, IOException {

		Iterator rowIterator = sheet.rowIterator();
		HSSFRow row1 = (HSSFRow)rowIterator.next();
		String sheetName = sheet.getSheetName();
		String userNameWithHint = userName + ":XL IMPORT";
		this.errors = errors;

		List exceptFields = Arrays.asList(new String[] {"service_group_name", "service_sub_group_name", "unit charge", "type_of_specimen"});

		GenericDAO mainTableDAO = new GenericDAO("diagnostics");
		BasicDynaBean mainBean = mainTableDAO.getBean();
		List<BasicDynaBean> orgList = new OrgMasterDAO().getAllOrgIdNames();
		List<String> bedTypes=BedMasterDAO.getUnionOfBedTypes();

		row1.getLastCellNum();
		String[] headers = new String[row1.getLastCellNum()];
		String[] xlHeaders = new String[row1.getLastCellNum()];

		detailsImporExp.setTableDBName("test_name");
		detailsImporExp.setDeptName("ddept_id");


		for (int i=0; i<headers.length; i++) {

			HSSFCell cell = row1.getCell(i);
			if (cell == null)
				headers[i] = null; /*putting null values, if found*/
			else {

				String header = cell.getStringCellValue().toLowerCase();
				String dbName = (String) (aliasUnmsToDBnmsMap.get(header) == null ? header : aliasUnmsToDBnmsMap.get(header));
				headers[i] = dbName;
				xlHeaders[i] = header;


				if (mainBean.getDynaClass().getDynaProperty(dbName) == null && !exceptFields.contains(dbName)) {
					addError(0, "Unknown header found in header "+dbName +" in the sheet "+sheetName);
					headers[i] = null;
					xlHeaders[i] = null;
				}

			}

		}

		for (String mfield : mandatoryFields) {
			if (!Arrays.asList(headers).contains(mfield)) {
				addError(0, "Mandatory field "+ mfield + " is missing cannot process further in the sheet "+sheetName);
				return;
			}
		}

		GenericDAO diagDao = new GenericDAO("diagnostics");
		GenericDAO orgDAO = new GenericDAO("test_org_details");
		GenericDAO chargeDAO = new GenericDAO("diagnostic_charges");
		BasicDynaBean tableBean = diagDao.getBean();
		BasicDynaBean chgBean = chargeDAO.getBean();
		BasicDynaBean orgBean = orgDAO.getBean();
		BasicDynaBean itemBean = null;
		Map deptMap = AddTestDAOImpl.getDiagDepData();
		List<String> inactiveBedList = detailsImporExp.getInactiveBeds();

nxtLine:while(rowIterator.hasNext()) {
			HSSFRow row = (HSSFRow)rowIterator.next();
			int lineNumber = row.getRowNum()+1;
			itemBean = diagDao.getBean();
			Map<String, String> keys = null;
			String operation = "update";
			String newId = null;
			String itemId = null;
			String itemName = null;
			Object itemDept = null;
			String beanId = null;
			Object grpId = null;
			String subGrpName = null;
			BasicDynaBean existOrNot = null;
			BasicDynaBean subGrpBean = null;
			boolean lineHasErrors = false;
			String sampleNeeded = null;
			String typeOfSample = null;
			String reportFormat = null;
			BigDecimal unitCharge = null;
			String stat = null;


nxtCell:	for (int j=0; j<headers.length; j++) {


				if (headers[j] == null)
					continue nxtCell;
				Object cellVal = null;
				DynaProperty property = null;

				HSSFCell rowcell = row.getCell(j);
				property = tableBean.getDynaClass().getDynaProperty(headers[j]);
				try {
				if (rowcell != null && !rowcell.equals("") && !exceptFields.contains(headers[j])) {

					/*check the id*/
					if (headers[j].equals("test_id")) {

						itemId = rowcell.getStringCellValue();
						if (itemId == null)
							operation = "insert";
						continue nxtCell;

					} else if (headers[j].equals("ddept_id")) {
						String exlDbName = rowcell.getStringCellValue();
						cellVal = deptMap.get(exlDbName);
						itemDept = cellVal;
						if (cellVal == null) {
							addError(lineNumber, "Department "+exlDbName+" not exist in the sheet "+sheetName);
							lineHasErrors = true;
							/*through error that dept not exist*/
						}
					} else {
						Class type = property.getType();
						if (type == java.lang.String.class) {
							cellVal = rowcell.getStringCellValue();
						} else if (type == java.lang.Boolean.class) {
							cellVal = rowcell.getBooleanCellValue();
						} else if (type == java.math.BigDecimal.class) {
							cellVal = rowcell.getNumericCellValue();
						}
					}

				}
				if (headers[j].equals("test_id") && cellVal == null) {
					operation = "insert";
				} else if (headers[j].equals("test_name")) {
					itemName = (String)cellVal;
					itemBean.set(headers[j], cellVal);

				} else if (headers[j].equals("service_group_name") ) {
					if (rowcell == null || rowcell.equals("")) {
						addError(lineNumber, "service_group_name should not be null in the sheet "+sheetName);
						lineHasErrors = true;
						continue nxtCell;
					}
					cellVal = rowcell.getStringCellValue();
					grpId = detailsImporExp.getGrpId(cellVal.toString());
					if (grpId == null) {
						addError(lineNumber, "Service Group Id not exist in the sheet "+sheetName);
						lineHasErrors = true;
					}
					continue nxtCell;
				} else if (headers[j].equals("service_sub_group_name")) {
					if (rowcell == null || rowcell.equals("")) {
						addError(lineNumber, "service_sub_group_name should not be null in the sheet "+sheetName);
						lineHasErrors = true;
						continue nxtCell;
					}
					subGrpName = rowcell.getStringCellValue();
					continue nxtCell;
				} else if (mandatoryFields.contains(headers[j]) && cellVal == null) {
					addError(lineNumber, headers[j] +" should not be null in the sheet "+sheetName);
					lineHasErrors = true;
					continue nxtCell;

				} else if (headers[j].equals("sample_needed") || headers[j].equals("type_of_specimen")) {
					if (headers[j].equals("sample_needed")) {
						sampleNeeded = rowcell.getStringCellValue();
						itemBean.set("sample_needed", sampleNeeded);
					}
					else {
						if (rowcell == null)
							continue nxtCell;
						else {
							typeOfSample = rowcell.getStringCellValue();
							itemBean.set("type_of_specimen", typeOfSample);
						}
					}

				} else if (headers[j].equals("conduction_format")) {
						reportFormat = rowcell.getStringCellValue();
						itemBean.set("conduction_format", reportFormat);

				} else if (headers[j].equals("unit charge")) {
					if (operation.equals("insert"))
						unitCharge = new BigDecimal(rowcell.getNumericCellValue());

				}else if (headers[j].equals("stat") && cellVal.equals("")) {
					stat = rowcell.getStringCellValue();
					if (stat == null || stat.equals("")) {
						addError(lineNumber, " STAT value should not be empty in the sheet "+sheetName);
						lineHasErrors = true;
					}
				}else {
					itemBean.set(headers[j], ConvertUtils.convert(cellVal, property.getType()));

				}
				} catch (Exception ex) {

					if (property != null) {
						addError(lineNumber, "Conversion error: Cell value" +
							" could not be converted to "+ property.getType() +" below headers of "+headers[j]+" in the sheet "+sheetName);
						lineHasErrors = true;
					} else {
						addError(lineNumber, "Conversion error: Cell value" +
								" could not be converted to class java.lang.String below headers of "+headers[j]+" in the sheet "+sheetName);
						lineHasErrors = true;
					}
					continue;	/*next cell*/
				}
			}
			if (itemName != null && itemDept != null)
				existOrNot = detailsImporExp.getBean(itemName, itemDept, "A");
			if (existOrNot != null)
				beanId = (String)existOrNot.get("test_id");
			if (operation.equals("update") && existOrNot != null) {
				if (!itemId.equals(beanId)) {
					addError(lineNumber, "Duplicate entry cannot updated in the sheet "+sheetName);
					lineHasErrors = true;
				}
			} else {
				if (beanId != null) {
					addError(lineNumber, "Duplicate entry cannot inserted in the sheet "+sheetName);
					lineHasErrors = true;
				}
			}
			if (grpId != null && subGrpName != null) {

				subGrpBean = detailsImporExp.getSubGrpInf(Integer.parseInt(grpId.toString()), subGrpName);
				if (subGrpBean == null) {
					addError(lineNumber, "Group and Subgroup is not matching in the sheet "+sheetName);
					lineHasErrors = true;
				} else {
					itemBean.set("service_sub_group_id", subGrpBean.get("service_sub_group_id"));
				}
			}

			if (lineHasErrors)
				continue nxtLine;

			/* updating or inserting part */
			Connection con = null;
			boolean success = false;

			try {
				/*get the diag code*/

				String orderAlias = AddTestDAOImpl.getOrderAlias("Diag", itemDept.toString(), grpId.toString(),
						subGrpBean.get("service_sub_group_id").toString());

				con = DBUtil.getReadOnlyConnection();
				con.setAutoCommit(false);

				if (operation.equals("update")) {
					itemBean.set("username", userNameWithHint);
					keys = new HashMap<String, String>();
					keys.put("test_id", itemId);

					success = diagDao.update(con, itemBean.getMap(), keys) > 0;

					if (success)
						con.commit();
/*insert*/		} else {
					newId = AutoIncrementId.getNewIncrId("Test_ID", "Diagnostics", "Diagnostic");
					itemBean.set("test_id", newId);
					itemBean.set("diag_code", orderAlias);
					success = diagDao.insert(con, itemBean);

					/*insert org details*/
					for (BasicDynaBean org : orgList) {

						orgBean.set("test_id", newId);
						orgBean.set("org_id", org.get("org_id"));
						orgBean.set("applicable", true);
						success &= orgDAO.insert(con, orgBean);

					}

					for (BasicDynaBean org : orgList) {
						for (String bedName : bedTypes) {

							chgBean.set("test_id", newId);
							chgBean.set("org_name", org.get("org_id"));
							chgBean.set("bed_type", bedName);
							chgBean.set("priority", "R");
							chgBean.set("username", userName);

							for (String chg : new String[] {"charge"}) {
								chgBean.set(chg, unitCharge);
							}
							success &= chargeDAO.insert(con, chgBean);

						}
					}

					if (!inactiveBedList.isEmpty()) {
						for (BasicDynaBean org : orgList) {
							for (String bedName : inactiveBedList) {
								chgBean.set("test_id", newId);
								chgBean.set("org_name", org.get("org_id"));
								chgBean.set("bed_type", bedName);
								chgBean.set("priority", "R");
								chgBean.set("username", userName);

								for (String chg : new String[] {"charge"}) {
									chgBean.set(chg, unitCharge);
								}
								success &= chargeDAO.insert(con, chgBean);
							}
						}
					}

				}
			} finally {
				DBUtil.commitClose(con, success);
			}
		}


	}

	private void importTestResults(HSSFSheet sheet, StringBuilder errors) throws SQLException, IOException {


		Iterator rowIterator = sheet.rowIterator();
		HSSFRow row1 = (HSSFRow)rowIterator.next();
		String sheetName = sheet.getSheetName();

		this.errors = errors;
		Map<String, String> aliasUnmsToDBnmsMap = new HashMap<String, String>();
		aliasUnmsToDBnmsMap.put("reference ranges", "reference_ranges");
		aliasUnmsToDBnmsMap.put("units", "units");
		aliasUnmsToDBnmsMap.put("display order", "display_order");
		aliasUnmsToDBnmsMap.put("resultlabel", "resultlabel");
		aliasUnmsToDBnmsMap.put("center", "center_name");
		aliasUnmsToDBnmsMap.put("resultlabel_id", "resultlabel_id");
		aliasUnmsToDBnmsMap.put("test name", "test_name");
		aliasUnmsToDBnmsMap.put("dept name", "ddept_name");
		aliasUnmsToDBnmsMap.put("methodology", "method_id");

		List<String> exceptFields = Arrays.asList(new String[] {"test_name", "ddept_name", "method_id", "center_name"});
		Map<Integer, String> testResultIds = AddTestDAOImpl.getTestResultIds();

		GenericDAO mainTableDAO = new GenericDAO("test_results_master");
		GenericDAO diagMethodologyDAO = new GenericDAO("diag_methodology_master");
		BasicDynaBean mainBean = mainTableDAO.getBean();
		List<String> mandatoryFields = Arrays.asList(new String[] {"test_name", "ddept_name", "center_name"});
		row1.getLastCellNum();
		String[] headers = new String[row1.getLastCellNum()];
		String[] xlHeaders = new String[row1.getLastCellNum()];
		List<String> columns = Arrays.asList(new String[] {"test_id","resultlabel", "method_id"});
		Map<String, Object> identifiers = new HashMap<String, Object>();


		for (int i=0; i<headers.length; i++) {

			HSSFCell cell = row1.getCell(i);
			if (cell == null)
				headers[i] = null; /*putting null values, if found*/
			else {

				String header = cell.getStringCellValue().toLowerCase();
				String dbName = (String) (aliasUnmsToDBnmsMap.get(header) == null ? header : aliasUnmsToDBnmsMap.get(header));
				headers[i] = dbName;
				xlHeaders[i] = header;


				if (mainBean.getDynaClass().getDynaProperty(dbName) == null && !exceptFields.contains(dbName)) {
					addError(0, "Unknown header found in header "+dbName+" in the sheet "+sheetName);
					headers[i] = null;
					xlHeaders[i] = null;
				}

			}

		}

		for (String mfield : mandatoryFields) {
			if (!Arrays.asList(headers).contains(mfield)) {
				addError(0, "Mandatory field "+ mfield + " is missing cannot process further in the sheet "+sheetName);
				return;
			}
		}


		GenericDAO resultDao = new GenericDAO("test_results_master");
		GenericDAO resultsCenterDAO = new GenericDAO("test_results_center");
		BasicDynaBean tableBean = resultDao.getBean();
		BasicDynaBean itemBean = null;
		BasicDynaBean resultsCenterBean = null;
		List<BasicDynaBean> centerIdsList = null;
		Map deptMap = AddTestDAOImpl.getDiagDepData();
		Map centerMap = AddTestDAOImpl.getAvailableCenters();


nxtLine:while(rowIterator.hasNext()) {
			HSSFRow row = (HSSFRow)rowIterator.next();
			int lineNumber = row.getRowNum()+1;
			itemBean = resultDao.getBean();
			resultsCenterBean = resultsCenterDAO.getBean();

			Map<String, Object> keys = null;
			String operation = "update";
			int newId = 0;
			Double	itemId = null;
			String itemName = null;
			String commaSeparatedCenterNames = null;
			String[] centerNames = null;
			List<String> insertCenterList = new ArrayList<String>();
			List<String> savedCenters = null;
			Object itemDept = null;
			String beanId = null;
			BasicDynaBean existOrNot = null;
			boolean lineHasErrors = false;

nxtCell:	for (int j=0; j<headers.length; j++) {


				if (headers[j] == null)
					continue nxtCell;
				Object cellVal = null;
				DynaProperty property = null;

				HSSFCell rowcell = row.getCell(j);
				property = tableBean.getDynaClass().getDynaProperty(headers[j]);
				try {
				if (rowcell != null && !rowcell.equals("") && !exceptFields.contains(headers[j])) {

					/*check the id*/
					if (headers[j].equals("resultlabel_id")) {

						itemId = rowcell.getNumericCellValue();
						//int val = itemId;
						if (itemId == null)
							operation = "insert";
						continue nxtCell;

					} else if (headers[j].equals("reference_ranges")) {
						switch (rowcell.getCellType()) {
							case HSSFCell.CELL_TYPE_NUMERIC: {
								cellVal = rowcell.getNumericCellValue();
								break;
							}

							case HSSFCell.CELL_TYPE_STRING: {
								cellVal = rowcell.getStringCellValue();
								break;
							}
						}

					} else {
						Class type = property.getType();
						if (type == java.lang.String.class) {
							cellVal = rowcell.getStringCellValue();
						} else if (type == java.lang.Boolean.class) {
							cellVal = rowcell.getBooleanCellValue();
						} else if (type == java.math.BigDecimal.class) {
							cellVal = rowcell.getNumericCellValue();
						} else if (type == java.lang.Integer.class) {
							cellVal = rowcell.getNumericCellValue();
						}
					}

				}
				if (headers[j].equals("resultlabel_id") && cellVal == null) {
					operation = "insert";
				} else if (headers[j].equals("test_name")) {

					itemName = rowcell.getStringCellValue();
				} else if (headers[j].equals("center_name")) {
					commaSeparatedCenterNames = rowcell.getStringCellValue();
					centerNames = commaSeparatedCenterNames.split(",");
					for(int i=0; i<centerNames.length; i++) {
						cellVal = centerMap.get(centerNames[i].trim());
						if (cellVal == null) {
							addError(lineNumber, "Center "+centerNames[i]+" not exist in the sheet "+sheetName);
							//lineHasErrors = true;
						}
					}
					centerIdsList = AddTestDAOImpl.getCenterDetails(centerNames);
					if(operation.equals("update")) {
						savedCenters = AddTestDAOImpl.getSavedCenters(itemId.intValue());
						for (int i=0; i<centerNames.length; i++) {
							if (centerMap.get(centerNames[i].trim()) != null) {
								if (savedCenters.contains(centerNames[i].trim())) {
									savedCenters.remove(centerNames[i].trim());
								} else {
									if(!insertCenterList.contains(centerNames[i].trim()))
										insertCenterList.add(centerNames[i].trim());
								}
							}
						}
					}

				} else if (headers[j].equals("ddept_name")) {
					String exlDbName = rowcell.getStringCellValue();
					cellVal = deptMap.get(exlDbName);
					itemDept = cellVal;
					if (cellVal == null) {
						addError(lineNumber, "Department "+exlDbName+" not exist in the sheet "+sheetName);
						lineHasErrors = true;
						/*through error that dept not exist*/
					}

				} else if(headers[j].equalsIgnoreCase("method_id")) {
					String methodology = null == rowcell ? null : rowcell.getStringCellValue();
					if (methodology == null || methodology.equals(""))
						continue nxtCell;
					BasicDynaBean methodBean = diagMethodologyDAO.findByKey("method_name", methodology);
					if (methodBean == null ) {
						addError(lineNumber, headers[j]+" there is no master value found for the "+methodology +" in the master.");
					} else {
						cellVal = (Integer)methodBean.get("method_id");
						itemBean.set(headers[j], ConvertUtils.convert(cellVal, property.getType()));
					}
				} else if (mandatoryFields.contains(headers[j]) && cellVal == null) {
					addError(lineNumber, headers[j] +" should not be null in the sheet "+sheetName);
					lineHasErrors = true;
					continue nxtCell;

				} else {
					itemBean.set(headers[j], ConvertUtils.convert(cellVal, property.getType()));

				}
				} catch (Exception ex) {

					if (property != null) {
						addError(lineNumber, "Conversion error: Cell value" +
							" could not be converted to "+ property.getType() +" below headers of "+headers[j]+" in the sheet "+sheetName);
					} else {
						addError(lineNumber, "Conversion error: Cell value" +
								" could not be converted to class java.lang.String below headers of "+headers[j]+" in the sheet "+sheetName);
					}
					continue;	/*next cell*/
				}
			}
			List<BasicDynaBean> testResultBean = null;

			if (itemName != null && itemDept != null)
				existOrNot = detailsImporExp.getBean(itemName, itemDept, null);
			if (existOrNot != null) {
				beanId = (String)existOrNot.get("test_id");
				identifiers.put("test_id", beanId);
				identifiers.put("resultlabel", itemBean.get("resultlabel"));
				identifiers.put("method_id", itemBean.get("method_id"));

				TestResultsDAO rdao = new TestResultsDAO();
				testResultBean = rdao.getExistingResultsList(beanId, (String)itemBean.get("resultlabel"), itemBean.get("method_id"));

			} else {
				addError(lineNumber, "there is no master value found for the test name and department in the sheet "+sheetName);

			}

			if (operation.equals("insert") && testResultBean != null && testResultBean.size() !=0) {
				lineHasErrors = true;
				addError(lineNumber, "Duplicate entry cannot inserted into Test Results master.. ");
			}

			if (lineHasErrors)
				continue nxtLine;

			/* updating or inserting part */
			Connection con = null;
			boolean success = false;

			try {
				con = DBUtil.getReadOnlyConnection();
				con.setAutoCommit(false);

				if (operation.equals("update")) {

					keys = new HashMap<String, Object>();
					keys.put("test_id", beanId);
					keys.put("resultlabel_id", itemId.intValue());
					success = resultDao.update(con, itemBean.getMap(), keys) > 0;

					if(success) {

						for(int i=0; i<insertCenterList.size(); i++) {
							resultsCenterBean.set("result_center_id", AddTestDAOImpl.getResultCenterNextSequence());
							resultsCenterBean.set("resultlabel_id", itemId.intValue());
							resultsCenterBean.set("center_id", Integer.parseInt(centerMap.get(insertCenterList.get(i)).toString()));
							resultsCenterBean.set("status", "A");
							success = resultsCenterDAO.insert(con, resultsCenterBean);
						}

						for(int i=0; i<savedCenters.size(); i++) {
							success = AddTestDAOImpl.deleteResultsCenter(itemId.intValue(), Integer.parseInt(centerMap.get(savedCenters.get(i)).toString()));
						}
					}
					if (success)
						con.commit();
/*insert*/		} else {
					newId = AddTestDAOImpl.getNextSequence();
					itemBean.set("resultlabel_id", newId);
					itemBean.set("test_id", beanId);
					success = resultDao.insert(con, itemBean);

					if(success) {
						for(int i=0; i<centerIdsList.size(); i++) {
							BasicDynaBean resultCenterIdBean = centerIdsList.get(i);
							int centerId = (Integer) resultCenterIdBean.get("center_id");
							resultsCenterBean.set("result_center_id", AddTestDAOImpl.getResultCenterNextSequence());
							resultsCenterBean.set("resultlabel_id", newId);
							resultsCenterBean.set("center_id", centerId);
							resultsCenterBean.set("status", "A");
							success = resultsCenterDAO.insert(con, resultsCenterBean);
						}
					}
				}
			} finally {
				DBUtil.commitClose(con, success);
			}
		}
	}

	private void importTestTemplates(HSSFSheet sheet, StringBuilder errors)throws SQLException,IOException {

		Iterator rowIterator = sheet.rowIterator();
		HSSFRow row1 = (HSSFRow)rowIterator.next();
		String sheetName = sheet.getSheetName();

		this.errors = errors;
		Map<String, String> aliasUnmsToDBnmsMap = new HashMap<String, String>();
		aliasUnmsToDBnmsMap.put("test name", "test_name");
		aliasUnmsToDBnmsMap.put("dept name", "ddept_name");
		aliasUnmsToDBnmsMap.put("format name", "format_name");

		List<String> exceptFields = Arrays.asList(new String[] {"test_name", "ddept_name"});
		Map<String, String> formatMasterData = AddTestDAOImpl.getTestTmtMasterData();
		Map<String, String> deletedIds = new HashMap<String, String>();

		GenericDAO mainTableDAO = new GenericDAO("test_template_master");
		BasicDynaBean mainBean = mainTableDAO.getBean();
		List<String> mandatoryFields = Arrays.asList(new String[] {"test_name", "ddept_name", "format_name"});

		row1.getLastCellNum();
		String[] headers = new String[row1.getLastCellNum()];
		String[] xlHeaders = new String[row1.getLastCellNum()];


		for (int i=0; i<headers.length; i++) {

			HSSFCell cell = row1.getCell(i);
			if (cell == null)
				headers[i] = null; /*putting null values, if found*/
			else {

				String header = cell.getStringCellValue().toLowerCase();
				String dbName = (String) (aliasUnmsToDBnmsMap.get(header) == null ? header : aliasUnmsToDBnmsMap.get(header));
				headers[i] = dbName;
				xlHeaders[i] = header;


				if (mainBean.getDynaClass().getDynaProperty(dbName) == null && !exceptFields.contains(dbName)) {
					addError(0, "Unknown header found in header "+dbName+" in the sheet "+sheetName);
					headers[i] = null;
					xlHeaders[i] = null;
				}

			}

		}

		for (String mfield : mandatoryFields) {
			if (!Arrays.asList(headers).contains(mfield)) {
				addError(0, "Mandatory field "+ mfield + " is missing cannot process further in the sheet "+sheetName);
				return;
			}
		}

		GenericDAO templateDao = new GenericDAO("test_template_master");
		BasicDynaBean tableBean = templateDao.getBean();
		BasicDynaBean itemBean = null;
		Map deptMap = AddTestDAOImpl.getDiagDepData();

nxtLine:while(rowIterator.hasNext()) {
			HSSFRow row = (HSSFRow)rowIterator.next();
			int lineNumber = row.getRowNum()+1;
			itemBean = templateDao.getBean();
			String itemName = null;
			Object itemDept = null;
			String beanId = null;
			BasicDynaBean existOrNot = null;
			boolean lineHasErrors = false;
			String formatId = null;


nxtCell:	for (int j=0; j<headers.length; j++) {


				if (headers[j] == null)
					continue nxtCell;
				Object cellVal = null;
				DynaProperty property = null;

				HSSFCell rowcell = row.getCell(j);
				property = tableBean.getDynaClass().getDynaProperty(headers[j]);
				try {
				if (rowcell != null && !rowcell.equals("") ) {


					if (headers[j].equals("test_name")) {

						itemName = rowcell.getStringCellValue();
						continue nxtCell;

					} else if (headers[j].equals("ddept_name")) {
						String exlDbName = rowcell.getStringCellValue();
						cellVal = deptMap.get(exlDbName);
						itemDept = cellVal;
						if (cellVal == null) {
							addError(lineNumber, "Department "+exlDbName+" not exist in the sheet "+sheetName);
							lineHasErrors = true;
							/*through error that dept not exist*/
						}

					} else if (headers[j].equals("format_name")) {
						String formatName = rowcell.getStringCellValue();
						formatId = formatMasterData.get(formatName);
						if (formatId != null)
							cellVal = formatId;
						else {
							addError(lineNumber, "No master value found for "+formatName +"on below headers of " +headers[j]+" in the sheet "+sheetName);
							lineHasErrors = true;
							continue nxtCell;
						}

					}

				}

				if (mandatoryFields.contains(headers[j]) && cellVal == null) {
					addError(lineNumber, headers[j] +" should not be null in the sheet "+sheetName);
					lineHasErrors = true;
					continue nxtCell;

				}
				} catch (Exception ex) {

					if (property != null) {
						addError(lineNumber, "Conversion error: Cell value" +
							" could not be converted to "+ property.getType() +" below headers of "+headers[j]+" in the sheet "+sheetName);
					} else {
						addError(lineNumber, "Conversion error: Cell value" +
								" could not be converted to class java.lang.String below headers of "+headers[j]+" in the sheet "+sheetName);
					}
					continue;	/*next cell*/
				}
			}
			if (itemName != null && itemDept != null)
				existOrNot = detailsImporExp.getBean(itemName, itemDept, null);
			if (existOrNot != null) {
				beanId = (String)existOrNot.get("test_id");

			} else {
				addError(lineNumber, "there is no master value found for the test name and department in the sheet "+sheetName);
				lineHasErrors = true;

			}

			if (lineHasErrors)
				continue nxtLine;

			/* updating or inserting part */
			Connection con = null;
			boolean success = false;

			try {
				con = DBUtil.getReadOnlyConnection();
				con.setAutoCommit(false);

				if (deletedIds.get(beanId) == null) {
					templateDao.delete(con, "test_id", beanId);
					deletedIds.put(beanId, "");
				}

				itemBean.set("test_id", beanId);
				itemBean.set("format_name", formatId);
				success = templateDao.insert(con, itemBean);


			} finally {
				DBUtil.commitClose(con, success);
			}
		}


	}

    private StringBuilder errors;
    private void addError(int line, String msg) {

        if (line > 0) {

            this.errors.append("Line ").append(line).append(": ");

        } else {

            this.errors.append("Error in header: ");
        }

        this.errors.append(msg).append("<br>");
        logger.error("Line {} : {}" , line , msg);

    }

    public ActionForward isTestconductedWithDifferentFormat(ActionMapping mapping, ActionForm form, HttpServletRequest request,
			HttpServletResponse response)throws IOException, ServletException, SQLException {

			String testId = request.getParameter("test_id");
			String status = "true";
			int existscount = getconductedtestscount(testId);
			response.setContentType("text/plain");
			response.setHeader("Cache-Control", "no-cache");
			PrintWriter pw = response.getWriter();
			if(existscount >= 1) {
				status = "false";
			}
			pw.write(status);
			pw.flush();
			pw.close();
			return null;
}

	private int getconductedtestscount(String testId) throws SQLException{
		PreparedStatement ps = null;
		int count=0;
		Connection con = null;
		try {
			con = DBUtil.getReadOnlyConnection();
			ps = con.prepareStatement("SELECT COUNT(*) AS count FROM diagnostics d  " +
									  " JOIN tests_prescribed tp using(test_id)" +
									  " WHERE d.test_id = ? AND tp.conducted IN ('P','C','V','RP','RC','RV') ");
			ps.setString(1, testId);
			count = DBUtil.getIntValueFromDB(ps);
		}finally {
			DBUtil.closeConnections(con, ps);
		}
		return count;
	}

}
