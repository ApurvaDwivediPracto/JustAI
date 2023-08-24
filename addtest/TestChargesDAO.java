package com.insta.hms.diagnosticsmasters.addtest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.beanutils.BasicDynaBean;

import com.bob.hms.common.DBUtil;
import com.insta.hms.auditlog.AuditLogDAO;
import com.insta.hms.common.GenericDAO;
import com.insta.hms.master.rateplan.ItemChargeDAO;

public class TestChargesDAO extends ItemChargeDAO {
	public TestChargesDAO(){
		super("diagnostic_charges");
	}

    // Rate Plan Changes - Begin

	public static boolean addOrgCodesForItems(Connection con, String newOrgId, String baseOrgId, String userName) throws Exception {
		return AddTestDAOImpl.addOrgCodesForTests(con, newOrgId, null, null, null, false, baseOrgId, null, userName);
	}

	public static boolean addOrgForTests(Connection con,String newOrgId, String varianceType,
			Double varianceValue,BigDecimal varianceBy,boolean useValue,String baseOrgId,
			Double nearstRoundOfValue,String userName,String orgName, boolean updateDiscounts) throws Exception {
		return AddTestDAOImpl.addOrgForTests(con, newOrgId, varianceType, varianceValue, varianceBy, useValue,
				baseOrgId, nearstRoundOfValue, userName, orgName, updateDiscounts);
	}

    private static String INIT_ORG_DETAILS = "INSERT INTO test_org_details " +
		" (test_id, org_id, applicable, item_code, code_type, base_rate_sheet_id, is_override) " +
		" SELECT test_id, ?, false, null, null, null, 'N'" +
		" FROM diagnostics";

    private static String INIT_CHARGES = "INSERT INTO diagnostic_charges(test_id,org_name,bed_type," +
        "charge,priority,username)" +
        "(SELECT test_id, ?, abov.bed_type, 0.0, 'R', ? " +
        "FROM diagnostics d CROSS JOIN all_beds_orgs_view abov WHERE abov.org_id =? ) ";

    private static final String INSERT_CHARGES = "INSERT INTO diagnostic_charges(test_id,org_name,bed_type," +
    " charge,priority,username, is_override) " +
    " SELECT tc.test_id, ?, tc.bed_type, " +
    " doroundvarying(tc.charge, ?, ?), " +
    " tc.priority, " +
    " ?, 'N' " +
    " FROM diagnostic_charges tc, test_org_details tod, test_org_details todtarget " +
    " where tc.org_name = tod.org_id and tc.test_id = tod.test_id " +
    " and todtarget.org_id = ? and todtarget.test_id = tod.test_id and todtarget.base_rate_sheet_id = ? " +
    " and tod.applicable = true "+
    " and tc.org_name = ? ";

    private static final String UPDATE_CHARGES = "UPDATE diagnostic_charges AS target SET " +
        " charge = doroundvarying(tc.charge, ?, ?), " +
        " discount = doroundvarying(tc.discount, ?, ?), " +
        " priority = tc.priority, " +
	    " username = ?, is_override = 'N' " +
	    " FROM diagnostic_charges tc, test_org_details tod " +
	    " where tod.org_id = ? and tc.test_id = tod.test_id and tod.base_rate_sheet_id = ? and " +
	    " target.test_id = tc.test_id and target.bed_type = tc.bed_type and " +
	    " tod.applicable = true and target.is_override != 'Y'"+
	    " and tc.org_name = ? and target.org_name = ?";

    private static final String UPDATE_EXCLUSIONS = "UPDATE test_org_details AS target " +
	    " SET item_code = tod.item_code, code_type = tod.code_type," +
	    " applicable = true, base_rate_sheet_id = tod.org_id, is_override = 'N' " +
	    " FROM test_org_details tod WHERE tod.test_id = target.test_id and " +
	    " tod.org_id = ? and tod.applicable = true and target.org_id = ? and target.applicable = false and target.is_override != 'Y'";

    public boolean updateRatePlan(Connection con,String newOrgId, String baseOrgId,
					 String varianceType, BigDecimal variance, Double rndOff,
					 String userName, String orgName ) throws Exception {

	    boolean status = false;
		if(!varianceType.equals("Incr")) {
			variance = (variance).negate();
	    }

		BigDecimal varianceBy = variance;
        BigDecimal roundOff = new BigDecimal(rndOff);

	    Object updparams[] = {varianceBy, roundOff, varianceBy, roundOff, userName, newOrgId, baseOrgId, baseOrgId, newOrgId};
	    Object insparams[] = {newOrgId, varianceBy, roundOff, userName, newOrgId, baseOrgId, baseOrgId};
		status = updateExclusions(con, UPDATE_EXCLUSIONS, newOrgId, baseOrgId, true);
		if (status) status = updateCharges(con, UPDATE_CHARGES, updparams);

		postAuditEntry(con, "diagnostics_charges_jaudit_log", userName, orgName,"org_name");

		return status;

    }

    public boolean initRatePlan(Connection con, String newOrgId, String varianceType,
			BigDecimal varianceBy,String baseOrgId, Double roundOff, String userName, String orgName) throws Exception {
		boolean status = addOrgCodesForItems(con, newOrgId, baseOrgId, userName);
		if (status) status = addOrgForTests(con,newOrgId, varianceType,	0.0, varianceBy, false, baseOrgId,
				roundOff, userName, orgName, true);
		postAuditEntry(con, "diagnostics_charges_jaudit_log", userName, orgName,"org_name");
		return status;
    }
    private static final String REINIT_EXCLUSIONS = "UPDATE test_org_details as target " +
    " SET applicable = tod.applicable, base_rate_sheet_id = tod.org_id, " +
    " item_code = tod.item_code, code_type = tod.code_type, is_override = 'N' " +
    " FROM test_org_details tod WHERE tod.test_id = target.test_id and " +
    " tod.org_id = ? and target.org_id = ? and target.is_override != 'Y'";

    public boolean reinitRatePlan(Connection con, String newOrgId, String varianceType,
			BigDecimal variance,String baseOrgId, Double rndOff,String userName, String orgName) throws Exception {
		boolean status = false;
		if(!varianceType.equals("Incr")) {
			variance = variance.negate();
		}
		BigDecimal varianceBy = variance;
        BigDecimal roundOff = new BigDecimal(rndOff);

		Object updparams[] = {varianceBy, roundOff, varianceBy, roundOff,
								newOrgId, baseOrgId};
		status = updateExclusions(con, REINIT_EXCLUSIONS, newOrgId, baseOrgId, true);
		if (status) status = updateCharges(con,UPDATE_RATEPLAN_DIAG_CHARGES, updparams);
		return status;
    }


    private static String INIT_ITEM_ORG_DETAILS = "INSERT INTO test_org_details " +
    "(test_id, org_id, applicable, item_code, code_type, base_rate_sheet_id, is_override)" +
    "(SELECT ?, od.org_id, false, null, null, prspv.base_rate_sheet_id, 'N' FROM organization_details od " +
    " LEFT OUTER JOIN priority_rate_sheet_parameters_view prspv ON od.org_id = prspv.org_id )";

    private static String INIT_ITEM_CHARGES = "INSERT INTO diagnostic_charges(test_id,org_name,bed_type," +
    "charge, username, priority)" +
    "(SELECT ?, abov.org_id, abov.bed_type, 0.0, ?, 'R' FROM all_beds_orgs_view abov) ";
    public boolean initItemCharges(Connection con, String serviceId, String userName) throws Exception {

        boolean status = false;
        //  disableAuditTriggers("service_master_charges", "z_services_charges_audit_trigger");
        status = initItemCharges(con, INIT_ITEM_ORG_DETAILS, INIT_ITEM_CHARGES, serviceId, userName);
        // postAuditEntry(con, "service_master_charges_audit_log", userName, orgName);
        return status;
    }

	public boolean updateOrgForDerivedRatePlans(Connection con,String[] ratePlanIds, String[] applicable,
			String testId, String orgItemCode, String codeType) throws Exception{
		return updateOrgForDerivedRatePlans(con,ratePlanIds, applicable, "test_org_details", "diagnostics",
				"test_id", testId, orgItemCode, codeType);
	}

	public  boolean updateChargesForDerivedRatePlans(Connection con,String baseRateSheetId, String[] ratePlanIds,
	    		String[] bedType, Double[] regularcharges,String testId, Double[] discounts, String[] applicable)throws Exception {

		return updateChargesForDerivedRatePlans(con,baseRateSheetId,ratePlanIds,bedType,
				regularcharges, "diagnostic_charges","test_org_details","diagnostics","test_id",
				testId, discounts,applicable);
	}

	private static final String GET_DERIVED_RATE_PALN_DETAILS = "select rp.org_id,od.org_name, "+
		" case when rate_variation_percent<0 then 'Decrease By' else 'Increase By' end as discormarkup, "+
		" rate_variation_percent,round_off_amount,tod.applicable,tod.test_id,rp.base_rate_sheet_id,tod.is_override "+
		" from rate_plan_parameters rp "+
		" join organization_details od on(od.org_id=rp.org_id) "+
		" join test_org_details tod on (tod.org_id = rp.org_id) "+
		" where rp.base_rate_sheet_id =?  and test_id=?  and tod.base_rate_sheet_id=? ";

	public List<BasicDynaBean> getDerivedRatePlanDetails(String baseRateSheetId,String testId)throws SQLException {
		return getDerivedRatePlanDetails(baseRateSheetId, "diagnostics", testId,GET_DERIVED_RATE_PALN_DETAILS);
	}

	private static final String UPDATE_RATEPLAN_DIAG_CHARGES = "UPDATE diagnostic_charges totab SET " +
		" charge = doroundvarying(fromtab.charge,?,?), discount = doroundvarying(fromtab.discount,?,?) " +
		" FROM diagnostic_charges fromtab" +
		" WHERE totab.org_name = ? AND fromtab.org_name = ?" +
		" AND totab.test_id = fromtab.test_id AND totab.bed_type = fromtab.bed_type AND totab.is_override='N'";

	public boolean updateTestChargesForDerivedRatePlans(String orgId,String varianceType,
		Double varianceValue,BigDecimal varianceBy,String baseOrgId,
		Double nearstRoundOfValue,String userName,String orgName,boolean upload ) throws SQLException, Exception {

		boolean success = false;
		Connection con = null;
		GenericDAO.alterTrigger("DISABLE", "diagnostic_charges", "z_diagnostictest_charges_audit_trigger");

		try {
			con = DBUtil.getConnection();
			con.setAutoCommit(false);

			if(upload) {
				GenericDAO rateParameterDao = new GenericDAO("rate_plan_parameters");
				List<BasicDynaBean> rateSheetList =  getRateSheetsByPriority(con, orgId, rateParameterDao);
				for (int i = 0; i < rateSheetList.size(); i++) {
					success = false;
					BasicDynaBean currentSheet = rateSheetList.get(i);
					BigDecimal variation = (BigDecimal)currentSheet.get("rate_variation_percent");
					String varType = (variation .compareTo(BigDecimal.ZERO) >= 0) ? "Incr" :"Decr";
					BigDecimal varBy = ((variation .compareTo(BigDecimal.ZERO) > 0 ? variation : variation.negate()));
					Double roundOff = new Double((Integer)currentSheet.get("round_off_amount"));
					if (i == 0) {
						success = reinitRatePlan(con, orgId, varType, varBy,
								(String)currentSheet.get("base_rate_sheet_id"), roundOff, userName, orgName);
					} else {
						success = updateRatePlan(con, orgId, (String)currentSheet.get("base_rate_sheet_id"), varType,
								varBy, roundOff, userName, orgName);
					}
				}
			} else {
				BigDecimal variance = varianceBy;
				BigDecimal roundoff = new BigDecimal(nearstRoundOfValue);
				Object updparams[] = {variance, roundoff, variance, roundoff, userName, orgId, baseOrgId, baseOrgId, orgId};
				success = updateCharges(con, UPDATE_CHARGES, updparams);
			}

			success &= new AuditLogDAO("Master","diagnostics_charges_jaudit_log").
			logMasterChanges(con, userName, "UPDATE","org_name", orgName);

		} finally {
			DBUtil.commitClose(con, success);
		}
		return success;
	}

	public static boolean updateChargesBasedOnNewRateSheet(Connection con, String orgId,BigDecimal varianceBy,
			String baseOrgId,Double nearstRoundOfValue, String testId)throws SQLException,Exception {
		boolean success = false;
		PreparedStatement ps = null;
		try {
			ps = con.prepareStatement(UPDATE_RATEPLAN_DIAG_CHARGES+ " AND totab.test_id = ? ");
			ps.setBigDecimal(1,varianceBy);
			ps.setBigDecimal(2, new BigDecimal(nearstRoundOfValue));
			ps.setBigDecimal(3, varianceBy);
			ps.setBigDecimal(4, new BigDecimal(nearstRoundOfValue));
			ps.setString(5, orgId);
			ps.setString(6, baseOrgId);
			ps.setString(7, testId);

			int i = ps.executeUpdate();
			if (i>=0) success = true;
		}finally {
			if (ps != null)
                ps.close();
		}
		return success;
	}

	 public boolean updateBedForRatePlan(Connection con, String ratePlanId,
				BigDecimal variance,String rateSheetId, Double rndOff, String bedType) throws Exception {

		 boolean status = false;

		BigDecimal varianceBy = variance;
        BigDecimal roundOff = new BigDecimal(rndOff);

		Object updparams[] = {varianceBy, roundOff, varianceBy, roundOff,
				ratePlanId, rateSheetId,bedType};
		status = updateCharges(con,UPDATE_RATEPLAN_DIAG_CHARGES + " AND totab.bed_type=? ", updparams);
		return status;
    }

}
