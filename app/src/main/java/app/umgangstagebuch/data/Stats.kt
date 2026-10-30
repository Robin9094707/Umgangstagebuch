package app.umgangstagebuch.data

data class VisitStats(
    val total: Int,
    val done: Int,
    val shortened: Int,
    val cancelledByFather: Int,
    val cancelledByMother: Int,
    val cancelledChildIll: Int,
    val cancelledOther: Int,
    val noShow: Int,
    val shortNotice: Int,
    val planned: Int,
) {
    val fatherMissed get() = cancelledByFather + noShow
    val cancelled get() = cancelledByFather + cancelledByMother + cancelledChildIll + cancelledOther

    /** Anteil wahrgenommener Umgänge gegenüber den vom Vater versäumten. */
    val reliability: Int?
        get() {
            val base = done + shortened + fatherMissed
            return if (base == 0) null else (done + shortened) * 100 / base
        }
}

fun List<Visit>.stats(from: Long = Long.MIN_VALUE, to: Long = Long.MAX_VALUE, now: Long = System.currentTimeMillis()): VisitStats {
    val list = filter { it.start in from..to }
    val cancelled = list.filter { it.status == VisitStatus.CANCELLED }
    return VisitStats(
        total = list.size,
        done = list.count { it.status == VisitStatus.DONE },
        shortened = list.count { it.status == VisitStatus.SHORTENED },
        cancelledByFather = cancelled.count { it.cancellation?.by == CancelledBy.FATHER },
        cancelledByMother = cancelled.count { it.cancellation?.by == CancelledBy.MOTHER },
        cancelledChildIll = cancelled.count { it.cancellation?.by == CancelledBy.CHILD_ILL },
        cancelledOther = cancelled.count { it.cancellation?.by in setOf(CancelledBy.MUTUAL, CancelledBy.OTHER, null) },
        noShow = list.count { it.status == VisitStatus.NO_SHOW },
        shortNotice = cancelled.count { v -> v.cancellation?.noticeHours(v.start)?.let { it < 24 } == true },
        planned = list.count { it.status == VisitStatus.PLANNED && it.end > now },
    )
}

/** Umgänge, deren Ende vorbei ist, die aber noch keinen Status/Bericht haben. */
fun AppData.visitsNeedingReport(now: Long = System.currentTimeMillis()): List<Visit> {
    val reported = entries.mapNotNull { it.visitId }.toSet()
    return visits.filter {
        it.end < now && it.end > now - 14L * 24 * 3600 * 1000 &&
            it.id !in reported &&
            it.status in setOf(VisitStatus.PLANNED, VisitStatus.DONE, VisitStatus.SHORTENED) &&
            it.type !in setOf(VisitType.PHONE, VisitType.VIDEO)
    }.sortedByDescending { it.end }
}

fun AppData.nextVisit(now: Long = System.currentTimeMillis()): Visit? =
    visits.filter { it.end > now && it.status == VisitStatus.PLANNED }.minByOrNull { it.start }

fun AppData.entriesFor(visitId: String) = entries.filter { it.visitId == visitId }
