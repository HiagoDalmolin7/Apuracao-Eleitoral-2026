package br.com.apuracaoeleitoral

/** Regras matemáticas compartilhadas entre o aplicativo e os testes JVM.
 *  Não confundir extrapolação proporcional com uma previsão estatística. */
internal object ElectionMath {
    /** Extrapolação de votos observados a partir da fração de seções contadas. */
    fun projectVotes(observed: Double, sectionsPercent: Double): Double {
        if (!observed.isFinite() || observed < 0.0 ||
            !sectionsPercent.isFinite() || sectionsPercent <= 0.0 || sectionsPercent > 100.0
        ) return 0.0
        return observed / (sectionsPercent / 100.0)
    }

    /**
     * Vitória matematicamente irreversível, não uma declaração oficial do TSE.
     * Usa exclusivamente o total NACIONAL da presidência ('BR'); nunca usa projeções.
     * A conta restante é um limite conservador de votos que poderiam ir ao adversário.
     */
    fun irreversibleWinner(
        location: String,
        votes22: Long,
        votes13: Long,
        sectionsDone: Long,
        sectionsTotal: Long,
        electorate: Long,
        turnout: Long,
        officialValidVotes: Long,
        totalBallots: Long
    ): Int? {
        if (location != "BR") return null
        if (sectionsTotal <= 0 || sectionsDone !in 1L..sectionsTotal) return null
        if (votes22 < 0 || votes13 < 0 || votes22 == votes13) return null
        if (electorate <= 0 || turnout < 0 || turnout > electorate) return null
        if (votes22 > turnout || votes13 > turnout - votes22) return null
        val totalVotes = votes22 + votes13 // comprovadamente <= turnout: sem overflow
        if (totalVotes == 0L) return null
        if (officialValidVotes > 0 && totalVotes > officialValidVotes) return null
        if (totalBallots > 0 && totalVotes > totalBallots) return null
        val margin = if (votes22 > votes13) votes22 - votes13 else votes13 - votes22
        val remainingUpperBound = electorate - turnout
        return if (sectionsDone == sectionsTotal || margin > remainingUpperBound) {
            if (votes22 > votes13) 22 else 13
        } else null
    }
}
