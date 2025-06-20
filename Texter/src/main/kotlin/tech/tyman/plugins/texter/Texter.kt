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
                "japanese",
                "Converts your text into Japanese-style characters", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toJapanese()) }
        registerConverterCommand(
                "clap",
                "Adds clapping icons to your text", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.clapify()) }
        registerConverterCommand(
                "reverse",
                "Reverses your text", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.reverse()) }
        registerConverterCommand(
                "space",
                "Spaces out your text", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.chunked(1).joinToString(" ")) }
        registerConverterCommand(
                "mock",
                "Capitalizes random parts of your text", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.mock()) }
        registerConverterCommand(
                "leet",
                "Makes your text 1337 style", listOf(
                "leetify"
        )
        ) { ctx: CommandContext -> getResult(Maps.leetLetters, ctx.getString("text")!!) }
        registerConverterCommand(
                "piglatin",
                "Converts your text to Pig Latin", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toPigLatin()) }
        registerConverterCommand(
                "binary",
                "Converts text to binary or binary back to text", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toBinaryOrFromBinary()) }
        registerConverterCommand(
                "morse",
                "Converts your text to Morse code", emptyList()
        ) { ctx: CommandContext -> getResult(ctx.getString("text")!!.toMorseCode()) }
        registerConverterCommand(
                "wave",
                "Adds a wave effect to your text", emptyList()
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

    private fun String.toJapanese(): String {
        val japaneseMap = mapOf(
            'a' to '卂', 'b' to '乃', 'c' to '匚', 'd' to '刀', 'e' to '乇', 'f' to '千', 'g' to '厶',
            'h' to '卄', 'i' to '丨', 'j' to 'ﾌ', 'k' to 'Ҝ', 'l' to 'ㄥ', 'm' to '爪', 'n' to '几',
            'o' to 'ㄖ', 'p' to '卩', 'q' to 'Ɋ', 'r' to '尺', 's' to '丂', 't' to 'ㄒ', 'u' to 'ㄩ',
            'v' to 'ᐯ', 'w' to '山', 'x' to '乂', 'y' to 'ㄚ', 'z' to '乙'
        )
        return this.map {
            japaneseMap[it.lowercaseChar()] ?: it
        }.joinToString("")
    }

    private fun String.toBinary(): String {
        return this.toByteArray().joinToString(" ") {
            it.toString(2).padStart(8, '0')
        }
    }

    private fun String.fromBinary(): String {
        return try {
            this.split(" ").map { binaryString ->
                binaryString.toInt(2).toChar()
            }.joinToString("")
        } catch (e: Exception) {
            "Error: Invalid binary format"
        }
    }

    private fun String.isBinary(): Boolean {
        val trimmed = this.trim()
        if (trimmed.isEmpty()) return false
        
        // Check if it contains only 0s, 1s, and spaces
        val validChars = trimmed.all { it == '0' || it == '1' || it == ' ' }
        if (!validChars) return false
        
        // Check if it has the typical binary format (groups of 8 bits separated by spaces)
        val binaryGroups = trimmed.split(" ")
        return binaryGroups.isNotEmpty() && binaryGroups.all { group ->
            group.isNotEmpty() && group.all { it == '0' || it == '1' } && group.length <= 8
        }
    }

    private fun String.toBinaryOrFromBinary(): String {
        return if (this.isBinary()) {
            this.fromBinary()
        } else {
            this.toBinary()
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