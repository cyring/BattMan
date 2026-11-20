package fr.cyring.battman.ai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizerOptions
import fr.cyring.battman.AiCapability
import fr.cyring.battman.AiCapability.*
import fr.cyring.battman.R
import kotlinx.coroutines.guava.await

enum class AiFeatureState {
    AI_UNKNOWN,
    AI_AVAILABLE,
    AI_DOWNLOADABLE,
    AI_DOWNLOADING,
    AI_UNAVAILABLE
}

data class AiCapabilityStatus(
    val state: AiFeatureState = AiFeatureState.AI_UNKNOWN,
    val errorCode: Int? = null,
    val detail: String? = null
)

class AiMonitor(context: Context) {
    private val appContext = context.applicationContext

    var aiAsStatus: AiCapabilityStatus by mutableStateOf(AiCapabilityStatus())
        private set

    var aiCoreStatus: AiCapabilityStatus by mutableStateOf(AiCapabilityStatus())
        private set

    var aiPromptStatus: AiCapabilityStatus by mutableStateOf(AiCapabilityStatus())
        private set

    var aiNlpStatus: AiCapabilityStatus by mutableStateOf(AiCapabilityStatus())
        private set

    var aiVlmStatus: AiCapabilityStatus by mutableStateOf(AiCapabilityStatus())
        private set

    var aiSpeechStatus: AiCapabilityStatus by mutableStateOf(AiCapabilityStatus())
        private set

    private fun probePackage(packageName: String): AiCapabilityStatus {
        return try {
            val pm = appContext.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val appInfo = packageInfo.applicationInfo
            val isEnabled = (appInfo?.enabled == true)
            val version = packageInfo.versionName

            if (isEnabled == true) {
                AiCapabilityStatus(
                    state = AiFeatureState.AI_AVAILABLE,
                    errorCode = null,
                    detail = version
                )
            } else {
                AiCapabilityStatus(
                    state = AiFeatureState.AI_UNAVAILABLE,
                    errorCode = null,
                    detail = version
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = null, detail = null)
        } catch (_: Throwable) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = null, detail = null)
        }
    }

    private fun mapFeatureStatus(status: Int): AiFeatureState {
        return when (status) {
            FeatureStatus.AVAILABLE -> AiFeatureState.AI_AVAILABLE
            FeatureStatus.DOWNLOADABLE -> AiFeatureState.AI_DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> AiFeatureState.AI_DOWNLOADING
            else -> AiFeatureState.AI_UNAVAILABLE
        }
    }

    private suspend fun probePrompt(): AiCapabilityStatus {
        return try {
            val client = Generation.getClient()
            val status = client.checkStatus()

            AiCapabilityStatus(state = mapFeatureStatus(status), errorCode = null)
        } catch (e: GenAiException) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = e.errorCode)
        } catch (_: Throwable) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = null)
        }
    }

    private suspend fun probeNLP(): AiCapabilityStatus {
        val options = SummarizerOptions.builder(appContext)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.ONE_BULLET)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build()
        val client = Summarization.getClient(options)

        return try {
            val status: Int = client.checkFeatureStatus().await()
            AiCapabilityStatus(state = mapFeatureStatus(status), errorCode = null)
        } catch (e: GenAiException) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = e.errorCode)
        } catch (_: Throwable) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = null)
        } finally {
            try {
                client.close()
            } catch (_: Throwable) {
                // NOP
            }
        }
    }

    private suspend fun probeVLM(): AiCapabilityStatus {
        val options = ImageDescriberOptions.builder(appContext).build()
        val client = ImageDescription.getClient(options)

        return try {
            val status: Int = client.checkFeatureStatus().await()
            AiCapabilityStatus(state = mapFeatureStatus(status), errorCode = null)
        } catch (e: GenAiException) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = e.errorCode)
        } catch (_: Throwable) {
            AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = null)
        } finally {
            try {
                client.close()
            } catch (_: Throwable) {
                // NOP
            }
        }
    }

    private fun probeSpeech(): AiCapabilityStatus {
        return AiCapabilityStatus(state = AiFeatureState.AI_UNAVAILABLE, errorCode = null)
    }

    suspend fun probeComponents() {
        aiAsStatus = probePackage("com.google.android.as")
        aiCoreStatus = probePackage("com.google.android.aicore")
        aiPromptStatus = probePrompt()
        aiNlpStatus = probeNLP()
        aiVlmStatus = probeVLM()
        aiSpeechStatus = probeSpeech()
    }

    fun buildComponent(
        context: Context,
        capability: AiCapability,
        showVersion: Boolean = false
    ): AnnotatedString {
        val info = when (capability) {
            AI_SA_CAP -> aiAsStatus
            AI_CORE_CAP -> aiCoreStatus
            AI_PROMPT_CAP -> aiPromptStatus
            AI_NLP_CAP -> aiNlpStatus
            AI_VLM_CAP -> aiVlmStatus
            AI_SPEECH_CAP -> aiSpeechStatus
        }

        val label = when (info.state) {
            AiFeatureState.AI_AVAILABLE -> context.getString(R.string.activated)
            AiFeatureState.AI_DOWNLOADABLE -> context.getString(R.string.ai_downloadable)
            AiFeatureState.AI_DOWNLOADING -> context.getString(R.string.ai_downloading)
            AiFeatureState.AI_UNAVAILABLE -> context.getString(R.string.unavailable)
            AiFeatureState.AI_UNKNOWN -> context.getString(R.string.none)
        }

        return buildAnnotatedString {
            if (showVersion == true && info.state == AiFeatureState.AI_AVAILABLE && info.detail != null) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "ai_version",
                        styles = TextLinkStyles(),
                        linkInteractionListener = {
                            Toast.makeText(context, info.detail, Toast.LENGTH_LONG).show()
                        }
                    )
                ) {
                    append(label)
                    append(context.getString(R.string._info_))
                }
            } else {
                append(label)
            }
        }
    }
}