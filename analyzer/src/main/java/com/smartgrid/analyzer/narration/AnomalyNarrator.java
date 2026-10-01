package com.smartgrid.analyzer.narration;

import com.smartgrid.analyzer.model.AnomalyEvent;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Turns a batch of already-detected anomalies into one short plain-English
 * summary with a single model call per scan, rather than one call per
 * anomaly — cheaper and lets the model note patterns across events (e.g.
 * several meters in the same district).
 */
@Component
public class AnomalyNarrator {

	private static final String SYSTEM_PROMPT = """
			You are a grid operations assistant. You will be given a list of
			anomalies detected on an electricity grid, one per line, already
			computed by a rule-based system. Write a short plain-English incident
			summary (3-5 sentences) for a human operator. Only describe what is in
			the data given to you - do not invent causes, locations, or facts that
			are not present in the list.
			""";

	private final ChatClient chatClient;

	public AnomalyNarrator(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder.build();
	}

	public String narrate(List<AnomalyEvent> events) {
		String eventLines = events.stream()
				.map(e -> "- meter %s (district %d, %s) [%s]: %s"
						.formatted(e.meterId(), e.districtNumber(), e.districtName(), e.type(), e.description()))
				.collect(Collectors.joining("\n"));

		return chatClient.prompt()
				.system(SYSTEM_PROMPT)
				.user(eventLines)
				.call()
				.content();
	}

}
