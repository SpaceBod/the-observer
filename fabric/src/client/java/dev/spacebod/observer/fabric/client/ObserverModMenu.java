package dev.spacebod.observer.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.spacebod.observer.client.ObserverConfigScreen;

/** The settings button in Mod Menu's list. */
public class ObserverModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ObserverConfigScreen::new;
	}
}
