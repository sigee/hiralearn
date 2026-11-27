package hu.sigee.hiralearn.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.core.KanaGroup
import hu.sigee.hiralearn.core.Lesson
import hu.sigee.hiralearn.core.Scheduler
import hu.sigee.hiralearn.core.Word

/** Localised lesson name; core keeps only English titles, the app translates by lesson index. */
@Composable
fun lessonTitle(lesson: Lesson): String = stringArrayResource(R.array.lesson_titles).getOrElse(lesson.index) { lesson.title }

@Composable
fun groupTitle(group: KanaGroup): String = stringArrayResource(R.array.group_titles).getOrElse(group.ordinal) { group.title }

/** "5 min", "3 h 20 min", "4 days", localised. Used after "in …" / "… múlva". */
@Composable
fun formatDuration(ms: Long): String {
    val minutes = ((ms.coerceAtLeast(0) + Scheduler.MINUTE - 1) / Scheduler.MINUTE).toInt()
    return when {
        minutes < 60 -> stringResource(R.string.duration_min, minutes)
        minutes < 24 * 60 -> stringResource(R.string.duration_h_min, minutes / 60, minutes % 60)
        else -> (minutes / (24 * 60)).let { pluralStringResource(R.plurals.duration_days, it, it) }
    }
}

/** Localised meaning of a word; core keeps only the word, the app translates by word id. */
@Composable
fun wordMeaning(word: Word): String = wordMeanings[word.id]?.let { stringResource(it) }.orEmpty()

/** [wordMeaning] outside composition (e.g. to search the glossary). */
fun wordMeaning(word: Word, resources: Resources): String = wordMeanings[word.id]?.let { resources.getString(it) }.orEmpty()

/** Word id → its meaning. A new word in core needs an entry here and a `word_<id>` string in every language. */
private val wordMeanings: Map<String, Int> = mapOf(
    "ai" to R.string.word_ai,
    "ie" to R.string.word_ie,
    "ue" to R.string.word_ue,
    "ao" to R.string.word_ao,
    "iie" to R.string.word_iie,
    "aka" to R.string.word_aka,
    "aki" to R.string.word_aki,
    "eki" to R.string.word_eki,
    "kao" to R.string.word_kao,
    "koe" to R.string.word_koe,
    "ike" to R.string.word_ike,
    "koko" to R.string.word_koko,
    "kuuki" to R.string.word_kuuki,
    "kikai" to R.string.word_kikai,
    "oka" to R.string.word_oka,
    "sushi" to R.string.word_sushi,
    "asa" to R.string.word_asa,
    "isu" to R.string.word_isu,
    "ushi" to R.string.word_ushi,
    "shio" to R.string.word_shio,
    "kasa" to R.string.word_kasa,
    "ashi" to R.string.word_ashi,
    "sekai" to R.string.word_sekai,
    "okashi" to R.string.word_okashi,
    "suika" to R.string.word_suika,
    "soko" to R.string.word_soko,
    "tako" to R.string.word_tako,
    "kutsu" to R.string.word_kutsu,
    "tsukue" to R.string.word_tsukue,
    "kuchi" to R.string.word_kuchi,
    "tsuki" to R.string.word_tsuki,
    "soto" to R.string.word_soto,
    "tokei" to R.string.word_tokei,
    "taiko" to R.string.word_taiko,
    "chikatetsu" to R.string.word_chikatetsu,
    "ichi" to R.string.word_ichi,
    "kita" to R.string.word_kita,
    "neko" to R.string.word_neko,
    "inu" to R.string.word_inu,
    "sakana" to R.string.word_sakana,
    "natsu" to R.string.word_natsu,
    "niku" to R.string.word_niku,
    "nani" to R.string.word_nani,
    "naka" to R.string.word_naka,
    "okane" to R.string.word_okane,
    "anata" to R.string.word_anata,
    "kinoko" to R.string.word_kinoko,
    "kuni" to R.string.word_kuni,
    "hana" to R.string.word_hana,
    "hito" to R.string.word_hito,
    "fune" to R.string.word_fune,
    "hoshi" to R.string.word_hoshi,
    "hashi" to R.string.word_hashi,
    "hikouki" to R.string.word_hikouki,
    "fuku" to R.string.word_fuku,
    "hako" to R.string.word_hako,
    "hone" to R.string.word_hone,
    "heso" to R.string.word_heso,
    "mimi" to R.string.word_mimi,
    "mushi" to R.string.word_mushi,
    "momo" to R.string.word_momo,
    "atama" to R.string.word_atama,
    "umi" to R.string.word_umi,
    "kuma" to R.string.word_kuma,
    "namae" to R.string.word_namae,
    "ame" to R.string.word_ame,
    "mise" to R.string.word_mise,
    "musume" to R.string.word_musume,
    "kami" to R.string.word_kami,
    "yama" to R.string.word_yama,
    "yuki" to R.string.word_yuki,
    "fuyu" to R.string.word_fuyu,
    "heya" to R.string.word_heya,
    "yasai" to R.string.word_yasai,
    "yume" to R.string.word_yume,
    "yasumi" to R.string.word_yasumi,
    "sakura" to R.string.word_sakura,
    "kuruma" to R.string.word_kuruma,
    "tori" to R.string.word_tori,
    "yoru" to R.string.word_yoru,
    "haru" to R.string.word_haru,
    "sora" to R.string.word_sora,
    "iro" to R.string.word_iro,
    "kusuri" to R.string.word_kusuri,
    "furo" to R.string.word_furo,
    "risu" to R.string.word_risu,
    "rekishi" to R.string.word_rekishi,
    "watashi" to R.string.word_watashi,
    "kawa" to R.string.word_kawa,
    "niwa" to R.string.word_niwa,
    "wani" to R.string.word_wani,
    "hon" to R.string.word_hon,
    "mikan" to R.string.word_mikan,
    "tenki" to R.string.word_tenki,
    "sensei" to R.string.word_sensei,
    "nihon" to R.string.word_nihon,
    "honya" to R.string.word_honya,
    "kagi" to R.string.word_kagi,
    "gohan" to R.string.word_gohan,
    "megane" to R.string.word_megane,
    "tamago" to R.string.word_tamago,
    "ringo" to R.string.word_ringo,
    "eigo" to R.string.word_eigo,
    "hiragana" to R.string.word_hiragana,
    "usagi" to R.string.word_usagi,
    "ginkou" to R.string.word_ginkou,
    "kagami" to R.string.word_kagami,
    "kaze" to R.string.word_kaze,
    "mizu" to R.string.word_mizu,
    "chizu" to R.string.word_chizu,
    "zou" to R.string.word_zou,
    "jikan" to R.string.word_jikan,
    "shizuka" to R.string.word_shizuka,
    "naze" to R.string.word_naze,
    "mado" to R.string.word_mado,
    "karada" to R.string.word_karada,
    "denwa" to R.string.word_denwa,
    "tomodachi" to R.string.word_tomodachi,
    "doko" to R.string.word_doko,
    "daigaku" to R.string.word_daigaku,
    "hanadi" to R.string.word_hanadi,
    "mikaduki" to R.string.word_mikaduki,
    "kaban" to R.string.word_kaban,
    "yubi" to R.string.word_yubi,
    "buta" to R.string.word_buta,
    "kabe" to R.string.word_kabe,
    "boushi" to R.string.word_boushi,
    "ebi" to R.string.word_ebi,
    "soba" to R.string.word_soba,
    "tabemono" to R.string.word_tabemono,
    "tenpura" to R.string.word_tenpura,
    "sanpo" to R.string.word_sanpo,
    "enpitsu" to R.string.word_enpitsu,
    "kanpai" to R.string.word_kanpai,
    "shinpai" to R.string.word_shinpai,
    "pikapika" to R.string.word_pikapika,
    "kyou" to R.string.word_kyou,
    "shashin" to R.string.word_shashin,
    "isha" to R.string.word_isha,
    "kyaku" to R.string.word_kyaku,
    "shukudai" to R.string.word_shukudai,
    "densha" to R.string.word_densha,
    "kaisha" to R.string.word_kaisha,
    "shouyu" to R.string.word_shouyu,
    "toshokan" to R.string.word_toshokan,
    "kyuuri" to R.string.word_kyuuri,
    "ocha" to R.string.word_ocha,
    "chawan" to R.string.word_chawan,
    "chuui" to R.string.word_chuui,
    "choucho" to R.string.word_choucho,
    "chuugoku" to R.string.word_chuugoku,
    "konnyaku" to R.string.word_konnyaku,
    "hyaku" to R.string.word_hyaku,
    "myouji" to R.string.word_myouji,
    "hyoutan" to R.string.word_hyoutan,
    "myaku" to R.string.word_myaku,
    "ryouri" to R.string.word_ryouri,
    "ryuu" to R.string.word_ryuu,
    "gyuunyuu" to R.string.word_gyuunyuu,
    "ryokou" to R.string.word_ryokou,
    "gyouza" to R.string.word_gyouza,
    "kingyo" to R.string.word_kingyo,
    "juu" to R.string.word_juu,
    "jouzu" to R.string.word_jouzu,
    "byouin" to R.string.word_byouin,
    "jisho" to R.string.word_jisho,
    "sanbyaku" to R.string.word_sanbyaku,
    "pyonpyon" to R.string.word_pyonpyon,
)
