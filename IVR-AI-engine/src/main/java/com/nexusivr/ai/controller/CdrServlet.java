package com.nexusivr.ai.controller;

import com.nexusivr.ai.dto.CdrRecord;
import com.nexusivr.ai.dto.CdrSummary;
import com.nexusivr.ai.service.CdrService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Controller servlet exposing Asterisk CDR call analytics.
 * <ul>
 *   <li>GET /api/v1/ai/cdr/calls — recent call records (newest first)</li>
 *   <li>GET /api/v1/ai/cdr/summary — aggregate KPIs + daily/hourly series</li>
 * </ul>
 */
public class CdrServlet extends BaseAiServlet {

    private final CdrService cdrService;

    public CdrServlet(CdrService cdrService) {
        this.cdrService = cdrService;
    }

    public CdrServlet() {
        this(null);
    }

    private CdrService getCdrService() {
        return cdrService != null ? cdrService : ServiceRegistry.getCdrService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String uri = req.getRequestURI() != null ? req.getRequestURI() : "";
            if (uri.endsWith("/summary")) {
                sendJsonResponse(resp, HttpServletResponse.SC_OK, getCdrService().getSummary());
            } else {
                List<CdrRecord> calls = getCdrService().getRecentCalls(200);
                sendJsonResponse(resp, HttpServletResponse.SC_OK, calls);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
