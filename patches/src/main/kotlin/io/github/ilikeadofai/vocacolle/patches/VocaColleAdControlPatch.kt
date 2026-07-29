package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.ilikeadofai.vocacolle.patches.shared.Constants.VOCACOLLE

private const val APP_OPEN_AD_PROVIDER = "Llg/c;"
private const val AD_CONTROL =
    "Lio/github/ilikeadofai/vocacolle/extension/ads/AdControl;"
private const val NO_AUDIO_AD = "Lcf/c\$c;"
private const val DISPLAY_AD_CONTROLLER = "LBj/b;"
private const val DISPLAY_AD_STATE = "LBj/a;"
private const val HIGH_QUALITY_PREMIUM_MESSAGE = 0x7f130553
private const val HIGH_QUALITY_PREMIUM_ACTION = 0x7f130554
private const val PREMIUM_DIALOG =
    "Ljp/nicovideo/nicobox/ui/premiummerit/PremiumMeritLeadDialog;"
private const val PREMIUM_DIALOG_FACTORY =
    "Ljp/nicovideo/nicobox/ui/premiummerit/PremiumMeritLeadDialog\$a;"

internal val appOpenAdAllowedFingerprint = Fingerprint(
    definingClass = APP_OPEN_AD_PROVIDER,
    name = "n",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Landroid/content/Context;", "Lsl/e;"),
    strings = listOf(
        "App Open Ad is not allowed. (no user info)",
        "App Open Ad is not allowed. (premium member)"
    ),
    custom = { method, _ -> method.implementation?.registerCount == 8 }
)

internal val audioAdContentFingerprint = Fingerprint(
    definingClass = "Lwh/b;",
    name = "e",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Z", "Z", "Lsl/e;"),
    custom = { method, _ -> method.implementation?.registerCount == 10 }
)

internal val displayAdLoadFingerprint = Fingerprint(
    definingClass = DISPLAY_AD_CONTROLLER,
    name = "s",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("loadAd("),
    custom = { method, _ -> method.implementation?.registerCount == 11 }
)

private fun premiumPromotionFingerprint(
    definingClass: String,
    name: String,
    returnType: String,
    parameters: List<String>,
    analyticsId: String,
    registerCount: Int
) = Fingerprint(
    definingClass = definingClass,
    name = name,
    returnType = returnType,
    parameters = parameters,
    strings = listOf(analyticsId),
    custom = { method, _ ->
        method.implementation?.let { implementation ->
            implementation.registerCount == registerCount &&
                implementation.instructions.count { instruction ->
                    ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.let {
                        it.definingClass == PREMIUM_DIALOG && it.name == "h1"
                    } == true
                } == 1
        } == true
    }
)

internal val homePremiumPromotionFingerprint = Fingerprint(
    definingClass = "Ljp/nicovideo/nicobox/ui/home/HomeFragment;",
    name = "N3",
    returnType = "Lnl/L;",
    parameters = listOf(
        "Ljp/nicovideo/nicobox/ui/home/HomeFragment;",
        "LFf/a;"
    ),
    custom = { method, _ ->
        method.implementation?.registerCount == 4 &&
            method.implementation?.instructions?.count {
                val reference = (it as? ReferenceInstruction)?.reference as? FieldReference
                reference?.definingClass ==
                    "Ljp/nicovideo/nicobox/ui/premiummerit/PremiumMeritLeadInfoBottomSheetDialog;" &&
                    reference.name == "i1"
            } == 1 &&
            method.implementation?.instructions?.count {
                val reference = (it as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == "Landroidx/fragment/app/DialogFragment;" &&
                    reference.name == "o2" &&
                    reference.returnType == "V"
            } == 1
    }
)

internal val highQualityPremiumSnackbarFingerprint = Fingerprint(
    definingClass = "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
    name = "y4",
    returnType = "Lnl/L;",
    parameters = listOf(
        "Ljp/nicovideo/nicobox/ui/player/PlayerFragment;",
        "Lwh/l;"
    ),
    custom = { method, _ ->
        method.implementation?.let { implementation ->
            val instructions = implementation.instructions
            implementation.registerCount == 9 &&
                instructions.count {
                    (it as? WideLiteralInstruction)?.wideLiteral ==
                        HIGH_QUALITY_PREMIUM_MESSAGE.toLong()
                } == 1 &&
                instructions.count {
                    (it as? WideLiteralInstruction)?.wideLiteral ==
                        HIGH_QUALITY_PREMIUM_ACTION.toLong()
                } == 1 &&
                instructions.count {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
                        reference.definingClass ==
                            "Ljp/nicovideo/nicobox/ui/player/PlayerContainerView;" &&
                            reference.name == "setMediaAudioQualityInfo" &&
                            reference.returnType == "V"
                    } == true
                } == 1 &&
                instructions.count {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
                        reference.definingClass ==
                            "Lcom/google/android/material/snackbar/Snackbar;" &&
                            reference.name == "Y" &&
                            reference.returnType == "V"
                    } == true
                } == 1
        } == true
    }
)

internal val premiumPromotionFingerprints = listOf(
    premiumPromotionFingerprint(
        "LZi/r;", "v", "V", listOf("Landroidx/fragment/app/u;"),
        "premium_merit_lead_from_playlist_create", 4
    ),
    premiumPromotionFingerprint(
        "Lbj/t;", "x", "V", listOf("Landroidx/fragment/app/u;"),
        "premium_merit_lead_from_playlist_select", 4
    ),
    premiumPromotionFingerprint(
        "Ljp/nicovideo/nicobox/ui/searchresult/SearchResultFragment;", "G2", "Lnl/L;",
        listOf(
            "Ljp/nicovideo/nicobox/ui/searchresult/SearchResultFragment;",
            "Lq0/u2;",
            "Lq0/u2;"
        ),
        "premium_merit_lead_from_search_result", 4
    ),
    premiumPromotionFingerprint(
        "Ljp/nicovideo/nicobox/ui/publicmylist/PublicMylistFragment;", "x3", "V", emptyList(),
        "premium_merit_lead_from_public_mylist", 4
    ),
    premiumPromotionFingerprint(
        "Ljp/nicovideo/nicobox/ui/player/PlayerFragment\$k;", "b", "V", listOf("Z", "Z"),
        "premium_merit_lead_from_player", 5
    ),
    premiumPromotionFingerprint(
        "Ljp/nicovideo/nicobox/ui/setting/cache/CacheSettingFragment;", "q2", "Lnl/L;",
        listOf(
            "Ljp/nicovideo/nicobox/ui/setting/cache/CacheSettingFragment;",
            "Lq0/u2;",
            "Z"
        ),
        "premium_merit_lead_from_cache_setting", 4
    ),
    premiumPromotionFingerprint(
        "Ljp/nicovideo/nicobox/ui/setting/player/PlayerSettingFragment;", "o2", "Lnl/L;",
        listOf(
            "Ljp/nicovideo/nicobox/ui/setting/player/PlayerSettingFragment;",
            "Z"
        ),
        "premium_merit_lead_from_player_setting", 4
    )
)

@Suppress("unused")
val vocacolleAdControlPatch = bytecodePatch(
    name = "VocaColle ad control",
    description = "Adds opt-in controls for VocaColle advertising surfaces.",
    default = false
) {
    compatibleWith(VOCACOLLE)
    dependsOn(vocacolleMorpheSettingsPatch)
    extendWith("extensions/extension.mpe")

    execute {
        initializeAdControl()
        overrideAppOpenAdEligibility()
        overrideDisplayAdLoad()
        overrideAudioAdContent()
        suppressHomePremiumPromotion()
        suppressHighQualityPremiumSnackbar()
        premiumPromotionFingerprints.forEach { suppressPremiumPromotion(it) }
    }
}

context(_: BytecodePatchContext)
private fun initializeAdControl() {
    val method = applicationOnCreateFingerprint.method
    val superCalls = method.implementation!!.instructions.withIndex().filter { (_, instruction) ->
        instruction.opcode == Opcode.INVOKE_SUPER &&
            ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                it.definingClass == "Landroid/app/Application;" &&
                    it.name == "onCreate" &&
                    it.returnType == "V" &&
                    it.parameterTypes.isEmpty()
            } == true
    }
    check(superCalls.size == 1) {
        "Expected one Application super.onCreate call, found ${superCalls.size}"
    }
    method.addInstructions(
        superCalls.single().index + 1,
        "invoke-static {p0}, $AD_CONTROL->initialize(Landroid/content/Context;)V"
    )
}

context(_: BytecodePatchContext)
private fun overrideAppOpenAdEligibility() {
    appOpenAdAllowedFingerprint.method.addInstructionsWithLabels(
        0,
        """
            invoke-static {p1}, $AD_CONTROL->appOpenAdOverride(Landroid/content/Context;)Ljava/lang/Boolean;
            move-result-object v0
            if-eqz v0, :original_app_open_ad_eligibility
            return-object v0
            :original_app_open_ad_eligibility
            nop
        """.trimIndent()
    )
}

context(_: BytecodePatchContext)
private fun overrideAudioAdContent() {
    val method = audioAdContentFingerprint.method
    val instructions = method.implementation!!.instructions.toList()
    val contextCallIndex = instructions.withIndex().single { (_, instruction) ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
            it.definingClass == "Landroid/content/Context;" &&
                it.name == "getApplicationContext" &&
                it.parameterTypes.isEmpty() &&
                it.returnType == "Landroid/content/Context;"
        } == true
    }.index
    val contextResult = instructions[contextCallIndex + 1]
    check(
        contextResult.opcode == Opcode.MOVE_RESULT_OBJECT &&
            (contextResult as OneRegisterInstruction).registerA == 9
    ) { "Expected AudioAdContentProvider application context in v9" }

    method.addInstructionsWithLabels(
        contextCallIndex + 2,
        """
            invoke-static {v9}, $AD_CONTROL->shouldBlockPlayerAds(Landroid/content/Context;)Z
            move-result v2
            if-eqz v2, :original_audio_ad_content
            sget-object v2, $NO_AUDIO_AD->a:$NO_AUDIO_AD
            return-object v2
            :original_audio_ad_content
            nop
        """.trimIndent()
    )
}

context(_: BytecodePatchContext)
private fun overrideDisplayAdLoad() {
    val method = displayAdLoadFingerprint.method
    val instructions = method.implementation!!.instructions.toList()
    val contextCallIndex = instructions.withIndex().single { (_, instruction) ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
            it.definingClass == "LNh/f;" &&
                it.name == "a" &&
                it.parameterTypes.isEmpty() &&
                it.returnType == "Landroid/content/Context;"
        } == true
    }.index
    val contextResult = instructions[contextCallIndex + 1]
    check(
        contextResult.opcode == Opcode.MOVE_RESULT_OBJECT &&
            (contextResult as OneRegisterInstruction).registerA == 3
    ) { "Expected display-ad application context in v3" }

    method.addInstructionsWithLabels(
        contextCallIndex + 2,
        """
            invoke-static {v3}, $AD_CONTROL->shouldBlockDisplayAds(Landroid/content/Context;)Z
            move-result v4
            if-eqz v4, :original_display_ad_load
            invoke-static {p0}, $DISPLAY_AD_CONTROLLER->j($DISPLAY_AD_CONTROLLER)Landroidx/lifecycle/E;
            move-result-object v4
            sget-object v5, $DISPLAY_AD_STATE->b:$DISPLAY_AD_STATE
            invoke-virtual {v4, v5}, Landroidx/lifecycle/E;->p(Ljava/lang/Object;)V
            return-void
            :original_display_ad_load
            nop
        """.trimIndent()
    )
}

context(_: BytecodePatchContext)
private fun suppressHomePremiumPromotion() {
    val method = homePremiumPromotionFingerprint.method
    val (index, instruction) = method.implementation!!.instructions.withIndex().single { (_, instruction) ->
        ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.let {
            it.definingClass ==
                "Ljp/nicovideo/nicobox/ui/premiummerit/PremiumMeritLeadInfoBottomSheetDialog;" &&
                it.name == "i1"
        } == true
    }
    val register = (instruction as OneRegisterInstruction).registerA

    method.replaceInstruction(
        index,
        "invoke-static {}, $AD_CONTROL->shouldHidePremiumPromotions()Z"
    )
    method.addInstructionsWithLabels(
        index + 1,
        """
            move-result v$register
            if-eqz v$register, :show_home_premium_promotion
            sget-object v$register, Lnl/L;->a:Lnl/L;
            return-object v$register
            :show_home_premium_promotion
            sget-object v$register, Ljp/nicovideo/nicobox/ui/premiummerit/PremiumMeritLeadInfoBottomSheetDialog;->i1:Ljp/nicovideo/nicobox/ui/premiummerit/PremiumMeritLeadInfoBottomSheetDialog${'$'}a;
        """.trimIndent(),
    )
}

context(_: BytecodePatchContext)
private fun suppressHighQualityPremiumSnackbar() {
    val method = highQualityPremiumSnackbarFingerprint.method
    val messageIndex = method.implementation!!.instructions.withIndex().single { (_, instruction) ->
        (instruction as? WideLiteralInstruction)?.wideLiteral ==
            HIGH_QUALITY_PREMIUM_MESSAGE.toLong()
    }.index

    method.replaceInstruction(
        messageIndex,
        "invoke-static {}, $AD_CONTROL->shouldHidePremiumPromotions()Z"
    )
    method.addInstructionsWithLabels(
        messageIndex + 1,
        """
            move-result v6
            if-eqz v6, :show_high_quality_premium_snackbar
            sget-object v6, Lnl/L;->a:Lnl/L;
            return-object v6
            :show_high_quality_premium_snackbar
            const v4, $HIGH_QUALITY_PREMIUM_MESSAGE
        """.trimIndent()
    )
}

context(_: BytecodePatchContext)
private fun suppressPremiumPromotion(fingerprint: Fingerprint) {
    val method = fingerprint.method
    val (index, instruction) = method.implementation!!.instructions.withIndex().single { (_, instruction) ->
        ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.let {
            it.definingClass == PREMIUM_DIALOG && it.name == "h1"
        } == true
    }
    val register = (instruction as OneRegisterInstruction).registerA
    val suppressedReturn = if (method.returnType == "V") {
        "return-void"
    } else {
        """
            sget-object v$register, Lnl/L;->a:Lnl/L;
            return-object v$register
        """.trimIndent()
    }

    method.replaceInstruction(
        index,
        "invoke-static {}, $AD_CONTROL->shouldHidePremiumPromotions()Z"
    )
    method.addInstructionsWithLabels(
        index + 1,
        """
            move-result v$register
            if-eqz v$register, :show_premium_promotion
            $suppressedReturn
            :show_premium_promotion
            sget-object v$register, $PREMIUM_DIALOG->h1:$PREMIUM_DIALOG_FACTORY
        """.trimIndent()
    )
}
