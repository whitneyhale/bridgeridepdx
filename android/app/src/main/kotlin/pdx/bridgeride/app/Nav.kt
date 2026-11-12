package pdx.bridgeride.app

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pdx.bridgeride.core.BridgeEnd
import pdx.bridgeride.core.BridgeEnds
import pdx.bridgeride.core.BridgeInfo
import pdx.bridgeride.core.BuiltInRide
import pdx.bridgeride.core.Fix
import pdx.bridgeride.core.FollowState
import pdx.bridgeride.core.RideFollower
import pdx.bridgeride.core.RidePlan

/** The data shipped in the app: bridge ends, bridge facts and the built-in rides. Read once. */
class AppData(val ends: List<BridgeEnd>, val infos: List<BridgeInfo>, val rides: List<RidePlan>) {
    val infoById: Map<String, BridgeInfo> = infos.associateBy { it.id }
    /** Bridges a bike may cross: the passport's total. */
    val bikeable: List<BridgeInfo> = infos.filter { it.access != pdx.bridgeride.core.BikeAccess.NO }

    companion object {
        @Volatile private var cached: AppData? = null

        fun get(context: Context): AppData = cached ?: synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }

        private fun lines(context: Context, name: String) = context.assets.open(name).bufferedReader().use { it.readLines() }

        private fun load(context: Context): AppData {
            val ends = BridgeEnds.parse(lines(context, "bridge_ends.csv"))
            val infos = BridgeInfo.parse(lines(context, "bridge_info.tsv"))
            val rides = context.assets.list("rides").orEmpty().filter { it.endsWith(".csv") }.sorted().mapNotNull { file ->
                BuiltInRide.parse(file.removeSuffix(".csv"), lines(context, "rides/$file"))?.let { RidePlan.of(it, ends, infos) }
            }
            return AppData(ends, infos, rides)
        }
    }
}

/**
 * The ride in progress: the plan being followed (or none, for a plain recording) and where the
 * rider is on it. The recording service feeds fixes in; the ride screen reads the state.
 * Everything here runs on the main thread.
 */
object Nav {
    private var follower = RideFollower(null)
    private val _plan = MutableStateFlow<RidePlan?>(null)
    private val _state = MutableStateFlow(FollowState())
    val plan: StateFlow<RidePlan?> = _plan.asStateFlow()
    val state: StateFlow<FollowState> = _state.asStateFlow()

    /** A steady clock: unlike the wall clock it cannot jump, so it cannot fake a GPS gap. */
    private fun now() = SystemClock.elapsedRealtime()

    fun begin(plan: RidePlan?) {
        follower = RideFollower(plan?.line)
        _plan.value = plan
        _state.value = follower.state
    }

    fun onFix(f: Fix) { _state.value = follower.onFix(f, now()) }

    fun tick() { _state.value = follower.tick(now()) }

    fun onCompass(deg: Double) { _state.value = follower.onCompass(deg, now()) }
}
