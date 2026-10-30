package com.xlntsmmr.xlntgallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xlntsmmr.xlntgallery.data.ai.AiModelType
import com.xlntsmmr.xlntgallery.data.ai.HardwareAccelerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 앱 전체에서 하나만 있어야 하므로 파일 최상단에 선언 (같은 파일을 두 곳에서 열면 오류)
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 설정 저장소 (DataStore). 앱을 껐다 켜도 유지됨.
 * SharedPreferences의 후속: 비동기(코루틴/Flow) 기반이라 메인 스레드를 막지 않음.
 */
class SettingsRepository(private val context: Context) {

    private val ocrEngineKey = stringPreferencesKey("ocr_engine")

    // 값이 바뀌면 자동으로 새 값을 흘려보내는 Flow
    val ocrEngine: Flow<OcrEngineType> = context.settingsDataStore.data.map { prefs ->
        prefs[ocrEngineKey]
            ?.let { saved -> OcrEngineType.entries.firstOrNull { it.name == saved } }
            ?: OcrEngineType.ML_KIT
    }

    suspend fun setOcrEngine(type: OcrEngineType) {
        context.settingsDataStore.edit { prefs -> prefs[ocrEngineKey] = type.name }
    }

    private val acceleratorKey = stringPreferencesKey("hardware_accelerator")

    // AI 모델을 돌릴 장치 (자동 / NPU / GPU / CPU)
    val accelerator: Flow<HardwareAccelerator> = context.settingsDataStore.data.map { prefs ->
        prefs[acceleratorKey]
            ?.let { saved -> HardwareAccelerator.entries.firstOrNull { it.name == saved } }
            ?: HardwareAccelerator.AUTO
    }

    private val mobileDataKey = androidx.datastore.preferences.core.booleanPreferencesKey("models_on_mobile_data")
    private val chargingOnlyKey = androidx.datastore.preferences.core.booleanPreferencesKey("analyze_charging_only")
    private val autoNewPhotosKey = androidx.datastore.preferences.core.booleanPreferencesKey("auto_new_photos")

    // AI 모델(약 600MB)을 모바일 데이터로도 받을지. 기본: Wi-Fi에서만 (데이터 요금 보호)
    val modelsOnMobileData: Flow<Boolean> = context.settingsDataStore.data.map { it[mobileDataKey] ?: false }
    suspend fun setModelsOnMobileData(enabled: Boolean) {
        context.settingsDataStore.edit { it[mobileDataKey] = enabled }
    }

    // 앱을 닫았을 때는 충전 중에만 분석할지. 기본: 아니오 (배터리가 부족하지 않으면 분석)
    val analyzeChargingOnly: Flow<Boolean> = context.settingsDataStore.data.map { it[chargingOnlyKey] ?: false }
    suspend fun setAnalyzeChargingOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[chargingOnlyKey] = enabled }
    }

    // 새 사진이 생기면 자동 분석. 기본: 켜짐
    val autoAnalyzeNewPhotos: Flow<Boolean> = context.settingsDataStore.data.map { it[autoNewPhotosKey] ?: true }
    suspend fun setAutoAnalyzeNewPhotos(enabled: Boolean) {
        context.settingsDataStore.edit { it[autoNewPhotosKey] = enabled }
    }

    private val geminiTaggingKey = androidx.datastore.preferences.core.booleanPreferencesKey("gemini_tagging")

    // Gemini Nano 사진 이해 (기본: 켜짐)
    val geminiTagging: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[geminiTaggingKey] ?: true
    }

    suspend fun setGeminiTagging(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[geminiTaggingKey] = enabled }
    }

    private val aiModelKey = stringPreferencesKey("ai_model")

    // AI 검색 모델 (CLIP / SigLIP 2)
    val aiModel: Flow<AiModelType> = context.settingsDataStore.data.map { prefs ->
        prefs[aiModelKey]
            ?.let { saved -> AiModelType.entries.firstOrNull { it.name == saved } }
            ?: AiModelType.CLIP
    }

    suspend fun setAiModel(type: AiModelType) {
        context.settingsDataStore.edit { prefs -> prefs[aiModelKey] = type.name }
    }

    suspend fun setAccelerator(accelerator: HardwareAccelerator) {
        context.settingsDataStore.edit { prefs -> prefs[acceleratorKey] = accelerator.name }
    }
}
