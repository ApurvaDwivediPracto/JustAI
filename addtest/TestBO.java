package com.insta.hms.diagnosticsmasters.addtest;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.beanutils.BasicDynaBean;

import com.bob.hms.common.DBUtil;
import com.bob.hms.common.RequestContext;
import com.insta.hms.common.CommonUtils;
import com.insta.hms.common.GenericDAO;
import com.insta.hms.diagnosticmodule.laboratory.ResultExpressionProcessor;
import com.insta.hms.diagnosticsmasters.Result;
import com.insta.hms.diagnosticsmasters.ResultRangesDAO;
import com.insta.hms.diagnosticsmasters.Test;
import com.insta.hms.diagnosticsmasters.TestObservations;
import com.insta.hms.diagnosticsmasters.TestTemplate;
import com.insta.hms.master.DiagResultsCenterApplicability.DiagResultsCenterApplicabilityDAO;

import sun.net.www.content.text.Generic;

public class TestBO {
	static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestBO.class);
	DiagResultsCenterApplicabilityDAO diagDao = new DiagResultsCenterApplicabilityDAO();

	public boolean addNewTest(Test test, ArrayList<Result> results,
			ArrayList<TestTemplate> templateList,StringBuilder msg, Map requestMap)
		throws Exception {

		Connection con = DBUtil.getConnection();
		con.setAutoCommit(false);
		AddTestDAOImpl dao = new AddTestDAOImpl(con);
		boolean success = true;
		ResultExpressionProcessor processor = new ResultExpressionProcessor();
		int centerId = RequestContext.getCenterId();
		try {
			outer:do {
				String genTestId = dao.getNextTestId();
				test.setTestId(genTestId);
				success = dao.insertTest(test);
				if (!success) break;
				success = dao.insertHl7Interfaces(test.getTestId(),test.getHl7ExportInterface());
				if(!success) break;
				// standard tat center mapping..
				success = saveCenterLevelStandardTAT(requestMap,test,msg);
				if(!success)break;
				
				Iterator<Result> it = results.iterator();
				if (null != test.getReportGroup() && test.getReportGroup().equals("V") && it.hasNext()) {
					while (it.hasNext()) {
						Result r = it.next();
						r.setTestId(genTestId);
						
						List centersList = new ArrayList();
						Integer resultLabelId = Integer.parseInt(r.getResultlabel_id());
						centersList = ResultRangesDAO.CentersForResults(con,r.getTestId(), resultLabelId);
						
						if ( !r.getExpression().isEmpty() && (centersList.contains(String.valueOf(centerId)) || centerId == 0) ){
							processor.setValidExpr(processor.istExpressionValid(con, r.getTestId(), r.getExpression()));
							success = processor.isValidExpr();

							if(!success){
								msg = msg.append("The expression for result : "+r.getResultLabel()+" is "+ r.getExpression()+" ," +
										"which is not a valid expression or results are mapped to different center.");
								break;
							}
						}
					}
					success &= dao.insertResults(results);
					success &= diagDao.insertResultsCenter(results,success,con);
					if (!success) break;
				}
				
				if(null != test.getReportGroup() && test.getReportGroup().equals("M")){
					success = dao.insertNogrowthTemplate(test.getTestId(), test.getNogrowthtemplate());
					if (!success) break;
				}
				
				if ( null != test.getReportGroup() && test.getReportGroup().equals("V")) {
					processor.setValidExpr(processor.istExpressionValid(
							con, test.getTestId(),test.getResultsValidation()));

					success = processor.isValidExpr();
					if (!success) {
						msg.append("The expression for Results Validation: "+test.getResultsValidation()+ " is not a valid expression.");
						break;
					}

					Map values = new HashMap<String, String>();
					values.put("results_validation", test.getResultsValidation().trim());

					success &= new GenericDAO("diagnostics").update(con,values,"test_id",test.getTestId()) > 0;
					if (!success) break;
				}

				Iterator<TestTemplate> tempit = templateList.iterator();
				if(null != test.getReportGroup() && test.getReportGroup().equals("T") && tempit.hasNext()){
					while(tempit.hasNext()){
						TestTemplate tt = tempit.next();
						tt.setTestId(genTestId);
						success = dao.insertTemplates(tt);
						if(!success)break outer;
					}
				}

				success &= new TestChargesDAO().initItemCharges(con, genTestId, test.getUserName());

			} while (false);

			logger.debug("Success value is {}" , success);
			if(success)
				dao.updateDiagnosticTimeStamp();

		} catch (Exception e) {
			success = false;
			throw(e);
		} finally {
			DBUtil.commitClose(con, success);
		}
		return success;
	}

	public boolean updateTestDetails(Test test, ArrayList<Result> addedResults,
			ArrayList<Result> modifiedResults, ArrayList<Result> deletedResults,
			ArrayList<TestTemplate> templateList,StringBuilder msg,Map requestMap) throws SQLException,IOException,ParseException,Exception {

		Connection con = DBUtil.getConnection();
		con.setAutoCommit(false);
		AddTestDAOImpl dao = new AddTestDAOImpl(con);
		boolean success = true;
		ResultExpressionProcessor processor = new ResultExpressionProcessor();
		int centerId = RequestContext.getCenterId();

		try {
			outer:do {
				if ( null != test.getReportGroup() && test.getReportGroup().equals("V")){
					if ( !test.getResultsValidation().isEmpty() ) {
						processor.setValidExpr(processor.istExpressionValid(
									con, test.getTestId(),test.getResultsValidation()));
						success = processor.isValidExpr();
						if(!success){
							msg = msg.append("The expression for Results Validation : "+test.getResultsValidation()+" is not valid.");
							break;
						}
					}
				}

				success = dao.updateTest(test);
				if (!success) break;

				success = dao.updateHl7Interface(test.getTestId(),test.getHl7ExportInterface());

				if(!success)break;
				// standard tat center mapping..
				success = saveCenterLevelStandardTAT(requestMap,test,msg);
				if(!success)break;
				
				if(null != test.getReportGroup() && test.getReportGroup().equals("V")){

					success &= dao.updateResults(modifiedResults);
					if (!success) break;

					success &= dao.deleteResults(deletedResults);
					/* result range with out a result label can not exist*/
		        	success &= dao.deleteResultRanges(con,deletedResults);
		        	success &= diagDao.deleteResultsCenter(deletedResults,success,con);
					if (!success) break;

					success &= dao.insertResults(addedResults);
					success &= diagDao.insertResultsCenter(addedResults,success,con);
					if (!success) break;

					Iterator<Result> it = modifiedResults.iterator();
					Result r = null;

					while(it.hasNext()) {
						r = it.next();
						
						List centersList = new ArrayList();
						Integer resultLabelId = Integer.parseInt(r.getResultlabel_id());
						centersList = ResultRangesDAO.CentersForResults(con,r.getTestId(), resultLabelId);
						if ( !r.getExpression().isEmpty() && (centersList.contains(String.valueOf(centerId)) || centerId == 0)){
							processor.setValidExpr(processor.istExpressionValid(con, r.getTestId(), r.getExpression()));
							success = processor.isValidExpr();

							if(!success){
								msg = msg.append("The expression for result : "+r.getResultLabel()+" is "+ r.getExpression()+" ," +
										"which is not a valid expression or results are mapped to different center.");
								break;
							}
						}
					}
					if (!success) break;
					
					it = addedResults.iterator();

					while (it.hasNext()) {
						r = it.next();
				List centersList = new ArrayList();
				Integer resultLabelId = Integer.parseInt(r.getResultlabel_id());
				centersList = ResultRangesDAO.CentersForResults(con,r.getTestId(), resultLabelId);
						if ( !r.getExpression().isEmpty()) {
							processor.setValidExpr(processor.istExpressionValid(con, r.getTestId(), r.getExpression()));
							success = processor.isValidExpr();

							if(!success){
								msg = msg.append("The expression for the result: "+r.getResultLabel()+" is "+r.getExpression()+" ," +
										" which is not a valid expression or results are mapped to different center.");
								break;
							}
						}
					}
					if (!success) break;

				}else if(null != test.getReportGroup() && test.getReportGroup().equals("M")){

					success = dao.deleteNogrowthTemplate(test.getTestId());
					success = dao.insertNogrowthTemplate(test.getTestId(), test.getNogrowthtemplate());
					if (!success) break;

				}else{
					success = dao.deleteTemplates(test.getTestId());
					Iterator<TestTemplate> tempit = templateList.iterator();
					if(null != test.getReportGroup() && test.getReportGroup().equals("T") && tempit.hasNext()){
						while(tempit.hasNext()){
							TestTemplate tt = tempit.next();
							success = dao.insertTemplates(tt);
							if(!success)break outer;
						}
					}
				}

			} while (false);
			if(success)
				dao.updateDiagnosticTimeStamp();

		} catch (SQLException e) {
			success = false;
			throw(e);
		} finally {
			DBUtil.commitClose(con, success);
		}
		return success;
	}

	public Map editTestCharges(String orgId,String testid)throws SQLException{

		Connection con = DBUtil.getConnection();
		AddTestDAOImpl dao = new AddTestDAOImpl(con);
		Map map = dao.editTestCharges(orgId,testid);
		con.close();

		return map;
	}


	public boolean updateTestCharge(ArrayList<TestCharge> tclist, String testId,
			String orgId, boolean disabled, String orgItemCode, String codeType)throws SQLException{
		boolean status = false;

		Connection con = DBUtil.getConnection();
		con.setAutoCommit(false);
		AddTestDAOImpl dao = new AddTestDAOImpl(con);
		ArrayList<TestCharge> codeList = new ArrayList<TestCharge>();

		status = dao.addOREditTestCharges(tclist);

		TestCharge itemCode = new TestCharge();
		itemCode.setApplicable(disabled);
		itemCode.setOrgItemCode(orgItemCode);
		itemCode.setOrgId(orgId);
		itemCode.setTestId(testId);
		itemCode.setCodeType(codeType);
		codeList.add(itemCode);
		status = dao.addOREditItemCode(codeList);
		if (status)
			dao.updateDiagnosticTimeStamp();
		DBUtil.commitClose(con, status);

		return status;
	}

	public boolean updateTestChargeList(ArrayList<TestCharge> chargeList) throws SQLException {
		Connection con = null;
		boolean success = false;
		try {
			con = DBUtil.getConnection();
			con.setAutoCommit(false);
			AddTestDAOImpl dao = new AddTestDAOImpl(con);
			success = dao.updateTestChargeList(chargeList);
		} finally {
			DBUtil.commitClose(con, success);
		}
		return success;
	}
	
	private boolean saveCenterLevelStandardTAT(Map requestMap, Test test, StringBuilder msg) throws SQLException, 
					IOException, NumberFormatException {
		
		boolean success = true;
		String[] testTATIds = (String[]) requestMap.get("test_tat_id");
		String[] centerIds = (String[]) requestMap.get("tat_center_id");
		String[] centerNames = (String[]) requestMap.get("tat_center_name");
		String[] stdTATs = (String[]) requestMap.get("standard_tat");
		String[] stdTATunits = (String[]) requestMap.get("std_tat_units");
		String[] priority = (String[]) requestMap.get("priority");
		
		String[] added = (String[]) requestMap.get("added");
		String[] edited = (String[]) requestMap.get("edited");
		String[] delItem = (String[]) requestMap.get("delItem");
		String testId = test.getTestId();
		
		GenericDAO testTATCenterDAO = new GenericDAO("test_tat_center");
		BasicDynaBean testTATCenterBean = null;
		Connection con = null;
		LinkedHashMap <String,Object> keys = new LinkedHashMap<String,Object>();
		
		try {
			con = DBUtil.getConnection();
			con.setAutoCommit(false);
			for(int i=0; i <centerIds.length; i++ ){
				if(null != centerIds[i] && !centerIds[i].equals("")) {
					testTATCenterBean = testTATCenterDAO.getBean();
					testTATCenterBean.set("test_id", testId);
					testTATCenterBean.set("center_id", Integer.parseInt(centerIds[i]));
					testTATCenterBean.set("standard_tat", Integer.parseInt(stdTATs[i]));
					testTATCenterBean.set("std_tat_units", stdTATunits[i]);
					testTATCenterBean.set("priority", priority[i]);
						
					List<BasicDynaBean> testTatbeanList = testTATCenterDAO.findAllByKey("test_id",testId);
					for (BasicDynaBean tatbean : testTatbeanList) {
			            if(tatbean.get("center_id").equals(Integer.parseInt(centerIds[i])) && tatbean.get("priority").equals(priority[i])
			            		&& added[i].equalsIgnoreCase("true")) {
			            	
			            	success = false;
							msg = msg.append("Center "+ centerNames[i] + ", is already mapped with same priority ");
							break;			
			            }
			           }
					if (added[i].equalsIgnoreCase("true") && delItem[i].equalsIgnoreCase("false")) {
							success &= testTATCenterDAO.insert(con, testTATCenterBean);
							
					} 
					else if (added[i].equalsIgnoreCase("false") && delItem[i].equalsIgnoreCase("true")) {						
						keys.put("test_tat_id", Integer.parseInt(testTATIds[i]));
						success &= testTATCenterDAO.delete(con, keys);
						
					} else if (edited[i].equalsIgnoreCase("true")) {
						keys.put("test_tat_id", Integer.parseInt(testTATIds[i]));
						success &= testTATCenterDAO.update(con, testTATCenterBean.getMap(), keys) > 0;
						
					}
					if (!success)
						break;
				}
				
			}
		} finally {
			DBUtil.commitClose(con, success);
		}
		
		return success;
	}

	public boolean updateTestObservations(ArrayList<TestObservations> addedTestObservations,
			ArrayList<TestObservations> modifiedTestObservations, ArrayList<TestObservations> deletedTestObservations)
			throws SQLException, IOException, ParseException, Exception {

		Connection con = DBUtil.getConnection();
		con.setAutoCommit(false);
		AddTestDAOImpl dao = new AddTestDAOImpl(con);
		boolean success = true;
		try {
			
			if(modifiedTestObservations.size() > 0) {
				Iterator<TestObservations> it = modifiedTestObservations.iterator();
				List<Integer> resultLabelIds = new ArrayList<Integer>(); 
				StringBuffer query = new StringBuffer("select distinct resultlabel_id from ha_test_results_master where resultlabel_id in (");
				while (it.hasNext()) {
					TestObservations testObservations = it.next();
					query.append(testObservations.getResultlabel_id()).append(",");
					resultLabelIds.add(testObservations.getResultlabel_id());
				}
				
				if(query.toString().endsWith(",")) {
					resultLabelIds = dao.getResultLabelIds(query.deleteCharAt(query.length()-1).append(")").toString());
				} else {
					resultLabelIds = dao.getResultLabelIds(query.append(")").toString());
				}
				
				it = modifiedTestObservations.iterator();
				while (it.hasNext()) {
					TestObservations testObservations = it.next();
					if(resultLabelIds.contains(testObservations.getResultlabel_id())) {
						Map<String,Object> keys = new HashMap<String, Object>();
						keys.put("resultlabel_id", testObservations.getResultlabel_id());
						keys.put("health_authority", testObservations.getHealthAuthority());
						if (null != new GenericDAO("ha_test_results_master").findByKey(keys)) {
							success &= dao.updateHealthAutorityLabObservations(testObservations);
						} else {
							dao.insertHealthAutorityLabObservations(testObservations);
						}
					} else {
						dao.insertHealthAutorityLabObservations(testObservations);
					}
				}
			}
			if(deletedTestObservations.size() > 0) 
				success &= dao.deleteHealthAutorityLabObservations(deletedTestObservations);
			if(addedTestObservations.size() > 0)
				success &= dao.insertHealthAutorityLabObservations(addedTestObservations);
		} catch (SQLException e) {
			success = false;
			throw (e);
		} finally {
			DBUtil.commitClose(con, success);
		}
		return success;
	}

	
}

