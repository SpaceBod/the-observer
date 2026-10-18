package dev.spacebod.observer.sim;

import dev.spacebod.observer.Observer;
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
 * With OBSERVER_MODEL set to a GGUF file, also prints each scenario through the writing model
 * (*.press.md), so the model's stories and the fact check's verdicts can be compared with the templates.
 */
class GalleryTest {
	@Test
	void writeGallery() throws IOException {
		Path dir = Path.of(System.getProperty("observer.gallery", "build/scenarios"));
		Files.createDirectories(dir);
		StringBuilder index = new StringBuilder("# Observer scenario gallery\n\n");
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
	@EnabledIfEnvironmentVariable(named = "OBSERVER_MODEL", matches = ".+")
	void writeGalleryThroughTheModel() throws IOException {
		Path dir = Path.of(System.getProperty("observer.gallery", "build/scenarios"));
		Path config = Files.createTempDirectory("observer-config");
		Observer.init(config, config, true);
		Observer.config().model = Path.of(System.getenv("OBSERVER_MODEL")).toAbsolutePath().toString();
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
