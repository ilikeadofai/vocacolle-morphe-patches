package io.github.ilikeadofai.vocacolle.extension.settings;

import java.util.Locale;

/** Localized strings for the resource-free extension settings UI. */
public final class MorpheSettingsStrings {
    private static final MorpheSettingsStrings JAPANESE = new MorpheSettingsStrings(
            "Morphe 設定",
            "一般",
            "表示言語",
            new String[]{"システム設定", "日本語", "英語", "韓国語"},
            "Morphe ランタイム機能",
            "今後追加される実行時機能の共通スイッチです。ビルド時に適用された翻訳パッチには影響しません。",
            "メタデータと翻訳",
            "VocaDBの曲名を表示",
            "現在のNicoNicoメディアIDのみをVocaDBへ送信し、完全一致した曲名メタデータを表示します。初期設定はオフです。",
            "広告",
            "起動時広告をブロック",
            "コールドスタート時とアプリへの復帰時に表示される広告をブロックします。",
            "バナー・インフィード広告をブロック",
            "ホーム、検索結果、ライブラリ、プレイリストに表示される広告をブロックします。",
            "再生中の音声広告をブロック",
            "曲間に挿入されるネットワーク広告とローカル音声広告をブロックします。",
            "Premiumポップアップを非表示",
            "ホーム起動時または制限機能の利用時に表示されるPremium案内を非表示にします。登録画面は引き続き利用できます。",
            "ストレージ",
            "キャッシュデータ",
            "使用量: %s / 上限: %s",
            "キャッシュを削除",
            "キャッシュ済みの応答だけを削除します。設定は保持されます。",
            "キャッシュを削除しました",
            "キャッシュを削除できませんでした",
            "診断情報",
            "VocaColle 7.40.0 / 設定スキーマ 1"
    );
    private static final MorpheSettingsStrings ENGLISH = new MorpheSettingsStrings(
            "Morphe Settings",
            "General",
            "Display language",
            new String[]{"System default", "Japanese", "English", "Korean"},
            "Morphe Runtime Features",
            "Common switch for runtime features added in the future. "
                    + "It does not affect translation patches applied at build time.",
            "Metadata & translation",
            "Show VocaDB titles",
            "Sends only the current NicoNico media ID to VocaDB and displays exact-match title metadata. Disabled by default.",
            "Ads",
            "Block app-open ads",
            "Blocks ads shown at cold start and when returning to the app.",
            "Block banner and in-feed ads",
            "Blocks ads shown in Home, search results, Library, and playlists.",
            "Block in-player audio ads",
            "Blocks network and local audio ads inserted between tracks.",
            "Hide Premium pop-ups",
            "Hides Premium dialogs shown on Home startup or after attempting restricted features. Registration remains available.",
            "Storage",
            "Cached data",
            "Used: %s / limit: %s",
            "Clear cache",
            "Deletes cached responses only. Settings are preserved.",
            "Cache cleared",
            "Could not clear cache",
            "Diagnostic Information",
            "VocaColle 7.40.0 / Settings schema 1"
    );
    private static final MorpheSettingsStrings KOREAN = new MorpheSettingsStrings(
            "Morphe 설정",
            "일반",
            "표시 언어",
            new String[]{"시스템 기본값", "일본어", "영어", "한국어"},
            "Morphe 런타임 기능",
            "향후 추가되는 런타임 기능을 위한 공통 스위치입니다. "
                    + "빌드 시 적용된 번역 패치에는 영향을 주지 않습니다.",
            "메타데이터 및 번역",
            "VocaDB 곡 제목 표시",
            "현재 NicoNico 미디어 ID만 VocaDB로 전송하고 정확히 일치한 곡 제목 메타데이터를 표시합니다. 기본값은 꺼짐입니다.",
            "광고",
            "앱 시작·복귀 광고 차단",
            "앱을 처음 실행하거나 다시 돌아올 때 표시되는 광고를 차단합니다.",
            "배너·피드 광고 차단",
            "홈, 검색 결과, 라이브러리, 재생목록에 표시되는 광고를 차단합니다.",
            "재생 중 음성 광고 차단",
            "곡 사이에 삽입되는 네트워크 광고와 로컬 음성 광고를 차단합니다.",
            "Premium 팝업 숨기기",
            "홈 진입 또는 제한 기능 사용 시 표시되는 Premium 안내 팝업을 숨깁니다. 가입 화면은 계속 이용할 수 있습니다.",
            "저장 공간",
            "캐시 데이터",
            "사용량: %s / 한도: %s",
            "캐시 비우기",
            "캐시된 응답만 삭제하며 설정은 유지됩니다.",
            "캐시를 비웠습니다",
            "캐시를 비우지 못했습니다",
            "진단 정보",
            "VocaColle 7.40.0 / 설정 스키마 1"
    );

    public final String settingsTitle;
    public final String generalCategory;
    public final String displayLanguageTitle;
    public final String[] displayLanguageEntries;
    public final String[] displayLanguageEntryValues = {"system", "ja", "en", "ko"};
    public final String runtimeFeaturesTitle;
    public final String runtimeFeaturesSummary;
    public final String metadataCategory;
    public final String vocaDbMetadataTitle;
    public final String vocaDbMetadataSummary;
    public final String adsCategory;
    public final String appOpenAdBlockingTitle;
    public final String appOpenAdBlockingSummary;
    public final String displayAdBlockingTitle;
    public final String displayAdBlockingSummary;
    public final String playerAdBlockingTitle;
    public final String playerAdBlockingSummary;
    public final String premiumPromotionHidingTitle;
    public final String premiumPromotionHidingSummary;
    public final String storageCategory;
    public final String cacheTitle;
    public final String cacheSummaryFormat;
    public final String clearCacheTitle;
    public final String clearCacheSummary;
    public final String cacheClearedMessage;
    public final String cacheClearFailedMessage;
    public final String diagnosticsTitle;
    public final String diagnosticsSummary;

    private MorpheSettingsStrings(
            String settingsTitle,
            String generalCategory,
            String displayLanguageTitle,
            String[] displayLanguageEntries,
            String runtimeFeaturesTitle,
            String runtimeFeaturesSummary,
            String metadataCategory,
            String vocaDbMetadataTitle,
            String vocaDbMetadataSummary,
            String adsCategory,
            String appOpenAdBlockingTitle,
            String appOpenAdBlockingSummary,
            String displayAdBlockingTitle,
            String displayAdBlockingSummary,
            String playerAdBlockingTitle,
            String playerAdBlockingSummary,
            String premiumPromotionHidingTitle,
            String premiumPromotionHidingSummary,
            String storageCategory,
            String cacheTitle,
            String cacheSummaryFormat,
            String clearCacheTitle,
            String clearCacheSummary,
            String cacheClearedMessage,
            String cacheClearFailedMessage,
            String diagnosticsTitle,
            String diagnosticsSummary
    ) {
        this.settingsTitle = settingsTitle;
        this.generalCategory = generalCategory;
        this.displayLanguageTitle = displayLanguageTitle;
        this.displayLanguageEntries = displayLanguageEntries;
        this.runtimeFeaturesTitle = runtimeFeaturesTitle;
        this.runtimeFeaturesSummary = runtimeFeaturesSummary;
        this.metadataCategory = metadataCategory;
        this.vocaDbMetadataTitle = vocaDbMetadataTitle;
        this.vocaDbMetadataSummary = vocaDbMetadataSummary;
        this.adsCategory = adsCategory;
        this.appOpenAdBlockingTitle = appOpenAdBlockingTitle;
        this.appOpenAdBlockingSummary = appOpenAdBlockingSummary;
        this.displayAdBlockingTitle = displayAdBlockingTitle;
        this.displayAdBlockingSummary = displayAdBlockingSummary;
        this.playerAdBlockingTitle = playerAdBlockingTitle;
        this.playerAdBlockingSummary = playerAdBlockingSummary;
        this.premiumPromotionHidingTitle = premiumPromotionHidingTitle;
        this.premiumPromotionHidingSummary = premiumPromotionHidingSummary;
        this.storageCategory = storageCategory;
        this.cacheTitle = cacheTitle;
        this.cacheSummaryFormat = cacheSummaryFormat;
        this.clearCacheTitle = clearCacheTitle;
        this.clearCacheSummary = clearCacheSummary;
        this.cacheClearedMessage = cacheClearedMessage;
        this.cacheClearFailedMessage = cacheClearFailedMessage;
        this.diagnosticsTitle = diagnosticsTitle;
        this.diagnosticsSummary = diagnosticsSummary;
    }

    public static MorpheSettingsStrings forCurrentLocale() {
        return forLanguage(Locale.getDefault().getLanguage());
    }

    public static MorpheSettingsStrings resolve(
            DisplayLanguage selectedLanguage,
            String systemLanguage
    ) {
        DisplayLanguage safeLanguage = selectedLanguage == null
                ? DisplayLanguage.SYSTEM
                : selectedLanguage;
        switch (safeLanguage) {
            case KOREAN:
                return KOREAN;
            case ENGLISH:
                return ENGLISH;
            case JAPANESE:
                return JAPANESE;
            case SYSTEM:
            default:
                return forLanguage(systemLanguage);
        }
    }

    public static MorpheSettingsStrings forLanguage(String language) {
        if (language == null) {
            return JAPANESE;
        }
        String normalized = language.toLowerCase(Locale.ROOT);
        if ("ko".equals(normalized)) {
            return KOREAN;
        }
        if ("en".equals(normalized)) {
            return ENGLISH;
        }
        return JAPANESE;
    }
}
