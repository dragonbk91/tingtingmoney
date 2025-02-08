
package money.tingting.data

data class PhraseType(
    val title: String,
    val phrases: List<String>,
    var isEnabled: Boolean = true
)

data class PhraseSettings(
    val openingPhrases: PhraseType = PhraseType("Mở hàng", listOf(
        "cảm ơn quý khách đã mở hàng",
        )),
    val busyPhrases: PhraseType = PhraseType("Khi đông khách", listOf(
        "cảm ơn quý khách đã kiên nhẫn chờ đợi",
        "cảm ơn nhiều nha nay đông khách quá quý khách thông cảm",
        )),
    val randomPhrases: PhraseType = PhraseType("Ngẫu nhiên", listOf(
        "cảm ơn quý khách",
        "hẹn gặp lại quý khách",
        "chúc bạn một ngày tốt lành",
        "chúc bạn ngày rực rỡ",
        "mong bạn có ngày bình yên như cơn gió"
    ))
)