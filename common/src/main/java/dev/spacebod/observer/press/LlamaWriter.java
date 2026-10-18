package dev.spacebod.observer.press;

import com.google.gson.JsonObject;
import dev.spacebod.observer.Observer;
import net.ladenthin.llama.LlamaModel;
import net.ladenthin.llama.args.LogFormat;
import net.ladenthin.llama.parameters.ChatRequest;
import net.ladenthin.llama.parameters.InferenceParameters;
import net.ladenthin.llama.parameters.ModelParameters;
import net.ladenthin.llama.value.ChatResponse;
import net.ladenthin.llama.value.LogLevel;
import net.ladenthin.llama.value.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The only class that touches llama.cpp. Kept apart so that a platform without a bundled native library
 * (or any other loading failure) surfaces as one catchable error in {@link Press}, never as a crash.
 *
 * <p>The model gets one slot per draft, so a story's drafts are decoded side by side in one batch: on a CPU that
 * costs little more than writing one. Requests start with the same system prompt and examples, which llama.cpp
 * keeps cached between calls.
 */
final class LlamaWriter implements AutoCloseable {
	/** Every layer: the whole model on the GPU. */
	private static final int ALL_LAYERS = 999;
	/** The shared context is at least this big, so one long request (the editor reading every dossier) fits. */
	private static final int MIN_CONTEXT = 8192;
	/** Where the model last ran, for /observer status: "GPU (device, 25/25 layers)" or "CPU". */
	private static volatile String device = "not run yet";
	private static volatile String gpuName = "";

	private final LlamaModel model;
	private final PressProfile.Sampling sampling;

	LlamaWriter(Path file, int threads, PressProfile profile, boolean gpu) {
		String library = Engine.load(gpu);
		LlamaModel.setLogger(LogFormat.TEXT, (level, message) -> {
			if (level == LogLevel.ERROR) {
				Observer.LOGGER.warn("llama.cpp: {}", message.strip());
			} else if (message.contains("ggml_vulkan: 0 = ")) {
				String name = message.substring(message.indexOf("= ") + 2);
				gpuName = (name.contains(" |") ? name.substring(0, name.indexOf(" |")) : name).strip();
			}
		});
		sampling = profile.sampling;
		LlamaModel loaded;
		try {
			loaded = open(file, threads, gpu ? ALL_LAYERS : 0);
		} catch (RuntimeException e) {
			if (!gpu) {
				throw e;
			}
			// Not enough video memory, or a driver that won't take the model: the CPU always can.
			Observer.LOGGER.warn("The press could not put the model on the GPU; using the CPU", e);
			gpu = false;
			loaded = open(file, threads, 0);
		}
		model = loaded;
		// The Vulkan build, or the Apple Silicon build (Metal is part of it).
		boolean onGpu = gpu && (library.equals("vulkan") || System.getProperty("os.name", "").startsWith("Mac"));
		String name = !gpuName.isEmpty() ? gpuName : library.equals("vulkan") ? "Vulkan" : "Metal";
		device = onGpu ? "GPU (" + name + ")" : "CPU (" + threads + " threads)";
		Observer.LOGGER.info("Press running on the {}", device);
	}

	private LlamaModel open(Path file, int threads, int gpuLayers) {
		int slots = Math.max(1, sampling.drafts);
		return new LlamaModel(new ModelParameters()
				.setModel(file.toString())
				.setCtxSize(Math.max(MIN_CONTEXT, sampling.contextSize * slots))
				.setKvUnified(true)
				.setParallel(slots)
				.setThreads(threads)
				.setThreadsBatch(threads)
				.setGpuLayers(gpuLayers));
	}

	static String device() {
		return device;
	}

	static String grammar(JsonObject schema) {
		return LlamaModel.jsonSchemaToGrammar(schema.toString());
	}

	/** One completion through the model's own chat template. */
	String write(String system, List<Pair<String, String>> turns, int seed, String grammar, int maxTokens) {
		return model.chatCompleteText(parameters(new InferenceParameters("").withMessages(system, turns), seed, grammar, maxTokens));
	}

	/** One completion per seed, decoded together. */
	List<String> drafts(String system, List<Pair<String, String>> turns, List<Integer> seeds, String grammar) {
		List<ChatRequest> requests = new ArrayList<>();
		for (int seed : seeds) {
			ChatRequest request = ChatRequest.empty().appendMessage("system", system);
			for (Pair<String, String> turn : turns) {
				request = request.appendMessage(turn.getKey(), turn.getValue());
			}
			requests.add(request.withInferenceCustomizer(p -> parameters(p, seed, grammar, sampling.maxTokens)));
		}
		List<String> out = new ArrayList<>();
		for (ChatResponse response : model.chatBatch(requests)) {
			out.add(response.getFirstContent());
		}
		return out;
	}

	private InferenceParameters parameters(InferenceParameters p, int seed, String grammar, int maxTokens) {
		return p.withTemperature(sampling.temperature)
				.withTopP(sampling.topP)
				.withTopK(sampling.topK)
				.withMinP(sampling.minP)
				.withPresencePenalty(sampling.presencePenalty)
				.withNPredict(maxTokens)
				.withSeed(seed)
				.withCachePrompt(true)
				.withGrammar(grammar)
				.withChatTemplateKwargs(Map.of("enable_thinking", Boolean.toString(sampling.thinking)));
	}

	@Override
	public void close() {
		model.close();
	}
}
