package com.nexusivr.ai.dao;

import com.nexusivr.ai.dto.AnalyticsPayload;
import com.nexusivr.ai.dto.CallLogDto;
import com.nexusivr.ai.dto.DistributionDto;
import com.nexusivr.ai.dto.VolumeDto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TelephonyAnalyticsDao {

    public List<CallLogDto> getRecentCalls(Connection conn, UUID tenantId) throws Exception {
        List<CallLogDto> calls = new ArrayList<>();
        String sql = "SELECT caller_id, status, duration, scenario_name " +
                     "FROM call_logs WHERE tenant_id = ? ORDER BY start_time DESC LIMIT 10";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CallLogDto call = new CallLogDto();
                    call.setCaller(rs.getString("caller_id"));
                    call.setStatus(rs.getString("status"));
                    int duration = rs.getInt("duration");
                    call.setDuration((duration / 60) + "m " + (duration % 60) + "s");
                    call.setScenario(rs.getString("scenario_name"));
                    calls.add(call);
                }
            }
        }
        return calls;
    }

    public List<VolumeDto> getHourlyVolume(Connection conn, UUID tenantId) throws Exception {
        List<VolumeDto> volumes = new ArrayList<>();
        String sql = "SELECT to_char(start_time, 'HH24:00') as hour, COUNT(*) as calls " +
                     "FROM call_logs " +
                     "WHERE tenant_id = ? AND start_time > now() - INTERVAL '24 hours' " +
                     "GROUP BY hour ORDER BY hour";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VolumeDto vol = new VolumeDto();
                    vol.setTime(rs.getString("hour"));
                    vol.setInbound(rs.getInt("calls"));
                    vol.setOutbound(0); // Assuming all inbound for now
                    volumes.add(vol);
                }
            }
        }
        return volumes;
    }

    public List<DistributionDto> getCallDistribution(Connection conn, UUID tenantId) throws Exception {
        List<DistributionDto> dists = new ArrayList<>();
        String sql = "SELECT ce.node_name as name, COUNT(*) as value " +
                     "FROM call_events ce " +
                     "JOIN call_logs cl ON ce.session_id = cl.session_id " +
                     "WHERE cl.tenant_id = ? AND ce.event_type = 'MENU_SELECTION' " +
                     "GROUP BY ce.node_name";
        String[] colors = {"#2563EB", "#22C55E", "#F59E0B", "#EC4899", "#8B5CF6"};
        int colorIdx = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DistributionDto dist = new DistributionDto();
                    dist.setName(rs.getString("name"));
                    dist.setValue(rs.getInt("value"));
                    dist.setColor(colors[colorIdx % colors.length]);
                    colorIdx++;
                    dists.add(dist);
                }
            }
        }
        return dists;
    }
}
