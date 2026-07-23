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
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.ilikeadofai.vocacolle.patches.shared.Constants.VOCACOLLE

private const val APP_OPEN_AD_PROVIDER = "Llg/c;"
private const val AD_CONTROL =
    "Lio/github/ilikeadofai/vocacolle/extension/ads/AdControl;"
private const val NO_AUDIO_AD = "Lcf/c\$c;"
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
    extendWith("extensions/extension.mpe")

    execute {
        initializeAdControl()
        overrideAppOpenAdEligibility()
        overrideAudioAdContent()
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
    audioAdContentFingerprint.method.addInstructionsWithLabels(
        0,
        """
            invoke-static {}, $AD_CONTROL->shouldBlockPlayerAds()Z
            move-result v0
            if-eqz v0, :original_audio_ad_content
            sget-object v0, $NO_AUDIO_AD->a:$NO_AUDIO_AD
            return-object v0
            :original_audio_ad_content
            nop
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
