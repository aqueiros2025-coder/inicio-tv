package com.example.tvlauncher

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

// VideoView que ocupa sempre todo o espaço que lhe dão (o vídeo é esticado até aos limites).
// Para um resultado sem deformação, usa vídeos em 16:9.
class FillVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : VideoView(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(0, widthMeasureSpec),
            getDefaultSize(0, heightMeasureSpec)
        )
    }
}
