package com.musicapp.ui.screens.player.lyric

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FoliaNativeGlRenderer : GLSurfaceView.Renderer {

    data class GlLine(val text: String, val startTimeMs: Long, val endTimeMs: Long, var fillProgress: Float = 0f)

    data class Track(
        var line: GlLine, val offset: Int,
        var y: Float = 0f, var vy: Float = 0f,
        var scale: Float = 0.8f, var vs: Float = 0f,
        var alpha: Float = 0f, var fromAlpha: Float = 0f,
        var elapsed: Float = 0f, var leaving: Boolean = false,
        var textureId: Int = 0, var texWidth: Int = 0, var texHeight: Int = 0
    )

    // ---- Shaders (从 Folia 原版移植) ----
    private val VERT = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        uniform float uScale;
        uniform vec2 uTranslate;
        varying vec2 vTexCoord;
        void main() {
            vec2 pos = aPosition * uScale + uTranslate;
            gl_Position = vec4(pos, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }"""

    private val FRAG = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec4 uBase;
        uniform vec4 uWord;
        uniform float uFront;
        uniform float uSoftness;
        uniform float uEdgeFade;
        uniform float uIsActive;
        void main() {
            vec4 tex = texture2D(uTexture, vTexCoord);
            float glyphAlpha = tex.a;
            if (glyphAlpha < 0.001) discard;
            float yFade = 1.0;
            if (uEdgeFade > 0.0) {
                yFade = smoothstep(0.0, 0.5 - uEdgeFade * 0.5, vTexCoord.y)
                      * smoothstep(1.0, 0.5 + uEdgeFade * 0.5, vTexCoord.y);
            }
            float sweepMask = 1.0;
            if (uIsActive > 0.5) {
                float softPx = uSoftness / max(uFront, 0.001);
                sweepMask = smoothstep(uFront - softPx, uFront, vTexCoord.x);
                sweepMask = clamp(sweepMask, 0.0, 1.0);
            }
            vec3 finalRGB = mix(uBase.rgb, uWord.rgb, sweepMask);
            float finalAlpha = glyphAlpha * yFade * mix(uBase.a, uWord.a, sweepMask);
            gl_FragColor = vec4(finalRGB, finalAlpha);
        }"""

    private var prog = 0
    private var aPos = 0; private var aTex = 0
    private var uTex = 0; private var uScale = 0; private var uTrans = 0
    private var uBase = 0; private var uWord = 0; private var uFront = 0
    private var uSoft = 0; private var uEdge = 0; private var uAct = 0

    private var widthPx = 1; private var heightPx = 1
    private val tracks = mutableListOf<Track>()
    private var lines: List<GlLine> = emptyList()
    private var posMs = 0L; private var lastMs = 0L; private var lastNs = 0L
    private var fontPx = 56f

    private fun ibuf(n: Int): java.nio.IntBuffer =
        ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asIntBuffer()

    private lateinit var vertBuf: FloatBuffer; private lateinit var idxBuf: ShortBuffer
    private var vbo = 0; private var ibo = 0

    fun setLines(lines: List<GlLine>) {
        this.lines = lines
        tracks.forEach { if (it.textureId != 0) { val b = ibuf(1); b.put(it.textureId); b.position(0); GLES20.glDeleteTextures(1, b) } }
        tracks.clear()
    }
    fun setPosition(ms: Long) { posMs = ms }
    fun setFontPx(px: Float) { fontPx = px }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        prog = glProg(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(prog, "aPosition")
        aTex = GLES20.glGetAttribLocation(prog, "aTexCoord")
        uTex = GLES20.glGetUniformLocation(prog, "uTexture")
        uScale = GLES20.glGetUniformLocation(prog, "uScale")
        uTrans = GLES20.glGetUniformLocation(prog, "uTranslate")
        uBase = GLES20.glGetUniformLocation(prog, "uBase")
        uWord = GLES20.glGetUniformLocation(prog, "uWord")
        uFront = GLES20.glGetUniformLocation(prog, "uFront")
        uSoft = GLES20.glGetUniformLocation(prog, "uSoftness")
        uEdge = GLES20.glGetUniformLocation(prog, "uEdgeFade")
        uAct = GLES20.glGetUniformLocation(prog, "uIsActive")

        val v = floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)
        val i = shortArrayOf(0,1,2, 1,3,2)
        vertBuf = ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }
        idxBuf = ByteBuffer.allocateDirect(i.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(i); position(0) }

        val ids = ibuf(2); GLES20.glGenBuffers(2, ids); vbo = ids.get(0); ibo = ids.get(1)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertBuf.remaining()*4, vertBuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idxBuf.remaining()*2, idxBuf, GLES20.GL_STATIC_DRAW)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width; heightPx = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (lines.isEmpty()) return

        val ns = System.nanoTime()
        val dt = if (lastNs > 0) min(0.05f, (ns - lastNs) / 1_000_000_000f) else 1/60f
        lastNs = ns

        if (posMs < lastMs || abs(posMs - lastMs) > 750) {
            tracks.forEach { if (it.textureId != 0) { val b = ibuf(1); b.put(it.textureId); b.position(0); GLES20.glDeleteTextures(1, b) } }
            tracks.clear()
        }
        lastMs = posMs

        var ai = -1
        for (i in lines.indices) {
            if (posMs >= lines[i].startTimeMs && posMs <= lines[i].endTimeMs) { ai = i; break }
            if (posMs < lines[i].startTimeMs) break; ai = i
        }
        if (ai < 0) ai = 0

        tracks.forEach { it.leaving = true; it.fromAlpha = it.alpha; it.elapsed = 0f }

        for (i in max(0, ai - 2)..min(lines.size - 1, ai + 2)) {
            val ln = lines[i]; val off = i - ai
            var t = tracks.find { it.line.startTimeMs == ln.startTimeMs && it.offset == off }
            if (t == null) {
                t = Track(ln, off, y = heightPx*0.46f + if (off>=0) 40 else -40, scale = 0.7f)
                val tex = mkTex(ln.text)
                t.textureId = tex.first; t.texWidth = tex.second; t.texHeight = tex.third
                tracks.add(t)
            } else { t.line = ln; t.leaving = false }
            if (off == 0) {
                val d = max(100L, ln.endTimeMs - ln.startTimeMs)
                ln.fillProgress = ((posMs - ln.startTimeMs).coerceIn(0L, d).toFloat() / d)
            }
        }

        val anchorY = heightPx * 0.46f; val lineH = fontPx * 1.18f; val gap = 28f
        val rm = mutableListOf<Track>()

        tracks.forEach { t ->
            val (sa, sda) = mkTone(t.offset, t.line.endTimeMs < posMs)
            val ty = when {
                t.offset < 0 -> anchorY - lineH*sa - gap
                t.offset > 0 -> anchorY + lineH + gap
                else -> anchorY
            } + if (t.leaving) (if (t.offset < 0) -38 else 38) else 0

            val (sy, sVy) = stepS(t.y, t.vy, ty, dt, 142f, 28f, 0.82f)
            val (ss, sVs) = stepS(t.scale, t.vs, sa, dt, 150f, 30f, 0.78f)
            t.y = sy; t.vy = sVy; t.scale = ss; t.vs = sVs; t.elapsed += dt

            val ta = if (t.leaving) 0f else sda
            val fad = min(1f, t.elapsed / 0.28f)
            t.alpha = t.fromAlpha + (ta - t.fromAlpha) * lattice(fad)

            val isAct = t.offset == 0 && !t.leaving
            drawT(t, isAct, 0) // glowFar
            drawT(t, isAct, 1) // glowNear
            drawT(t, isAct, 2) // main

            if (t.leaving && fad >= 1f) rm.add(t)
        }
        rm.forEach { if (it.textureId != 0) { val b = ibuf(1); b.put(it.textureId); b.position(0); GLES20.glDeleteTextures(1, b) }; tracks.remove(it) }
    }

    private fun drawT(t: Track, isAct: Boolean, pass: Int) {
        GLES20.glUseProgram(prog)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, 0)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, 8)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.textureId)
        GLES20.glUniform1i(uTex, 0)

        val (b, w) = when (pass) {
            0 -> floatArrayOf(0.25f,0.35f,1f,0f) to floatArrayOf(0.25f,0.35f,1f,t.alpha*0.3f)
            1 -> floatArrayOf(0.7f,0.75f,0.85f,0f) to floatArrayOf(1f,1f,1f,t.alpha*0.5f)
            else -> floatArrayOf(0.35f,0.38f,0.42f,t.alpha*0.55f) to floatArrayOf(1f,1f,1f,t.alpha)
        }
        GLES20.glUniform4fv(uBase, 1, b, 0); GLES20.glUniform4fv(uWord, 1, w, 0)

        GLES20.glUniform1f(uFront, if (isAct) t.line.fillProgress else 1f)
        GLES20.glUniform1f(uSoft, 12f / max(t.texWidth.toFloat(), 1f))
        GLES20.glUniform1f(uEdge, if (isAct) 0f else 0.3f)
        GLES20.glUniform1f(uAct, if (isAct) 1f else 0f)

        val worldH = fontPx * 1.18f / (heightPx * 0.5f)
        GLES20.glUniform1f(uScale, t.scale * worldH)
        GLES20.glUniform2f(uTrans, 0f, (t.y / heightPx) * 2f - 1f)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun mkTex(text: String): Triple<Int, Int, Int> {
        val p = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = fontPx; typeface = Typeface.DEFAULT }
        val r = android.graphics.Rect(); p.getTextBounds(text, 0, text.length, r)
        val pad = 8; val w = r.width()+pad*2; val h = r.height()+pad*2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.TRANSPARENT)
        Canvas(bmp).drawText(text, pad.toFloat(), pad + r.height().toFloat() + r.top, p)
        val ids = ibuf(1); GLES20.glGenTextures(1, ids); val tex = ids.get(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        return Triple(ids[0], w, h)
    }

    private fun glProg(v: String, f: String): Int {
        val vs = glC(GLES20.GL_VERTEX_SHADER, v); val fs = glC(GLES20.GL_FRAGMENT_SHADER, f)
        val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p); return p
    }
    private fun glC(t: Int, s: String): Int {
        val sh = GLES20.glCreateShader(t); GLES20.glShaderSource(sh, s); GLES20.glCompileShader(sh)
        return sh
    }

    private fun stepS(v: Float, vel: Float, tgt: Float, dt: Float, k: Float, c: Float, m: Float): Pair<Float, Float> {
        val steps = max(1, (dt * 120).toInt()); val sdt = dt / steps
        var vv = vel; var v2 = v
        repeat(steps) {
            vv += ((tgt - v2) * k - vv * c) / m * sdt
            v2 += vv * sdt
        }
        return Pair(v2, vv)
    }

    private fun mkTone(off: Int, passed: Boolean): Pair<Float, Float> {
        val d = abs(off)
        return if (passed || off < 0) when (d) {
            0 -> 1f to 1f; 1 -> 0.82f to 0.52f; 2 -> 0.68f to 0.24f; else -> 0.56f to 0.08f
        } else when (d) {
            0 -> 1f to 1f; 1 -> 0.82f to 0.58f; 2 -> 0.70f to 0.28f; else -> 0.60f to 0.10f
        }
    }

    private fun lattice(t: Float): Float { val tt = max(0f, min(1f, t)); return 1f - (1f-tt)*(1f-tt)*(1f-tt) }
}
