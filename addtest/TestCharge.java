package com.insta.hms.diagnosticsmasters.addtest;

import java.math.BigDecimal;

public class TestCharge {

	private String testId;
	private String testName;
	private String orgId;
	private BigDecimal charge;
	private BigDecimal discount;
	private String bedType;
	private String priority;
	private String userName;
	private String codeType;


	private String orgItemCode;
	private boolean applicable;

	public boolean getApplicable() {
		return applicable;
	}
	public void setApplicable(boolean applicable) {
		this.applicable = applicable;
	}
	public String getOrgItemCode() {
		return orgItemCode;
	}
	public void setOrgItemCode(String orgItemCode) {
		this.orgItemCode = orgItemCode;
	}
	public String getBedType() {
		return bedType;
	}
	public void setBedType(String bedType) {
		this.bedType = bedType;
	}
	public BigDecimal getCharge() {
		return charge;
	}
	public void setCharge(BigDecimal charge) {
		this.charge = charge;
	}
	public String getOrgId() {
		return orgId;
	}
	public void setOrgId(String orgId) {
		this.orgId = orgId;
	}
	public String getPriority() {
		return priority;
	}
	public void setPriority(String priority) {
		this.priority = priority;
	}
	public String getTestId() {
		return testId;
	}
	public void setTestId(String testId) {
		this.testId = testId;
	}
	public String getTestName() {
		return testName;
	}
	public void setTestName(String testName) {
		this.testName = testName;
	}
	public BigDecimal getDiscount() {
		return discount;
	}
	public void setDiscount(BigDecimal discount) {
		this.discount = discount;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getCodeType() { return codeType; }
	public void setCodeType(String v) { codeType = v; }

}
