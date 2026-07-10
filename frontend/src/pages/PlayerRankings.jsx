import { useState, useEffect } from 'react'
import { rankingsApi } from '../api/axiosClient'

const POSITIONS = ['PG', 'SG', 'SF', 'PF', 'C']

const matchesPosition = (playerPosition, wanted) => {
  if (!playerPosition) return false
  const normalized = String(playerPosition).toUpperCase()
  const tokens = normalized.split(/[^A-Z]+/).filter(Boolean)
  if (tokens.includes(wanted)) return true
  if ((wanted === 'PG' || wanted === 'SG') && tokens.includes('G')) return true
  if ((wanted === 'SF' || wanted === 'PF') && tokens.includes('F')) return true
  return wanted === 'C' && tokens.includes('C')
}

const getDisplayPosition = (player) => player?.eligiblePositions || player?.position || ''

const getPositionCountMap = (players) => {
  const source = Array.isArray(players) ? players : []

  return POSITIONS.reduce((counts, position) => {
    counts[position] = source.filter((player) => matchesPosition(getDisplayPosition(player), position)).length
    return counts
  }, {})
}

export default function PlayerRankings() {
  const [rankings, setRankings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedPosition, setSelectedPosition] = useState('ALL')
  const [searchTerm, setSearchTerm] = useState('')
  const [sortBy, setSortBy] = useState('rank') // rank, name, position, fpts, adp
  const positionCounts = getPositionCountMap(rankings)

  useEffect(() => {
    const fetchRankings = async () => {
      try {
        setLoading(true)
        const response = await rankingsApi.getAllRankings()
        setRankings(response.data || [])
        setError('')
      } catch (err) {
        setError('Failed to load player rankings')
        console.error(err)
      } finally {
        setLoading(false)
      }
    }

    fetchRankings()
  }, [])

  // Filter rankings
  let filtered = rankings.filter((player) => {
    const displayPosition = getDisplayPosition(player)
    const matchesPos = selectedPosition === 'ALL' || matchesPosition(displayPosition, selectedPosition)
    const matchesSearch = !searchTerm ||
      player.playerName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      player.team?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      getDisplayPosition(player).toLowerCase().includes(searchTerm.toLowerCase())
    ||
      (player.team ?? '').toLowerCase().includes(searchTerm.toLowerCase())
    return matchesPos && matchesSearch
  })

  // Sort rankings
  filtered = filtered.sort((a, b) => {
    switch (sortBy) {
      case 'rank':
        return (a.finalRank || 999) - (b.finalRank || 999)
      case 'name':
        return (a.playerName || '').localeCompare(b.playerName || '')
      case 'position':
        return getDisplayPosition(a).localeCompare(getDisplayPosition(b))
      case 'fpts':
        return (b.sleeperFantasyPtsAvg || 0) - (a.sleeperFantasyPtsAvg || 0)
      case 'adp':
        const adpA = a.sleeperSearchRank || 9999
        const adpB = b.sleeperSearchRank || 9999
        return adpA - adpB
      case 'blended':
        return (b.blendedScore || 0) - (a.blendedScore || 0)
      default:
        return 0
    }
  })

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-2">Player Rankings</h1>
        <p className="text-sleeper-muted">
          Dynasty and redraft rankings updated with the latest performance data.
        </p>
        <div className="mt-4 rounded-xl border border-sleeper-border bg-sleeper-border/20 p-4 text-sm text-sleeper-muted">
          <div className="flex items-center gap-2 text-white font-medium mb-2">
            <span aria-hidden="true">ℹ️</span>
            <span>How rankings work</span>
          </div>
          <ul className="space-y-1.5">
            <li><span className="text-white font-medium">Blended</span> combines Sleeper market value, recent production, and external rankings.</li>
            <li><span className="text-white font-medium">ADP</span> is the Sleeper value rank/search rank. Lower is better.</li>
            <li><span className="text-white font-medium">Rookies</span> use draft-consensus signals, but are intentionally tempered versus proven veterans on the full board.</li>
          </ul>
        </div>
      </div>

      {/* Controls */}
      <div className="card mb-6">
        <div className="space-y-4">
          {/* Search */}
          <div>
            <label className="block text-sm font-medium mb-2">Search</label>
            <input
              type="text"
              placeholder="Player name or team..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full px-4 py-2 bg-sleeper-input border border-sleeper-border rounded-lg text-white placeholder-sleeper-muted focus:outline-none focus:border-sleeper-accent"
            />
          </div>

          {/* Filters */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Position Filter */}
            <div>
              <label className="block text-sm font-medium mb-2">Position</label>
              <div className="flex gap-2 flex-wrap">
                {['ALL', ...POSITIONS].map((pos) => (
                  <button
                    key={pos}
                    onClick={() => setSelectedPosition(pos)}
                    className={`px-3 py-1 rounded-lg text-sm font-medium transition ${
                      selectedPosition === pos
                        ? 'bg-sleeper-accent text-black'
                        : 'bg-sleeper-border text-sleeper-muted hover:text-white'
                    }`}
                  >
                    {pos} ({pos === 'ALL' ? rankings.length : (positionCounts[pos] || 0)})
                  </button>
                ))}
              </div>
            </div>

            {/* Sort */}
            <div>
              <label className="block text-sm font-medium mb-2">Sort By</label>
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="w-full px-4 py-2 bg-sleeper-input border border-sleeper-border rounded-lg text-white focus:outline-none focus:border-sleeper-accent"
              >
                <option value="rank">Rank</option>
                <option value="name">Name</option>
                <option value="position">Position</option>
                <option value="blended">Blended Score</option>
                <option value="fpts">Fantasy Points</option>
                <option value="adp">ADP</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="mb-6 p-4 bg-red-900/20 border border-red-900 rounded-lg text-red-200">
          {error}
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div className="flex justify-center py-12">
          <div className="w-8 h-8 border-4 border-sleeper-accent border-t-transparent rounded-full animate-spin" />
        </div>
      )}

      {/* Results */}
      {!loading && (
        <div className="card overflow-x-auto">
          {filtered.length === 0 ? (
            <div className="p-8 text-center text-sleeper-muted">
              No players found matching your filters.
            </div>
          ) : (
            <table className="w-full">
              <thead>
                <tr className="border-b border-sleeper-border">
                  <th className="px-4 py-3 text-left text-sm font-semibold text-sleeper-muted">Rank</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-sleeper-muted">Player</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-sleeper-muted">Pos</th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-sleeper-muted">Team</th>
                  <th className="px-4 py-3 text-right text-sm font-semibold text-sleeper-muted">Blended</th>
                  <th className="px-4 py-3 text-right text-sm font-semibold text-sleeper-muted">F.Pts</th>
                  <th className="px-4 py-3 text-right text-sm font-semibold text-sleeper-muted">ADP</th>
                  <th className="px-4 py-3 text-right text-sm font-semibold text-sleeper-muted">Ext Rank</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((player, idx) => (
                  <tr
                    key={`${player.playerId}-${idx}`}
                    className="border-b border-sleeper-border/50 hover:bg-sleeper-border/30 transition"
                  >
                    <td className="px-4 py-3 text-sm font-medium text-sleeper-accent">
                      #{player.finalRank || '-'}
                    </td>
                    <td className="px-4 py-3 text-sm font-medium">
                      <div className="flex items-center gap-2">
                        <span>{player.playerName}</span>
                        {player.rookie && (
                          <span className="text-xs bg-sleeper-accent text-black px-1.5 py-0.5 rounded font-medium">
                            R
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-sleeper-muted">
                      {getDisplayPosition(player) || '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-sleeper-muted">
                      {player.team || 'FA'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right font-medium">
                      {player.blendedScore?.toFixed(3) || '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right">
                      {player.sleeperFantasyPtsAvg?.toFixed(1) || '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right">
                      {player.sleeperSearchRank ? `#${player.sleeperSearchRank}` : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-sleeper-muted">
                      {player.externalAvgRank ? `${player.externalAvgRank.toFixed(1)} (${player.externalRankCount})` : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {/* Summary */}
          <div className="px-4 py-3 border-t border-sleeper-border text-sm text-sleeper-muted bg-sleeper-border/20">
            Showing {filtered.length} of {rankings.length} players
          </div>
        </div>
      )}
    </div>
  )
}
