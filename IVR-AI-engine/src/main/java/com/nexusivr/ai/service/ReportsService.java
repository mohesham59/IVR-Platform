package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.ReportsDao;

import java.sql.Timestamp;
import java.util.*;

public class ReportsService {

    private final ReportsDao dao;

    public ReportsService(ReportsDao dao) {
        this.dao = dao;
    }

    public ReportsService() {
        this(new ReportsDao());
    }

    public List<Map<String, Object>> getTenantTelephonyReport(Timestamp dateFrom, Timestamp dateTo, UUID tenantId) {
        return dao.getTenantTelephonyReport(dateFrom, dateTo, tenantId);
    }

    public List<Map<String, Object>> getTenantBillingReport(Timestamp dateFrom, Timestamp dateTo, UUID tenantId) {
        return dao.getTenantBillingReport(dateFrom, dateTo, tenantId);
    }

    public String exportTelephonyReportCsv(Timestamp dateFrom, Timestamp dateTo, UUID tenantId) {
        List<Map<String, Object>> rows = dao.getTenantTelephonyReport(dateFrom, dateTo, tenantId);
        return dao.generateTelephonyReportCsv(rows);
    }

    public String exportBillingReportCsv(Timestamp dateFrom, Timestamp dateTo, UUID tenantId) {
        List<Map<String, Object>> rows = dao.getTenantBillingReport(dateFrom, dateTo, tenantId);
        return dao.generateBillingReportCsv(rows);
    }
}
