package com.smartgrid.analyzer.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smartgrid.analyzer.model.AnomalyEvent;
import com.smartgrid.analyzer.model.AnomalyType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class AnomalyNarratorTest {

	@Test
	void returnsTheModelsSummaryAndSendsEveryEventInThePrompt() {
		ChatModel chatModel = mock(ChatModel.class);
		given(chatModel.getOptions()).willReturn(ChatOptions.builder().build());
		given(chatModel.call(any(Prompt.class))).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("District 1 shows a voltage dip.")))));
		AnomalyNarrator narrator = new AnomalyNarrator(ChatClient.builder(chatModel));

		List<AnomalyEvent> events = List.of(
				new AnomalyEvent("01-001", 1, "Innere Stadt", AnomalyType.UNDER_VOLTAGE, 200.0, 207.0,
						"Voltage 200.0V is below the expected minimum of 207.0V"),
				new AnomalyEvent("02-005", 2, "Leopoldstadt", AnomalyType.POWER_SPIKE, 9.0, 5.0,
						"Power draw 9.00kW is 4.0 standard deviations above its recent average of 5.00kW"));

		String narrative = narrator.narrate(events);

		assertThat(narrative).isEqualTo("District 1 shows a voltage dip.");
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(promptCaptor.capture());
		String promptText = promptCaptor.getValue().getContents();
		assertThat(promptText).contains("01-001", "UNDER_VOLTAGE", "02-005", "POWER_SPIKE");
	}

}
