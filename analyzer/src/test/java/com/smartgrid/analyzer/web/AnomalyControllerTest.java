package com.smartgrid.analyzer.web;

import static org.mockito.Mockito.when;

import com.smartgrid.analyzer.detection.LatestAnomalyReport;
import com.smartgrid.analyzer.model.AnomalyEvent;
import com.smartgrid.analyzer.model.AnomalyReport;
import com.smartgrid.analyzer.model.AnomalyType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AnomalyController.class)
class AnomalyControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private LatestAnomalyReport latestReport;

	@Test
	void returnsTheLatestReportAsJson() {
		AnomalyReport report = new AnomalyReport(
				Instant.parse("2026-09-30T12:00:00Z"),
				List.of(new AnomalyEvent("01-001", 1, "Innere Stadt", AnomalyType.UNDER_VOLTAGE, 200.0, 207.0,
						"Voltage 200.0V is below the expected minimum of 207.0V")),
				"District 1 shows a voltage dip.");
		when(latestReport.get()).thenReturn(report);

		mvc.get().uri("/anomalies").assertThat()
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{
							"generatedAt": "2026-09-30T12:00:00Z",
							"narrative": "District 1 shows a voltage dip.",
							"events": [
								{
									"meterId": "01-001",
									"districtNumber": 1,
									"districtName": "Innere Stadt",
									"type": "UNDER_VOLTAGE",
									"observedValue": 200.0,
									"referenceValue": 207.0,
									"description": "Voltage 200.0V is below the expected minimum of 207.0V"
								}
							]
						}
						""");
	}

}
