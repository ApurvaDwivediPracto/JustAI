package com.insta.hms.diagnosticsmasters.addtest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.beanutils.BasicDynaBean;

import com.bob.hms.common.DBUtil;
import com.insta.hms.common.GenericDAO;

public class TestResultsDAO extends GenericDAO {

	public TestResultsDAO(){
		super("test_results_master");
	}

	private static final String RESULTS_FOR_EUIPMENT =
		"    SELECT d.test_name,resultlabel_id as id,resultlabel as name,units as units    " +
		"    FROM test_results_master trm                                                  " +
		"    JOIN diagnostics d  USING(test_id)  WHERE status = 'A'                        " ;

	public List<BasicDynaBean> listAll()throws SQLException{
		Connection con = null;
		PreparedStatement ps = null;
		try{
			con = DBUtil.getConnection();
			ps = con.prepareStatement(RESULTS_FOR_EUIPMENT);
			return DBUtil.queryToDynaList(ps);
		}finally{
			DBUtil.closeConnections(con, ps);
		}
	}

	public static List searchResults(String resultNameQuery)
	throws SQLException{
		Connection con = null;
		PreparedStatement ps = null;
		try{
			con = DBUtil.getConnection();
			ps = con.prepareStatement(RESULTS_FOR_EUIPMENT+" AND resultlabel ilike ? ");
			ps.setString(1, resultNameQuery);
			return DBUtil.queryToDynaList(ps);
		}finally{
			DBUtil.closeConnections(con, ps);
		}
	}

	 private static final String GET_RESULTS_LIST = " select test_id,units,display_order,trm.resultlabel_id," +
	 		" expr_4_calc_result, case when trm.method_id is not null then resultlabel|| '.' ||method_name " +
	 		" else resultlabel end as resultlabel, code_type,result_code,data_allowed,source_if_list," +
	 		" resultlabel_short, hl7_export_code,trm.method_id "+
	 		" FROM test_results_master trm "+
	 		" LEFT JOIN diag_methodology_master dm ON (dm.method_id = trm.method_id) "+
	 		" LEFT JOIN test_results_center trc ON (trc.resultlabel_id = trm.resultlabel_id) "+
	 		" WHERE trm.test_id=?";

	 private static final String GET_RESULTS_LIST_CENTER = " select test_id,units,display_order,trm.resultlabel_id," +
			" expr_4_calc_result, case when trm.method_id is not null then resultlabel|| '.' ||method_name " +
			" else resultlabel end as resultlabel, code_type,result_code,data_allowed,source_if_list," +
			" resultlabel_short, hl7_export_code,trm.method_id,trc.center_id "+
			" FROM test_results_master trm "+
			" LEFT JOIN diag_methodology_master dm ON (dm.method_id = trm.method_id) "+
			" LEFT JOIN test_results_center trc ON (trc.resultlabel_id = trm.resultlabel_id) "+
			" WHERE trm.test_id=? " ; //and (trc.center_id= ? OR trc.center_id = 0)

	 public List<BasicDynaBean> getResultsList(Connection con,String testId,int centerId) throws SQLException,Exception {
		 PreparedStatement ps = null;
		 try {
			 ps = con.prepareStatement(GET_RESULTS_LIST_CENTER);
			 ps.setString(1, testId);
			 //ps.setInt(2, centerId);
			 return DBUtil.queryToDynaList(ps);
		 }finally{
			 if(ps!=null) ps.close();
		 }
	 }

	 public BasicDynaBean getResultBean(String testId, String resultLabel, String methodId) throws SQLException {
		 Connection con = null;
		 PreparedStatement ps = null;
		 try{
			 con = DBUtil.getReadOnlyConnection();
			 ps = con.prepareStatement(GET_RESULTS_LIST + " AND trm.resultlabel=? AND trm.method_id =? " );
			 ps.setString(1, testId);
			 ps.setString(2, resultLabel);
			 ps.setInt(3, Integer.parseInt(methodId));
			 return DBUtil.queryToDynaBean(ps);
		 }finally{
			 DBUtil.closeConnections(con, ps);
		 }
	 }

	 private static final String GET_EXISTING_RESULTS_LIST = "SELECT * FROM test_results_master WHERE test_id = ? " +
	 		" AND resultlabel = ? ";

	 public List<BasicDynaBean> getExistingResultsList(String testId, String resultlabel, Object methodId) throws SQLException{
		 Connection con = null;
		 PreparedStatement ps = null;
		 try{
			 con = DBUtil.getReadOnlyConnection();
			 if(null == methodId) {
				 ps = con.prepareStatement(GET_EXISTING_RESULTS_LIST + " AND method_id is null");
				 ps.setString(1, testId);
				 ps.setString(2, resultlabel);
			 }
			 else {
				 ps = con.prepareStatement(GET_EXISTING_RESULTS_LIST + " AND method_id=? ");
				 ps.setString(1, testId);
				 ps.setString(2, resultlabel);
				 ps.setObject(3, methodId);
			 }

			 return DBUtil.queryToDynaList(ps);

		 }finally{
			 DBUtil.closeConnections(con, ps);
		 }

	 }
}
