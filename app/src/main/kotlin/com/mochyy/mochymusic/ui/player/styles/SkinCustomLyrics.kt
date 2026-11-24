// a module player's own lyrics: one moving focus places and poses every line, the sung one sweeps word by word, and the module's shaders can paint or bend them (docs/wiki, lyrics your way)

package com.mochyy.mochymusic.ui.player.styles

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.PoseProp
import com.mochyy.mochymusic.modules.skin.SkinAlign
import com.mochyy.mochymusic.modules.skin.SkinLyricStyle
import com.mochyy.mochymusic.modules.skin.SkinProgram
import com.mochyy.mochymusic.modules.skin.SkinShaderSource
import com.mochyy.mochymusic.ui.component.agslRenderEffect
import com.mochyy.mochymusic.ui.component.asAgslPaintShader
import com.mochyy.mochymusic.ui.component.setAgslColorUniform
import com.mochyy.mochymusic.ui.component.setAgslUniform
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun SkinCustomLyrics(lines: List<LyricsEntry>, style: SkinLyricStyle, context: SkinContext, modifier: Modifier) {
    val clock = rememberLyricClock(lines, context.lyricsPosition)
    // the list hears the sung line, never the position
    val current by remember(clock) { derivedStateOf { clock.line().coerceIn(-1, lines.size) } }
    val focus = remember(lines) { Animatable(current.toFloat()) }
    LaunchedEffect(current, lines) {
        val t = style.transition
        val target = current.toFloat()
        when {
            context.preview || t.durationMs == 0 -> focus.snapTo(target)
            t.spring -> focus.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 240f))
            else -> focus.animateTo(target, tween(t.durationMs, easing = t.easing))
        }
    }
    // the sung line's entrance, in ms since it started; it rests past its end until the next line
    val enter = style.enter
    val entered = remember(lines) { Animatable(Float.MAX_VALUE) }
    if (enter != null) {
        LaunchedEffect(current, lines) {
            if (context.preview) return@LaunchedEffect
            val total = (enter.delayMs + enter.durationMs).toFloat()
            entered.snapTo(0f)
            entered.animateTo(total, tween(total.toInt().coerceAtLeast(1), easing = LinearEasing))
        }
    }
    // read while drawing; kept the same so a line change doesn't hand every row a new lambda and recompose it
    val focusAt = remember(focus) { { focus.value } }
    val enteredAt = remember(entered) { { entered.value } }
    val values = context.values
    val factor = style.scale.factor(LocalConfiguration.current.screenWidthDp.toFloat(), values)
    val fill = style.fill?.let { lyricProgram(context, it) }
    val effect = style.effect?.let { lyricProgram(context, it) }
    val textStyle = rememberLyricText(style, factor, fill)
    val base = context.color(style.color)
    val sung = context.color(style.sung)
    val align = style.align ?: context.layout.align

    val bent = if (effect != null && style.effect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Modifier.lyricEffect(effect, style.effect, context) else Modifier
    BoxWithConstraints(modifier.then(bent)) {
        val top = maxHeight * style.anchor
        val state = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
        LaunchedEffect(current, lines) {
            val target = current.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            if (context.preview) state.scrollToItem(target) else state.animateScrollToItem(target)
        }
        LazyColumn(
            state = state,
            userScrollEnabled = false,
            contentPadding = PaddingValues(top = top, bottom = (maxHeight - top).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.spacedBy(style.gap * factor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }, contentType = { _, _ -> 0 }) { index, line ->
                LyricRow(index, line, style, clock, focusAt, enteredAt, textStyle, base, sung, align, fill, context)
            }
        }
    }
}

/** a line's words and pose: past, sung or coming, read from the moving focus while drawing so a line change never recomposes the rows */
@Composable
private fun LyricRow(
    index: Int,
    line: LyricsEntry,
    style: SkinLyricStyle,
    clock: LyricClock,
    focus: () -> Float,
    entered: () -> Float,
    textStyle: TextStyle,
    base: Color,
    sung: Color,
    align: SkinAlign,
    fill: SkinProgram?,
    context: SkinContext,
) {
    // -1 sung already, 0 being sung, 1 still to come; only the rows crossing over recompose
    val place by remember(clock, index) { derivedStateOf { (index - clock.line()).coerceIn(-1, 1) } }
    val layout = remember { LayoutHolder() }
    val words = remember(line) { wordRanges(line) }
    val wordSegments = remember(line, words) { style3WordSegments(line.text, words) }
    val glowPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val pose = remember { PoseSum() }
    val origin = when (align) {
        SkinAlign.Start -> TransformOrigin(0f, 0.5f)
        SkinAlign.Center -> TransformOrigin(0.5f, 0.5f)
        SkinAlign.End -> TransformOrigin(1f, 0.5f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                poseFor(pose, index - focus(), style)
                val enter = style.enter
                if (enter != null && place == 0) {
                    val elapsed = entered()
                    if (elapsed < enter.delayMs + enter.durationMs) {
                        for (track in enter.frames.tracks) pose.add(track.prop, track.valueAt(enter.position(elapsed), FastOutSlowInEasing), 1f)
                    }
                }
                translationX = pose.x.dp.toPx()
                translationY = pose.y.dp.toPx()
                scaleX = pose.scaleX
                scaleY = pose.scaleY
                rotationZ = pose.rotate
                rotationX = pose.rotateX
                rotationY = pose.rotateY
                if (pose.rotateX != 0f || pose.rotateY != 0f) cameraDistance = 14f * density
                alpha = pose.alpha.coerceIn(0f, 1f)
                transformOrigin = origin
                renderEffect = if (alpha > 0.01f) blockBlur(pose.blur.dp.toPx()) else null
            },
    ) {
        Text(
            text = line.text,
            style = textStyle,
            // a shader paints every row itself; without one the sung rows are lit and the rest wait
            color = if (fill != null) Color.Unspecified else if (place < 0 || (place == 0 && !style.sweep)) sung else base,
            maxLines = style.lines,
            overflow = TextOverflow.Ellipsis,
            textAlign = align.textAlign,
            onTextLayout = { layout.result = it },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    if (fill != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setRowUniforms(fill, style.fill, context, index - focus(), if (place < 0) 1f else if (place > 0) 0f else clock.lineProgress())
                    }
                    if (style.wordMotion && fill == null && place == 0) {
                        val result = layout.result
                        if (result != null) {
                            drawStyle3Words(result, line.text, wordSegments, words.isNotEmpty(), clock, base, sung, textStyle, glowPaint)
                            return@drawWithContent
                        }
                    }
                    drawContent()
                    // only the sung row reads the clock, so the others never redraw for it
                    if (fill != null || !style.sweep || place != 0) return@drawWithContent
                    val result = layout.result ?: return@drawWithContent
                    sweep(result, sungChars(line, words, clock), sung)
                },
        )
        if (style.translation) LyricTranslation(line, textStyle, base)
    }
}

// the sung colour over the words sung so far, one line of the wrapped text at a time
private fun androidx.compose.ui.graphics.drawscope.DrawScope.sweep(result: TextLayoutResult, chars: Float, sung: Color) {
    if (chars <= 0f) return
    for (l in 0 until result.lineCount) {
        val start = result.getLineStart(l)
        val end = result.getLineEnd(l, visibleEnd = true)
        if (chars <= start) break
        val left = result.getLineLeft(l)
        val right = result.getLineRight(l)
        val rtl = result.getParagraphDirection(start) == ResolvedTextDirection.Rtl
        val edge = if (chars >= end) (if (rtl) left else right) else charX(result, chars, end)
        val top = result.getLineTop(l)
        val bottom = result.getLineBottom(l)
        clipRect(left = if (rtl) edge else min(left, edge), top = top, right = if (rtl) right else edge, bottom = bottom) {
            drawText(result, color = sung)
        }
    }
}

// where a part-way character offset sits, between the edges of the whole characters around it
private fun charX(result: TextLayoutResult, chars: Float, end: Int): Float {
    val whole = floor(chars).toInt().coerceIn(0, end)
    val next = (whole + 1).coerceAtMost(end)
    val a = result.getHorizontalPosition(whole, usePrimaryDirection = true)
    val b = result.getHorizontalPosition(next, usePrimaryDirection = true)
    return a + (b - a) * (chars - whole)
}

/** how many characters of the line are sung: by its words' timing when it has them, else evenly over the line */
private fun sungChars(line: LyricsEntry, words: List<IntRange>, clock: LyricClock): Float {
    val word = clock.word()
    if (words.isEmpty() || word < 0) return line.text.length * clock.lineProgress()
    val range = words.getOrNull(word) ?: return line.text.length.toFloat()
    return range.first + (range.last + 1 - range.first) * clock.wordProgress()
}

// each timed word's characters in the line's text, found in order; one that can't be found takes the space after the last
private fun wordRanges(line: LyricsEntry): List<IntRange> {
    val words = line.words ?: return emptyList()
    var cursor = 0
    return words.map { word ->
        val text = word.text.trim()
        val at = if (text.isEmpty()) -1 else line.text.indexOf(text, cursor)
        val start = if (at >= 0) at else cursor
        val end = if (at >= 0) at + text.length else (cursor + text.length).coerceAtMost(line.text.length)
        cursor = end
        start until end
    }
}

// the pose a row takes [distance] lines from the sung one: past ones blend toward the past pose, coming ones toward the next pose
private fun poseFor(pose: PoseSum, distance: Float, style: SkinLyricStyle) {
    pose.reset()
    val away = min(abs(distance), 1f)
    val side = if (distance < 0f) style.past else style.next
    for (prop in PoseProp.entries) {
        val sung = style.current[prop] ?: prop.neutral
        val there = side[prop] ?: prop.neutral
        pose.add(prop, sung + (there - sung) * away, 1f)
    }
}

/** the row's text as it's measured: the module's size and weight, the player's font, and the fill shader as its brush */
@Composable
private fun rememberLyricText(style: SkinLyricStyle, factor: Float, fill: SkinProgram?): TextStyle {
    val inherited = LocalTextStyle.current
    return remember(inherited, style, factor, fill) {
        val sized = inherited.merge(TextStyle(fontSize = style.size * factor, fontWeight = style.weight, lineHeight = style.size * factor * 1.18f))
        // the shader brush is android 13+, and the program is only there on 13+
        if (fill != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) sized.merge(TextStyle(brush = ShaderBrush(fill.shader.asAgslPaintShader()))) else sized
    }
}

@Composable
private fun LyricTranslation(line: LyricsEntry, textStyle: TextStyle, color: Color) {
    val translation = line.translatedTextFlow.collectAsState().value?.takeIf { it.isNotBlank() } ?: return
    Text(
        text = translation,
        style = TextStyle(fontFamily = textStyle.fontFamily, fontWeight = textStyle.fontWeight, fontSize = textStyle.fontSize * 0.6f, lineHeight = textStyle.lineHeight * 0.6f),
        color = color.copy(alpha = color.alpha * 0.8f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

// a lyrics shader that compiled, only on android 13+
@Composable
private fun lyricProgram(context: SkinContext, source: SkinShaderSource): SkinProgram? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ActiveSkin.shader(context.skin.moduleId, source.file) else null

// a row's fill: mochy's uniforms, how far it is from the sung line and how far it's sung, then the module's own
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.setRowUniforms(program: SkinProgram, source: SkinShaderSource?, context: SkinContext, distance: Float, progress: Float) {
    val shader = program.shader
    val declared = program.declared
    commonUniforms(shader, declared, context, size.width, size.height, density)
    // not "distance", which is agsl's own function and would clash with the module's code
    if ("lineOffset" in declared) shader.setAgslUniform("lineOffset", distance)
    // the row's own progress, after the clock's so a past or coming row reads 1 or 0
    if ("lineProgress" in declared) shader.setAgslUniform("lineProgress", progress)
    source?.uniforms?.forEach { (name, value) -> if (name in declared) shader.setAgslUniform(name, value) }
}

// the whole block through the module's shader, the drawn lyrics as its content; rebuilt each frame it moves, since an effect keeps the uniforms it was made with
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.lyricEffect(program: SkinProgram, source: SkinShaderSource, context: SkinContext): Modifier = graphicsLayer {
    val shader = program.shader
    commonUniforms(shader, program.declared, context, size.width, size.height, density)
    source.uniforms.forEach { (name, value) -> if (name in program.declared) shader.setAgslUniform(name, value) }
    renderEffect = agslRenderEffect(shader, "content").asComposeRenderEffect()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun commonUniforms(shader: Any, declared: Set<String>, context: SkinContext, width: Float, height: Float, density: Float) {
    val pulse = context.pulse
    // read so a new frame of the clock redraws this
    pulse.frame
    if ("size" in declared) shader.setAgslUniform("size", width, height)
    if ("time" in declared) shader.setAgslUniform("time", pulse.time)
    if ("density" in declared) shader.setAgslUniform("density", density)
    if ("level" in declared) shader.setAgslUniform("level", pulse.level)
    if ("bass" in declared) shader.setAgslUniform("bass", pulse.bass)
    if ("mid" in declared) shader.setAgslUniform("mid", pulse.mid)
    if ("treble" in declared) shader.setAgslUniform("treble", pulse.treble)
    if ("progress" in declared) shader.setAgslUniform("progress", (context.position.positionMs().toFloat() / context.duration.coerceAtLeast(1L)).coerceIn(0f, 1f))
    if ("cover" in declared) shader.setAgslColorUniform("cover", context.cover.toArgb())
    if ("accent" in declared) shader.setAgslColorUniform("accent", context.accent.toArgb())
    if ("played" in declared) shader.setAgslColorUniform("played", context.tones.played.toArgb())
    if ("remaining" in declared) shader.setAgslColorUniform("remaining", context.tones.remaining.toArgb())
    setLyricUniforms(shader, declared, context)
}

/** the row's last measured text, kept outside state so measuring never recomposes it */
private class LayoutHolder {
    var result: TextLayoutResult? = null
}


private data class WordSegment(
    val range: IntRange,
    val wordIndex: Int,
    val startFrac: Float,
    val endFrac: Float,
)

private val NonSpaceRegex = Regex("""\S+""")

private fun style3WordSegments(text: String, timedWords: List<IntRange>): List<WordSegment> {
    if (text.isEmpty()) return emptyList()
    val raw: List<Pair<IntRange, Int>> = if (timedWords.isNotEmpty()) {
        buildList {
            var cursor = 0
            timedWords.forEachIndexed { idx, r ->
                val s = r.first.coerceIn(0, text.length)
                val e = (r.last + 1).coerceIn(s, text.length)
                if (s > cursor) {
                    for (m in NonSpaceRegex.findAll(text.substring(cursor, s))) {
                        add((cursor + m.range.first until cursor + m.range.last + 1) to idx)
                    }
                }
                if (e > s) {
                    add((s until e) to idx)
                    cursor = max(cursor, e)
                }
            }
            if (cursor < text.length) {
                val lastIdx = (timedWords.size - 1).coerceAtLeast(0)
                for (m in NonSpaceRegex.findAll(text.substring(cursor))) {
                    add((cursor + m.range.first until cursor + m.range.last + 1) to lastIdx)
                }
            }
        }
    } else {
        NonSpaceRegex.findAll(text).mapIndexed { idx, m -> (m.range.first until m.range.last + 1) to idx }.toList()
    }
    if (raw.isEmpty()) return emptyList()
    val totalChars = raw.sumOf { (r, _) -> (r.last - r.first + 1).coerceAtLeast(1) }.coerceAtLeast(1).toFloat()
    var acc = 0f
    return raw.map { (r, wIdx) ->
        val len = (r.last - r.first + 1).coerceAtLeast(1).toFloat()
        val sFrac = acc / totalChars
        acc += len
        val eFrac = acc / totalChars
        WordSegment(r, wIdx, sFrac, eFrac)
    }
}

private fun DrawScope.drawStyle3Words(
    result: TextLayoutResult,
    text: String,
    segments: List<WordSegment>,
    hasTimedWords: Boolean,
    clock: LyricClock,
    base: Color,
    sung: Color,
    textStyle: TextStyle,
    glowPaint: Paint,
) {
    if (segments.isEmpty() || result.lineCount <= 0) {
        drawText(result, color = sung)
        return
    }
    val maxVisible = result.getLineEnd(result.lineCount - 1, visibleEnd = true).coerceIn(0, text.length)
    if (maxVisible <= 0) return

    val activeWord = if (hasTimedWords) clock.word() else -1
    val wordProg = if (hasTimedWords && activeWord >= 0) clock.wordProgress().coerceIn(0f, 1f) else 0f
    val lineProg = clock.lineProgress().coerceIn(0f, 1f)
    val timedCursor = if (hasTimedWords && activeWord >= 0) activeWord + wordProg else -1f

    val fontSizePx = if (textStyle.fontSize.isSp) textStyle.fontSize.toPx() else 18.dp.toPx()
    val maxRisePx = 5.5.dp.toPx()
    val maxGlowRadiusPx = 11.dp.toPx()

    for (seg in segments) {
        val p: Float
        val settle: Float
        if (timedCursor >= 0f) {
            p = (timedCursor - seg.wordIndex).coerceIn(0f, 1f)
            settle = (timedCursor - (seg.wordIndex + 1f)).coerceIn(0f, 1f)
        } else {
            val span = (seg.endFrac - seg.startFrac).coerceAtLeast(0.02f)
            p = ((lineProg - seg.startFrac) / span).coerceIn(0f, 1f)
            settle = ((lineProg - seg.endFrac) / 0.14f).coerceIn(0f, 1f)
        }

        val easeOut = 1f - (1f - p) * (1f - p)
        val midPeak = if (p > 0f && p < 1f) sin(p * PI.toFloat()).coerceAtLeast(0f) else 0f
        val riseFactor = if (settle > 0f) {
            val damp = exp(-3.0f * settle) * (1f - settle)
            0.35f + 0.65f * damp
        } else {
            (easeOut * 0.65f + midPeak * 0.55f).coerceAtMost(1.1f)
        }
        val riseY = if (p > 0f) -maxRisePx * riseFactor else 0f

        val swell = when {
            p <= 0f -> 0f
            settle > 0f -> 0.09f * exp(-3.5f * settle) * cos(2.8f * settle * PI.toFloat()) * (1f - settle)
            else -> 0.085f * easeOut + 0.065f * midPeak
        }
        val wordScaleX = 1f + swell * 0.85f
        val wordScaleY = 1f + swell

        val glowStrength = when {
            p <= 0f -> 0f
            settle > 0f -> (1f - settle / 0.85f).coerceIn(0f, 1f) * 0.75f
            else -> (0.35f * easeOut + 0.65f * midPeak).coerceIn(0f, 1f)
        }

        var segPos = seg.range.first.coerceIn(0, maxVisible)
        val segEndMax = (seg.range.last + 1).coerceIn(segPos, maxVisible)
        while (segPos < segEndMax) {
            val lineIdx = result.getLineForOffset(segPos)
            val lineEnd = result.getLineEnd(lineIdx, visibleEnd = true).coerceIn(segPos, maxVisible)
            val chunkEnd = min(segEndMax, lineEnd)
            if (chunkEnd <= segPos) break

            var left = Float.POSITIVE_INFINITY
            var right = Float.NEGATIVE_INFINITY
            for (c in segPos until chunkEnd) {
                val box = result.getBoundingBox(c)
                if (box.left < left) left = box.left
                if (box.right > right) right = box.right
            }
            if (left.isFinite() && right.isFinite() && right > left) {
                val top = result.getLineTop(lineIdx)
                val bottom = result.getLineBottom(lineIdx)
                val w = (right - left).coerceAtLeast(1f)
                val h = (bottom - top).coerceAtLeast(1f)
                val layoutOffset = Offset(-left, -top)
                val rtl = result.getParagraphDirection(segPos) == ResolvedTextDirection.Rtl

                withTransform({
                    translate(left = left, top = top + riseY)
                    if (swell != 0f) {
                        scale(scaleX = wordScaleX, scaleY = wordScaleY, pivot = Offset(w / 2f, h * 0.82f))
                    }
                }) {
                    if (glowStrength > 0.02f) {
                        val glowRadius = (maxGlowRadiusPx * glowStrength).coerceAtLeast(1f)
                        val glowAlpha = (0.55f * glowStrength).coerceIn(0f, 0.55f)
                        val chunkText = text.substring(segPos, chunkEnd)
                        val baseline = result.getLineBaseline(lineIdx) - top
                        drawIntoCanvas { canvas ->
                            glowPaint.maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
                            glowPaint.color = sung.copy(alpha = glowAlpha).toArgb()
                            glowPaint.textSize = fontSizePx
                            canvas.nativeCanvas.drawText(chunkText, 0f, baseline, glowPaint)
                        }
                    }

                    clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                        if (p >= 1f) {
                            drawText(result, topLeft = layoutOffset, color = sung)
                        } else if (p <= 0f) {
                            drawText(result, topLeft = layoutOffset, color = base)
                        } else {
                            drawText(result, topLeft = layoutOffset, color = base)
                            val edgeW = (w * 0.36f).coerceAtLeast(2f)
                            if (!rtl) {
                                val fillX = w * p
                                val solidEnd = (fillX - edgeW).coerceAtLeast(0f)
                                if (solidEnd > 0f) {
                                    clipRect(left = 0f, top = 0f, right = solidEnd, bottom = h) {
                                        drawText(result, topLeft = layoutOffset, color = sung)
                                    }
                                }
                                val steps = 8
                                for (s in 0 until steps) {
                                    val s0 = solidEnd + (fillX - solidEnd) * (s.toFloat() / steps)
                                    val s1 = (solidEnd + (fillX - solidEnd) * ((s + 1f) / steps) + 0.5f).coerceAtMost(fillX)
                                    if (s1 > s0) {
                                        val stepAlpha = 1f - (s + 0.5f) / steps
                                        clipRect(left = s0, top = 0f, right = s1, bottom = h) {
                                            drawText(result, topLeft = layoutOffset, color = sung.copy(alpha = sung.alpha * stepAlpha))
                                        }
                                    }
                                }
                            } else {
                                val fillLeft = w * (1f - p)
                                val solidStart = (fillLeft + edgeW).coerceAtMost(w)
                                if (solidStart < w) {
                                    clipRect(left = solidStart, top = 0f, right = w, bottom = h) {
                                        drawText(result, topLeft = layoutOffset, color = sung)
                                    }
                                }
                                val steps = 8
                                for (s in 0 until steps) {
                                    val s1 = solidStart - (solidStart - fillLeft) * (s.toFloat() / steps)
                                    val s0 = (solidStart - (solidStart - fillLeft) * ((s + 1f) / steps) - 0.5f).coerceAtLeast(fillLeft)
                                    if (s1 > s0) {
                                        val stepAlpha = 1f - (s + 0.5f) / steps
                                        clipRect(left = s0, top = 0f, right = s1, bottom = h) {
                                            drawText(result, topLeft = layoutOffset, color = sung.copy(alpha = sung.alpha * stepAlpha))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            segPos = chunkEnd
        }
    }
}
