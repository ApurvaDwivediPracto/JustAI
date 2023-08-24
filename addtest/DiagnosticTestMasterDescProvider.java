package com.insta.hms.diagnosticsmasters.addtest;

import com.insta.hms.auditlog.AuditLogDesc;
import com.insta.hms.auditlog.AuditLogDescProvider;

public class DiagnosticTestMasterDescProvider implements AuditLogDescProvider {

	public AuditLogDesc getAuditLogDesc(String tableName) {
		AuditLogDesc desc = new AuditLogDesc(tableName);

		desc.addField("mod_time", "Mod Time");
		desc.addField("operation", "Operation");
		desc.addField("username", "User Name");
		desc.addField("test_name", "Test Name");
		desc.addField("test_id", "searchKey", false);
		desc.addField("testid", "Test Id");
		desc.addField("remarks", "Remarks");
		desc.addField("ddept_name", "Department");
		desc.addField("conducting_doc_mandatory", "Conducting Doc Mandatory");
		desc.addField("insurance_category_id", "Insurance category Id");
		desc.addField("prior_auth_required", "Prior Auth Required");
		desc.addField("allow_rate_increase", "Allow Rate Increase");
		desc.addField("allow_rate_decrease", "Allow Rate Decrease");
		desc.addField("conducting_role_id", "Conducting Role ID");
		desc.addField("stat", "Stat");
		desc.addField("cross_match_test", "Cross Match Test");
		desc.addField("allergen_test", "Allergen Test");
		desc.addField("consent_required", "Consent Required");
		desc.addField("clinical_information_form", "Clinical Information Form");
		desc.addField("vat_applicable", "Vat Applicable");
		desc.addField("vat_percent", "Vat Percent");
		desc.addField("vat_option", "Vat Option");
		desc.addField("dependent_test_id", "Dependent Test");
		desc.addField("service_sub_group_id", "Service Sub Group Id");
		desc.addField("test_short_name", "Test Short Name");
		desc.addField("sample_type_id", "Sample Type");
		desc.addField("results_entry_applicable", "Result Entry Applicable");
		desc.addField("sample_collection_instructions", "Sample Instructions");
		desc.addField("conduction_instructions", "Conduction Instructions");
		desc.addField("results_validation", "Results Validation");

		desc.addField("sample_needed", "Sample Needed");
		desc.addField("obsolete_house_status", "House Status");
		desc.addField("type_of_specimen", "Type Of Specimen");
		desc.addField("diag_code", "Diag Code");
		desc.addField("conduction_format", "Conduction Format");
		desc.addField("status", "Status");
		desc.addField("conduction_applicable", "Conduction Applicable");
		desc.addField("rateplan_category_id", "Rateplan Category");
		desc.addField("cancer_screening_id", "Cancer Screening Id");

		return desc;
	}

}