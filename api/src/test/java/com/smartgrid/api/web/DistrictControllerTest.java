package com.smartgrid.api.web;

import static org.mockito.Mockito.when;

import com.smartgrid.api.db.DistrictRepository;
import com.smartgrid.api.model.DistrictLive;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DistrictController.class)
class DistrictControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private DistrictRepository repository;

	@Test
	void returnsLiveTotalsAsJson() {
		when(repository.findLiveTotals()).thenReturn(List.of(
				new DistrictLive(1, "Innere Stadt", 4.0),
				new DistrictLive(2, "Leopoldstadt", 0.0)));

		mvc.get().uri("/districts/live").assertThat()
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						[
							{"districtNumber": 1, "name": "Innere Stadt", "totalPowerKw": 4.0},
							{"districtNumber": 2, "name": "Leopoldstadt", "totalPowerKw": 0.0}
						]
						""");
	}

}
