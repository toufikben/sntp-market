package dz.sntp.market

import android.app.Application
import dz.sntp.market.data.local.Prefs
import dz.sntp.market.data.repo.MarketRepository
import dz.sntp.market.data.repo.RepoConfig
import dz.sntp.market.data.repo.VoteRepository

class SntpApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var marketRepository: MarketRepository
        private set
    lateinit var voteRepository: VoteRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        marketRepository = MarketRepository(
            context = this,
            config = RepoConfig(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                anonKey = BuildConfig.SUPABASE_ANON_KEY,
                marketSlug = BuildConfig.MARKET_SLUG,
            ),
            prefs = prefs,
        )
        voteRepository = VoteRepository(this, marketRepository, prefs)
    }

    companion object {
        lateinit var instance: SntpApp
            private set
    }
}
