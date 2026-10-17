package dev.spacebod.herald.edition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One story as printed: a section label ("Industry", "Mishaps"...), a headline and the body text. */
public record Article(String section, String headline, String body) {
	public static final Codec<Article> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("section").forGetter(Article::section),
			Codec.STRING.fieldOf("headline").forGetter(Article::headline),
			Codec.STRING.fieldOf("body").forGetter(Article::body)
	).apply(i, Article::new));
}
