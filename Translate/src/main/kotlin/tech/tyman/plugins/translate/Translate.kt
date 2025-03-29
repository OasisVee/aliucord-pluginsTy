package tech.tyman.plugins.translate

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.aliucord.CollectionUtils
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.discord.api.commands.ApplicationCommandType
import com.discord.databinding.WidgetChatListActionsBinding
import com.discord.utilities.textprocessing.node.EditedMessageNode
import com.discord.utilities.view.text.SimpleDraweeSpanTextView
import com.discord.widgets.chat.list.WidgetChatList
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.MessageEntry
import com.facebook.drawee.span.DraweeSpanStringBuilder
import com.lytefast.flexinput.R
import org.json.JSONArray
import java.lang.reflect.Field
import java.util.regex.Pattern

@AliucordPlugin
class Translate : Plugin() {
    lateinit var pluginIcon: Drawable
    private val translatedMessages = mutableMapOf<Long, TranslateSuccessData>()
    private var chatList: WidgetChatList? = null
    private val messageLoggerEditedRegex = Pattern.compile("(?:.+ \\(.+: .+\\)\\n)+(.+)\$")

    // Improved patterns for Discord formatting elements
    private val mentionPattern = Pattern.compile("<@!?\\d+>|<@&\\d+>|<#\\d+>")
    // Fixed emoji pattern to ensure no spaces are added
    private val emojiPattern = Pattern.compile("<:[a-zA-Z0-9_]+:\\d+>|<a:[a-zA-Z0-9_]+:\\d+>")
    // Enhanced formatting pattern to capture content within markdown markers
    private val formattingPattern = Pattern.compile("(\\*\\*|\\*|__|_|~~|`{1,3}|\\|\\||>)([^\\1]*?)(\\1|$)")

    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(settings)
    }

    override fun load(ctx: Context) {
        pluginIcon = ContextCompat.getDrawable(ctx, R.e.ic_locale_24dp)!!
    }

    override fun start(context: Context) {
        patchMessageContextMenu()
        patchProcessMessageText()
        commands.registerCommand(
            "translate",
            "Translates text from one language to another, sends by default",
            listOf(
                Utils.createCommandOption(ApplicationCommandType.STRING, "text", "The text to translate"),
                Utils.createCommandOption(ApplicationCommandType.STRING, "to", "The language to translate to (default en, must be a language code described in plugin settings)"),
                Utils.createCommandOption(ApplicationCommandType.STRING, "from", "The language to translate from (default auto, must be a language code described in plugin settings)"),
                Utils.createCommandOption(ApplicationCommandType.BOOLEAN, "send", "Whether or not to send the message in chat (default true)")
            )
        ) { ctx ->
            val translateData = translateMessage(
                ctx.getRequiredString("text"),
                ctx.getString("from"),
                ctx.getString("to")
            )
            if (translateData !is TranslateSuccessData) {
                with(translateData as TranslateErrorData) {
                    return@registerCommand CommandsAPI.CommandResult(
                        "$errorText ($errorCode)",
                        null,
                        false
                    )
                }
            }
            return@registerCommand CommandsAPI.CommandResult(
                translateData.translatedText,
                null,
                ctx.getBoolOrDefault("send", true)
            )
        }
    }

    private fun DraweeSpanStringBuilder.setTranslated(translateData: TranslateSuccessData, context: Context) {
        val builderString = this.toString()

        val contentStartIndex = messageLoggerEditedRegex.matcher(builderString).let {
            if (it.find()) {
                it.start(1)
            } else 0
        }

        // Make sure we don't replace beyond the string length
        val endIndex = Math.min(contentStartIndex + translateData.sourceText.length, builderString.length)

        // Replace only up to the valid end index
        this.replace(contentStartIndex, endIndex, translateData.translatedText)

        val textEnd = this.length
        this.append(" (translated: ${translateData.sourceLanguage} -> ${translateData.translatedLanguage})")
        this.setSpan(RelativeSizeSpan(0.75f), textEnd, this.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (textEnd != this.length) {
            this.setSpan(EditedMessageNode.Companion.`access$getForegroundColorSpan`(EditedMessageNode.Companion, context),
                textEnd, this.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun patchProcessMessageText() {
        patcher.patch(WidgetChatList::class.java.getDeclaredConstructor(), Hook {
            chatList = it.thisObject as WidgetChatList
        })

        val mDraweeStringBuilder: Field = SimpleDraweeSpanTextView::class.java.getDeclaredField("mDraweeStringBuilder")
        mDraweeStringBuilder.isAccessible = true
        patcher.patch(WidgetChatListAdapterItemMessage::class.java, "processMessageText", arrayOf(SimpleDraweeSpanTextView::class.java, MessageEntry::class.java), Hook {
            val messageEntry = it.args[1] as MessageEntry
            val message = messageEntry.message ?: return@Hook
            val id = message.id
            val translateData = translatedMessages[id] ?: return@Hook
            if (translateData.showingOriginal) return@Hook

            // Get the content of the message
            val messageContent = message.content ?: return@Hook

            // Check if the source text matches or is contained within the message content
            if (!messageContent.contains(translateData.sourceText)) {
                translatedMessages.remove(id)
                return@Hook
            }

            val textView = it.args[0] as SimpleDraweeSpanTextView
            val builder = mDraweeStringBuilder[textView] as DraweeSpanStringBuilder?
                ?: return@Hook
            val context = textView.context

            try {
                builder.setTranslated(translateData, context)
                textView.setDraweeSpanStringBuilder(builder)
            } catch (e: Exception) {
                // Remove the translation data if we encounter an error
                translatedMessages.remove(id)
                Utils.showToast("Translation error: ${e.message}", true)
            }
        })
    }

    private fun patchMessageContextMenu() {
        val viewId = View.generateViewId()
        val messageContextMenu = WidgetChatListActions::class.java
        val getBinding = messageContextMenu.getDeclaredMethod("getBinding").apply { isAccessible = true }

        patcher.patch(messageContextMenu.getDeclaredMethod("configureUI", WidgetChatListActions.Model::class.java), Hook {
            val menu = it.thisObject as WidgetChatListActions
            val binding = getBinding.invoke(menu) as WidgetChatListActionsBinding
            val translateButton = binding.a.findViewById<TextView>(viewId)
            translateButton.setOnClickListener { _ ->
                val message = (it.args[0] as WidgetChatListActions.Model).message
                val translationEntry = translatedMessages[message.id]

                if (translationEntry == null) {
                    // If not translated yet, fetch and cache the translation, then rerender the message
                    Utils.threadPool.execute {
                        val response = translateMessage(message.content)
                        if (response !is TranslateSuccessData) {
                            with(response as TranslateErrorData) {
                                Utils.showToast("$errorText ($errorCode)", true)
                                return@execute
                            }
                        }
                        translatedMessages[message.id] = response
                        chatList?.rerenderMessage(message.id)
                        Utils.showToast("Translated message")
                        menu.dismiss()
                    }
                } else {
                    // If translated, then no need to translate anything, so just flip the showingOriginal property and rerender
                    translationEntry.showingOriginal = !translationEntry.showingOriginal
                    chatList?.rerenderMessage(message.id)
                    menu.dismiss()
                }
            }
        })

        patcher.patch(messageContextMenu, "onViewCreated", arrayOf(View::class.java, Bundle::class.java), Hook {
            val linearLayout = (it.args[0] as NestedScrollView).getChildAt(0) as LinearLayout
            val context = linearLayout.context
            val messageId = WidgetChatListActions.`access$getMessageId$p`(it.thisObject as WidgetChatListActions)
            linearLayout.addView(TextView(context, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                val translationEntry = translatedMessages[messageId]

                id = viewId
                text = if (translationEntry == null || translationEntry.showingOriginal) {
                    // If not translated yet, or original is currently shown
                    "Translate message"
                } else {
                    // Otherwise, it must be translated and original is not currently being shown
                    "Show original"
                }
                setCompoundDrawablesRelativeWithIntrinsicBounds(pluginIcon, null, null, null)
            })
        })
    }

    override fun stop(context: Context?) = patcher.unpatchAll()

    // Enhanced class to hold information about text segments that should be preserved
    data class SpecialTextSegment(
        val text: String,          // The full text of the segment
        val startIndex: Int,       // Start position in the original text
        val endIndex: Int,         // End position in the original text
        val contentStart: Int = -1, // Start of content within markers (for formatting)
        val contentEnd: Int = -1    // End of content within markers (for formatting)
    )

    private fun translateMessage(text: String, from: String? = null, to: String? = null): TranslateData {
        // Find all special segments that should be preserved
        val preservedSegments = mutableListOf<SpecialTextSegment>()

        // Process emoji first to ensure they're fully preserved
        findEmojiSegments(text, preservedSegments)
        
        // Process mentions to ensure they're preserved correctly
        findMentionSegments(text, preservedSegments)
        
        // Process formatting markers last
        findFormattingSegments(text, preservedSegments)

        // Sort segments by start index
        preservedSegments.sortBy { it.startIndex }

        // Merge overlapping segments
        val mergedSegments = mergeOverlappingSegments(preservedSegments)

        // Create a list of text parts to translate and their positions
        val textPartsToTranslate = mutableListOf<String>()
        val positions = mutableListOf<Int>()
        var lastIndex = 0

        for (segment in mergedSegments) {
            if (lastIndex < segment.startIndex) {
                // Add text between special segments for translation
                textPartsToTranslate.add(text.substring(lastIndex, segment.startIndex))
                positions.add(lastIndex)
            }
            lastIndex = segment.endIndex
        }

        // Add final text segment if exists
        if (lastIndex < text.length) {
            textPartsToTranslate.add(text.substring(lastIndex))
            positions.add(lastIndex)
        }

        // Translate only the non-preserved parts
        val toLang = to ?: settings.getString("defaultLanguage", "en")
        val fromLang = from ?: "auto"
        
        // If there's nothing to translate, return early
        if (textPartsToTranslate.isEmpty()) {
            return TranslateSuccessData(
                sourceLanguage = fromLang,
                translatedLanguage = toLang,
                sourceText = text,
                translatedText = text
            )
        }
        
        val queryBuilder = Http.QueryBuilder("https://translate.googleapis.com/translate_a/single").run {
            append("client", "gtx")
            append("sl", fromLang)
            append("tl", toLang)
            append("dt", "t")
            append("q", textPartsToTranslate.joinToString("\n\u0002\n"))  // Use a special delimiter
        }
        
        val translatedJsonReq = Http.Request(queryBuilder.toString(), "GET").apply {
            setHeader("Content-Type", "application/json")
            setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4592.0 Safari/537.36")
        }.execute()

        if (!translatedJsonReq.ok()) {
            return when (translatedJsonReq.statusCode) {
                429 -> TranslateErrorData(
                    errorCode = 429,
                    errorText = "Translate API ratelimit reached. Please try again later."
                )
                else -> TranslateErrorData(
                    errorCode = translatedJsonReq.statusCode,
                    errorText = "An unknown error occurred. Please report this to the developer of Translate."
                )
            }
        }

        try {
            val parsedJson = JSONArray(translatedJsonReq.text())
            val translatedSections = parsedJson.getJSONArray(0)
            
            // Get translated parts
            val translatedTextParts = mutableListOf<String>()
            for (i in 0 until translatedSections.length()) {
                val section = translatedSections.getJSONArray(i)
                if (section.length() > 0 && !section.isNull(0)) {
                    translatedTextParts.add(section.getString(0))
                }
            }

            // Reconstruct the full translated text, inserting preserved segments
            val reconstructedText = StringBuilder()
            var lastTranslatedPos = 0
            var translatedPartIndex = 0

            for (i in 0 until positions.size + mergedSegments.size) {
                if (i % 2 == 0 && translatedPartIndex < translatedTextParts.size) {
                    // Add translated part
                    reconstructedText.append(translatedTextParts[translatedPartIndex++])
                } else if (i % 2 == 1 && (i - 1) / 2 < mergedSegments.size) {
                    // Add preserved segment
                    val segment = mergedSegments[(i - 1) / 2]
                    reconstructedText.append(text.substring(segment.startIndex, segment.endIndex))
                }
            }

            // Get detected language (index 2), fallback to "unknown" if not available
            val detectedLang = if (parsedJson.length() > 2 && !parsedJson.isNull(2)) parsedJson.getString(2) else "unknown"

            return TranslateSuccessData(
                sourceLanguage = detectedLang,
                translatedLanguage = toLang,
                sourceText = text,
                translatedText = reconstructedText.toString()
            )
        } catch (e: Exception) {
            return TranslateErrorData(
                errorCode = -2,
                errorText = "Failed to parse translation response: ${e.message}"
            )
        }
    }

    // Helper function to find emoji segments
    private fun findEmojiSegments(text: String, segments: MutableList<SpecialTextSegment>) {
        val matcher = emojiPattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
    }

    // Helper function to find mention segments
    private fun findMentionSegments(text: String, segments: MutableList<SpecialTextSegment>) {
        val matcher = mentionPattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
    }

    // Helper function to find formatting segments with their content
    private fun findFormattingSegments(text: String, segments: MutableList<SpecialTextSegment>) {
        // Identify block formatting (code blocks, quotes, spoilers)
        findBlockFormatting(text, segments)
        
        // Identify inline formatting (bold, italic, underline, strikethrough)
        findInlineFormatting(text, segments)
    }
    
    // Handle block formatting like code blocks, quotes, and spoilers
    private fun findBlockFormatting(text: String, segments: MutableList<SpecialTextSegment>) {
        // Match code blocks (```code```)
        val codeBlockPattern = Pattern.compile("```([\\s\\S]*?)```")
        var matcher = codeBlockPattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
        
        // Match inline code (`code`)
        val inlineCodePattern = Pattern.compile("`([^`]+)`")
        matcher = inlineCodePattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
        
        // Match spoilers (||spoiler||)
        val spoilerPattern = Pattern.compile("\\|\\|([\\s\\S]*?)\\|\\|")
        matcher = spoilerPattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
        
        // Match quotes (> quote)
        val quotePattern = Pattern.compile("^(>.+?)$", Pattern.MULTILINE)
        matcher = quotePattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
    }
    
    // Handle inline formatting like bold, italic, underline, strikethrough
    private fun findInlineFormatting(text: String, segments: MutableList<SpecialTextSegment>) {
        // Match bold (**bold**)
        var pattern = Pattern.compile("\\*\\*([^*]+?)\\*\\*")
        var matcher = pattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
        
        // Match italic (*italic*)
        pattern = Pattern.compile("\\*([^*]+?)\\*")
        matcher = pattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
        
        // Match underline (__underline__)
        pattern = Pattern.compile("__([^_]+?)__")
        matcher = pattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
        
        // Match italic with underscore (_italic_)
        pattern = Pattern.compile("_([^_]+?)_")
        matcher = pattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
        
        // Match strikethrough (~~strikethrough~~)
        pattern = Pattern.compile("~~([^~]+?)~~")
        matcher = pattern.matcher(text)
        while (matcher.find()) {
            segments.add(SpecialTextSegment(
                text = matcher.group(),
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }
    }

    // Helper function to merge overlapping segments
    private fun mergeOverlappingSegments(segments: List<SpecialTextSegment>): List<SpecialTextSegment> {
        if (segments.isEmpty()) return segments
        
        val sortedSegments = segments.sortedBy { it.startIndex }
        val mergedSegments = mutableListOf<SpecialTextSegment>()
        var current = sortedSegments[0]
        
        for (i in 1 until sortedSegments.size) {
            val next = sortedSegments[i]
            if (current.endIndex >= next.startIndex) {
                // Segments overlap, merge them
                current = SpecialTextSegment(
                    text = current.text + next.text.substring(current.endIndex - next.startIndex),
                    startIndex = current.startIndex,
                    endIndex = Math.max(current.endIndex, next.endIndex)
                )
            } else {
                // No overlap, add current and move to next
                mergedSegments.add(current)
                current = next
            }
        }
        
        // Add the last segment
        mergedSegments.add(current)
        return mergedSegments
    }
}
