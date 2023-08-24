package com.insta.hms.diagnosticsmasters.addtest;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BasicDynaBean;

import com.bob.hms.adminmasters.organization.OrgMasterDAO;
import com.bob.hms.common.AutoIncrementId;
import com.bob.hms.common.Constants;
import com.bob.hms.common.DBUtil;
import com.bob.hms.common.DateUtil;
import com.bob.hms.common.Logger;
import com.bob.hms.common.RequestContext;
import com.insta.hms.adminmasters.bedmaster.BedMasterDAO;
import com.insta.hms.auditlog.AuditLogDAO;
import com.insta.hms.common.CommonUtils;
import com.insta.hms.common.ConversionUtils;
import com.insta.hms.common.GenericDAO;
import com.insta.hms.common.PagedList;
import com.insta.hms.common.SearchQueryBuilder;
import com.insta.hms.diagnosticmodule.laboratory.LaboratoryBO;
import com.insta.hms.diagnosticsmasters.Result;
import com.insta.hms.diagnosticsmasters.Test;
import com.insta.hms.diagnosticsmasters.TestObservations;
import com.insta.hms.diagnosticsmasters.TestTemplate;
import com.insta.hms.master.GenericPreferences.GenericPreferencesDAO;
import com.insta.hms.master.Order.OrderMasterDAO;
import com.nmc.hms.common.connectionfactory.ConnectionFactory;

import au.com.bytecode.opencsv.CSVWriter;

public class AddTestDAOImpl  {

    static org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(AddTestDAOImpl.class);

    Connection con = null;

    /*
     * Constructor with connection. Required for non-static methods
     */
    public AddTestDAOImpl(Connection con) {
        this.con = con;
    }

    public static String getNextTestId() throws SQLException {
        return AutoIncrementId.getNewIncrId("Test_ID", "Diagnostics",
                "Diagnostic");
    }

    /***************************************************************************
     * Static query-only methods: connection is acquired in these.
     **************************************************************************/

    public static final String GET_DIAG_DEPTS = " SELECT DDEPT_ID, DDEPT_NAME || '(' || d.dept_name || ')' as DDEPT_NAME,dd.CATEGORY "
            + " FROM DIAGNOSTICS_DEPARTMENTS dd join department d on(d.dept_id = dd.category) "
            + " WHERE dd.STATUS='A' ORDER BY dd.category ";

    public static List getDiagDepartments() throws SQLException {
        return DBUtil.simpleQueryToArrayList(GET_DIAG_DEPTS);
    }

    public static final String TEST_FORMATS = "SELECT TESTFORMAT_ID,FORMAT_NAME,DDEPT_ID from TEST_FORMAT order by FORMAT_NAME";

    public static List getReportFormats() throws SQLException {
        return DBUtil.simpleQueryToArrayList(TEST_FORMATS);
    }
    
    public static final String TEST_FORMATS_BY_DEPT = "SELECT TESTFORMAT_ID,FORMAT_NAME,DDEPT_ID from TEST_FORMAT WHERE DDEPT_ID = ? order by FORMAT_NAME";
    
    public static List getReportFormats(String ddeptId) throws SQLException {
        return DBUtil.queryToDynaList(TEST_FORMATS_BY_DEPT, ddeptId);
    }
    
    public static List getAllReportFormats() throws SQLException {
        return DBUtil.queryToDynaList(TEST_FORMATS);
    }

    private static final String GET_DEPT_IDS = "SELECT ddept_id FROM diagnostics_departments";

    public static ArrayList<String> getAllDeptsIds() throws SQLException {
        ArrayList<String> al = null;
        Connection con = DBUtil.getReadOnlyConnection();
        PreparedStatement ps = con.prepareStatement(GET_DEPT_IDS);
        al = DBUtil.queryToOnlyArrayList(ps);
        ps.close();
        con.close();
        return al;
    }

    private static final String EXIST_TEST_QUERY = "SELECT d.diag_code, d.test_name, d.test_id, "
            + " d.ddept_id, d.conduction_format, dd.ddept_name, dc.charge FROM diagnostics d "
            + " JOIN diagnostics_departments dd USING (ddept_id) "
            + " LEFT OUTER JOIN diagnostic_charges dc ON (d.test_id = dc.test_id AND dc.org_name = 'ORG0001' "
            + "   AND dc.bed_type = 'GENERAL' AND dc.priority = 'R') "
            + " ORDER BY dd.ddept_name, d.test_name offset ? limit ? ";

    public static ArrayList getExistingTests(int offset, int pageSize)
            throws SQLException {
        PreparedStatement ps = null;
        Connection con = null;
        ArrayList diagList = null;
        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(EXIST_TEST_QUERY);
            ps.setInt(1, offset);
            ps.setInt(2, pageSize);
            diagList = DBUtil.queryToArrayList(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return diagList;
    }

    private static final String TEST_NAME_QUERY = "SELECT test_name,ddept_id from diagnostics";

    public static ArrayList getTestNames() throws SQLException {
        PreparedStatement ps = null;
        Connection con = null;
        ArrayList diagList = null;
        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(TEST_NAME_QUERY);

            diagList = DBUtil.queryToArrayList(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return diagList;
    }

    public static BasicDynaBean getTestBean(String testId) throws SQLException {
    	return new GenericDAO("diagnostics").findByKey("test_id", testId);
    }

    /**
     * Gets test name of a single test by taking test id
     *
     * @param testid
     * @return
     * @throws SQLException
     */
    public static String getTestName(String testid) throws SQLException {
        Connection con = DBUtil.getReadOnlyConnection();
        PreparedStatement ps = con
                .prepareStatement("SELECT test_name,ddept_id from diagnostics WHERE TEST_ID=?");
        ps.setString(1, testid);
        String testname = DBUtil.getStringValueFromDB(ps);
        ps.close();
        con.close();
        return testname;

    }

    private static final String TEST_DETAILS =
        "SELECT d.diag_code, d.test_name, d.test_id, d.ddept_id, dd.ddept_name, d.type_of_specimen,d.remarks, "
        + " d.status, d.sample_needed, d.conduction_format,d.clinical_information_form,  "
        + " d.conduction_applicable, d.hl7_export_code, "
        + " dcr.charge as routine_charge, dcs.charge as stat_charge, dcsc.charge as schedule_charge, "
        + " dd.category,d.service_sub_group_id, d.conducting_doc_mandatory,d.prior_auth_required,"
        + " d.sample_collection_instructions,d.conduction_instructions, "
        + " d.insurance_category_id,d.results_validation,d.allow_rate_increase,d.allow_rate_decrease, "
        + " d.dependent_test_id, dependent.test_name as dependent_test_name, d.sample_type_id, "
        + " d.results_entry_applicable,d.conducting_role_id,d.stat, "
        + " d.consent_required , d.cross_match_test, d.allergen_test, d.test_short_name, d.clinical_information_form,d.rateplan_category_id, "
        + " d.vat_percent,  d.vat_option ,d.cancer_screening_id, d.is_sensitivity, d.covid_test,d.qrcode_required_test_report,d.allow_bulk_signoff, "
        + " d.symptoms_id "
        + " FROM diagnostics d "
        + " LEFT JOIN diagnostics dependent ON (d.dependent_test_id = dependent.test_id) "
        + " JOIN diagnostics_departments dd ON (dd.ddept_id=d.ddept_id) "
        + " LEFT OUTER JOIN diagnostic_charges dcr ON (d.test_id = dcr.test_id AND dcr.org_name='ORG0001' "
        + "   AND dcr.bed_type = 'GENERAL' AND dcr.priority = 'R') "
        + " LEFT OUTER JOIN diagnostic_charges dcs ON (d.test_id = dcs.test_id AND dcs.org_name='ORG0001' "
        + "   AND dcs.bed_type = 'GENERAL' AND dcs.priority = 'S') "
        + " LEFT OUTER JOIN diagnostic_charges dcsc ON (d.test_id = dcsc.test_id AND dcsc.org_name='ORG0001' "
        + "   AND dcsc.bed_type = 'GENERAL' AND dcsc.priority = 'SC') "
        + " WHERE d.test_id=?";

    public static ArrayList getTestDetails(String testId) throws SQLException {

        PreparedStatement ps = null;
        Connection con = null;
        ArrayList testDetails = null;
        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(TEST_DETAILS);
            ps.setString(1, testId);
            testDetails = DBUtil.queryToArrayList(ps);
          //ArrayList interfaceList =  getHl7InterfaceDetails(testId);
//          if(interfaceList!=null)testDetails.addAll((ArrayList)interfaceList);

        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return testDetails;
    }


    private static final String GET_TEST_CATEGORY = "SELECT dd.category FROM diagnostics_departments dd JOIN "
            + " diagnostics d  using(ddept_id)  where d.test_id = ? ";

    public static String getTestCategory(String testId) throws SQLException {
        String category = null;
        PreparedStatement ps = null;
        Connection con = null;
        try {
            con = DBUtil.getReadOnlyConnection();
            ps = con.prepareStatement(GET_TEST_CATEGORY);
            ps.setString(1, testId);
            category = DBUtil.getStringValueFromDB(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return category;
    }

    private static final String GET_PRESCRIPTION_TYPE = "SELECT d.house_status FROM Diagnostics d where d.test_id =?";

    public static String getTestPrescriptionType(String testId)
            throws SQLException {
        String presType = null;
        PreparedStatement ps = null;
        Connection con = null;
        try {
            con = DBUtil.getReadOnlyConnection();
            ps = con.prepareStatement(GET_PRESCRIPTION_TYPE);
            ps.setString(1, testId);
            String temp = DBUtil.getStringValueFromDB(ps);
            if (temp != null) {
                if (temp.equals("I")) {
                    presType = LaboratoryBO.HOSPITAL_TEST; // these
                    // prescription will
                    // be conducted in
                    // hospital it self
                } else if (temp.equals("O"))
                    presType = LaboratoryBO.OUTHOUST_TEST; // these
                // prescriptions
                // will be sent to
                // the outhouse
                // hospitals.
            }
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return presType;
    }

    public static List<BasicDynaBean> getTestDetails1(String testId)
            throws SQLException {
        PreparedStatement ps = null;
        Connection con = null;
        List<BasicDynaBean> testDetails = null;
        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(TEST_DETAILS);
            ps.setString(1, testId);
            testDetails = DBUtil.queryToDynaList(ps);

           // if(interfaceList!=null)testDetails.addAll((ArrayList)interfaceList);

        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return testDetails;
    }

    private static String INTERFACE_DETAILS = "SELECT interface_name from diagnostics_export_interface where test_id = ?";

    @SuppressWarnings("unchecked")
	public static ArrayList<String> getHl7InterfaceDetails(String testId) throws SQLException{
    	PreparedStatement ps = null;
        Connection con = null;
        ArrayList<String> interfaceDetails = null;
    	try{
    		con = DBUtil.getConnection();
    		ps=con.prepareStatement(INTERFACE_DETAILS);
    		ps.setString(1, testId);
    		interfaceDetails = DBUtil.queryToOnlyArrayList(ps);
    	}finally{
    		DBUtil.closeConnections(con, ps);
    	}
    	return interfaceDetails;
    }

    private static final String TEST_RESULTS =
    		  " SELECT array_to_string(array_agg(hcm.center_name),',') as centers,array_to_string(array_agg(hcm.center_id),',') as numcenter,"
    		  + "trc.status, hcm.health_authority, dmm.method_name, "
    		+ " trm.* FROM TEST_RESULTS_MASTER trm "
    		+ " LEFT JOIN diag_methodology_master as dmm USING (method_id) "
    		+ " LEFT JOIN test_results_center as trc USING (resultlabel_id) "
    		+ " LEFT JOIN hospital_center_master hcm on hcm.center_id=trc.center_id"
    		+ " WHERE TEST_ID = ? "
    		+ " GROUP BY trm.resultlabel_id ,dmm.method_name,trc.status, hcm.health_authority "
    		+ " ORDER BY trm.display_order";

    public static ArrayList getTestResults(String testId) throws SQLException {

    	PreparedStatement ps = null;
        Connection con = null;
        ArrayList testDetails = null;
        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(TEST_RESULTS);
            ps.setString(1, testId);
            testDetails = DBUtil.queryToArrayList(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return testDetails;
    }

    private static final String existing_labels =
    	" SELECT DISTINCT(trm.resultlabel_id),trm.*, trs.*, dmm.*, (CASE WHEN patient_gender = 'M' THEN 'Male' WHEN patient_gender = 'F' THEN 'Female' " +
    	" WHEN patient_gender = 'O' THEN 'Others' ELSE '' END) as gender " +
    	" FROM TEST_RESULTS_MASTER trm " +
    	" JOIN test_results_center trc using(resultlabel_id) " +
    	" JOIN test_result_ranges trs using(resultlabel_id) " +
    	" LEFT JOIN diag_methodology_master dmm USING(method_id)" +
    	" WHERE TEST_ID = ? AND trc.status = 'A' order by display_order,priority";

    private static final String existing_labels_center_wise =
        	" SELECT *,(CASE WHEN patient_gender = 'M' THEN 'Male' WHEN patient_gender = 'F' THEN 'Female' " +
        	" WHEN patient_gender = 'O' THEN 'Others' ELSE '' END) as gender,trs.reference_range_txt,range_for_all, " +
        	" dmm.method_name, (SELECT count(center_id) " +
        		" FROM TEST_RESULTS_MASTER " +
        		" JOIN test_results_center trc using(resultlabel_id) " +
        		" WHERE TEST_ID = ? AND resultlabel_id = trm.resultlabel_id GROUP BY resultlabel_id) as numcenter " +
        	" FROM TEST_RESULTS_MASTER trm " +
        	" JOIN test_results_center trc using(resultlabel_id) " +
        	" JOIN test_result_ranges trs using(resultlabel_id) " +
        	" LEFT JOIN diag_methodology_master dmm USING(method_id)" +
        	" WHERE TEST_ID = ? AND (trc.center_id = 0 OR trc.center_id = ?) AND trc.status = 'A' order by display_order,priority";

    public static List getExistingLables(String test_id)throws SQLException{
    	 PreparedStatement ps = null;
         Connection con = null;
         int centerId = RequestContext.getCenterId();
         try {
             con = DBUtil.getConnection();
             if (centerId != 0 && GenericPreferencesDAO.getGenericPreferences().getMax_centers_inc_default() > 1) {
            	 ps = con.prepareStatement(existing_labels_center_wise);
            	 ps.setString(1, test_id);
            	 ps.setString(2, test_id);
            	 ps.setInt(3, centerId);
             } else {
            	 ps = con.prepareStatement(existing_labels);
            	 ps.setString(1, test_id);
             }
             return  DBUtil.queryToDynaList(ps);

         } finally {
             DBUtil.closeConnections(con, ps);
         }
    }

    /***************************************************************************
     * Updation/insertion methods: require a DAO object constructed with a
     * connection
     **************************************************************************/

    private static final String INSERT_TEST = "INSERT INTO diagnostics "
            + " (test_id, test_name,ddept_id, sample_needed, diag_code,"
            + "  sample_type_id, conduction_format,"
            + " conduction_applicable,username,service_sub_group_id, conducting_doc_mandatory, "
            + " hl7_export_code,sample_collection_instructions,"
            + " conduction_instructions,insurance_category_id,prior_auth_required,"
            + " remarks,allow_rate_increase,allow_rate_decrease,dependent_test_id,"
            + " results_entry_applicable,updated_timestamp, "
            + " conducting_role_id,stat,consent_required, cross_match_test, test_short_name, "
            + " clinical_information_form, allergen_test,rateplan_category_id, vat_percent, vat_option, "
            + " cancer_screening_id, is_sensitivity, covid_test, qrcode_required_test_report, allow_bulk_signoff,symptoms_id ) "
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public boolean insertTest(Test test) throws SQLException {
        PreparedStatement ps = null;
        boolean success = true;

        ps = con.prepareStatement(INSERT_TEST);

        ps.setString(1, test.getTestId());
        ps.setString(2, test.getTestName());
        ps.setString(3, test.getDdeptId());
        ps.setString(4, test.getSampleNeed());
        ps.setString(5, test.getDiagCode());
        ps.setInt(6, (test.getSpecimen() != null && !test.getSpecimen().equals("")) ? test.getSpecimen() : 0);
        ps.setString(7, test.getReportGroup());
        ps.setBoolean(8, test.isConduction_applicable());
        ps.setString(9, test.getUserName());
        ps.setInt(10, test.getServiceSubGroupId());
        ps.setString(11, test.getConducting_doc_mandatory());
        ps.setString(12, (test.getHl7ExportCode() != null && !test.getHl7ExportCode().equals("")) ? test.getHl7ExportCode() : null);
        ps.setString(13, test.getSampleCollectionInstructions());
        ps.setString(14, test.getConductionInstructions());
        ps.setInt(15, test.getInsurance_category_id());
        ps.setString(16, test.getPreAuthReq());
        ps.setString(17, test.getRemarks());
        ps.setBoolean(18, test.isAllow_rate_increase());
        ps.setBoolean(19, test.isAllow_rate_decrease());
        ps.setString(20, test.getDependent_test_id());
        ps.setBoolean(21, test.isResults_entry_applicable());
        ps.setTimestamp(22, DateUtil.getCurrentTimestamp());      
        ps.setString(23, CommonUtils.getCommaSeparatedString(test.getConductingRoleIds()));
        ps.setString(24, test.getStat());
        ps.setString(25, test.getConsent_required());
        ps.setBoolean(26, test.isCross_match_test());
        ps.setString(27, test.getTestShortName());
        ps.setInt(28, test.getClinical_Information_Form());
        ps.setBoolean(29, test.isAllergen_test());
        ps.setInt(30, test.getRateplan_category_id());
        ps.setBigDecimal(31, test.getVat_percent());
        ps.setString(32, test.getVat_option());
        ps.setString(33, CommonUtils.getCommaSeparatedString(test.getCancerScreeningIds()));
        ps.setString(34, test.getIs_sensitivity());
        ps.setBoolean(35, test.isCovid_test());
        ps.setBoolean(36, test.isQrcode_required_test_report());
        ps.setBoolean(37, test.isAllow_bulk_signoff());
        ps.setString(38, CommonUtils.getCommaSeparatedString(test.getSymptomsIds()));
        int i = ps.executeUpdate();
        if (i <= 0) {
            success = false;
            logger.error("Failed to insert test:{}" , i);
        }
        ps.close();

        return success;
    }

    private String INSERT_HL7_INTERFACE = "INSERT INTO diagnostics_export_interface " +
    		"( test_id, interface_name) " +
    		"VALUES(?,?)";

    /**
     * Insert multiple interface corresponding one testId
     *
     * Example :
     *
     * test_id  interface_name
     * -------   ------------
     *    1      iSite
     *    2      PowerScribe
     *
     *
     * @param testId
     * @param interfaceNames
     * @return
     * @throws SQLException
     */
    public boolean insertHl7Interfaces(String testId,String[] interfaceNames)
    throws SQLException {

    	boolean isSuccess = true;

    	 PreparedStatement ps = null;
    	 ps = con.prepareStatement(INSERT_HL7_INTERFACE);

    	for (String interfaceName : interfaceNames) {
    		ps.setString(1, testId);
    		ps.setString(2, interfaceName);
    		ps.addBatch();
		}

    	int count[] = ps.executeBatch();
    	isSuccess = count!=null;
    	return isSuccess;
    }

    private static final String INSERT_RESULTS = "INSERT INTO test_results_master "
            + " (test_id, resultlabel, units, display_order, resultlabel_id,"
            + " expr_4_calc_result,code_type,result_code,data_allowed,source_if_list,resultlabel_short,hl7_export_code, method_id, list_severities ) "
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";

    public boolean insertResults(List<Result> results) throws SQLException,IOException,Exception {

        PreparedStatement ps = con.prepareStatement(INSERT_RESULTS);
        boolean success = true;

        Iterator<Result> it = results.iterator();
        int i =0;
        while (it.hasNext()) {
            Result r = it.next();
            ps.setString(1, r.getTestId());
            ps.setString(2, r.getResultLabel());
            ps.setString(3, r.getUnits());
            if(r.getOrder()=="")
                ps.setInt(4, 0);
            else
                ps.setInt(4, Integer.parseInt(r.getOrder()));


            ps.setInt(5, Integer.parseInt(r.getResultlabel_id()));
            ps.setString(6, r.getExpression());
            ps.setString(7, r.getCode_type());
            ps.setString(8, (r.getResult_code() !=null &&  !r.getResult_code().equals(""))?r.getResult_code().trim():r.getResult_code() );
            ps.setString(9, r.getDataAllowed());
            ps.setString(10, r.getSourceIfList());
            ps.setString(11, r.getResultLabelShort());
            ps.setString(12, (r.getHl7_interface() != null && !r.getHl7_interface().equals("")) ? r.getHl7_interface() : null);
            ps.setObject(13, r.getMethodId());
            ps.setObject(14, r.getListSeverities());
            ps.addBatch();

            i++;
        }

        int updates[] = ps.executeBatch();
        ps.close();

        for (int p = 0; p < updates.length; p++) {
            if (updates[p] <= 0) {
                success = false;
				logger.error("Failed to insert result at {} : {}", p, updates[p]);
                break;
            }
        }

        return success;
    }
    public boolean insertResultRanges(Connection con,List<BasicDynaBean> addedResultRanges)throws SQLException,IOException{
    	boolean sucess = true;
    	sucess &= new GenericDAO("test_result_ranges").insertAll(con, addedResultRanges);
        return sucess;
    }

    private static final String INSERT_TEMPLATES = "INSERT INTO test_template_master(test_id,format_name)"
            + " values(?,?)";

    public boolean insertTemplates(TestTemplate templates) throws SQLException {
        boolean success = false;
        PreparedStatement ps = con.prepareStatement(INSERT_TEMPLATES);

        ps.setString(1, templates.getTestId());
        ps.setString(2, templates.getTemplateId());

        int i = ps.executeUpdate();
        if (i > 0) {
            success = true;
        }

        return success;
    }

    private static final String DELETE_TEMPLATES = "DELETE FROM test_template_master where test_id=?";

    public boolean deleteTemplates(String testId) throws SQLException {
        boolean status = false;
        PreparedStatement ps = con.prepareStatement(DELETE_TEMPLATES);
        ps.setString(1, testId);

        int a = ps.executeUpdate();
        if (a >= 0) {
            status = true;
        }

        return status;
    }

    private static final String INSERT_TEST_CHARGE = "INSERT INTO diagnostic_charges(test_id,"
            + "org_name,charge,bed_type,priority,discount,username)VALUES(?," + "?,?,?,?,?,?)  ";

    private static final String CHECK_FOR_TESTCHARGE = "SELECT COUNT(*) FROM  diagnostic_charges WHERE "
            + "test_id=? AND bed_type=? AND org_name=? AND priority=? ";

    private static final String UPDATE_TEST_CHARGE =
        "UPDATE diagnostic_charges SET charge=?,discount=?,username=? WHERE "
        + "test_id=? AND bed_type=? AND org_name=? AND priority=? ";

    public boolean addOREditTestCharges(ArrayList<TestCharge> tcList)
            throws SQLException {
        boolean status = false;

        PreparedStatement rps = con.prepareStatement(CHECK_FOR_TESTCHARGE);
        PreparedStatement ps = con.prepareStatement(INSERT_TEST_CHARGE);
        PreparedStatement ups = con.prepareStatement(UPDATE_TEST_CHARGE);
        Iterator<TestCharge> it = tcList.iterator();

        while (it.hasNext()) {
            TestCharge tc = it.next();
            rps.setString(1, tc.getTestId());
            rps.setString(2, tc.getBedType());
            rps.setString(3, tc.getOrgId());
            rps.setString(4, tc.getPriority());

            String count = DBUtil.getStringValueFromDB(rps);
            if (count.equals("0")) {
                ps.setString(1, tc.getTestId());
                ps.setString(2, tc.getOrgId());
                ps.setBigDecimal(3, tc.getCharge());
                ps.setString(4, tc.getBedType());
                ps.setString(5, tc.getPriority());
                ps.setBigDecimal(6, tc.getDiscount());
                ps.setString(7, tc.getUserName());
                ps.addBatch();
            } else {
                ups.setBigDecimal(1, tc.getCharge());
                ups.setBigDecimal(2, tc.getDiscount());
                ups.setString(3, tc.getUserName());
                ups.setString(4, tc.getTestId());
                ups.setString(5, tc.getBedType());
                ups.setString(6, tc.getOrgId());
                ups.setString(7, tc.getPriority());

                ups.addBatch();
            }

        }

        do {
            int a[] = ps.executeBatch();
            status = DBUtil.checkBatchUpdates(a);
            if (!status)
                break;

            int b[] = ups.executeBatch();
            status = DBUtil.checkBatchUpdates(b);

        } while (false);

        rps.close();
        ups.close();
        ps.close();

        return status;
    }

    public boolean updateTestChargeList(List<TestCharge> chargeList)
            throws SQLException {

        PreparedStatement ups = con.prepareStatement(UPDATE_TEST_CHARGE);

        for (TestCharge tc : chargeList) {
            int i=1;
            ups.setBigDecimal(i++, tc.getCharge());
            ups.setBigDecimal(i++, tc.getDiscount());
            ups.setString(i++, tc.getUserName());
            ups.setString(i++, tc.getTestId());
            ups.setString(i++, tc.getBedType());
            ups.setString(i++, tc.getOrgId());
            ups.setString(i++, tc.getPriority());

            ups.addBatch();
        }

        int results[] = ups.executeBatch();
        boolean status = DBUtil.checkBatchUpdates(results);
        ups.close();

        return status;
    }

    private static final String UPDATE_APPLICABLE = "UPDATE test_org_details SET applicable=? WHERE "
        + "test_id=? AND org_id=?";

    public boolean updateRatePlanApplicableList(List<TestCharge> ratePlanApplicableList)
            throws SQLException {

        PreparedStatement ps = con.prepareStatement(UPDATE_APPLICABLE);

        for (TestCharge rpa : ratePlanApplicableList) {
            ps.setBoolean(1, rpa.getApplicable());
            ps.setString(2, rpa.getTestId());
            ps.setString(3, rpa.getOrgId());
            ps.addBatch();
        }

        int results[] = ps.executeBatch();
        boolean status = DBUtil.checkBatchUpdates(results);
        ps.close();

        return status;
    }

    private static final String UPDATE_TEST = "UPDATE diagnostics " +
            " SET test_name=?, sample_needed=?, " +
            "  sample_type_id=?, conduction_format=?, status=?, " +
            "  conduction_applicable=?, diag_code=?, username=?, service_sub_group_id=?, " +
            "  conducting_doc_mandatory=?, hl7_export_code=?, " +
            "  sample_collection_instructions=? , conduction_instructions = ? , " +
            "  insurance_category_id=?,prior_auth_required=?,results_validation = ?,remarks = ?," +
            "  allow_rate_increase=?, allow_rate_decrease=? , dependent_test_id=?,results_entry_applicable = ?," +
            "  updated_timestamp= ?, conducting_role_id = ?, stat = ?, " +
            "  consent_required = ? , cross_match_test = ?, test_short_name = ?, clinical_information_form = ?, " +
            " allergen_test = ?,rateplan_category_id=?, vat_percent = ?, vat_option = ?,cancer_screening_id=?, is_sensitivity = ?, covid_test = ?,qrcode_required_test_report = ?,allow_bulk_signoff = ? ,symptoms_id=? " +
            " WHERE test_id=?";

    public boolean updateTest(Test test) throws SQLException {
        PreparedStatement ps = con.prepareStatement(UPDATE_TEST);
        boolean success = true;
        int i = 1;
        ps.setString(i++, test.getTestName());
        ps.setString(i++, test.getSampleNeed());
        ps.setInt(i++, (test.getSpecimen() != null && !test.getSpecimen().equals("")) ? test.getSpecimen() : 0);
        ps.setString(i++, test.getReportGroup());
        ps.setString(i++, test.getTestStatus());
        ps.setBoolean(i++, test.isConduction_applicable());
        ps.setString(i++, test.getDiagCode());
        ps.setString(i++, test.getUserName());
        ps.setInt(i++, test.getServiceSubGroupId());
        ps.setString(i++, test.getConducting_doc_mandatory());
        ps.setString(i++, (test.getHl7ExportCode() != null && !test.getHl7ExportCode().equals("")) ? test.getHl7ExportCode() : null);
        ps.setString(i++, test.getSampleCollectionInstructions());
        ps.setString(i++, test.getConductionInstructions());
        ps.setInt(i++, test.getInsurance_category_id());
        ps.setString(i++, test.getPreAuthReq());
        ps.setString(i++, test.getResultsValidation());
        ps.setString(i++, test.getRemarks());
        ps.setBoolean(i++, test.isAllow_rate_increase());
        ps.setBoolean(i++, test.isAllow_rate_decrease());
        ps.setString(i++, test.getDependent_test_id());
        ps.setBoolean(i++, test.isResults_entry_applicable());
        ps.setTimestamp(i++, DateUtil.getCurrentTimestamp());
		ps.setString(i++, CommonUtils.getCommaSeparatedString(test.getConductingRoleIds()));
		ps.setString(i++, test.getStat());
		ps.setString(i++, test.getConsent_required());
		ps.setBoolean(i++, test.isCross_match_test());
		ps.setString(i++, test.getTestShortName());
		ps.setInt(i++, test.getClinical_Information_Form());
		ps.setBoolean(i++, test.isAllergen_test());
		ps.setInt(i++, test.getRateplan_category_id());
		ps.setBigDecimal(i++, test.getVat_percent());
		ps.setString(i++, test.getVat_option());
		ps.setString(i++,CommonUtils.getCommaSeparatedString(test.getCancerScreeningIds()));
		ps.setString(i++, test.getIs_sensitivity());
        ps.setBoolean(i++, test.isCovid_test());
        ps.setBoolean(i++, test.isQrcode_required_test_report());
        ps.setBoolean(i++, test.isAllow_bulk_signoff());
        ps.setString(i++, CommonUtils.getCommaSeparatedString(test.getSymptomsIds()));
        ps.setString(i++, test.getTestId());
       
        int rows = ps.executeUpdate();
        ps.close();

        if (rows <= 0)
            success = false;
        return success;
    }




    /**
     *For updating interface name corresponding each test its need to be delete the interface rows and reinsert the interface rows
     *
     * Cause :
     *
     * test_id  interface_name
     * ------   ------------
     *    1     iSite
     *    1     PowerScribe
     *    1     iPlatina
     *
     *  Now Updated one requred only iSite and PowerScribe so we need to detlete and re-insert in this table.
     *
     *
     * @param testId
     * @param interfaceNames
     * @return boolean
     */

    public boolean updateHl7Interface(String testId, String[] interfaceNames) throws SQLException{
    	boolean success = true;

    	if(deleteHl7Interface(testId) >= 0) {
    		success = insertHl7Interfaces(testId, interfaceNames);
    	}
    	return success;

    }

    private String DELETE_INTERFACE = "DELETE FROM diagnostics_export_interface WHERE test_id = ?";

    /**
     * Deleting All interface name rows corresponding testId.
     * @param testId
     * @return
     */

   public int deleteHl7Interface(String testId) throws SQLException{
	   int updatedRow = 0;

	   PreparedStatement ps = con.prepareStatement(DELETE_INTERFACE);
	   ps.setString(1, testId);
	   updatedRow =  ps.executeUpdate();
	   return updatedRow;
   }
    private static final String UPDATE_TEST_RESULTS =
        "UPDATE test_results_master SET resultlabel=?, units=?, display_order=?, " +
        " expr_4_calc_result = ? , code_type = ?, result_code = ? ,data_allowed = ?, source_if_list =?,resultlabel_short=?,hl7_export_code=?, method_id=?, list_severities=? "
            + " WHERE test_id=? AND resultlabel_id=? ";

    public boolean updateResults(ArrayList<Result> results)
    	throws SQLException ,IOException,Exception{
        PreparedStatement ps = con.prepareStatement(UPDATE_TEST_RESULTS);
        boolean success = true;

        Iterator<Result> it = results.iterator();
        while (it.hasNext()) {
            Result r = it.next();
            ps.setString(1, r.getResultLabel());
            ps.setString(2, r.getUnits());
            if (r.getOrder().equals(""))
            	ps.setInt(3, 0);
            else
            	ps.setInt(3, Integer.parseInt(r.getOrder()));
            ps.setString(4, r.getExpression());
            ps.setString(5, r.getCode_type());
            ps.setString(6, (r.getResult_code() !=null &&  !r.getResult_code().equals(""))?r.getResult_code().trim():r.getResult_code());
            ps.setString(7, r.getDataAllowed());
            ps.setString(8, r.getSourceIfList());
            ps.setString(9, r.getResultLabelShort());
            ps.setString(10, (r.getHl7_interface() != null && !r.getHl7_interface().equals("")) ? r.getHl7_interface() : null);
            ps.setObject(11, r.getMethodId());
            ps.setObject(12, r.getListSeverities());
            ps.setString(13, r.getTestId());
            ps.setInt(14, Integer.parseInt(r.getResultlabel_id()));

            ps.addBatch();
        }

        int updates[] = ps.executeBatch();
        ps.close();

        for (int p = 0; p < updates.length; p++) {
            if (updates[p] <= 0) {
                success = false;
                logger.error("Failed to update test result at {} : {}" , p ,
                         updates[p]);
                break;
            }
        }

        return success;
    }
    public boolean updateResultRanges(Connection con,List<BasicDynaBean> modifiedResultsRanges)
     	throws SQLException,IOException{
    	Map key = new HashMap();
    	boolean sucess = true;

    	for(BasicDynaBean modifedResultRange : modifiedResultsRanges){
    		key.put("resultlabel_id", modifedResultRange.get("resultlabel_id"));
    		sucess &= new GenericDAO("test_result_ranges").update(con, modifedResultRange.getMap(), key) > 0;
    	}
    	return sucess;
    }
    public static final String DELETE_TEST_RESULTS = "DELETE FROM test_results_master "
            + " WHERE test_id=? AND resultlabel_id=?";

    public boolean deleteResults(ArrayList<Result> results)
     	throws SQLException,IOException {
        PreparedStatement ps = con.prepareStatement(DELETE_TEST_RESULTS);
        boolean success = true;

        Iterator<Result> it = results.iterator();
        while (it.hasNext()) {
            Result r = it.next();
            ps.setString(1, r.getTestId());
            ps.setInt(2, Integer.parseInt(r.getResultlabel_id()));
            ps.addBatch();
        }

        int updates[] = ps.executeBatch();
        ps.close();

        for (int p = 0; p < updates.length; p++) {
            if (updates[p] <= 0) {
                success = false;
                logger.error("Failed to delete test result at {} : {}" ,p , updates[p]);
                break;
            }
        }
        return success;
    }
    public boolean deleteResultRanges(Connection con,List<Result> deletedResultsRanges)
    	throws SQLException,IOException{
    	boolean sucess = true;
    	GenericDAO dao = new GenericDAO("test_result_ranges");
    	BasicDynaBean bean = null;

    	for(Result modifedResultRange : deletedResultsRanges){
    		bean = dao.findByKey("resultlabel_id", new Integer(modifedResultRange.getResultlabel_id()));
    		if (bean != null)
    			sucess &= dao.delete(con, "resultlabel_id",new Integer(modifedResultRange.getResultlabel_id()));
    	}
    	return sucess;

    }
    private static final String GET_DISCOUNT = "SELECT discount FROM  diagnostic_charges WHERE "
            + "test_id=? AND bed_type=? AND org_name=? AND priority='R' ";

    private static final String GET_ROUTINE_CHARGE = "SELECT charge FROM  diagnostic_charges WHERE "
        + "test_id=? AND bed_type=? AND org_name=? AND priority='R' ";

    public Map editTestCharges(String orgId, String testid) throws SQLException {

        LinkedHashMap<String, ArrayList<String>> map = new LinkedHashMap<String, ArrayList<String>>();
        ArrayList<String> beds = new ArrayList<String>();
        ArrayList<String> regularCharge = new ArrayList<String>();
        ArrayList<String> discount = new ArrayList<String>();
        /*
         * ArrayList<String> statCharge = new ArrayList<String>(); ArrayList<String>
         * scheduleCharge = new ArrayList<String>();
         */

        BedMasterDAO bddao = new BedMasterDAO();
        ArrayList<Hashtable<String, String>> bedTypes = bddao
                .getUnionOfAllBedTypes();
        Iterator<Hashtable<String, String>> it = bedTypes.iterator();

        PreparedStatement dps = con.prepareStatement(GET_DISCOUNT);
        PreparedStatement rps = con.prepareStatement(GET_ROUTINE_CHARGE);
        // PreparedStatement scps = con.prepareStatement(GET_SCHEDULE_CHARGE);

        while (it.hasNext()) {
            Hashtable<String, String> ht = it.next();
            String bedType = ht.get("BED_TYPE");
            beds.add(bedType);


            dps.setString(1, testid);
            dps.setString(2, bedType);
            dps.setString(3, orgId);


            rps.setString(1, testid);
            rps.setString(2, bedType);
            rps.setString(3, orgId);

            /*
             * scps.setString(1, testid); scps.setString(2, bedType);
             * scps.setString(3, orgId);
             */
            discount.add(DBUtil.getStringValueFromDB(dps));
            regularCharge.add(DBUtil.getStringValueFromDB(rps));
            /*
             * statCharge.add(DBUtil.getStringValueFromDB(sps));
             * scheduleCharge.add(DBUtil.getStringValueFromDB(scps));
             */

        }

        // sps.close();
        rps.close();
        // scps.close();

        map.put("CHARGES", beds);
        map.put("REGULARCHARGE", regularCharge);
        map.put("DISCOUNT", discount);
        /*
         * map.put("STATCHARGE", statCharge); map.put("SCHEDULECHARGE",
         * scheduleCharge);
         */

        logger.debug("editTestCharges {}",map);
        return map;
    }

    private static final String ALL_TEST_NAME = "SELECT distinct test_id,test_name FROM diagnostics ORDER BY test_id ";

    public static ArrayList getAllTestNames() throws SQLException {
        ArrayList l = new ArrayList();

        Connection con = null;
        con = DBUtil.getReadOnlyConnection();
        PreparedStatement ps = con.prepareStatement(ALL_TEST_NAME);
        l = DBUtil.queryToArrayList(ps);
        ps.close();
        con.close();

        return l;
    }

    public static List getDiagnosticCharges(String testid, String orgid, String bedtype) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List cl = null;
        try {
            con = DBUtil.getReadOnlyConnection();
            String orgquery = "select org_id from organization_details where org_name='"
                    + Constants.getConstantValue("ORG") + "'";
            String generalorgid = DBUtil.getStringValueFromDB(orgquery);
            String generalbedtype = Constants.getConstantValue("BEDTYPE");

            String chargequery = "select charge,bed_type,priority from diagnostic_charges where "
                    + " test_id =? and org_name=? and bed_type=?";
            ps = con.prepareStatement(chargequery);
            ps.setString(1, testid);
            ps.setString(2, orgid);
            ps.setString(3, bedtype);

            cl = DBUtil.queryToArrayList(ps);
            logger.debug("getDiagnosticCharges {}",cl);

            if (cl.size() > 0) {

            } else {
                ps.setString(1, testid);
                ps.setString(2, generalorgid);
                ps.setString(3, generalbedtype);
                cl = DBUtil.queryToArrayList(ps);
                logger.debug("getDiagnosticCharges {}",cl);
            }

        } catch (Exception e) {
            logger.debug("Exception in getDiagnosticCharges method", e);
        } finally {
            try {
                if (rs != null)
                    rs.close();
                if (ps != null)
                    ps.close();
                if (con != null)
                    con.close();
            } catch (Exception e) {
                logger.debug("Exception in getDiagnosticCharges method", e);
            }
        }

        return cl;
    }

    /*
     * Retrieves a list of tests, their departments and corresponding charges.
     * Charges can vary depending on bed type and organization, so we need these
     * as input to this method.
     */
    private static final String GET_TEST_DEPT_CHARGE_QUERY = " SELECT dc.test_id, d.test_name, dc.charge, "
            + " d.ddept_id,tod.item_code,dc.discount FROM diagnostic_charges dc "
            + " JOIN diagnostics d ON (dc.test_id = d.test_id and d.status = 'A') "
            + " JOIN test_org_details tod ON (dc.test_id = tod.test_id and tod.org_id = dc.org_name and tod.applicable) "
            + " WHERE priority='R' " + " AND bed_type=? AND org_name=? ";


    public static List getTestDeptCharges(String bedType, String orgid)
            throws SQLException {
        Connection con = null;
        PreparedStatement ps = null;
        List list = null;

        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(GET_TEST_DEPT_CHARGE_QUERY);
            ps.setString(1, bedType);
            ps.setString(2, orgid);
            // ps.setString(3, bedType);
            // ps.setString(4, orgid);
            list = DBUtil.queryToArrayList(ps);
        } finally {
            if (ps != null)
                ps.close();
            if (con != null)
                con.close();
        }

        return list;
    }

    private static final String GET_TEMPLATE_LIST = "SELECT format_name FROM test_template_master where "
            + " test_id =?";

    public static ArrayList<String> getTemplateList(String testId)
            throws SQLException {
        ArrayList<String> al = null;
        Connection con = DBUtil.getReadOnlyConnection();
        PreparedStatement ps = con.prepareStatement(GET_TEMPLATE_LIST);
        ps.setString(1, testId);

        al = DBUtil.queryToOnlyArrayList(ps);

        ps.close();
        con.close();

        return al;
    }

    public static StringBuffer getValueOrTemplate(String testid)
            throws SQLException {
        Connection con = null;
        ResultSet rs = null;
        PreparedStatement ps = null;
        StringBuffer value = null;

        try {
            con = DBUtil.getReadOnlyConnection();
            ps = con
                    .prepareStatement("select conduction_format from diagnostics where test_id=?");
            ps.setString(1, testid);
            if (DBUtil.getStringValueFromDB(ps).equalsIgnoreCase("Y")) {
                String templateQuery = "SELECT tf.format_name FROM test_format tf join test_template_master ttm"
                        + " on (tf.testformat_id= ttm.format_name) and ttm.test_id = ?";
                ps = con.prepareStatement(templateQuery);
                ps.setString(1, testid);
                rs = ps.executeQuery();
                value = new StringBuffer("Template( ");
                boolean templateExists = false;
                String template = null;
                while (rs.next()) {
                    templateExists = true;
                    template = rs.getString(1);
                    if (template != null && !template.equals("")) {
                        if (template.length() > 20) {
                            template = template.substring(0, 20);
                            template = template.concat("..");
                        }
                    } else {
                        template = "No Template";
                    }

                    value.append(template);
                    value.append(", ");
                }
                if (!templateExists) {
                    template = "No Template";
                    value.append(template);
                    value.append(", ");
                }
                value = new StringBuffer(value.substring(0, value.length() - 6));
                value.append(')');
            } else {
                ps = con
                        .prepareStatement("select count(*) from test_results_master where test_id=?");
                ps.setString(1, testid);
                value = new StringBuffer("Values(");
                value.append(DBUtil.getStringValueFromDB(ps));
                value.append(')');
            }
        } finally {
            DBUtil.closeConnections(con, ps, rs);
        }
        return value;
    }

    public static String getTestId(String prescribedid) throws SQLException {
        Connection con = null;
        PreparedStatement ps = null;
        String testid = null;
        try {
            con = DBUtil.getConnection();
            ps = con
                    .prepareStatement("SELECT TEST_ID FROM TESTS_PRESCRIBED WHERE PRESCRIBED_ID=?");
            ps.setInt(1, Integer.parseInt(prescribedid));
            testid = DBUtil.getStringValueFromDB(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return testid;
    }

    private static final String UPDATE_TIMESTAMP = "UPDATE diag_test_timestamp set test_timestamp=test_timestamp+1;";

    public void updateDiagnosticTimeStamp() throws SQLException {
        PreparedStatement ps = con.prepareStatement(UPDATE_TIMESTAMP);
        ps.executeUpdate();
        ps.close();
    }

    /*
     * Copy the GENERAL bed type charges to all inactive bed types for the given test ID.
     * These charges will not be inserted normally when the test is created, since
     * only active bed charges are input from the user.
     */
    private static final String COPY_GENERAL_CHARGES_TO_INACTIVE_BEDS =
        " INSERT INTO diagnostic_charges (org_name, bed_type, test_id, charge, priority) " +
        " SELECT abo.org_id, abo.bed_type, dc.test_id, dc.charge, 'R' " +
        " FROM all_beds_orgs_view abo " +
        "   JOIN diagnostic_charges dc ON (dc.org_name = abo.org_id AND dc.bed_type = 'GENERAL') " +
        " WHERE abo.bed_type IN ( " +
        "     SELECT DISTINCT intensive_bed_type FROM icu_bed_charges WHERE bed_status = 'I' " +
        "     UNION ALL SELECT DISTINCT bed_type FROM bed_details WHERE bed_status = 'I') " +
        "   AND dc.test_id=? " ;

    public void copyGeneralChargesToInactiveBeds(String id) throws SQLException {
        PreparedStatement ps = con.prepareStatement(COPY_GENERAL_CHARGES_TO_INACTIVE_BEDS);
        ps.setString(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public void groupIncreaseTestCharges(String orgId, List<String> bedTypes,
            List<String> testIds, BigDecimal amount, boolean isPercentage,
            BigDecimal roundTo,String updateTable, String userName) throws SQLException {

		if (roundTo.compareTo(BigDecimal.ZERO) == 0) {
			groupIncreaseChargesNoRoundOff(orgId, bedTypes, testIds, amount,
					isPercentage, updateTable, userName);
		}else {
			groupIncreaseChargesWithRoundOff(orgId, bedTypes, testIds, amount,
					isPercentage, roundTo, updateTable, userName);
		}
	}

    /*
     * Group increase charges: takes in: - orgId to update (reqd) - list of bed
     * types to update (optional, if not given, all bed types) - list of test
     * IDs to update (optional, if not given, all tests) - amount to increase by
     * (can be negative for a decrease) - whether the amount is a percentage
     * instead of an abs. amount - an amount to be rounded to (nearest).
     * Rounding to 0 is invalid.
     *
     * The new amount will not be allowed to go less than zero.
     */
    private static final String GROUP_INCR_TEST_CHARGES = " UPDATE diagnostic_charges " +
    		" SET charge = GREATEST( round((charge+?)/?,0)*?, 0), username = ?"
            + " WHERE org_name=? ";

    private static final String GROUP_INCR_TEST_CHARGES_PERCENTAGE = " UPDATE diagnostic_charges " +
    		" SET charge = GREATEST( round(charge*(100+?)/100/?,0)*?, 0), username = ?"
            + " WHERE org_name=? ";

    private static final String GROUP_INCR_TEST_DISCOUNTS = " UPDATE diagnostic_charges " +
    	" SET discount = LEAST(GREATEST( round((discount+?)/?,0)*?, 0), charge), username = ? "
        + " WHERE org_name=? ";

    private static final String GROUP_INCR_TEST_DISCOUNT_PERCENTAGE = " UPDATE diagnostic_charges " +
    		" SET discount = LEAST(GREATEST( round(discount*(100+?)/100/?,0)*?, 0), charge), username = ? "
            + " WHERE org_name=? ";

    private static final String GROUP_APPLY_DISCOUNTS = " UPDATE diagnostic_charges" +
    	" SET discount = LEAST(GREATEST( round((charge+?)/?,0)*?, 0), charge), username = ?"
        + " WHERE org_name=? ";

    private static final String GROUP_APPLY_DISCOUNT_PERCENTAGE = " UPDATE diagnostic_charges" +
    		" SET discount =LEAST(GREATEST( round(charge+(charge*?/100/?),0)*?, 0), charge), username = ?"
            + " WHERE org_name=? ";

    private static final String AUDIT_LOG_HINT = ":GROUP UPDATE";

    public void groupIncreaseChargesWithRoundOff(String orgId, List<String> bedTypes,
            List<String> testIds, BigDecimal amount, boolean isPercentage,
            BigDecimal roundTo,String updateTable, String userName) throws SQLException {

    	String userNameWithHint = ((null == userName) ? "" : userName) + AUDIT_LOG_HINT;
    	StringBuilder query = null;
        if(updateTable !=null && updateTable.equals("UPDATECHARGE")) {
            query = new StringBuilder(
                isPercentage ? GROUP_INCR_TEST_CHARGES_PERCENTAGE
                        : GROUP_INCR_TEST_CHARGES);

        }else if(updateTable.equals("UPDATEDISCOUNT")) {
            query = new StringBuilder(
                isPercentage ? GROUP_INCR_TEST_DISCOUNT_PERCENTAGE
                        : GROUP_INCR_TEST_DISCOUNTS);
        }else {
            query = new StringBuilder(
                isPercentage ? GROUP_APPLY_DISCOUNT_PERCENTAGE
                        : GROUP_APPLY_DISCOUNTS);
        }

        SearchQueryBuilder.addWhereFieldOpValue(true, query, "bed_type", "IN",
                bedTypes);
        SearchQueryBuilder.addWhereFieldOpValue(true, query, "test_id", "IN",
                testIds);

        PreparedStatement ps = con.prepareStatement(query.toString());

        // sanity: round to zero is not allowed, can cause div/0
        if (roundTo.equals(BigDecimal.ZERO))
            roundTo = BigDecimal.ONE;

        int i = 1;
        ps.setBigDecimal(i++, amount);
        ps.setBigDecimal(i++, roundTo);
        ps.setBigDecimal(i++, roundTo); // roundTo appears twice in the query
        ps.setString(i++, userNameWithHint);
        ps.setString(i++, orgId);

        if (bedTypes != null)
            for (String bedType : bedTypes) {
                ps.setString(i++, bedType);
            }
        if (testIds != null)
            for (String testId : testIds) {
                ps.setString(i++, testId);
            }

        ps.executeUpdate();
        if (ps != null)
            ps.close();
    }

    private static final String GROUP_INCR_TEST_CHARGES_NO_ROUNDOFF = " UPDATE diagnostic_charges " +
	" SET charge = GREATEST( charge + ?, 0), username = ?"
    + " WHERE org_name=? ";

	private static final String GROUP_INCR_TEST_CHARGES_PERCENTAGE_NO_ROUNDOFF = " UPDATE diagnostic_charges " +
		" SET charge = GREATEST(charge +(charge * ? / 100 ) , 0), username = ?"
	    + " WHERE org_name=? ";

	private static final String GROUP_INCR_TEST_DISCOUNTS_NO_ROUNDOFF = " UPDATE diagnostic_charges " +
	" SET discount = LEAST(GREATEST( discount + ?, 0), charge), username = ? "
	+ " WHERE org_name=? ";

	private static final String GROUP_INCR_TEST_DISCOUNT_PERCENTAGE_NO_ROUNDOFF = " UPDATE diagnostic_charges " +
		" SET discount = LEAST(GREATEST(discount +(discount * ? / 100 ) , 0), charge), username = ? "
	    + " WHERE org_name=? ";

	private static final String GROUP_APPLY_DISCOUNTS_NO_ROUNDOFF = " UPDATE diagnostic_charges" +
	" SET discount = LEAST(GREATEST( charge + ?, 0), charge), username = ?"
	+ " WHERE org_name=? ";

	private static final String GROUP_APPLY_DISCOUNT_PERCENTAGE_NO_ROUNDOFF = " UPDATE diagnostic_charges" +
		" SET discount = LEAST(GREATEST( charge + (charge * ? / 100) , 0), charge), username = ?"
	    + " WHERE org_name=? ";

	public void groupIncreaseChargesNoRoundOff(String orgId, List<String> bedTypes,
	    List<String> testIds, BigDecimal amount, boolean isPercentage,
	    String updateTable, String userName) throws SQLException {

		String userNameWithHint = ((null == userName) ? "" : userName) + AUDIT_LOG_HINT;
		StringBuilder query = null;
		if(updateTable !=null && updateTable.equals("UPDATECHARGE")) {
		    query = new StringBuilder(
		        isPercentage ? GROUP_INCR_TEST_CHARGES_PERCENTAGE_NO_ROUNDOFF
		                : GROUP_INCR_TEST_CHARGES_NO_ROUNDOFF);

		}else if(updateTable.equals("UPDATEDISCOUNT")) {
		    query = new StringBuilder(
		        isPercentage ? GROUP_INCR_TEST_DISCOUNT_PERCENTAGE_NO_ROUNDOFF
		                : GROUP_INCR_TEST_DISCOUNTS_NO_ROUNDOFF);
		}else {
		    query = new StringBuilder(
		        isPercentage ? GROUP_APPLY_DISCOUNT_PERCENTAGE_NO_ROUNDOFF
		                : GROUP_APPLY_DISCOUNTS_NO_ROUNDOFF);
		}

		SearchQueryBuilder.addWhereFieldOpValue(true, query, "bed_type", "IN",
		        bedTypes);
		SearchQueryBuilder.addWhereFieldOpValue(true, query, "test_id", "IN",
		        testIds);

		PreparedStatement ps = con.prepareStatement(query.toString());

		int i = 1;
		ps.setBigDecimal(i++, amount);
		ps.setString(i++, userNameWithHint);
		ps.setString(i++, orgId);

		if (bedTypes != null)
		    for (String bedType : bedTypes) {
		        ps.setString(i++, bedType);
		    }
		if (testIds != null)
		    for (String testId : testIds) {
		        ps.setString(i++, testId);
		    }

		ps.executeUpdate();
		if (ps != null)
		    ps.close();
	}

    private static final String GET_ORG_ITEM_CODE = "SELECT * FROM test_org_details where test_id = ? AND org_id = ?";

    public static List<BasicDynaBean> getOrgItemCode(String orgId, String testId)
            throws SQLException {
        Connection con = null;
        PreparedStatement ps = null;
        List<BasicDynaBean> list = null;
        try {
            con = DBUtil.getReadOnlyConnection();
            ps = con.prepareStatement(GET_ORG_ITEM_CODE);
            ps.setString(1, testId);
            ps.setString(2, orgId);
            list = DBUtil.queryToDynaList(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return list;
    }

    private static final String GET_TEST_NOT_APPLICABLE =
    	"SELECT org_id FROM test_org_details where test_id = ? AND applicable = false AND org_id != ? ";

    public static List<String> getTestNotApplicableRatePlans(String testId, String orgId)
            throws SQLException {
        Connection con = null;
        PreparedStatement ps = null;
        List<String> list = new ArrayList<String>();
        try {
            con = DBUtil.getReadOnlyConnection();
            ps = con.prepareStatement(GET_TEST_NOT_APPLICABLE);
            ps.setString(1, testId);
            ps.setString(2, orgId);
            list = DBUtil.queryToStringList(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return list;
    }

    private static final String INSERT_ITEM_CODE = "INSERT INTO test_org_details(test_id,"
            + "org_id,applicable,item_code, code_type)VALUES(?,?,?,?,?)  ";

    private static final String CHECK_FOR_ITEM_CODE = "SELECT COUNT(*) FROM  test_org_details WHERE "
            + "test_id=? AND org_Id=?";

    private static final String UPDATE_ITEM_CODE = "UPDATE test_org_details SET applicable=?, item_code=?, code_type=? WHERE "
            + "test_id=? AND org_id=?";

    public boolean addOREditItemCode(ArrayList<TestCharge> testCodes)
            throws SQLException {
        boolean status = false;

        PreparedStatement rps = con.prepareStatement(CHECK_FOR_ITEM_CODE);
        PreparedStatement ps = con.prepareStatement(INSERT_ITEM_CODE);
        PreparedStatement ups = con.prepareStatement(UPDATE_ITEM_CODE);

        for (TestCharge tc : testCodes) {

            rps.setString(1, tc.getTestId());
            rps.setString(2, tc.getOrgId());
            String count = DBUtil.getStringValueFromDB(rps);
            if (count.equals("0")) {
                ps.setString(1, tc.getTestId());
                ps.setString(2, tc.getOrgId());
                ps.setBoolean(3, tc.getApplicable());
                ps.setString(4, tc.getOrgItemCode());
                ps.setString(5, tc.getCodeType());
                ps.addBatch();

            } else {
                ups.setBoolean(1, tc.getApplicable());
                ups.setString(2, tc.getOrgItemCode());
                ups.setString(3, tc.getCodeType());
                ups.setString(4, tc.getTestId());
                ups.setString(5, tc.getOrgId());
                ups.addBatch();
            }
        }
        do {
            int a[] = ps.executeBatch();
            status = DBUtil.checkBatchUpdates(a);
            if (!status)
                break;

            int b[] = ups.executeBatch();
            status = DBUtil.checkBatchUpdates(b);

        } while (false);

        return status;
    }

    public static int getNextSequence() throws SQLException {
        Connection con = null;
        con = DBUtil.getConnection();
        PreparedStatement ps = null;
        try {
            ps = con.prepareStatement("select nextval('resultlabel_seq')");
            return DBUtil.getIntValueFromDB(ps);
        } finally {
            con.close();
            ps.close();
        }

    }

    public static final String CATEGORY_WISE_TESTS = "select d.test_id, d.test_name from diagnostics d join"
            + " diagnostics_departments dd on d.ddept_id=dd.ddept_id	and d.status='A' and dd.category=? "
            + " order by d.test_name";

    public static List getTests(String category) throws SQLException {
        return DBUtil.queryToDynaList(CATEGORY_WISE_TESTS, category);
    }

    public static final String ALL_TEST_PAYMENTS_QUERY = "SELECT tpm.id,dd.ddept_name,dd.ddept_id,dia.test_id,dia.test_name,"
            + " dept.dept_id,dept.dept_name,doc.doctor_id,doc.doctor_name,"
            + " (case when payment_in_percent='true' then 't' else 'f' end) as payment_in_percent, test_payment,tpm.status"
            + " from test_payment_master tpm join diagnostics dia using(test_id) join diagnostics_departments dd using(ddept_id)"
            + " join doctors doc on doc.doctor_id = tpm.doc_id join department dept using(dept_id) ";

    public static List<BasicDynaBean> getAllTestPaymentDetails()
            throws SQLException {
        return DBUtil.queryToDynaList(ALL_TEST_PAYMENTS_QUERY);
    }

    public static final String ALL_SERVICE_PAYMENTS_QUERY = "SELECT spm.id,sd.department,ser.service_name,ser.service_id,"
            + " dept.dept_id,dept.dept_name,doc.doctor_id,doc.doctor_name,"
            + " (case when payment_in_percent='true' then 't' else 'f' end) as payment_in_percent, service_payment,spm.status"
            + " from service_payment_master spm join services ser using(service_id) "
            + " join services_departments sd on sd.department = ser.dept_name "
            + " join doctors doc on doc.doctor_id = spm.doc_id join department dept using(dept_id) ";

    public static List<BasicDynaBean> getAllServicePaymentDetails()
            throws SQLException {
        return DBUtil.queryToDynaList(ALL_SERVICE_PAYMENTS_QUERY);
    }

    private static final String APP_TESTS_FOR_RATEPLAN = " select d.test_name, d.test_id from  diagnostics d "
            + " join test_org_details tod on tod.org_id=? and tod.applicable and tod.test_id=d.test_id";

    public static List<BasicDynaBean> getAppTestsForRatePlan(String ratePlan)
            throws SQLException {
        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(APP_TESTS_FOR_RATEPLAN);
            ps.setString(1, ratePlan);
            return DBUtil.queryToDynaList(ps);
        } finally {
            DBUtil.closeConnections(con, ps);
        }
    }

    private static String GET_ALL_TESTS = " select test_name from diagnostics ";

    public static List getAllTests() {
        PreparedStatement ps = null;
        ArrayList testList = null;
        Connection con = null;

        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(GET_ALL_TESTS);
            testList = DBUtil.queryToArrayList1(ps);
        } catch (SQLException e) {
            Logger.log(e);
        } finally {
            DBUtil.closeConnections(con, ps);
        }

        return testList;
    }

    private static final String ALL_LABORATORY_TESTS = "select d.test_id,d.test_name from  diagnostics d,diagnostics_departments dd "
            + " where d.ddept_id=dd.ddept_id and dd.category='DEP_LAB' and d.status='A'";

    public static ArrayList getAllLaboratoryTests() throws SQLException {
        ArrayList l = new ArrayList();

        Connection con = null;
        con = DBUtil.getReadOnlyConnection();
        PreparedStatement ps = con.prepareStatement(ALL_LABORATORY_TESTS);
        l = DBUtil.queryToArrayList(ps);
        ps.close();
        con.close();

        return l;
    }

    private static final String ALL_RADIOLOGY_TESTS = "select d.test_id,d.test_name from  diagnostics d,diagnostics_departments dd "
            + " where d.ddept_id=dd.ddept_id and dd.category='DEP_RAD' and d.status='A'";

    public static ArrayList getAllRadiolodyTests() throws SQLException {
        ArrayList l = new ArrayList();

        Connection con = null;
        con = DBUtil.getReadOnlyConnection();
        PreparedStatement ps = con.prepareStatement(ALL_RADIOLOGY_TESTS);
        l = DBUtil.queryToArrayList(ps);
        ps.close();
        con.close();

        return l;
    }

    /*
     * Gets the basic test details and the charge associated with the test, only routine charges
     * are considered (we are not handling S and SC priorities).
     */
    private static final String TEST_CHARGE_QUERY = " SELECT " +
        "  d.diag_code, tod.item_code as rate_plan_code, d.test_name, d.test_id, d.status, " +
        "  d.ddept_id, d.conduction_format, dd.ddept_name, dc.charge, dd.category, dc.discount, " +
        "  d.conduction_applicable,tod.applicable,d.service_sub_group_id, tod.code_type, " +
        "  d.conducting_doc_mandatory, d.insurance_category_id,tod.org_id," +
        "  d.allow_rate_increase,d.allow_rate_decrease,dependent.test_name as dependent_test_name" +
        "  ,d.dependent_test_id, d.results_entry_applicable, d.consent_required, d.cross_match_test, d.allergen_test, d.rateplan_category_id, d.vat_percent, d.vat_option ,d.rateplan_category_id " +
        " FROM diagnostics d " +
        " LEFT JOIN diagnostics dependent ON (d.dependent_test_id = dependent.test_id) "+
        "  JOIN diagnostics_departments dd ON  (dd.ddept_id = d.ddept_id) " +
        "  JOIN diagnostic_charges dc ON (d.test_id = dc.test_id) " +
        "  JOIN test_org_details tod ON  (tod.test_id = dc.test_id AND tod.org_id = dc.org_name ) " +
        " WHERE dc.test_id =? AND dc.org_name =? " +
        "  AND dc.bed_type =? AND dc.priority = 'R' ";

    private static final String QUERY_FOR_TEST_CHARGE = " SELECT " +
	    "  d.diag_code, tod.item_code as rate_plan_code, d.test_name, d.test_id, d.status, " +
	    "  d.ddept_id, d.conduction_format, dd.ddept_name, dc.charge, dd.category, dc.discount, " +
	    "  d.conduction_applicable,tod.applicable,d.service_sub_group_id, tod.code_type, " +
	    "  d.conducting_doc_mandatory, d.insurance_category_id,tod.org_id," +
	    "  d.allow_rate_increase,d.allow_rate_decrease,dependent.test_name as dependent_test_name" +
	    "  ,d.dependent_test_id, d.results_entry_applicable, d.consent_required,  d.cross_match_test, d.allergen_test , " +
	    "  CASE WHEN is_outhouse_test(d.test_id,?) THEN 'O' ELSE 'I' END AS house_status,d.rateplan_category_id,  "+
	    " d.vat_percent, d.vat_option  "+
	    " FROM diagnostics d " +
	    " LEFT JOIN diagnostics dependent ON (d.dependent_test_id = dependent.test_id) "+
	    "  JOIN diagnostics_departments dd ON  (dd.ddept_id = d.ddept_id) " +
	    "  JOIN diagnostic_charges dc ON (d.test_id = dc.test_id) " +
	    "  JOIN test_org_details tod ON  (tod.test_id = dc.test_id AND tod.org_id = dc.org_name ) " +
	    " WHERE dc.test_id =? AND dc.org_name =? " +
	    "  AND dc.bed_type =? AND dc.priority = 'R' ";

    public static BasicDynaBean getTestDetails(String testId, String bedType, String orgId, int centerId)
        throws SQLException {
        PreparedStatement ps = null;
        BasicDynaBean bean = null;
        Connection con = null;
        try {
            con = DBUtil.getConnection();
            String generalorgid = "ORG0001";
            String generalbedtype = "GENERAL";

            if(bedType == null || bedType.equals("")) bedType = generalbedtype;
            if(orgId == null || orgId.equals("")) orgId = generalorgid;

            ps = con.prepareStatement(QUERY_FOR_TEST_CHARGE);
            ps.setInt(1, centerId);
            ps.setString(2, testId);
            ps.setString(3, orgId);
            ps.setString(4, bedType);
            List list = DBUtil.queryToDynaList(ps);
            if (!list.isEmpty()) {
                bean = (BasicDynaBean) list.get(0);
            }
            if (bean == null) {
                ps = con.prepareStatement(QUERY_FOR_TEST_CHARGE);
                ps.setInt(1, centerId);
                ps.setString(2, testId);
                ps.setString(3, generalorgid);
                ps.setString(4, generalbedtype);
                list = DBUtil.queryToDynaList(ps);
                bean = (BasicDynaBean) list.get(0);
            }
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return bean;
    }

    public static BasicDynaBean getTestDetails(Connection con,String testId, String bedType, String orgId)
    throws SQLException {
    PreparedStatement ps = null;
    BasicDynaBean bean = null;
    boolean flag = (con == null) ? true : false; 
    try {
    	if (flag)
    		con = DBUtil.getConnection();
        String generalorgid = "ORG0001";
        String generalbedtype = "GENERAL";

        if(bedType == null || bedType.equals("")) bedType = generalbedtype;
        if(orgId == null || orgId.equals("")) orgId = generalorgid;

        ps = con.prepareStatement(TEST_CHARGE_QUERY);
        ps.setString(1, testId);
        ps.setString(2, orgId);
        ps.setString(3, bedType);
        List list = DBUtil.queryToDynaList(ps);
        if (!list.isEmpty()) {
            bean = (BasicDynaBean) list.get(0);
        }
        if (bean == null) {
            ps = con.prepareStatement(TEST_CHARGE_QUERY);
            ps.setString(1, testId);
            ps.setString(2, generalorgid);
            ps.setString(3, generalbedtype);
            list = DBUtil.queryToDynaList(ps);
            bean = (BasicDynaBean) list.get(0);
        }
    } finally {
        DBUtil.closeConnections(flag ? con : null, ps);
    }
    return bean;
}
    
    public static BasicDynaBean getTestDetails(String testId, String bedType, String orgId)
    	    throws SQLException {
    	return getTestDetails(null, testId, bedType, orgId);
    }

    /*
     * Returns test charges for all bed types for the given rate plan. Each bed
     * type's charge will be a column in the returned list. Since the available
     * bed types can only be determined at runtime, we have to construct the
     * query dynamically.
     *
     * This is used for displaying the test charges for each bed in the main
     * test list master screen
     *
     */
    public static List<BasicDynaBean> getTestChargesForAllBedTypes(
            String orgId, List<String> bedTypes, List<String> testIds)
            throws SQLException {

        Connection con = null;
        PreparedStatement ps = null;

        try {
            con = DBUtil.getReadOnlyConnection();
            ps = getTestChargesForAllBedTypesStmt(con, orgId, bedTypes, testIds);
            return DBUtil.queryToDynaListWithCase(ps);

        } finally {
            DBUtil.closeConnections(con, ps);
        }
    }

    public static void getTestChargesForAllBedTypesCSV(String orgId,
            List<String> bedTypes, CSVWriter w) throws SQLException,
            IOException {

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBUtil.getReadOnlyConnection();
            ps = getTestChargesForAllBedTypesStmt(con, orgId, bedTypes, null);
            rs = ps.executeQuery();
            w.writeAll(rs, true);
            w.flush();

        } finally {
            DBUtil.closeConnections(con, ps, rs);
        }
    }
    public static PreparedStatement getTestChargesForAllBedTypesStmt(
            Connection con, String orgId, List<String> bedTypes,
            List<String> testIds) throws SQLException {

        StringBuilder query = new StringBuilder();
        query.append("SELECT d.test_id, test_name, applicable AS "
                    + DBUtil.quoteIdent("Rate Plan Applicable", true));
        query.append(", item_code AS "
                    + DBUtil.quoteIdent("Itemcode", true));
        for (String bedType : bedTypes) {
            query.append(", (SELECT charge FROM diagnostic_charges dc WHERE "
                    + " dc.test_id=d.test_id AND bed_type=? AND org_name=?) AS "
                    + DBUtil.quoteIdent(bedType, true));
            query.append(", (SELECT discount FROM diagnostic_charges dc WHERE "
                    + " dc.test_id=d.test_id AND bed_type=? AND org_name=?) AS "
                    + DBUtil.quoteIdent(bedType+"(Discount)", true));
        }
            query.append(" FROM diagnostics d  "
                    + " JOIN test_org_details tod ON (d.test_id=tod.test_id AND tod.org_id=? )");
        if(testIds ==  null)
            query.append(" WHERE d.status='A' ");

        SearchQueryBuilder.addWhereFieldOpValue(false, query, "d.test_id", "IN",
                testIds);


        PreparedStatement ps = null;
        ps = con.prepareStatement(query.toString());


        int i = 1;
        for (String bedType : bedTypes) {
            ps.setString(i++, bedType);
            ps.setString(i++, orgId);
            ps.setString(i++, bedType);
            ps.setString(i++, orgId);
        }
            ps.setString(i++, orgId);
        if (testIds != null) {
            for (String testId : testIds) {
                ps.setString(i++, testId);
            }
        }
        return ps;
    }

    /*
     * Search the tests: returns a PagedList suitable for a dashboard type list
     * of tests.
     */
    private static final String SEARCH_TEST_FIELDS = "SELECT *";

    private static final String SEARCH_TEST_COUNT = "SELECT count(*)";

    private static final String SEARCH_TEST_TABLES = " FROM (SELECT vat_percent, vat_option, tod.test_id, tod.applicable,"
        + " tod.item_code,d.diag_code AS alias_item_code, d.test_name, d.status,  d.ddept_id, d.conduction_format, dd.ddept_name, "
        + " tod.org_id, tod.code_type, d.service_sub_group_id,od.org_name,'diagnostics'::text as chargeCategory,  "
        + " tod.is_override, d.allergen_test "
        + " FROM test_org_details tod "
        + " JOIN diagnostics d ON (d.test_id = tod.test_id) "
        + " JOIN organization_details od on(od.org_id=tod.org_id) "
        + " JOIN diagnostics_departments dd ON (d.ddept_id = dd.ddept_id)) AS foo";

    public static PagedList searchTests(Map requestParams, Map pagingParams)
            throws ParseException, SQLException {

        Connection con = null;
        SearchQueryBuilder qb = null;
        Map map = requestParams;
        try {
            con = ConnectionFactory.getConnectionFactory().getConnection();
            qb = new SearchQueryBuilder(con, SEARCH_TEST_FIELDS, SEARCH_TEST_COUNT,
                        SEARCH_TEST_TABLES, pagingParams);
            qb.addFilterFromParamMap(map);
            qb.addSecondarySort("test_id");
            qb.build();

            return qb.getMappedPagedList();
        } finally {
        	DBUtil.closeConnectoinAndQueryBuilder(con, qb);
        }

    }

    private static final String BACKUP_CHARGES =
        " INSERT INTO diagnostic_charges_backup (user_name, bkp_time, org_name, bed_type, test_id, charge, discount) "
        + " SELECT ?, current_timestamp, org_name, bed_type, test_id, charge, discount "
        + " FROM diagnostic_charges WHERE org_name=?";

    public void backupCharges(String orgId, String user) throws SQLException {
        PreparedStatement ps = con.prepareStatement(BACKUP_CHARGES);
        ps.setString(1, user);
        ps.setString(2, orgId);
        ps.execute();
        ps.close();
    }

    public static String gettestcharge(String testid, String selectedorgname,
            String priority) {
        Connection con = null;
        String xml = null;
        Statement stm = null;
        PreparedStatement ps = null, ps1 = null;

        try {
            con = DBUtil.getConnection();
            ps = con
                    .prepareStatement("select ORG_ID from organization_details where org_name=?");
            ps.setString(1, selectedorgname);
            String orgid = DBUtil.getStringValueFromDB(ps);
            ps1 = con
                    .prepareStatement("select charge from diagnostic_charges where test_id =? and org_name=? AND BED_TYPE=? AND priority=?");
            ps1.setString(1, testid);
            ps1.setString(2, orgid);
            ps1.setString(3, Constants.getConstantValue("BEDTYPE"));
            ps1.setString(4, priority);
            xml = DBUtil.getStringValueFromDB(ps1);

        } catch (Exception e) {
            logger.error(e.fillInStackTrace().toString());
        } finally {
            try {
                if (ps != null)
                    ps.close();
                if (ps1 != null)
                    ps1.close();
                if (stm != null)
                    stm.close();
                if (con != null)
                    con.close();
            } catch (Exception e) {
                logger.error(e.fillInStackTrace().toString());
            }

        }

        return xml;
    }

    private static final String GET_TEST_DETAILS="" +
										    "SELECT  d.test_id,d.test_name,d.sample_needed,dd.ddept_name," +
										    "st.sample_type as type_of_specimen,d.conduction_format,d.status, " +
											"sg.service_group_name, ssg.service_sub_group_name," +
											"d.conduction_applicable, conducting_doc_mandatory, dcr.charge, " +
											"d.results_entry_applicable,d.diag_code,d.stat,d.consent_required, d.cross_match_test, d.allergen_test "+
										"FROM diagnostics d " +
										"JOIN diagnostics_departments dd USING(ddept_id) " +
										"JOIN service_sub_groups ssg using(service_sub_group_id) " +
										"JOIN service_groups sg using(service_group_id) " +
										"LEFT JOIN diagnostic_charges dcr ON (d.test_id = dcr.test_id AND dcr.org_name='ORG0001' " +
										"AND dcr.bed_type = 'GENERAL' AND dcr.priority = 'R') " +
										"LEFT JOIN sample_type st ON (st.sample_type_id = d.sample_type_id)  "+
										"WHERE d.status='A' ORDER BY test_id";

    private static final String GET_TEST_RESULT_LABEL_DETAILS="" +
									    "SELECT trm.resultlabel_id,d.test_name,dd.ddept_name, trm.resultlabel, " +
									    "TEXTCAT_COMMACAT(COALESCE(hcm.center_name,'') ) as center_name, " +
									    "dmm.method_name,trm.units,trm.display_order " +
									"FROM test_results_master trm " +
									"JOIN test_results_center trc ON (trc.resultlabel_id = trm.resultlabel_id) " +
									"JOIN hospital_center_master hcm ON (hcm.center_id = trc.center_id) " +
									"JOIN diagnostics d USING(test_id) " +
									"JOIN diagnostics_departments dd USING(ddept_id)" +
									"LEFT JOIN diag_methodology_master dmm ON(dmm.method_id = trm.method_id) " +
									"WHERE trc.status = 'A' AND d.conduction_format = 'V' " +
									"GROUP BY trm.resultlabel_id, d.test_name, dd.ddept_name, trm.resultlabel, " +
									"dmm.method_name, trm.units, trm.display_order ORDER BY test_id";

     public static List<BasicDynaBean> getAllTestDetails() throws SQLException {

        List list = null;
        PreparedStatement ps = null;
        Connection con = null;
        try {
	        con = DBUtil.getReadOnlyConnection();
	        ps = con.prepareStatement(GET_TEST_DETAILS);
	        list = DBUtil.queryToDynaList(ps);
        }finally{
        	DBUtil.closeConnections(con, ps);
        }

        return list;
    }

    public static List<BasicDynaBean> getAllTestResultLabelDetails()
            throws SQLException {

        List list = null;
        PreparedStatement ps = null;
        Connection con = null;
        try {
	        con = DBUtil.getReadOnlyConnection();
	        ps = con.prepareStatement(GET_TEST_RESULT_LABEL_DETAILS);
	        list = DBUtil.queryToDynaList(ps);
        }finally{
        	DBUtil.closeConnections(con, ps);
        }

        return list;
    }

   public static final String GET_RESULT_LABELS="SELECT * FROM test_results_master WHERE test_id=? AND resultlabel_id=?";

    public static BasicDynaBean getTestResultLabels(String testId,int resultLabelId) throws SQLException{

        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = DBUtil.getConnection();
            ps = con.prepareStatement(GET_RESULT_LABELS);
            ps.setString(1, testId);
            ps.setInt(2, resultLabelId);

            List l = DBUtil.queryToDynaList(ps);
            if (l.size() > 0) {
                return (BasicDynaBean) l.get(0);
            }
        } finally {
            DBUtil.closeConnections(con, ps);
        }
        return null;
    }
    public static final String GET_COUNT=" SELECT COUNT(*) FROM ";

    public static  BigDecimal getCount(String tableName) throws SQLException{

        BigDecimal count = BigDecimal.ZERO;
        PreparedStatement ps = null;
        Connection con = null;
        ResultSet rs = null;
        StringBuilder builder = new StringBuilder(GET_COUNT);
        try {
	        con = DBUtil.getReadOnlyConnection();
	        builder = builder.append(tableName);
	        ps = con.prepareStatement(builder.toString());
	        rs = ps.executeQuery();
	        while(rs.next()){
	            count=rs.getBigDecimal(1);
	        }
        } finally {
        	DBUtil.closeConnections(con, ps, rs);
        }
        return count;
    }


    private static final String TESTS_NAMESAND_iDS="select test_name,test_id from diagnostics";
               public static List getTestsNamesAndIds() throws SQLException{

                   return ConversionUtils.copyListDynaBeansToMap(DBUtil.queryToDynaList(TESTS_NAMESAND_iDS));
               }


               private static String GET_DIAG_DEPARTMENT="SELECT DDEPT_ID, DDEPT_NAME || '(' || d.dept_name || ')' as DDEPT_NAME " +
  			 "FROM DIAGNOSTICS_DEPARTMENTS dd " +
  			 "join department d on(d.dept_id = dd.category) " +
  			 "WHERE dd.STATUS='A' ORDER BY dd.category " ;

  	public static HashMap getDiagDepartmentHashMap() throws SQLException{

  		HashMap<String,String> dDeptHashMap =new HashMap <String,String>();
  		Connection con = null;
  		PreparedStatement ps = null;
  		ResultSet rs = null;
  		con=DBUtil.getReadOnlyConnection();
  		ps = con.prepareStatement(GET_DIAG_DEPARTMENT);
  		rs = ps.executeQuery();
  		while(rs.next()){
  			dDeptHashMap.put(rs.getString("ddept_name"), rs.getString("ddept_id"));
  		}
  		DBUtil.closeConnections(con, ps,rs);

  		return dDeptHashMap;
  	}

  	private static final String TEST_TEMPLATES = "SELECT d.test_name, dd.ddept_name, tf.format_name " +
  			"FROM test_template_master trm " +
  			"JOIN diagnostics d using(test_id) " +
  			"JOIN diagnostics_departments dd using(ddept_id) " +
  			"JOIN test_format tf on(trm.format_name = tf.testformat_id) " +
  			"WHERE d.conduction_format='T' order by test_id";

  	public static List<BasicDynaBean> getTestTemplates()throws SQLException {
  		Connection con = null;
  		PreparedStatement pstmt = null;
  		try {
  			con = DBUtil.getReadOnlyConnection();
  			pstmt = con.prepareStatement(TEST_TEMPLATES);
  			return DBUtil.queryToDynaList(pstmt);

  		} finally {
  			DBUtil.closeConnections(con, pstmt);

  		}

  	}

      private static String GET_DIAG_DEPS="SELECT DDEPT_ID, DDEPT_NAME " +
  	 "FROM DIAGNOSTICS_DEPARTMENTS dd " +
  	 "join department d on(d.dept_id = dd.category) " +
  	 "WHERE dd.STATUS='A' ORDER BY dd.category " ;

  	public static HashMap getDiagDepData() throws SQLException{

  		HashMap<String,String> dDeptHashMap =new HashMap <String,String>();
  		Connection con = null;
  		PreparedStatement ps = null;
  		ResultSet rs = null;
  		con=DBUtil.getReadOnlyConnection();
  		ps = con.prepareStatement(GET_DIAG_DEPS);
  		rs = ps.executeQuery();
  		while(rs.next()){
  			dDeptHashMap.put(rs.getString("ddept_name"), rs.getString("ddept_id"));
  		}
  		DBUtil.closeConnections(con, ps,rs);

  		return dDeptHashMap;
  	}

  	public static Map getTestResultIds() throws SQLException {
  		Connection con = null;
  		Map<Integer, String> tRstMap = new HashMap<Integer, String>();
  		PreparedStatement pstmt = null;
  		try {
  			con = DBUtil.getReadOnlyConnection();
  			pstmt = con.prepareStatement("select * from test_results_master");
  			List l = DBUtil.queryToDynaList(pstmt);
  			Iterator it = l.iterator();
  			while(it.hasNext()) {
  				BasicDynaBean bean = (BasicDynaBean)it.next();
  				tRstMap.put((Integer)bean.get("resultlabel_id"), (String)bean.get("test_id"));
  			}

  		} finally {
  			DBUtil.closeConnections(con, pstmt);
  		}
  		return tRstMap;
  	}

  	public static Map<String, String> getTestTmtMasterData() throws SQLException {
  		Connection con = null;
  		PreparedStatement pstmt = null;
  		ResultSet rs = null;
  		Map<String, String> tmtMasterData = new HashMap<String, String>();
  		try {
  			con = DBUtil.getReadOnlyConnection();
  			pstmt = con.prepareStatement("select testformat_id,format_name FROM test_format");
  			rs = pstmt.executeQuery();
  			while (rs.next()) {
  				tmtMasterData.put(rs.getString(2), rs.getString(1));
  			}
  		} finally {
  			DBUtil.closeConnections(con, pstmt);

  		}
  		return tmtMasterData;
  	}

  	public static String getOrderAlias(String type, String deptId, String groupId, String subGrpId)
  	throws SQLException{

  		BasicDynaBean masterCounts = new OrderMasterDAO().getMastersCounts(type, deptId);
  		BasicDynaBean serviceGroup = new GenericDAO("service_groups").findByKey("service_group_id", new Integer(groupId));
  		BasicDynaBean serviceSubGroup = new GenericDAO("service_sub_groups").findByKey("service_sub_group_id",  new Integer(subGrpId));
  		String groupCode = (String)serviceGroup.get("service_group_code") == null?"":(String)serviceGroup.get("service_group_code");
  		String subGrpCode = (String)serviceSubGroup.get("service_sub_group_code") == null ?"":(String)serviceSubGroup.get("service_sub_group_code");
  		String count = (masterCounts == null)?"":masterCounts.get("count").toString();

  		return groupCode+subGrpCode+count;

  	}

    private static final String INSERT_TEST_CHARGE_PLUS = "INSERT INTO diagnostic_charges(test_id,org_name,charge,bed_type,priority,username)" +
		"(SELECT test_id,?,ROUND(charge + ?), bed_type, priority,? FROM diagnostic_charges WHERE org_name=?)";

    private static final String INSERT_TEST_CHARGE_MINUS = "INSERT INTO diagnostic_charges(test_id,org_name,charge,bed_type,priority,username)" +
		"(SELECT test_id,?, GREATEST(ROUND(charge - ?), 0), bed_type, priority,? FROM diagnostic_charges WHERE org_name=?)";

    private static final String INSERT_TEST_CHARGE_BY = "INSERT INTO diagnostic_charges(test_id,org_name,charge,bed_type,priority,username)" +
    	"(SELECT test_id,?,doroundvarying(charge,?,?), bed_type, priority,? FROM diagnostic_charges WHERE org_name=?)";

    private static final String INSERT_TEST_WITH_DISCOUNTS_BY = "INSERT INTO diagnostic_charges(test_id,org_name,charge,discount,bed_type,priority,username)" +
	"(SELECT test_id,?,doroundvarying(charge,?,?), doroundvarying(discount,?,?), bed_type, priority,? FROM diagnostic_charges WHERE org_name=?)";

    public static boolean addOrgForTests(Connection con,String newOrgId,String varianceType,
    		Double varianceValue,BigDecimal varianceBy,boolean useValue,String baseOrgId,
    		Double nearstRoundOfValue,String userName,String orgName) throws Exception{

    	return addOrgForTests(con,newOrgId,varianceType, varianceValue,varianceBy,useValue,baseOrgId,
        		nearstRoundOfValue,userName,orgName,false);

    }

    public static boolean addOrgForTests(Connection con,String newOrgId,String varianceType,
		Double varianceValue,BigDecimal varianceBy,boolean useValue,String baseOrgId,
		Double nearstRoundOfValue,String userName,String orgName, boolean updateDiscounts) throws Exception{
   		boolean status = false;
   		PreparedStatement ps = null;
   		GenericDAO.alterTrigger(con, "DISABLE", "diagnostic_charges", "z_diagnostictest_charges_audit_trigger");

		if(useValue){
			if(varianceType.equals("Incr")){
				ps = con.prepareStatement(INSERT_TEST_CHARGE_PLUS);
				ps.setString(1,newOrgId);
				ps.setDouble(2,varianceValue);
				ps.setString(3, userName);
				ps.setString(4,baseOrgId);

				int i = ps.executeUpdate();
				logger.debug("INSERT_TEST_CHARGE_PLUS {}",i);
				if(i>=0)status = true;
			}else{
				ps = con.prepareStatement(INSERT_TEST_CHARGE_MINUS);
				ps.setString(1,newOrgId);
				ps.setDouble(2,varianceValue);
				ps.setString(3, userName);
				ps.setString(4,baseOrgId);

				int i = ps.executeUpdate();
				logger.debug("INSERT_TEST_CHARGE_MINUS {}",i);
				if(i>=0)status = true;
			}
		 }else{
			if(!varianceType.equals("Incr")){
				varianceBy =(varianceBy).negate();
			}
/*				 ps = con.prepareStatement(INSERT_TEST_CHARGE_BY);
				 ps.setString(1, newOrgId);
				 ps.setBigDecimal(2, new BigDecimal(varianceBy));
				 ps.setBigDecimal(3, new BigDecimal(nearstRoundOfValue));
				 ps.setString(4, userName);
				 ps.setString(5, baseOrgId);

				 int i = ps.executeUpdate(); */
			int i = insertChargesByPercent(con, newOrgId, baseOrgId, userName, varianceBy, nearstRoundOfValue, updateDiscounts);
			logger.debug("INSERT_TEST_CHARGE_MINUS {}",i);
			if(i>=0)status = true;
		}
		if (null != ps) ps.close();

		status &= new AuditLogDAO("Master","diagnostics_charges_jaudit_log").
		logMasterChanges(con, userName, "INSERT","org_name", orgName);

   		return status;
	}
    private static int insertChargesByPercent(Connection con, String newOrgId, String baseOrgId, String userName,
            BigDecimal varianceBy, Double nearstRoundOfValue, boolean updateDiscounts) throws Exception {

	    int ndx = 1;
	    int numCharges = 1;

	    PreparedStatement pstmt = null;
	    StringBuilder builder = new StringBuilder(updateDiscounts ?
				INSERT_TEST_WITH_DISCOUNTS_BY : INSERT_TEST_CHARGE_BY);
	    try {
			pstmt = con.prepareStatement(builder.toString());
			pstmt.setString(ndx++, newOrgId);

			for (int i = 0; i < numCharges; i++) {
		        pstmt.setBigDecimal(ndx++, varianceBy);
		        pstmt.setBigDecimal(ndx++, BigDecimal.valueOf(nearstRoundOfValue));
			}

			if (updateDiscounts) { // go one more round setting the parameters
		        for (int i = 0; i < numCharges; i++) {
	                pstmt.setBigDecimal(ndx++, varianceBy);
	                pstmt.setBigDecimal(ndx++, BigDecimal.valueOf(nearstRoundOfValue));
		        }
			}

			pstmt.setString(ndx++, userName);
			pstmt.setString(ndx++, baseOrgId);

			return pstmt.executeUpdate();

	    } finally {
            if (null != pstmt) pstmt.close();
	    }
    }


    private static final String INSERT_TEST_CODES_FOR_ORG = "INSERT INTO test_org_details " +
		    	"	SELECT test_id, ?, applicable, item_code, code_type, ?, 'N'" +
				"	FROM test_org_details WHERE  org_id = ?";

    public static boolean addOrgCodesForTests(Connection con,String newOrgId,String varianceType,
			Double varianceValue,BigDecimal varianceBy,boolean useValue,String baseOrgId,
			Double nearstRoundOfValue, String userName) throws Exception{
	 		boolean status = false;
	 		PreparedStatement ps = null;
            BasicDynaBean obean = new OrgMasterDAO().findByKey(con, "org_id", newOrgId);
            String rateSheetId = ("N".equals((String)obean.get("is_rate_sheet")) ? baseOrgId : null);
			try{
		   		ps = con.prepareStatement(INSERT_TEST_CODES_FOR_ORG);
				ps.setString(1,newOrgId);
				ps.setString(2, rateSheetId);
				ps.setString(3, baseOrgId);

				int i = ps.executeUpdate();
				logger.debug("INSERT_TEST_CODES_FOR_ORG {}",i);
				if(i>=0)status = true;

			}finally{
				if (ps != null)
					ps.close();
			}
 		return status;
	 }



	private static final String UPDATE_DIAG_CHARGES_PLUS = "UPDATE diagnostic_charges totab SET " +
		" charge = round(fromtab.charge + ?)" +
		" FROM diagnostic_charges fromtab" +
		" WHERE totab.org_name = ? AND fromtab.org_name = ?" +
		" AND totab.test_id = fromtab.test_id AND totab.bed_type = fromtab.bed_type AND totab.is_override='N'";

	private static final String UPDATE_DIAG_CHARGES_MINUS = "UPDATE diagnostic_charges totab SET " +
		" charge = GREATEST(round(fromtab.charge - ?), 0)" +
		" FROM diagnostic_charges fromtab" +
		" WHERE totab.org_name = ? AND fromtab.org_name = ?" +
		" AND totab.test_id = fromtab.test_id AND totab.bed_type = fromtab.bed_type AND totab.is_override='N'";

	private static final String UPDATE_DIAG_CHARGES_BY = "UPDATE diagnostic_charges totab SET " +
		" charge = doroundvarying(fromtab.charge,?,?)" +
		" FROM diagnostic_charges fromtab" +
		" WHERE totab.org_name = ? AND fromtab.org_name = ?" +
		" AND totab.test_id = fromtab.test_id AND totab.bed_type = fromtab.bed_type AND totab.is_override='N'";

	public static boolean updateOrgForTests(Connection con,String orgId,String varianceType,
		Double varianceValue,BigDecimal varianceBy,boolean useValue,String baseOrgId,
		Double nearstRoundOfValue,String userName,String orgName ) throws SQLException, IOException {

			boolean status = false;
			PreparedStatement pstmt = null;
			GenericDAO.alterTrigger("DISABLE", "diagnostic_charges", "z_diagnostictest_charges_audit_trigger");

			if (useValue) {

			if (varianceType.equals("Incr"))
				pstmt = con.prepareStatement(UPDATE_DIAG_CHARGES_PLUS);
			else
				pstmt = con.prepareStatement(UPDATE_DIAG_CHARGES_MINUS);

			pstmt.setBigDecimal(1, new BigDecimal(varianceValue));
			pstmt.setString(2, orgId);
			pstmt.setString(3, baseOrgId);

			int i = pstmt.executeUpdate();
			if (i>=0) status = true;

			} else {

			pstmt = con.prepareStatement(UPDATE_DIAG_CHARGES_BY);
			if (!varianceType.equals("Incr"))
			varianceBy = varianceBy.negate();

			pstmt.setBigDecimal(1, varianceBy);
			pstmt.setBigDecimal(2, new BigDecimal(nearstRoundOfValue));
			pstmt.setString(3, orgId);
			pstmt.setString(4, baseOrgId);

			int i = pstmt.executeUpdate();
			if (i>=0) status = true;

			}
			status &= new AuditLogDAO("Master","diagnostics_charges_jaudit_log").
			logMasterChanges(con, userName, "UPDATE","org_name", orgName);

			pstmt.close();

		return status;
	}

	private static final String TEST_ORG_DETAILS =
		" select d.*, dorg.org_id, dorg.applicable, dorg.item_code, dorg.code_type,od.org_name, dorg.base_rate_sheet_id " +
		" FROM diagnostics  d " +
		" JOIN test_org_details dorg on dorg.test_id = d.test_id "+
		" JOIN organization_details od on (od.org_id = dorg.org_id) "+
		" WHERE d.test_id=? and dorg.org_id=? ";

	public static BasicDynaBean getTestOrgDetails(String testId, String orgId) throws SQLException {
		Connection con = null;
		PreparedStatement ps = null;

		try{
			con = DBUtil.getReadOnlyConnection();
			ps = con.prepareStatement(TEST_ORG_DETAILS);
			ps.setString(1, testId);
			ps.setString(2, orgId);
			return DBUtil.queryToDynaBean(ps);
		}finally {
			DBUtil.closeConnections(con, ps);
		}
	}

	private static final String GET_ALL_CHARGES_FOR_ORG =
		" select test_id,bed_type,charge, discount from diagnostic_charges where org_name=? " ;

	public static List<BasicDynaBean> getAllChargesForOrg(String orgId, String testId) throws SQLException {

		Connection con = null;
		PreparedStatement ps = null;

		try {
			con = DBUtil.getReadOnlyConnection();
			ps = con.prepareStatement(GET_ALL_CHARGES_FOR_ORG + "and test_id=? ");
			ps.setString(1, orgId);
			ps.setString(2, testId);
			return DBUtil.queryToDynaList(ps);

		} finally {
			DBUtil.closeConnections(con, ps);
		}
	}

	private static final String GET_DERIVED_RATE_PLAN_DETAILS = "select rp.org_id,od.org_name, "+
			" case when rate_variation_percent<0 then 'Increase By' else 'Decrease By' end as discormarkup, "+
			" rate_variation_percent,round_off_amount,tod.applicable,tod.test_id,rp.base_rate_sheet_id "+
			" from rate_plan_parameters rp "+
			" join organization_details od on(od.org_id=rp.org_id) "+
			" join test_org_details tod on (tod.org_id = rp.org_id) "+
			" where rp.base_rate_sheet_id =?  and test_id=? ";

	public static List<BasicDynaBean> getDerivedRatePlanDetails(String baseRateSheetId,String testId)throws SQLException {
		Connection con  = null;
		PreparedStatement ps = null;
		try {
			con = DBUtil.getReadOnlyConnection();
			ps = con.prepareStatement(GET_DERIVED_RATE_PLAN_DETAILS);
			ps.setString(1, baseRateSheetId);
			ps.setString(2, testId);
			return DBUtil.queryToDynaList(ps);
		} finally {
			DBUtil.closeConnections(con, ps);
		}
	}

	private static final String GET_AVAILABLE_CENTERS =
			" SELECT distinct(hcm.center_id), hcm.center_name " +
			" FROM hospital_center_master hcm " ;

	public static List getCenterDetails(String[] resultsCenterNames)throws Exception {
		StringBuilder builder = new StringBuilder(GET_AVAILABLE_CENTERS);
		DBUtil.addWhereFieldInList(builder, "center_name", Arrays.asList(resultsCenterNames),false);
		Connection con = DBUtil.getConnection();
		PreparedStatement ps = null;
		try {
			ps = con.prepareStatement(builder.toString());
			int i=1;
			if (resultsCenterNames != null) {
				for (String val : resultsCenterNames) {
					ps.setString(i++, val.trim());
				}
			}
			return DBUtil.queryToDynaList(ps);
		} finally {
			DBUtil.closeConnections(con, ps);
		}
	}

    public static int getResultCenterNextSequence() throws SQLException {
        Connection con = null;
        con = DBUtil.getConnection();
        PreparedStatement ps = null;
        try {
            ps = con.prepareStatement("select nextval('test_results_center_seq')");
            return DBUtil.getIntValueFromDB(ps);
        } finally {
            con.close();
            ps.close();
        }
    }

	public static HashMap getAvailableCenters() throws SQLException{

		HashMap<String,String> availableCenterHashMap =new HashMap <String,String>();
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		con=DBUtil.getReadOnlyConnection();
		ps = con.prepareStatement(GET_AVAILABLE_CENTERS);
		rs = ps.executeQuery();
		while(rs.next()){
			availableCenterHashMap.put(rs.getString("center_name"), rs.getString("center_id"));
		}
		DBUtil.closeConnections(con, ps,rs);

		return availableCenterHashMap;
	}

	private static final String GET_SAVED_CENTERS =
			" SELECT hcm.center_name " +
			" FROM hospital_center_master hcm " +
			" JOIN test_results_center trc ON (trc.center_id = hcm.center_id) " +
			" WHERE trc.resultlabel_id = ? " ;

	public static List<String> getSavedCenters(Integer resultLabelID)throws SQLException {
		return DBUtil.queryToList(GET_SAVED_CENTERS, resultLabelID);
	}

    private static final String DELETE_RESULTS_CENTER = "DELETE FROM test_results_center WHERE resultlabel_id = ? AND center_id = ? ";

    public static boolean deleteResultsCenter(int resultLabelId, int centerId) throws SQLException {
        boolean status = false;
        Connection con = null;
        con = DBUtil.getConnection();
        PreparedStatement ps = con.prepareStatement(DELETE_RESULTS_CENTER);
        ps.setInt(1, resultLabelId);
        ps.setInt(2, centerId);
        int a = ps.executeUpdate();
        if (a >= 0) {
            status = true;
        }
        return status;
    }

    private static final String DELETE_NOGROWTHTEMPLATES = "DELETE FROM test_no_growth_template_master where test_id=?";

	public boolean deleteNogrowthTemplate(String testId) throws SQLException {
		 boolean status = false;
	        PreparedStatement ps = con.prepareStatement(DELETE_NOGROWTHTEMPLATES);
	        ps.setString(1, testId);

	        int a = ps.executeUpdate();
	        if (a >= 0) {
	            status = true;
	        }

	        return status;
	}

	private static final String INSERT_NOGROWTHTEMPLATES = "INSERT INTO test_no_growth_template_master(test_id,format_name)"
        + " values(?,?)";

	public boolean insertNogrowthTemplate(String testId, String nogrowthtemplate) throws SQLException {
	    boolean success = false;
	    PreparedStatement ps = con.prepareStatement(INSERT_NOGROWTHTEMPLATES);

	    ps.setString(1, testId);
	    ps.setString(2, nogrowthtemplate);

	    int i = ps.executeUpdate();
	    if (i > 0) {
	        success = true;
	    }

	    return success;
	}

	public static String getNogrowthTemplate(String testId) throws SQLException {
		Connection con = DBUtil.getReadOnlyConnection();
        PreparedStatement ps = con
                .prepareStatement("SELECT format_name from test_no_growth_template_master where test_id=?");
        ps.setString(1, testId);
        String formatname = DBUtil.getStringValueFromDB(ps);
        ps.close();
        con.close();
        return formatname;
	}    

	private static final String AllergenAvailable = "select * from allergen_master where id not in (select allergen_id from diagnostics_allergen where test_id='";
 
    public static ArrayList getAllergenAvailable(String testId) {
    	String appendQuery = testId+"') and status='A' order by name";
		return DBUtil.queryToArrayList(AllergenAvailable+appendQuery);
	}

	private static final String AllergenSelected = "select am.* from allergen_master am join diagnostics_allergen da on (am.id = da.allergen_id and am.status='A'";

    public static ArrayList getAllergenSelected(String testId) {
    	String appendQuery = " and da.test_id='"+testId+"') order by am.name";
		return DBUtil.queryToArrayList(AllergenSelected+appendQuery);
	}

	private static final String GET_TEST_MICRO_TEMPLATES = "SELECT d.test_name, dd.ddept_name, mnot.nogrowth_template_name " +
		"FROM test_no_growth_template_master tngt " +
		"JOIN diagnostics d using(test_id) " +
		"JOIN diagnostics_departments dd using(ddept_id) " +
		"JOIN micro_nogrowth_template_master mnot ON (mnot.nogrowth_template_id::text = tngt.format_name) " +
		"WHERE d.conduction_format='M' order by test_id";

	public static List<BasicDynaBean> getTestMicroTemplates()throws SQLException {
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DBUtil.getReadOnlyConnection();
			pstmt = con.prepareStatement(GET_TEST_MICRO_TEMPLATES);
			return DBUtil.queryToDynaList(pstmt);

		} finally {
			DBUtil.closeConnections(con, pstmt);

		}

}
	private static String GET_TEST_TAT_DETAILS = "SELECT test_tat_id, ttc.center_id,h.center_name, standard_tat, std_tat_units, " +
			" CASE WHEN std_tat_units = 'D' THEN 'Days' WHEN std_tat_units = 'H' THEN 'Hours' "+
			" WHEN std_tat_units = 'M' THEN 'Minutes' ELSE '' END as tatunitdesc, priority, "+
			" CASE WHEN priority='S' THEN 'STAT' ELSE 'Regular' END as prioritydesc"+
			" FROM test_tat_center ttc"+
			" LEFT JOIN hospital_center_master h ON (h.center_id = ttc.center_id) "+
			" WHERE ttc.test_id = ? ORDER BY test_tat_id ";
	public static List<BasicDynaBean> getCenterWiseTATs(String testId) throws SQLException {
		Connection con = null;
		PreparedStatement ps = null;
		try {
			con = DBUtil.getConnection();
			ps = con.prepareStatement(GET_TEST_TAT_DETAILS);
			ps.setString(1, testId);
			return DBUtil.queryToDynaListDates(ps);
		}finally {
			DBUtil.closeConnections(con, ps);
		}
	}
	
	public boolean deleteHealthAutorityLabObservations(List<TestObservations> deletedTestObservations)
			throws SQLException, IOException {
		boolean sucess = true;
		GenericDAO dao = new GenericDAO("ha_test_results_master");
		BasicDynaBean bean = null;
		for (TestObservations testObservations : deletedTestObservations) {
			sucess &= dao.delete(con, "resultlabel_id", new Integer(testObservations.getResultlabel_id()));
		}
		return sucess;
	}
	

	private static final String INSERT_HEALTH_AUTHORITY_OBSERVATIONS = "INSERT INTO ha_test_results_master "
			+ " (resultlabel_id, code_type, result_code, result_units, health_authority, created_by, modified_by)"
			+ " VALUES (?,?,?,?,?,?,?) ";

	public boolean insertHealthAutorityLabObservations(List<TestObservations> testObservationsList) throws SQLException, IOException, Exception {

		PreparedStatement ps = con.prepareStatement(INSERT_HEALTH_AUTHORITY_OBSERVATIONS);
		boolean success = true;

		Iterator<TestObservations> it = testObservationsList.iterator();
		while (it.hasNext()) {
			TestObservations r = it.next();
			ps.setInt(1, r.getResultlabel_id());
			ps.setString(2, r.getCode_type());
			ps.setString(3, r.getResult_code());
			ps.setString(4, r.getUnits());
			ps.setString(5, r.getHealthAuthority());
			ps.setString(6, r.getUserId());
			ps.setString(7, r.getUserId());
			ps.addBatch();
		}
		int updates[] = ps.executeBatch();
		ps.close();
		for (int p = 0; p < updates.length; p++) {
			if (updates[p] <= 0) {
				success = false;
				logger.error("Failed to insert result at {} : {}", p, updates[p]);
				break;
			}
		}
		return success;
	}
	
	public boolean insertHealthAutorityLabObservations(TestObservations testObservations)
			throws SQLException, IOException, Exception {

		PreparedStatement ps = con.prepareStatement(INSERT_HEALTH_AUTHORITY_OBSERVATIONS);
		boolean success = true;
		try {
		ps.setInt(1, testObservations.getResultlabel_id());
		ps.setString(2, testObservations.getCode_type());
		ps.setString(3, testObservations.getResult_code());
		ps.setString(4, testObservations.getUnits());
		ps.setString(5, testObservations.getHealthAuthority());
		ps.setString(6, testObservations.getUserId());
		ps.setString(7, testObservations.getUserId());
		success = success && (ps.executeUpdate()>0);
		} catch(Exception e) {
			throw e;
		} finally {
			ps.close();
		}
		return success;
	}
	
	private static final String UPDATE_HEALTH_AUTHORITY_OBSERVATIONS= "UPDATE ha_test_results_master "
			+ " set code_type=?, result_code = ?, result_units=?, modified_by=? where resultlabel_id = ? and health_authority = ?";

	public boolean updateHealthAutorityLabObservations(List<TestObservations> testObservationsList) throws SQLException, IOException, Exception {

		PreparedStatement ps = con.prepareStatement(UPDATE_HEALTH_AUTHORITY_OBSERVATIONS);
		boolean success = true;

		Iterator<TestObservations> it = testObservationsList.iterator();
		while (it.hasNext()) {
			TestObservations r = it.next();
			ps.setString(1, r.getCode_type());
			ps.setString(2, r.getResult_code());
			ps.setString(3, r.getUnits());
			ps.setString(4, r.getUserId());
			ps.setInt(5, r.getResultlabel_id());
			ps.setString(6, r.getHealthAuthority());
			ps.addBatch();
		}
		int updates[] = ps.executeBatch();
		ps.close();
		for (int p = 0; p < updates.length; p++) {
			if (updates[p] <= 0) {
				success = false;
				logger.error("Failed to insert result at {} : {}", p, updates[p]);
				break;
			}
		}
		return success;
	}
	
	public boolean updateHealthAutorityLabObservations(TestObservations testObservations)
			throws SQLException, IOException, Exception {

		PreparedStatement ps = con.prepareStatement(UPDATE_HEALTH_AUTHORITY_OBSERVATIONS);
		boolean success = true;
		try {
			ps.setString(1, testObservations.getCode_type());
			ps.setString(2, testObservations.getResult_code());
			ps.setString(3, testObservations.getUnits());
			ps.setString(4, testObservations.getUserId());
			ps.setInt(5, testObservations.getResultlabel_id());
			ps.setString(6, testObservations.getHealthAuthority());
			success = success && (ps.executeUpdate() > 0);
		} catch (Exception e) {
			throw e;
		} finally {
			ps.close();
		}
		return success;
	}
	
	
	private static final String SELECT_HEALTH_AUTHORITY_OBSERVATIONS = "SELECT health_authority, code_type, result_code, result_units from ha_test_results_master where resultlabel_id = ?";
	
	public static Map<String, TestObservations> getResultLabelObservations(int resultLableId) throws SQLException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		Map<String, TestObservations> testObservationsMap = new HashMap<>();
		try {
			connection = DBUtil.getConnection();
			ps = connection.prepareStatement(SELECT_HEALTH_AUTHORITY_OBSERVATIONS);
			ps.setInt(1, resultLableId);
			rs = ps.executeQuery();
			while(rs.next()) {
				TestObservations testObservations = new TestObservations(rs.getString("health_authority"), rs.getString("code_type"), 
						rs.getString("result_code"), rs.getString("result_units"));
				testObservationsMap.put(rs.getString("health_authority"), testObservations);
			}

		} finally {
			DBUtil.closeConnections(connection, ps, rs);

		}
		return testObservationsMap;
	}
	
	public List<Integer> getResultLabelIds(String query) throws SQLException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		List<Integer> resultLabelIds = new ArrayList<>();
		try {
			connection = DBUtil.getConnection();
			ps = connection.prepareStatement(query);
			rs = ps.executeQuery();
			while(rs.next()) {
				resultLabelIds.add(rs.getInt(1));
			}

		} finally {
			DBUtil.closeConnections(connection, ps, rs);

		}
		return resultLabelIds;
	}
	
	
}
