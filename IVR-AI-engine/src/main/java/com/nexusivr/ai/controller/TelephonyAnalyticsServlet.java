package com.nexusivr.ai.controller;

import com.nexusivr.ai.dao.DatabaseManager;
import com.nexusivr.ai.dao.TelephonyAnalyticsDao;
import com.nexusivr.ai.dto.AnalyticsPayload;
import com.nexusivr.ai.dto.CallLogDto;
import com.nexusivr.ai.dto.DistributionDto;
import com.nexusivr.ai.dto.VolumeDto;
import com.nexusivr.ai.service.AsteriskMonitor;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

@WebServlet(urlPatterns = {"/api/v1/telephony/analytics"})
public class TelephonyAnalyticsServlet extends BaseAiServlet {

    private final TelephonyAnalyticsDao analyticsDao = new TelephonyAnalyticsDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UUID tenantId = extractTenantId(req);
            
            try (Connection conn = DatabaseManager.getConnection()) {
                // 1. Get recent calls
                List<CallLogDto> recentCalls = analyticsDao.getRecentCalls(conn, tenantId);
                
                // 2. Call volume per hour (Inbound volume chart)
                List<VolumeDto> hourlyVolume = analyticsDao.getHourlyVolume(conn, tenantId);
                
                // 3. Distribution count (Pie chart)
                List<DistributionDto> callDist = analyticsDao.getCallDistribution(conn, tenantId);
                
                // 4. Live calls (from AMI)
                int liveCalls = AsteriskMonitor.getInstance().getActiveCallsCount();

                // Construct Response JSON
                AnalyticsPayload payload = new AnalyticsPayload(liveCalls, recentCalls, hourlyVolume, callDist);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, payload);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
