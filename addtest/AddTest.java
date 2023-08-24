package com.insta.hms.diagnosticsmasters.addtest;

import java.math.BigDecimal;

public class AddTest {

	private String specimen;
	private String testconduct;
	private String formatname;
	private String ddeptid;
	private String newtestname;
	private String diagcode;
	private String sampleneed;
	private String reportgroup;
	private boolean successStatus;
	private String addOrEdit;
	private String testid;

	private String resultlabel[];
	private String units[];
	private String refrange[];

	/*
	 * The following are the GENERAL/GENERAL charges
	 */
	private BigDecimal statCharge;
	private BigDecimal routineCharge;
	private BigDecimal scheduleCharge;

	public String getDdeptid() {
		return ddeptid;
	}
	public void setDdeptid(String ddeptid) {
		this.ddeptid = ddeptid;
	}
	public String getDiagcode() {
		return diagcode;
	}
	public void setDiagcode(String diagcode) {
		this.diagcode = diagcode;
	}
	public String getFormatname() {
		return formatname;
	}
	public void setFormatname(String formatname) {
		this.formatname = formatname;
	}
	public String getNewtestname() {
		return newtestname;
	}
	public void setNewtestname(String newtestname) {
		this.newtestname = newtestname;
	}
	public String[] getRefrange() {
		return refrange;
	}
	public void setRefrange(String[] refrange) {
		this.refrange = refrange;
	}
	public String[] getResultlabel() {
		return resultlabel;
	}
	public void setResultlabel(String[] resultlabel) {
		this.resultlabel = resultlabel;
	}
	public String getSampleneed() {
		return sampleneed;
	}
	public void setSampleneed(String sampleneed) {
		this.sampleneed = sampleneed;
	}
	public String getSpecimen() {
		return specimen;
	}
	public void setSpecimen(String specimen) {
		this.specimen = specimen;
	}
	public String getTestconduct() {
		return testconduct;
	}
	public void setTestconduct(String testconduct) {
		this.testconduct = testconduct;
	}
	public String[] getUnits() {
		return units;
	}
	public void setUnits(String[] units) {
		this.units = units;
	}
	public String getReportgroup() {
		return reportgroup;
	}
	public void setReportgroup(String reportgroup) {
		this.reportgroup = reportgroup;
	}
	public boolean isSuccessStatus() {
		return successStatus;
	}
	public void setSuccessStatus(boolean successStatus) {
		this.successStatus = successStatus;
	}
	public String getAddOrEdit() {
		return addOrEdit;
	}
	public void setAddOrEdit(String addOrEdit) {
		this.addOrEdit = addOrEdit;
	}
	public String getTestid() {
		return testid;
	}
	public void setTestid(String testid) {
		this.testid = testid;
	}

	public BigDecimal getStatCharge() { return statCharge; }
	public void setStatCharge(BigDecimal v) { statCharge = v; }

	public BigDecimal getRoutineCharge() { return routineCharge; }
	public void setRoutineCharge(BigDecimal v) { routineCharge = v; }

	public BigDecimal getScheduleCharge() { return scheduleCharge; }
	public void setScheduleCharge(BigDecimal v) { scheduleCharge = v; }

}
