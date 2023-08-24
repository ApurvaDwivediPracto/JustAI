package com.insta.hms.diagnosticsmasters.addtest;

import java.math.BigDecimal;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;

public class AddTestForm extends ActionForm {

	static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AddTestForm.class);

	private static final long serialVersionUID = 1L;

	// the following are used in add/edit test
	private String addOrEdit;
	private String ddeptId;
	private String testName;
	private String sampleNeed;
	private Integer specimen;
	private String testConduct;
	private String testStatus;
	private String routineCharge;
	private String reportGroup;
	private String formatName[];
	private boolean conduction_applicable;
	private boolean results_entry_applicable;
	private String conducting_doc_mandatory;
	private String remarks;
	 private Integer rateplan_category_id; 
	private String resultOp[];
	private String resultLabel[];
	private String resultLabelShort[];
	private String order[];
	private String units[];
	private String hl7_interface[];
	private String refRange[];
	private String resultlabel_id[];

	private String testId;
	private String dep_test_name;
	private String dependent_test_id;

	// the following are used in edit charges
	private String diagCode;
	private String orgItemCode;
	private boolean applicable;
	private String chargeType;
	private String orgName;
	private String orgId;
	private String[] bedTypes;
	private Double[] regularCharges;//routine
	private Double[] discount;//discount
	private String updateTable;

	// the following are used in group udpate/import
	private String allBedTypes;
	private String allTests;
	private String[] selectTest;
	private String[] selectBedType;
	private String incType;
	private String amtType;
	private BigDecimal amount;
	private BigDecimal roundOff;

	private FormFile xlsChargeFile;
	//This is for test defination details upload
	private FormFile xlsTestFile;
	private String codeType;
	private int serviceSubGroupId;
	private String[] hl7ExportInterface;
	private String hl7ExportCode;

	private String sampleCollectionInstructions;
	private String expression[];
	private String conductionInstructions;

	private int insurance_category_id;
	private String preAuthReq;
	private String resultsValidation;
	private boolean allow_rate_increase;
	private boolean allow_rate_decrease;

	private String[] notApplicableRatePlans;
	private String stdTAT;
	private String stdTATUnits;
	private String[] conductingRoleIds;
	private String stat;
	private String nogrowthtemplate;
	private String consent_required;
	
	private boolean cross_match_test;
	private boolean allergen_test;
	private String testShortName;
    private BigDecimal vat_percent;
    private String vat_option;
    private String[] cancer_screening_id;
    private String is_sensitivity;
    private boolean covid_test;
    private boolean qrcode_required_test_report;
    private boolean allow_bulk_signoff;
    private String[] symptomsIds;

	public String[] getSymptomsIds() {
		return symptomsIds;
	}
	public void setSymptomsIds(String[] symptomsIds) {
		this.symptomsIds = symptomsIds;
	}
	public boolean isAllow_bulk_signoff() {
		return allow_bulk_signoff;
	}
	public void setAllow_bulk_signoff(boolean allow_bulk_signoff) {
		this.allow_bulk_signoff = allow_bulk_signoff;
	}
	public boolean isQrcode_required_test_report() {
		return qrcode_required_test_report;
	}
	public void setQrcode_required_test_report(boolean qrcode_required_test_report) {
		this.qrcode_required_test_report = qrcode_required_test_report;
	}
	public String getVat_option() {
		return vat_option;
	}
	public void setVat_option(String vat_option) {
		this.vat_option = vat_option;
	}
	public BigDecimal getVat_percent() {
		return vat_percent;
	}
	public void setVat_percent(BigDecimal vat_percent) {
		this.vat_percent = vat_percent;
	}
	public String getConsent_required() {
		return consent_required;
	}
	public void setConsent_required(String consent_required) {
		this.consent_required = consent_required;
	}
	public String[] getConductingRoleIds() {
		return conductingRoleIds;
	}
	public void setConductingRoleIds(String[] conductingRoleIds) {
		this.conductingRoleIds = conductingRoleIds;
	}
	public String[] getNotApplicableRatePlans() {
		return notApplicableRatePlans;
	}
	public void setNotApplicableRatePlans(String[] notApplicableRatePlans) {
		this.notApplicableRatePlans = notApplicableRatePlans;
	}
	public boolean isAllow_rate_decrease() {
		return allow_rate_decrease;
	}
	public void setAllow_rate_decrease(boolean allow_rate_decrease) {
		this.allow_rate_decrease = allow_rate_decrease;
	}
	public boolean isAllow_rate_increase() {
		return allow_rate_increase;
	}
	public void setAllow_rate_increase(boolean allow_rate_increase) {
		this.allow_rate_increase = allow_rate_increase;
	}
	public String getResultsValidation() {
		return resultsValidation;
	}
	public void setResultsValidation(String resultsValidation) {
		this.resultsValidation = resultsValidation;
	}
	public String getPreAuthReq() {
		return preAuthReq;
	}
	public void setPreAuthReq(String preAuthReq) {
		this.preAuthReq = preAuthReq;
	}
	public int getInsurance_category_id() {
		return insurance_category_id;
	}
	public void setInsurance_category_id(int insurance_category_id) {
		this.insurance_category_id = insurance_category_id;
	}
	public String getConductionInstructions() {
		return conductionInstructions;
	}
	public void setConductionInstructions(String conductionInstructions) {
		this.conductionInstructions = conductionInstructions;
	}
	public String[] getExpression() {
		return expression;
	}
	public void setExpression(String[] expression) {
		this.expression = expression;
	}
	public String getSampleCollectionInstructions() {
		return sampleCollectionInstructions;
	}
	public void setSampleCollectionInstructions(String sampleCollectionInstructions) {
		this.sampleCollectionInstructions = sampleCollectionInstructions;
	}
	// accessors
	public int getServiceSubGroupId() {
		return serviceSubGroupId;
	}
	public void setServiceSubGroupId(int serviceSubGroupId) {
		this.serviceSubGroupId = serviceSubGroupId;
	}
	public String[] getHl7ExportInterface() {
		return hl7ExportInterface;
	}
	public void setHl7ExportInterface(String[] v) {
		hl7ExportInterface = v;
	}

	public String[] getHl7_interface() {
		return hl7_interface;
	}
	public void setHl7_interface(String[] hl7_interface) {
		this.hl7_interface = hl7_interface;
	}
	public String getHl7ExportCode() { return hl7ExportCode; }
	public void setHl7ExportCode(String v) { hl7ExportCode = v; }

	public boolean isApplicable() { return applicable; }
	public void setApplicable(boolean applicable) { this.applicable = applicable; }

	public String getOrgItemCode() { return orgItemCode; }
	public void setOrgItemCode(String orgItemCode) { this.orgItemCode = orgItemCode; }

	public Double[] getRegularCharges() { return regularCharges; }
	public void setRegularCharges(Double[] regularCharges) { this.regularCharges = regularCharges; }

	public Double[] getDiscount() { return discount; }
	public void setDiscount(Double[] discount) { this.discount = discount; }

	public String[] getBedTypes() { return bedTypes; }
	public void setBedTypes(String[] bedTypes) { this.bedTypes = bedTypes; }

	public String getUpdateTable() { return updateTable; }
	public void setUpdateTable(String v) { updateTable = v; }

	public Integer getSpecimen() { return specimen; }
	public void setSpecimen(Integer v) { specimen = v; }

	public String getTestConduct() { return testConduct; }
	public void setTestConduct(String v) { testConduct = v; }

	public String[] getFormatName() { return formatName; }
	public void setFormatName(String[] v) { formatName = v; }

	public String getDdeptId() { return ddeptId; }
	public void setDdeptId(String v) { ddeptId = v; }

	public String getTestName() { return testName; }
	public void setTestName(String v) { testName = v; }

	public String getDiagCode() { return diagCode; }
	public void setDiagCode(String v) { diagCode = v; }

	public String getSampleNeed() { return sampleNeed; }
	public void setSampleNeed(String v) { sampleNeed = v; }

	public String getReportGroup() { return reportGroup; }
	public void setReportGroup(String v) { reportGroup = v; }

	public String getAddOrEdit() { return addOrEdit; }
	public void setAddOrEdit(String v) { addOrEdit = v; }

	public String getTestId() { return testId; }
	public void setTestId(String v) { testId = v; }

	public String getRoutineCharge() { return routineCharge; }
	public void setRoutineCharge(String v) { routineCharge = v; }

	public String[] getResultOp() { return resultOp; }
	public void setResultOp(String[] v) { resultOp = v; }

	public String[] getResultLabel() { return resultLabel; }
	public void setResultLabel(String[] v) { resultLabel = v; }

	public String[] getUnits() { return units; }
	public void setUnits(String[] v) { units = v; }

	public String[] getRefRange() { return refRange; }
	public void setRefRange(String[] v) { refRange = v; }

	public String[] getOrder() { return order;}
	public void setOrder(String[] order) { this.order = order;}

	public String getChargeType() {return chargeType;}
	public void setChargeType(String chargeType) {this.chargeType = chargeType;}

	public String getOrgId() {return orgId;}
	public void setOrgId(String orgId) {this.orgId = orgId;}

	public String getOrgName() {return orgName;}
	public void setOrgName(String orgName) {this.orgName = orgName;}

	public String getTestStatus() {	return testStatus;}
	public void setTestStatus(String testStatus) {this.testStatus = testStatus;}

	public String[] getResultlabel_id() {
		return resultlabel_id;
	}

	public void setResultlabel_id(String[] resultlabel_id) {
		this.resultlabel_id = resultlabel_id;
	}
	public String getAllBedTypes() { return allBedTypes; }
	public void setAllBedTypes(String v) { allBedTypes = v; }

	public String getAllTests() { return allTests; }
	public void setAllTests(String v) { allTests = v; }

	public String[] getSelectTest() { return selectTest; }
	public void setSelectTest(String[] v) { selectTest = v; }

	public String[] getSelectBedType() { return selectBedType; }
	public void setSelectBedType(String[] v) { selectBedType = v; }

	public String getIncType() { return incType; }
	public void setIncType(String v) { incType = v; }

	public String getAmtType() { return amtType; }
	public void setAmtType(String v) { amtType = v; }

	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal v) { amount = v; }

	public BigDecimal getRoundOff() { return roundOff; }
	public void setRoundOff(BigDecimal v) { roundOff = v; }

	public FormFile getXlsChargeFile() { return xlsChargeFile; }
	public void setXlsChargeFile(FormFile v) { xlsChargeFile = v; }

	public FormFile getXlsTestFile() {return xlsTestFile;}
	public void setXlsTestFile(FormFile v) {this.xlsTestFile = v;}



	public void reset(ActionMapping arg0, HttpServletRequest arg1) {
		try {
			arg1.setCharacterEncoding("UTF-8");
		} catch (Exception e) {}

		super.reset(arg0, arg1);
		this.specimen = null;
		this.testConduct = null;
		this.formatName = null;
		this.ddeptId = null;
		this.testName =null;
		this.diagCode = null;
		this.sampleNeed = null;
		this.reportGroup = null;
		this.testStatus = null;

		this.resultOp = null;
		this.resultLabel = null;
		this.units = null;
		this.refRange = null;

		this.routineCharge = null;
		this.discount = null;
		this.updateTable = null;
		this.allBedTypes = "no";

		logger.debug("Resetting inside AddTestForm form {}" , this);
	}

	public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1) {
		logger.debug("in side validate method=========++>");
		return super.validate(arg0, arg1);
	}
	public boolean isConduction_applicable() {return conduction_applicable;}
	public void setConduction_applicable(boolean conduction_applicable) {this.conduction_applicable = conduction_applicable;}

	public String getCodeType() { return codeType; }
	public void setCodeType(String v) { codeType = v; }
	public String getConducting_doc_mandatory() {
		return conducting_doc_mandatory;
	}
	public void setConducting_doc_mandatory(String conducting_doc_mandatory) {
		this.conducting_doc_mandatory = conducting_doc_mandatory;
	}
	public String getRemarks() {
		return remarks;
	}
	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}
	public String[] getResultLabelShort() {
		return resultLabelShort;
	}
	public void setResultLabelShort(String[] resultLabelShort) {
		this.resultLabelShort = resultLabelShort;
	}
	public String getDep_test_name() {
		return dep_test_name;
	}
	public void setDep_test_name(String dep_test_name) {
		this.dep_test_name = dep_test_name;
	}
	public String getDependent_test_id() {
		return dependent_test_id;
	}
	public void setDependent_test_id(String dependent_test_id) {
		this.dependent_test_id = dependent_test_id;
	}
	public boolean isResults_entry_applicable() {
		return results_entry_applicable;
	}
	public void setResults_entry_applicable(boolean results_entry_applicable) {
		this.results_entry_applicable = results_entry_applicable;
	}
	public String getStdTAT() {
		return stdTAT;
	}
	public void setStdTAT(String stdTAT) {
		this.stdTAT = stdTAT;
	}
	public String getStdTATUnits() {
		return stdTATUnits;
	}
	public void setStdTATUnits(String stdTATUnits) {
		this.stdTATUnits = stdTATUnits;
	}
	public String getStat() {
		return stat;
	}
	public void setStat(String stat) {
		this.stat = stat;
	}
	public String getNogrowthtemplate() {
		return nogrowthtemplate;
	}
	public void setNogrowthtemplate(String nogrowthtemplate) {
		this.nogrowthtemplate = nogrowthtemplate;
	}
	public boolean isCross_match_test() {
		return cross_match_test;
	}
	public void setCross_match_test(boolean cross_match_test) {
		this.cross_match_test = cross_match_test;
	}
	public boolean isAllergen_test() {
		return allergen_test;
	}
	public void setAllergen_test(boolean allergen_test) {
		this.allergen_test = allergen_test;
	}
	public String getTestShortName() {
		return testShortName;
	}
	public void setTestShortName(String testShortName) {
		this.testShortName = testShortName;
	}
	public Integer getRateplan_category_id() {
		return rateplan_category_id;
	}
	public void setRateplan_category_id(Integer rateplan_category_id) {
		this.rateplan_category_id = rateplan_category_id;
	}
	public String[] getCancerScreeningIds() {
		return cancer_screening_id;
	}
	public void setCancerScreeningIds(String[] cancer_screening_id) {
		this.cancer_screening_id = cancer_screening_id;
	}
	public String getIs_sensitivity() {
		return is_sensitivity;
	}
	public void setIs_sensitivity(String is_sensitivity) {
		this.is_sensitivity = is_sensitivity;
	}

	public boolean isCovid_test() {
		return covid_test;
	}

	public void setCovid_test(boolean covid_test) {
		this.covid_test = covid_test;
	}
}