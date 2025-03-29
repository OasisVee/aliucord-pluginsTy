package tech.tyman.plugins.texter

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.CommandContext
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType

@AliucordPlugin
class Texter : Plugin() {
    override fun start(context: Context) {
        registerConverterCommand(
                "small",
                "Turns your text into small letters", emptyList()
        ) { ctx: CommandContext -> getResult(Maps.smallLetters, ctx.getString("text")!!) }
        registerConverterCommand(
                "smaller",
                "Turns your text into smaller letters", emptyList()
        ) { ctx: CommandContext -> getResult(Maps.smallerLetters, ctx.getString("text")!!) }
        registerConverterCommand(
                "fullwidth",
                "Turns your text into full width letters", listOf(
                "fw"
        )
        ) { ctx: CommandContext -> getResult(Maps.fullWidthLetters, ctx.getString("text")!!) }
        registerConverterCommand(
                "emoji",
                "Turns your text into emoji letters",
                listOf(
                        "emojify",
                        "blockify"
                )
        ) { ctx: CommandContext -> getResult(Maps.emojiLetters, ctx.getString("text")!!) }
        registerConverterCommand(
                "stylized",
                "Converts your text into stylized characters"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toStylized()) }
        registerConverterCommand(
                "clap",
                "Adds clapping icons to your text"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.clapify()) }
        registerConverterCommand(
                "reverse",
                "Reverses your text"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.reverse()) }
        registerConverterCommand(
                "space",
                "Spaces out your text"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.chunked(1).joinToString(" ")) }
        registerConverterCommand(
                "mock",
                "Capitalizes random parts of your text"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.mock()) }
        registerConverterCommand(
                "leet",
                "Makes your text 1337 style", listOf(
                "leetify"
        )
        ) { ctx: CommandContext -> getResult(Maps.leetLetters, ctx.getString("text")!!) }
        registerConverterCommand(
                "piglatin",
                "Converts your text to Pig Latin"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toPigLatin()) }
        registerConverterCommand(
                "binary",
                "Converts your text to binary"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toBinary()) }
        registerConverterCommand(
                "morse",
                "Converts your text to Morse code"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toMorseCode()) }
        registerConverterCommand(
                "wave",
                "Adds a wave effect to your text"
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toWave()) }
    }

    private fun registerConverterCommand(name: String, description: String, execute: (CommandContext) -> CommandsAPI.CommandResult) {
        commands.registerCommand(
                name,
                description, listOf(
                Utils.createCommandOption(ApplicationCommandType.STRING, "text", "The text to change")
        ),
                execute
        )
    }
    private fun registerConverterCommand(name: String, description: String, aliases: List<String>, execute: (CommandContext) -> CommandsAPI.CommandResult) {
        commands.registerCommand(
                name,
                description, listOf(
                Utils.createCommandOption(ApplicationCommandType.STRING, "text", "The text to change")
        ),
                execute
        )
        for (alias in aliases) {
            commands.registerCommand(
                    alias,
                    description, listOf(
                    Utils.createCommandOption(ApplicationCommandType.STRING, "text", "The text to change")
            ),
                    execute
            )
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }

    private fun getResult(map: Map<String, String>, textToMap: String): CommandsAPI.CommandResult {
        return CommandsAPI.CommandResult(Maps.getMappedString(map, textToMap), null, true)
    }

    private fun getResult(textToSend: String): CommandsAPI.CommandResult {
        return CommandsAPI.CommandResult(textToSend, null, true)
    }

    private fun String.toPigLatin(): String {
        return this.split(" ").joinToString(" ") {
            if (it.isNotEmpty() && it[0].lowercaseChar() in "aeiou") {
                "$it-way"
            } else {
                "${it.drop(1)}-${it[0].lowercaseChar()}ay"
            }
        }
    }

    private fun String.toStylized(): String {
        val styles = listOf(
            { c: Char -> c.toUpperCase() },
            { c: Char -> c.toLowerCase() },
            { c: Char -> if (c.isLetter()) c + 0x1D400 - 'A' else c }, // Math Bold
            { c: Char -> if (c.isLetter()) c + 0x1D56C - 'A' else c }  // Math Bold Italic
        )
        return this.mapIndexed { index, char ->
            styles[index % styles.size](char)
        }.joinToString("")
    }

    private fun String.toBinary(): String {
        return this.toByteArray().joinToString(" ") {
            it.toString(2).padStart(8, '0')
        }
    }

    private fun String.toMorseCode(): String {
        val morseMap = mapOf(
            'a' to ".-", 'b' to "-...", 'c' to "-.-.", 'd' to "-..", 'e' to ".", 'f' to "..-.",
            'g' to "--.", 'h' to "....", 'i' to "..", 'j' to ".---", 'k' to "-.-", 'l' to ".-..",
            'm' to "--", 'n' to "-.", 'o' to "---", 'p' to ".--.", 'q' to "--.-", 'r' to ".-.",
            's' to "...", 't' to "-", 'u' to "..-", 'v' to "...-", 'w' to ".--", 'x' to "-..-",
            'y' to "-.--", 'z' to "--..", '1' to ".----", '2' to "..---", '3' to "...--",
            '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
            '9' to "----.", '0' to "-----"
        )
        return this.lowercase().chunked(1).joinToString(" ") {
            morseMap[it[0]] ?: it
        }
    }

    private fun String.toWave(): String {
        val waveChars = arrayOf(" ", ".", "o", "O", "o", ".", " ")
        val waveText = StringBuilder()
        for ((index, char) in this.withIndex()) {
            waveText.append(waveChars[index % waveChars.size]).append(char)
        }
        return waveText.toString()
    }
}
