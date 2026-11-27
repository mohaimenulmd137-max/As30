package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAdjustment

@Composable
fun StudioGuideDialog(
    isBangla: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = AmberDocumentary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBangla) "ChitraFlow Studio · পূর্ণাঙ্গ নির্দেশিকা" else "ChitraFlow Studio · Complete Guide",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GuideStepCard(
                    icon = Icons.Default.Layers,
                    accent = CyanPlayhead,
                    title = if (isBangla) "১. ভিডিও, ছবি ও মাল্টি-লেয়ার কন্ট্রোল" else "1. Media & Multi-Layer Timeline",
                    body = if (isBangla) {
                        "• 'মিডিয়া' বাটন থেকে ভিডিও/ছবি নিন — মূল ভিডিওর সাউন্ড ও ফুল ডিউরেশন অটো যুক্ত হবে।\n• যেকোনো ক্লিপের ২ পাশে টেনে ছোট/বড় করুন এবং ক্লিপ ধরে ডানে-বামে বা উপরে-নিচে সাব-লেয়ারে (L1, L2...) সাজান।\n• 'কপি' দিলে পাশে অথবা 'নতুন লেয়ার' দিলে নিচের লেয়ারে কপি বসবে।"
                    } else {
                        "• Tap Media to import video/photos with full duration and audio.\n• Drag clip handles to trim cleanly, or move clips across sub-layers (L1, L2...) without overlaps."
                    }
                )
                GuideStepCard(
                    icon = Icons.Default.TextFields,
                    accent = AmberDocumentary,
                    title = if (isBangla) "২. ফর্মেটেড বাংলা টেক্সট ও কাস্টম ফন্ট" else "2. Formatted Text & Custom Fonts",
                    body = if (isBangla) {
                        "• '+ টেক্সট' দিয়ে টাইটেল বা 'সাবটাইটেল' থেকে ডকুমেন্টারি লোয়ার-থার্ড যোগ করুন।\n• নিচের টেক্সট প্যানেল থেকে ১৬+ হলিউড প্রিসেট, কালার গ্রেডিয়েন্ট, আউটলাইন স্ট্রোক, শ্যাডো ও বাংলা ফন্ট (.ttf ইমপোর্টসহ) সেট করুন।"
                    } else {
                        "• Tap +Text for titles or Subtitles for lower-thirds.\n• Pick 16+ formatted styles, gradient colors, stroke outlines, and custom .ttf fonts."
                    }
                )
                GuideStepCard(
                    icon = Icons.Default.AutoAwesome,
                    accent = VioletAdjustment,
                    title = if (isBangla) "৩. অ্যানিমেশন, কি-ফ্রেম ও VFX / ট্রানজিশন" else "3. Animation, Keyframes, VFX & Transitions",
                    body = if (isBangla) {
                        "• 'অ্যানিমেশন' ট্যাব থেকে ভাসমান এডিটর খুলুন — একসাথে In + Loop + Out অ্যানিমেশন, Duration (0.2s–15s), Direction ও Custom Keyframe সেট করা যায়।\n• ক্লিপের শুরু ও শেষের ট্রানজিশন, জ্যামিতিক বর্ডার ও হলিউড VFX (ফ্লেয়ার, গড রেজ, ফিল্ম গ্রেইন) আলাদাভাবে নিয়ন্ত্রণ করুন।"
                    } else {
                        "• Open Floating Animation Editor for simultaneous In + Loop + Out slots and Keyframes.\n• Adjust Start/End transitions, geometric borders, and cinematic VFX layers."
                    }
                )
                GuideStepCard(
                    icon = Icons.Default.BarChart,
                    accent = CyanPlayhead,
                    title = if (isBangla) "৪. ০ থেকে কাস্টম চার্ট, ৭ গ্রাফ ও ম্যাপ রাডার" else "4. 0-to-100 Custom Charts & Map Radar",
                    body = if (isBangla) {
                        "• মিডিয়া মেনু বা নিচের 'প্রো চার্ট ও ম্যাপ' থেকে চার্ট নিন (শুরুতে ০ বার থাকবে)। নিজের মতো যেকোনো সংখ্যা/শতাংশ লিখে বার যোগ করুন।\n• বার, রিং/পাই, ডোনাট, লাইন, রাডার ও ক্যাপসুল সহ ৭টি গ্রাফ স্টাইল যেকোনো সময় বদলাতে পারবেন।"
                    } else {
                        "• Start charts from 0 bars, enter custom labels/values up to 100 bars, and switch between 7 live graph styles (Bar, Pie, Donut, Line, Radar, Pillar, Capsule)."
                    }
                )
                GuideStepCard(
                    icon = Icons.Default.MusicNote,
                    accent = EmeraldAudio,
                    title = if (isBangla) "৫. অডিও মিক্সিং, মিউট/সোলো ও অটো-ডাকিং" else "5. Audio Mixer, Mute/Solo & Auto-Ducking",
                    body = if (isBangla) {
                        "• প্রতিটি ভিডিও, ভয়েসওভার ও মিউজিক ক্লিপের ভলিউম (০%–২০০%), Mute এবং Solo আলাদাভাবে নিয়ন্ত্রণ করুন।\n• ভয়েস রেকর্ড করলে ব্যাকগ্রাউন্ড মিউজিক অটো-ডাকিং হয়ে স্মুথ নেমে যায় এবং ৫২+ হাই-ফাই সাউন্ড ইফেক্ট (SFX) যুক্ত করা যায়।"
                    } else {
                        "• Control per-clip volume (0–200%), Mute, and Solo independently.\n• Auto-ducking lowers music cleanly beneath voiceovers, plus 52+ studio SFX."
                    }
                )
                GuideStepCard(
                    icon = Icons.Default.Bolt,
                    accent = EmeraldAudio,
                    title = if (isBangla) "৬. দ্রুত MP4 এক্সপোর্ট ও গ্যালারি সেভ" else "6. Turbo MP4 Export to Gallery",
                    body = if (isBangla) {
                        "• উপরের হলুদ 'এক্সপোর্ট' বাটনে চাপ দিন, দ্রুত রেন্ডারের জন্য 'দ্রুত টার্বো (Fast)' ও ৭২০p/১০৮০p বেছে নিয়ে 'রেন্ডার শুরু করুন' চাপুন।\n• রেন্ডার শেষে ভিডিও সরাসরি Movies/ChitraFlow Studio ফোল্ডারে সেভ হবে এবং 'দেখুন' বা 'শেয়ার' বাটন থেকেই প্লে করা যাবে।"
                    } else {
                        "• Tap Export at top-right, choose Fast Turbo + 720p/1080p, and tap Render Video.\n• Output saves directly to Movies/ChitraFlow Studio with instant Watch & Share buttons."
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                modifier = Modifier.testTag("close_guide_dialog_btn")
            ) {
                Text(if (isBangla) "বুঝেছি" else "Got It", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun GuideStepCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
