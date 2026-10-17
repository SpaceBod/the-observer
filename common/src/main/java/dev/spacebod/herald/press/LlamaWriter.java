package dev.spacebod.herald.press;

import dev.spacebod.herald.Herald;
import net.ladenthin.llama.LlamaModel;
import net.ladenthin.llama.args.LogFormat;
import net.ladenthin.llama.parameters.InferenceParameters;
import net.ladenthin.llama.parameters.ModelParameters;
import net.ladenthin.llama.value.LogLevel;
import net.ladenthin.llama.value.Pair;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The only class that touches llama.cpp. Kept apart so that a platform without a bundled native library
 * (or any other loading failure) surfaces as one catchable error in {@link Press}, never as a crash.
 */
final class LlamaWriter implements AutoCloseable {
	private final LlamaModel model;
	private final PressProfile.Sampling sampling;
	/** The profile's JSON Schema compiled to a llama.cpp grammar: replies can only be JSON of that shape. */
	private final String grammar;

	LlamaWriter(Path file, int threads, PressProfile profile) {
		LlamaModel.setLogger(LogFormat.TEXT, (level, message) -> {
			if (level == LogLevel.ERROR) {
				Herald.LOGGER.warn("llama.cpp: {}", message.strip());
			}
		});
		sampling = profile.sampling;
		grammar = LlamaModel.jsonSchemaToGrammar(profile.schema.toString());
		model = new LlamaModel(new ModelParameters()
				.setModel(file.toString())
				.setCtxSize(sampling.contextSize)
				.setThreads(threads)
				.setThreadsBatch(threads));
	}

	/**
	 * One completion through the model's own chat template.
	 *
	 * @param turns earlier turns (the few-shot examples) followed by this story's request, as role/content pairs
	 */
	String write(String system, List<Pair<String, String>> turns, int seed) {
		InferenceParameters params = new InferenceParameters("")
				.withMessages(system, turns)
				.withTemperature(sampling.temperature)
				.withTopP(sampling.topP)
				.withTopK(sampling.topK)
				.withMinP(sampling.minP)
				.withPresencePenalty(sampling.presencePenalty)
				.withNPredict(sampling.maxTokens)
				.withSeed(seed)
				.withCachePrompt(true)
				.withGrammar(grammar)
				.withChatTemplateKwargs(Map.of("enable_thinking", Boolean.toString(sampling.thinking)));
		return model.chatCompleteText(params);
	}

	@Override
	public void close() {
		model.close();
	}
}
