package dev.spacebod.herald.sim;

import dev.spacebod.herald.Herald;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes every scenario's edition as Markdown (build/scenarios/), to read what the paper would actually say.
 * With HERALD_MODEL set to a GGUF file, also prints each scenario through the writing model
 * (*.press.md), so the model's stories and the fact check's verdicts can be compared with the templates.
 */
class GalleryTest {
	@Test
	void writeGallery() throws IOException {
		Path dir = Path.of(System.getProperty("herald.gallery", "build/scenarios"));
		Files.createDirectories(dir);
		StringBuilder index = new StringBuilder("# Herald scenario gallery\n\n");
		for (Map.Entry<String, Supplier<Scenarios.Run>> entry : Scenarios.all().entrySet()) {
			Scenarios.Run run = entry.getValue().get();
			Files.writeString(dir.resolve(entry.getKey() + ".md"), run.result().markdown(entry.getKey(), run.players()));
			index.append("- [").append(entry.getKey()).append("](").append(entry.getKey()).append(".md): ")
					.append(run.result().kinds()).append("\n");
		}
		Files.writeString(dir.resolve("README.md"), index);
		assertTrue(Files.exists(dir.resolve("busy-server.md")));
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "HERALD_MODEL", matches = ".+")
	void writeGalleryThroughTheModel() throws IOException {
		Path dir = Path.of(System.getProperty("herald.gallery", "build/scenarios"));
		Path config = Files.createTempDirectory("herald-config");
		Herald.init(config, config);
		Herald.config().model = Path.of(System.getenv("HERALD_MODEL")).toAbsolutePath().toString();
		Files.createDirectories(dir);
		Scenarios.throughPress = true;
		try {
			for (String name : new String[]{"mining-expedition", "bad-week", "busy-server"}) {
				Scenarios.Run run = Scenarios.all().get(name).get();
				Files.writeString(dir.resolve(name + ".press.md"), run.result().markdown(name + " (through the model)", run.players()));
			}
		} finally {
			Scenarios.throughPress = false;
		}
	}
}
