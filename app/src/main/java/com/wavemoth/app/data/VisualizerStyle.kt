package com.wavemoth.app.data

/** The shape of the seek control, chosen to suit each visualizer's design. */
enum class VisualizerProgress { Round, Straight }

/** The looks the Home visualizer can have. Picked in Settings. */
enum class VisualizerStyle(val label: String, val description: String, val progress: VisualizerProgress) {
    Classic("Classic", "A ring of bars around a dark core", VisualizerProgress.Round),
    SoundWave("Sound Wave", "Soft layers of light that rise and fall", VisualizerProgress.Round),
    RoseGold("Rose Gold", "Glowing rose columns on a lit panel", VisualizerProgress.Straight),
    LedMeter("LED Meter", "A wide green-to-red level meter", VisualizerProgress.Straight),
    Waveform("Waveform", "One bright line that follows the sound", VisualizerProgress.Straight),
    Prism("Prism", "Mirrored bars drifting through every hue", VisualizerProgress.Straight);

    companion object {
        fun fromName(name: String?): VisualizerStyle = entries.firstOrNull { it.name == name } ?: Classic
    }
}
