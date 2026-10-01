package com.fleetiq.ml;

import com.fleetiq.config.AppProperties;
import com.fleetiq.ml.MlDtos.DemandFeatures;
import com.fleetiq.ml.MlDtos.DemandRequest;
import com.fleetiq.ml.MlDtos.DemandResponse;
import com.fleetiq.ml.MlDtos.ForecastRequest;
import com.fleetiq.ml.MlDtos.ForecastResponse;
import com.fleetiq.ml.MlDtos.MaintenanceFeatures;
import com.fleetiq.ml.MlDtos.MaintenanceRequest;
import com.fleetiq.ml.MlDtos.MaintenanceResponse;
import com.fleetiq.ml.MlDtos.RiskFeatures;
import com.fleetiq.ml.MlDtos.RiskRequest;
import com.fleetiq.ml.MlDtos.RiskResponse;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the Python ML service. Every call returns Optional.empty() on failure so callers
 * fall back to a rule-based estimate: the rental flow keeps working when the model is down.
 */
@Component
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private final RestClient rest;

    public MlClient(AppProperties props, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.ml().timeoutMs());
        factory.setReadTimeout(props.ml().timeoutMs());
        this.rest = builder.baseUrl(props.ml().baseUrl()).requestFactory(factory).build();
    }

    public Optional<DemandResponse> predictDemand(List<DemandFeatures> zones) {
        return post("/predict/demand", new DemandRequest(zones), DemandResponse.class);
    }

    public Optional<ForecastResponse> forecast(ForecastRequest request) {
        return post("/predict/forecast", request, ForecastResponse.class);
    }

    public Optional<MaintenanceResponse> predictMaintenance(List<MaintenanceFeatures> vehicles) {
        return post("/predict/maintenance", new MaintenanceRequest(vehicles), MaintenanceResponse.class);
    }

    public Optional<RiskResponse> predictRisk(List<RiskFeatures> customers) {
        return post("/predict/risk", new RiskRequest(customers), RiskResponse.class);
    }

    private <T> Optional<T> post(String path, Object body, Class<T> type) {
        try {
            return Optional.ofNullable(rest.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type));
        } catch (Exception e) {
            log.warn("ML service call {} failed, using rule-based fallback: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
