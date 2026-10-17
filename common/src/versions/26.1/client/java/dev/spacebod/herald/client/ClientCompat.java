package dev.spacebod.herald.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/** The few client calls that moved between Minecraft series; one copy per series in src/versions/. */
public final class ClientCompat {
	private ClientCompat() {
	}

	public static @Nullable Screen screen(Minecraft minecraft) {
		return minecraft.screen;
	}

	public static void setScreen(Minecraft minecraft, @Nullable Screen screen) {
		minecraft.setScreen(screen);
	}

	public static void chat(Minecraft minecraft, Component message) {
		minecraft.gui.getChat().addClientSystemMessage(message);
	}

	public static void openFolder(Path folder) {
		Util.getPlatform().openPath(folder);
	}
}
