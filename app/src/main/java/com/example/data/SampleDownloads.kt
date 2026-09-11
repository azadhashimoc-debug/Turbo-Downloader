package com.example.data

import com.example.data.model.DownloadCategory

data class SampleDownloadItem(
    val title: String,
    val description: String,
    val sizeText: String,
    val url: String,
    val fileName: String,
    val category: DownloadCategory
)

object SampleDownloads {
    val items = listOf(
        SampleDownloadItem(
            title = "Test PDF Sənədi",
            description = "W3C standart nümunə PDF sənədi",
            sizeText = "~150 KB",
            url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
            fileName = "sample_w3c_document.pdf",
            category = DownloadCategory.DOCUMENT
        ),
        SampleDownloadItem(
            title = "Yüksək Keyfiyyətli Şəkil (HD)",
            description = "Unsplash təbiət fotosu",
            sizeText = "~2.5 MB",
            url = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=2560&auto=format&fit=crop",
            fileName = "yosemite_landscape_hd.jpg",
            category = DownloadCategory.IMAGE
        ),
        SampleDownloadItem(
            title = "Nümunə MP3 Audio",
            description = "Klassik test audio treki",
            sizeText = "~1.2 MB",
            url = "https://actions.google.com/sounds/v1/ambiences/rain_heavy.ogg",
            fileName = "rain_ambient_audio.ogg",
            category = DownloadCategory.AUDIO
        ),
        SampleDownloadItem(
            title = "Nümunə MP4 Video",
            description = "Big Buck Bunny qısa video treyleri",
            sizeText = "~5.3 MB",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            fileName = "sample_video_clip.mp4",
            category = DownloadCategory.VIDEO
        ),
        SampleDownloadItem(
            title = "ZIP Arxivi (Nümunə)",
            description = "GitHub açıq mənbəli zip arxivi",
            sizeText = "~3.8 MB",
            url = "https://github.com/git/git/archive/refs/tags/v2.40.0.zip",
            fileName = "git_v2.40.0_source.zip",
            category = DownloadCategory.ARCHIVE
        )
    )
}
