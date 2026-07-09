import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { authApi } from '../api/axiosClient'

export default function SelectLeague() {
  const { user, selectLeague } = useAuth()
  const navigate = useNavigate()

  const [leagues, setLeagues] = useState([])
  const [fetching, setFetching] = useState(true)
  const [fetchError, setFetchError] = useState('')
  const [selecting, setSelecting] = useState(null) // leagueId being selected

  // Fetch the user's available Sleeper leagues
  useEffect(() => {
    authApi.getLeagues()
      .then((res) => setLeagues(res.data))
      .catch(() => setFetchError('Failed to load leagues. Please try again.'))
      .finally(() => setFetching(false))
  }, [])

  const handleSelect = async (leagueId) => {
    setSelecting(leagueId)
    try {
      await selectLeague(leagueId)
      navigate('/dashboard')
    } catch {
      setSelecting(null)
    }
  }

  return (
    <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-lg">
        {/* Progress indicator */}
        <div className="flex items-center gap-2 mb-8 justify-center">
          {[1, 2, 3].map((n) => (
            <div key={n} className="flex items-center gap-2">
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold
                  ${n <= 2 ? 'bg-sleeper-green text-sleeper-bg' : 'bg-sleeper-accent text-sleeper-bg'}`}
              >
                {n <= 2 ? '✓' : n}
              </div>
              {n < 3 && <div className="w-10 h-px bg-sleeper-border" />}
            </div>
          ))}
        </div>

        {/* Header */}
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold">Select your league</h1>
          <p className="text-sleeper-muted mt-1 text-sm">
            Synced from{' '}
            <span className="text-sleeper-accent font-medium">@{user?.sleeperUsername}</span>
            {' '}· Choose the league you want to analyse
          </p>
        </div>

        {/* Content */}
        {fetching && (
          <div className="flex justify-center py-12">
            <div className="w-8 h-8 border-4 border-sleeper-accent border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {fetchError && (
          <div className="card text-sleeper-red text-sm text-center">{fetchError}</div>
        )}

        {!fetching && !fetchError && leagues.length === 0 && (
          <div className="card text-center">
            <p className="text-sleeper-muted">No NBA leagues found for this Sleeper account.</p>
            <p className="text-xs text-sleeper-muted mt-1">
              Make sure you&apos;re in at least one active NBA league on Sleeper.
            </p>
          </div>
        )}

        {!fetching && leagues.length > 0 && (
          <div className="space-y-3">
            {leagues.map((league) => {
              const id = league.league_id || league.leagueId
              return (
                <button
                  key={id}
                  onClick={() => handleSelect(id)}
                  disabled={!!selecting}
                  className="w-full card text-left hover:border-sleeper-accent transition-colors
                             disabled:opacity-60 disabled:cursor-not-allowed group"
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-semibold group-hover:text-sleeper-accent transition-colors">
                        {league.name}
                      </p>
                      <div className="flex gap-3 mt-1 text-xs text-sleeper-muted">
                        <span>{league.total_rosters ?? league.totalRosters} teams</span>
                        <span>·</span>
                        <span>{league.season} {league.season_type ?? league.seasonType}</span>
                        <span>·</span>
                        <span
                          className={`font-medium ${
                            league.status === 'in_season'
                              ? 'text-sleeper-green'
                              : 'text-sleeper-muted'
                          }`}
                        >
                          {league.status?.replace(/_/g, ' ')}
                        </span>
                      </div>
                    </div>
                    <div className="text-sleeper-muted group-hover:text-sleeper-accent transition-colors ml-4">
                      {selecting === id ? (
                        <div className="w-5 h-5 border-2 border-sleeper-accent border-t-transparent rounded-full animate-spin" />
                      ) : (
                        '→'
                      )}
                    </div>
                  </div>
                </button>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
