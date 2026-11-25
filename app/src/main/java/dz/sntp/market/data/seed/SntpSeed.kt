package dz.sntp.market.data.seed

import dz.sntp.market.data.model.DaySchedule
import dz.sntp.market.data.model.Shop

/**
 * Opening data for SNTP (El Hamiz). Every shop below comes from published
 * business listings for the market, so the list is real on day one instead of
 * an empty screen waiting for users.
 *
 * `shopNumber` is deliberately left null. The codes that the listings publish
 * next to these shops (P6GC+85, P6GC+RHJ, …) are Google Plus Codes — short
 * coordinates — and not the numbering the market itself uses. Printing them as
 * "#P6GC+85" would hand a trader a number that no shop in the market answers
 * to, and two of the codes found in the listings (P6GC+R, P6GC+Q) are truncated
 * to the point that they do not resolve to a location at all. A real market
 * number has to come from the shop owner, so the field stays empty until one
 * does.
 *
 * The weekly schedule is deliberately marked as *usual*, never as official:
 * the market has no published timetable, which is the entire reason this app
 * exists. Any day that the sources disagree on is left open-ended with a note.
 */
object SntpSeed {

    const val MARKET_ID = "sntp-el-hamiz"
    const val MARKET_NAME_AR = "سوق سانتيبي SNTP"
    const val MARKET_NAME_FR = "Marché SNTP"

    /** Geometric centre of the SNTP block inside El Hamiz. */
    const val LAT = 36.724663
    const val LNG = 3.222052

    /** A vote from further than this cannot describe the market's state. */
    const val VOTE_RADIUS_M = 1200.0

    /** Beyond this the app still lets you read, but tells you that you are far. */
    const val FAR_RADIUS_M = 5000.0

    val week: List<DaySchedule> = listOf(
        DaySchedule(1, null, null, "معتاد الإغلاق — بعض المحلات تفتح صباحاً", "Fermeture habituelle — certains ouvrent le matin"),
        DaySchedule(2, "07:00", "13:00", null, null),
        DaySchedule(3, "06:00", "17:00", "أكثر الأيام ازدحاماً", "Jour le plus chargé"),
        DaySchedule(4, "07:00", "12:00", "التوقيت يتغيّر كثيراً", "Horaire très variable"),
        DaySchedule(5, null, null, "إغلاق شبه كامل", "Fermeture quasi totale"),
        DaySchedule(6, "06:00", "13:00", null, null),
        DaySchedule(7, "06:00", "13:00", null, null),
    )

    val shops: List<Shop> = listOf(
        Shop(
            id = "sntp-001",
            name = "محلات SNTP للملابس الرجالية",
            category = "ملابس رجالية",
            phone = "+213658980632",
            whatsapp = "+213658980632",
            isVerified = false,
            shopNumber = null,
        ),
        Shop(
            id = "sntp-002",
            name = "بيع ملابس بالجملة عند السعيد",
            category = "ملابس بالجملة",
            phone = null,
            whatsapp = null,
            isVerified = false,
            shopNumber = null,
        ),
        Shop(
            id = "sntp-003",
            name = "SK 13 SNTP",
            category = "ملابس",
            phone = "+213777766681",
            whatsapp = "+213777766681",
            isVerified = false,
            shopNumber = null,
        ),
        Shop(
            id = "sntp-004",
            name = "Market Dz KK",
            category = "ملابس",
            phone = "+213772644553",
            whatsapp = "+213772644553",
            isVerified = false,
            shopNumber = null,
        ),
        Shop(
            id = "sntp-005",
            name = "Tamim SNTP",
            category = "ملابس",
            phone = "+213550142552",
            whatsapp = "+213550142552",
            isVerified = false,
            shopNumber = null,
        ),
        Shop(
            id = "sntp-006",
            name = "Ab Mode SNTP",
            category = "ملابس رجالية",
            phone = null,
            whatsapp = null,
            isVerified = false,
            shopNumber = null,
        ),
        Shop(
            id = "sntp-007",
            name = "شلبي للتسوق — أقمصة وهدايا الحج والعمرة",
            category = "أقمصة وهدايا",
            phone = "+213552508621",
            whatsapp = "+213552508621",
            isVerified = false,
            shopNumber = null,
        ),
    )
}
