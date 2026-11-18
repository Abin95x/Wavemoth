package com.wavemoth.app.data

/** The looks the Home visualizer can have. Picked in Settings. */
enum class VisualizerStyle(val label: String, val description: String) {
    Classic("Classic", "A ring of bars around a dark core"),
    SoundWave("Sound Wave", "Soft layers of light that rise and fall");

    companion object {
        fun fromName(name: String?): VisualizerStyle = entries.firstOrNull { it.name == name } ?: Classic
    }
}
