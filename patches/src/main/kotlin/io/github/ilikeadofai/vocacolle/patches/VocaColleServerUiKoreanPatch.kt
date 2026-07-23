package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import io.github.ilikeadofai.vocacolle.patches.shared.Constants.VOCACOLLE

private const val SERVER_UI_TRANSLATOR =
    "Lio/github/ilikeadofai/vocacolle/extension/ServerUiKoreanTranslator;"
private const val NICO_PUSH_TOPIC = "Lzf/j;"

private object RankingDisplayLabelFingerprint : Fingerprint(
    definingClass = "Ljp/nicovideo/nicobox/ui/ranking/b;",
    name = "g",
    returnType = "Ljava/lang/String;",
    parameters = listOf("I", "Landroid/content/Context;")
)

private object NicoPushTitleFingerprint : Fingerprint(
    definingClass = NICO_PUSH_TOPIC,
    name = "c",
    returnType = "Ljava/lang/String;",
    parameters = emptyList()
)

private object NicoPushDescriptionFingerprint : Fingerprint(
    definingClass = NICO_PUSH_TOPIC,
    name = "a",
    returnType = "Ljava/lang/String;",
    parameters = emptyList()
)

@Suppress("unused")
val vocacolleServerUiKoreanPatch = bytecodePatch(
    name = "Korean native server UI",
    description = "Translates whitelisted server-provided labels only at native UI display boundaries.",
    default = false
) {
    compatibleWith(VOCACOLLE)
    extendWith("extensions/extension.mpe")

    execute {
        patchRankingDisplayReturns()
        patchPushDisplayGetter(
            NicoPushTitleFingerprint,
            "translatePushTitle"
        )
        patchPushDisplayGetter(
            NicoPushDescriptionFingerprint,
            "translatePushDescription"
        )
    }
}

context(_: BytecodePatchContext)
private fun patchRankingDisplayReturns() {
    val method = RankingDisplayLabelFingerprint.method
    val returns = method.implementation!!.instructions
        .withIndex()
        .filter { it.value.opcode == Opcode.RETURN_OBJECT }
        .toList()

    check(returns.isNotEmpty()) { "Ranking display method has no object returns" }

    returns.asReversed().forEach { (index, instruction) ->
        val register = (instruction as OneRegisterInstruction).registerA
        method.replaceInstruction(
            index,
            "invoke-static {v$register}, $SERVER_UI_TRANSLATOR->translateRanking(Ljava/lang/String;)Ljava/lang/String;"
        )
        method.addInstructions(
            index + 1,
            """
                move-result-object v$register
                return-object v$register
            """.trimIndent()
        )
    }
}

context(_: BytecodePatchContext)
private fun patchPushDisplayGetter(
    fingerprint: Fingerprint,
    translatorMethod: String
) {
    val method = fingerprint.method
    val implementation = method.implementation!!
    val returns = implementation.instructions
        .withIndex()
        .filter { it.value.opcode == Opcode.RETURN_OBJECT }
        .toList()

    check(returns.size == 1) {
        "Expected one object return in ${method.definingClass}->${method.name}, found ${returns.size}"
    }

    val (index, instruction) = returns.single()
    val sourceRegister = (instruction as OneRegisterInstruction).registerA
    val parameterRegister = implementation.registerCount - 1
    check(sourceRegister != parameterRegister) {
        "Push display getter needs a local register distinct from p0"
    }

    method.replaceInstruction(
        index,
        "iget-object p0, p0, $NICO_PUSH_TOPIC->d:Ljava/lang/String;"
    )
    method.addInstructions(
        index + 1,
        """
            invoke-static {p0, v$sourceRegister}, $SERVER_UI_TRANSLATOR->$translatorMethod(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
            move-result-object v$sourceRegister
            return-object v$sourceRegister
        """.trimIndent()
    )
}
