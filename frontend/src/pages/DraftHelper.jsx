import { useState, useEffect, useCallback, useRef } from 'react'
import { draftApi, playersApi, rankingsApi, leaguesApi } from '../api/axiosClient'

const POSITIONS = ['PG', 'SG', 'SF', 'PF', 'C']
const STORAGE_KEY = 'draftHelperSession'
const POLL_INTERVAL_MS = 5000
const DEFAULT_LINEUP_SLOTS = ['PG', 'SG', 'SF', 'PF', 'C', 'UTIL', 'UTIL', 'UTIL']

const getNum = (value, fallback) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

const clamp = (value, lo, hi) => Math.max(lo, Math.min(hi, value))

// Mirrors the backend SLEEPER-board score: shrink small-sample production and
// gate/anchor it by Sleeper ADP so anomalies (great short line, poor ADP) don't
// leapfrog established, highly-drafted producers.
const rankingScore = (player) => {
  const fpts = getNum(player?.fantasyPtsAvg, 0)
  const prodNorm = clamp(fpts / 50, 0, 1.1)

  const games = getNum(player?.gamesPlayed, 0)
  const sampleConfidence = clamp(games / 25, 0, 1)
  const shrunkProd = prodNorm * sampleConfidence

  const adp = getNum(player?.searchRank, 0)
  const hasAdp = adp > 0
  const adpAnchor = hasAdp ? clamp((300 - adp) / 300, 0, 1) : 0
  const adpConfidence = hasAdp ? clamp((350 - adp) / 350, 0.15, 1) : 0.3

  const corroboratedProd = shrunkProd * adpConfidence
  return corroboratedProd * 0.68 + adpAnchor * 0.32
}

const cleanDynastyName = (name) => {
  if (!name) return ''
  let cleaned = String(name).replace(/\s+/g, ' ').trim()
  cleaned = cleaned.replace(/^(?:\d+(?:\.\d+)?\s+)+/, '').trim()
  cleaned = cleaned.replace(/\s+\d+(?:\.\d+)?\s.*$/, '').trim()
  return cleaned
}

const matchesPosition = (playerPosition, wanted) => {
  if (!playerPosition) return false
  const normalized = String(playerPosition).toUpperCase()
  const tokens = normalized.split(/[^A-Z]+/).filter(Boolean)
  if (tokens.includes(wanted)) return true
  if ((wanted === 'PG' || wanted === 'SG') && tokens.includes('G')) return true
  if ((wanted === 'SF' || wanted === 'PF') && tokens.includes('F')) return true
  return wanted === 'C' && tokens.includes('C')
}

const buildFallbackBestAvailable = (allPlayers, picks, limitPerPos = 15) => {
  const pickedIds = new Set(
    (picks || [])
      .map((pick) => pick.player_id ?? pick.playerId)
      .filter((id) => !!id)
  )

  const pool = (allPlayers || [])
    .filter((p) => {
      const playerId = p.playerId ?? p.player_id
      return playerId && !pickedIds.has(playerId)
    })
    .sort((a, b) => rankingScore(b) - rankingScore(a))

  const byPosition = {}
  POSITIONS.forEach((pos) => {
    byPosition[pos] = pool.filter((p) => matchesPosition(p.position, pos)).slice(0, limitPerPos)
  })

  return {
    overall: pool.slice(0, Math.max(limitPerPos, 30)),
    byPosition,
    totalPicksMade: pickedIds.size,
  }
}

const isBenchLikeSlot = (slot) => {
  const normalized = String(slot || '').toUpperCase()
  return normalized === 'BN' || normalized === 'BENCH' || normalized === 'TAXI'
}

const isReserveLikeSlot = (slot) => {
  const normalized = String(slot || '').toUpperCase()
  return normalized === 'IR' || normalized === 'IR+' || normalized === 'INJURED_RESERVE'
}

const hasUsablePosition = (playerPosition) =>
  String(playerPosition || '').split(/[^A-Z]+/i).filter(Boolean).length > 0

const canFillLineupSlot = (playerPosition, slot) => {
  const normalizedSlot = String(slot || '').toUpperCase()

  if (!hasUsablePosition(playerPosition)) return false

  if (['PG', 'SG', 'SF', 'PF', 'C'].includes(normalizedSlot)) {
    return matchesPosition(playerPosition, normalizedSlot)
  }

  if (normalizedSlot === 'G') {
    return matchesPosition(playerPosition, 'PG') || matchesPosition(playerPosition, 'SG')
  }

  if (normalizedSlot === 'F') {
    return matchesPosition(playerPosition, 'SF') || matchesPosition(playerPosition, 'PF')
  }

  if (normalizedSlot === 'G/F' || normalizedSlot === 'GF') {
    return normalizedSlot === 'GF'
      ? hasUsablePosition(playerPosition)
      : matchesPosition(playerPosition, 'PG')
        || matchesPosition(playerPosition, 'SG')
        || matchesPosition(playerPosition, 'SF')
        || matchesPosition(playerPosition, 'PF')
  }

  if (normalizedSlot === 'F/C' || normalizedSlot === 'FC') {
    return matchesPosition(playerPosition, 'SF')
      || matchesPosition(playerPosition, 'PF')
      || matchesPosition(playerPosition, 'C')
  }

  if (normalizedSlot === 'UTIL' || normalizedSlot === 'UTL' || normalizedSlot === 'FLEX') {
    return hasUsablePosition(playerPosition)
  }

  return hasUsablePosition(playerPosition)
}

const getEligibleStarterSlotCount = (playerPosition, starterSlots) =>
  starterSlots.filter((slot) => canFillLineupSlot(playerPosition, slot)).length

const getLineupSlotPriority = (slot) => {
  const normalized = String(slot || '').toUpperCase()

  switch (normalized) {
    case 'PG':
      return 1
    case 'SG':
      return 2
    case 'SF':
      return 3
    case 'PF':
      return 4
    case 'C':
      return 5
    case 'G':
      return 6
    case 'F':
      return 7
    case 'G/F':
    case 'GF':
      return 8
    case 'F/C':
    case 'FC':
      return 9
    case 'UTIL':
    case 'UTL':
    case 'FLEX':
      return 10
    default:
      return 11
  }
}

const buildTeamLayout = (picks, rosterPositions) => {
  const orderedPicks = [...(picks || [])].sort(
    (a, b) => (a.pickNo ?? Number.MAX_SAFE_INTEGER) - (b.pickNo ?? Number.MAX_SAFE_INTEGER)
  )

  const sourceSlots = Array.isArray(rosterPositions) && rosterPositions.length > 0
    ? rosterPositions
    : DEFAULT_LINEUP_SLOTS

  const starterSlots = sourceSlots
    .filter((slot) => !isBenchLikeSlot(slot) && !isReserveLikeSlot(slot))
    .map((slot, index) => ({ slot, index }))
    .sort((a, b) => {
      const priorityDiff = getLineupSlotPriority(a.slot) - getLineupSlotPriority(b.slot)
      if (priorityDiff !== 0) return priorityDiff
      return a.index - b.index
    })
    .map(({ slot }) => slot)

  const assignedIndexes = new Set()
  const starters = starterSlots.map((slot) => {
    const eligible = orderedPicks
      .map((player, index) => ({ player, index }))
      .filter(({ player, index }) => {
        return !assignedIndexes.has(index) && canFillLineupSlot(player.position, slot)
      })
      .sort((a, b) => {
        const eligibleSlotsA = getEligibleStarterSlotCount(a.player.position, starterSlots)
        const eligibleSlotsB = getEligibleStarterSlotCount(b.player.position, starterSlots)
        if (eligibleSlotsA !== eligibleSlotsB) return eligibleSlotsA - eligibleSlotsB

        return (a.player.pickNo ?? Number.MAX_SAFE_INTEGER) - (b.player.pickNo ?? Number.MAX_SAFE_INTEGER)
      })

    const selected = eligible[0]
    if (selected) {
      assignedIndexes.add(selected.index)
    }

    return {
      slot,
      player: selected?.player ?? null,
    }
  })

  const bench = orderedPicks.filter((_, index) => !assignedIndexes.has(index))

  return {
    starters,
    bench,
    hasStructuredSlots: true,
    usedFallbackSlots: !Array.isArray(rosterPositions) || rosterPositions.length === 0,
  }
}

const getPositionBadgeTone = (position) => {
  const normalized = String(position || '').toUpperCase()
  if (matchesPosition(normalized, 'PG')) return 'text-sky-300 bg-sky-400/10 border-sky-400/30'
  if (matchesPosition(normalized, 'SG')) return 'text-indigo-300 bg-indigo-400/10 border-indigo-400/30'
  if (matchesPosition(normalized, 'SF')) return 'text-emerald-300 bg-emerald-400/10 border-emerald-400/30'
  if (matchesPosition(normalized, 'PF')) return 'text-amber-300 bg-amber-400/10 border-amber-400/30'
  if (matchesPosition(normalized, 'C')) return 'text-rose-300 bg-rose-400/10 border-rose-400/30'
  return 'text-sleeper-muted bg-sleeper-border/40 border-sleeper-border'
}

const PositionBadge = ({ position }) => {
  if (!position) return null

  return (
    <span className={`inline-flex items-center rounded-md border px-1.5 py-0.5 text-[10px] font-semibold ${getPositionBadgeTone(position)}`}>
      {position}
    </span>
  )
}

const getDisplayPosition = (player) => player?.eligiblePositions || player?.position || ''

const getPositionCountMap = (players) => {
  const source = Array.isArray(players) ? players : []

  return POSITIONS.reduce((counts, position) => {
    counts[position] = source.filter((player) => matchesPosition(getDisplayPosition(player), position)).length
    return counts
  }, {})
}

export default function DraftHelper() {
  // Setup form state
  const [sleeperUsername, setSleeperUsername] = useState('')
  const [draftId, setDraftId] = useState('')
  const [setupError, setSetupError] = useState('')
  const [connecting, setConnecting] = useState(false)

  // Active session + live board state
  const [session, setSession] = useState(null) // { userId, username, draftId }
  const [draft, setDraft] = useState(null)
  const [league, setLeague] = useState(null)
  const [picks, setPicks] = useState([])
  const [myPicks, setMyPicks] = useState([])
  const [bestAvailable, setBestAvailable] = useState(null)
  const [dynastyRankings, setDynastyRankings] = useState(null)
  const [rankingMode, setRankingMode] = useState('SLEEPER') // SLEEPER | DYNASTY
  const [activeTab, setActiveTab] = useState('ALL')
  const [boardError, setBoardError] = useState('')
  const [initialLoading, setInitialLoading] = useState(false)

  const intervalRef = useRef(null)
  const draftLeagueId = draft?.leagueId ?? draft?.league_id

  // Restore the last used username/draft ID so the form isn't empty on revisit
  useEffect(() => {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (!saved) return
    try {
      const parsed = JSON.parse(saved)
      setSleeperUsername(parsed.username || '')
      setDraftId(parsed.draftId || '')
    } catch {
      // ignore malformed cache
    }
  }, [])

  useEffect(() => {
    if (!draftLeagueId) {
      setLeague(null)
      return undefined
    }

    let cancelled = false

    leaguesApi.getLeague(draftLeagueId)
      .then((res) => {
        if (!cancelled) {
          setLeague(res.data)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLeague(null)
        }
      })

    return () => {
      cancelled = true
    }
  }, [draftLeagueId])

  const refreshBoard = useCallback(async (currentDraftId, currentUserId) => {
    try {
      const [draftRes, picksRes, myPicksRes, bestRes, dynastyRes] = await Promise.allSettled([
        draftApi.getDraft(currentDraftId),
        draftApi.getPicks(currentDraftId),
        draftApi.getMyPicks(currentDraftId, currentUserId),
        draftApi.getBestAvailable(currentDraftId, 15),
        rankingsApi.getRemainingForDraft(currentDraftId, 20),
      ])

      if (draftRes.status !== 'fulfilled' || picksRes.status !== 'fulfilled' || myPicksRes.status !== 'fulfilled') {
        throw (draftRes.reason || picksRes.reason || myPicksRes.reason)
      }

      setDraft(draftRes.value.data)
      setPicks(picksRes.value.data)
      setMyPicks(myPicksRes.value.data)

      if (dynastyRes.status === 'fulfilled') {
        setDynastyRankings(dynastyRes.value.data)
      } else {
        setDynastyRankings(null)
        if (rankingMode === 'DYNASTY') {
          setRankingMode('SLEEPER')
        }
      }

      const bestData = bestRes.status === 'fulfilled' ? bestRes.value.data : null
      const hasBestData =
        (bestData?.overall && bestData.overall.length > 0) ||
        Object.values(bestData?.byPosition || {}).some((arr) => Array.isArray(arr) && arr.length > 0)

      if (hasBestData) {
        setBestAvailable(bestData)
      } else {
        const allPlayersRes = await playersApi.getAll()
        const fallback = buildFallbackBestAvailable(allPlayersRes.data, picksRes.value.data, 15)
        setBestAvailable(fallback)
      }
      setBoardError('')
    } catch (err) {
      setBoardError(
        err.response?.data?.message || 'Lost connection to the draft. Retrying...'
      )
    }
  }, [])

  // Poll the draft while a session is active
  useEffect(() => {
    if (!session) return undefined

    setInitialLoading(true)
    refreshBoard(session.draftId, session.userId).finally(() => setInitialLoading(false))

    intervalRef.current = setInterval(() => {
      refreshBoard(session.draftId, session.userId)
    }, POLL_INTERVAL_MS)

    return () => clearInterval(intervalRef.current)
  }, [session, refreshBoard])

  const handleConnect = async (e) => {
    e.preventDefault()
    setSetupError('')
    setConnecting(true)
    try {
      const trimmedUsername = sleeperUsername.trim()
      const trimmedDraftId = draftId.trim()

      const [userRes] = await Promise.all([
        draftApi.resolveUser(trimmedUsername),
        draftApi.getDraft(trimmedDraftId), // validates the draft ID up front
      ])

      const newSession = {
        userId: userRes.data.userId,
        username: userRes.data.username,
        draftId: trimmedDraftId,
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(newSession))
      setActiveTab('ALL')
      setSession(newSession)
    } catch (err) {
      setSetupError(
        err.response?.data?.message ||
        'Could not connect. Double-check your Sleeper username and draft ID.'
      )
    } finally {
      setConnecting(false)
    }
  }

  const handleDisconnect = () => {
    clearInterval(intervalRef.current)
    setSession(null)
    setDraft(null)
    setLeague(null)
    setPicks([])
    setMyPicks([])
    setBestAvailable(null)
    setDynastyRankings(null)
    setBoardError('')
  }

  // ---------------------------------------------------------------------
  // Setup screen
  // ---------------------------------------------------------------------
  if (!session) {
    return (
      <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">
          <div className="mb-6 text-center">
            <h1 className="text-2xl font-bold">Draft Helper</h1>
            <p className="text-sleeper-muted mt-1 text-sm">
              Connect to a live Sleeper draft to track your picks and see the
              best available players at every position.
            </p>
          </div>

          <div className="card">
            <form onSubmit={handleConnect} className="space-y-4">
              <div>
                <label htmlFor="sleeperUsername">Sleeper Username</label>
                <input
                  id="sleeperUsername"
                  type="text"
                  placeholder="e.g. hoopsdynasty"
                  value={sleeperUsername}
                  onChange={(e) => setSleeperUsername(e.target.value)}
                  required
                />
              </div>

              <div>
                <label htmlFor="draftId">League Draft ID</label>
                <input
                  id="draftId"
                  type="text"
                  placeholder="e.g. 987654321012345678"
                  value={draftId}
                  onChange={(e) => setDraftId(e.target.value)}
                  required
                />
                <p className="text-xs text-sleeper-muted mt-1.5">
                  Find this in your league&apos;s draft URL on Sleeper (the long
                  number after /draft/).
                </p>
              </div>

              {setupError && <p className="error-msg">{setupError}</p>}

              <button
                type="submit"
                className="btn-primary"
                disabled={connecting || !sleeperUsername.trim() || !draftId.trim()}
              >
                {connecting ? 'Connecting...' : 'Connect to Draft'}
              </button>
            </form>
          </div>
        </div>
      </div>
    )
  }

  // ---------------------------------------------------------------------
  // Live draft board
  // ---------------------------------------------------------------------
  const totalRounds = draft?.settings?.rounds
  const totalTeams = draft?.settings?.teams
  const totalPicks = totalRounds && totalTeams ? totalRounds * totalTeams : null
  const picksMade = bestAvailable?.totalPicksMade ?? picks.length
  const currentPickNo = Math.min(picksMade + 1, totalPicks ?? picksMade + 1)
  const myTeamLayout = buildTeamLayout(
    myPicks,
    league?.rosterPositions ?? league?.roster_positions ?? []
  )
  const filledStarterCount = myTeamLayout.starters.filter((slot) => slot.player).length
  const totalStarterCount = myTeamLayout.starters.length

  const recentPicks = [...picks]
    .filter((p) => p.player_id)
    .sort((a, b) => (b.pick_no ?? 0) - (a.pick_no ?? 0))
    .slice(0, 8)

  const tabs = rankingMode === 'DYNASTY'
    ? ['ALL', ...POSITIONS, 'ROOKIES']
    : ['ALL', ...POSITIONS]
  const sleeperOverall = bestAvailable?.overall || []
  const dynastyOverall = dynastyRankings?.overall || []
  const sleeperPositionCounts = getPositionCountMap(sleeperOverall)
  const dynastyPositionCounts = getPositionCountMap(dynastyOverall)
  const rookieCount = (dynastyRankings?.rookies || dynastyOverall).filter((player) => player.rookie).length

  const sleeperActiveList =
    activeTab === 'ALL'
      ? sleeperOverall
      : (bestAvailable?.byPosition?.[activeTab]?.length
        ? bestAvailable.byPosition[activeTab]
        : sleeperOverall.filter((player) => matchesPosition(player.position, activeTab)))

  const dynastyActiveList =
    activeTab === 'ALL'
      ? dynastyOverall
      : activeTab === 'ROOKIES'
        ? (dynastyRankings?.rookies || dynastyOverall).filter((player) => player.rookie)
        : dynastyOverall.filter((player) => matchesPosition(player.position, activeTab))

  const activeList = rankingMode === 'DYNASTY' ? dynastyActiveList : sleeperActiveList

  const rankedActiveList = rankingMode === 'DYNASTY'
    ? [...(activeList || [])].sort((a, b) => {
      if (activeTab === 'ROOKIES') {
        const aBlend = a.blendedScore ?? -999
        const bBlend = b.blendedScore ?? -999
        if (aBlend !== bBlend) return bBlend - aBlend

        const aRank = a.externalAvgRank ?? Number.MAX_SAFE_INTEGER
        const bRank = b.externalAvgRank ?? Number.MAX_SAFE_INTEGER
        if (aRank !== bRank) return aRank - bRank
      }
      const aBlend = a.blendedScore ?? -999
      const bBlend = b.blendedScore ?? -999
      return bBlend - aBlend
    })
    : [...(activeList || [])].sort((a, b) => rankingScore(b) - rankingScore(a))

  const getTabCount = (tab) => {
    if (tab === 'ALL') {
      return rankingMode === 'DYNASTY' ? dynastyOverall.length : sleeperOverall.length
    }

    if (tab === 'ROOKIES') {
      return rookieCount
    }

    return rankingMode === 'DYNASTY'
      ? (dynastyPositionCounts[tab] || 0)
      : (sleeperPositionCounts[tab] || 0)
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex items-start justify-between flex-wrap gap-4 mb-6">
        <div>
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-sleeper-green animate-pulse" />
            <h1 className="text-2xl font-bold">Draft Helper</h1>
          </div>
          <p className="text-sleeper-muted text-sm mt-1">
            Connected as{' '}
            <span className="text-sleeper-accent font-medium">@{session.username}</span>
            {draft?.status && (
              <>
                {' '}·{' '}
                <span
                  className={draft.status === 'complete' ? 'text-sleeper-red' : 'text-sleeper-green'}
                >
                  {draft.status.replace('_', ' ')}
                </span>
              </>
            )}
            {totalPicks && (
              <>
                {' '}· Pick {Math.min(currentPickNo, totalPicks)} of {totalPicks}
              </>
            )}
          </p>
        </div>
        <button onClick={handleDisconnect} className="btn-secondary w-auto px-4 text-sm">
          Change Draft
        </button>
      </div>

      {boardError && (
        <div className="card mb-6 border-sleeper-red/50 text-sleeper-red text-sm">
          {boardError}
        </div>
      )}

      {initialLoading ? (
        <div className="flex justify-center py-16">
          <div className="w-8 h-8 border-4 border-sleeper-accent border-t-transparent rounded-full animate-spin" />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Left column: My Team + Recent Picks */}
          <div className="lg:col-span-1 space-y-6">
            <div className="card">
              <h2 className="font-semibold mb-3 flex items-center justify-between">
                My Team
                <span className="text-xs text-sleeper-muted font-normal">
                  {myPicks.length} pick{myPicks.length === 1 ? '' : 's'}
                </span>
              </h2>
              {myPicks.length === 0 ? (
                <p className="text-sm text-sleeper-muted">
                  No picks yet. They&apos;ll show up here as soon as it&apos;s your turn.
                </p>
              ) : myTeamLayout.hasStructuredSlots ? (
                <div className="space-y-4">
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <div>
                        <p className="text-[11px] uppercase tracking-wider text-sleeper-muted font-semibold">
                          Starting Lineup
                        </p>
                        {myTeamLayout.usedFallbackSlots && (
                          <p className="text-[10px] text-sleeper-border mt-0.5">
                            Estimated lineup layout
                          </p>
                        )}
                      </div>
                      <span className="text-[11px] text-sleeper-muted">
                        {filledStarterCount}/{totalStarterCount} filled
                      </span>
                    </div>
                    <ul className="space-y-2">
                      {myTeamLayout.starters.map(({ slot, player }, index) => (
                        <li
                          key={`${slot}-${index}`}
                          className="flex items-start gap-2 rounded-lg border border-sleeper-border/60 bg-sleeper-bg/30 px-2 py-2"
                        >
                          <span className="shrink-0 min-w-10 text-center text-[11px] font-semibold bg-sleeper-border text-sleeper-accent rounded px-2 py-1">
                            {slot}
                          </span>
                          {player ? (
                            <div className="min-w-0 text-sm">
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <p className="font-medium truncate">{player.fullName}</p>
                                <PositionBadge position={player.position} />
                              </div>
                              <p className="text-xs text-sleeper-muted truncate">
                                {player.team || 'FA'}
                              </p>
                            </div>
                          ) : (
                            <div className="min-w-0">
                              <p className="text-sm text-sleeper-muted italic">Open slot</p>
                              <p className="text-xs text-sleeper-border">Draft a player eligible for {slot}</p>
                            </div>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <p className="text-[11px] uppercase tracking-wider text-sleeper-muted font-semibold">
                        Bench
                      </p>
                      <span className="text-[11px] text-sleeper-muted">
                        {myTeamLayout.bench.length} player{myTeamLayout.bench.length === 1 ? '' : 's'}
                      </span>
                    </div>
                    {myTeamLayout.bench.length === 0 ? (
                      <p className="text-sm text-sleeper-muted">No bench players yet.</p>
                    ) : (
                      <ul className="space-y-2">
                        {myTeamLayout.bench.map((pick, index) => (
                          <li
                            key={pick.pickNo}
                            className="flex items-start gap-2 rounded-lg border border-sleeper-border/60 bg-sleeper-bg/30 px-2 py-2"
                          >
                            <span className="shrink-0 min-w-10 text-center text-[11px] font-semibold bg-sleeper-border text-sleeper-muted rounded px-2 py-1">
                              BN{index + 1}
                            </span>
                            <div className="min-w-0 text-sm">
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <p className="font-medium truncate">{pick.fullName}</p>
                                <PositionBadge position={pick.position} />
                              </div>
                              <p className="text-xs text-sleeper-muted truncate">
                                {pick.team || 'FA'}
                              </p>
                            </div>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              ) : null}
            </div>

            <div className="card">
              <h2 className="font-semibold mb-3">Recent Picks</h2>
              {recentPicks.length === 0 ? (
                <p className="text-sm text-sleeper-muted">No picks yet.</p>
              ) : (
                <ul className="space-y-2">
                  {recentPicks.map((pick) => {
                    const meta = pick.metadata || {}
                    const name = `${meta.first_name || ''} ${meta.last_name || ''}`.trim() || 'Unknown'
                    return (
                      <li key={pick.pick_no} className="text-sm">
                        <span className="text-sleeper-muted text-xs mr-1.5">
                          #{pick.pick_no}
                        </span>
                        <span className="font-medium">{name}</span>
                        <span className="text-xs text-sleeper-muted ml-1.5">
                          {meta.position || ''} {meta.team ? `· ${meta.team}` : ''}
                        </span>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          </div>

          {/* Main: Best Available */}
          <div className="lg:col-span-3">
            <div className="card">
              <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
                <h2 className="font-semibold">Best Available</h2>
                <div className="flex items-center gap-2 flex-wrap">
                  <div className="flex gap-1 flex-wrap">
                    <button
                      onClick={() => {
                        setRankingMode('SLEEPER')
                        if (activeTab === 'ROOKIES') setActiveTab('ALL')
                      }}
                      className={`text-xs font-medium px-3 py-1.5 rounded-lg transition ${
                        rankingMode === 'SLEEPER'
                          ? 'bg-sleeper-accent text-sleeper-bg'
                          : 'bg-sleeper-bg border border-sleeper-border text-sleeper-muted hover:text-white'
                      }`}
                    >
                      Sleeper
                    </button>
                    <button
                      onClick={() => setRankingMode('DYNASTY')}
                      className={`text-xs font-medium px-3 py-1.5 rounded-lg transition ${
                        rankingMode === 'DYNASTY'
                          ? 'bg-sleeper-accent text-sleeper-bg'
                          : 'bg-sleeper-bg border border-sleeper-border text-sleeper-muted hover:text-white'
                      }`}
                    >
                      Dynasty + Rookies
                    </button>
                  </div>

                  <div className="flex gap-1 flex-wrap">
                    {tabs.map((tab) => (
                      <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`text-xs font-medium px-3 py-1.5 rounded-lg transition ${
                          activeTab === tab
                            ? 'bg-sleeper-accent text-sleeper-bg'
                            : 'bg-sleeper-bg border border-sleeper-border text-sleeper-muted hover:text-white'
                        }`}
                      >
                        {tab} ({getTabCount(tab)})
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="mb-4 rounded-xl border border-sleeper-border bg-sleeper-border/20 p-3 text-xs text-sleeper-muted">
                <div className="flex items-center gap-2 text-white font-medium mb-1.5">
                  <span aria-hidden="true">ℹ️</span>
                  <span>How this board works</span>
                </div>
                <ul className="space-y-1">
                  <li><span className="text-white font-medium">Sleeper</span> uses market rank and fantasy production for the best-available board.</li>
                  <li><span className="text-white font-medium">Blended</span> in Dynasty mode mixes Sleeper value, production, and external rankings.</li>
                  <li><span className="text-white font-medium">ADP Rank</span> is the Sleeper value/search rank. Lower is better.</li>
                  <li><span className="text-white font-medium">Rookies</span> are ranked with draft-consensus input, but the full dynasty board still gives proven veterans meaningful weight.</li>
                </ul>
              </div>

              {!rankedActiveList || rankedActiveList.length === 0 ? (
                <p className="text-sm text-sleeper-muted py-6 text-center">
                  No available players found for this position.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  {/* Column headers */}
                  {rankingMode === 'DYNASTY' ? (
                    <div className="grid grid-cols-[2rem_1fr_6rem_6.5rem_5.5rem] gap-x-3 text-xs font-semibold text-sleeper-muted uppercase tracking-wider px-2 pb-2 border-b border-sleeper-border mb-1">
                      <span>#</span>
                      <span>Player</span>
                      <span className="text-center">Blend</span>
                      <span className="text-center">Ext Rank</span>
                      <span className="text-center">Rookie</span>
                    </div>
                  ) : (
                    <div className="grid grid-cols-[2rem_1fr_5.5rem_5.5rem_auto] gap-x-3 text-xs font-semibold text-sleeper-muted uppercase tracking-wider px-2 pb-2 border-b border-sleeper-border mb-1">
                      <span>#</span>
                      <span>Player</span>
                      <span className="text-center">FPTS/G</span>
                      <span className="text-center">ADP Rank</span>
                      <span>Stats/G</span>
                    </div>
                  )}

                  <ul className="space-y-0.5">
                    {rankedActiveList.map((player, i) => {
                      if (rankingMode === 'DYNASTY') {
                        return (
                          <li
                            key={`${player.playerId || 'rookie'}-${player.playerName || player.fullName}-${i}`}
                            className="grid grid-cols-[2rem_1fr_6rem_6.5rem_5.5rem] gap-x-3 items-center px-2 py-2 rounded-lg hover:bg-sleeper-border/30 transition"
                          >
                            <span className="text-xs text-sleeper-muted font-mono">{player.finalRank ?? i + 1}</span>
                            <div className="min-w-0">
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <span className="font-medium text-sm truncate">{cleanDynastyName(player.playerName || player.fullName)}</span>
                                <PositionBadge position={getDisplayPosition(player)} />
                              </div>
                              <p className="text-xs text-sleeper-muted truncate">
                                {getDisplayPosition(player) || '—'} · {player.team || 'FA'}
                              </p>
                            </div>

                            <div className="text-center">
                              {player.blendedScore != null ? (
                                <span className="font-bold text-sm text-sleeper-accent">
                                  {player.blendedScore.toFixed(2)}
                                </span>
                              ) : (
                                <span className="text-xs text-sleeper-muted">—</span>
                              )}
                            </div>

                            <div className="text-center">
                              {player.externalAvgRank != null ? (
                                <span className="text-xs text-sleeper-muted font-mono">
                                  #{Math.round(player.externalAvgRank)}
                                </span>
                              ) : (
                                <span className="text-xs text-sleeper-muted">—</span>
                              )}
                            </div>

                            <div className="text-center">
                              {player.rookie ? (
                                <span className="text-[11px] px-2 py-1 rounded bg-sleeper-accent/20 text-sleeper-accent font-semibold">
                                  Yes
                                </span>
                              ) : (
                                <span className="text-xs text-sleeper-muted">No</span>
                              )}
                            </div>
                          </li>
                        )
                      }

                      const hasFpts = player.fantasyPtsAvg != null
                      const hasStats = player.avgPts != null
                      return (
                        <li
                          key={player.playerId}
                          className="grid grid-cols-[2rem_1fr_5.5rem_5.5rem_auto] gap-x-3 items-center px-2 py-2 rounded-lg hover:bg-sleeper-border/30 transition"
                        >
                          {/* Rank */}
                          <span className="text-xs text-sleeper-muted font-mono">{i + 1}</span>

                          {/* Name + position + injury */}
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5 flex-wrap">
                              <span className="font-medium text-sm truncate">{player.fullName}</span>
                              <PositionBadge position={getDisplayPosition(player)} />
                              {player.injuryStatus && (
                                <span className="text-xs text-sleeper-red font-semibold shrink-0">
                                  {player.injuryStatus}
                                </span>
                              )}
                            </div>
                            <p className="text-xs text-sleeper-muted">
                              {getDisplayPosition(player) || '—'} · {player.team || 'FA'}
                              {player.age ? ` · Age ${player.age}` : ''}
                            </p>
                          </div>

                          {/* Fantasy pts avg */}
                          <div className="text-center">
                            {hasFpts ? (
                              <span className="font-bold text-sm text-sleeper-accent">
                                {player.fantasyPtsAvg.toFixed(1)}
                              </span>
                            ) : (
                              <span className="text-xs text-sleeper-muted">—</span>
                            )}
                          </div>

                          {/* ADP rank (search_rank from Sleeper = dynasty value rank) */}
                          <div className="text-center">
                            {player.searchRank != null ? (
                              <span className="text-xs text-sleeper-muted font-mono">
                                #{player.searchRank}
                              </span>
                            ) : (
                              <span className="text-xs text-sleeper-muted">—</span>
                            )}
                          </div>

                          {/* Per-game stat line */}
                          <div className="text-xs text-sleeper-muted whitespace-nowrap">
                            {hasStats ? (
                              <span>
                                {player.avgPts}pts · {player.avgReb}reb · {player.avgAst}ast
                                {(player.avgStl > 0 || player.avgBlk > 0) && (
                                  <> · {player.avgStl}stl · {player.avgBlk}blk</>
                                )}
                              </span>
                            ) : (
                              <span className="text-sleeper-border italic">Run /players/sync-stats</span>
                            )}
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
