package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.ilikeadofai.vocacolle.patches.shared.Constants.VOCACOLLE

private const val PLAYER_FRAGMENT = "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;"
private const val PLAYER_VIEW_MODEL = "Ljp/nicovideo/nicobox/ui/player/o;"
private const val VIDEO_INFO_DETAIL = "Ljp/nicovideo/nicobox/ui/player/o\$j;"
private const val TITLE_AND_OWNER = "Ljp/nicovideo/nicobox/ui/player/o\$h;"
private const val PLAYER_MEDIA_CENTER =
    "Ljp/nicovideo/nicobox/ui/player/PlayerMediaCenterContentView;"
private const val METADATA_CONTROL =
    "Lio/github/ilikeadofai/vocacolle/extension/metadata/MetadataControl;"
private const val PLAYER_TITLE_ENRICHMENT =
    "Lio/github/ilikeadofai/vocacolle/extension/metadata/PlayerTitleEnrichment;"
private const val PLAYER_TITLE_BRIDGE =
    "Lio/github/ilikeadofai/vocacolle/extension/metadata/PlayerTitleBridge;"

internal val playerTitleStateFingerprint = Fingerprint(
    definingClass = PLAYER_FRAGMENT,
    name = "J4",
    returnType = "Lnl/L;",
    parameters = listOf("LEl/M;", "LEl/O;", PLAYER_FRAGMENT, TITLE_AND_OWNER),
    custom = { method, _ ->
        method.implementation?.let { implementation ->
            implementation.registerCount == 7 &&
                implementation.instructions.count { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                        it.definingClass == PLAYER_MEDIA_CENTER &&
                            it.name == "setDisplayType" &&
                            it.returnType == "V"
                    } == true
                } == 1
        } == true
    }
)

internal val currentVideoInfoFingerprint = Fingerprint(
    definingClass = PLAYER_FRAGMENT,
    name = "K4",
    returnType = "Lnl/L;",
    parameters = listOf(PLAYER_FRAGMENT, "LEl/K;", VIDEO_INFO_DETAIL),
    custom = { method, _ ->
        method.implementation?.let { implementation ->
            implementation.registerCount == 12 &&
                implementation.instructions.count { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                        it.definingClass == PLAYER_VIEW_MODEL &&
                            it.name == "h2" &&
                            it.parameterTypes.map(CharSequence::toString) == listOf(
                                "Ljava/lang/String;",
                                "Ljava/lang/String;",
                                "Ljava/lang/String;",
                                "I",
                                "Z"
                            ) &&
                            it.returnType == "V"
                    } == true
                } == 1
        } == true
    }
)

@Suppress("unused")
val vocacolleVocaDbPlayerTitlePatch = bytecodePatch(
    name = "VocaDB player titles",
    description = "Adds opt-in exact VocaDB title metadata to the full player.",
    default = false
) {
    compatibleWith(VOCACOLLE)
    dependsOn(vocacolleMorpheSettingsPatch)
    extendWith("extensions/extension.mpe")

    execute {
        initializeMetadataControl()
        makePlayerTitleDedupeTitleAware()
        startPlayerTitleEnrichment()
    }
}

context(_: BytecodePatchContext)
private fun initializeMetadataControl() {
    val method = applicationOnCreateFingerprint.method
    val superCalls = method.implementation!!.instructions.withIndex().filter { (_, instruction) ->
        instruction.opcode == Opcode.INVOKE_SUPER &&
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == "Landroid/app/Application;" &&
                    it.name == "onCreate" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "V"
            } == true
    }
    check(superCalls.size == 1) {
        "Expected one exact VocaColle Application super.onCreate call, found ${superCalls.size}"
    }
    method.addInstructions(
        superCalls.single().index + 1,
        "invoke-static {}, $METADATA_CONTROL->markHooksInstalled()V"
    )
}

context(_: BytecodePatchContext)
private fun makePlayerTitleDedupeTitleAware() {
    val method = playerTitleStateFingerprint.method
    val videoIdGetters = method.implementation!!.instructions.withIndex().filter { (_, instruction) ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
            it.definingClass == TITLE_AND_OWNER &&
                it.name == "d" &&
                it.parameterTypes.isEmpty() &&
                it.returnType == "Ljava/lang/String;"
        } == true
    }
    check(videoIdGetters.size == 1) {
        "Expected one player title-state video ID getter, found ${videoIdGetters.size}"
    }
    val getterIndex = videoIdGetters.single().index
    val videoIdResult = method.implementation!!.instructions[getterIndex + 1]
    check(
        videoIdResult.opcode == Opcode.MOVE_RESULT_OBJECT &&
            (videoIdResult as? OneRegisterInstruction)?.registerA == 1
    ) {
        "Expected player title-state video ID in v1"
    }
    method.addInstructions(
        getterIndex + 2,
        """
            invoke-virtual {v6}, $TITLE_AND_OWNER->c()Ljava/lang/String;
            move-result-object v2
            invoke-static {v1, v2}, $PLAYER_TITLE_BRIDGE->bindingKey(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
            move-result-object v1
        """.trimIndent()
    )
}

context(_: BytecodePatchContext)
private fun startPlayerTitleEnrichment() {
    val method = currentVideoInfoFingerprint.method
    val titleStateCalls = method.implementation!!.instructions.withIndex().filter { (_, instruction) ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
            it.definingClass == PLAYER_VIEW_MODEL &&
                it.name == "h2" &&
                it.parameterTypes.map(CharSequence::toString) == listOf(
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "I",
                    "Z"
                ) &&
                it.returnType == "V"
        } == true
    }
    check(titleStateCalls.size == 1) {
        "Expected one current-video title-state publish call, found ${titleStateCalls.size}"
    }
    val titleStateCall = titleStateCalls.single().value
    check(
        (titleStateCall as? RegisterRangeInstruction)?.let {
            it.startRegister == 1 && it.registerCount == 6
        } == true
    ) {
        "Expected current-video title-state arguments in v1..v6"
    }
    method.addInstructions(
        titleStateCalls.single().index + 1,
        """
            invoke-virtual {v9}, $PLAYER_FRAGMENT->W3()$PLAYER_MEDIA_CENTER
            move-result-object v0
            invoke-static/range {v0 .. v6}, $PLAYER_TITLE_ENRICHMENT->onBound(Landroid/view/View;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ)V
        """.trimIndent()
    )
}
