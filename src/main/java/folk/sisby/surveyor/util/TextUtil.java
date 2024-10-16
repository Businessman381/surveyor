package folk.sisby.surveyor.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.text.Texts;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class TextUtil {
	public static Text stripInteraction(Text text) {
		try {
			NbtCompound nbt = (NbtCompound) TextCodecs.CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow();
			NbtUtil.removeRecursive(nbt, List.of("hoverEvent", "clickEvent", "insertion"));
			return TextCodecs.CODEC.decode(NbtOps.INSTANCE, nbt).getOrThrow().getFirst();
		} catch (Exception e) {
			return Text.literal(text.getString());
		}
	}

	public static MutableText highlightStrings(Collection<String> list, Function<String, Formatting> highlighter) {
		return Text.literal("[").append(Texts.join(
			list,
			Text.literal(", "),
			s -> Text.literal(s).setStyle(Style.EMPTY.withFormatting(Objects.requireNonNullElse(highlighter.apply(s), Formatting.RESET)))
		)).append(Text.literal("]"));
	}
}
