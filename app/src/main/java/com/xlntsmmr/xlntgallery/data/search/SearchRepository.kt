package com.xlntsmmr.xlntgallery.data.search

import android.content.Context
import android.net.Uri
import com.xlntsmmr.xlntgallery.data.Photo
import com.xlntsmmr.xlntgallery.data.SettingsRepository
import com.xlntsmmr.xlntgallery.data.ai.AiModelType
import com.xlntsmmr.xlntgallery.data.ai.AiModels
import com.xlntsmmr.xlntgallery.data.ai.TextEmbedder
import com.xlntsmmr.xlntgallery.data.ai.VectorMath
import com.xlntsmmr.xlntgallery.data.db.GalleryDatabase
import com.xlntsmmr.xlntgallery.data.db.PhotoEntity
import com.xlntsmmr.xlntgallery.data.db.SearchTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 어떻게 찾았는지 (결과 카드의 뱃지)
enum class MatchType(val label: String) {
    FILE_NAME("파일명"),
    OCR("글자 인식"),
    AI("AI 추론")
}

data class SearchHit(
    val photo: Photo,
    val matches: Set<MatchType>,
    val ocrText: String? = null,
    val aiScore: Float? = null,
    val rank: Float = 0f
)

/**
 * 검색 = 다섯 가지 근거를 합쳐서 판단 (각자 잘하는 영역이 다름)
 *
 *  근거                 잘하는 것                              점수
 *  ───────────────────────────────────────────────────────────────────────
 *  파일명               파일 이름 정확히 일치                    +6
 *  OCR                  사진 속 글자 정확히 일치                 +5
 *  Gemini Nano 태그     사진을 "읽고" 종류 판단 (계약서 vs 성분표)   종류 +9 / 키워드 +7 / 요약 +4
 *  SigLIP 2 벡터        장면·사물 (주 모델)                      z-score × 0.6
 *  CLIP 벡터            장면·사물 (교차 확인)                    z-score × 0.4
 *
 *  이미지 AI(SigLIP 2·CLIP)만으로 결과에 넣는 조건 — "억지 결과" 방지:
 *   · 두 모델이 합의: 둘 다 z ≥ 2.0  (+ 합의 보너스 2점)
 *   · 한 모델만 분석된 사진: 그 모델 z ≥ 3.5
 *   · 사진 속에 검색어 글자가 있으면: z ≥ 1.5
 *   · 단, Gemini가 이미 보고 "다른 종류"로 판단한 사진은 두 모델 모두 z ≥ 3.0이어야 하고 3점 감점
 *  z-score = 그 검색어에 대해 "전체 사진 평균보다 표준편차 몇 배 높은지" (모델마다 점수 척도가 달라도 비교 가능)
 */
class SearchRepository(private val context: Context) {

    private val dao = GalleryDatabase.get(context).galleryDao()
    private val settings = SettingsRepository(context)
    private val lock = Mutex()

    // 모델별 검색 준비물 (텍스트 인코더·허브 보정 벡터·사진 벡터 색인)
    private val searchers = HashMap<AiModelType, ModelSearcher>()

    suspend fun search(query: String, types: Set<MatchType>): List<SearchHit> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val useAi = MatchType.AI in types

        // ── 근거 모으기 ──
        val fileHits = if (MatchType.FILE_NAME in types) dao.searchByName(q) else emptyList()
        val textHits = if (MatchType.OCR in types || useAi) {
            SearchTokenizer.matchQuery(q)?.let { dao.searchByText(it) }.orEmpty()
        } else emptyList()
        val textIds = textHits.map { it.photo.id }.toSet()

        val tagScores = if (useAi) tagSearch(q) else emptyMap()
        val siglip = if (useAi) vectorZScores(AiModelType.SIGLIP2, q) else null
        val clip = if (useAi) vectorZScores(AiModelType.CLIP, q) else null

        // ── 이미지 AI 후보 판정 ──
        val vectorCandidates = HashSet<Long>()
        siglip?.scores?.keys?.let { vectorCandidates += it }
        clip?.scores?.keys?.let { vectorCandidates += it }
        val tagged = if (vectorCandidates.isEmpty()) emptySet()
        else dao.taggedAmong(vectorCandidates.toList()).map { it.photoId }.toSet()

        val vectorScores = HashMap<Long, Float>()
        for (id in vectorCandidates) {
            val zs = siglip?.scores?.get(id)
            val zc = clip?.scores?.get(id)
            val hasS = siglip?.analyzed?.contains(id) == true
            val hasC = clip?.analyzed?.contains(id) == true
            val geminiDisagrees = id in tagged && id !in tagScores   // Gemini가 보고 "다른 종류"로 판단

            val agree = zs != null && zc != null && min(zs, zc) >= Z_AGREE
            val singleStrong = (hasS != hasC) && max(zs ?: 0f, zc ?: 0f) >= Z_SINGLE
            val textEvidence = id in textIds && max(zs ?: 0f, zc ?: 0f) >= Z_WITH_TEXT
            val passes = if (geminiDisagrees) {
                zs != null && zc != null && min(zs, zc) >= Z_OVERRIDE_GEMINI
            } else {
                agree || singleStrong || textEvidence
            }
            if (!passes) continue

            var score = (zs ?: 0f).coerceIn(0f, 10f) * W_SIGLIP + (zc ?: 0f).coerceIn(0f, 10f) * W_CLIP
            if (agree) score += AGREE_BONUS
            if (id in textIds && MatchType.OCR !in types) score += OCR_BOOST
            if (geminiDisagrees) score -= GEMINI_PENALTY
            vectorScores[id] = score
        }

        // ── 결과 합치기 ──
        val entities = HashMap<Long, PhotoEntity>()
        fileHits.forEach { entities[it.id] = it }
        textHits.forEach { entities[it.photo.id] = it.photo }
        val missing = (vectorScores.keys + tagScores.keys) - entities.keys
        if (missing.isNotEmpty()) dao.photosByIds(missing.toList()).forEach { entities[it.id] = it }

        val fileIds = fileHits.map { it.id }.toSet()
        val ocrTextById = textHits.associate { it.photo.id to it.text }
        val candidateIds = buildSet {
            addAll(fileIds)
            if (MatchType.OCR in types) addAll(textIds)
            addAll(vectorScores.keys)
            addAll(tagScores.keys)
        }

        candidateIds.mapNotNull { id ->
            val entity = entities[id] ?: return@mapNotNull null
            val matches = buildSet {
                if (id in fileIds) add(MatchType.FILE_NAME)
                if (MatchType.OCR in types && id in textIds) add(MatchType.OCR)
                if (id in vectorScores || id in tagScores) add(MatchType.AI)
            }
            var rank = 0f
            if (MatchType.FILE_NAME in matches) rank += 6f
            if (MatchType.OCR in matches) rank += 5f
            rank += tagScores[id] ?: 0f
            rank += vectorScores[id] ?: 0f
            SearchHit(entity.toPhoto(), matches, ocrTextById[id], vectorScores[id], rank)
        }.sortedWith(compareByDescending<SearchHit> { it.rank }.thenByDescending { it.photo.dateTaken })
    }

    /** Gemini Nano 태그 검색: 사진 ID → 점수. 검색어 전체 + 띄어쓴 단어별로 찾음 */
    private suspend fun tagSearch(query: String): Map<Long, Float> {
        val terms = (listOf(query) + query.split(' ').filter { it.length >= 2 }).distinct()
        val scores = HashMap<Long, Float>()
        for (term in terms) {
            for (match in dao.searchTags(term)) {
                var score = 0f
                val category = match.category
                if (category.isNotBlank() && (category.contains(term) || term.contains(category))) score += TAG_CATEGORY
                if (match.keywords.split(',').any { it.contains(term) || (it.length >= 2 && term.contains(it)) }) score += TAG_KEYWORD
                if (match.summary.contains(term)) score += TAG_SUMMARY
                if (score > 0f) scores[match.photoId] = maxOf(scores[match.photoId] ?: 0f, score)
            }
        }
        return scores
    }

    /** 한 모델의 결과: 기준(z ≥ 1.5)을 넘은 사진의 z-score + 이 모델로 분석된 사진 전체 */
    private class ModelResult(val scores: Map<Long, Float>, val analyzed: Set<Long>)

    private suspend fun vectorZScores(type: AiModelType, query: String): ModelResult? = lock.withLock {
        if (!AiModels.pack(context, type).isReady()) return null
        val searcher = try {
            searchers.getOrPut(type) { ModelSearcher(type, AiModels.textEmbedder(context, type, settings.accelerator.first())) }
        } catch (e: Throwable) {
            return null   // 메모리 부족 등으로 모델을 못 올리면 이 모델은 빼고 검색
        }
        val index = searcher.loadIndex() ?: return null

        // ③ 프롬프트 앙상블
        val queryVector = searcher.averageOf(AiModels.queryTemplates(type).map { searcher.encoder.encode(it.replace("{q}", query)) })
        // ① 허브 보정
        val raw = index.scores(queryVector)
        val baseline = index.baseline(searcher.neutral)
        val adjusted = FloatArray(raw.size) { raw[it] - baseline[it] }
        // ② z-score
        val mean = adjusted.average().toFloat()
        var variance = 0f
        for (s in adjusted) variance += (s - mean) * (s - mean)
        val std = sqrt(variance / adjusted.size).coerceAtLeast(1e-6f)

        val scores = HashMap<Long, Float>()
        for (i in adjusted.indices.sortedByDescending { adjusted[it] }) {
            val z = (adjusted[i] - mean) / std
            if (z < Z_WITH_TEXT || scores.size >= MAX_PER_MODEL) break
            scores[index.ids[i]] = z
        }
        // 분석된 사진이 너무 적으면 z-score가 믿을 수 없음 (적은 표본 안에서 "그나마 비슷한" 사진이 튀어 보임)
        if (index.ids.size < MIN_ANALYZED) return ModelResult(emptyMap(), index.ids.toSet())
        return ModelResult(scores, index.ids.toSet())
    }

    private inner class ModelSearcher(val type: AiModelType, val encoder: TextEmbedder) {
        val neutral: FloatArray = averageOf(AiModels.neutralPrompts.map { encoder.encode(it) })
        private var index: VectorIndex? = null

        suspend fun loadIndex(): VectorIndex? {
            val modelId = AiModels.modelId(type)
            val count = dao.embeddingCount(modelId)
            if (count == 0) return null
            index?.takeIf { it.ids.size == count }?.let { return it }
            val rows = dao.allEmbeddings(modelId)
            val dim = VectorMath.fromBytes(rows.first().vector).size
            val matrix = FloatArray(rows.size * dim)
            rows.forEachIndexed { i, row -> VectorMath.fromBytes(row.vector).copyInto(matrix, i * dim) }
            return VectorIndex(rows.map { it.photoId }.toLongArray(), matrix, dim).also { index = it }
        }

        fun averageOf(vectors: List<FloatArray>): FloatArray {
            val sum = FloatArray(vectors.first().size)
            for (v in vectors) for (i in v.indices) sum[i] += v[i]
            return VectorMath.normalize(sum)
        }
    }

    fun close() {
        searchers.values.forEach { it.encoder.close() }
        searchers.clear()
    }

    private fun PhotoEntity.toPhoto() = Photo(id = id, uri = Uri.parse(uri), name = displayName, dateTaken = dateTaken)

    private class VectorIndex(val ids: LongArray, private val matrix: FloatArray, private val dim: Int) {
        private var baselineCache: FloatArray? = null

        fun scores(query: FloatArray): FloatArray = FloatArray(ids.size) { row ->
            var sum = 0f
            val offset = row * dim
            for (d in 0 until dim) sum += matrix[offset + d] * query[d]
            sum
        }

        fun baseline(neutral: FloatArray): FloatArray =
            baselineCache ?: scores(neutral).also { baselineCache = it }
    }

    private companion object {
        const val MAX_PER_MODEL = 300
        const val MIN_ANALYZED = 300          // 이보다 적게 분석된 모델은 아직 검색에 안 씀
        const val Z_AGREE = 2.0f
        const val Z_SINGLE = 3.5f
        const val Z_WITH_TEXT = 1.5f
        const val Z_OVERRIDE_GEMINI = 3.0f
        const val W_SIGLIP = 0.6f
        const val W_CLIP = 0.4f
        const val AGREE_BONUS = 2f
        const val OCR_BOOST = 3f
        const val GEMINI_PENALTY = 3f
        const val TAG_CATEGORY = 9f
        const val TAG_KEYWORD = 7f
        const val TAG_SUMMARY = 4f
    }
}
