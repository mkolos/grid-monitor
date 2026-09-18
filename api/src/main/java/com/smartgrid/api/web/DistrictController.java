package com.smartgrid.api.web;

import com.smartgrid.api.db.DistrictRepository;
import com.smartgrid.api.model.DistrictLive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DistrictController {

	private final DistrictRepository repository;

	public DistrictController(DistrictRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/districts/live")
	public List<DistrictLive> liveTotals() {
		return repository.findLiveTotals();
	}

}
