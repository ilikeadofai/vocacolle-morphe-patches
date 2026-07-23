package io.github.ilikeadofai.vocacolle.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import io.github.ilikeadofai.vocacolle.patches.shared.Constants.VOCACOLLE

private val externalLinkDialogTitle = string("リンク先に遷移する")
private val externalLinkDialogOk = string("OK")
private val externalLinkDialogCancel = string("Cancel")
private val byteDanceLandingDownload = string("ダウンロード")
private val byteDanceVideoLandingDownload = string("ダウンロード")
private const val PROSEKA_DESCRIPTION =
    "「ロキ」「シャルル」「Tell Your World」「ハッピーシンセサイザ」などの名曲を3DMV付きで多数収録！SEGA×Colorful Paletteが贈る、誰でもかんたんに楽しめるリズムゲーム。"
private val prosekaDescription = string(PROSEKA_DESCRIPTION)
private val prosekaSubtitle = string("初音ミクも登場する新作リズムゲーム")
private val prosekaTitle = string("プロジェクトセカイ カラフルステージ！ feat. 初音ミク")

private object ExternalLinkDialogFingerprint : Fingerprint(
    definingClass = "Lyc/u\$c;",
    name = "shouldOverrideUrlLoading",
    returnType = "Z",
    parameters = listOf("Landroid/webkit/WebView;", "Ljava/lang/String;"),
    filters = listOf(externalLinkDialogTitle, externalLinkDialogOk, externalLinkDialogCancel)
)

private object ByteDanceLandingDownloadFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/single/TTLandingPageActivity;",
    name = "<init>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(byteDanceLandingDownload)
)

private object ByteDanceVideoLandingDownloadFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/sdk/openadsdk/activity/single/TTVideoLandingPageActivity;",
    name = "<init>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(byteDanceVideoLandingDownload)
)

private object ProsekaTopContentFingerprint : Fingerprint(
    definingClass = "Ldj/j;",
    name = "l",
    returnType = "Lnl/L;",
    parameters = listOf("Lq0/r;", "I"),
    filters = listOf(prosekaDescription, prosekaSubtitle, prosekaTitle)
)

@Suppress("unused")
val vocacolleHardcodedKoreanUiPatch = bytecodePatch(
    name = "Korean hardcoded UI",
    description = "Translates production Compose and third-party UI strings embedded directly in VocaColle bytecode.",
    default = false
) {
    compatibleWith(VOCACOLLE)

    execute {
        replaceStringLiteral(ExternalLinkDialogFingerprint, 0, "외부 링크로 이동")
        replaceStringLiteral(ExternalLinkDialogFingerprint, 1, "확인")
        replaceStringLiteral(ExternalLinkDialogFingerprint, 2, "취소")
        replaceStringLiteral(ByteDanceLandingDownloadFingerprint, 0, "다운로드")
        replaceStringLiteral(ByteDanceVideoLandingDownloadFingerprint, 0, "다운로드")
        replaceStringLiteral(
            ProsekaTopContentFingerprint,
            0,
            "‘로키’, ‘샤를’, ‘Tell Your World’, ‘해피 신시사이저’ 등의 명곡을 3D MV와 함께 다수 수록! " +
                "SEGA×Colorful Palette가 선사하는 누구나 쉽고 재미있게 즐길 수 있는 리듬 게임."
        )
        replaceStringLiteral(ProsekaTopContentFingerprint, 1, "하츠네 미쿠도 등장하는 신작 리듬 게임")
        replaceStringLiteral(
            ProsekaTopContentFingerprint,
            2,
            "프로젝트 세카이 컬러풀 스테이지! feat. 하츠네 미쿠"
        )
    }
}

context(_: BytecodePatchContext)
private fun replaceStringLiteral(fingerprint: Fingerprint, matchIndex: Int, replacement: String) {
    val match = fingerprint.instructionMatches[matchIndex]
    val register = fingerprint.method
        .getInstruction<OneRegisterInstruction>(match.index)
        .registerA
    fingerprint.method.replaceInstruction(
        match.index,
        "const-string v$register, \"$replacement\""
    )
}
